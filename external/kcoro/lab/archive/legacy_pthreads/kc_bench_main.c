// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <stdlib.h>

#include "../include/kcoro_bench.h"

int main(int argc, char **argv)
{
    kc_bench_config_t config = {
        .producers = 4,
        .consumers = 4,
        .per_producer = 25000,
        .capacity = 64,
        .msg_size = 32,
        .pkt_bytes = 32,
        .spin_iters = 0,
        .pin_threads = 0
    };
    if (argc >= 2) config.producers = atoi(argv[1]);
    if (argc >= 3) config.consumers = atoi(argv[2]);
    if (argc >= 4) config.per_producer = atoi(argv[3]);

    printf("=== kcoro Pthread Benchmark ===\n");
    printf("Producers: %d, Consumers: %d, Messages per producer: %d\n",
           config.producers, config.consumers, config.per_producer);
    printf("Total messages: %d\n\n", config.producers * config.per_producer);

    kc_bench_result_t result;
    int rc = kc_bench_run(&config, &result);
    if (rc == 0) {
        printf("SUCCESS! Pthread benchmark completed.\n\n");
        printf("=== PTHREAD BENCHMARK RESULTS ===\n");
        printf("Throughput: %.1f Mpps (%.0f messages/sec)\n", result.pps / 1e6, result.pps);
        printf("Duration: %.3f seconds\n", result.duration_s);
        printf("Bandwidth: %.3f Gbps\n", result.gbps);
        printf("Messages processed: %d/%d\n", result.processed, result.expected);
        printf("Per-producer mismatches: %d\n", result.per_prod_mismatch);
        printf("Runtime errors: %d\n", result.runtime_errors);
    } else {
        printf("FAILED: Pthread benchmark returned error code %d\n", rc);
    }
    return rc;
}

