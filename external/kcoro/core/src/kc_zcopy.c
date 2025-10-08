// SPDX-License-Identifier: BSD-3-Clause
/*
 * kc_zcopy.c — Unified zero‑copy backend (zref) and neutral region stubs
 * ----------------------------------------------------------------------
 *
 * Scope
 * - Provides a single built‑in zero‑copy backend named "zref" that implements
 *   both rendezvous hand‑to‑hand transfer and queued pointer‑descriptor paths
 *   for buffered/unlimited/conflated channels.
 * - Exposes a tiny backend registry so external adapters
 *   can register their own ops in a separate library without pulling any
 *   non‑POSIX headers into kcoro.
 * - Supplies neutral region stubs; actual region registration/translation is
 *   reserved for out‑of‑tree adapters.
 *
 * Invariants
 * - Return 0 on success; negative KC_* (mapped to -errno) on failure.
 * - Successful ops increment byte/ops counters exactly once.
 * - Rendezvous (state choreography):
 *     1) send publishes (ptr,len) → zref_ready=1 → enqueue SEND waiter (if needed)
 *     2) recv observes zref_ready, copies (ptr,len) into descriptor, clears state
 *     3) recv wakes exactly one parked SEND waiter (if present) and returns
 *   Exactly‑once dequeue of parked waiters is enforced by zref_pop_first_* helpers.
 * - No ownership transfer: payload buffers are never freed by kcoro.
 *
 * Non‑rendezvous kinds (buffered/unlimited/conflated) share the same zref
 * surface but operate on a descriptor ring: we copy only the small
 * (ptr,len) record; payloads remain external.
 */

#include <errno.h>
#include <string.h>
#include <assert.h>
#include "../../include/kcoro.h"
#include "../../include/kcoro_zcopy.h"
#include "../../include/kcoro_core.h"
#include "../../include/kcoro_sched.h"
#include "../../include/kcoro_config.h"
#include "kc_chan_internal.h"
#include "kc_select_internal.h"
#include <stdio.h>
#include <stdarg.h>

static void kc_zref_schedule_co(kcoro_t *co)
{
    if (!co) return;
    if (kcoro_is_parked(co)) {
        kcoro_unpark(co);
    }
    kc_sched_t *sched = kc_sched_current();
    if (!sched) {
        sched = kc_sched_default();
    }
    if (sched) {
        kc_sched_enqueue_ready(sched, co);
    }
    kcoro_release(co);
}

/* ========================= Backend Registry & Regions ===================== */
static const kc_zcopy_backend_ops_t g_zref_ops; /* fwd */

typedef struct kc_zcopy_backend_entry {
    const char *name;
    kc_zcopy_backend_id id;
    uint32_t caps;
    const kc_zcopy_backend_ops_t *ops;
} kc_zcopy_backend_entry_t;

#define KC_ZBACKENDS_MAX 8
static kc_zcopy_backend_entry_t g_zbackends[KC_ZBACKENDS_MAX];
static int g_zbackends_cnt = 0;

/* Forward declarations of zref backend ops (handles rendezvous + queued) */
static int zref_send(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms);
static int zref_recv(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms);
static int zref_send_c(kc_chan_t *c, const kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct);
static int zref_recv_c(kc_chan_t *c, kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct);

/* Ensure the built‑in backend is available (idempotent). */
static void ensure_builtin_backends(void)
{
    /* idempotent: register returns existing id if already present */
    (void)kc_zcopy_register("zref", &g_zref_ops, KC_CHAN_CAP_ZERO_COPY);
}

/** Register a backend under a stable name. See kcoro_zcopy.h for details. */
kc_zcopy_backend_id kc_zcopy_register(const char *name,
                                      const kc_zcopy_backend_ops_t *ops,
                                      uint32_t caps)
{
    if (!name || !ops) return -EINVAL;
    for (int i = 0; i < g_zbackends_cnt; ++i) {
        if (strcmp(g_zbackends[i].name, name) == 0) return g_zbackends[i].id;
    }
    if (g_zbackends_cnt >= KC_ZBACKENDS_MAX) return -ENOSPC;
    int id = g_zbackends_cnt;
    g_zbackends[g_zbackends_cnt++] = (kc_zcopy_backend_entry_t){ name, id, caps, ops };
    return id;
}

