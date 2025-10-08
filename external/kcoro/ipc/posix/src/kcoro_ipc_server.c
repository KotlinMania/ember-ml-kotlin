// SPDX-License-Identifier: BSD-3-Clause
/*
 * Server-side Channel Dispatcher (IPC over POSIX)
 * ----------------------------------------------
 *
 * Role
 * - Receives channel RPC frames over a POSIX socket connection, decodes TLV
 *   payloads, invokes the corresponding local channel operation, and replies.
 * - Maintains a registry of local channels (chan_id → kc_chan_t*).
 * - Echoes `req_id` in responses (when present) for client correlation.
 *
 * Notes
 * - The coroutine‑native variant replaces the condvar bridges with park/unpark
 *   while preserving exact error semantics (EAGAIN/ETIME/ECANCELED/EPIPE).
 */
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdio.h>
#include <arpa/inet.h>
#include <pthread.h>

#include "../include/kcoro_ipc_posix.h"
#include "../../../include/kcoro.h"
#include "../../../include/kcoro_sched.h"

/* Small helpers to run channel ops inside coroutine context */
struct kc_send_task { kc_chan_t* ch; void* elem; long tmo; int rc; pthread_mutex_t mu; pthread_cond_t cv; int done; };
struct kc_recv_task { kc_chan_t* ch; void* elem; long tmo; int rc; pthread_mutex_t mu; pthread_cond_t cv; int done; };

static void kc_ipc_send_co(void* arg)
{
    struct kc_send_task* s = (struct kc_send_task*)arg;
    s->rc = kc_chan_send(s->ch, s->elem, s->tmo);
    pthread_mutex_lock(&s->mu); s->done = 1; pthread_cond_signal(&s->cv); pthread_mutex_unlock(&s->mu);
}

static void kc_ipc_recv_co(void* arg)
{
    struct kc_recv_task* r = (struct kc_recv_task*)arg;
    r->rc = kc_chan_recv(r->ch, r->elem, r->tmo);
    pthread_mutex_lock(&r->mu); r->done = 1; pthread_cond_signal(&r->cv); pthread_mutex_unlock(&r->mu);
}
#include "../../../proto/kcoro_proto.h"

/* Server channel registry */
struct kc_chan_entry {
    uint32_t id;
    kc_chan_t *chan;
    int kind;
    size_t elem_sz;
    struct kc_chan_entry *next;
};

/* Server context */
typedef struct kc_ipc_server_ctx {
    struct kc_chan_entry *channels;  /* Channel registry */
    uint32_t next_chan_id;           /* Next channel ID to assign */
} kc_ipc_server_ctx_t;

/* Parse TLV attributes from payload */
static int parse_tlv_u32(const uint8_t *payload, size_t len, uint16_t attr_type, uint32_t *out)
{
    size_t off = 0;
    while (off + 4 <= len) {
        uint16_t t, l;
        memcpy(&t, payload + off, 2);
        memcpy(&l, payload + off + 2, 2);
        t = ntohs(t);
        l = ntohs(l);
        off += 4;
        
        if (off + l > len) break;
        if (t == attr_type && l == 4) {
            uint32_t v;
            memcpy(&v, payload + off, 4);
            *out = ntohl(v);
            return 0;
        }
        off += l;
    }
    return -1;
}

/* Parse element data from TLV */
static int parse_tlv_element(const uint8_t *payload, size_t len, void *out, size_t elem_sz)
{
    size_t off = 0;
    while (off + 4 <= len) {
        uint16_t t, l;
        memcpy(&t, payload + off, 2);
        memcpy(&l, payload + off + 2, 2);
        t = ntohs(t);
        l = ntohs(l);
        off += 4;
        
        if (off + l > len) break;
        if (t == KCORO_ATTR_ELEMENT && l == elem_sz) {
            memcpy(out, payload + off, elem_sz);
            return 0;
        }
        off += l;
    }
    return -1;
}

/* Find channel by ID */
static struct kc_chan_entry *find_channel(kc_ipc_server_ctx_t *ctx, uint32_t chan_id)
{
    for (struct kc_chan_entry *e = ctx->channels; e; e = e->next) {
        if (e->id == chan_id) return e;
    }
    return NULL;
}

