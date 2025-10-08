// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200112L
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <pthread.h>
#include <stdatomic.h>
#include <time.h>
#include <string.h>
#include <unistd.h>

#include "../include/kcoro.h"
#include "../include/kcoro_bench.h"

static inline uint64_t nsec_now(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

typedef struct {
    kc_chan_t *ch;
    atomic_ulong *sent;
    int id;
    int count;
    size_t elem_size;
    int spin_iters;
    volatile int *stop_flag;
} prod_ctx_t;

typedef struct {
    kc_chan_t *ch;
    atomic_ulong *received;
    size_t elem_size;
    int spin_iters;
    volatile int *stop_flag;
} cons_ctx_t;

static void* benchmark_producer(void *arg)
{
    prod_ctx_t *ctx = (prod_ctx_t*)arg;
    uint8_t *buf = malloc(ctx->elem_size);
    if (!buf) return NULL;
    
    // Fill buffer with producer ID pattern
    memset(buf, (unsigned)(ctx->id & 0xff), ctx->elem_size);
    
    for (int i = 0; i < ctx->count && !*ctx->stop_flag; i++) {
        int spin_count = 0;
        
        // Busy-wait optimization: spin before blocking
        while (spin_count < ctx->spin_iters && !*ctx->stop_flag) {
            int rc = kc_chan_send(ctx->ch, buf, 0); // try non-blocking first
            if (rc == 0) {
                atomic_fetch_add(ctx->sent, 1);
                goto next_msg;
            } else if (rc == -32) { // KC_EPIPE - channel closed
                goto cleanup;
            }
            spin_count++;
            // CPU yield hint for better spin efficiency (ARM64 compatible)
            #if defined(__x86_64__) || defined(__i386__)
            __asm__ volatile ("pause" ::: "memory");
            #elif defined(__aarch64__) || defined(__arm__)
            __asm__ volatile ("yield" ::: "memory");
            #else
            __asm__ volatile ("" ::: "memory"); // compiler barrier only
            #endif
        }
        
        // Fallback to blocking send if spin didn't succeed
        int rc = kc_chan_send(ctx->ch, buf, -1);
        if (rc == 0) {
            atomic_fetch_add(ctx->sent, 1);
        } else if (rc == -32) { // KC_EPIPE
            break;
        }
        
        next_msg:;
    }
    
    cleanup:
    free(buf);
    return NULL;
}

static void* benchmark_consumer(void *arg)
{
    cons_ctx_t *ctx = (cons_ctx_t*)arg;
    uint8_t *buf = malloc(ctx->elem_size);
    if (!buf) return NULL;
    
    while (!*ctx->stop_flag) {
        int spin_count = 0;
        
        // Busy-wait optimization: spin before blocking
        while (spin_count < ctx->spin_iters && !*ctx->stop_flag) {
            int rc = kc_chan_recv(ctx->ch, buf, 0); // try non-blocking first
            if (rc == 0) {
                atomic_fetch_add(ctx->received, 1);
                goto next_msg;
            } else if (rc == -32) { // KC_EPIPE - channel closed
                goto cleanup;
            }
            spin_count++;
            // CPU yield hint for better spin efficiency (ARM64 compatible)
            #if defined(__x86_64__) || defined(__i386__)
            __asm__ volatile ("pause" ::: "memory");
            #elif defined(__aarch64__) || defined(__arm__)
            __asm__ volatile ("yield" ::: "memory");
            #else
            __asm__ volatile ("" ::: "memory"); // compiler barrier only
            #endif
        }
        
        // Fallback to blocking recv if spin didn't succeed
        int rc = kc_chan_recv(ctx->ch, buf, -1);
        if (rc == 0) {
            atomic_fetch_add(ctx->received, 1);
        } else if (rc == -32) { // KC_EPIPE
            break;
        }
        
        next_msg:;
    }
    
    cleanup:
    free(buf);
    return NULL;
}

int kc_bench_run(const kc_bench_config_t *cfg, kc_bench_result_t *out)
{
    if (!cfg || !out) return -1;
    
    // Initialize result structure
    memset(out, 0, sizeof(*out));
    
    // Create channel
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make(&ch, KC_BUFFERED, cfg->msg_size, cfg->capacity);
    if (rc != 0) return rc;
    
    // Allocate thread arrays
    pthread_t *producers = calloc(cfg->producers, sizeof(pthread_t));
    pthread_t *consumers = calloc(cfg->consumers, sizeof(pthread_t));
    prod_ctx_t *prod_ctx = calloc(cfg->producers, sizeof(prod_ctx_t));
    cons_ctx_t *cons_ctx = calloc(cfg->consumers, sizeof(cons_ctx_t));
    
    if (!producers || !consumers || !prod_ctx || !cons_ctx) {
        free(producers); free(consumers); free(prod_ctx); free(cons_ctx);
        kc_chan_destroy(ch);
        return -1;
    }
    
    // Shared atomics and control
    atomic_ulong total_sent, total_received;
    atomic_init(&total_sent, 0);
    atomic_init(&total_received, 0);
    volatile int stop_flag = 0;
    
    // Set up spin iterations (use environment override if available)
    int spin_iters = cfg->spin_iters >= 0 ? cfg->spin_iters : 1024;
    const char *env_spin = getenv("KCORO_BENCH_SPIN");
    if (env_spin && *env_spin) {
        spin_iters = atoi(env_spin);
    }
    
    // Initialize contexts
    for (int i = 0; i < cfg->producers; i++) {
        prod_ctx[i].ch = ch;
        prod_ctx[i].sent = &total_sent;
        prod_ctx[i].id = i;
        prod_ctx[i].count = cfg->per_producer;
        prod_ctx[i].elem_size = cfg->msg_size;
        prod_ctx[i].spin_iters = spin_iters;
        prod_ctx[i].stop_flag = &stop_flag;
    }
    
    for (int i = 0; i < cfg->consumers; i++) {
        cons_ctx[i].ch = ch;
        cons_ctx[i].received = &total_received;
        cons_ctx[i].elem_size = cfg->msg_size;
        cons_ctx[i].spin_iters = spin_iters;
        cons_ctx[i].stop_flag = &stop_flag;
    }
    
    // Start consumers first
    for (int i = 0; i < cfg->consumers; i++) {
        if (pthread_create(&consumers[i], NULL, benchmark_consumer, &cons_ctx[i]) != 0) {
            // Handle startup failure
            stop_flag = 1;
            for (int j = 0; j < i; j++) {
                pthread_join(consumers[j], NULL);
            }
            free(producers); free(consumers); free(prod_ctx); free(cons_ctx);
            kc_chan_destroy(ch);
            return -1;
        }
    }
    
    // Record start time and launch producers
    uint64_t start_ns = nsec_now();
    
    for (int i = 0; i < cfg->producers; i++) {
        if (pthread_create(&producers[i], NULL, benchmark_producer, &prod_ctx[i]) != 0) {
            // Handle startup failure
            stop_flag = 1;
            for (int j = 0; j < i; j++) {
                pthread_join(producers[j], NULL);
            }
            for (int j = 0; j < cfg->consumers; j++) {
                pthread_join(consumers[j], NULL);
            }
            free(producers); free(consumers); free(prod_ctx); free(cons_ctx);
            kc_chan_destroy(ch);
            return -1;
        }
    }
    
    // Wait for all producers to complete
    for (int i = 0; i < cfg->producers; i++) {
        pthread_join(producers[i], NULL);
    }
    
    // Close channel to signal consumers
    kc_chan_close(ch);
    
    // Wait for consumers to drain
    for (int i = 0; i < cfg->consumers; i++) {
        pthread_join(consumers[i], NULL);
    }
    
    uint64_t end_ns = nsec_now();
    
    // Calculate results
    out->processed = (int)atomic_load(&total_received);
    out->expected = cfg->producers * cfg->per_producer;
    out->duration_s = (double)(end_ns - start_ns) / 1e9;
    out->pps = out->duration_s > 0 ? (double)out->processed / out->duration_s : 0.0;
    out->gbps = out->duration_s > 0 ? (out->pps * cfg->pkt_bytes * 8.0) / 1e9 : 0.0;
    
    // Check for mismatches (producers that didn't send their full quota)
    unsigned long sent = atomic_load(&total_sent);
    out->per_prod_mismatch = (sent != (unsigned long)out->expected) ? 1 : 0;
    out->runtime_errors = 0; // TODO: could track individual send/recv errors
    
    // Cleanup
    free(producers);
    free(consumers);
    free(prod_ctx);
    free(cons_ctx);
    kc_chan_destroy(ch);
    
    return 0;
}
