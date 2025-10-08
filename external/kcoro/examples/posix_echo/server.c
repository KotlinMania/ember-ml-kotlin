// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/epoll.h>

#include "../../../kcoro/proto/kcoro_proto.h"
#include "../../../kcoro/ipc/posix/include/kcoro_ipc_posix.h"
#include "../../../kcoro/include/kcoro_abi.h"
#include <errno.h>

static void print_tlvs(const uint8_t *p, size_t n)
{
    size_t off = 0;
    while (off + 4 <= n) {
        uint16_t t, l; memcpy(&t, p+off, 2); memcpy(&l, p+off+2, 2);
        t = ntohs(t); l = ntohs(l); off += 4;
        if (off + l > n) break;
        if (t == KCORO_ATTR_ABI_MAJOR && l == 4) {
            uint32_t v; memcpy(&v, p+off, 4); printf(" ABI_MAJOR=%u", ntohl(v));
        } else if (t == KCORO_ATTR_ABI_MINOR && l == 4) {
            uint32_t v; memcpy(&v, p+off, 4); printf(" ABI_MINOR=%u", ntohl(v));
        } else if (t == KCORO_ATTR_CAPS && l == 4) {
            uint32_t v; memcpy(&v, p+off, 4); printf(" CAPS=0x%x", ntohl(v));
        } else {
            printf(" T%u(L%u)", t, l);
        }
        off += l;
    }
    printf("\n");
}

int main(int argc, char **argv)
{
    const char *sock = (argc > 1) ? argv[1] : "/tmp/kcoro.sock";
    kc_ipc_server_t *srv = NULL; kc_ipc_conn_t *conn = NULL;
    int efd = -1; int srvfd = -1; int connfd = -1; int want_out = 0;

    if (kc_ipc_srv_listen(sock, &srv) != 0) { perror("listen"); return 1; }
    kc_ipc_srv_set_nb(srv, 1);
    srvfd = kc_ipc_srv_fd(srv);
    printf("[srv] listening on %s\n", sock);

    efd = epoll_create1(EPOLL_CLOEXEC);
    if (efd < 0) { perror("epoll_create1"); kc_ipc_srv_close(srv); return 1; }
    struct epoll_event ev = { .events = EPOLLIN, .data.fd = srvfd };
    if (epoll_ctl(efd, EPOLL_CTL_ADD, srvfd, &ev) < 0) { perror("epoll_ctl add srv"); kc_ipc_srv_close(srv); close(efd); return 1; }

    struct epoll_event events[8];
    for (;;) {
        int n = epoll_wait(efd, events, 8, -1);
        if (n < 0) { if (errno == EINTR) continue; perror("epoll_wait"); break; }
        for (int i = 0; i < n; ++i) {
            int fd = events[i].data.fd;
            uint32_t evs = events[i].events;
            if (fd == srvfd && (evs & EPOLLIN)) {
                /* accept all pending connections; keep the first and close extras */
                kc_ipc_conn_t *c = NULL;
                while (kc_ipc_srv_accept_nb(srv, &c) == 0) {
                    if (!conn) {
                        conn = c; connfd = kc_ipc_conn_fd(conn);
                        kc_ipc_conn_set_nb(conn, 1);
                        struct epoll_event cev = { .events = EPOLLIN, .data.fd = connfd };
                        epoll_ctl(efd, EPOLL_CTL_ADD, connfd, &cev);
                        printf("[srv] client connected\n");
                        /* blocking handshake (simple) */
                        uint32_t maj=0,min=0;
                        if (kc_ipc_hs_srv(conn, &maj, &min) != 0) { fprintf(stderr, "handshake failed\n"); }
                        else { printf("[srv] peer ABI: %u.%u\n", maj, min); }
                    } else {
                        /* single-client example: close extras */
                        kc_ipc_conn_close(c);
                    }
                }
            } else if (conn && fd == connfd) {
                if (evs & EPOLLIN) {
                    for (;;) {
                        uint16_t cmd; uint8_t *pl=NULL; size_t len=0;
                        int rc = kc_ipc_recv_nb(conn, &cmd, &pl, &len);
                        if (rc == -EAGAIN) break;
                        if (rc != 0) { fprintf(stderr, "[srv] recv error %d\n", rc); free(pl); goto out; }
                        printf("[srv] cmd=%u len=%zu\n", cmd, len);
                        if (cmd == KCORO_CMD_GET_INFO) {
                            uint8_t buf[64]; uint8_t *cur=buf, *end=buf+sizeof(buf);
                            kc_tlv_put_u32(&cur, end, KCORO_ATTR_ABI_MAJOR, KCORO_PROTO_ABI_MAJOR);
                            kc_tlv_put_u32(&cur, end, KCORO_ATTR_ABI_MINOR, KCORO_PROTO_ABI_MINOR);
                            kc_tlv_put_u32(&cur, end, KCORO_ATTR_CAPS, 0);
                            int s = kc_ipc_send_nb(conn, KCORO_CMD_GET_INFO, buf, (size_t)(cur-buf));
                            if (s == -EAGAIN) { want_out = 1; struct epoll_event mev={.events=EPOLLIN|EPOLLOUT,.data.fd=connfd}; epoll_ctl(efd, EPOLL_CTL_MOD, connfd, &mev); }
                        } else if (cmd == KCORO_CMD_GET_STATS) {
                            uint8_t buf[64]; uint8_t *cur=buf, *end=buf+sizeof(buf);
                            kc_tlv_put_u32(&cur, end, KCORO_ATTR_SEND_OPS, 0);
                            kc_tlv_put_u32(&cur, end, KCORO_ATTR_RECV_OPS, 0);
                            int s = kc_ipc_send_nb(conn, KCORO_CMD_GET_STATS, buf, (size_t)(cur-buf));
                            if (s == -EAGAIN) { want_out = 1; struct epoll_event mev={.events=EPOLLIN|EPOLLOUT,.data.fd=connfd}; epoll_ctl(efd, EPOLL_CTL_MOD, connfd, &mev); }
                        } else {
                            int s = kc_ipc_send_nb(conn, cmd, pl, len);
                            if (s == -EAGAIN) { want_out = 1; struct epoll_event mev={.events=EPOLLIN|EPOLLOUT,.data.fd=connfd}; epoll_ctl(efd, EPOLL_CTL_MOD, connfd, &mev); }
                        }
                        if (len) { printf("[srv] payload:"); print_tlvs(pl, len); }
                        free(pl);
                    }
                }
                if (want_out && (evs & EPOLLOUT)) {
                    int fr = kc_ipc_flush(conn);
                    if (fr == 0) {
                        want_out = 0; struct epoll_event mev={.events=EPOLLIN,.data.fd=connfd}; epoll_ctl(efd, EPOLL_CTL_MOD, connfd, &mev);
                    } else if (fr != -EAGAIN) {
                        fprintf(stderr, "[srv] flush error %d\n", fr); goto out;
                    }
                }
                if (evs & (EPOLLHUP|EPOLLERR)) {
                    fprintf(stderr, "[srv] peer closed/error\n");
                    goto out;
                }
            }
        }
    }
out:
    if (conn) kc_ipc_conn_close(conn);
    if (srv) kc_ipc_srv_close(srv);
    if (efd >= 0) close(efd);
    return 0;
}
