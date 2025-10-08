// SPDX-License-Identifier: BSD-3-Clause
/**
 * @file kcoro_ipc_server.h
 * @brief Minimal server‑side dispatcher for distributed channel RPCs.
 *
 * Role
 * - Owns a simple registry of local channels (chan_id → kc_chan_t*).
 * - Decodes incoming frames (CHAN_MAKE/SEND/RECV/CLOSE/DESTROY), invokes the
 *   corresponding local operation, and replies with a result payload.
 * - Echoes `req_id` (if provided) so clients can correlate responses.
 *
 * Notes
 * - The coroutine‑native version replaces any blocking waits with park/unpark.
 * - Error codes in replies mirror local channel semantics.
 */
#pragma once

#include <stddef.h>
#include <stdint.h>

#include "kcoro_ipc_posix.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Server context for managing distributed channels */
typedef struct kc_ipc_server_ctx kc_ipc_server_ctx_t;

/**
 * Create a new server context for handling distributed channels
 * 
 * @return Server context or NULL on failure
 */
kc_ipc_server_ctx_t *kc_ipc_server_ctx_create(void);

/**
 * Destroy server context and clean up all channels
 * 
 * @param ctx Server context (can be NULL)
 */
void kc_ipc_server_ctx_destroy(kc_ipc_server_ctx_t *ctx);

/**
 * Handle an incoming IPC command from a client
 * 
 * This dispatches channel operations (CHAN_MAKE, CHAN_SEND, etc.)
 * to the appropriate handlers and manages the channel registry.
 * 
 * @param ctx Server context
 * @param conn IPC connection to client
 * @param cmd Command code (from kcoro_proto.h)
 * @param payload Command payload (TLV-encoded attributes)
 * @param len Payload length
 * @return 0 on success, negative errno on failure
 */
int kc_ipc_handle_command(kc_ipc_server_ctx_t *ctx, kc_ipc_conn_t *conn,
                         uint16_t cmd, const uint8_t *payload, size_t len);

#ifdef __cplusplus
}
#endif