/* Handle CHAN_MAKE command */
static int handle_chan_make(kc_ipc_server_ctx_t *ctx, kc_ipc_conn_t *conn, 
                           const uint8_t *payload, size_t len)
{
    uint32_t kind = KC_RENDEZVOUS, elem_sz = 0, capacity = 0;
    
    /* Parse parameters */
    parse_tlv_u32(payload, len, KCORO_ATTR_KIND, &kind);
    parse_tlv_u32(payload, len, KCORO_ATTR_ELEM_SIZE, &elem_sz);  
    parse_tlv_u32(payload, len, KCORO_ATTR_CAPACITY, &capacity);
    
    if (elem_sz == 0 || elem_sz > 65536) {
        return -EINVAL; /* Reasonable size limits */
    }
    
    /* Create local channel */
    kc_chan_t *chan = NULL;
    int rc = kc_chan_make(&chan, (int)kind, elem_sz, capacity);
    if (rc != 0) return rc;
    
    /* Add to registry */
    struct kc_chan_entry *entry = malloc(sizeof(*entry));
    if (!entry) {
        kc_chan_destroy(chan);
        return -ENOMEM;
    }
    
    entry->id = ++ctx->next_chan_id;
    entry->chan = chan;
    entry->kind = (int)kind;
    entry->elem_sz = elem_sz;
    entry->next = ctx->channels;
    ctx->channels = entry;
    
    /* Send response with channel ID (echo req_id if present) */
    uint8_t buf[32];
    uint8_t *cur = buf, *end = buf + sizeof(buf);
    {
        uint32_t req_id = 0; (void)parse_tlv_u32(payload, len, KCORO_ATTR_REQ_ID, &req_id);
        if (req_id) { (void)kc_tlv_put_u32(&cur, end, KCORO_ATTR_REQ_ID, req_id); }
    }
    
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_CHAN_ID, entry->id) != 0) {
        return -EMSGSIZE;
    }
    
    return kc_ipc_send(conn, KCORO_CMD_CHAN_MAKE, buf, (size_t)(cur - buf));
}

/* Handle CHAN_SEND command */
static int handle_chan_send(kc_ipc_server_ctx_t *ctx, kc_ipc_conn_t *conn,
                           const uint8_t *payload, size_t len)
{
    uint32_t chan_id = 0, timeout_ms = 0;
    
    /* Parse parameters */
    if (parse_tlv_u32(payload, len, KCORO_ATTR_CHAN_ID, &chan_id) != 0) {
        /* Respond with error */
        uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)-EINVAL);
        return kc_ipc_send(conn, KCORO_CMD_CHAN_SEND, buf, (size_t)(cur - buf));
    }
    parse_tlv_u32(payload, len, KCORO_ATTR_TIMEOUT_MS, &timeout_ms);
    
    /* Find channel */
    struct kc_chan_entry *entry = find_channel(ctx, chan_id);
    if (!entry) {
        uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)-ENOENT);
        return kc_ipc_send(conn, KCORO_CMD_CHAN_SEND, buf, (size_t)(cur - buf));
    }
    
    /* Extract element data */
    void *element = malloc(entry->elem_sz);
    if (!element) return -ENOMEM;
    
    int rc = parse_tlv_element(payload, len, element, entry->elem_sz);
    if (rc != 0) {
        free(element);
        uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)-EINVAL);
        return kc_ipc_send(conn, KCORO_CMD_CHAN_SEND, buf, (size_t)(cur - buf));
    }
    
    /* Perform send operation inside coroutine context */
    struct kc_send_task st = {
        .ch = entry->chan, .elem = element, .tmo = (long)timeout_ms, .rc = 0, .done = 0
    };
    pthread_mutex_init(&st.mu, NULL); pthread_cond_init(&st.cv, NULL);
    kc_sched_t *sched = kc_sched_default();
    if (kc_spawn_co(sched, kc_ipc_send_co, &st, 0, NULL) != 0) { pthread_mutex_destroy(&st.mu); pthread_cond_destroy(&st.cv); free(element); return -ENOMEM; }
    pthread_mutex_lock(&st.mu); while (!st.done) pthread_cond_wait(&st.cv, &st.mu); pthread_mutex_unlock(&st.mu);
    rc = st.rc;
    pthread_cond_destroy(&st.cv); pthread_mutex_destroy(&st.mu);
    free(element);
    
    /* Send result back (echo req_id if present) */
    uint8_t buf[32];
    uint8_t *cur = buf, *end = buf + sizeof(buf);
    {
        uint32_t req_id = 0; (void)parse_tlv_u32(payload, len, KCORO_ATTR_REQ_ID, &req_id);
        if (req_id) { (void)kc_tlv_put_u32(&cur, end, KCORO_ATTR_REQ_ID, req_id); }
    }
    
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)rc) != 0) {
        return -EMSGSIZE;
    }
    
    return kc_ipc_send(conn, KCORO_CMD_CHAN_SEND, buf, (size_t)(cur - buf));
}

