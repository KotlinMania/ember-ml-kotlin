// SPDX-License-Identifier: BSD-3-Clause
#include "kcoro_token_kernel.h"

#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <pthread.h>
#include <sched.h>

#include "kcoro_core.h"
#include "kcoro_port.h"
#include "kcoro_desc.h"

#ifndef KC_TOKEN_KERNEL_BUCKETS
#define KC_TOKEN_KERNEL_BUCKETS 1024u
#endif

typedef struct kc_token_block kc_token_block;

struct kc_token_block {
    kc_token_block    *next_hash;
    struct kc_chan    *channel;
    kcoro_t           *owner_co;
    kc_payload         payload;
    void              (*resume_pc)(void);
    kc_token_id_t      id;
};

typedef struct kc_token_bucket {
    pthread_mutex_t mu;
    kc_token_block *head;
} kc_token_bucket;

typedef struct kc_token_freelist {
    pthread_mutex_t mu;
    kc_token_block *head;
} kc_token_freelist;

typedef struct kc_token_ready_queue {
    pthread_mutex_t mu;
    pthread_cond_t  cv;
    kc_token_block *head;
    kc_token_block *tail;
    int             stop;
} kc_token_ready_queue;

static kc_token_block *ready_dequeue(kc_token_ready_queue *q);
static void ready_enqueue(kc_token_ready_queue *q, kc_token_block *blk);
static void freelist_push(kc_token_freelist *fl, kc_token_block *blk);

enum {
    KC_TOKEN_INIT_UNINITIALIZED = 0,
    KC_TOKEN_INIT_IN_PROGRESS   = 1,
    KC_TOKEN_INIT_READY         = 2,
};

static struct {
    atomic_uint_fast64_t next_id;
    kc_token_bucket     *buckets;
    size_t               bucket_count;
    kc_token_freelist    freelist;
    kc_token_ready_queue ready_queue;
    pthread_t            worker;
    int                  worker_started;
    atomic_int           initialized;
} g_kernel = {
    .next_id = ATOMIC_VAR_INIT(1),
    .buckets = NULL,
    .bucket_count = 0,
    .worker_started = 0,
    .initialized = ATOMIC_VAR_INIT(KC_TOKEN_INIT_UNINITIALIZED),
};

static void freelist_init(kc_token_freelist *fl)
{
    pthread_mutex_init(&fl->mu, NULL);
    fl->head = NULL;
}

static void freelist_destroy(kc_token_freelist *fl)
{
    pthread_mutex_lock(&fl->mu);
    kc_token_block *cur = fl->head;
    while (cur) {
        kc_token_block *next = cur->next_hash;
        free(cur);
        cur = next;
    }
    fl->head = NULL;
    pthread_mutex_unlock(&fl->mu);
    pthread_mutex_destroy(&fl->mu);
}

static void ready_queue_init(kc_token_ready_queue *q) {
    pthread_mutex_init(&q->mu, NULL);
    pthread_cond_init(&q->cv, NULL);
    q->head = q->tail = NULL;
    q->stop = 0;
}

static void ready_queue_destroy(kc_token_ready_queue *q) {
    pthread_mutex_destroy(&q->mu);
    pthread_cond_destroy(&q->cv);
}

static void ready_queue_stop(kc_token_ready_queue *q) {
    pthread_mutex_lock(&q->mu);
    q->stop = 1;
    pthread_cond_broadcast(&q->cv);
    pthread_mutex_unlock(&q->mu);
}

static void ready_enqueue(kc_token_ready_queue *q, kc_token_block *blk) {
    pthread_mutex_lock(&q->mu);
    blk->next_hash = NULL;
    if (q->tail) q->tail->next_hash = blk; else q->head = blk;
    q->tail = blk;
    pthread_cond_signal(&q->cv);
    pthread_mutex_unlock(&q->mu);
}

static kc_token_block *ready_dequeue(kc_token_ready_queue *q) {
    pthread_mutex_lock(&q->mu);
    while (!q->head && !q->stop) {
        pthread_cond_wait(&q->cv, &q->mu);
    }
    if (!q->head) {
        pthread_mutex_unlock(&q->mu);
        return NULL;
    }
    kc_token_block *blk = q->head;
    q->head = blk->next_hash;
    if (!q->head) q->tail = NULL;
    pthread_mutex_unlock(&q->mu);
    blk->next_hash = NULL;
    return blk;
}

static void kc_token_process_block(kc_token_block *blk) {
    if (blk->owner_co) {
        kcoro_t *co = blk->owner_co;
        co->token_payload_ptr = blk->payload.ptr;
        co->token_payload_len = blk->payload.len;
        co->token_payload_status = blk->payload.status;
        co->token_payload_desc = blk->payload.desc_id;
        atomic_store_explicit(&co->token_payload_ready, 1, memory_order_release);
        kcoro_unpark(co);
    }
    // TODO(token-kernel): honor blk->resume_pc once interpreter hand-off is implemented.
    freelist_push(&g_kernel.freelist, blk);
}

