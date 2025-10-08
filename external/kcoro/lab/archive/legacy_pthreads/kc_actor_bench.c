// SPDX-License-Identifier: BSD-3-Clause
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#define _POSIX_C_SOURCE 200809L

#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "../../include/kcoro.h"
#include "../../include/kcoro_port.h"
#include "../../include/kc_actor_bench.h"

typedef struct {
    size_t num_producers;
    size_t num_consumers; 
    size_t messages_per_producer;
    size_t message_size;
    int use_spin_loops;
    size_t spin_iterations;
    kc_chan_t **consumer_channels;  /* array of channels, one per consumer */
    atomic_size_t messages_sent;
    atomic_size_t messages_received;
    atomic_size_t producer_mismatches;
    atomic_size_t consumer_mismatches;
    struct timespec start_time;
    struct timespec end_time;
} actor_bench_state_t;

typedef struct {
    size_t message_id;
    size_t producer_id;
    char payload[32];
} actor_test_message_t;

static void do_spin_work(size_t iterations)
{
    if (iterations == 0) return;
    volatile size_t dummy = 0;
    for (size_t i = 0; i < iterations; i++) dummy += i;
}

typedef struct { actor_bench_state_t* state; size_t thread_id; } thread_data_t;

static void* producer_actor(void* arg)
{
    thread_data_t* data = (thread_data_t*)arg;
    actor_bench_state_t* state = data->state;
    size_t producer_id = data->thread_id;
    for (size_t i = 0; i < state->messages_per_producer; i++) {
        actor_test_message_t msg = { .message_id = i, .producer_id = producer_id };
        size_t consumer_idx = i % state->num_consumers;
        kc_chan_t* target = state->consumer_channels[consumer_idx];
        int rc = kc_chan_send(target, &msg, -1);
        if (rc == 0) atomic_fetch_add(&state->messages_sent, 1);
        else atomic_fetch_add(&state->producer_mismatches, 1);
        do_spin_work(state->spin_iterations);
    }
    return NULL;
}

static int actor_consumer_process(const void* msg, void* user)
{
    actor_bench_state_t* state = (actor_bench_state_t*)user;
    if (!msg || !state) return -1;
    atomic_fetch_add(&state->messages_received, 1);
    do_spin_work(state->spin_iterations);
    return 0;
}

static void* benchmark_coordinator(void *arg)
{
    actor_bench_state_t *state = (actor_bench_state_t*)arg;
    clock_gettime(CLOCK_MONOTONIC, &state->start_time);

    pthread_t* producer_threads = malloc(state->num_producers * sizeof(pthread_t));
    thread_data_t* producer_data = malloc(state->num_producers * sizeof(thread_data_t));
    for (size_t i = 0; i < state->num_producers; i++) {
        producer_data[i].state = state; producer_data[i].thread_id = i;
        pthread_create(&producer_threads[i], NULL, producer_actor, &producer_data[i]);
    }

    kc_actor_t* consumers = calloc(state->num_consumers, sizeof(kc_actor_t));
    kc_cancel_t** cancels = calloc(state->num_consumers, sizeof(kc_cancel_t*));
    for (size_t i = 0; i < state->num_consumers; i++) {
        kc_cancel_init(&cancels[i]);
        kc_actor_ctx_ex_t ex = {0};
        ex.base.chan = state->consumer_channels[i];
        ex.base.msg_size = sizeof(actor_test_message_t);
        ex.base.timeout_ms = -1; /* blocking with cancellation */
        ex.base.process = actor_consumer_process;
        ex.base.user = state;
        ex.cancel = cancels[i];
        consumers[i] = kc_actor_start_ex(&ex);
    }

    for (size_t i = 0; i < state->num_producers; i++) pthread_join(producer_threads[i], NULL);
    for (size_t i = 0; i < state->num_consumers; i++) kc_chan_close(state->consumer_channels[i]);
    for (size_t i = 0; i < state->num_consumers; i++) if (consumers[i]) kc_actor_cancel(consumers[i]);

    clock_gettime(CLOCK_MONOTONIC, &state->end_time);
    free(producer_threads); free(producer_data);
    for (size_t i = 0; i < state->num_consumers; i++) if (cancels[i]) kc_cancel_destroy(cancels[i]);
    free(consumers); free(cancels);
    return NULL;
}

int kc_actor_bench_run(const kc_actor_bench_config_t* config, kc_actor_bench_result_t* result)
{
    if (!config || !result) return -1;

    actor_bench_state_t state = {0};
    state.num_producers = config->num_producers;
    state.num_consumers = config->num_consumers;
    state.messages_per_producer = config->messages_per_producer;
    state.message_size = config->message_size;
    state.use_spin_loops = config->use_spin_loops;
    state.spin_iterations = config->spin_iterations;
    atomic_store(&state.messages_sent, 0);
    atomic_store(&state.messages_received, 0);
    atomic_store(&state.producer_mismatches, 0);
    atomic_store(&state.consumer_mismatches, 0);

    state.consumer_channels = calloc(state.num_consumers, sizeof(kc_chan_t*));
    if (!state.consumer_channels) return -1;
    for (size_t i = 0; i < state.num_consumers; i++) {
        if (kc_chan_make(&state.consumer_channels[i], KC_BUFFERED,
                         sizeof(actor_test_message_t), 64) != 0) {
            for (size_t j = 0; j < i; j++) kc_chan_destroy(state.consumer_channels[j]);
            free(state.consumer_channels);
            return -1;
        }
    }

    pthread_t coord;
    if (pthread_create(&coord, NULL, benchmark_coordinator, &state) != 0) {
        for (size_t i = 0; i < state.num_consumers; i++) kc_chan_destroy(state.consumer_channels[i]);
        free(state.consumer_channels);
        return -1;
    }
    pthread_join(coord, NULL);

    double duration_sec = (state.end_time.tv_sec - state.start_time.tv_sec) +
                          (state.end_time.tv_nsec - state.start_time.tv_nsec) / 1e9;
    size_t messages_sent = atomic_load(&state.messages_sent);
    size_t messages_received = atomic_load(&state.messages_received);

    result->total_time_seconds = duration_sec;
    result->messages_per_second = duration_sec > 0 ? (messages_received / duration_sec) : 0.0;
    result->gigabits_per_second = duration_sec > 0 ? ((messages_received * state.message_size * 8) / (duration_sec * 1e9)) : 0.0;
    result->total_messages_sent = messages_sent;
    result->total_messages_received = messages_received;
    result->producer_mismatches = atomic_load(&state.producer_mismatches);
    result->consumer_mismatches = atomic_load(&state.consumer_mismatches);

    for (size_t i = 0; i < state.num_consumers; i++) kc_chan_destroy(state.consumer_channels[i]);
    free(state.consumer_channels);
    return (messages_received == state.num_producers * state.messages_per_producer) ? 0 : -1;
}

