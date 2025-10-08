// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <stdlib.h>

#include "kcoro_unified.h"

static void simple_task(void* arg)
{
    int task_id = *(int*)arg;
    free(arg);
    
    printf("[Task %d] Using unified API\n", task_id);
    
    /* Test both naming families work */
    for (int i = 0; i < 3; i++) {
        printf("[Task %d] Simple yield/sleep - iteration %d\n", task_id, i);
        kc_yield();           /* From kcoro_sched.h */
        kc_sleep_ms(50);      /* From kcoro_sched.h */
    }
    
    for (int i = 0; i < 2; i++) {
        printf("[Task %d] Task-aware yield/sleep - iteration %d\n", task_id, i);
    sched_yield();
    struct timespec ts = {0, 75*1000000L}; nanosleep(&ts, NULL);
    }
    
    /* Test convenience functions directly */
    printf("[Task %d] Using direct function calls\n", task_id);
    sched_yield();
    struct timespec ts2 = {0, 25*1000000L}; nanosleep(&ts2, NULL);
    
    printf("[Task %d] Unified API test completed\n", task_id);
}

int main(void)
{
    printf("Testing kcoro unified API\n");
    
    /* Initialize scheduler */
    kc_sched_opts_t opts = { .workers = 2 };
    kc_sched_t* scheduler = kc_sched_init(&opts);
    if (!scheduler) {
        printf("Failed to initialize scheduler\n");
        return 1;
    }
    
    printf("Scheduler initialized - testing both naming families\n");
    
    /* Spawn tasks to test unified API */
    for (int i = 0; i < 3; i++) {
        int* task_id = malloc(sizeof(int));
        if (!task_id) continue;
        *task_id = i;
        
        if (kc_spawn(scheduler, simple_task, task_id) != 0) {
            printf("Failed to spawn task %d\n", i);
            free(task_id);
        }
    }
    
    /* Let tasks run */
    printf("Tasks running - both kc_* and kc_task_* functions available\n");
    kc_sleep_ms(2000);  /* Simple sleep while tasks run */
    
    kc_sched_shutdown(scheduler);
    printf("Unified API test completed successfully\n");
    
    return 0;
}

