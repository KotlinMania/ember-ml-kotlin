// SPDX-License-Identifier: BSD-3-Clause
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <assert.h>
#include <sys/mman.h>
#include <unistd.h>
#include <pthread.h>

#ifndef MAP_ANON
#define MAP_ANON 0x1000
#endif
#ifndef MAP_ANONYMOUS
#define MAP_ANONYMOUS MAP_ANON
#endif

#include "kcoro_core.h"
#include "kcoro_sched.h"

/* Thread-local current coroutine */
static __thread kcoro_t* current_kcoro = NULL;
/* Thread-local main coroutine (yield target) */
static __thread kcoro_t* main_kcoro = NULL;

/* Coroutine ID counter */
static uint64_t next_kcoro_id = 1;

/* Default stack size */
#define KCORO_DEFAULT_STACK_SIZE (64 * 1024)  /* 64KB */

/* Function protector implementation */
void kcoro_funcp_protector(void)
{
    kcoro_t *current = current_kcoro;
    int state = current ? (int)current->state : -1;
    fprintf(stderr,
            "kcoro: coroutine function returned unexpectedly (co=%p state=%d main=%p fn=%p)\n",
            (void*)current,
            state,
            current ? (void*)current->main_co : NULL,
            current ? (void*)current->fn : NULL);
    abort();
}

/* Internal function: coroutine trampoline */
static void kcoro_trampoline(void);

kcoro_t* kcoro_create_main(void)
{
    kcoro_t* main_co = (kcoro_t*)calloc(1, sizeof(kcoro_t));
    if (!main_co) return NULL;

    /* Initialize main coroutine */
    memset(main_co->reg, 0, sizeof(main_co->reg));
    main_co->state = KCORO_RUNNING;
    main_co->fn = NULL;  /* Main has no function */
    main_co->arg = NULL;
    main_co->id = 0;     /* Special ID for main */
    main_co->main_co = NULL;  /* Main has no parent */
    main_co->name = "main";
    main_co->ready_enqueued = false;
    atomic_init(&main_co->running_flag, 0);
    atomic_init(&main_co->refcount, 1);
    
    /* Set as current */
    current_kcoro = main_co;
    main_kcoro = main_co;

    return main_co;
}

void kcoro_set_thread_main(kcoro_t* main_co)
{
    main_kcoro = main_co;
    current_kcoro = main_co;
}

kcoro_t* kcoro_create(kcoro_fn_t fn, void* arg, size_t stack_size)
{
    if (!fn) return NULL;
    if (stack_size == 0) stack_size = KCORO_DEFAULT_STACK_SIZE;
    
    kcoro_t* co = (kcoro_t*)calloc(1, sizeof(kcoro_t));
    if (!co) return NULL;
    
    /* Allocate stack with mmap for guard page support */
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size < 0) page_size = 4096;
    
    /* Align stack size to page boundary */
    size_t total_size = (stack_size + page_size - 1) & ~(page_size - 1);
    
    void* stack_mem = mmap(NULL, total_size, PROT_READ | PROT_WRITE,
                          MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (stack_mem == MAP_FAILED) {
        free(co);
        return NULL;
    }
    
    /* Initialize coroutine */
    memset(co->reg, 0, sizeof(co->reg));
    co->state = KCORO_CREATED;
    co->fn = fn;
    co->arg = arg;
    co->id = __sync_fetch_and_add(&next_kcoro_id, 1);
    co->main_co = main_kcoro;     /* Default yield target */
    co->stack_ptr = stack_mem;
    co->stack_size = total_size;
    co->ready_enqueued = false;
    atomic_init(&co->running_flag, 0);
    atomic_init(&co->refcount, 1);
    
    /* Set up stack and entry point (ARM64 ABI compliant) */
    uintptr_t stack_top = (uintptr_t)stack_mem + total_size;
    stack_top = stack_top & ~0xFUL;  /* 16-byte align */
    stack_top -= 16;  /* Leave space */
    
    co->reg[14] = (void*)stack_top;           /* SP at reg[14] */
    co->reg[15] = (void*)stack_top;           /* FP at reg[15] */  
    co->reg[13] = (void*)kcoro_trampoline;    /* LR at reg[13] - entry point */
    
    return co;
}

static void kcoro_free(kcoro_t* co)
{
    if (!co) return;
    if (co->stack_ptr && co->stack_size > 0) {
        munmap(co->stack_ptr, co->stack_size);
    }
    if (current_kcoro == co) {
        current_kcoro = NULL;
    }
    if (main_kcoro == co) {
        main_kcoro = NULL;
    }
    free(co);
}

static int kcoro_ref_debug_enabled(void)
{
    static int cached = -1;
    if (__builtin_expect(cached == -1, 0)) {
        const char *env = getenv("KCORO_REF_DEBUG");
        cached = (env && *env && env[0] != '0');
    }
    return cached;
}

void kcoro_destroy(kcoro_t* co)
{
    kcoro_release(co);
}

void kcoro_set_name(kcoro_t* co, const char* name)
{
    if (co) {
        co->name = name;
    }
}

kcoro_t* kcoro_current(void)
{
    return current_kcoro;
}

