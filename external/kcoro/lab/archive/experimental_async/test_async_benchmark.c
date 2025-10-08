// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <stdlib.h>

#ifdef KC_SCHED
#include "kcoro_sched.h"
#include "kcoro_async.h"
#include "kcoro_task.h"
#endif

#include "kc_actor_bench.h"

int main(int argc, char* argv[])
{
    (void)argc;
    (void)argv;
    
    kc_bench_config_t config = {
        .num_producers = 4,
        .num_consumers = 2,
        .channel_capacity = 1000,
        .messages_per_producer = 100000,
        .producer_delay_us = 0,
        .consumer_delay_us = 0
    };
    
#ifdef KC_SCHED
    printf("Running async benchmark with scheduler support\n");
    return run_actor_benchmark_async(&config);
#else
    printf("Running standard benchmark\n");
    return run_actor_benchmark(&config);
#endif
}