/** Resolve backend id by name. */
kc_zcopy_backend_id kc_zcopy_resolve(const char *name)
{
    ensure_builtin_backends();
    if (!name) return -EINVAL;
    for (int i = 0; i < g_zbackends_cnt; ++i) {
        if (strcmp(g_zbackends[i].name, name) == 0) return g_zbackends[i].id;
    }
    return -ENOENT;
}

/** Bind a backend to a channel and set KC_CHAN_CAP_ZERO_COPY. */
int kc_chan_enable_zero_copy_backend(kc_chan_t *c,
                                     kc_zcopy_backend_id id,
                                     const void *opts)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch) return -EINVAL;
    if (id < 0 || id >= g_zbackends_cnt) return -ENOENT;
    const kc_zcopy_backend_ops_t *ops = g_zbackends[id].ops;
    KC_MUTEX_LOCK(&ch->mu);
    ch->capabilities |= KC_CHAN_CAP_ZERO_COPY;
    ch->zc_ops = ops;
    ch->zc_backend_id = id;
    int rc = 0;
    if (ops && ops->attach) rc = ops->attach(c, opts);
    KC_MUTEX_UNLOCK(&ch->mu);
    return rc;
}

/* Region management stubs — placeholders for future adapters. */
struct kc_region { int unused; };

int kc_region_register(kc_region_t **out, void *addr, size_t len, unsigned flags) {
    (void)addr; (void)len; (void)flags;
    if (out) *out = NULL;
    return -ENOTSUP;
}
int kc_region_deregister(kc_region_t *reg) {
    (void)reg; return -ENOTSUP;
}
int kc_region_export_id(const kc_region_t *reg, unsigned long *out_id) {
    (void)reg; if (out_id) *out_id = 0; return -ENOTSUP;
}

/* ================= Zero‑Copy Backend (zref unified) ====================== */

/* ==================== zref rendezvous backend (moved) ==================== */

/* No compile-time debug macros; use explicit logging in rare cases. */

/** Pop first parked SEND waiter (zref). */
static struct kc_waiter* zref_pop_first_sender(struct kc_waiter **head, struct kc_waiter **tail)
{
    struct kc_waiter *prev = NULL, *cur = *head;
    while (cur) {
        if (cur->is_zref && cur->clause_kind == KC_SELECT_CLAUSE_SEND) {
            if (prev) prev->next = cur->next; else *head = cur->next;
            if (*tail == cur) *tail = prev;
            cur->next = NULL;
            return cur;
        }
        prev = cur; cur = cur->next;
    }
    return NULL;
}

/** Pop first parked RECV waiter (zref). */
static struct kc_waiter* zref_pop_first_recv(struct kc_waiter **head, struct kc_waiter **tail)
{
    struct kc_waiter *prev = NULL, *cur = *head;
    while (cur) {
        if (cur->is_zref && cur->clause_kind == KC_SELECT_CLAUSE_RECV) {
            if (prev) prev->next = cur->next; else *head = cur->next;
            if (*tail == cur) *tail = prev;
            cur->next = NULL;
            return cur;
        }
        prev = cur; cur = cur->next;
    }
    return NULL;
}

static inline void zref_assert_invariants(struct kc_chan *ch)
{
    (void)ch; /* retain for future deep checks in debug builds */
}

/**
 * Park or yield the calling coroutine until SEND side may progress.
 * timeout_ms==0 → KC_EAGAIN; <0 → park; >0 → yield until deadline.
 */
static int zref_wait_send(struct kc_chan *ch, long timeout_ms, long *deadline_ns)
{
    if (timeout_ms == 0) { KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN; }
    if (timeout_ms < 0) {
        struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_SEND);
        if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
        w->is_zref = 1;
        kc_waiter_append(&ch->wq_send_head, &ch->wq_send_tail, w);
        KC_MUTEX_UNLOCK(&ch->mu);
        kcoro_park();
    } else {
        KC_MUTEX_UNLOCK(&ch->mu);
        if (kc_now_ns() >= *deadline_ns) return KC_ETIME;
        kcoro_yield();
    }
    return 1;
}

/**
 * Park or yield the calling coroutine until RECV side may progress.
 * timeout_ms==0 → KC_EAGAIN; <0 → park; >0 → yield until deadline.
 */
