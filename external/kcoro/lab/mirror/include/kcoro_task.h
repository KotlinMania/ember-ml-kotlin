// SPDX-License-Identifier: BSD-3-Clause
#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* 
 * Coroutine task context for cooperative multitasking
 * 
 * This provides the foundation for true async/await patterns:
 * - Tasks can yield and be rescheduled
 * - Channels can park/wake tasks instead of blocking threads
 * - M:N scheduling: many tasks per worker thread
 */

typedef struct kc_task kc_task_t;
typedef uint64_t kc_task_id_t;

/* Task state for scheduler */
typedef enum {
    KC_TASK_READY,     /* Ready to run */
    KC_TASK_RUNNING,   /* Currently executing */
    KC_TASK_BLOCKED,   /* Waiting on channel/timer */
    KC_TASK_FINISHED   /* Completed */
} kc_task_state_t;

/* Task context (per-worker thread local) */
typedef struct kc_task_ctx {
    kc_task_t* current_task;     /* Currently running task on this worker */
    struct kc_sched* scheduler;  /* Back-reference to scheduler */
    int worker_id;               /* Worker thread ID */
} kc_task_ctx_t;

/* Get current task context (thread-local) */
kc_task_ctx_t* kc_current_task_ctx(void);

/* Get current running task (NULL if not in task context) */
kc_task_t* kc_current_task(void);

/* Yield current task - reschedule to run later */
void kc_task_yield(void);

/* Block current task on a wait condition (used by channels) */
void kc_task_block(void);

/* Wake a blocked task (used by channels) */
void kc_task_wake(kc_task_t* task);

/* Sleep current task for specified milliseconds */
void kc_task_sleep_ms(int ms);

#ifdef __cplusplus
}
#endif
