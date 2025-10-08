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
#include "../../include/kcoro_token_kernel.h"
#include "../../include/kcoro_zcopy.h"
#include "kcoro_desc.h"

struct kc_zcopy_backend_ops;

enum kc_pending_kind {
    KC_PENDING_KIND_BYTES = 0,
    KC_PENDING_KIND_PTR   = 1,
    KC_PENDING_KIND_ZDESC = 2,
};

enum kc_pending_role {
    KC_PENDING_ROLE_CORO   = 0,
    KC_PENDING_ROLE_SELECT = 1,
};

struct kc_pending_send {
    struct kc_pending_send *next;
    enum kc_pending_kind    kind;
    enum kc_pending_role    role;
    kc_ticket               ticket;
    kc_select_t            *sel;
    int                     clause_index;
    kc_desc_id              desc_id;
};

struct kc_pending_recv {
    struct kc_pending_recv *next;
    enum kc_pending_kind    kind;
    enum kc_pending_role    role;
    kc_ticket               ticket;
    kc_select_t            *sel;
    int                     clause_index;
    kc_desc_id              desc_id; /* for future buffered integration */
};

static inline void kc_pending_send_append(struct kc_pending_send **head,
                                          struct kc_pending_send **tail,
                                          struct kc_pending_send *node)
{
    node->next = NULL;
    if (*tail) (*tail)->next = node; else *head = node;
    *tail = node;
}

static inline struct kc_pending_send *kc_pending_send_pop(struct kc_pending_send **head,
                                                          struct kc_pending_send **tail)
{
    struct kc_pending_send *node = *head;
    if (!node) return NULL;
    *head = node->next;
    if (!*head) *tail = NULL;
    node->next = NULL;
    return node;
}

static inline void kc_pending_recv_append(struct kc_pending_recv **head,
                                          struct kc_pending_recv **tail,
                                          struct kc_pending_recv *node)
{
    node->next = NULL;
    if (*tail) (*tail)->next = node; else *head = node;
    *tail = node;
}

static inline struct kc_pending_recv *kc_pending_recv_pop(struct kc_pending_recv **head,
                                                          struct kc_pending_recv **tail)
{
    struct kc_pending_recv *node = *head;
    if (!node) return NULL;
    *head = node->next;
    if (!*head) *tail = NULL;
    node->next = NULL;
    return node;
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

    /* descriptor ring for queued kinds */
    kc_desc_id     *ring_descs;
    size_t          head;      /* read index */
    size_t          tail;      /* write index */
    size_t          count;     /* elements in buffer */

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
    kc_desc_id      rv_slot_desc;  /* descriptor staged for rendezvous handoff */

    /* Token kernel pending queues */
    struct kc_pending_send *token_send_head;
    struct kc_pending_send *token_send_tail;
    struct kc_pending_recv *token_recv_head;
    struct kc_pending_recv *token_recv_tail;
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