kcoro_t* kcoro_thread_main(void)
{
    return main_kcoro;
}

void kcoro_retain(kcoro_t* co)
{
    if (!co) return;
    int prev = atomic_fetch_add_explicit(&co->refcount, 1, memory_order_relaxed);
    if (kcoro_ref_debug_enabled()) {
        fprintf(stderr, "[kcoro][ref] retain co=%p -> %d\n", (void*)co, prev + 1);
    }
}

void kcoro_release(kcoro_t* co)
{
    if (!co) return;
    int prev = atomic_fetch_sub_explicit(&co->refcount, 1, memory_order_acq_rel);
    if (kcoro_ref_debug_enabled()) {
        fprintf(stderr, "[kcoro][ref] release co=%p prev=%d\n", (void*)co, prev);
    }
    if (prev == 1) {
        kcoro_free(co);
    }
}

void kcoro_resume(kcoro_t* co)
{
    if (!co || co->state == KCORO_FINISHED) return;
    
    kcoro_t* yield_co = current_kcoro;
    kcoro_t* from_co = yield_co ? yield_co : main_kcoro;
    if (!from_co) {
        from_co = co->main_co;
    }
    
    /* Update states */
    if (yield_co && yield_co != co) {
        yield_co->state = KCORO_SUSPENDED;
    }
    co->state = KCORO_RUNNING;
    current_kcoro = co;
    
    /* Context switch */
    kcoro_switch(from_co, co);

    /* Returned from context switch - restore current */
    current_kcoro = yield_co ? yield_co : main_kcoro;
    if (yield_co) {
        yield_co->state = KCORO_RUNNING;
    } else if (main_kcoro) {
        main_kcoro->state = KCORO_RUNNING;
    }
}

void kcoro_yield(void)
{
    kcoro_t* current = current_kcoro;
    kcoro_t* main_co = main_kcoro ? main_kcoro : (current ? current->main_co : NULL);
    if (!current || !main_co) {
        /* No main coroutine to yield to - this might be in a different context */
        return;
    }

    /* Update states */
    current->state = KCORO_SUSPENDED;
    main_co->state = KCORO_RUNNING;
    current_kcoro = main_co;
    
    /* Context switch back to main */
    kcoro_switch(current, main_co);
    
    /* When resumed, we'll be back here */
    current->state = KCORO_RUNNING;
    current_kcoro = current;
}

void kcoro_yield_to(kcoro_t* target_co)
{
    if (!target_co) return;
    
    kcoro_t* current = current_kcoro;
    
    /* Update states */
    if (current) {
        current->state = KCORO_SUSPENDED;
    }
    target_co->state = KCORO_RUNNING;
    current_kcoro = target_co;
    
    /* Context switch */
    kcoro_switch(current, target_co);
    
    /* When resumed, restore our state */
    if (current) {
        current->state = KCORO_RUNNING;
        current_kcoro = current;
    }
}

/* Park current coroutine: transitions to KCORO_PARKED and switches to main */
void kcoro_park(void)
{
    kcoro_t* current = current_kcoro;
    kcoro_t* main_co = main_kcoro ? main_kcoro : (current ? current->main_co : NULL);
    if (!current || !main_co) return;
    if (current->state == KCORO_FINISHED) return;
    current->state = KCORO_PARKED;
    main_co->state = KCORO_RUNNING;
    current_kcoro = main_co;
    kcoro_switch(current, main_co);
    /* When unparked & resumed, state will be set by kcoro_unpark before scheduling */
    if (current->state == KCORO_PARKED) {
        /* Defensive: if resumed without state change, mark running */
        current->state = KCORO_RUNNING;
    }
    current_kcoro = current;
}

void kcoro_unpark(kcoro_t* co)
{
    if (!co) return;
    if (co->state != KCORO_PARKED) return; /* Only parked coroutines */
    co->state = KCORO_READY;
    kc_sched_t* s = kc_sched_current();
    /* Do NOT auto-create a default scheduler here; tests that drive coroutines
     * manually (single-threaded) expect unparks to enqueue only when running
     * under an explicit worker scheduler. Auto-initializing would introduce a
     * background thread concurrently resuming coroutines, leading to races and
     * duplicate consumption. */
    if (s) {
        /* Unified scheduler path: legacy and v2 merged, so enqueue is always work-stealing aware now. */
        kc_sched_enqueue_ready(s, co);
    }
}

int kcoro_is_parked(const kcoro_t* co)
{
    return co && co->state == KCORO_PARKED;
}

/* Internal coroutine trampoline function */
static void kcoro_trampoline(void)
{
    kcoro_t* current = current_kcoro;
    assert(current && current->fn);
    
    /* Mark as running and call the function */
    current->state = KCORO_RUNNING;
    current->fn(current->arg);
    
    /* Function completed - mark as finished */
    current->state = KCORO_FINISHED;
    
    /* Yield back to main coroutine */
    if (main_kcoro) {
        kcoro_t* main_co = main_kcoro;
        main_co->state = KCORO_RUNNING;
        current_kcoro = main_co;
        kcoro_switch(current, main_co);
        return;
    }

    /* Should never reach here, but if we do, call protector */
    kcoro_funcp_protector();
}