static int zref_wait_recv(struct kc_chan *ch, long timeout_ms, long *deadline_ns)
{
    if (timeout_ms == 0) { KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN; }
    if (timeout_ms < 0) {
        struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_RECV);
        if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
        w->is_zref = 1;
        kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
        KC_MUTEX_UNLOCK(&ch->mu);
        kcoro_park();
    } else {
        KC_MUTEX_UNLOCK(&ch->mu);
        if (kc_now_ns() >= *deadline_ns) return KC_ETIME;
        kcoro_yield();
    }
    return 1;
}

static int zref_send(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d || !d->addr || d->len == 0) return -EINVAL;
    /* Non-rendezvous: queued descriptor path (former ptr backend) */
    if (ch->kind != KC_RENDEZVOUS) {
        assert(kcoro_current() != NULL);
        long deadline_ns = 0; int timed = (timeout_ms > 0);
        if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;
    again_qsend:
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->closed) { ch->send_epipe++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EPIPE; }
        /* Conflated: keep only latest */
        if (ch->kind == KC_CONFLATED) {
            struct kc_chan_ptrmsg msg = { .ptr=(void*)d->addr, .len=d->len };
            memcpy(ch->slot, &msg, sizeof(msg)); ch->has_value=1;
            kc_chan_update_send_stats_len_locked(ch, d->len);
            KC_COND_SIGNAL(&ch->cv_recv);
            KC_MUTEX_UNLOCK(&ch->mu); return 0;
        }
        if (timeout_ms == 0) {
            if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) { ch->send_eagain++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN; }
        } else if (timeout_ms < 0) {
            if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) { KC_MUTEX_UNLOCK(&ch->mu); kcoro_yield(); goto again_qsend; }
        } else {
            if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) { KC_MUTEX_UNLOCK(&ch->mu); if (kc_now_ns() >= deadline_ns) { ch->send_etime++; return KC_ETIME; } kcoro_yield(); goto again_qsend; }
        }
        if (ch->count == ch->capacity && ch->kind == KC_UNLIMITED) {
            size_t newcap = ch->capacity ? ch->capacity * 2 : KCORO_UNLIMITED_INIT_CAP;
            unsigned char *nbuf = malloc(newcap * ch->elem_sz);
            if (!nbuf) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
            size_t old_cap = ch->capacity; size_t old_mask = ch->mask;
            for (size_t i = 0; i < ch->count; ++i) {
                size_t idx = old_mask ? ((ch->head + i) & old_mask) : ((ch->head + i) % old_cap);
                memcpy(nbuf + (i * ch->elem_sz), ch->buf + (idx * ch->elem_sz), ch->elem_sz);
            }
            free(ch->buf); ch->buf = nbuf; ch->capacity = newcap; ch->mask = newcap - 1; ch->head = 0; ch->tail = ch->count;
        }
        size_t t = kc_ring_idx(ch, ch->tail);
        struct kc_chan_ptrmsg msg = { .ptr=(void*)d->addr, .len=d->len };
        memcpy(ch->buf + (t * ch->elem_sz), &msg, sizeof(msg));
        size_t next = ch->tail + 1; ch->tail = ch->mask ? (next & ch->mask) : (next % ch->capacity);
        ch->count++;
        kc_chan_update_send_stats_len_locked(ch, d->len);
        KC_COND_SIGNAL(&ch->cv_recv);
        KC_MUTEX_UNLOCK(&ch->mu);
        return 0;
    }
    assert(kcoro_current() != NULL);
    long deadline_ns = 0; int timed = (timeout_ms > 0);
    if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;
