// SPDX-License-Identifier: BSD-3-Clause
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <stdatomic.h>
#include <time.h>

#include "../include/kcoro.h"
#include "../include/kcoro_unified.h"
#include "../include/kcoro_sched.h"
#include "../include/kcoro_port.h"

typedef struct {
    kc_chan_t* ch;
    int id;
    int count;
    atomic_int* errors;
} prod_arg_t;

typedef struct {
    kc_chan_t* ch;
    int producers;
    atomic_int* per_counts; // size = producers
} cons_arg_t;

static inline double now_sec(void)
{
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec / 1e9;
}

static void prod_task(void* arg)
{
    prod_arg_t *pa = (prod_arg_t*)arg;
    for (int i = 0; i < pa->count; ++i) {
        int v = (pa->id << 24) | i;
        for (;;) {
            int rc = kc_chan_try_send(pa->ch, &v);
            if (rc == 0) break;
            if (rc == KC_EPIPE) { atomic_fetch_add(pa->errors, 1); return; }
            /* historical: kc_task_yield() removed; simple yield for fairness */
            sched_yield();
        }
    }
}

static void cons_task(void* arg)
{
    cons_arg_t *ca = (cons_arg_t*)arg;
    for (;;) {
        int v;
        int rc = kc_chan_try_recv(ca->ch, &v);
        if (rc == 0) {
            int pid = (v >> 24) & 0xFF;
            if (pid >= 0 && pid < ca->producers)
                atomic_fetch_add(&ca->per_counts[pid], 1);
        } else if (rc == KC_EPIPE) {
            break; // closed and drained
        } else if (rc == KC_EAGAIN) {
            /* historical: kc_task_yield() removed; simple yield for fairness */
            sched_yield();
        }
    }
}

int main(int argc, char **argv)
{
    int producers = 4, consumers = 4, per_prod = 100000, cap = 1024;
    if (argc >= 2) producers = atoi(argv[1]);
    if (argc >= 3) consumers = atoi(argv[2]);
    if (argc >= 4) per_prod  = atoi(argv[3]);
    if (argc >= 5) cap       = atoi(argv[4]);

    printf("=== kcoro Async Throughput (scheduler + try/yield) ===\n");
    printf("Producers=%d Consumers=%d PerProducer=%d Capacity=%d\n",
           producers, consumers, per_prod, cap);

    kc_sched_opts_t opts = { .workers = producers + consumers };
    kc_sched_t *sched = kc_sched_init(&opts);
    if (!sched) { fprintf(stderr, "scheduler init failed\n"); return 2; }

    kc_chan_t *ch = NULL; if (kc_chan_make(&ch, KC_BUFFERED, sizeof(int), (size_t)cap) != 0) {
        fprintf(stderr, "chan make failed\n");
        kc_sched_shutdown(sched);
        return 3;
    }

    atomic_int errors; atomic_init(&errors, 0);
    atomic_int *per_counts = calloc((size_t)producers, sizeof(*per_counts));
    if (!per_counts) { fprintf(stderr, "oom\n"); kc_chan_destroy(ch); kc_sched_shutdown(sched); return 4; }

    cons_arg_t carg = { .ch = ch, .producers = producers, .per_counts = per_counts };

    // Spawn consumers first
    for (int i = 0; i < consumers; ++i) {
        if (kc_spawn(sched, cons_task, &carg) != 0) { fprintf(stderr, "spawn cons %d failed\n", i); }
    }

    // Prepare producer args (stable storage)
    prod_arg_t *pargs = calloc((size_t)producers, sizeof(*pargs));
    if (!pargs) { fprintf(stderr, "oom\n"); kc_chan_destroy(ch); kc_sched_shutdown(sched); free(per_counts); return 5; }
    for (int i = 0; i < producers; ++i) {
        pargs[i].ch = ch; pargs[i].id = i; pargs[i].count = per_prod; pargs[i].errors = &errors;
    }

    double t0 = now_sec();
    for (int i = 0; i < producers; ++i) {
        if (kc_spawn(sched, prod_task, &pargs[i]) != 0) { fprintf(stderr, "spawn prod %d failed\n", i); }
    }

    // Wait for all expected messages
    const int expected = producers * per_prod;
    const double max_wait_s = 15.0; // safety timeout
    for (;;) {
        int got = 0;
        for (int i = 0; i < producers; ++i) got += atomic_load(&per_counts[i]);
        if (got >= expected) break;
        if ((now_sec() - t0) > max_wait_s) {
            fprintf(stderr, "[bench] timeout waiting for messages: got=%d expected=%d\n", got, expected);
            break;
        }
        kc_sleep_ms(1);
    }

    // Close and allow consumers to exit
    kc_chan_close(ch);
    kc_sleep_ms(10);
    double t1 = now_sec();

    int got = 0, bad = 0;
    for (int i = 0; i < producers; ++i) {
        int cnt = atomic_load(&per_counts[i]);
        got += cnt;
        if (cnt != per_prod) bad++;
    }
    int err = atomic_load(&errors);

    double dur = t1 - t0;
    double pps = dur > 0 ? (double)got / dur : 0.0;
    double gbps = (pps * 32.0 * 8.0) / 1e9; // assume 32B payload

    printf("Processed: %d/%d\n", got, expected);
    printf("Duration: %.6f s; Throughput: %.1f pps (%.3f Mpps); Bandwidth: %.3f Gbps\n",
           dur, pps, pps/1e6, gbps);
    printf("Per-producer mismatches: %d; Runtime errors: %d\n", bad, err);

    free(per_counts); free(pargs);
    kc_chan_destroy(ch);
    kc_sched_shutdown(sched);
    return got == expected && err == 0 ? 0 : 1;
}
