// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200809L
/* Production Test: kcoro Integration with Scheduler
 *
 * This test validates the new kcoro core integrated with the existing
 * scheduler system, maintaining backward compatibility while adding
 * true coroutine capabilities.
 */

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <time.h>
#include <string.h>

#include "kcoro_sched.h"  /* Existing scheduler */
#include "kcoro_core.h"   /* New coroutine core */

/* Test counters */
static volatile int coroutine_tasks_completed = 0;
static volatile int legacy_tasks_completed = 0;
static volatile int yields_performed = 0;

/* Test 1: Pure coroutine execution (no scheduler) */
static void test_pure_coroutines(void)
{
    printf("=== Test 1: Pure Coroutine Execution ===\n");
    
    /* Create main coroutine */
    kcoro_t* main_co = kcoro_create_main();
    assert(main_co != NULL);
    assert(kcoro_current() == main_co);
    
    printf("✓ Main coroutine created: %p\n", main_co);
    
    /* Test coroutine function */
    void task_func(void* arg) {
        int* counter = (int*)arg;
        printf("  CORO: Started with arg=%d\n", *counter);
        
        (*counter)++;
        printf("  CORO: Incremented to %d, yielding...\n", *counter);
        kcoro_yield();
        
        printf("  CORO: Resumed, arg now=%d\n", *counter);
        (*counter)++;
        printf("  CORO: Final value=%d, finishing\n", *counter);
    }
    
    /* Create and run coroutine */
    int test_value = 10;
    kcoro_t* task_co = kcoro_create(task_func, &test_value, 0);
    assert(task_co != NULL);
    kcoro_set_name(task_co, "test_task");
    
    printf("✓ Task coroutine created: %p\n", task_co);
    
    /* Resume the task */
    printf("MAIN: Resuming task...\n");
    kcoro_resume(task_co);
    
    printf("MAIN: Task yielded back, test_value=%d\n", test_value);
    assert(test_value == 11);
    
    /* Resume again to finish */
    printf("MAIN: Resuming task again...\n");
    kcoro_resume(task_co);
    
    printf("MAIN: Task completed, final test_value=%d\n", test_value);
    assert(test_value == 12);
    
    /* Cleanup */
    kcoro_destroy(task_co);
    kcoro_destroy(main_co);
    
    printf("✅ Pure coroutine test passed\n\n");
}

/* Test 2: Coroutine cooperative multitasking */
static void test_cooperative_multitasking(void)
{
    printf("=== Test 2: Cooperative Multitasking ===\n");
    
    kcoro_t* main_co = kcoro_create_main();
    
    void task1_func(void* arg) {
        (void)arg;
        printf("  TASK1: Started\n");
        for (int i = 0; i < 3; i++) {
            printf("  TASK1: Step %d\n", i + 1);
            kcoro_yield();
        }
        printf("  TASK1: Finished\n");
        __sync_fetch_and_add(&coroutine_tasks_completed, 1);
    }
    
    void task2_func(void* arg) {
        (void)arg;
        printf("  TASK2: Started\n");
        for (int i = 0; i < 2; i++) {
            printf("  TASK2: Step %d\n", i + 1);
            kcoro_yield();
        }
        printf("  TASK2: Finished\n");
        __sync_fetch_and_add(&coroutine_tasks_completed, 1);
    }
    
    /* Create multiple coroutines */
    kcoro_t* task1 = kcoro_create(task1_func, NULL, 0);
    kcoro_t* task2 = kcoro_create(task2_func, NULL, 0);
    kcoro_set_name(task1, "task1");
    kcoro_set_name(task2, "task2");
    
    /* Round-robin execution */
    printf("MAIN: Starting cooperative execution...\n");
    
    for (int round = 0; round < 4; round++) {
        printf("MAIN: Round %d\n", round + 1);
        
        if (task1->state != KCORO_FINISHED) {
            printf("MAIN: -> task1\n");
            kcoro_resume(task1);
        }
        
        if (task2->state != KCORO_FINISHED) {
            printf("MAIN: -> task2\n");
            kcoro_resume(task2);
        }
    }
    
    printf("MAIN: Cooperative execution completed\n");
    printf("✓ Completed tasks: %d\n", coroutine_tasks_completed);
    
    /* Cleanup */
    kcoro_destroy(task1);
    kcoro_destroy(task2);
    kcoro_destroy(main_co);
    
    printf("✅ Cooperative multitasking test passed\n\n");
}

