// SPDX-License-Identifier: BSD-3-Clause
/**
 * @file kcoro_sched.h
 * @brief Cooperative work‑stealing scheduler and coroutine spawn API.
 *
 * -----------------------------------------------------------------------------
 * Header Surface & Optional Items
 * -----------------------------------------------------------------------------
 * Purpose
 *   Portable scheduler facade with a default instance, task/coroutine launch,
 *   and light statistics. Keeps the core scheduling model small and clear.
 *
 * Optional tunables
 *   - KC_SCHED_STEAL_SCAN_MAX
 *     Compile‑time bound on the number of deques probed during a steal. Lower
 *     values reduce probe cost; higher values can improve fairness under skew.
 *     This is a harmless, overridable macro and can be left at its default.
 *
 * Install guidance
 *   - This header is part of the production public API and should be installed.
 */
#pragma once

#include <stddef.h>
#include "kcoro_core.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Unified cooperative work-stealing scheduler API.
 * Implementation resides in kc_sched.c (merged work-stealing design).
 */

typedef struct kc_sched kc_sched_t;   /* opaque */

/* Task entrypoint: runs on a worker thread. */
typedef void (*kc_task_fn)(void *arg);

typedef struct kc_sched_opts {
    int  workers;        /* number of worker threads (<=0 => auto) */
    int  queue_capacity; /* optional, 0 => unbounded (legacy placeholder) */
    int  inject_q_cap;   /* optional global inject queue capacity (0 => default) */
} kc_sched_opts_t;

/** Create and start a scheduler with a worker pool. */
kc_sched_t* kc_sched_init(const kc_sched_opts_t *opts);

/** Stop workers and free scheduler. Blocks until all workers exit. */
void kc_sched_shutdown(kc_sched_t *s);

/** Spawn a task on the scheduler. Returns 0 on success. */
int kc_spawn(kc_sched_t *s, kc_task_fn fn, void *arg);

/** Yield CPU to allow other tasks to run. */
void kc_yield(void);

/* Sleep helper for tasks. If called from a coroutine running on a kcoro
 * worker, this is cooperative (parks the coroutine and wakes it later without
 * blocking a worker thread). Otherwise falls back to thread sleep. */
void kc_sleep_ms(int ms);

/* Extended: coroutine-aware scheduling */

/** Get or start a default scheduler instance. */
kc_sched_t* kc_sched_default(void);

/** Spawn a coroutine on the scheduler (M:N). out_co optional. */
int kc_spawn_co(kc_sched_t* s, kcoro_fn_t fn, void* arg, size_t stack_size, kcoro_t** out_co);

/** Enqueue a coroutine to be resumed by the scheduler. */
void kc_sched_enqueue_ready(kc_sched_t* s, kcoro_t* co);

/** Scheduler bound to the current worker thread, if any. */
kc_sched_t* kc_sched_current(void);

/* -------------------- Statistics (from former v2) -------------------- */
typedef struct kc_sched_stats {
    unsigned long tasks_submitted;
    unsigned long tasks_completed;
    unsigned long steals_probes;
    unsigned long steals_succeeded;
    unsigned long steals_failures;
    unsigned long fastpath_hits;
    unsigned long fastpath_misses;
    unsigned long inject_pulls;
    unsigned long donations;
} kc_sched_stats_t;

/** Obtain a snapshot of scheduler counters (best‑effort, racy). */
void kc_sched_get_stats(kc_sched_t *s, kc_sched_stats_t *out);

/* Steal scan tunable (was KC_SCHED2_STEAL_SCAN_MAX during migration) */
/**
 * @brief Upper bound on victim deques probed during a steal attempt.
 * Lower values reduce probe cost; higher may improve fairness under skew.
 */
#ifndef KC_SCHED_STEAL_SCAN_MAX
#define KC_SCHED_STEAL_SCAN_MAX 4
#endif

/**
 * @brief Best‑effort drain to a quiescent state.
 * Waits until ready queues and deques are empty and workers appear idle, or
 * until timeout_ms elapses. Returns 0 if quiescent observed, KC_ETIME if timed out.
 */
int kc_sched_drain(kc_sched_t *s, long timeout_ms);

/** Timer API (cooperative timers) -------------------------------------------
 * Minimal parity with C++ scheduler timers. Timers wake coroutines at/after
 * deadlines without blocking worker threads. Cancellation is best‑effort.
 */

typedef struct kc_timer_handle { unsigned long long id; } kc_timer_handle_t;

/** Schedule a coroutine to be enqueued as ready after delay_ms. */
kc_timer_handle_t kc_sched_timer_wake_after(kc_sched_t* s, kcoro_t* co, long delay_ms);
/** Schedule a coroutine to be enqueued at absolute deadline (CLOCK_MONOTONIC ns). */
kc_timer_handle_t kc_sched_timer_wake_at(kc_sched_t* s, kcoro_t* co, unsigned long long deadline_ns);
/** Best‑effort cancel of a previously scheduled wake. Returns 1 if canceled, 0 otherwise. */
int kc_sched_timer_cancel(kc_sched_t* s, kc_timer_handle_t h);

#ifdef __cplusplus
}
#endif
