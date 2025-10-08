// SPDX-License-Identifier: BSD-3-Clause
#ifndef KC_CANCEL_H
#define KC_CANCEL_H

#include "kcoro_port.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @file kc_cancel.h
 * @brief Hierarchical cancellation tokens for structured concurrency.
 *
 * -----------------------------------------------------------------------------
 * Header Surface & Optional Items
 * -----------------------------------------------------------------------------
 * Purpose
 *   A cancellation token represents a cooperative stop signal. Tokens can be
 *   arranged in a tree so that triggering a parent cancels all descendants.
 *   Channels’ _c variants and higher‑level constructs use these primitives.
 *
 * Optional items
 *   - KC_ECANCELED (negative): conventional error code returned by _c variants
 *     when cancellation is observed. Override is discouraged; consistency helps
 *     across the API.
 *
 * Install guidance
 *   - This header is part of the production public API and should be installed.
 */

typedef struct kc_cancel kc_cancel_t;

/* Error codes for cancellation operations */
/**
 * Cancellation observed by a _c variant.
 * Returned promptly when the token is triggered; use to distinguish cancel from
 * timeout (KC_ETIME) and pipe closure (KC_EPIPE).
 */
#define KC_ECANCELED  (-42)

/** Create a new cancellation token.
 * @param out    Pointer to store the created token
 * @param parent Optional parent token for hierarchical cancellation
 * @return 0 on success, negative KC_* on failure
 */
int kc_cancel_create(kc_cancel_t **out, kc_cancel_t *parent);

/** Destroy a cancellation token and cleanup resources. */
void kc_cancel_destroy(kc_cancel_t *cancel);

/** Trigger cancellation (propagates to all children). */
void kc_cancel_trigger(kc_cancel_t *cancel);

/** Check if a token has been cancelled (1 yes, 0 no). */
int kc_cancel_is_triggered(const kc_cancel_t *cancel);

/** Wait for cancellation with timeout.
 * @param cancel     Token to wait on
 * @param timeout_ms Timeout in milliseconds (-1 for infinite)
 * @return 0 if cancelled, KC_ETIME on timeout, negative KC_* on error
 */
int kc_cancel_wait(const kc_cancel_t *cancel, long timeout_ms);

/** Add a child token for hierarchical cancellation.
 * @return 0 on success, negative KC_* on failure
 */
int kc_cancel_add_child(kc_cancel_t *parent, kc_cancel_t *child);

/** Remove a child token from its parent. */
void kc_cancel_remove_child(kc_cancel_t *parent, kc_cancel_t *child);

#ifdef __cplusplus
}
#endif

#endif /* KC_CANCEL_H */