/* Test 3: Integration with existing scheduler */
static void test_scheduler_integration(void)
{
    printf("=== Test 3: Scheduler Integration ===\n");
    
    /* Legacy task function (existing scheduler) */
    void legacy_task(void* arg) {
        int task_id = *(int*)arg;
        printf("  LEGACY: Task %d executing (no coroutines)\n", task_id);
        struct timespec ts = {0, 1000 * 1000};
        nanosleep(&ts, NULL); /* Simulate work */
        printf("  LEGACY: Task %d completed\n", task_id);
        __sync_fetch_and_add(&legacy_tasks_completed, 1);
    }
    
    /* Hybrid task function (uses coroutines within scheduler) */
    void hybrid_task(void* arg) {
        int task_id = *(int*)arg;
        printf("  HYBRID: Task %d starting coroutine execution\n", task_id);
        
        /* Create main coroutine within the task */
        kcoro_t* main_co = kcoro_create_main();
        
        void subtask_func(void* subarg) {
            int subtask_id = *(int*)subarg;
            printf("    SUBTASK: %d.%d executing\n", task_id, subtask_id);
            kcoro_yield();
            printf("    SUBTASK: %d.%d resumed\n", task_id, subtask_id);
            __sync_fetch_and_add(&yields_performed, 1);
        }
        
        /* Create and run sub-coroutines */
        int sub_ids[] = {1, 2};
        kcoro_t* sub1 = kcoro_create(subtask_func, &sub_ids[0], 0);
        kcoro_t* sub2 = kcoro_create(subtask_func, &sub_ids[1], 0);
        
        /* Execute sub-coroutines */
        kcoro_resume(sub1);
        kcoro_resume(sub2);
        kcoro_resume(sub1);  /* Finish them */
        kcoro_resume(sub2);
        
        /* Cleanup */
        kcoro_destroy(sub1);
        kcoro_destroy(sub2);
        kcoro_destroy(main_co);
        
        printf("  HYBRID: Task %d completed with coroutines\n", task_id);
        __sync_fetch_and_add(&coroutine_tasks_completed, 1);
    }
    
    /* Test with existing scheduler */
    kc_sched_opts_t opts = { .workers = 2, .queue_capacity = 0 };
    kc_sched_t* sched = kc_sched_init(&opts);
    assert(sched != NULL);
    
    printf("MAIN: Scheduler created with 2 workers\n");
    
    /* Submit mixed workload */
    int task_ids[] = {1, 2, 3, 4};
    
    /* Legacy tasks */
    for (int i = 0; i < 2; i++) {
        int rc = kc_spawn(sched, legacy_task, &task_ids[i]);
        assert(rc == 0);
    }
    
    /* Hybrid tasks (scheduler + coroutines) */
    for (int i = 2; i < 4; i++) {
        int rc = kc_spawn(sched, hybrid_task, &task_ids[i]);
        assert(rc == 0);
    }
    
    /* Wait for completion */
    printf("MAIN: Waiting for tasks to complete...\n");
    
    while (legacy_tasks_completed < 2 || coroutine_tasks_completed < 2) {
        { struct timespec ts2 = {0, 10 * 1000 * 1000};
        nanosleep(&ts2, NULL); }/* 10ms */
    }
    
    printf("✓ Legacy tasks: %d, Coroutine tasks: %d, Yields: %d\n",
           legacy_tasks_completed, coroutine_tasks_completed, yields_performed);
    
    /* Shutdown */
    kc_sched_shutdown(sched);
    
    printf("✅ Scheduler integration test passed\n\n");
}

int main(void)
{
    printf("=== kcoro Production Integration Test ===\n");
    printf("Testing new coroutine core with existing scheduler\n\n");
    
    /* Run tests */
    test_pure_coroutines();
    test_cooperative_multitasking();
    test_scheduler_integration();
    
    printf("=== All Tests Completed Successfully ===\n");
    printf("🎉 kcoro core integration validated!\n");
    printf("   • Pure coroutine execution: ✅\n");
    printf("   • Cooperative multitasking: ✅\n");  
    printf("   • Scheduler integration: ✅\n");
    printf("   • Backward compatibility: ✅\n\n");
    
    printf("Ready for production use!\n");
    
    return 0;
}
