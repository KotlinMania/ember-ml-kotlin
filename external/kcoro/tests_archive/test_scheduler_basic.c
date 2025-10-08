// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "kcoro_sched.h"
#include "kcoro_task.h"

static void task_function(void* arg)
{
    int task_id = *(int*)arg;
    free(arg);
    
    printf("[Task %d] Starting\n", task_id);
    
    for (int i = 0; i < 5; i++) {
        printf("[Task %d] Iteration %d\n", task_id, i);
        kc_yield(); /* Yield to other tasks */
    struct timespec ts = {0,100*1000000L}; nanosleep(&ts,NULL);
    }
    
    printf("[Task %d] Finished\n", task_id);
}

int main(void)
{
    printf("Testing kcoro scheduler with task yielding\n");
    
    /* Initialize scheduler with 2 workers */
    kc_sched_opts_t opts = { .workers = 2 };
    kc_sched_t* scheduler = kc_sched_init(&opts);
    if (!scheduler) {
        printf("Failed to initialize scheduler\n");
        return 1;
    }
    
    printf("Scheduler initialized with %d workers\n", opts.workers);
    
    /* Spawn some tasks */
    for (int i = 0; i < 4; i++) {
        int* task_id = malloc(sizeof(int));
        if (!task_id) {
            printf("Failed to allocate task ID\n");
            continue;
        }
        *task_id = i;
        
        if (kc_spawn(scheduler, task_function, task_id) != 0) {
            printf("Failed to spawn task %d\n", i);
            free(task_id);
        }
    }
    
    printf("Tasks spawned, waiting for completion...\n");
    
    /* Let tasks run for a while */
    sleep(3);
    
    /* Shutdown scheduler */
    kc_sched_shutdown(scheduler);
    
    printf("Scheduler test completed\n");
    return 0;
}

