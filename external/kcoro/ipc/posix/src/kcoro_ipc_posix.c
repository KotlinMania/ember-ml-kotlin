// SPDX-License-Identifier: BSD-3-Clause
/*
 * POSIX domain-socket transport (full‑duplex, frame + TLV)
 * -------------------------------------------------------
 *
 * Purpose
 * - Provide a small, portable, full‑duplex transport for kcoro IPC. Keeps
 *   transport mechanics (connect/listen/accept, frame I/O) separate from
 *   channel semantics.
 *
 * Framing
 * - Wire header carries cmd and payload length; the payload is TLV‑encoded.
 *   Request/response correlation uses a `req_id` TLV that servers echo.
 *
 * Non‑blocking model
 * - This implementation supports staged non‑blocking send/recv with internal
 *   buffers. The coroutine‑native harness will drive these with yield/park.
 *
 * Semantics
 * - Preserves channel error codes in replies. Logging is gated by KCORO_DEBUG.
 */
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdint.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <stdio.h>
#include <stdarg.h>

#include "../include/kcoro_ipc_posix.h"
#include "../../../include/kcoro_abi.h"
#include "../../../include/kcoro_config.h"

typedef struct kc_ipc_server {
    int fd;
    char path[108];
    pthread_mutex_t mu; /* thread-safe accept/config */
} kc_ipc_server_t;

struct kc_wire_hdr {
    uint16_t cmd;     /* kcoro_cmd */
    uint16_t rsvd;
    uint32_t len;     /* payload bytes */
};

typedef struct kc_ipc_conn {
    int fd;
    /* Non-blocking staged write */
    uint8_t *wbuf;      /* header+payload staged */
    size_t   wlen;      /* total bytes */
    size_t   woff;      /* bytes already sent */
    /* Non-blocking staged read */
    struct kc_wire_hdr rhdr;  /* header staging */
    size_t   rgot;      /* bytes of header read so far */
    uint8_t *rbuf;      /* payload buffer */
    size_t   rlen;      /* expected payload length */
    size_t   roff;      /* bytes of payload read */
    pthread_mutex_t mu; /* thread-safe staged I/O */
} kc_ipc_conn_t;

static size_t kc_strnlen(const char *s, size_t max)
{
    size_t i = 0; if (!s) return 0; while (i < max && s[i] != '\0') i++; return i;
}

static int sockaddr_un_init(struct sockaddr_un *sa, const char *path)
{
    memset(sa, 0, sizeof(*sa));
    sa->sun_family = AF_UNIX;
    size_t maxlen = sizeof(sa->sun_path) - 1;
    size_t n = kc_strnlen(path, maxlen + 1);
    if (n > maxlen) return -ENAMETOOLONG;
    memcpy(sa->sun_path, path, n);
    return (int)(offsetof(struct sockaddr_un, sun_path) + n + 1);
}

/* Debug helpers (enable with KCORO_DEBUG env var). */
static int kc_dbg_enabled(void)
{
    static int init = 0, on = 0;
    if (!init) { const char *s = getenv("KCORO_DEBUG"); on = (s && *s && s[0] != '0'); init = 1; }
    return on;
}
static void kc_dbg(const char *fmt, ...)
{
    if (!kc_dbg_enabled()) return;
    va_list ap; va_start(ap, fmt);
    fprintf(stderr, "[kcoro] "); vfprintf(stderr, fmt, ap); fprintf(stderr, "\n");
    va_end(ap);
}

int kc_ipc_srv_listen(const char *sock_path, kc_ipc_server_t **out)
{
    if (!sock_path || !out) return -EINVAL;
    int fd = socket(AF_UNIX, SOCK_SEQPACKET, 0);
    if (fd < 0) return -errno;
    struct sockaddr_un sa; int len = sockaddr_un_init(&sa, sock_path);
    if (len < 0) { close(fd); return len; }
    unlink(sock_path);
    if (bind(fd, (struct sockaddr*)&sa, len) < 0) { int e=-errno; close(fd); return e; }
    if (listen(fd, KCORO_IPC_BACKLOG) < 0) { int e=-errno; close(fd); unlink(sock_path); return e; }
    kc_ipc_server_t *srv = calloc(1, sizeof(*srv));
    if (!srv) { close(fd); unlink(sock_path); return -ENOMEM; }
    srv->fd = fd; strncpy(srv->path, sock_path, sizeof(srv->path)-1);
    pthread_mutex_init(&srv->mu, NULL);
    kc_dbg("srv%p listen %s fd=%d", (void*)srv, sock_path, fd);
    *out = srv; return 0;
}