again:
    KC_MUTEX_LOCK(&ch->mu);
    zref_assert_invariants(ch);
    if (!(ch->capabilities & KC_CHAN_CAP_ZERO_COPY)) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOTSUP; }
    if (ch->kind != KC_RENDEZVOUS) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOTSUP; }
    ch->zref_mode = 1;
    if (ch->closed) { KC_MUTEX_UNLOCK(&ch->mu); ch->zref_aborted_close++; return KC_EPIPE; }
    if (ch->zref_ready) {
        int r = zref_wait_send(ch, timeout_ms, &deadline_ns);
        if (r == 1) goto again; else return r;
    }
    if (ch->wq_recv_head == NULL) {
        if (timeout_ms < 0) {
            ch->zref_ptr = (void*)d->addr; ch->zref_len = d->len; ch->zref_ready = 1; ch->zref_epoch++; ch->zref_sent++;
            kc_chan_update_send_stats_len_locked(ch, d->len);
            struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_SEND);
            if (!w) { ch->zref_ready=0; ch->zref_ptr=NULL; ch->zref_len=0; KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
            w->is_zref=1; kc_waiter_append(&ch->wq_send_head, &ch->wq_send_tail, w);
            ch->zref_sender_waiter_expected = 1; zref_assert_invariants(ch); KC_MUTEX_UNLOCK(&ch->mu);
            kcoro_park(); KC_MUTEX_LOCK(&ch->mu); zref_assert_invariants(ch);
            int ret=0; if (ch->closed && ch->zref_ready) { ch->zref_aborted_close++; ch->zref_ready=0; ch->zref_ptr=NULL; ch->zref_len=0; ret=KC_EPIPE; }
            ch->zref_sender_waiter_expected = 0; zref_assert_invariants(ch); KC_MUTEX_UNLOCK(&ch->mu); return ret;
        } else { int r = zref_wait_send(ch, timeout_ms, &deadline_ns); if (r==1) goto again; else return r; }
    }
    ch->zref_ptr = (void*)d->addr; ch->zref_len = d->len; ch->zref_ready = 1; ch->zref_epoch++; ch->zref_sent++;
    kc_chan_update_send_stats_len_locked(ch, d->len);
    /* Wake a pending zref receiver (if any) */
    kcoro_t *wake_co = NULL;
    struct kc_waiter *w = zref_pop_first_recv(&ch->wq_recv_head, &ch->wq_recv_tail);
    if (w) {
        if (w->co) {
            wake_co = w->co;
            kcoro_retain(wake_co);
        }
        kc_waiter_dispose(w);
    }
    KC_COND_SIGNAL(&ch->cv_recv);
    KC_MUTEX_UNLOCK(&ch->mu);
    kc_zref_schedule_co(wake_co);
    return 0;
}

static int zref_recv(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    /* Non-rendezvous: queued descriptor path */
    if (ch->kind != KC_RENDEZVOUS) {
        assert(kcoro_current() != NULL);
        long deadline_ns = 0; int timed = (timeout_ms > 0);
        if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;
    again_qrecv:
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->kind == KC_CONFLATED) {
            int rc = 0;
            if (!ch->has_value) {
                if (timeout_ms == 0) { ch->recv_eagain++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN; }
                if (timeout_ms < 0) { KC_MUTEX_UNLOCK(&ch->mu); kcoro_yield(); goto again_qrecv; }
                KC_MUTEX_UNLOCK(&ch->mu); if (kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; } kcoro_yield(); goto again_qrecv;
            }
            struct kc_chan_ptrmsg tmp; memcpy(&tmp, ch->slot, sizeof(tmp)); ch->has_value = 0;
            d->addr = tmp.ptr; d->len = tmp.len;
            kc_chan_update_recv_stats_len_locked(ch, tmp.len);
            KC_COND_SIGNAL(&ch->cv_send);
            KC_MUTEX_UNLOCK(&ch->mu); return rc;
        }
        if (timeout_ms == 0) {
            if (ch->count == 0) { int rcl = ch->closed ? KC_EPIPE : KC_EAGAIN; if (rcl==KC_EAGAIN) ch->recv_eagain++; else ch->recv_epipe++; KC_MUTEX_UNLOCK(&ch->mu); return rcl; }
        } else if (timeout_ms < 0) {
            if (ch->count == 0 && !ch->closed) { KC_MUTEX_UNLOCK(&ch->mu); kcoro_yield(); goto again_qrecv; }
        } else {
            if (ch->count == 0 && !ch->closed) { KC_MUTEX_UNLOCK(&ch->mu); if (kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; } kcoro_yield(); goto again_qrecv; }
        }
        if (ch->count > 0) {
            size_t h = kc_ring_idx(ch, ch->head);
            struct kc_chan_ptrmsg tmp; memcpy(&tmp, ch->buf + (h * ch->elem_sz), sizeof(tmp));
            size_t n2 = ch->head + 1; ch->head = ch->mask ? (n2 & ch->mask) : (n2 % ch->capacity);
            ch->count--; d->addr = tmp.ptr; d->len = tmp.len;
            kc_chan_update_recv_stats_len_locked(ch, tmp.len);
            KC_COND_SIGNAL(&ch->cv_send);
            KC_MUTEX_UNLOCK(&ch->mu); return 0;
        }
        if (ch->closed && ch->count == 0) { KC_MUTEX_UNLOCK(&ch->mu); return KC_EPIPE; }
        KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN;
    }
    assert(kcoro_current() != NULL);
    long deadline_ns = 0; int timed = (timeout_ms > 0);
    if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;
