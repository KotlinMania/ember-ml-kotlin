// SPDX-License-Identifier: BSD-3-Clause
#ifndef KC_CHAN_ENHANCED_H
#define KC_CHAN_ENHANCED_H

#include "kcoro_port.h"
#include "kc_cancel.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Enhanced channel types from lost functionality (commit 2fb037d)
 * Provides Kotlin-style channel semantics with performance optimizations
 */

typedef struct kc_chan_enhanced kc_chan_enhanced_t;

/* Enhanced channel kinds */
enum kc_chan_kind {
    KC_BUFFERED = 0,        /* Standard buffered channel */
    KC_RENDEZVOUS = -1,     /* Synchronous handoff */
    KC_CONFLATED = -2,      /* Always latest value */
    KC_UNLIMITED = -3       /* Auto-growing buffer */
};

/**
 * Create an enhanced channel with specific semantics
 * @param out Pointer to store the created channel
 * @param kind Channel type (KC_BUFFERED, KC_RENDEZVOUS, KC_CONFLATED, KC_UNLIMITED)
 * @param elem_sz Size of each element in bytes
 * @param capacity Buffer capacity (ignored for RENDEZVOUS/CONFLATED)
 * @return 0 on success, negative error code on failure
 */
int kc_chan_enhanced_create(kc_chan_enhanced_t **out, enum kc_chan_kind kind, 
                           size_t elem_sz, size_t capacity);

/**
 * Send to enhanced channel
 * @param chan Channel to send to
 * @param msg Message to send
 * @param timeout_ms Timeout in milliseconds (-1 for infinite, 0 for non-blocking)
 * @return 0 on success, KC_EAGAIN if non-blocking and would block, KC_EPIPE if closed
 */
int kc_chan_enhanced_send(kc_chan_enhanced_t *chan, const void *msg, long timeout_ms);

/**
 * Send to enhanced channel with cancellation support
 * @param chan Channel to send to
 * @param msg Message to send
 * @param timeout_ms Timeout in milliseconds
 * @param cancel Cancellation token (optional)
 * @return 0 on success, KC_ECANCELED if cancelled, KC_ETIME on timeout
 */
int kc_chan_enhanced_send_c(kc_chan_enhanced_t *chan, const void *msg, 
                           long timeout_ms, const kc_cancel_t *cancel);

/**
 * Receive from enhanced channel
 * @param chan Channel to receive from
 * @param msg Buffer to store received message
 * @param timeout_ms Timeout in milliseconds (-1 for infinite, 0 for non-blocking)
 * @return 0 on success, KC_EAGAIN if non-blocking and would block, KC_EPIPE if closed
 */
int kc_chan_enhanced_recv(kc_chan_enhanced_t *chan, void *msg, long timeout_ms);

/**
 * Receive from enhanced channel with cancellation support
 * @param chan Channel to receive from
 * @param msg Buffer to store received message
 * @param timeout_ms Timeout in milliseconds
 * @param cancel Cancellation token (optional)
 * @return 0 on success, KC_ECANCELED if cancelled, KC_ETIME on timeout
 */
int kc_chan_enhanced_recv_c(kc_chan_enhanced_t *chan, void *msg, 
                           long timeout_ms, const kc_cancel_t *cancel);

/**
 * Close the channel
 * @param chan Channel to close
 */
void kc_chan_enhanced_close(kc_chan_enhanced_t *chan);

/**
 * Get current channel length (number of buffered elements)
 * @param chan Channel to query
 * @return Number of elements currently in channel
 */
size_t kc_chan_enhanced_len(kc_chan_enhanced_t *chan);

/**
 * Get channel capacity
 * @param chan Channel to query  
 * @return Channel capacity (0 for rendezvous, SIZE_MAX for unlimited)
 */
size_t kc_chan_enhanced_cap(kc_chan_enhanced_t *chan);

/**
 * Check if channel is closed
 * @param chan Channel to check
 * @return 1 if closed, 0 if open
 */
int kc_chan_enhanced_is_closed(kc_chan_enhanced_t *chan);

/**
 * Destroy the channel and free resources
 * @param chan Channel to destroy
 */
void kc_chan_enhanced_destroy(kc_chan_enhanced_t *chan);

/* Performance monitoring */
typedef struct {
    size_t total_sends;
    size_t total_recvs;
    size_t send_waits;
    size_t recv_waits;
    size_t peak_length;
} kc_chan_stats_t;

/**
 * Get channel performance statistics
 * @param chan Channel to query
 * @param stats Output buffer for statistics
 * @return 0 on success, negative on error
 */
int kc_chan_enhanced_stats(kc_chan_enhanced_t *chan, kc_chan_stats_t *stats);

#ifdef __cplusplus
}
#endif

#endif /* KC_CHAN_ENHANCED_H */
