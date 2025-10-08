// SPDX-License-Identifier: BSD-3-Clause
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../../include/kcoro_port.h"
#include "../../include/kcoro.h"
/* forward decl to avoid including kcoro_zcopy.h here */
struct kc_zcopy_backend_ops;

/* Internal channel structure and helpers shared between kc_chan.c and kc_zcopy.c.
 * Not part of the public API surface. */

enum kc_waiter_kind { KC_WAITER_CORO=0, KC_WAITER_SELECT=1 };
struct kc_waiter {
    enum kc_waiter_kind kind;
    kcoro_t *co;
    kc_select_t *sel;
    int clause_index;
    enum kc_select_clause_kind clause_kind;
    int is_zref;
    struct kc_waiter *next;
    unsigned long magic;
    int freed;
    void **recv_ptr_slot;
    size_t *recv_len_slot;
};

enum kc_waiter_token_status {
    KC_WAITER_TOKEN_INIT = 0,
    KC_WAITER_TOKEN_ENQUEUED = 1,
};

struct kc_waiter_token {
    enum kc_waiter_token_status status;
};

static inline void kc_waiter_token_reset(struct kc_waiter_token *token)
{
    if (token) token->status = KC_WAITER_TOKEN_INIT;
}

static inline int kc_waiter_token_is_enqueued(const struct kc_waiter_token *token)
{
    return token && token->status == KC_WAITER_TOKEN_ENQUEUED;
}

struct kc_chan {
    KC_MUTEX_T mu;
    KC_COND_T  cv_send;
    KC_COND_T  cv_recv;
    int             closed;

    int             kind;      /* enum kc_kind or >0 => buffered capacity */
    size_t          elem_sz;
    size_t          capacity;  /* elements */
    size_t          mask;      /* capacity-1 when capacity is power-of-two, else 0 */

    /* ring buffer */
    unsigned char  *buf;       /* capacity * elem_sz */
    size_t          head;      /* read index */
    size_t          tail;      /* write index */
    size_t          count;     /* elements in buffer */

    /* conflated */
    unsigned char  *slot;      /* elem_sz */
    int             has_value;

    /* waiter counters (best-effort hints) */
    unsigned        waiters_send;
    unsigned        waiters_recv;

    /* Cooperative wait queues (used by select or park) */
    struct kc_waiter *wq_send_head, *wq_send_tail;
    struct kc_waiter *wq_recv_head, *wq_recv_tail;

    /* Capabilities */
    unsigned        capabilities;   /* KC_CHAN_CAP_* bitmask */
    int             zref_mode;      /* rendezvous zero-copy engaged */
    /* rendezvous zref scratch */
    void           *zref_ptr;
    size_t          zref_len;
    int             zref_ready;
    int             zref_sender_waiter_expected;
    unsigned long   zref_epoch;
    unsigned long   zref_last_consumed_epoch;
    /* zref counters */
    unsigned long   zref_sent, zref_received, zref_fallback_small, zref_fallback_capacity,
                    zref_canceled, zref_aborted_close;

    /* Throughput statistics */
    unsigned long   total_sends, total_recvs;
    unsigned long   total_bytes_sent, total_bytes_recv;
    long            first_op_time_ns, last_op_time_ns;

    /* Metrics pipe */
    struct kc_chan *metrics_pipe;
    unsigned long   last_emit_sends, last_emit_recvs;
    unsigned long   last_emit_bytes_sent, last_emit_bytes_recv;
    long            last_emit_time_ns;

    /* Failure counters */
    unsigned long   send_eagain, send_etime, send_epipe;
    unsigned long   recv_eagain, recv_etime, recv_epipe;

    /* Emission cost control */
    unsigned long   ops_since_emit_check, emit_check_mask;

    /* Pointer-descriptor channel mode */
    int             ptr_mode;  /* 1 when elements are kc_chan_ptrmsg */

    /* Zero-copy backend binding (factory). When non-NULL, kc_chan routes
     * zero-copy calls via these ops. The classic copy path remains when ops==NULL. */
    const struct kc_zcopy_backend_ops *zc_ops; /* vtable */
    void           *zc_priv;    /* backend per-channel state */
    int             zc_backend_id; /* registry id */

    /* Rendezvous metrics */
    unsigned long   rv_matches;
    unsigned long   rv_cancels;
    unsigned long   rv_zdesc_matches;
};

static inline long kc_now_ns(void)
{
    struct timespec ts;
#ifdef CLOCK_MONOTONIC
    clock_gettime(CLOCK_MONOTONIC, &ts);
#else
    clock_gettime(CLOCK_REALTIME, &ts);
#endif
    return (long)ts.tv_sec * 1000000000L + ts.tv_nsec;
}

static inline size_t kc_ring_idx(const struct kc_chan *ch, size_t i)
{
    return ch->mask ? (i & ch->mask) : (i % ch->capacity);
}

/* Stats helpers (defined in kc_chan.c) */
void kc_chan_emit_metrics_if_needed(struct kc_chan *ch, long now);
void kc_chan_update_send_stats_len_locked(struct kc_chan *ch, size_t len);
void kc_chan_update_recv_stats_len_locked(struct kc_chan *ch, size_t len);

/* Waiter helpers (shared by core and zcopy backends) */
static inline struct kc_waiter* kc_waiter_new_coro(enum kc_select_clause_kind kind)
{
    struct kc_waiter *w = (struct kc_waiter*)malloc(sizeof(*w));
    if (!w) return NULL;
    w->kind = KC_WAITER_CORO;
    w->co = kcoro_current();
    kcoro_retain(w->co);
    w->sel = NULL;
    w->clause_index = -1;
    w->clause_kind = kind;
    w->is_zref = 0;
    w->next = NULL;
    w->magic = 0xCAFEBABEUL;
    w->freed = 0;
    w->recv_ptr_slot = NULL;
    w->recv_len_slot = NULL;
    return w;
}

static inline void kc_waiter_append(struct kc_waiter **head, struct kc_waiter **tail, struct kc_waiter *w)
{
    if (*tail) (*tail)->next = w; else *head = w;
    *tail = w;
}

static inline struct kc_waiter* kc_waiter_pop(struct kc_waiter **head, struct kc_waiter **tail)
{
    struct kc_waiter *w = *head;
    if (!w) return NULL;
    *head = w->next;
    if (!*head) *tail = NULL;
    w->next = NULL;
    return w;
}

/* Dispose a waiter exactly once; logs if double-disposed in dev runs. */
static inline void kc_waiter_dispose(struct kc_waiter *w)
{
    if (!w) return;
    if (w->freed) {
        const char *dbg = getenv("KCORO_DEBUG");
        if (dbg && *dbg && dbg[0] != '0') {
            fprintf(stderr, "[kcoro][waiter] double-dispose w=%p kind=%d clause=%d magic=%lx\n",
                    (void*)w, w->kind, w->clause_kind, w->magic);
        }
        return;
    }
    if (w->magic != 0xCAFEBABEUL) {
        const char *dbg = getenv("KCORO_DEBUG");
        if (dbg && *dbg && dbg[0] != '0') {
            fprintf(stderr, "[kcoro][waiter] bad magic before free w=%p magic=%lx\n",
                    (void*)w, w->magic);
        }
    }
    if (w->co) {
        kcoro_release(w->co);
        w->co = NULL;
    }
    w->freed = 1;
    w->magic = 0xDEADDEADUL;
    free(w);
}