int kc_ipc_srv_accept(kc_ipc_server_t *srv, kc_ipc_conn_t **out)
{
    if (!srv || !out) return -EINVAL;
    int cfd = accept(srv->fd, NULL, NULL);
    if (cfd < 0) return -errno;
    (void)fcntl(cfd, F_SETFD, FD_CLOEXEC);
    kc_ipc_conn_t *c = calloc(1, sizeof(*c)); if (!c) { close(cfd); return -ENOMEM; }
    c->fd = cfd; pthread_mutex_init(&c->mu, NULL); *out = c; kc_dbg("srv%p accept fd=%d conn%p", (void*)srv, cfd, (void*)c); return 0;
}

int kc_ipc_srv_set_nb(kc_ipc_server_t *srv, int nb_on)
{
    if (!srv) return -EINVAL;
    pthread_mutex_lock(&srv->mu);
    int flags = fcntl(srv->fd, F_GETFL, 0); if (flags < 0) { int e=-errno; pthread_mutex_unlock(&srv->mu); return e; }
    if (nb_on) flags |= O_NONBLOCK; else flags &= ~O_NONBLOCK;
    int rc = fcntl(srv->fd, F_SETFL, flags) < 0 ? -errno : 0;
    if (rc == 0) kc_dbg("srv%p set_nb %d", (void*)srv, nb_on);
    pthread_mutex_unlock(&srv->mu);
    return rc;
}

int kc_ipc_srv_accept_nb(kc_ipc_server_t *srv, kc_ipc_conn_t **out)
{
    if (!srv || !out) return -EINVAL;
    int cfd = accept(srv->fd, NULL, NULL);
    if (cfd < 0) return (errno == EAGAIN || errno == EWOULDBLOCK) ? -EAGAIN : -errno;
    (void)fcntl(cfd, F_SETFD, FD_CLOEXEC);
    kc_ipc_conn_t *c = calloc(1, sizeof(*c)); if (!c) { close(cfd); return -ENOMEM; }
    c->fd = cfd; pthread_mutex_init(&c->mu, NULL); *out = c; kc_dbg("srv%p try_accept fd=%d conn%p", (void*)srv, cfd, (void*)c); return 0;
}

int kc_ipc_srv_fd(kc_ipc_server_t *srv)
{ return srv ? srv->fd : -1; }

void kc_ipc_srv_close(kc_ipc_server_t *srv)
{
    if (!srv) return;
    close(srv->fd);
    unlink(srv->path);
    kc_dbg("srv%p close fd=%d", (void*)srv, srv->fd);
    pthread_mutex_destroy(&srv->mu);
    free(srv);
}

int kc_ipc_connect(const char *sock_path, kc_ipc_conn_t **out)
{
    if (!sock_path || !out) return -EINVAL;
    int fd = socket(AF_UNIX, SOCK_SEQPACKET, 0); if (fd < 0) return -errno;
    struct sockaddr_un sa; int len = sockaddr_un_init(&sa, sock_path);
    if (len < 0) { close(fd); return len; }
    if (connect(fd, (struct sockaddr*)&sa, len) < 0) { int e=-errno; close(fd); return e; }
    kc_ipc_conn_t *c = calloc(1, sizeof(*c)); if (!c) { close(fd); return -ENOMEM; }
    c->fd = fd; pthread_mutex_init(&c->mu, NULL); *out = c; kc_dbg("conn%p connect %s fd=%d", (void*)c, sock_path, fd); return 0;
}

void kc_ipc_conn_close(kc_ipc_conn_t *c)
{
    if (!c) return;
    pthread_mutex_lock(&c->mu);
    close(c->fd);
    free(c->wbuf);
    free(c->rbuf);
    kc_dbg("conn%p close fd=%d", (void*)c, c->fd);
    pthread_mutex_unlock(&c->mu);
    pthread_mutex_destroy(&c->mu);
    free(c);
}

