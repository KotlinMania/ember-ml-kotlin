// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200112L
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <time.h>
#include <string.h>

#include "../include/kcoro_bench.h"

static inline double now_sec(void)
{ struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts); return ts.tv_sec + ts.tv_nsec/1e9; }


int main(int argc, char **argv)
{
    int producers=4, consumers=2, per_prod=100000, capacity=4096, pkt=64;
    int spin=-1, pin=0;
    int i=1;
    while (i < argc && strncmp(argv[i], "-", 1) == 0) {
        if ((strcmp(argv[i], "--spin") == 0 || strcmp(argv[i], "-s") == 0) && i+1 < argc) { spin = atoi(argv[++i]); i++; }
        else if (strcmp(argv[i], "--pin") == 0) { pin = 1; i++; }
        else { break; }
    }
    if (argc - i >= 3) { producers=atoi(argv[i]); consumers=atoi(argv[i+1]); per_prod=atoi(argv[i+2]); }
    if (argc - i >= 4) capacity=atoi(argv[i+3]);
    if (argc - i >= 5) pkt=atoi(argv[i+4]);

    kc_bench_config_t cfg = {
        .producers=producers,
        .consumers=consumers,
        .per_producer=per_prod,
        .capacity=(size_t)capacity,
        .msg_size=sizeof(int),
        .pkt_bytes=pkt,
        .spin_iters=spin,
        .pin_threads=pin
    };
    kc_bench_result_t out;
    double t0 = now_sec();
    int rc = kc_bench_run(&cfg, &out);
    double t1 = now_sec(); (void)t0; (void)t1; /* caller wall clock if desired */
    if (rc) { fprintf(stderr, "bench_run rc=%d\n", rc); return 1; }

    printf("[stress] processed=%d expected=%d duration_s=%.6f\n", out.processed, out.expected, out.duration_s);
    printf("[stress] throughput_pps=%.0f throughput_mpps=%.3f\n", out.pps, out.pps/1e6);
    printf("[stress] bandwidth_mbps=%.3f bandwidth_gbps=%.3f\n", out.gbps*1000.0, out.gbps);
    printf("[stress] per-producer mismatches=%d runtime_errors=%d\n", out.per_prod_mismatch, out.runtime_errors);

    return (out.processed == out.expected && out.per_prod_mismatch == 0 && out.runtime_errors == 0) ? 0 : 2;
}
