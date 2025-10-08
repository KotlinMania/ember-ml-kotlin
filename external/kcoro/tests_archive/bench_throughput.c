// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200112L
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <stdatomic.h>
#include <time.h>
#include <stdint.h>

#include "../include/kcoro.h"
#include "../include/kcoro_port.h"

static inline uint64_t nsec_now(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec*1000000000ull + (uint64_t)ts.tv_nsec;
}

typedef struct { kc_chan_t *ch; int id; int count; } producer_arg_t;
typedef struct { kc_chan_t *ch; atomic_int *received; } consumer_arg_t;

static void* producer_thread(void *arg)
{
    producer_arg_t *pa = (producer_arg_t*)arg;
    int v;
    for (int i=0;i<pa->count;i++) {
        v = (pa->id<<24) | i;
        kc_chan_send(pa->ch, &v, -1);
    }
    return NULL;
}

static void* consumer_thread(void *arg)
{
    consumer_arg_t *ca = (consumer_arg_t*)arg;
    int v;
    for (;;) {
        int rc = kc_chan_recv(ca->ch, &v, -1);
        if (rc == 0) atomic_fetch_add(ca->received, 1);
        else if (rc == KC_EPIPE) break;
    }
    return NULL;
}

int main(int argc, char **argv)
{
    int producers = 4, consumers = 4, per_prod = 250000, capacity = 1024;
    if (argc >= 5) { producers=atoi(argv[1]); consumers=atoi(argv[2]); per_prod=atoi(argv[3]); capacity=atoi(argv[4]); }
    int total = producers*per_prod;
    printf("[bench] throughput: P=%d C=%d N=%d cap=%d total=%d\n", producers, consumers, per_prod, capacity, total);

    kc_chan_t *ch=NULL; if (kc_chan_make(&ch, KC_BUFFERED, sizeof(int), (size_t)capacity) != 0) { fprintf(stderr, "make failed\n"); return 1; }

    pthread_t *pt = calloc((size_t)producers, sizeof(*pt));
    pthread_t *ct = calloc((size_t)consumers, sizeof(*ct));
    producer_arg_t *pargs = calloc((size_t)producers, sizeof(*pargs));
    consumer_arg_t carg; carg.ch = ch; atomic_int recvd; atomic_init(&recvd, 0); carg.received = &recvd;

    for (int i=0;i<consumers;i++) pthread_create(&ct[i], NULL, consumer_thread, &carg);
    for (int i=0;i<producers;i++) { pargs[i].ch=ch; pargs[i].id=i; pargs[i].count=per_prod; }

    uint64_t t0 = nsec_now();
    for (int i=0;i<producers;i++) pthread_create(&pt[i], NULL, producer_thread, &pargs[i]);
    for (int i=0;i<producers;i++) pthread_join(pt[i], NULL);
    kc_chan_close(ch);
    for (int i=0;i<consumers;i++) pthread_join(ct[i], NULL);
    uint64_t t1 = nsec_now();

    int got = atomic_load(&recvd);
    double sec = (double)(t1 - t0) / 1e9;
    double mps = (double)got / sec;
    printf("[bench] time=%.3fs msgs=%d rate=%.1f msg/s\n", sec, got, mps);
    kc_chan_destroy(ch); free(pt); free(ct); free(pargs);
    return (got == total) ? 0 : 2;
}
