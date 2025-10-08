// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>

#include "../../../kcoro/proto/kcoro_proto.h"
#include "../../../kcoro/ipc/posix/include/kcoro_ipc_posix.h"

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
        } else if (t == KCORO_ATTR_SEND_OPS && l == 4) {
            uint32_t v; memcpy(&v, p+off, 4); printf(" SEND_OPS=%u", ntohl(v));
        } else if (t == KCORO_ATTR_RECV_OPS && l == 4) {
            uint32_t v; memcpy(&v, p+off, 4); printf(" RECV_OPS=%u", ntohl(v));
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
    kc_ipc_conn_t *conn = NULL;
    if (kc_ipc_connect(sock, &conn) != 0) { perror("connect"); return 1; }
    uint32_t maj=0,min=0; 
    if (kc_ipc_hs_cli(conn, &maj, &min) != 0) { fprintf(stderr, "handshake failed\n"); kc_ipc_conn_close(conn); return 1; }
    printf("[cli] server ABI: %u.%u\n", maj, min);

    /* GET_INFO */
    kc_ipc_send(conn, KCORO_CMD_GET_INFO, NULL, 0);
    uint16_t cmd; uint8_t *pl=NULL; size_t len=0;
    if (kc_ipc_recv(conn, &cmd, &pl, &len) == 0) {
        printf("[cli] GET_INFO reply cmd=%u len=%zu\n", cmd, len);
        if (len) { printf("[cli] payload:"); print_tlvs(pl, len); }
        free(pl);
    }

    /* GET_STATS */
    kc_ipc_send(conn, KCORO_CMD_GET_STATS, NULL, 0);
    if (kc_ipc_recv(conn, &cmd, &pl, &len) == 0) {
        printf("[cli] GET_STATS reply cmd=%u len=%zu\n", cmd, len);
        if (len) { printf("[cli] payload:"); print_tlvs(pl, len); }
        free(pl);
    }

    kc_ipc_conn_close(conn);
    return 0;
}
