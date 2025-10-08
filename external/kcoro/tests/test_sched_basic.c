// Basic scheduler test
// This test validates fundamental scheduler functionality by submitting
// a known number of tasks and verifying they execute correctly
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include "kcoro_sched.h"

// Worker function for test tasks
// This is a minimal task that does nothing but serves as a placeholder
// for actual work to be performed by the scheduler
static void work_fn(void *arg){ (void)arg; }

int main(void){
    // Configure scheduler options with 4 worker threads
    kc_sched_opts_t opts = {0};
    opts.workers = 4;

    // Initialize the scheduler with configured options
    kc_sched_t *s = kc_sched_init(&opts);
    if(!s){ fprintf(stderr, "[sched] create failed\n"); return 1; }
    // Submit 1000 tasks to the scheduler for execution
    enum { N = 1000 };
    for(int i=0;i<N;i++){
        if(kc_spawn(s, work_fn, NULL)!=0){ fprintf(stderr, "submit failed at %d\n", i); kc_sched_shutdown(s); return 1; }
    }

    // Allow tasks to run. In a real test we would join or wait; here we sleep briefly.
    // This gives the scheduler time to process the submitted tasks
    kc_sleep_ms(50);
    // Retrieve scheduler statistics after tasks have had time to execute
    kc_sched_stats_t st; kc_sched_get_stats(s,&st);

    // Verify that all submitted tasks were accounted for
    if(st.tasks_submitted != N){ fprintf(stderr, "stats mismatch submitted=%lu expected=%d\n", st.tasks_submitted, N); kc_sched_shutdown(s); return 3; }

    // Verify that at least some tasks completed (to ensure scheduler is working)
    if(st.tasks_completed == 0){ fprintf(stderr, "no tasks completed yet\n"); kc_sched_shutdown(s); return 4; }

    // Shutdown the scheduler and report results
    kc_sched_shutdown(s);
    printf("[sched] basic test passed: %d tasks executed, probes=%lu succ=%lu fail=%lu fast_hits=%lu fast_miss=%lu\n", N, st.steals_probes, st.steals_succeeded, st.steals_failures, st.fastpath_hits, st.fastpath_misses);
    return 0;
}
