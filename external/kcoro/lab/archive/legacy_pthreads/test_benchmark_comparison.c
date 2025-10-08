// SPDX-License-Identifier: BSD-3-Clause
/*
 * Actor-based benchmark runner
 * 
 * This runs the proper actor/coroutine benchmark that follows the Kotlin model:
 * - Individual actors with dedicated channels  
 * - Measures true message-passing throughput
 * - No thread contention on shared resources
 * - Suitable for 100Gbps packet analysis performance testing
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "kc_actor_bench.h"

static void print_comparison_header()
{
    printf("=====================================\n");
    printf("kcoro Actor Benchmark\n");
    printf("=====================================\n");
    printf("This benchmark uses the proper actor/coroutine model:\n");
    printf("- Individual actors with dedicated channels\n");
    printf("- Measures true message-passing throughput\n");
    printf("- Follows Kotlin coroutine patterns\n");
    printf("- No thread contention on shared resources\n");
    printf("\n");
    printf("Architecture: N producers → N consumers (N channels)\n");
    printf("Each consumer has its own dedicated message channel.\n");
    printf("=====================================\n\n");
}

int main(int argc, char **argv)
{
    int num_producers = 4;
    int num_consumers = 4;
    int messages_per_worker = 25000;
    
    /* Accept CLI as: <producers> [consumers] [messages_per_producer] */
    if (argc >= 2) num_producers = atoi(argv[1]);
    if (argc >= 3) num_consumers = atoi(argv[2]);
    if (argc >= 4) messages_per_worker = atoi(argv[3]);
    
    print_comparison_header();
    
    printf("Configuration: %d producers, %d consumers, %d messages per producer\n", 
           num_producers, num_consumers, messages_per_worker);
    printf("Total messages: %d\n\n", num_producers * messages_per_worker);
    
    /* Run actor-based benchmark (proper coroutine model) */
    printf(">>> Running ACTOR BENCHMARK (individual actor channels)...\n");
    
    kc_actor_bench_config_t actor_config = {
        .num_producers = (size_t)num_producers,
        .num_consumers = (size_t)num_consumers,
        .messages_per_producer = (size_t)messages_per_worker,
        .message_size = 32,
        .use_spin_loops = 0,
        .spin_iterations = 0
    };
    
    kc_actor_bench_result_t actor_result;
    int rc = kc_actor_bench_run(&actor_config, &actor_result);
    
    if (rc == 0) {
        printf("SUCCESS! Actor benchmark completed.\n\n");
        
        printf("=== ACTOR BENCHMARK RESULTS ===\n");
        printf("Throughput: %.1f Mpps (%.0f messages/sec)\n", 
               actor_result.messages_per_second / 1e6, actor_result.messages_per_second);
        printf("Duration: %.3f seconds\n", actor_result.total_time_seconds);
        printf("Bandwidth: %.3f Gbps\n", actor_result.gigabits_per_second);
        printf("Messages sent: %zu\n", actor_result.total_messages_sent);
        printf("Messages received: %zu\n", actor_result.total_messages_received);
        printf("Producer mismatches: %zu\n", actor_result.producer_mismatches);
        printf("Consumer mismatches: %zu\n", actor_result.consumer_mismatches);
        
        printf("\n=== PER-ACTOR METRICS ===\n");
        printf("Per-producer throughput: %.1f Mpps\n", 
               (actor_result.messages_per_second / 1e6) / num_producers);
        printf("Per-consumer throughput: %.1f Mpps\n", 
               (actor_result.messages_per_second / 1e6) / num_consumers);
        printf("Messages per producer: %d\n", messages_per_worker);
        
        size_t total_expected = (size_t)num_producers * (size_t)messages_per_worker;
        if (actor_result.total_messages_received == total_expected) {
            printf("✓ Perfect accuracy: All messages received\n");
        } else {
            printf("⚠ Message loss: %zu/%zu received (%.1f%%)\n", 
                   actor_result.total_messages_received, total_expected,
                   (actor_result.total_messages_received * 100.0) / total_expected);
        }
        
        printf("\n=== ARCHITECTURE SUMMARY ===\n");
        printf("Model: Actor-based (proper coroutine pattern)\n");
        printf("Channels: %d dedicated channels (one per consumer)\n", num_consumers);
        printf("Distribution: Round-robin from producers to consumers\n");
        printf("Contention: None (each actor has dedicated channel)\n");
        
        /* Performance assessment for 100Gbps target */
        double target_100gbps_pps = (100e9) / (64 * 8); /* 100Gbps at 64-byte packets */
        printf("\n=== 100 GBPS ASSESSMENT ===\n");
        printf("Target for 100Gbps (64-byte packets): %.1f Mpps\n", target_100gbps_pps / 1e6);
        printf("Current actor throughput: %.1f Mpps\n", actor_result.messages_per_second / 1e6);
        
        if (actor_result.messages_per_second >= target_100gbps_pps) {
            printf("✓ EXCEEDS 100Gbps target!\n");
        } else {
            double ratio = target_100gbps_pps / actor_result.messages_per_second;
            printf("⚠ Need %.1fx improvement for 100Gbps target\n", ratio);
        }
    } else {
        printf("FAILED: Actor benchmark returned error code %d\n", rc);
    }
    
    printf("\n=====================================\n");
    
    return rc;
}
