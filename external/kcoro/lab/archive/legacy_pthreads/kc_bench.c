// SPDX-License-Identifier: BSD-3-Clause
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#define _POSIX_C_SOURCE 200112L
#include <pthread.h>
#include <stdatomic.h>
#include <sched.h>
#include <time.h>
#include <unistd.h>
#include <stdlib.h>

#include "../../include/kcoro.h"
#include "../../include/kcoro_bench.h"
#include "../../include/kcoro_port.h"

typedef struct prod_arg {
    kc_chan_t   *ch;
    int          id;
    int          count;
    atomic_int  *errors;
    size_t       msg_size;
} prod_arg_t;

typedef struct cons_arg {
    kc_chan_t   *ch;
    atomic_int  *per_counts; /* size = producers */
    int          producers;
    size_t       msg_size;
} cons_arg_t;

static int g_spin_iters = 0;
static int g_pin_threads = 0;

static void* prod_fn(void *arg)
{
    prod_arg_t *pa = (prod_arg_t*)arg;
#ifdef __linux__
    if (g_pin_threads) {
        cpu_set_t set; CPU_ZERO(&set);
        long n = sysconf(_SC_NPROCESSORS_ONLN);
        int cpu = (n > 0) ? (pa->id % (int)n) : 0;
        CPU_SET(cpu, &set);
        sched_setaffinity(0, sizeof(set), &set);
    }
#endif
    int v;
    for (int i = 0; i < pa->count; ++i) {
        v = (pa->id << 24) | i;
        for (;;) {
            int rc = kc_chan_try_send(pa->ch, &v);
            if (rc == 0) break;
            if (rc == KC_EPIPE) { atomic_fetch_add(pa->errors, 1); goto out; }
            for (int k=0;k<g_spin_iters;k++) {
                rc = kc_chan_try_send(pa->ch, &v);
                if (rc == 0) goto sent;
                if (rc == KC_EPIPE) { atomic_fetch_add(pa->errors, 1); goto out; }
            }
            sched_yield();
sent: ;
        }
    }
out:
    return NULL;
}

static void* cons_fn(void *arg)
{
    cons_arg_t *ca = (cons_arg_t*)arg;
    int v;
    for (;;) {
        int rc = kc_chan_try_recv(ca->ch, &v);
        if (rc == 0) {
            int pid = (v >> 24) & 0xFF;
            if (pid >= 0 && pid < ca->producers)
                atomic_fetch_add(&ca->per_counts[pid], 1);
        } else if (rc == KC_EPIPE) {
            break;
        } else if (rc == KC_EAGAIN) {
            for (int k=0;k<g_spin_iters;k++) {
                rc = kc_chan_try_recv(ca->ch, &v);
                if (rc == 0) {
                    int pid = (v >> 24) & 0xFF;
                    if (pid >= 0 && pid < ca->producers)
                        atomic_fetch_add(&ca->per_counts[pid], 1);
                    goto next;
                }
                if (rc == KC_EPIPE) goto done;
            }
            sched_yield();
        }
next: ;
    }
done:
    return NULL;
}

static inline double now_sec(void)
{
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec / 1e9;
}

int kc_bench_run(const kc_bench_config_t *cfg, kc_bench_result_t *out)
{
    if (!cfg || !out) return -1;
    int producers = cfg->producers > 0 ? cfg->producers : 1;
    int consumers = cfg->consumers > 0 ? cfg->consumers : 1;
    int per_prod  = cfg->per_producer > 0 ? cfg->per_producer : 1;
    size_t cap    = cfg->capacity > 0 ? cfg->capacity : 1024;
    size_t msg_sz = cfg->msg_size > 0 ? cfg->msg_size : sizeof(int);
    int pkt       = cfg->pkt_bytes > 0 ? cfg->pkt_bytes : 64;

    out->expected = producers * per_prod;
    g_spin_iters = (cfg->spin_iters >= 0) ? cfg->spin_iters : 0;
    g_pin_threads = cfg->pin_threads ? 1 : 0;

    kc_chan_t *ch = NULL; if (kc_chan_make(&ch, KC_BUFFERED, msg_sz, cap) != 0) return -2;

    pthread_t *pt = calloc((size_t)producers, sizeof(*pt));
    pthread_t *ct = calloc((size_t)consumers, sizeof(*ct));
    prod_arg_t *pargs = calloc((size_t)producers, sizeof(*pargs));
    atomic_int *per_counts = calloc((size_t)producers, sizeof(*per_counts));
    atomic_int errors; atomic_init(&errors, 0);

    cons_arg_t carg = { .ch = ch, .per_counts = per_counts, .producers = producers, .msg_size = msg_sz };

    double t0 = now_sec();
    for (int i = 0; i < consumers; ++i) pthread_create(&ct[i], NULL, cons_fn, &carg);
    for (int i = 0; i < producers; ++i) {
        pargs[i].ch = ch; pargs[i].id = i; pargs[i].count = per_prod; pargs[i].errors = &errors; pargs[i].msg_size = msg_sz;
        pthread_create(&pt[i], NULL, prod_fn, &pargs[i]);
    }
    for (int i = 0; i < producers; ++i) pthread_join(pt[i], NULL);
    kc_chan_close(ch);
    for (int i = 0; i < consumers; ++i) pthread_join(ct[i], NULL);
    double t1 = now_sec();

    int got = 0; int bad = 0;
    for (int i = 0; i < producers; ++i) {
        int cnt = atomic_load(&per_counts[i]);
        got += cnt;
        if (cnt != per_prod) bad++;
    }
    int err = atomic_load(&errors);

    out->processed = got;
    out->per_prod_mismatch = bad;
    out->runtime_errors = err;
    out->duration_s = (t1 - t0);
    out->pps = out->duration_s > 0 ? (double)got / out->duration_s : 0.0;
    out->gbps = (out->pps * (double)pkt * 8.0) / 1e9;

    kc_chan_destroy(ch);
    free(pt); free(ct); free(pargs); free((void*)per_counts);
    return 0;
}