/* Handle CHAN_RECV command */
static int handle_chan_recv(kc_ipc_server_ctx_t *ctx, kc_ipc_conn_t *conn,
                           const uint8_t *payload, size_t len)
{
    uint32_t chan_id = 0, timeout_ms = 0;
    
    /* Parse parameters */
    if (parse_tlv_u32(payload, len, KCORO_ATTR_CHAN_ID, &chan_id) != 0) {
        uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)-EINVAL);
        return kc_ipc_send(conn, KCORO_CMD_CHAN_RECV, buf, (size_t)(cur - buf));
    }
    parse_tlv_u32(payload, len, KCORO_ATTR_TIMEOUT_MS, &timeout_ms);
    
    /* Find channel */
    struct kc_chan_entry *entry = find_channel(ctx, chan_id);
    if (!entry) {
        uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)-ENOENT);
        return kc_ipc_send(conn, KCORO_CMD_CHAN_RECV, buf, (size_t)(cur - buf));
    }
    
    /* Allocate buffer for received element */
    void *element = malloc(entry->elem_sz);
    if (!element) return -ENOMEM;
    
    /* Perform receive operation inside coroutine context */
    struct kc_recv_task rt = {
        .ch = entry->chan, .elem = element, .tmo = (long)timeout_ms, .rc = 0, .done = 0
    };
    pthread_mutex_init(&rt.mu, NULL); pthread_cond_init(&rt.cv, NULL);
    kc_sched_t *sched = kc_sched_default();
    if (kc_spawn_co(sched, kc_ipc_recv_co, &rt, 0, NULL) != 0) { pthread_mutex_destroy(&rt.mu); pthread_cond_destroy(&rt.cv); free(element); return -ENOMEM; }
    pthread_mutex_lock(&rt.mu); while (!rt.done) pthread_cond_wait(&rt.cv, &rt.mu); pthread_mutex_unlock(&rt.mu);
    int rc = rt.rc;
    pthread_cond_destroy(&rt.cv); pthread_mutex_destroy(&rt.mu);
    
    /* Prepare response (echo req_id if present) */
    size_t resp_size = 32 + entry->elem_sz;
    uint8_t *resp_buf = malloc(resp_size);
    if (!resp_buf) {
        free(element);
        return -ENOMEM;
    }
    
    uint8_t *cur = resp_buf, *end = resp_buf + resp_size;
    
    /* Add req_id and result code */
    {
        uint32_t req_id = 0; (void)parse_tlv_u32(payload, len, KCORO_ATTR_REQ_ID, &req_id);
        if (req_id) { (void)kc_tlv_put_u32(&cur, end, KCORO_ATTR_REQ_ID, req_id); }
    }
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)rc) != 0) {
        free(element);
        free(resp_buf);
        return -EMSGSIZE;
    }
    
    /* Add element data if successful */
    if (rc == 0) {
        if ((size_t)(end - cur) < 4 + entry->elem_sz) {
            free(element);
            free(resp_buf);
            return -EMSGSIZE;
        }
        
        uint16_t t = htons(KCORO_ATTR_ELEMENT);
        uint16_t l = htons((uint16_t)entry->elem_sz);
        memcpy(cur, &t, 2);
        memcpy(cur + 2, &l, 2);
        memcpy(cur + 4, element, entry->elem_sz);
        cur += 4 + entry->elem_sz;
    }
    
    rc = kc_ipc_send(conn, KCORO_CMD_CHAN_RECV, resp_buf, (size_t)(cur - resp_buf));
    
    free(element);
    free(resp_buf);
    return rc;
}

