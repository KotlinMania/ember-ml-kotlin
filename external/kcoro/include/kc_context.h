// SPDX-License-Identifier: BSD-3-Clause
#pragma once

/**
 * @file kc_context.h
 * @brief Coroutine context key/value aggregate (structured context).
 *
 * Contexts let you annotate coroutine execution with ambient values such as
 * cancellation tokens, deadlines, dispatchers, and trace IDs. They compose
 * immutably: adding or replacing a key yields a new context layered over the
 * previous one. Lookups walk the chain. This mirrors the structured context
 * model in modern coroutine frameworks while remaining a tiny, C‑friendly API.
 *
 * Status & install guidance
 *   - Status: available surface; usage in core is optional and limited. Future
 *     phases integrate contexts more deeply with scheduling/select.
 *   - Install: safe to publish, but if you want the smallest possible surface,
 *     you may omit it from installed headers until adoption grows.
 */

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct kc_context kc_context_t; /* opaque */

typedef struct kc_ctx_key {
    const char *name; /* diagnostic only */
} kc_ctx_key_t;

typedef void (*kc_ctx_drop_fn)(void *value);

/* Built‑in key declarations (definitions in library). */
extern const kc_ctx_key_t KC_CTX_KEY_DISPATCHER;
extern const kc_ctx_key_t KC_CTX_KEY_JOB;
extern const kc_ctx_key_t KC_CTX_KEY_CANCEL;
extern const kc_ctx_key_t KC_CTX_KEY_DEADLINE;
extern const kc_ctx_key_t KC_CTX_KEY_TRACE_ID;

/* Obtain singleton empty root context (retained). */
kc_context_t* kc_context_empty(void);

/* Retain / release */
kc_context_t* kc_context_retain(kc_context_t *ctx);
void          kc_context_release(kc_context_t *ctx);

/* Add key (append or layer); returns new context with refcount 1. */
kc_context_t* kc_context_add(kc_context_t *base, const kc_ctx_key_t *key,
                             void *value, kc_ctx_drop_fn drop_fn);

/* Replace existing key value (if not found behaves like add). */
kc_context_t* kc_context_replace(kc_context_t *base, const kc_ctx_key_t *key,
                                 void *value, kc_ctx_drop_fn drop_fn);

/* Lookup (NULL if absent). */
void* kc_context_get(const kc_context_t *ctx, const kc_ctx_key_t *key);

/* Convenience: fetch deadline (ns), returns 0 on success else -ENOENT. */
int kc_context_get_deadline_ns(const kc_context_t *ctx, long long *out_ns);

/* Current executing coroutine context (borrowed, do not release). */
kc_context_t* kc_context_current(void);

/* Run function with derived context (push/pop). */
typedef void (*kcoro_fn_t)(void*); /* forward consistency */
int kc_context_with(kc_context_t *ctx, kcoro_fn_t fn, void *arg);

#ifdef __cplusplus
}
#endif
