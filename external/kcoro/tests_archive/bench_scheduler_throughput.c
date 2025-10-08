// SPDX-License-Identifier: BSD-3-Clause
/* 
 * Scheduler-based throughput benchmark
 * 
 * This benchmark demonstrates the M:N threading model by creating many tasks
 * that cooperatively yield, showing how the scheduler handles task switching
 * efficiently without thread creation overhead.
 */
#define _POSIX_C_SOURCE 200112L
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdatomic.h>
#include <time.h>
#include <unistd.h>

#include "kcoro_unified.h"
#include "kcoro_port.h"

static inline uint64_t nsec_now(void) {
    struct timespec ts; 
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

typedef struct {
    kc_chan_t *ch;
    int producer_id;
    int messages_per_producer;
    atomic_int *total_sent;
} producer_task_t;

typedef struct {
    kc_chan_t *ch;
    atomic_int *total_received;
    int *stop_flag;
} consumer_task_t;

// Producer task function - runs as a coroutine
static void producer_task(void *arg) {
    producer_task_t *pt = (producer_task_t*)arg;
    
    for (int i = 0; i < pt->messages_per_producer; i++) {
        int msg = (pt->producer_id << 24) | i;
        
        // Use blocking send - will yield if channel full
        int rc = kc_chan_send(pt->ch, &msg, -1);
        if (rc != 0) {
            printf("Producer %d: send failed at %d\n", pt->producer_id, i);
            break;
        }
        
        atomic_fetch_add(pt->total_sent, 1);
        
        // Yield occasionally to show cooperative multitasking
        if (i % 100 == 0) {
            kc_yield();
        }
    }
    
    printf("Producer %d completed %d messages\n", pt->producer_id, pt->messages_per_producer);
}

// Consumer task function - runs as a coroutine  
static void consumer_task(void *arg) {
    consumer_task_t *ct = (consumer_task_t*)arg;
    int msg;
    
    while (!*ct->stop_flag) {
        int rc = kc_chan_recv(ct->ch, &msg, 50);  // 50ms timeout
        
        if (rc == 0) {
            atomic_fetch_add(ct->total_received, 1);
            
            // Yield every few messages to show cooperative scheduling
            if (atomic_load(ct->total_received) % 50 == 0) {
                kc_yield();
            }
        } else if (rc == KC_ETIME) {
            // Timeout - check stop flag and yield
            kc_yield();
        } else if (rc == KC_EPIPE) {
            // Channel closed
            break;
        }
    }
}

int main(int argc, char **argv) {
    int num_producers = 4;
    int num_consumers = 2;  
    int messages_per_producer = 1000;
    int channel_capacity = 128;
    
    if (argc >= 2) num_producers = atoi(argv[1]);
    if (argc >= 3) num_consumers = atoi(argv[2]);
    if (argc >= 4) messages_per_producer = atoi(argv[3]);
    if (argc >= 5) channel_capacity = atoi(argv[4]);
    
    int total_expected = num_producers * messages_per_producer;
    
    printf("[bench_scheduler] M:N Coroutine Throughput Test\n");
    printf("Producers: %d, Consumers: %d, Messages/Producer: %d\n", 
           num_producers, num_consumers, messages_per_producer);
    printf("Channel capacity: %d, Total expected: %d\n", 
           channel_capacity, total_expected);
    
    // Initialize scheduler  
    kc_sched_opts_t opts = { .workers = 2, .queue_capacity = 0 };
    kc_sched_t *sched = kc_sched_init(&opts);
    if (!sched) {
        fprintf(stderr, "Failed to initialize scheduler\n");
        return 1;
    }
    
    // Create channel
    kc_chan_t *ch = NULL;
    if (kc_chan_make(&ch, KC_BUFFERED, sizeof(int), channel_capacity) != 0) {
        fprintf(stderr, "Failed to create channel\n");
        return 1;
    }
    
    // Shared counters
    atomic_int total_sent, total_received;
    atomic_init(&total_sent, 0);
    atomic_init(&total_received, 0);
    int stop_flag = 0;
    
    // Create producer task args
    producer_task_t *producer_args = calloc(num_producers, sizeof(producer_task_t));
    for (int i = 0; i < num_producers; i++) {
        producer_args[i].ch = ch;
        producer_args[i].producer_id = i;
        producer_args[i].messages_per_producer = messages_per_producer;
        producer_args[i].total_sent = &total_sent;
    }
    
    // Create consumer task args
    consumer_task_t *consumer_args = calloc(num_consumers, sizeof(consumer_task_t));
    for (int i = 0; i < num_consumers; i++) {
        consumer_args[i].ch = ch;
        consumer_args[i].total_received = &total_received;
        consumer_args[i].stop_flag = &stop_flag;
    }
    
    printf("Starting benchmark...\n");
    uint64_t start_time = nsec_now();
    
    // Spawn consumer tasks first
    for (int i = 0; i < num_consumers; i++) {
        if (kc_spawn(sched, consumer_task, &consumer_args[i]) != 0) {
            fprintf(stderr, "Failed to spawn consumer task %d\n", i);
            return 1;
        }
    }
    
    // Spawn producer tasks
    for (int i = 0; i < num_producers; i++) {
        if (kc_spawn(sched, producer_task, &producer_args[i]) != 0) {
            fprintf(stderr, "Failed to spawn producer task %d\n", i);
            return 1;
        }
    }
    
    // Let tasks run - estimate time needed based on throughput
    int estimated_seconds = (total_expected / 50000) + 2;  // Rough estimate + buffer
    if (estimated_seconds > 10) estimated_seconds = 10;    // Cap at 10 seconds
    if (estimated_seconds < 1) estimated_seconds = 1;      // At least 1 second
    
    printf("Letting tasks run for %d seconds...\n", estimated_seconds);
    sleep(estimated_seconds);
    
    uint64_t end_time = nsec_now();
    stop_flag = 1;
    
    // Close channel to wake up any waiting consumers
    kc_chan_close(ch);
    
    // Give consumers time to exit cleanly
    sleep(1);
    
    // Results
    int final_sent = atomic_load(&total_sent);
    int final_received = atomic_load(&total_received);
    double duration_sec = (double)(end_time - start_time) / 1e9;
    double throughput_mps = (double)final_received / duration_sec;
    
    printf("\n[RESULTS]\n");
    printf("Duration: %.3f seconds\n", duration_sec);
    printf("Messages sent: %d/%d (%.1f%%)\n", final_sent, total_expected, 
           100.0 * final_sent / total_expected);
    printf("Messages received: %d/%d (%.1f%%)\n", final_received, total_expected,
           100.0 * final_received / total_expected);
    printf("Throughput: %.1f messages/second\n", throughput_mps);
    printf("Throughput: %.3f M msg/sec\n", throughput_mps / 1e6);
    
    if (final_received >= total_expected * 0.95) {  // Allow 5% tolerance
        printf("✅ Benchmark completed successfully\n");
    } else if (final_received > 0) {
        printf("⚠️  Partial completion - some messages may still be in flight\n");
    } else {
        printf("❌ No messages received - check for deadlock\n");
    }
    
    // Cleanup
    kc_chan_destroy(ch);
    kc_sched_shutdown(sched);
    free(producer_args);
    free(consumer_args);
    
    return (final_received >= total_expected * 0.90) ? 0 : 1;  // 90% success threshold
}