static void *kc_token_worker_main(void *arg) {
    (void)arg;
    for (;;) {
        kc_token_block *blk = ready_dequeue(&g_kernel.ready_queue);
        if (!blk) break;
        kc_token_process_block(blk);
    }
    return NULL;
}

static kc_token_block *freelist_pop(kc_token_freelist *fl)
{
    pthread_mutex_lock(&fl->mu);
    kc_token_block *blk = fl->head;
    if (blk) {
        fl->head = blk->next_hash;
        blk->next_hash = NULL;
    }
    pthread_mutex_unlock(&fl->mu);
    if (!blk) {
        blk = calloc(1, sizeof(*blk));
    }
    return blk;
}

static void freelist_push(kc_token_freelist *fl, kc_token_block *blk)
{
    if (!blk) return;
    memset(&blk->payload, 0, sizeof(blk->payload));
    blk->channel = NULL;
    blk->owner_co = NULL;
    blk->resume_pc = NULL;
    blk->id = 0;

    pthread_mutex_lock(&fl->mu);
    blk->next_hash = fl->head;
    fl->head = blk;
    pthread_mutex_unlock(&fl->mu);
}

static size_t token_bucket_index(kc_token_id_t id)
{
    return (size_t)(id & ((kc_token_id_t)(KC_TOKEN_KERNEL_BUCKETS - 1)));
}

static int bucket_init_many(size_t count)
{
    g_kernel.buckets = calloc(count, sizeof(kc_token_bucket));
    if (!g_kernel.buckets) {
        return -ENOMEM;
    }
    g_kernel.bucket_count = count;
    for (size_t i = 0; i < count; ++i) {
        pthread_mutex_init(&g_kernel.buckets[i].mu, NULL);
        g_kernel.buckets[i].head = NULL;
    }
    return 0;
}

static void bucket_destroy_many(void)
{
    if (!g_kernel.buckets) return;
    for (size_t i = 0; i < g_kernel.bucket_count; ++i) {
        pthread_mutex_destroy(&g_kernel.buckets[i].mu);
    }
    free(g_kernel.buckets);
    g_kernel.buckets = NULL;
    g_kernel.bucket_count = 0;
}

static void bucket_insert(kc_token_block *blk)
{
    size_t idx = token_bucket_index(blk->id) % g_kernel.bucket_count;
    kc_token_bucket *bucket = &g_kernel.buckets[idx];
    pthread_mutex_lock(&bucket->mu);
    blk->next_hash = bucket->head;
    bucket->head = blk;
    pthread_mutex_unlock(&bucket->mu);
}

static kc_token_block *bucket_remove(kc_token_id_t id)
{
    size_t idx = token_bucket_index(id) % g_kernel.bucket_count;
    kc_token_bucket *bucket = &g_kernel.buckets[idx];
    pthread_mutex_lock(&bucket->mu);
    kc_token_block *prev = NULL;
    kc_token_block *cur = bucket->head;
    while (cur) {
        if (cur->id == id) {
            if (prev) {
                prev->next_hash = cur->next_hash;
            } else {
                bucket->head = cur->next_hash;
            }
            cur->next_hash = NULL;
            pthread_mutex_unlock(&bucket->mu);
            return cur;
        }
        prev = cur;
        cur = cur->next_hash;
    }
    pthread_mutex_unlock(&bucket->mu);
    return NULL;
}

int kc_token_kernel_global_init(void)
{
    for (;;) {
        int state = atomic_load_explicit(&g_kernel.initialized, memory_order_acquire);
        if (state == KC_TOKEN_INIT_READY) {
            return 0;
        }
        if (state == KC_TOKEN_INIT_UNINITIALIZED) {
            int expected = KC_TOKEN_INIT_UNINITIALIZED;
            if (atomic_compare_exchange_strong_explicit(&g_kernel.initialized,
                                                        &expected,
                                                        KC_TOKEN_INIT_IN_PROGRESS,
                                                        memory_order_acq_rel,
                                                        memory_order_acquire)) {
                freelist_init(&g_kernel.freelist);
                ready_queue_init(&g_kernel.ready_queue);
                int rc = bucket_init_many(KC_TOKEN_KERNEL_BUCKETS);
                if (rc != 0) {
                    ready_queue_destroy(&g_kernel.ready_queue);
                    freelist_destroy(&g_kernel.freelist);
                    atomic_store_explicit(&g_kernel.initialized, KC_TOKEN_INIT_UNINITIALIZED, memory_order_release);
                    return rc;
                }
                atomic_store_explicit(&g_kernel.next_id, 1, memory_order_relaxed);
                if (pthread_create(&g_kernel.worker, NULL, kc_token_worker_main, NULL) != 0) {
                    ready_queue_destroy(&g_kernel.ready_queue);
                    freelist_destroy(&g_kernel.freelist);
                    bucket_destroy_many();
                    atomic_store_explicit(&g_kernel.initialized, KC_TOKEN_INIT_UNINITIALIZED, memory_order_release);
                    return -errno;
                }
                g_kernel.worker_started = 1;
                atomic_store_explicit(&g_kernel.initialized, KC_TOKEN_INIT_READY, memory_order_release);
                return 0;
            }
            continue; /* another thread raced us; re-read state */
        }
        /* Another thread is initializing. Yield until ready or reset. */
        while (atomic_load_explicit(&g_kernel.initialized, memory_order_acquire) == KC_TOKEN_INIT_IN_PROGRESS) {
            sched_yield();
        }
        /* Loop will re-check state and either observe READY or retry initialization. */
    }
}

