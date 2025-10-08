// SPDX-License-Identifier: BSD-3-Clause
#pragma once

/**
 * @file kc_job.h
 * @brief Structured concurrency job API (draft).
 *
 * Jobs generalize hierarchical cancellation and lifetime management. A parent
 * owns child jobs; cancelling the parent propagates downstream. Joining a job
 * waits for completion and returns a result code. This API sketches the stable
 * surface we will finalize in Phase S3.
 *
 * Status & install guidance
 *   - Status: design documented; implementation pending. The core library does
 *     not depend on these APIs yet.
 *   - Install: treat this header as draft/experimental. It can be excluded from
 *     "installed" headers until implementation lands, to keep the production
 *     surface crisp.
 */

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct kc_job kc_job_t; /* opaque */

typedef enum {
    KC_JOB_ACTIVE = 0,
    KC_JOB_CANCELLING,
    KC_JOB_CANCELLED,
    KC_JOB_COMPLETED,
    KC_JOB_FAILED
} kc_job_state_t;

/* Flags for job creation */
/**
 * Supervisor job: child failure does not cancel siblings.
 * Useful for “one bad apple shouldn’t end the orchard” supervision trees
 * where siblings continue despite localized failure.
 */
#define KC_JOB_F_SUPERVISOR   (1u<<0)
/**
 * Detached job: not tracked by parent for joins.
 * Lifetime is managed externally; parent shutdown will not wait on it unless
 * an explicit join is later performed.
 */
#define KC_JOB_F_DETACHED     (1u<<1)
/**
 * Internal timeout wrapper flag.
 * Used by the implementation to tag jobs created by timeouts; not a public
 * policy bit for application code.
 */
#define KC_JOB_F_TIMEOUT_WRP  (1u<<2)

/* Create child job (internal use; public launch helpers will wrap). */
int kc_job_create(kc_job_t **out, kc_job_t *parent, unsigned flags);

/* Increment external reference (deferred handle / API consumer). */
kc_job_t* kc_job_retain(kc_job_t *j);

/* Release external reference; object freed when refcount hits 0. */
void kc_job_release(kc_job_t *j);

/* Current executing job (NULL if not in coroutine context). */
kc_job_t* kc_job_current(void);

/* Request cancellation (reason is errno-style; 0 => generic). */
int kc_job_cancel(kc_job_t *j, int reason);

/* Join (wait until terminal). Returns 0 on success, <0 on error (failure/cancel). */
int kc_job_join(kc_job_t *j, int *out_result_code);

/* Query state (non-blocking). */
kc_job_state_t kc_job_state(const kc_job_t *j);

/* Retrieve failure/cancel code after terminal state. */
int kc_job_result_code(const kc_job_t *j);

/* Launch a coroutine bound to a new child job (stack_size=0 => default). */
struct kc_context; /* forward */
typedef void (*kcoro_fn_t)(void*); /* reuse existing typedef location later */
typedef struct kc_deferred kc_deferred_t; /* forward for async handles */
int kc_job_launch(kc_job_t *parent, struct kc_context *ctx, kcoro_fn_t fn, void *arg,
                  size_t stack_size, kc_job_t **out_job);

#ifdef __cplusplus
}
#endif