again:
    KC_MUTEX_LOCK(&ch->mu);
    zref_assert_invariants(ch);
    if (!(ch->capabilities & KC_CHAN_CAP_ZERO_COPY)) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOTSUP; }
    if (ch->kind != KC_RENDEZVOUS) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOTSUP; }
    ch->zref_mode = 1;
    if (ch->zref_ready) {
        d->addr = ch->zref_ptr; d->len = ch->zref_len; ch->zref_ready=0; ch->zref_ptr=NULL; ch->zref_len=0;
        ch->zref_received++; ch->zref_last_consumed_epoch = ch->zref_epoch;
        ch->rv_matches++;
        ch->rv_zdesc_matches++;
        kc_chan_update_recv_stats_len_locked(ch, d->len);
        /* Wake first parked zref sender */
        kcoro_t *wake_co = NULL;
        struct kc_waiter *w = zref_pop_first_sender(&ch->wq_send_head, &ch->wq_send_tail);
        /* Rare anomaly: receiver consumed while no sender waiter was queued. */
        if (!w) {
            const char *dbg = getenv("KCORO_DEBUG");
            if (dbg && *dbg && dbg[0] != '0') {
                fprintf(stderr, "[kcoro][zref] recv_zref: no sender waiter (debug)\n");
            }
        } else {
            if (w->co) {
                wake_co = w->co;
                kcoro_retain(wake_co);
            }
            kc_waiter_dispose(w);
            ch->zref_sender_waiter_expected = 0;
        }
        zref_assert_invariants(ch);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_zref_schedule_co(wake_co);
        return 0;
    }
    if (ch->closed) { KC_MUTEX_UNLOCK(&ch->mu); return KC_EPIPE; }
    { int r = zref_wait_recv(ch, timeout_ms, &deadline_ns); if (r==1) goto again; else return r; }
}

static int zref_send_c(kc_chan_t *c, const kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct)
{ if (ct && kc_cancel_is_set(ct)) return KC_ECANCELED; return zref_send(c, d, tmo_ms); }
static int zref_recv_c(kc_chan_t *c, kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct)
{ if (ct && kc_cancel_is_set(ct)) return KC_ECANCELED; return zref_recv(c, d, tmo_ms); }

static const kc_zcopy_backend_ops_t g_zref_ops = {
    .attach = NULL, .detach=NULL,
    .send = zref_send, .recv = zref_recv,
    .send_c = zref_send_c, .recv_c = zref_recv_c,
};

/* Public zref wrappers now call unified descriptor API */
int kc_chan_send_zref(kc_chan_t *c, void *ptr, size_t len, long timeout_ms)
{
    kc_zdesc_t d = { .addr = ptr, .len = len };
    return kc_chan_send_desc(c, &d, timeout_ms);
}
int kc_chan_send_zref_c(kc_chan_t *c, void *ptr, size_t len, long timeout_ms, const kc_cancel_t *ct)
{
    kc_zdesc_t d = { .addr = ptr, .len = len };
    return kc_chan_send_desc_c(c, &d, timeout_ms, ct);
}
int kc_chan_recv_zref(kc_chan_t *c, void **out_ptr, size_t *out_len, long timeout_ms)
{
    kc_zdesc_t d={0}; int rc = kc_chan_recv_desc(c, &d, timeout_ms);
    if (rc==0) { if (out_ptr) *out_ptr=d.addr; if (out_len) *out_len=d.len; }
    return rc;
}
int kc_chan_recv_zref_c(kc_chan_t *c, void **out_ptr, size_t *out_len, long timeout_ms, const kc_cancel_t *ct)
{
    kc_zdesc_t d={0}; int rc = kc_chan_recv_desc_c(c, &d, timeout_ms, ct);
    if (rc==0) { if (out_ptr) *out_ptr=d.addr; if (out_len) *out_len=d.len; }
    return rc;
}


