// SPDX-License-Identifier: BSD-3-Clause
/**
 * @file kcoro_core.h
 * @brief Core coroutine primitives (create/destroy, resume/yield, park/unpark).
 *
 * -----------------------------------------------------------------------------
 * Header Surface & Optional Items
 * -----------------------------------------------------------------------------
 * Purpose
 *   Small, focused primitives for user‑space coroutines with an ARM64 switcher.
 *   Assembly keeps context hops fast; scheduling and policy remain in C.
 *
 * Optional items
 *   - This header intentionally has no optional tunables. The primitives here
 *     are foundational and stable.
 *
 * Install guidance
 *   - This header is part of the production public API and should be installed.
 */
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdatomic.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Forward declarations */
typedef struct kcoro kcoro_t;
typedef struct kcoro_sched kcoro_sched_t;

/* Coroutine function type */
typedef void (*kcoro_fn_t)(void* arg);

/* Coroutine state */
typedef enum {
    KCORO_CREATED,           /* Created but not started */
    KCORO_READY,             /* Ready to run */
    KCORO_RUNNING,           /* Currently executing */
    KCORO_SUSPENDED,         /* Suspended (yielded) */
    KCORO_PARKED,            /* Parked (not runnable until explicitly unparked) */
    KCORO_FINISHED           /* Completed execution */
} kcoro_state_t;

/* Core coroutine structure - matches ARM64 assembly requirements */
struct kcoro {
    /* Register save area - MUST be first field for assembly */
    void* reg[32];               /* ARM64: x19-x28, x30, sp, x29 at specific indices */
    
    /* Coroutine metadata */
    kcoro_state_t state;         /* Current execution state */
    kcoro_fn_t fn;               /* Task function */
    void* arg;                   /* Task argument */
    uint64_t id;                 /* Unique coroutine ID */

    /* Execution context */
    kcoro_t* main_co;            /* Main coroutine (yield target) */
    kcoro_sched_t* scheduler;    /* Owning scheduler */
    bool ready_enqueued;         /* Scheduler ready-queue flag */
    atomic_int running_flag;     /* 0 = idle, 1 = running */
    atomic_int refcount;         /* Reference count for lifetime management */

    /* Stack management */
    void* stack_ptr;             /* Private stack (if not using shared) */
    size_t stack_size;           /* Stack size */
    
    /* Scheduler linkage */
    kcoro_t* next;               /* Next in queue */
    kcoro_t* prev;               /* Previous in queue */
    
    /* Debug info */
    const char* name;            /* Optional name for debugging */
};

/** ARM64 assembly context switching primitive (internal). */
extern void* kcoro_switch(kcoro_t* from_co, kcoro_t* to_co);

/* Function protector for proper stack cleanup */
extern void kcoro_funcp_protector_asm(void);
void kcoro_funcp_protector(void);

/**
 * @name Core coroutine API
 * Create/destroy coroutines and control execution.
 * @{ */
kcoro_t* kcoro_create(kcoro_fn_t fn, void* arg, size_t stack_size);
void kcoro_destroy(kcoro_t* co);

/* Set optional name for debugging */
void kcoro_set_name(kcoro_t* co, const char* name);

/** Execution control */
void kcoro_resume(kcoro_t* co);
kcoro_t* kcoro_current(void);
kcoro_t* kcoro_thread_main(void);
void kcoro_retain(kcoro_t* co);
void kcoro_release(kcoro_t* co);
void kcoro_yield(void);
void kcoro_yield_to(kcoro_t* target_co);

/** Parking (no fairness requeue) */
void kcoro_park(void);              /* Park current coroutine (must be running in scheduler context) */
void kcoro_unpark(kcoro_t* co);     /* Make a parked coroutine ready again */
int  kcoro_is_parked(const kcoro_t* co);

/** Main coroutine setup */
kcoro_t* kcoro_create_main(void);
void kcoro_set_thread_main(kcoro_t* main_co);
/** @} */

#ifdef __cplusplus
}
#endif
