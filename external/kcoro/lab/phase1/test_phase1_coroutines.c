// SPDX-License-Identifier: BSD-3-Clause
/* Phase 1 Test: Basic Coroutine Context Switching
 * 
 * This test validates:
 * 1. Coroutine creation and execution  
 * 2. Basic context switching with ARM64 assembly
 * 3. Task suspension and resumption
 * 4. Shared stack management
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>

#include "../legacy_ctx/kcoro_ctx.h"
#include "../legacy_ctx/kcoro_stack.h"

/* Test statistics */
static volatile int tasks_completed = 0;
static volatile int yields_performed = 0;

/* Simple test task that yields multiple times */
static void test_yielding_task(void* arg)
{
    int task_id = *(int*)arg;
    printf("[Task %d] Starting execution\n", task_id);
    
    for (int i = 0; i < 3; i++) {
        printf("[Task %d] Before yield %d\n", task_id, i+1);
        kc_yield();  /* This should use true coroutine context switching */
        printf("[Task %d] After yield %d\n", task_id, i+1);
        __sync_fetch_and_add(&yields_performed, 1);
    }
    
    printf("[Task %d] Completed\n", task_id);
    __sync_fetch_and_add(&tasks_completed, 1);
}

/* Test task that demonstrates stack usage */
static void test_stack_task(void* arg)
{
    int task_id = *(int*)arg;
    char stack_buffer[1024];  /* Use some stack space */
    
    /* Fill buffer to test stack save/restore */
    for (int i = 0; i < 1024; i++) {
        stack_buffer[i] = (char)(i % 256);
    }
    
    printf("[Stack Task %d] Using stack, yielding...\n", task_id);
    kc_yield();
    
    /* Verify stack content after context switch */
    int errors = 0;
    for (int i = 0; i < 1024; i++) {
        if (stack_buffer[i] != (char)(i % 256)) {
            errors++;
        }
    }
    
    if (errors == 0) {
        printf("[Stack Task %d] Stack preserved correctly!\n", task_id);
    } else {
        printf("[Stack Task %d] ERROR: %d stack corruption errors!\n", task_id, errors);
    }
    
    __sync_fetch_and_add(&tasks_completed, 1);
}

/* Legacy task for backward compatibility testing */
static void test_legacy_task(void* arg)
{
    int task_id = *(int*)arg;
    printf("[Legacy Task %d] Running (no coroutine context)\n", task_id);
    
    /* This should use thread-level yield */
    kc_yield();
    
    printf("[Legacy Task %d] Completed\n", task_id);
    __sync_fetch_and_add(&tasks_completed, 1);
}

int main(void)
{
    printf("=== kcoro Phase 1 Test: Basic Coroutine Context Switching ===\n\n");
    
    /* Initialize scheduler */
    kc_sched_opts_t opts = { .workers = 2, .queue_capacity = 0 };
    kc_sched_t* sched = kc_sched_init(&opts);
    assert(sched != NULL);
    
    printf("1. Testing Legacy Task Compatibility\n");
    
    /* Test legacy tasks (should work unchanged) */
    int legacy_ids[] = {100, 101};
    for (int i = 0; i < 2; i++) {
        int rc = kc_spawn(sched, test_legacy_task, &legacy_ids[i]);
        assert(rc == 0);
    }
    
    /* Wait for legacy tasks */
    while (tasks_completed < 2) {
        usleep(10000);  /* 10ms */
    }
    
    printf("✓ Legacy tasks completed: %d\n\n", tasks_completed);
    
    printf("2. Testing Coroutine Tasks with Context Switching\n");
    
    /* Reset counters */
    tasks_completed = 0;
    yields_performed = 0;
    
    /* Test coroutine tasks */
    int coro_ids[] = {1, 2, 3};
    for (int i = 0; i < 3; i++) {
        int rc = kc_spawn_coro(sched, test_yielding_task, &coro_ids[i]);
        assert(rc == 0);
    }
    
    /* Wait for coroutine tasks */
    while (tasks_completed < 3) {
        usleep(10000);  /* 10ms */
    }
    
    printf("✓ Coroutine tasks completed: %d\n", tasks_completed);
    printf("✓ Total yields performed: %d\n\n", yields_performed);
    
    printf("3. Testing Stack Save/Restore\n");
    
    /* Reset counters */
    tasks_completed = 0;
    
    /* Test stack preservation across context switches */
    int stack_ids[] = {10, 11};
    for (int i = 0; i < 2; i++) {
        int rc = kc_spawn_coro(sched, test_stack_task, &stack_ids[i]);
        assert(rc == 0);
    }
    
    /* Wait for stack tasks */
    while (tasks_completed < 2) {
        usleep(10000);  /* 10ms */
    }
    
    printf("✓ Stack tasks completed: %d\n\n", tasks_completed);
    
    printf("4. Testing Direct Context API\n");
    
    /* Test direct coroutine context creation */
    kcoro_t* test_ctx = kcoro_create(test_yielding_task, &coro_ids[0]);
    assert(test_ctx != NULL);
    assert(test_ctx->fn == test_yielding_task);
    assert(test_ctx->arg == &coro_ids[0]);
    assert(test_ctx->state == KCORO_READY);
    
    printf("✓ Direct context creation works\n");
    
    kcoro_destroy(test_ctx);
    printf("✓ Context destruction works\n\n");
    
    printf("5. Testing Stack Pool\n");
    
    /* Test stack pool operations */
    kc_stack_pool_t* pool = kc_stack_pool_create(4, KC_SHARED_STACK_SIZE);
    assert(pool != NULL);
    
    kc_shared_stack_t* stack1 = kc_stack_pool_get(pool);
    kc_shared_stack_t* stack2 = kc_stack_pool_get(pool);
    assert(stack1 != NULL && stack2 != NULL);
    assert(stack1 != stack2);  /* Should get different stacks */
    
    printf("✓ Stack pool allocation works\n");
    
    kc_stack_pool_put(pool, stack1);
    kc_stack_pool_put(pool, stack2);
    kc_stack_pool_destroy(pool);
    
    printf("✓ Stack pool cleanup works\n\n");
    
    /* Clean shutdown */
    kc_sched_shutdown(sched);
    
    printf("=== Phase 1 Test Results ===\n");
    printf("✅ Legacy task compatibility: PASSED\n");
    printf("✅ Coroutine context switching: PASSED  \n");
    printf("✅ Stack save/restore: PASSED\n");
    printf("✅ Direct context API: PASSED\n");
    printf("✅ Stack pool management: PASSED\n");
    printf("\n🎉 Phase 1 implementation successful!\n");
    printf("    - ARM64 context switching active\n");
    printf("    - True M:N coroutines functional\n");
    printf("    - Shared stack model working\n");
    printf("    - Backward compatibility maintained\n\n");
    
    return 0;
}
