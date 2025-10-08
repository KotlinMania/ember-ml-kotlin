/* SPDX-License-Identifier: BSD-3-Clause */
/**
 * @file kc_actor_bench.h
 * @brief Minimal actor benchmark harnesses (for labs/tools only).
 *
 * These headers describe helper entrypoints used by lab tools to run simple
 * producer/consumer actor benchmarks. They are not part of the core library’s
 * stability promise and may change to suit tooling needs.
 *
 * Install guidance
 *   Not installed as part of the production public headers. Lab tools include
 *   this file directly; applications should rely on the stable kcoro APIs.
 */
#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    size_t num_producers;
    size_t num_consumers;
    size_t messages_per_producer;
    size_t message_size;
    int use_spin_loops;  /* 0 = false, 1 = true */
    size_t spin_iterations;
} kc_actor_bench_config_t;

typedef struct {
    double total_time_seconds;
    double messages_per_second;
    double gigabits_per_second;
    size_t total_messages_sent;
    size_t total_messages_received;
    size_t producer_mismatches;
    size_t consumer_mismatches;
} kc_actor_bench_result_t;

int kc_actor_bench_run(const kc_actor_bench_config_t* config,
                       kc_actor_bench_result_t* result);

/* Simple benchmark configuration for compatibility */
typedef struct {
    int num_producers;
    int num_consumers;
    int channel_capacity;
    int messages_per_producer;
    int producer_delay_us;
    int consumer_delay_us;
} kc_bench_config_t;

/* Run actor benchmark */
int run_actor_benchmark(const kc_bench_config_t* config);

#ifdef KC_SCHED
/* Run async actor benchmark with scheduler support */
int run_actor_benchmark_async(const kc_bench_config_t* config);
#endif

#ifdef __cplusplus
}
#endif