/* kc_chan_recv_ptr is defined in kc_chan.c */

/* kc_chan_send_ptr_c is defined in kc_chan.c */

/* kc_chan_recv_ptr_c is defined in kc_chan.c */
/* Unified descriptor-based API */
int kc_chan_send_desc(kc_chan_t *c, const kc_zdesc_t *d, long tmo_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (!ch->zc_ops || !ch->zc_ops->send) return -ENOTSUP;
    return ch->zc_ops->send(c, d, tmo_ms);
}

int kc_chan_recv_desc(kc_chan_t *c, kc_zdesc_t *d, long tmo_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (!ch->zc_ops || !ch->zc_ops->recv) return -ENOTSUP;
    return ch->zc_ops->recv(c, d, tmo_ms);
}

int kc_chan_send_desc_c(kc_chan_t *c, const kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (ct && kc_cancel_is_set(ct)) return KC_ECANCELED;
    if (!ch->zc_ops) return -ENOTSUP;
    if (ch->zc_ops->send_c) return ch->zc_ops->send_c(c, d, tmo_ms, ct);
    /* Fallback: slice loop using non-cancellable send */
    if (!ct) return kc_chan_send_desc(c, d, tmo_ms);
    const long SLICE_MS = KCORO_CANCEL_SLICE_MS;
    if (tmo_ms == 0) return kc_chan_send_desc(c, d, 0);
    if (tmo_ms < 0) {
        for (;;) { if (kc_cancel_is_set(ct)) return KC_ECANCELED; int rc = kc_chan_send_desc(c, d, SLICE_MS); if (rc==0) return 0; if (rc!=KC_ETIME && rc!=KC_EAGAIN) return rc; }
    } else {
        long remain=tmo_ms; while (remain>=0) { if (kc_cancel_is_set(ct)) return KC_ECANCELED; long slice=(SLICE_MS<remain)?SLICE_MS:remain; int rc=kc_chan_send_desc(c,d,slice); if (rc==0) return 0; if (rc!=KC_ETIME && rc!=KC_EAGAIN) return rc; remain-=slice; if (remain<=0) return KC_ETIME; } return KC_ETIME;
    }
}

int kc_chan_recv_desc_c(kc_chan_t *c, kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (ct && kc_cancel_is_set(ct)) return KC_ECANCELED;
    if (!ch->zc_ops) return -ENOTSUP;
    if (ch->zc_ops->recv_c) return ch->zc_ops->recv_c(c, d, tmo_ms, ct);
    if (!ct) return kc_chan_recv_desc(c, d, tmo_ms);
    const long SLICE_MS = KCORO_CANCEL_SLICE_MS;
    if (tmo_ms == 0) return kc_chan_recv_desc(c, d, 0);
    if (tmo_ms < 0) {
        for (;;) { if (kc_cancel_is_set(ct)) return KC_ECANCELED; int rc = kc_chan_recv_desc(c, d, SLICE_MS); if (rc==0 || rc==KC_EPIPE) return rc; if (rc!=KC_ETIME && rc!=KC_EAGAIN) return rc; }
    } else {
        long remain=tmo_ms; while (remain>=0) { if (kc_cancel_is_set(ct)) return KC_ECANCELED; long slice=(SLICE_MS<remain)?SLICE_MS:remain; int rc=kc_chan_recv_desc(c,d,slice); if (rc==0 || rc==KC_EPIPE) return rc; if (rc!=KC_ETIME && rc!=KC_EAGAIN) return rc; remain-=slice; if (remain<=0) return KC_ETIME; } return KC_ETIME;
    }
}
/* No direct dependency on kc_chan's wake helpers; we rely on condvars and
 * cooperative yields for test coverage paths. */
/* Runtime debug logging (enabled when KCORO_DEBUG=1). */
/* logging helpers disabled by default */
/* use only when adding ad-hoc debug lines; keep to silence unused warnings */
static inline __attribute__((unused)) void zlog(const char*fmt,...){ (void)fmt; }
