// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200112L
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <pthread.h>
#include <time.h>

#include "../include/kcoro.h"

static inline uint64_t nsec_now(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec*1000000000ull + (uint64_t)ts.tv_nsec;
}

typedef struct { kc_chan_t *ping; kc_chan_t *pong; int iters; } worker_arg_t;

static void* worker(void *arg)
{
    worker_arg_t *wa = (worker_arg_t*)arg;
    uint32_t v;
    for (int i = 0; i < wa->iters; ++i) {
        kc_chan_recv(wa->ping, &v, -1);
        kc_chan_send(wa->pong, &v, -1);
    }
    return NULL;
}

int main(int argc, char **argv)
{
    int iters = 200000; /* default */
    if (argc >= 2) iters = atoi(argv[1]);
    printf("[bench] latency: iters=%d (rendezvous ping-pong)\n", iters);

    kc_chan_t *ping=NULL, *pong=NULL;
    kc_chan_make(&ping, KC_RENDEZVOUS, sizeof(uint32_t), 0);
    kc_chan_make(&pong, KC_RENDEZVOUS, sizeof(uint32_t), 0);

    worker_arg_t wa = { .ping = ping, .pong = pong, .iters = iters };
    pthread_t th; pthread_create(&th, NULL, worker, &wa);

    uint32_t v = 0;
    const int warmup = 1000;
    for (int i=0;i<warmup;i++) { kc_chan_send(ping, &v, -1); kc_chan_recv(pong, &v, -1); }

    uint64_t t0 = nsec_now();
    for (int i=0;i<iters;i++) { kc_chan_send(ping, &v, -1); kc_chan_recv(pong, &v, -1); }
    uint64_t t1 = nsec_now();

    pthread_join(th, NULL);
    kc_chan_destroy(ping); kc_chan_destroy(pong);

    uint64_t ns = t1 - t0;
    double rt_ns = (double)ns / (double)iters;
    double one_way_ns = rt_ns / 2.0;
    double rt_us = rt_ns / 1000.0;
    printf("[bench] total_ns=%llu rt_ns=%.2f (%.2fus) one_way_ns=%.2f\n",
           (unsigned long long)ns, rt_ns, rt_us, one_way_ns);
    return 0;
}

