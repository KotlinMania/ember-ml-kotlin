// SPDX-License-Identifier: BSD-3-Clause
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
#include <assert.h>
#include "../../include/kcoro_bench.h"
#include "../../include/kcoro_sched.h"
#include "../../include/kcoro_port.h"
#include "../../include/kcoro_core.h"
#include "../../include/kcoro_config.h"

struct kc_bench_handle {
    kc_chan_t   *ch;
    kc_sched_t  *sched;
    kc_bench_params_t params;
    _Atomic int  shutdown;
    _Atomic int  active_prod;
    _Atomic int  active_cons;
    /* arrays retained until stop() */
    int *sent_counts;
    int *per_counts;
};

typedef struct prod_arg { struct kc_bench_handle *h; int id; } prod_arg_t;
typedef struct cons_arg { struct kc_bench_handle *h; } cons_arg_t;

static void co_producer_ptr(void *arg)
{
    prod_arg_t *pa = (prod_arg_t*)arg;
    struct kc_bench_handle *h = pa->h;
    size_t len = h->params.packet_size ? h->params.packet_size : 64;
    static __thread unsigned char *buf = NULL; if (!buf) buf = aligned_alloc(64, len);
    int sent = 0; atomic_fetch_add(&h->active_prod, 1);
    while (!atomic_load(&h->shutdown)) {
        for (int i = 0; i < h->params.packets_per_cycle; ++i) {
            for (;;) {
                int rc = kc_chan_send_ptr(h->ch, buf, len, 0);
                if (rc == 0) { sent++; if (h->sent_counts) h->sent_counts[pa->id] = sent; break; }
                if (rc == KC_EPIPE) goto out;
                for (int k = 0; k < h->params.spin_iters; ++k) {
                    rc = kc_chan_send_ptr(h->ch, buf, len, 0);
                    if (rc == 0) { sent++; if (h->sent_counts) h->sent_counts[pa->id] = sent; goto next; }
                    if (rc == KC_EPIPE) goto out;
                }
                kcoro_yield();
            }
        next: ;
        }
        kcoro_yield();
    }
out:
    atomic_fetch_sub(&h->active_prod, 1);
}

static void co_producer_int(void *arg)
{
    prod_arg_t *pa = (prod_arg_t*)arg;
    struct kc_bench_handle *h = pa->h;
    int sent = 0; atomic_fetch_add(&h->active_prod, 1);
    while (!atomic_load(&h->shutdown)) {
        for (int i = 0; i < h->params.packets_per_cycle; ++i) {
            int v = (pa->id << 24) | i;
            for (;;) {
                int rc = kc_chan_send(h->ch, &v, 0);
                if (rc == 0) { sent++; if (h->sent_counts) h->sent_counts[pa->id] = sent; break; }
                if (rc == KC_EPIPE) goto out;
                for (int k = 0; k < h->params.spin_iters; ++k) {
                    rc = kc_chan_send(h->ch, &v, 0);
                    if (rc == 0) { sent++; if (h->sent_counts) h->sent_counts[pa->id] = sent; goto next; }
                    if (rc == KC_EPIPE) goto out;
                }
                kcoro_yield();
            }
        next: ;
        }
        kcoro_yield();
    }
out:
    atomic_fetch_sub(&h->active_prod, 1);
}

static void co_consumer_ptr(void *arg)
{
    cons_arg_t *ca = (cons_arg_t*)arg; struct kc_bench_handle *h = ca->h;
    atomic_fetch_add(&h->active_cons, 1);
    void *ptr = NULL; size_t len = 0;
    while (!atomic_load(&h->shutdown)) {
        int rc = kc_chan_recv_ptr(h->ch, &ptr, &len, 0);
        if (rc == 0) { if (h->per_counts) h->per_counts[0]++; }
        else if (rc == KC_EPIPE) break;
        else if (rc == KC_EAGAIN) {
            for (int k = 0; k < h->params.spin_iters; ++k) {
                rc = kc_chan_recv_ptr(h->ch, &ptr, &len, 0);
                if (rc == 0) { if (h->per_counts) h->per_counts[0]++; goto next; }
                if (rc == KC_EPIPE) goto out;
            }
            kcoro_yield();
        }
    next: ;
    }
out:
    atomic_fetch_sub(&h->active_cons, 1);
}