int kc_ipc_conn_set_nb(kc_ipc_conn_t *c, int nb_on)
{
    if (!c) return -EINVAL;
    pthread_mutex_lock(&c->mu);
    int flags = fcntl(c->fd, F_GETFL, 0); if (flags < 0) { int e=-errno; pthread_mutex_unlock(&c->mu); return e; }
    if (nb_on) flags |= O_NONBLOCK; else flags &= ~O_NONBLOCK;
    int rc = fcntl(c->fd, F_SETFL, flags) < 0 ? -errno : 0;
    if (rc == 0) kc_dbg("conn%p set_nb %d", (void*)c, nb_on);
    pthread_mutex_unlock(&c->mu);
    return rc;
}

int kc_ipc_conn_fd(kc_ipc_conn_t *c)
{ return c ? c->fd : -1; }

static int send_all(int fd, const void *buf, size_t len)
{
    const uint8_t *p = buf; size_t off=0; ssize_t n;
    while (off < len) { n = send(fd, p+off, len-off, 0); if (n < 0) return -errno; off += (size_t)n; }
    return 0;
}

static int recv_all(int fd, void *buf, size_t len)
{
    uint8_t *p = buf; size_t off=0; ssize_t n;
    while (off < len) { n = recv(fd, p+off, len-off, 0); if (n <= 0) return n==0 ? -ECONNRESET : -errno; off += (size_t)n; }
    return 0;
}

int kc_ipc_send(kc_ipc_conn_t *c, uint16_t cmd, const void *payload, size_t len)
{
    if (!c) return -EINVAL;
    struct kc_wire_hdr h = { .cmd = htons(cmd), .rsvd = 0, .len = htonl((uint32_t)len) };
    int rc = send_all(c->fd, &h, sizeof(h)); if (rc) return rc;
    if (len && payload) rc = send_all(c->fd, payload, len);
    kc_dbg("conn%p send cmd=%u len=%zu rc=%d", (void*)c, cmd, len, rc);
    return rc;
}

int kc_ipc_recv(kc_ipc_conn_t *c, uint16_t *cmd, uint8_t **payload, size_t *len)
{
    if (!c || !cmd || !payload || !len) return -EINVAL;
    struct kc_wire_hdr h; int rc = recv_all(c->fd, &h, sizeof(h)); if (rc) return rc;
    uint32_t n = ntohl(h.len);
    uint16_t k = ntohs(h.cmd);
    uint8_t *buf = NULL;
    if (n) { buf = malloc(n); if (!buf) return -ENOMEM; rc = recv_all(c->fd, buf, n); if (rc) { free(buf); return rc; } }
    *cmd = k; *payload = buf; *len = n; kc_dbg("conn%p recv cmd=%u len=%zu", (void*)c, k, (size_t)n); return 0;
}

/* Non-blocking staged send: returns 0 when fully flushed, -EAGAIN if pending */
int kc_ipc_flush(kc_ipc_conn_t *c)
{
    if (!c) return -EINVAL;
    pthread_mutex_lock(&c->mu);
    if (c->wbuf == NULL || c->wlen == c->woff) { pthread_mutex_unlock(&c->mu); return 0; }
    ssize_t n = send(c->fd, c->wbuf + c->woff, c->wlen - c->woff, 0);
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) { pthread_mutex_unlock(&c->mu); return -EAGAIN; }
        int e = -errno; free(c->wbuf); c->wbuf=NULL; c->wlen=c->woff=0; pthread_mutex_unlock(&c->mu); return e;
    }
    c->woff += (size_t)n;
    if (c->woff == c->wlen) { free(c->wbuf); c->wbuf=NULL; c->wlen=c->woff=0; pthread_mutex_unlock(&c->mu); kc_dbg("conn%p flush done", (void*)c); return 0; }
    pthread_mutex_unlock(&c->mu);
    return -EAGAIN;
}