/* Handle CHAN_CLOSE command */
static int handle_chan_close(kc_ipc_server_ctx_t *ctx, kc_ipc_conn_t *conn,
                            const uint8_t *payload, size_t len)
{
    uint32_t chan_id = 0;
    
    if (parse_tlv_u32(payload, len, KCORO_ATTR_CHAN_ID, &chan_id) != 0) {
        uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
        {
            uint32_t req_id = 0; (void)parse_tlv_u32(payload, len, KCORO_ATTR_REQ_ID, &req_id);
            if (req_id) { (void)kc_tlv_put_u32(&cur, end, KCORO_ATTR_REQ_ID, req_id); }
        }
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)-EINVAL);
        return kc_ipc_send(conn, KCORO_CMD_CHAN_CLOSE, buf, (size_t)(cur - buf));
    }
    
    struct kc_chan_entry *entry = find_channel(ctx, chan_id);
    if (!entry) {
        uint8_t buf[32]; uint8_t *cur = buf, *end = buf + sizeof(buf);
        {
            uint32_t req_id = 0; (void)parse_tlv_u32(payload, len, KCORO_ATTR_REQ_ID, &req_id);
            if (req_id) { (void)kc_tlv_put_u32(&cur, end, KCORO_ATTR_REQ_ID, req_id); }
        }
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, (uint32_t)-ENOENT);
        return kc_ipc_send(conn, KCORO_CMD_CHAN_CLOSE, buf, (size_t)(cur - buf));
    }
    
    kc_chan_close(entry->chan);
    
    /* Send acknowledgment (echo req_id) */
    uint8_t buf[32];
    uint8_t *cur = buf, *end = buf + sizeof(buf);
    {
        uint32_t req_id = 0; (void)parse_tlv_u32(payload, len, KCORO_ATTR_REQ_ID, &req_id);
        if (req_id) { (void)kc_tlv_put_u32(&cur, end, KCORO_ATTR_REQ_ID, req_id); }
    }
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_RESULT, 0) != 0) {
        return -EMSGSIZE;
    }
    
    return kc_ipc_send(conn, KCORO_CMD_CHAN_CLOSE, buf, (size_t)(cur - buf));
}

/* Handle CHAN_DESTROY command */
static int handle_chan_destroy(kc_ipc_server_ctx_t *ctx, kc_ipc_conn_t *conn,
                              const uint8_t *payload, size_t len)
{
    (void)conn; /* unused parameter */
    uint32_t chan_id = 0;
    
    if (parse_tlv_u32(payload, len, KCORO_ATTR_CHAN_ID, &chan_id) != 0) {
        return 0; /* ignore */
    }
    /* Do not remove the channel immediately; just close it to allow draining */
    struct kc_chan_entry *entry = find_channel(ctx, chan_id);
    if (entry && entry->chan) {
        kc_chan_close(entry->chan);
    }
    return 0; /* best effort */
}

/* Main command dispatcher for kcoro server */
int kc_ipc_handle_command(kc_ipc_server_ctx_t *ctx, kc_ipc_conn_t *conn,
                         uint16_t cmd, const uint8_t *payload, size_t len)
{
    switch (cmd) {
        case KCORO_CMD_CHAN_MAKE:
            return handle_chan_make(ctx, conn, payload, len);
        case KCORO_CMD_CHAN_SEND:
        case KCORO_CMD_CHAN_TRY_SEND: /* Same handler, timeout differentiates */
            return handle_chan_send(ctx, conn, payload, len);
        case KCORO_CMD_CHAN_RECV:
        case KCORO_CMD_CHAN_TRY_RECV: /* Same handler, timeout differentiates */
            return handle_chan_recv(ctx, conn, payload, len);
        case KCORO_CMD_CHAN_CLOSE:
            return handle_chan_close(ctx, conn, payload, len);
        case KCORO_CMD_CHAN_DESTROY:
            return handle_chan_destroy(ctx, conn, payload, len);
        default:
            return -ENOSYS; /* Unsupported command */
    }
}

/* Create server context */
kc_ipc_server_ctx_t *kc_ipc_server_ctx_create(void)
{
    kc_ipc_server_ctx_t *ctx = calloc(1, sizeof(*ctx));
    if (ctx) {
        ctx->next_chan_id = 1000; /* Start IDs from 1000 */
    }
    return ctx;
}

/* Destroy server context */
void kc_ipc_server_ctx_destroy(kc_ipc_server_ctx_t *ctx)
{
    if (!ctx) return;
    
    /* Clean up all channels */
    while (ctx->channels) {
        struct kc_chan_entry *entry = ctx->channels;
        ctx->channels = entry->next;
        kc_chan_destroy(entry->chan);
        free(entry);
    }
    
    free(ctx);
}