void kc_token_kernel_global_shutdown(void)
{
    if (atomic_load_explicit(&g_kernel.initialized, memory_order_acquire) != KC_TOKEN_INIT_READY) {
        return;
    }
    ready_queue_stop(&g_kernel.ready_queue);
    if (g_kernel.worker_started) {
        pthread_join(g_kernel.worker, NULL);
        g_kernel.worker_started = 0;
    }
    ready_queue_destroy(&g_kernel.ready_queue);
    bucket_destroy_many();
    freelist_destroy(&g_kernel.freelist);
    atomic_store_explicit(&g_kernel.initialized, KC_TOKEN_INIT_UNINITIALIZED, memory_order_release);
}

static kc_token_id_t next_token_id(void)
{
    return atomic_fetch_add_explicit(&g_kernel.next_id, 1, memory_order_relaxed);
}

static kc_ticket publish_common(struct kc_chan *ch,
                                const kc_payload *initial_payload,
                                void (*resume_pc)(void))
{
    kc_ticket ticket = {0, ch};
    if (!atomic_load(&g_kernel.initialized)) {
        if (kc_token_kernel_global_init() != 0) {
            return ticket;
        }
    }

    kcoro_t *current = kcoro_current();
    if (!current) {
        return ticket;
    }

    kc_token_block *blk = freelist_pop(&g_kernel.freelist);
    blk->channel = ch;
    blk->owner_co = current;
    blk->resume_pc = resume_pc;
    blk->id = next_token_id();
    if (initial_payload) {
        blk->payload = *initial_payload;
    } else {
        blk->payload.ptr = NULL;
        blk->payload.len = 0;
        blk->payload.status = 0;
        blk->payload.desc_id = 0;
    }

    bucket_insert(blk);

    ticket.id = blk->id;
    return ticket;
}

kc_ticket kc_token_kernel_publish_send(struct kc_chan *ch,
                                       void *ptr,
                                       size_t len,
                                       void (*resume_pc)(void))
{
    kc_payload payload = { .ptr = ptr, .len = len, .status = 0, .desc_id = 0 };
    return publish_common(ch, &payload, resume_pc);
}

kc_ticket kc_token_kernel_publish_recv(struct kc_chan *ch,
                                       void (*resume_pc)(void))
{
    return publish_common(ch, NULL, resume_pc);
}

void kc_token_kernel_callback(kc_ticket ticket, kc_payload payload)
{
    if (ticket.id == 0) {
        return;
    }
    kc_token_block *blk = bucket_remove(ticket.id);
    if (!blk) {
        return;
    }
    blk->payload = payload;
    ready_enqueue(&g_kernel.ready_queue, blk);
}

void kc_token_kernel_cancel(kc_ticket ticket, int reason)
{
    if (ticket.id == 0) {
        return;
    }
    kc_token_block *blk = bucket_remove(ticket.id);
    if (!blk) {
        return;
    }
    blk->payload.ptr = NULL;
    blk->payload.len = 0;
    if (blk->payload.desc_id) {
        kc_desc_release(blk->payload.desc_id);
        blk->payload.desc_id = 0;
    }
    blk->payload.status = reason;
    ready_enqueue(&g_kernel.ready_queue, blk);
}

int kc_token_kernel_consume_payload(kc_payload *out_payload)
{
    kcoro_t *current = kcoro_current();
    if (!current) return -EINVAL;
    int ready = atomic_exchange_explicit(&current->token_payload_ready, 0, memory_order_acq_rel);
    if (!ready) {
        return KC_EAGAIN;
    }
    if (out_payload) {
        out_payload->ptr = current->token_payload_ptr;
        out_payload->len = current->token_payload_len;
        out_payload->status = current->token_payload_status;
        out_payload->desc_id = current->token_payload_desc;
    }
    current->token_payload_ptr = NULL;
    current->token_payload_len = 0;
    current->token_payload_desc = 0;
    return current->token_payload_status;
}
