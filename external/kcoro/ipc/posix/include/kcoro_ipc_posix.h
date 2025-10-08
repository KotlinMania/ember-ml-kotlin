// SPDX-License-Identifier: BSD-3-Clause
/**
 * @file kcoro_ipc_posix.h
 * @brief POSIX domain-socket transport for kcoro IPC (full‑duplex, frame‑based).
 *
 * Purpose
 * - Provide a small, portable, full‑duplex transport that carries channel RPCs
 *   across processes using a simple frame header + TLV payloads.
 * - Keep the transport self‑contained and free of channel semantics to preserve
 *   clean layering (channels remain pure library logic).
 *
 * Framing
 * - Wire header (versioned) + TLV payload. Request/response correlation is
 *   handled by `req_id` TLV; servers echo the same `req_id` so clients can
 *   complete the correct awaiter.
 *
 * Semantics
 * - Transport preserves channel error codes (EAGAIN/ETIME/ECANCELED/EPIPE).
 * - One connection is full‑duplex; callers should not assume symmetric blocking
 *   semantics (non‑blocking I/O with yield/park is preferred for integration).
 */
#pragma once

#include <stddef.h>
#include <stdint.h>

#include "../../../proto/kcoro_proto.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct kc_ipc_server kc_ipc_server_t;
typedef struct kc_ipc_conn   kc_ipc_conn_t;

/* Server lifecycle (listening endpoint, accepts client connections). */
int  kc_ipc_srv_listen(const char *sock_path, kc_ipc_server_t **out);
int  kc_ipc_srv_accept(kc_ipc_server_t *srv, kc_ipc_conn_t **out);
void kc_ipc_srv_close(kc_ipc_server_t *srv);

/* Non‑blocking helpers for servers (best‑effort; return -EAGAIN if no progress). */
int  kc_ipc_srv_set_nb(kc_ipc_server_t *srv, int nb_on);
int  kc_ipc_srv_accept_nb(kc_ipc_server_t *srv, kc_ipc_conn_t **out); /* -EAGAIN if none */
int  kc_ipc_srv_fd(kc_ipc_server_t *srv); /* for epoll/kqueue */

/* Client lifecycle (active connect). */
int  kc_ipc_connect(const char *sock_path, kc_ipc_conn_t **out);

/* Handshake (version exchange). Returns 0 on success; fills peer ABI. */
int  kc_ipc_hs_cli(kc_ipc_conn_t *c, uint32_t *peer_major, uint32_t *peer_minor);
int  kc_ipc_hs_srv(kc_ipc_conn_t *c, uint32_t *peer_major, uint32_t *peer_minor);

/* Message send/recv (TLV‑encoded payload). Allocates *payload on recv; caller frees. */
int  kc_ipc_send(kc_ipc_conn_t *c, uint16_t cmd, const void *payload, size_t len);
int  kc_ipc_recv(kc_ipc_conn_t *c, uint16_t *cmd, uint8_t **payload, size_t *len);

/* Close connection and free resources. */
void kc_ipc_conn_close(kc_ipc_conn_t *c);

/* Non‑blocking helpers for connections. */
int  kc_ipc_conn_set_nb(kc_ipc_conn_t *c, int nb_on);
int  kc_ipc_conn_fd(kc_ipc_conn_t *c); /* for epoll/kqueue */

/* Frame‑based non‑blocking I/O with internal state (staged buffers). */
int  kc_ipc_send_nb(kc_ipc_conn_t *c, uint16_t cmd, const void *payload, size_t len);
int  kc_ipc_flush(kc_ipc_conn_t *c); /* attempt to flush pending write; -EAGAIN if still pending */
int  kc_ipc_recv_nb(kc_ipc_conn_t *c, uint16_t *cmd, uint8_t **payload, size_t *len);

/* TLV helpers (encode into a flat buffer). */
int  kc_tlv_put_u32(uint8_t **cursor, uint8_t *end, uint16_t type, uint32_t v);
int  kc_tlv_put_u64(uint8_t **cursor, uint8_t *end, uint16_t type, uint64_t v);

/* No alias layer; short names are canonical. */

#ifdef __cplusplus
}
#endif
