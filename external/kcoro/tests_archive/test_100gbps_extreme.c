// SPDX-License-Identifier: BSD-3-Clause
// Extreme 100Gbps packet analysis stress test
#define _GNU_SOURCE
#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <stdatomic.h>
#include <time.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sched.h>
#include <sys/syscall.h>
#include <stdint.h>
#include "../include/kcoro.h"
#include "../include/kcoro_port.h"

// Extreme 100Gbps test - 8 producers, 4 consumers, 2M messages per producer = 16M total
// This simulates: 8 NIC queues feeding 4 packet analysis cores
// Target: Handle 148M pps (100Gbps @ 64-byte packets)

typedef struct {
    kc_chan_t *ch;
    int id;
    int count;
    atomic_int *errors;
    atomic_long *total_latency_ns;
} producer_arg_t;

typedef struct {
    kc_chan_t *ch;
    atomic_int *received;
    atomic_int *errors;
    atomic_int *out_of_range_errors;
    int num_producers;
    int per_producer;
    atomic_long *total_latency_ns;
} consumer_arg_t;

static inline uint64_t get_cycles(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000000000UL + ts.tv_nsec;
}

static void* producer_thread(void *arg)
{
    producer_arg_t *pa = (producer_arg_t*)arg;
    
    // Pin to specific CPU core
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(pa->id % 8, &cpuset);  // Distribute across first 8 cores
    pthread_setaffinity_np(pthread_self(), sizeof(cpu_set_t), &cpuset);
    
    // Maximum priority real-time scheduling
    struct sched_param sp;
    sp.sched_priority = 99;
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);
    
    printf("[producer %d] Starting extreme 100Gbps simulation on CPU %d\n", pa->id, pa->id % 8);
    
    uint64_t start_time, end_time;
    
    // Extreme tight loop - no yields, no batching, pure speed
    for (int i = 0; i < pa->count; i++) {
        int v = (pa->id << 24) | i;
        
        start_time = get_cycles();
        
        // Ultra-aggressive: try non-blocking first, then single retry with minimal timeout
        int rc = kc_chan_send(pa->ch, &v, 0);
        if (rc == KC_EAGAIN) {
            rc = kc_chan_send(pa->ch, &v, 0);  // Immediate retry
        }
        
        end_time = get_cycles();
        atomic_fetch_add(pa->total_latency_ns, (end_time - start_time));
        
        if (rc != 0) {
            atomic_fetch_add(pa->errors, 1);
            // Drop packet and continue - can't block 100Gbps packet flow
        }
    }
    
    printf("[producer %d] Completed %d packets\n", pa->id, pa->count);
    return NULL;
}

static void* consumer_thread(void *arg)
{
    consumer_arg_t *ca = (consumer_arg_t*)arg;
    int v;
    int processed_count = 0;
    
    // Pin to specific CPU cores (offset from producers)
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET((processed_count % 4) + 8, &cpuset);  // Use cores 8-11 for consumers
    pthread_setaffinity_np(pthread_self(), sizeof(cpu_set_t), &cpuset);
    
    // High priority real-time scheduling
    struct sched_param sp;
    sp.sched_priority = 95;  // Slightly lower than producers
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);
    
    printf("[consumer] Starting packet analysis on CPU %d\n", (processed_count % 4) + 8);
    
    uint64_t start_time, end_time;
    
    // Extreme packet processing loop
    for (;;) {
        start_time = get_cycles();
        
        int rc = kc_chan_recv(ca->ch, &v, 0);  // Pure non-blocking
        
        if (rc == 0) {
            end_time = get_cycles();
            atomic_fetch_add(ca->total_latency_ns, (end_time - start_time));
            
            atomic_fetch_add(ca->received, 1);
            processed_count++;
            
            // Minimal packet validation - just bounds check
            int producer_id = (v >> 24) & 0xFF;
            if (__builtin_expect(producer_id >= ca->num_producers, 0)) {
                atomic_fetch_add(ca->out_of_range_errors, 1);
            }
            
            // Simulate minimal packet analysis work
            // In real system: DPI, counter updates, pattern matching
            __asm__ __volatile__("" ::: "memory");  // Compiler barrier
            
        } else if (rc == KC_EPIPE) {
            break;
        } else {
            // Brief pause on empty queue to prevent 100% CPU spinning
            if ((processed_count & 0xFFFF) == 0) {
                struct timespec ts = {0, 1};  // 1ns
                nanosleep(&ts, NULL);
            }
        }
    }
    
    printf("[consumer] Processed %d packets\n", processed_count);
    return NULL;
}