static void co_consumer_int(void *arg)
{
    cons_arg_t *ca = (cons_arg_t*)arg; struct kc_bench_handle *h = ca->h;
    atomic_fetch_add(&h->active_cons, 1);
    int v;
    while (!atomic_load(&h->shutdown)) {
        int rc = kc_chan_recv(h->ch, &v, 0);
        if (rc == 0) { if (h->per_counts) h->per_counts[0]++; }
        else if (rc == KC_EPIPE) break;
        else if (rc == KC_EAGAIN) {
            for (int k = 0; k < h->params.spin_iters; ++k) {
                rc = kc_chan_recv(h->ch, &v, 0);
                if (rc == 0) { if (h->per_counts) h->per_counts[0]++; goto next; }
                if (rc == KC_EPIPE) goto out;
            }
            kcoro_yield();
        }
    next: ;
    }
out:
    atomic_fetch_sub(&h->active_cons, 1);
}

int kc_bench_chan_start(const kc_bench_params_t *p,
                        kc_bench_handle_t **out_handle,
                        kc_chan_t **out_chan)
{
    if (!p || !out_handle) return -EINVAL;
    if (p->producers <= 0 || p->consumers <= 0 || p->packets_per_cycle <= 0) return -EINVAL;

    struct kc_bench_handle *h = calloc(1, sizeof(*h));
    if (!h) return -ENOMEM;
    h->params = *p;
    h->sched = kc_sched_default();

    /* Create channel */
    int rc;
    if (p->pointer_mode) {
        rc = kc_chan_make_ptr(&h->ch, p->kind, p->capacity);
        if (rc == 0) {
            int zrc = kc_chan_enable_zero_copy(h->ch);
            if (zrc != 0) {
                kc_chan_destroy(h->ch);
                free(h);
                return zrc;
            }
        }
    } else {
        rc = kc_chan_make(&h->ch, p->kind, sizeof(int), p->capacity);
    }
    if (rc != 0) { free(h); return rc; }

    h->sent_counts = calloc((size_t)p->producers, sizeof(int));
    h->per_counts  = calloc((size_t)p->producers, sizeof(int));

    /* Spawn */
    for (int i = 0; i < p->consumers; ++i) {
        cons_arg_t *ca = malloc(sizeof(*ca)); if (!ca) return -ENOMEM; ca->h = h;
        kc_spawn_co(h->sched, p->pointer_mode ? co_consumer_ptr : co_consumer_int, ca, 0, NULL);
    }
    for (int i = 0; i < p->producers; ++i) {
        prod_arg_t *pa = malloc(sizeof(*pa)); if (!pa) return -ENOMEM; pa->h = h; pa->id = i;
        kc_spawn_co(h->sched, p->pointer_mode ? co_producer_ptr : co_producer_int, pa, 0, NULL);
    }

    if (out_chan) *out_chan = h->ch;
    *out_handle = h;
    return 0;
}

void kc_bench_chan_stop(kc_bench_handle_t *h)
{
    if (!h) return;
    atomic_store(&h->shutdown, 1);
    kc_chan_close(h->ch);
    /* Wait for producers/consumers */
    for (int i = 0; i < 1000; ++i) { /* up to ~100ms */
        if (atomic_load(&h->active_prod) == 0 && atomic_load(&h->active_cons) == 0) break;
        kcoro_yield();
    }
    kc_chan_destroy(h->ch);
    free(h->sent_counts); free(h->per_counts);
    free(h);
}