int kc_ipc_send_nb(kc_ipc_conn_t *c, uint16_t cmd, const void *payload, size_t len)
{
    if (!c) return -EINVAL;
    pthread_mutex_lock(&c->mu);
    /* If a previous frame is pending, attempt to flush it first */
    if (c->wbuf && c->wlen != c->woff) {
        ssize_t n = send(c->fd, c->wbuf + c->woff, c->wlen - c->woff, 0);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) { pthread_mutex_unlock(&c->mu); return -EAGAIN; }
            int e = -errno; free(c->wbuf); c->wbuf=NULL; c->wlen=c->woff=0; pthread_mutex_unlock(&c->mu); return e;
        }
        c->woff += (size_t)n;
        if (c->woff != c->wlen) { pthread_mutex_unlock(&c->mu); return -EAGAIN; }
        free(c->wbuf); c->wbuf=NULL; c->wlen=c->woff=0;
    }

    /* Stage header+payload into a single contiguous buffer */
    size_t tot = sizeof(struct kc_wire_hdr) + len;
    uint8_t *buf = malloc(tot); if (!buf) { pthread_mutex_unlock(&c->mu); return -ENOMEM; }
    struct kc_wire_hdr h = { .cmd = htons(cmd), .rsvd = 0, .len = htonl((uint32_t)len) };
    memcpy(buf, &h, sizeof(h));
    if (len && payload) memcpy(buf + sizeof(h), payload, len);

    /* Try to write immediately */
    ssize_t n = send(c->fd, buf, tot, 0);
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) { c->wbuf = buf; c->wlen = tot; c->woff = 0; pthread_mutex_unlock(&c->mu); return -EAGAIN; }
        int e = -errno; free(buf); pthread_mutex_unlock(&c->mu); return e;
    }
    if ((size_t)n == tot) { free(buf); pthread_mutex_unlock(&c->mu); kc_dbg("conn%p send_nb cmd=%u len=%zu ok", (void*)c, cmd, len); return 0; }
    /* Partial */
    c->wbuf = buf; c->wlen = tot; c->woff = (size_t)n; pthread_mutex_unlock(&c->mu); kc_dbg("conn%p send_nb cmd=%u len=%zu EAGAIN", (void*)c, cmd, len); return -EAGAIN;
}

/* Non-blocking staged recv: returns 0 and fills out when a full frame is ready;
 * -EAGAIN if need more data. Allocates *payload for caller to free. */
int kc_ipc_recv_nb(kc_ipc_conn_t *c, uint16_t *cmd, uint8_t **payload, size_t *len)
{
    if (!c || !cmd || !payload || !len) return -EINVAL;
    pthread_mutex_lock(&c->mu);
    /* Stage header */
    if (c->rgot < sizeof(struct kc_wire_hdr)) {
        ssize_t n = recv(c->fd, ((uint8_t*)&c->rhdr) + c->rgot, sizeof(struct kc_wire_hdr) - c->rgot, 0);
        if (n < 0) { int e = (errno == EAGAIN || errno == EWOULDBLOCK) ? -EAGAIN : -errno; pthread_mutex_unlock(&c->mu); return e; }
        if (n == 0) { pthread_mutex_unlock(&c->mu); return -ECONNRESET; }
        c->rgot += (size_t)n;
        if (c->rgot < sizeof(struct kc_wire_hdr)) { pthread_mutex_unlock(&c->mu); return -EAGAIN; }
        c->rlen = ntohl(c->rhdr.len);
        c->roff = 0;
        if (c->rlen) { c->rbuf = malloc(c->rlen); if (!c->rbuf) { c->rgot = 0; pthread_mutex_unlock(&c->mu); return -ENOMEM; } }
    }
    /* Stage payload */
    if (c->rlen) {
        ssize_t n = recv(c->fd, c->rbuf + c->roff, c->rlen - c->roff, 0);
        if (n < 0) { int e = (errno == EAGAIN || errno == EWOULDBLOCK) ? -EAGAIN : -errno; pthread_mutex_unlock(&c->mu); return e; }
        if (n == 0) { pthread_mutex_unlock(&c->mu); return -ECONNRESET; }
        c->roff += (size_t)n;
        if (c->roff < c->rlen) { pthread_mutex_unlock(&c->mu); return -EAGAIN; }
    }
    /* Full frame */
    *cmd = ntohs(c->rhdr.cmd);
    *len = c->rlen;
    *payload = c->rbuf; /* may be NULL for zero-length */
    /* Reset state for next frame */
    c->rbuf = NULL; c->rlen = 0; c->roff = 0; c->rgot = 0;
    pthread_mutex_unlock(&c->mu);
    kc_dbg("conn%p recv_nb cmd=%u len=%zu", (void*)c, *cmd, *len);
    return 0;
}

