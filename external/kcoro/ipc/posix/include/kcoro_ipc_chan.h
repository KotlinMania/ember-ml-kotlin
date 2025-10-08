// SPDX-License-Identifier: BSD-3-Clause
#pragma once

#include <stddef.h>
#include <stdint.h>

#include "kcoro_ipc_posix.h"
#include "../../../include/kcoro.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Distributed channel handle (opaque) */
typedef struct kc_ipc_chan kc_ipc_chan_t;

/* Distributed Channel API - Kotlin-like patterns over IPC */

/**
 * Create a distributed channel over IPC connection
 * 
 * Similar to Kotlin's Channel(capacity, onBufferOverflow) but distributed
 * 
 * @param conn IPC connection to remote kcoro server
 * @param kind Channel kind (KC_RENDEZVOUS, KC_BUFFERED, etc.)
 * @param elem_sz Size of each element in bytes
 *                 Note: TLV transport limits a single element to <= 65535 bytes.
 * @param capacity Buffer capacity
 * @param out Output handle to created distributed channel
 * @return 0 on success, negative errno on failure
 */
int kc_ipc_chan_make(kc_ipc_conn_t *conn, int kind, size_t elem_sz, 
                     size_t capacity, kc_ipc_chan_t **out);

/**
 * Open an existing distributed channel by ID
 *
 * Attaches to a channel previously created on the server. The caller must
 * know the `chan_id`, `kind`, and `elem_sz` (e.g., exchanged out-of-band).
 */
int kc_ipc_chan_open(kc_ipc_conn_t *conn, uint32_t chan_id, int kind,
                     size_t elem_sz, kc_ipc_chan_t **out);

/**
 * Get the remote channel ID associated with this handle.
 */
uint32_t kc_ipc_chan_id(kc_ipc_chan_t *ich);

/**
 * Send element to distributed channel (suspending)
 * 
 * Equivalent to Kotlin's channel.send(element)
 * Suspends until element can be sent or timeout occurs
 * 
 * @param ich Distributed channel handle
 * @param msg Element to send (must be exactly elem_sz bytes)
 * @param timeout_ms Timeout in milliseconds (-1 = infinite, 0 = non-blocking)
 * @return 0 on success, KC_EAGAIN if would block, KC_ETIME on timeout, KC_EPIPE if closed
 */
int kc_ipc_chan_send(kc_ipc_chan_t *ich, const void *msg, long timeout_ms);

/**
 * Receive element from distributed channel (suspending)
 * 
 * Equivalent to Kotlin's channel.receive()
 * Suspends until element is available or timeout occurs
 * 
 * @param ich Distributed channel handle  
 * @param out Buffer to store received element (must be at least elem_sz bytes)
 * @param timeout_ms Timeout in milliseconds (-1 = infinite, 0 = non-blocking)
 * @return 0 on success, KC_EAGAIN if would block, KC_ETIME on timeout, KC_EPIPE if closed
 */
int kc_ipc_chan_recv(kc_ipc_chan_t *ich, void *out, long timeout_ms);

/**
 * Non-blocking send to distributed channel
 * 
 * Equivalent to Kotlin's channel.trySend(element)
 * Never suspends, returns immediately
 * 
 * @param ich Distributed channel handle
 * @param msg Element to send
 * @return 0 on success, KC_EAGAIN if channel full, KC_EPIPE if closed
 */
int kc_ipc_chan_try_send(kc_ipc_chan_t *ich, const void *msg);

/**
 * Non-blocking receive from distributed channel
 * 
 * Equivalent to Kotlin's channel.tryReceive()
 * Never suspends, returns immediately
 * 
 * @param ich Distributed channel handle
 * @param out Buffer for received element
 * @return 0 on success, KC_EAGAIN if channel empty, KC_EPIPE if closed
 */
int kc_ipc_chan_try_recv(kc_ipc_chan_t *ich, void *out);

/**
 * Close distributed channel for sending
 * 
 * Equivalent to Kotlin's channel.close()
 * 
 * @param ich Distributed channel handle
 * @return 0 on success, negative errno on failure
 */
int kc_ipc_chan_close(kc_ipc_chan_t *ich);

/**
 * Destroy distributed channel handle
 * 
 * Frees local resources and notifies remote side
 * 
 * @param ich Distributed channel handle (can be NULL)
 */
void kc_ipc_chan_destroy(kc_ipc_chan_t *ich);

#ifdef __cplusplus
}
#endif