int main(int argc, char **argv)
{
    // Extreme 100Gbps test parameters
    int producers = 8, consumers = 4, per_prod = 2000000;  // 16M total packets
    if (argc == 4) { 
        producers = atoi(argv[1]); 
        consumers = atoi(argv[2]); 
        per_prod = atoi(argv[3]); 
    }
    
    printf("[extreme] === EXTREME 100Gbps Packet Analysis Test ===\n");
    printf("[extreme] Configuration: P=%d C=%d N=%d (total: %d packets)\n", 
           producers, consumers, per_prod, producers * per_prod);
    printf("[extreme] Target: >100M pps for full 100Gbps @ 64-byte packets\n");
    
    // Massive buffer - 4M entries = 16MB for extreme burst handling
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make(&ch, KC_BUFFERED, sizeof(int), 4194304);  // 4M entries
    if (rc) { 
        fprintf(stderr, "[extreme] Channel creation failed rc=%d\n", rc); 
        return 1; 
    }
    
    // Allocate thread arrays
    pthread_t *pt = calloc(producers, sizeof(*pt));
    pthread_t *ct = calloc(consumers, sizeof(*ct));
    producer_arg_t *pargs = calloc(producers, sizeof(*pargs));
    
    // Shared performance counters
    atomic_int recvd, errors, range_errors;
    atomic_long producer_latency, consumer_latency;
    atomic_init(&recvd, 0);
    atomic_init(&errors, 0);
    atomic_init(&range_errors, 0);
    atomic_init(&producer_latency, 0);
    atomic_init(&consumer_latency, 0);
    
    // Consumer setup
    consumer_arg_t carg = {
        .ch = ch,
        .received = &recvd,
        .errors = &errors,
        .out_of_range_errors = &range_errors,
        .num_producers = producers,
        .per_producer = per_prod,
        .total_latency_ns = &consumer_latency
    };
    
    struct timespec start, end;
    clock_gettime(CLOCK_MONOTONIC, &start);
    
    // Start consumers first (they need to be ready)
    for (int i = 0; i < consumers; i++) {
        pthread_create(&ct[i], NULL, consumer_thread, &carg);
    }
    
    // Brief delay to let consumers initialize
    usleep(1000);
    
    // Start producers with maximum aggression
    for (int i = 0; i < producers; i++) {
        pargs[i].ch = ch;
        pargs[i].id = i;
        pargs[i].count = per_prod;
        pargs[i].errors = &errors;
        pargs[i].total_latency_ns = &producer_latency;
        pthread_create(&pt[i], NULL, producer_thread, &pargs[i]);
    }
    
    // Wait for producers to finish
    for (int i = 0; i < producers; i++) {
        pthread_join(pt[i], NULL);
    }
    
    // Close channel and wait for consumers to drain
    kc_chan_close(ch);
    for (int i = 0; i < consumers; i++) {
        pthread_join(ct[i], NULL);
    }
    
    clock_gettime(CLOCK_MONOTONIC, &end);
    double elapsed = (end.tv_sec - start.tv_sec) + (end.tv_nsec - start.tv_nsec) / 1e9;
    
    // Results analysis
    int expected = producers * per_prod;
    int got = atomic_load(&recvd);
    int error_count = atomic_load(&errors);
    int range_error_count = atomic_load(&range_errors);
    long prod_latency = atomic_load(&producer_latency);
    long cons_latency = atomic_load(&consumer_latency);
    
    // Performance calculations
    double packets_per_sec = got / elapsed;
    double throughput_gbps = (got * 64 * 8) / (elapsed * 1000000000.0);  // 64-byte packets
    double loss_rate = 100.0 * (expected - got) / expected;
    
    printf("\n[extreme] === EXTREME 100Gbps Performance Results ===\n");
    printf("[extreme] Packet Processing:\n");
    printf("[extreme]   Processed: %d/%d packets (%.3f%% success)\n", got, expected, 100.0 - loss_rate);
    printf("[extreme]   Duration: %.6fs\n", elapsed);
    printf("[extreme]   Throughput: %.1f Mpps\n", packets_per_sec / 1000000);
    printf("[extreme]   Bandwidth: %.1f Gbps (64-byte packets)\n", throughput_gbps);
    
    printf("[extreme] Error Analysis:\n");
    printf("[extreme]   Runtime errors: %d (%.4f%%)\n", error_count, 100.0 * error_count / expected);
    printf("[extreme]   Invalid messages: %d (%.4f%%)\n", range_error_count, 100.0 * range_error_count / got);
    printf("[extreme]   Packet loss: %.4f%%\n", loss_rate);
    
    printf("[extreme] Latency Analysis:\n");
    printf("[extreme]   Avg producer latency: %.1f cycles\n", (double)prod_latency / (producers * per_prod));
    printf("[extreme]   Avg consumer latency: %.1f cycles\n", (double)cons_latency / got);
    
    // Performance assessment
    if (packets_per_sec >= 100000000) {
        printf("[extreme]   🚀 OUTSTANDING: >100M pps - Full 100Gbps capability!\n");
    } else if (packets_per_sec >= 50000000) {
        printf("[extreme]   ✅ EXCELLENT: >50M pps - Can handle 50Gbps+\n");
    } else if (packets_per_sec >= 10000000) {
        printf("[extreme]   ✓ GOOD: >10M pps - Basic 100Gbps capability\n");
    } else {
        printf("[extreme]   ⚠️  LIMITED: <10M pps - May struggle with 100Gbps\n");
    }
    
    // Cleanup
    kc_chan_destroy(ch);
    free(pt);
    free(ct);
    free(pargs);
    
    // Pass/fail criteria for extreme test
    if (loss_rate > 1.0) {  // Allow up to 1% loss for extreme test
        fprintf(stderr, "[extreme] FAIL: Excessive packet loss %.3f%% (threshold: 1.0%%)\n", loss_rate);
        return 2;
    }
    if (error_count > expected / 100) {  // Max 1% errors
        fprintf(stderr, "[extreme] FAIL: Too many runtime errors %d (threshold: %d)\n", error_count, expected / 100);
        return 3;
    }
    if (packets_per_sec < 5000000) {  // Minimum 5M pps
        fprintf(stderr, "[extreme] FAIL: Insufficient throughput %.1f Mpps (minimum: 5M)\n", packets_per_sec / 1000000);
        return 4;
    }
    
    printf("[extreme] PASS: Extreme test successful!\n");
    return 0;
}