int kc_tlv_put_u32(uint8_t **cursor, uint8_t *end, uint16_t type, uint32_t v)
{
    if (!cursor || !*cursor) return -EINVAL;
    uint8_t *p = *cursor;
    if ((size_t)(end - p) < 8) return -EMSGSIZE; /* t(2)+l(2)+v(4) */
    uint16_t t = htons(type); uint16_t l = htons(4); uint32_t val = htonl(v);
    memcpy(p, &t, 2); memcpy(p+2, &l, 2); memcpy(p+4, &val, 4);
    *cursor = p+8; return 0;
}

static inline uint64_t kc_htobe64(uint64_t x)
{
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
    uint64_t hi = htonl((uint32_t)(x >> 32));
    uint64_t lo = htonl((uint32_t)(x & 0xFFFFFFFFULL));
    return (lo << 32) | hi;
#else
    return x;
#endif
}

int kc_tlv_put_u64(uint8_t **cursor, uint8_t *end, uint16_t type, uint64_t v)
{
    if (!cursor || !*cursor) return -EINVAL;
    uint8_t *p = *cursor;
    if ((size_t)(end - p) < 12) return -EMSGSIZE; /* t(2)+l(2)+v(8) */
    uint16_t t = htons(type); uint16_t l = htons(8);
    uint64_t val = kc_htobe64(v);
    memcpy(p, &t, 2); memcpy(p+2, &l, 2); memcpy(p+4, &val, 8);
    *cursor = p+12; return 0;
}

/* Simple HELLO handshake using KCORO_CMD_HELLO with ABI in TLVs */
static int send_hello(kc_ipc_conn_t *c, uint32_t major, uint32_t minor)
{
    uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_ABI_MAJOR, major)) return -EMSGSIZE;
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_ABI_MINOR, minor)) return -EMSGSIZE;
    return kc_ipc_send(c, KCORO_CMD_HELLO, buf, (size_t)(cur - buf));
}

static int parse_hello(const uint8_t *p, size_t n, uint32_t *maj, uint32_t *min)
{
    size_t off = 0; *maj = *min = 0;
    while (off + 4 <= n) {
        uint16_t t, l; memcpy(&t, p+off, 2); memcpy(&l, p+off+2, 2); t = ntohs(t); l = ntohs(l); off += 4;
        if (off + l > n) return -EMSGSIZE;
        if (t == KCORO_ATTR_ABI_MAJOR && l == 4) { uint32_t v; memcpy(&v, p+off, 4); *maj = ntohl(v); }
        else if (t == KCORO_ATTR_ABI_MINOR && l == 4) { uint32_t v; memcpy(&v, p+off, 4); *min = ntohl(v); }
        off += l;
    }
    return (*maj || *min) ? 0 : -EINVAL;
}

int kc_ipc_hs_cli(kc_ipc_conn_t *c, uint32_t *peer_major, uint32_t *peer_minor)
{
    if (!c || !peer_major || !peer_minor) return -EINVAL;
    int rc = send_hello(c, KCORO_PROTO_ABI_MAJOR, KCORO_PROTO_ABI_MINOR); if (rc) return rc;
    uint16_t cmd; uint8_t *pl = NULL; size_t n = 0;
    rc = kc_ipc_recv(c, &cmd, &pl, &n); if (rc) return rc;
    if (cmd != KCORO_CMD_HELLO) { free(pl); return -EPROTO; }
    rc = parse_hello(pl, n, peer_major, peer_minor); free(pl); kc_dbg("conn%p hs_cli ok peer=%u.%u", (void*)c, *peer_major, *peer_minor); return rc;
}

int kc_ipc_hs_srv(kc_ipc_conn_t *c, uint32_t *peer_major, uint32_t *peer_minor)
{
    if (!c || !peer_major || !peer_minor) return -EINVAL;
    uint16_t cmd; uint8_t *pl = NULL; size_t n = 0;
    int rc = kc_ipc_recv(c, &cmd, &pl, &n); if (rc) return rc;
    if (cmd != KCORO_CMD_HELLO) { free(pl); return -EPROTO; }
    rc = parse_hello(pl, n, peer_major, peer_minor); free(pl); if (rc) return rc;
    kc_dbg("conn%p hs_srv peer=%u.%u", (void*)c, *peer_major, *peer_minor);
    return send_hello(c, KCORO_PROTO_ABI_MAJOR, KCORO_PROTO_ABI_MINOR);
}
