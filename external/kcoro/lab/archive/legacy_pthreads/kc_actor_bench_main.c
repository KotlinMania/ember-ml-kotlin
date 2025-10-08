/* SPDX-License-Identifier: BSD-3-Clause */
/*
 * Actor benchmark standalone executable
 */
#include <stdio.h>
#include <stdlib.h>

#include "kc_actor_bench.h"

/* Main function for standalone benchmark */
int main(int argc, char **argv)
{
    kc_actor_bench_config_t config = {
        .num_producers = 2,
        .num_consumers = 2,
        .messages_per_producer = 10000,
        .message_size = 32,
        .use_spin_loops = 0,
        .spin_iterations = 0
    };
    
    if (argc >= 2) config.num_producers = (size_t)atoi(argv[1]);
    if (argc >= 3) config.num_consumers = (size_t)atoi(argv[2]);
    if (argc >= 4) config.messages_per_producer = (size_t)atoi(argv[3]);
    
    printf("Running actor benchmark: %zu producers, %zu consumers, %zu messages each\n", 
           config.num_producers, config.num_consumers, config.messages_per_producer);
    
    kc_actor_bench_result_t result;
    int rc = kc_actor_bench_run(&config, &result);
    
    return rc;
}
