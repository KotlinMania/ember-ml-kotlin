// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200112L
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <stdio.h>
#include <stdarg.h>
#include <limits.h>
/*
 * kc_chan.c — Channel kinds and operations
 * ----------------------------------------
 *
 * Channels coordinate data exchange between coroutines. They implement
 * rendezvous (0‑buffer), buffered rings, conflated latest‑value, and unlimited
 * growth, while preserving clear error semantics:
 *   - EAGAIN (try path would block), ETIME (bounded wait timed out),
 *     ECANCELED (token set), EPIPE (closed with no progress possible).
 * Matching and minimal locking happen here; the coroutine engine decides where
 * resumed work runs (inline vs scheduler dispatch).
 */
#include <assert.h>

#include "../../include/kcoro.h"
#include "../../include/kcoro_port.h"
#include "../../include/kcoro_config.h"
#include "../../include/kcoro_core.h"
#include "../../include/kcoro_sched.h"
#include "../../include/kcoro_zcopy.h"
#include "kc_select_internal.h"
#include "kc_chan_internal.h" /* single definition of struct kc_chan + helpers */
#include "../../include/kcoro_config_runtime.h"

/* No compile-time debug macros; use runtime logging via kc_dbg()/KCORO_DEBUG. */

/* struct kc_chan and waiter types come from kc_chan_internal.h */

/* kc_now_ns is provided inline in kc_chan_internal.h */

/* Update throughput statistics - called with mutex held (only on sampled ops) */
void kc_chan_emit_metrics_if_needed(struct kc_chan *ch, long now)
{
    if (!ch->metrics_pipe) return;
    const struct kc_runtime_config *cfg = kc_runtime_config_get();
    unsigned long min_ops = cfg ? cfg->chan_metrics_emit_min_ops : 1024UL;
    long min_ns = (cfg ? cfg->chan_metrics_emit_min_ms : 50) * 1000000L;
    unsigned long delta_ops = (ch->total_sends - ch->last_emit_sends) + (ch->total_recvs - ch->last_emit_recvs);
    long since_ns = now - ch->last_emit_time_ns;
    if (delta_ops < min_ops && since_ns < min_ns) return;
    struct kc_chan_metrics_event ev;
    ev.chan = ch;
    ev.total_sends = ch->total_sends;
    ev.total_recvs = ch->total_recvs;
    ev.total_bytes_sent = ch->total_bytes_sent;
    ev.total_bytes_recv = ch->total_bytes_recv;
    ev.delta_sends = ch->total_sends - ch->last_emit_sends;
    ev.delta_recvs = ch->total_recvs - ch->last_emit_recvs;
    ev.delta_bytes_sent = ch->total_bytes_sent - ch->last_emit_bytes_sent;
    ev.delta_bytes_recv = ch->total_bytes_recv - ch->last_emit_bytes_recv;
    ev.first_op_time_ns = ch->first_op_time_ns;
    ev.last_op_time_ns = ch->last_op_time_ns;
    ev.emit_time_ns = now;
    if (kc_chan_try_send((kc_chan_t*)ch->metrics_pipe, &ev) == 0) {
        ch->last_emit_sends = ch->total_sends;
        ch->last_emit_recvs = ch->total_recvs;
        ch->last_emit_bytes_sent = ch->total_bytes_sent;
        ch->last_emit_bytes_recv = ch->total_bytes_recv;
        ch->last_emit_time_ns = now;
    }
}

static inline void kc_chan_update_send_stats_locked(struct kc_chan *ch)
{
    ch->total_sends++;
    ch->total_bytes_sent += ch->elem_sz;
    /* Always record timing for every operation so duration becomes
     * non-zero immediately (previously gated by emit_check). */
    long now = kc_now_ns();
    if (ch->first_op_time_ns == 0) ch->first_op_time_ns = now;
    ch->last_op_time_ns = now;
    if ((++ch->ops_since_emit_check & ch->emit_check_mask) == 0) {
        kc_chan_emit_metrics_if_needed(ch, now);
    }
}

static inline void kc_chan_update_recv_stats_locked(struct kc_chan *ch)
{
    ch->total_recvs++;
    ch->total_bytes_recv += ch->elem_sz;
    long now = kc_now_ns();
    if (ch->first_op_time_ns == 0) ch->first_op_time_ns = now;
    ch->last_op_time_ns = now;
    if ((++ch->ops_since_emit_check & ch->emit_check_mask) == 0) {
        kc_chan_emit_metrics_if_needed(ch, now);
    }
}

/* Variant for zero-copy where the logical payload length may differ from elem_sz. */
void kc_chan_update_send_stats_len_locked(struct kc_chan *ch, size_t len)
{
    ch->total_sends++;
    ch->total_bytes_sent += len;
    long now = kc_now_ns();
    if (ch->first_op_time_ns == 0) ch->first_op_time_ns = now;
    ch->last_op_time_ns = now;
    if ((++ch->ops_since_emit_check & ch->emit_check_mask) == 0) {
        kc_chan_emit_metrics_if_needed(ch, now);
    }
}

void kc_chan_update_recv_stats_len_locked(struct kc_chan *ch, size_t len)
{
    ch->total_recvs++;
    ch->total_bytes_recv += len;
    long now = kc_now_ns();
    if (ch->first_op_time_ns == 0) ch->first_op_time_ns = now;
    ch->last_op_time_ns = now;
    if ((++ch->ops_since_emit_check & ch->emit_check_mask) == 0) {
        kc_chan_emit_metrics_if_needed(ch, now);
    }
}

/* Lightweight debug helper (enabled via KCORO_DEBUG env var). */
static int kc_dbg_enabled(void)
{
    static int init = 0, on = 0;
    if (!init) {
        const char *s = getenv("KCORO_DEBUG");
        on = (s && *s && s[0] != '0');
        init = 1;
    }
    return on;
}

static void kc_dbg(const char *fmt, ...)
{
    if (!kc_dbg_enabled()) return;
    va_list ap; va_start(ap, fmt);
    fprintf(stderr, "[kcoro] ");
    vfprintf(stderr, fmt, ap);
    fprintf(stderr, "\n");
    va_end(ap);
}

static int __attribute__((unused)) timespec_from_ms(struct timespec *ts, long timeout_ms)
{
    if (timeout_ms < 0) return -1; /* infinite */
    struct timespec now;
    /* Use CLOCK_MONOTONIC to match condition variables configured in port */
#ifdef CLOCK_MONOTONIC
    clock_gettime(CLOCK_MONOTONIC, &now);
#else
    clock_gettime(CLOCK_REALTIME, &now);
#endif
    
    long sec = timeout_ms / 1000;
    long nsec = (timeout_ms % 1000) * 1000000L;
    ts->tv_sec = now.tv_sec + sec;
    ts->tv_nsec = now.tv_nsec + nsec;
    if (ts->tv_nsec >= 1000000000L) { 
        ts->tv_sec++; 
        ts->tv_nsec -= 1000000000L; 
    }
    return 0;
}

/* Round up to next power-of-two (minimum 1). */
static size_t kc_next_pow2(size_t x)
{
    if (x < 2) return 1;
    x--; x |= x >> 1; x |= x >> 2; x |= x >> 4; x |= x >> 8; x |= x >> 16;
#if ULONG_MAX > 0xffffffffUL
    x |= x >> 32;
#endif
    return x + 1;
}

/* Compute ring index with optional mask fast-path. */
/* kc_ring_idx is provided inline in kc_chan_internal.h */

struct kc_wake {
    kcoro_t *co;
    kc_select_t *sel;
};

static void kc_chan_schedule_wake(struct kc_wake wake)
{
    if (!wake.co) return;
    kcoro_t *co = wake.co;
    kc_sched_t *current = kc_sched_current();
    int was_parked = kcoro_is_parked(co);
    if (was_parked) {
        kcoro_unpark(co);
    }
    if (!current) {
        kc_sched_t *s = kc_sched_default();
        kc_sched_enqueue_ready(s, co);
    } else {
        kc_sched_enqueue_ready(current, co);
    }
    kcoro_release(co);
}

struct kc_wake_list {
    struct kc_wake items[4];
    int count;
};

static void kc_wake_list_append(struct kc_wake_list *list, struct kc_wake wake)
{
    if (!wake.co) return;
    if (list->count < (int)(sizeof(list->items) / sizeof(list->items[0]))) {
        list->items[list->count++] = wake;
    } else {
        kcoro_release(wake.co);
    }
}

static void kc_wake_list_schedule(struct kc_wake_list *list)
{
    for (int i = 0; i < list->count; ++i) {
        kc_chan_schedule_wake(list->items[i]);
    }
    list->count = 0;
}

static int kc_waiter_token_ensure_enqueued(struct kc_waiter_token *token,
                                           struct kc_chan *ch,
                                           enum kc_select_clause_kind clause)
{
    if (kc_waiter_token_is_enqueued(token)) {
        return 0;
    }
    struct kc_waiter *w = kc_waiter_new_coro(clause);
    if (!w) {
        return -ENOMEM;
    }
    if (clause == KC_SELECT_CLAUSE_SEND) {
        kc_waiter_append(&ch->wq_send_head, &ch->wq_send_tail, w);
    } else {
        kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
    }
    token->status = KC_WAITER_TOKEN_ENQUEUED;
    return 0;
}

/* use kc_waiter_new_coro from kc_chan_internal.h */

static struct kc_waiter* kc_waiter_new_select(kc_select_t *sel, int clause_index, enum kc_select_clause_kind kind)
{
    struct kc_waiter *w = (struct kc_waiter*)malloc(sizeof(*w));
    if (!w) return NULL;
    w->kind = KC_WAITER_SELECT;
    w->co = kc_select_waiter(sel);
    if (w->co) kcoro_retain(w->co);
    w->sel = sel;
    w->clause_index = clause_index;
    w->clause_kind = kind;
    w->is_zref = 0;
    w->next = NULL;
    w->magic = 0xCAFEBABEUL;
    return w;
}

/* use kc_waiter_append from kc_chan_internal.h */

/* use kc_waiter_pop from kc_chan_internal.h */

/* zref queue scanning lives in zcopy backend */

static struct kc_wake kc_chan_wake_recv_locked(struct kc_chan *ch);
static struct kc_wake kc_chan_wake_send_locked(struct kc_chan *ch);

/* zref invariants now asserted inside kc_zcopy.c */

int kc_chan_make(kc_chan_t **out, int kind, size_t elem_sz, size_t capacity)
{
    if (!out || elem_sz == 0)
        return -EINVAL;
    struct kc_chan *ch = calloc(1, sizeof(*ch));
    if (!ch) return -ENOMEM;
    KC_MUTEX_INIT(&ch->mu);
    KC_COND_INIT(&ch->cv_send);
    KC_COND_INIT(&ch->cv_recv);
    ch->wq_send_head = ch->wq_send_tail = NULL;
    ch->wq_recv_head = ch->wq_recv_tail = NULL;
    ch->kind = kind;
    ch->elem_sz = elem_sz;
    if (kind == KC_CONFLATED) {
        ch->slot = malloc(elem_sz);
        if (!ch->slot) { free(ch); return -ENOMEM; }
        ch->has_value = 0;
    } else if (kind == KC_RENDEZVOUS) {
        ch->slot = malloc(elem_sz);
        if (!ch->slot) { free(ch); return -ENOMEM; }
        ch->has_value = 0;
    } else {
        ch->capacity = capacity ? capacity : (kind > 0 ? (size_t)kind : 64);
        /* Prefer power-of-two capacity for fast ring math (non-unlimited). */
        if (kind != KC_UNLIMITED) {
            size_t pow2 = kc_next_pow2(ch->capacity);
            ch->capacity = pow2;
        }
        ch->mask = (ch->capacity && ( (ch->capacity & (ch->capacity - 1)) == 0)) ? (ch->capacity - 1) : 0;
        ch->buf = malloc(ch->capacity * elem_sz);
        if (!ch->buf) { free(ch); return -ENOMEM; }
    }
    *out = ch;
    kc_dbg("chan%p make kind=%d elem_sz=%zu cap=%zu", (void*)ch, kind, elem_sz,
           (kind == KC_BUFFERED || kind > 0) ? ch->capacity : 0);
    const struct kc_runtime_config *cfg_aut = kc_runtime_config_get();
    if (cfg_aut && cfg_aut->chan_metrics_auto_enable) {
        kc_chan_t *pipe_tmp = NULL;
        kc_chan_enable_metrics_pipe((kc_chan_t*)ch, &pipe_tmp, cfg_aut->chan_metrics_pipe_capacity);
    }
    ch->emit_check_mask = 0x3FFUL; /* default: every 1024 ops */
    ch->ops_since_emit_check = 0;
    return 0;
}

void kc_chan_destroy(kc_chan_t *c)
{
    if (!c) return;
    struct kc_chan *ch = (struct kc_chan*)c;
    kc_dbg("chan%p destroy", (void*)ch);
    
    /* Ensure channel is closed before destroying to wake any waiters */
    if (!ch->closed) {
        kc_chan_close(c);
    }
    
    free(ch->buf);
    free(ch->slot);
    /* Destroy sync primitives (port-provided). */
    KC_MUTEX_DESTROY(&ch->mu);
    KC_COND_DESTROY(&ch->cv_send);
    KC_COND_DESTROY(&ch->cv_recv);

    free(ch);
}

static int kc_chan_select_deliver_recv_locked(struct kc_chan *ch, struct kc_waiter *w, int *schedule_out, int *consumed_out)
{
    if (schedule_out) *schedule_out = 0;
    if (consumed_out) *consumed_out = 0;
    kc_select_t *sel = w->sel;
    if (!sel || kc_select_is_completed(sel)) return 0;

    int rc = 0;
    void *dst = kc_select_recv_buffer(sel, w->clause_index);
    if (!dst) {
        rc = KC_ECANCELED;
        if (ch->kind == KC_RENDEZVOUS) ch->rv_cancels++;
    } else if (ch->kind == KC_CONFLATED) {
        if (ch->has_value) {
            memcpy(dst, ch->slot, ch->elem_sz);
            ch->has_value = 0;
            kc_chan_update_recv_stats_locked(ch);
            KC_COND_SIGNAL(&ch->cv_send);
            if (consumed_out) *consumed_out = 1;
        } else if (ch->closed) {
            rc = KC_EPIPE;
        } else {
            rc = KC_EAGAIN;
        }
    } else if (ch->kind == KC_RENDEZVOUS) {
        if (ch->has_value) {
            memcpy(dst, ch->slot, ch->elem_sz);
            ch->has_value = 0;
            ch->rv_matches++;
            kc_chan_update_recv_stats_locked(ch);
            if (consumed_out) *consumed_out = 1;
        } else if (ch->closed) {
            rc = KC_EPIPE;
        } else {
            rc = KC_EAGAIN;
        }
    } else {
        if (ch->count > 0) {
            size_t h = kc_ring_idx(ch, ch->head);
            memcpy(dst, ch->buf + (h * ch->elem_sz), ch->elem_sz);
            size_t n2 = ch->head + 1;
            ch->head = ch->mask ? (n2 & ch->mask) : (n2 % ch->capacity);
            ch->count--;
            kc_chan_update_recv_stats_locked(ch);
            KC_COND_SIGNAL(&ch->cv_send);
            if (consumed_out) *consumed_out = 1;
        } else if (ch->closed) {
            rc = KC_EPIPE;
        } else {
            rc = KC_EAGAIN;
        }
    }

    if (rc == KC_EAGAIN)
        return rc;

    if (kc_select_try_complete(sel, w->clause_index, rc)) {
        /* Only schedule if waiter is parked; if we're in its own context (immediate path),
         * it will continue after registration without needing scheduling. */
        kcoro_t *co = kc_select_waiter(sel);
        if (co && kcoro_is_parked(co) && schedule_out) {
            *schedule_out = 1;
        }
    }
    return rc;
}

static int kc_chan_select_deliver_send_locked(struct kc_chan *ch, struct kc_waiter *w, int *schedule_out)
{
    if (schedule_out) *schedule_out = 0;
    kc_select_t *sel = w->sel;
    if (!sel || kc_select_is_completed(sel)) return 0;

    int rc = 0;
    const void *src = kc_select_send_buffer(sel, w->clause_index);

    if (ch->kind == KC_CONFLATED) {
        if (src) memcpy(ch->slot, src, ch->elem_sz);
        ch->has_value = 1;
        kc_chan_update_send_stats_locked(ch);
        KC_COND_SIGNAL(&ch->cv_recv);
    if (kc_select_try_complete(sel, w->clause_index, 0)) {
        kcoro_t *co = kc_select_waiter(sel);
        if (co && kcoro_is_parked(co) && schedule_out) {
            *schedule_out = 1;
        }
    }
        return 0;
    }

    if (ch->kind == KC_RENDEZVOUS) {
        if (src) memcpy(ch->slot, src, ch->elem_sz);
        ch->has_value = 1;
        kc_chan_update_send_stats_locked(ch);
    if (kc_select_try_complete(sel, w->clause_index, 0)) {
        kcoro_t *co = kc_select_waiter(sel);
        if (co && kcoro_is_parked(co) && schedule_out) {
            *schedule_out = 1;
        }
    }
        return 0;
    }

    if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) {
        rc = KC_EAGAIN;
    } else {
        if (ch->count == ch->capacity && ch->kind == KC_UNLIMITED) {
            size_t newcap = ch->capacity ? ch->capacity * 2 : KCORO_UNLIMITED_INIT_CAP;
            unsigned char *nbuf = malloc(newcap * ch->elem_sz);
            if (!nbuf) rc = -ENOMEM;
            else {
                size_t old_cap = ch->capacity; size_t old_mask = ch->mask;
                for (size_t i = 0; i < ch->count; ++i) {
                    size_t idx = old_mask ? ((ch->head + i) & old_mask) : ((ch->head + i) % old_cap);
                    memcpy(nbuf + (i * ch->elem_sz), ch->buf + (idx * ch->elem_sz), ch->elem_sz);
                }
                free(ch->buf);
                ch->buf = nbuf;
                ch->capacity = newcap;
                ch->mask = newcap - 1;
                ch->head = 0;
                ch->tail = ch->count;
            }
        }
        if (rc == 0) {
            size_t t = kc_ring_idx(ch, ch->tail);
            if (src) memcpy(ch->buf + (t * ch->elem_sz), src, ch->elem_sz);
            size_t next = ch->tail + 1;
            ch->tail = ch->mask ? (next & ch->mask) : (next % ch->capacity);
            ch->count++;
            kc_chan_update_send_stats_locked(ch);
            KC_COND_SIGNAL(&ch->cv_recv);
        }
    if (kc_select_try_complete(sel, w->clause_index, rc)) {
            kcoro_t *co = kc_select_waiter(sel);
            if (co && kcoro_is_parked(co) && schedule_out) {
                *schedule_out = 1;
            }
        }
        return rc;
    }

    if (kc_select_try_complete(sel, w->clause_index, rc)) {
        kcoro_t *co = kc_select_waiter(sel);
        if (co && kcoro_is_parked(co) && schedule_out) {
            *schedule_out = 1;
        }
    }
    return rc;
}

static struct kc_wake kc_chan_wake_recv_locked(struct kc_chan *ch)
{
    struct kc_wake wake = {0};
    for (;;) {
        struct kc_waiter *w = kc_waiter_pop(&ch->wq_recv_head, &ch->wq_recv_tail);
        if (!w) return wake;
        if (w->kind == KC_WAITER_CORO) {
            wake.co = w->co;
            if (wake.co) kcoro_retain(wake.co);
            kc_waiter_dispose(w);
            return wake;
        }
        int schedule = 0;
        int consumed = 0;
        kc_select_t *sel = w->sel;
        kc_chan_select_deliver_recv_locked(ch, w, &schedule, &consumed);
        kc_waiter_dispose(w);
        if (schedule) {
            wake.co = kc_select_waiter(sel);
            if (wake.co) kcoro_retain(wake.co);
            wake.sel = sel;
            return wake;
        }
        /* Otherwise continue to next waiter (select already resolved). */
    }
}

static struct kc_wake kc_chan_wake_send_locked(struct kc_chan *ch)
{
    struct kc_wake wake = {0};
    for (;;) {
        struct kc_waiter *w = kc_waiter_pop(&ch->wq_send_head, &ch->wq_send_tail);
        if (!w) return wake;
        if (w->kind == KC_WAITER_CORO) {
            wake.co = w->co;
            if (wake.co) kcoro_retain(wake.co);
            kc_waiter_dispose(w);
            return wake;
        }
        int schedule = 0;
        kc_select_t *sel = w->sel;
        kc_chan_select_deliver_send_locked(ch, w, &schedule);
        kc_waiter_dispose(w);
        if (schedule) {
            wake.co = kc_select_waiter(sel);
            if (wake.co) kcoro_retain(wake.co);
            wake.sel = sel;
            return wake;
        }
        /* Otherwise select already finished; continue scanning. */
    }
}

int kc_chan_select_register_recv(kc_chan_t *c, kc_select_t *sel, int clause_index)
{
    if (!c || !sel) return -EINVAL;
    struct kc_chan *ch = (struct kc_chan*)c;
    /* Disallow registering selects once zero-copy mode engaged to avoid mixing semantics */
    if (ch->zref_mode) return -ENOTSUP;
    struct kc_wake_list wakes = {0};
    KC_MUTEX_LOCK(&ch->mu);

    if (ch->kind == KC_CONFLATED) {
        if (ch->has_value) {
            void *dst = kc_select_recv_buffer(sel, clause_index);
            int result = 0;
            if (dst) {
                memcpy(dst, ch->slot, ch->elem_sz);
                ch->has_value = 0;
                KC_COND_SIGNAL(&ch->cv_send);
                /* Immediate success: complete select and schedule waiter */
                if (kc_select_try_complete(sel, clause_index, 0)) {
                    kcoro_t *co = kc_select_waiter(sel);
                    if (co && kcoro_is_parked(co)) {
                        kcoro_retain(co);
                        struct kc_wake wake = { .co = co, .sel = sel };
                        kc_wake_list_append(&wakes, wake);
                    }
                }
            } else {
                result = KC_ECANCELED;
            }
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return result;
        }
        if (ch->closed) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return KC_EPIPE;
        }
    } else if (ch->kind == KC_RENDEZVOUS) {
        if (ch->has_value) {
            void *dst = kc_select_recv_buffer(sel, clause_index);
            int result = 0;
            if (dst) {
                memcpy(dst, ch->slot, ch->elem_sz);
                ch->has_value = 0;
                ch->rv_matches++;
                /* Complete select for immediate rendezvous value */
                if (kc_select_try_complete(sel, clause_index, 0)) {
                    kcoro_t *co = kc_select_waiter(sel);
                    if (co && kcoro_is_parked(co)) {
                        kcoro_retain(co);
                        struct kc_wake wake = { .co = co, .sel = sel };
                        kc_wake_list_append(&wakes, wake);
                    }
                }
            } else {
                result = KC_ECANCELED;
                ch->rv_cancels++;
            }
            struct kc_wake send_wake = kc_chan_wake_send_locked(ch);
            kc_wake_list_append(&wakes, send_wake);
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return result;
        }
        if (ch->closed) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return KC_EPIPE;
        }
    } else { /* buffered/unlimited */
        if (ch->count > 0) {
            size_t h = kc_ring_idx(ch, ch->head);
            void *dst = kc_select_recv_buffer(sel, clause_index);
            int result = 0;
            if (dst) {
                memcpy(dst, ch->buf + (h * ch->elem_sz), ch->elem_sz);
                size_t n2 = ch->head + 1;
                ch->head = ch->mask ? (n2 & ch->mask) : (n2 % ch->capacity);
                ch->count--;
                KC_COND_SIGNAL(&ch->cv_send);
                struct kc_wake send_wake = kc_chan_wake_send_locked(ch);
                kc_wake_list_append(&wakes, send_wake);
                if (kc_select_try_complete(sel, clause_index, 0)) {
                    kcoro_t *co = kc_select_waiter(sel);
                    if (co && kcoro_is_parked(co)) {
                        kcoro_retain(co);
                        struct kc_wake wake = { .co = co, .sel = sel };
                        kc_wake_list_append(&wakes, wake);
                    }
                }
            } else {
                result = KC_ECANCELED;
            }
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return result;
        }
        if (ch->closed) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return KC_EPIPE;
        }
    }

    struct kc_waiter *w = kc_waiter_new_select(sel, clause_index, KC_SELECT_CLAUSE_RECV);
    if (!w) {
        KC_MUTEX_UNLOCK(&ch->mu);
        return -ENOMEM;
    }
    kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
    KC_MUTEX_UNLOCK(&ch->mu);
    return KC_EAGAIN;
}

int kc_chan_select_register_send(kc_chan_t *c, kc_select_t *sel, int clause_index)
{
    if (!c || !sel) return -EINVAL;
    struct kc_chan *ch = (struct kc_chan*)c;
    if (ch->zref_mode) return -ENOTSUP;
    struct kc_wake_list wakes = {0};
    const void *src = kc_select_send_buffer(sel, clause_index);
    if (!src) src = NULL;

    KC_MUTEX_LOCK(&ch->mu);
    if (ch->closed) {
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_wake_list_schedule(&wakes);
        return KC_EPIPE;
    }

    if (ch->kind == KC_CONFLATED) {
        if (src) memcpy(ch->slot, src, ch->elem_sz);
        ch->has_value = 1;
        KC_COND_SIGNAL(&ch->cv_recv);
        /* Deliver immediately to any waiting select recv waiter */
        struct kc_wake recv_wake = kc_chan_wake_recv_locked(ch);
        kc_wake_list_append(&wakes, recv_wake);
        if (kc_select_try_complete(sel, clause_index, 0)) {
            kcoro_t *co = kc_select_waiter(sel);
            if (co && kcoro_is_parked(co)) {
                kcoro_retain(co);
                struct kc_wake wake = { .co = co, .sel = sel };
                kc_wake_list_append(&wakes, wake);
            }
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_wake_list_schedule(&wakes);
        return 0;
    }

    if (ch->kind == KC_RENDEZVOUS) {
        if (ch->wq_recv_head) {
            if (src) memcpy(ch->slot, src, ch->elem_sz);
            ch->has_value = 1;
            struct kc_wake recv_wake = kc_chan_wake_recv_locked(ch);
            kc_wake_list_append(&wakes, recv_wake);
            /* Select waiter will be scheduled via wake list; completion happens in deliver */
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return 0;
        }
    } else { /* buffered/unlimited */
        if (ch->count < ch->capacity || ch->kind == KC_UNLIMITED) {
            if (ch->count == ch->capacity && ch->kind == KC_UNLIMITED) {
                size_t newcap = ch->capacity ? ch->capacity * 2 : KCORO_UNLIMITED_INIT_CAP;
                unsigned char *nbuf = malloc(newcap * ch->elem_sz);
                if (!nbuf) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
                size_t old_cap = ch->capacity; size_t old_mask = ch->mask;
                for (size_t i = 0; i < ch->count; ++i) {
                    size_t idx = old_mask ? ((ch->head + i) & old_mask) : ((ch->head + i) % old_cap);
                    memcpy(nbuf + (i * ch->elem_sz), ch->buf + (idx * ch->elem_sz), ch->elem_sz);
                }
                free(ch->buf);
                ch->buf = nbuf;
                ch->capacity = newcap;
                ch->mask = newcap - 1;
                ch->head = 0;
                ch->tail = ch->count;
            }
            size_t t = kc_ring_idx(ch, ch->tail);
            if (src) memcpy(ch->buf + (t * ch->elem_sz), src, ch->elem_sz);
            size_t next = ch->tail + 1;
            ch->tail = ch->mask ? (next & ch->mask) : (next % ch->capacity);
            ch->count++;
            KC_COND_SIGNAL(&ch->cv_recv);
            struct kc_wake recv_wake = kc_chan_wake_recv_locked(ch);
            kc_wake_list_append(&wakes, recv_wake);
            if (kc_select_try_complete(sel, clause_index, 0)) {
                kcoro_t *co = kc_select_waiter(sel);
                if (co && kcoro_is_parked(co)) {
                    kcoro_retain(co);
                    struct kc_wake wake = { .co = co, .sel = sel };
                    kc_wake_list_append(&wakes, wake);
                }
            }
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_wake_list_schedule(&wakes);
            return 0;
        }
    }

    struct kc_waiter *w = kc_waiter_new_select(sel, clause_index, KC_SELECT_CLAUSE_SEND);
    if (!w) {
        KC_MUTEX_UNLOCK(&ch->mu);
        return -ENOMEM;
    }
    kc_waiter_append(&ch->wq_send_head, &ch->wq_send_tail, w);
    KC_MUTEX_UNLOCK(&ch->mu);
    return KC_EAGAIN;
}

void kc_chan_select_cancel(kc_chan_t *c, kc_select_t *sel, int clause_index, enum kc_select_clause_kind kind)
{
    if (!c || !sel) return;
    struct kc_chan *ch = (struct kc_chan*)c;
    KC_MUTEX_LOCK(&ch->mu);

    struct kc_waiter **lists_head[] = { &ch->wq_recv_head, &ch->wq_send_head };
    struct kc_waiter **lists_tail[] = { &ch->wq_recv_tail, &ch->wq_send_tail };

    for (int i = 0; i < 2; ++i) {
        struct kc_waiter **head = lists_head[i];
        struct kc_waiter **tail = lists_tail[i];
        struct kc_waiter *prev = NULL;
        struct kc_waiter *cur = *head;
        while (cur) {
            int match = (cur->kind == KC_WAITER_SELECT && cur->sel == sel && cur->clause_index == clause_index && cur->clause_kind == kind);
            if (match) {
                struct kc_waiter *dead = cur;
                if (prev) prev->next = cur->next; else *head = cur->next;
                cur = cur->next;
                if (dead == *tail) *tail = prev;
                kc_waiter_dispose(dead);
                if (ch->kind == KC_RENDEZVOUS) ch->rv_cancels++;
            } else {
                prev = cur;
                cur = cur->next;
            }
        }
    }

    KC_MUTEX_UNLOCK(&ch->mu);
}

void kc_chan_close(kc_chan_t *c)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    KC_MUTEX_LOCK(&ch->mu);
    ch->closed = 1;
    ch->zref_sender_waiter_expected = 0; /* clear to avoid invariant trips after close */
    /* Close policy for zero-copy staged pointer:
     * If a pointer is already published (zref_ready=1) at the moment of close, we DO NOT
     * discard it here; the consumer may still observe it via kc_chan_recv_zref until consumed.
     * After that, further recv attempts will yield KC_EPIPE once no staged pointer remains.
     * This mirrors conventional rendezvous semantics where a send that has logically happened
     * before close must still pair with a receive. */
    kc_dbg("chan%p close", (void*)ch);
    KC_COND_BROADCAST(&ch->cv_send);
    KC_COND_BROADCAST(&ch->cv_recv);
    struct kc_wake_list wakes = {0};

    struct kc_waiter *w;
    while ((w = kc_waiter_pop(&ch->wq_send_head, &ch->wq_send_tail)) != NULL) {
        if (w->kind == KC_WAITER_CORO) {
            if (w->co) kcoro_retain(w->co);
            struct kc_wake wake = { .co = w->co };
            kc_wake_list_append(&wakes, wake);
        } else if (w->sel) {
            if (kc_select_try_complete(w->sel, w->clause_index, KC_EPIPE)) {
                kcoro_t *co = kc_select_waiter(w->sel);
                if (co) kcoro_retain(co);
                struct kc_wake wake = { .co = co, .sel = w->sel };
                kc_wake_list_append(&wakes, wake);
            }
        }
        if (ch->kind == KC_RENDEZVOUS) ch->rv_cancels++;
        kc_waiter_dispose(w);
    }
    while ((w = kc_waiter_pop(&ch->wq_recv_head, &ch->wq_recv_tail)) != NULL) {
        if (w->kind == KC_WAITER_CORO) {
            if (w->co) kcoro_retain(w->co);
            struct kc_wake wake = { .co = w->co };
            kc_wake_list_append(&wakes, wake);
        } else if (w->sel) {
            if (kc_select_try_complete(w->sel, w->clause_index, KC_EPIPE)) {
                kcoro_t *co = kc_select_waiter(w->sel);
                if (co) kcoro_retain(co);
                struct kc_wake wake = { .co = co, .sel = w->sel };
                kc_wake_list_append(&wakes, wake);
            }
        }
        if (ch->kind == KC_RENDEZVOUS) ch->rv_cancels++;
        kc_waiter_dispose(w);
    }
    KC_MUTEX_UNLOCK(&ch->mu);
    kc_wake_list_schedule(&wakes);
}

unsigned kc_chan_len(kc_chan_t *c)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    KC_MUTEX_LOCK(&ch->mu);
    unsigned v = 0;
    if (ch->kind == KC_CONFLATED)
        v = ch->has_value ? 1 : 0;
    else
        v = (unsigned)ch->count;
    KC_MUTEX_UNLOCK(&ch->mu);
    return v;
}

int kc_chan_send(kc_chan_t *c, const void *msg, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !msg) return -EINVAL;
    if (ch->zref_mode) return -EINVAL; /* disallow mixing modes */
    /* Require coroutine context (no thread-blocking). */
    assert(kcoro_current() != NULL);
    long deadline_ns = 0; int timed = (timeout_ms > 0);
    if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;
again_send:
    KC_MUTEX_LOCK(&ch->mu);
    kc_dbg("chan%p send kind=%d tmo=%ld cnt=%zu cap=%zu", (void*)ch, ch->kind,
           timeout_ms, ch->count, ch->capacity);
    if (ch->closed) { ch->send_epipe++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EPIPE; }

    struct kc_wake wake_recv = {0};

    if (ch->kind == KC_CONFLATED) {
        memcpy(ch->slot, msg, ch->elem_sz);
        ch->has_value = 1;
        kc_chan_update_send_stats_locked(ch);
        KC_COND_SIGNAL(&ch->cv_recv);
        wake_recv = kc_chan_wake_recv_locked(ch);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_recv);
        kc_dbg("chan%p send conflated set", (void*)ch);
        return 0;
    }

    if (ch->kind == KC_RENDEZVOUS) {
        if (ch->wq_recv_head == NULL || ch->has_value) {
            if (timeout_ms == 0) { ch->send_eagain++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN; }
            if (timed) {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (kc_now_ns() >= deadline_ns) return KC_ETIME;
                kcoro_yield();
                goto again_send;
            }
            struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_SEND);
            if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
            kc_waiter_append(&ch->wq_send_head, &ch->wq_send_tail, w);
            KC_MUTEX_UNLOCK(&ch->mu);
            kcoro_yield();
            goto again_send;
        }
        memcpy(ch->slot, msg, ch->elem_sz);
        ch->has_value = 1;
        kc_chan_update_send_stats_locked(ch);
        KC_COND_SIGNAL(&ch->cv_recv);
        wake_recv = kc_chan_wake_recv_locked(ch);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_recv);
        return 0;
    }

    /* buffered/unlimited */
    int rc = 0;
    if (timeout_ms == 0) {
        if (ch->count == ch->capacity) {
            if (ch->kind == KC_UNLIMITED) {
                size_t newcap = ch->capacity ? ch->capacity * 2 : KCORO_UNLIMITED_INIT_CAP;
                unsigned char *nbuf = malloc(newcap * ch->elem_sz);
                if (!nbuf) { KC_MUTEX_UNLOCK(&ch->mu); kc_dbg("chan%p grow ENOMEM", (void*)ch); return -ENOMEM; }
                size_t old_cap = ch->capacity; size_t old_mask = ch->mask;
                for (size_t i = 0; i < ch->count; ++i) {
                    size_t idx = old_mask ? ((ch->head + i) & old_mask) : ((ch->head + i) % old_cap);
                    memcpy(nbuf + (i * ch->elem_sz), ch->buf + (idx * ch->elem_sz), ch->elem_sz);
                }
                free(ch->buf); ch->buf = nbuf; ch->capacity = newcap; ch->mask = newcap - 1; ch->head = 0; ch->tail = ch->count;
            } else { ch->send_eagain++; KC_MUTEX_UNLOCK(&ch->mu); kc_dbg("chan%p send EAGAIN (full)", (void*)ch); return KC_EAGAIN; }
        }
    } else if (timeout_ms < 0) {
        if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) {
            /* Park cooperatively */
            struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_SEND);
            if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
            kc_waiter_append(&ch->wq_send_head, &ch->wq_send_tail, w);
            KC_MUTEX_UNLOCK(&ch->mu);
            kcoro_yield();
            goto again_send;
        }
    } else {
        /* Timed waits: cooperative yield-retry until deadline */
        if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) {
            KC_MUTEX_UNLOCK(&ch->mu);
            if (kc_now_ns() >= deadline_ns) { ch->send_etime++; return KC_ETIME; }
            kcoro_yield();
            goto again_send;
        }
    }
    if (rc == 0 && !ch->closed) {
        size_t t = kc_ring_idx(ch, ch->tail);
        memcpy(ch->buf + (t * ch->elem_sz), msg, ch->elem_sz);
        size_t next = ch->tail + 1; ch->tail = ch->mask ? (next & ch->mask) : (next % ch->capacity);
        ch->count++;
        kc_chan_update_send_stats_locked(ch);
        KC_COND_SIGNAL(&ch->cv_recv);
        wake_recv = kc_chan_wake_recv_locked(ch);
        kc_dbg("chan%p send ok cnt=%zu", (void*)ch, ch->count);
    }
    KC_MUTEX_UNLOCK(&ch->mu);
    kc_chan_schedule_wake(wake_recv);
    return rc;
}

int kc_chan_recv(kc_chan_t *c, void *out, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !out) return -EINVAL;
    if (ch->ptr_mode) return -EINVAL; /* pointer descriptor channels use kc_chan_recv_ptr */
    if (ch->zref_mode) return -EINVAL; /* disallow mixing modes */
    assert(kcoro_current() != NULL);
    long deadline_ns = 0; int timed = (timeout_ms > 0);
    if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;
again_recv:
    KC_MUTEX_LOCK(&ch->mu);
    kc_dbg("chan%p recv kind=%d tmo=%ld cnt=%zu", (void*)ch, ch->kind, timeout_ms, ch->count);
    struct kc_wake wake_send = {0};
    if (ch->kind == KC_CONFLATED) {
        int rc = 0;
        if (timeout_ms == 0) {
            if (!ch->has_value) rc = KC_EAGAIN;
        } else if (timeout_ms < 0) {
            if (!ch->has_value && !ch->closed) {
                struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_RECV);
                if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
                kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
                KC_MUTEX_UNLOCK(&ch->mu);
                kcoro_yield();
                goto again_recv;
            }
        } else {
            if (!ch->has_value && !ch->closed) {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (kc_now_ns() >= deadline_ns) return KC_ETIME;
                kcoro_yield();
                goto again_recv;
            }
        }
        if (rc == 0 && ch->has_value) {
            memcpy(out, ch->slot, ch->elem_sz);
            ch->has_value = 0;
            kc_chan_update_recv_stats_locked(ch);
            KC_COND_SIGNAL(&ch->cv_send);
            wake_send = kc_chan_wake_send_locked(ch);
            kc_dbg("chan%p recv conflated ok", (void*)ch);
        } else if (rc == 0 && ch->closed && !ch->has_value) {
            rc = KC_EPIPE;
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_send);
        return rc;
    }

    if (ch->kind == KC_RENDEZVOUS) {
        int rc = 0;
        if (timeout_ms == 0) {
            if (!ch->has_value) rc = KC_EAGAIN;
        } else if (timeout_ms < 0) {
            if (!ch->has_value && !ch->closed) {
                struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_RECV);
                if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
                kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
                KC_MUTEX_UNLOCK(&ch->mu);
                kcoro_yield();
                goto again_recv;
            }
        } else {
            if (!ch->has_value && !ch->closed) {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (kc_now_ns() >= deadline_ns) return KC_ETIME;
                kcoro_yield();
                goto again_recv;
            }
        }
        if (rc == 0 && ch->has_value) {
            memcpy(out, ch->slot, ch->elem_sz);
            ch->has_value = 0;
            ch->rv_matches++;
            kc_chan_update_recv_stats_locked(ch);
            KC_COND_SIGNAL(&ch->cv_send);
            wake_send = kc_chan_wake_send_locked(ch);
            kc_dbg("chan%p recv rv ok", (void*)ch);
        } else if (rc == 0 && ch->closed && !ch->has_value) {
            rc = KC_EPIPE;
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_send);
        return rc;
    }

    /* buffered/unlimited */
    int rc = 0;
    if (timeout_ms == 0) {
        if (ch->count == 0) {
            int rc_local = ch->closed ? KC_EPIPE : KC_EAGAIN;
            if (rc_local == KC_EAGAIN) ch->recv_eagain++; else ch->recv_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_dbg("chan%p recv %s (empty)", (void*)ch, rc_local==KC_EPIPE?"EPIPE":"EAGAIN");
            return rc_local;
        }
    } else if (timeout_ms < 0) {
        if (ch->count == 0 && !ch->closed) {
            struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_RECV);
            if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
            kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
            KC_MUTEX_UNLOCK(&ch->mu);
            kcoro_yield();
            goto again_recv;
        }
    } else {
        if (ch->count == 0 && !ch->closed) {
            KC_MUTEX_UNLOCK(&ch->mu);
            if (kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; }
            kcoro_yield();
            goto again_recv;
        }
    }
    if (rc == 0 && ch->count > 0) {
        size_t h = kc_ring_idx(ch, ch->head);
        memcpy(out, ch->buf + (h * ch->elem_sz), ch->elem_sz);
        size_t n2 = ch->head + 1; ch->head = ch->mask ? (n2 & ch->mask) : (n2 % ch->capacity);
        ch->count--;
        kc_chan_update_recv_stats_locked(ch);
        KC_COND_SIGNAL(&ch->cv_send);
        wake_send = kc_chan_wake_send_locked(ch);
        kc_dbg("chan%p recv ok cnt=%zu", (void*)ch, ch->count);
    } else if (rc == 0 && ch->closed && ch->count == 0) {
        rc = KC_EPIPE;
        ch->recv_epipe++;
    }
    KC_MUTEX_UNLOCK(&ch->mu);
    kc_chan_schedule_wake(wake_send);
    return rc;
}

/* Cancellable wrappers: slice blocking waits to check cancel token periodically */
static long kc_min_long(long a, long b) { return a < b ? a : b; }

int kc_chan_send_c(kc_chan_t* ch, const void* msg, long timeout_ms, const kc_cancel_t* cancel)
{
    if (!cancel) return kc_chan_send(ch, msg, timeout_ms);
    if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
    if (timeout_ms == 0) return kc_chan_send(ch, msg, 0);
    const long SLICE_MS = KCORO_CANCEL_SLICE_MS; /* coarse */
    if (timeout_ms < 0) {
        for (;;) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            int rc = kc_chan_send(ch, msg, SLICE_MS);
            if (rc == 0) return 0;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
        }
    } else {
        long remain = timeout_ms;
        while (remain >= 0) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            long slice = kc_min_long(SLICE_MS, remain);
            int rc = kc_chan_send(ch, msg, slice);
            if (rc == 0) return 0;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
            remain -= slice;
            if (remain <= 0) return KC_ETIME;
        }
        return KC_ETIME;
    }
}

int kc_chan_recv_c(kc_chan_t* ch, void* out, long timeout_ms, const kc_cancel_t* cancel)
{
    if (!cancel) return kc_chan_recv(ch, out, timeout_ms);
    if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
    if (timeout_ms == 0) return kc_chan_recv(ch, out, 0);
    const long SLICE_MS = KCORO_CANCEL_SLICE_MS;
    if (timeout_ms < 0) {
        for (;;) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            int rc = kc_chan_recv(ch, out, SLICE_MS);
            if (rc == 0) return 0;
            if (rc == KC_EPIPE) return KC_EPIPE;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
        }
    } else {
        long remain = timeout_ms;
        while (remain >= 0) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            long slice = kc_min_long(SLICE_MS, remain);
            int rc = kc_chan_recv(ch, out, slice);
            if (rc == 0) return 0;
            if (rc == KC_EPIPE) return KC_EPIPE;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
            remain -= slice;
            if (remain <= 0) return KC_ETIME;
        }
        return KC_ETIME;
    }
}

/* ===================== Zero-Copy (zref) Implementation ===================== */

unsigned kc_chan_capabilities(kc_chan_t *c) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch) return 0;
    KC_MUTEX_LOCK(&ch->mu);
    unsigned caps = ch->capabilities;
    KC_MUTEX_UNLOCK(&ch->mu);
    return caps;
}

int kc_chan_enable_zero_copy(kc_chan_t *c) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch) return -EINVAL;
    KC_MUTEX_LOCK(&ch->mu);
    ch->capabilities |= KC_CHAN_CAP_ZERO_COPY;
    KC_MUTEX_UNLOCK(&ch->mu);
    extern kc_zcopy_backend_id kc_zcopy_resolve(const char*);
    extern int kc_chan_enable_zero_copy_backend(kc_chan_t*, kc_zcopy_backend_id, const void*);
    kc_zcopy_backend_id id = kc_zcopy_resolve("zref");
    if (id >= 0) (void)kc_chan_enable_zero_copy_backend(c, id, NULL);
    return 0;
}

/* zref wait helpers live in kc_zcopy.c */

/* zref wrappers live in kc_zcopy.c */
int kc_chan_get_zstats(kc_chan_t *c, struct kc_chan_zstats *out) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !out) return -EINVAL;
    KC_MUTEX_LOCK(&ch->mu);
    out->zref_sent = ch->zref_sent;
    out->zref_received = ch->zref_received;
    out->zref_fallback_small = ch->zref_fallback_small;
    out->zref_fallback_capacity = ch->zref_fallback_capacity;
    out->zref_canceled = ch->zref_canceled;
    out->zref_aborted_close = ch->zref_aborted_close;
    KC_MUTEX_UNLOCK(&ch->mu);
    return 0;
}

int kc_chan_get_stats(kc_chan_t *c, struct kc_chan_stats *out) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !out) return -EINVAL;
    
    KC_MUTEX_LOCK(&ch->mu);
    out->total_sends = ch->total_sends;
    out->total_recvs = ch->total_recvs;
    out->total_bytes_sent = ch->total_bytes_sent;
    out->total_bytes_recv = ch->total_bytes_recv;
    out->first_op_time_ns = ch->first_op_time_ns;
    out->last_op_time_ns = ch->last_op_time_ns;
    
    /* Compute rates */
    out->duration_sec = 0.0;
    out->send_rate_ops_sec = 0.0;
    out->recv_rate_ops_sec = 0.0;
    out->send_rate_bytes_sec = 0.0;
    out->recv_rate_bytes_sec = 0.0;
    
    if (ch->first_op_time_ns > 0 && ch->last_op_time_ns > ch->first_op_time_ns) {
        long duration_ns = ch->last_op_time_ns - ch->first_op_time_ns;
        out->duration_sec = (double)duration_ns / 1000000000.0;
        
        if (out->duration_sec > 0.0) {
            out->send_rate_ops_sec = (double)ch->total_sends / out->duration_sec;
            out->recv_rate_ops_sec = (double)ch->total_recvs / out->duration_sec;
            out->send_rate_bytes_sec = (double)ch->total_bytes_sent / out->duration_sec;
            out->recv_rate_bytes_sec = (double)ch->total_bytes_recv / out->duration_sec;
        }
    }
    
    KC_MUTEX_UNLOCK(&ch->mu);
    return 0;
}

int kc_chan_enable_metrics_pipe(kc_chan_t *c, kc_chan_t **out_pipe, size_t capacity) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch) return -EINVAL;
    KC_MUTEX_LOCK(&ch->mu);
    if (!ch->metrics_pipe) {
        kc_chan_t *pipe = NULL;
        size_t cap = capacity ? capacity : 64;
        int rc = kc_chan_make(&pipe, (int)cap, sizeof(struct kc_chan_metrics_event), cap);
        if (rc != 0) { KC_MUTEX_UNLOCK(&ch->mu); return rc; }
        ch->metrics_pipe = (struct kc_chan*)pipe;
        ch->last_emit_sends = ch->total_sends;
        ch->last_emit_recvs = ch->total_recvs;
        ch->last_emit_bytes_sent = ch->total_bytes_sent;
        ch->last_emit_bytes_recv = ch->total_bytes_recv;
        ch->last_emit_time_ns = kc_now_ns();
    }
    if (out_pipe) *out_pipe = (kc_chan_t*)ch->metrics_pipe;
    KC_MUTEX_UNLOCK(&ch->mu);
    return 0;
}

int kc_chan_disable_metrics_pipe(kc_chan_t *c) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch) return -EINVAL;
    KC_MUTEX_LOCK(&ch->mu);
    ch->metrics_pipe = NULL; /* caller retains previous pipe channel pointer */
    KC_MUTEX_UNLOCK(&ch->mu);
    return 0;
}

kc_chan_t *kc_chan_metrics_pipe(kc_chan_t *c) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch) return NULL;
    KC_MUTEX_LOCK(&ch->mu);
    struct kc_chan *p = ch->metrics_pipe;
    KC_MUTEX_UNLOCK(&ch->mu);
    return (kc_chan_t*)p;
}

int kc_chan_snapshot(kc_chan_t *c, struct kc_chan_snapshot *out) {
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !out) return -EINVAL;
    KC_MUTEX_LOCK(&ch->mu);
    memset(out, 0, sizeof(*out));
    out->chan = ch;
    out->kind = ch->kind;
    out->elem_sz = ch->elem_sz;
    out->capacity = ch->capacity;
    if (ch->kind == KC_CONFLATED) out->count = ch->has_value ? 1 : 0; else out->count = ch->count;
    out->capabilities = ch->capabilities;
    out->closed = ch->closed;
    out->zref_mode = ch->zref_mode;
    out->ptr_mode = ch->ptr_mode;
    out->total_sends = ch->total_sends;
    out->total_recvs = ch->total_recvs;
    out->total_bytes_sent = ch->total_bytes_sent;
    out->total_bytes_recv = ch->total_bytes_recv;
    out->first_op_time_ns = ch->first_op_time_ns;
    out->last_op_time_ns = ch->last_op_time_ns;
    out->send_eagain = ch->send_eagain;
    out->send_etime  = ch->send_etime;
    out->send_epipe  = ch->send_epipe;
    out->recv_eagain = ch->recv_eagain;
    out->recv_etime  = ch->recv_etime;
    out->recv_epipe  = ch->recv_epipe;
    out->zref_sent = ch->zref_sent;
    out->zref_received = ch->zref_received;
    out->zref_aborted_close = ch->zref_aborted_close;
    out->rv_matches = ch->rv_matches;
    out->rv_cancels = ch->rv_cancels;
    out->rv_zdesc_matches = ch->rv_zdesc_matches;
    if (ch->first_op_time_ns && ch->last_op_time_ns > ch->first_op_time_ns) {
        long dur = ch->last_op_time_ns - ch->first_op_time_ns;
        out->duration_sec = (double)dur / 1e9;
    }
    KC_MUTEX_UNLOCK(&ch->mu);
    return 0;
}

int kc_chan_compute_rate(const struct kc_chan_snapshot *prev,
                         const struct kc_chan_snapshot *curr,
                         struct kc_chan_rate_sample *out)
{
    if (!curr || !out) return -EINVAL;
    memset(out, 0, sizeof(*out));
    if (!prev) prev = &(struct kc_chan_snapshot){0};
    unsigned long ds = curr->total_sends - prev->total_sends;
    unsigned long dr = curr->total_recvs - prev->total_recvs;
    unsigned long dbs = curr->total_bytes_sent - prev->total_bytes_sent;
    unsigned long dbr = curr->total_bytes_recv - prev->total_bytes_recv;
    out->delta_sends = ds;
    out->delta_recvs = dr;
    out->delta_bytes_sent = dbs;
    out->delta_bytes_recv = dbr;
    out->delta_send_eagain = curr->send_eagain - prev->send_eagain;
    out->delta_recv_eagain = curr->recv_eagain - prev->recv_eagain;
    out->delta_send_epipe  = curr->send_epipe  - prev->send_epipe;
    out->delta_recv_epipe  = curr->recv_epipe  - prev->recv_epipe;
    out->delta_rv_matches = curr->rv_matches - prev->rv_matches;
    out->delta_rv_cancels = curr->rv_cancels - prev->rv_cancels;
    out->delta_rv_zdesc_matches = curr->rv_zdesc_matches - prev->rv_zdesc_matches;
    /* Determine time interval: prefer difference in duration_sec if prev had
     * a valid base; otherwise use curr->duration_sec; fallback microsecond. */
    double base_prev = prev->duration_sec;
    double base_curr = curr->duration_sec;
    double interval = 0.0;
    if (base_prev > 0.0 && base_curr >= base_prev) {
        interval = base_curr - base_prev;
    } else if (base_curr > 0.0) {
        interval = base_curr;
    }
    if (interval <= 0.0 && (ds || dr || dbs || dbr ||
                            out->delta_rv_matches || out->delta_rv_cancels || out->delta_rv_zdesc_matches)) {
        interval = 1e-6; /* minimal */
    }
    out->interval_sec = interval;
    if (interval > 0.0) {
        out->sends_per_sec = ds / interval;
        out->recvs_per_sec = dr / interval;
        out->bytes_sent_per_sec = dbs / interval;
        out->bytes_recv_per_sec = dbr / interval;
    }
    return 0;
}

/* =======================================================================
 * Pointer‑Descriptor API (front-end convenience)
 * -----------------------------------------------------------------------
 * Canonical element: struct kc_chan_ptrmsg { void *ptr; size_t len; }.
 * The channel copies only the small descriptor for queued kinds; rendezvous
 * uses hand‑to‑hand pointer exchange. Statistics account bytes by len on every
 * successful op (send/recv), unified with classic copy paths.
 *
 * When a zero‑copy backend is bound (zc_ops != NULL), these wrappers compose a
 * kc_zdesc and dispatch to the unified descriptor API in kc_zcopy.c.
 * ======================================================================= */

/**
 * Create a pointer‑descriptor channel and bind the unified zref backend.
 * @param out      out parameter for the created channel
 * @param kind     KC_RENDEZVOUS / KC_BUFFERED / KC_UNLIMITED / KC_CONFLATED
 * @param capacity ring capacity for buffered/unlimited kinds (ignored for rv)
 * @return 0 on success; negative KC_* on failure
 */
int kc_chan_make_ptr(kc_chan_t **out, int kind, size_t capacity)
{
    if (!out) return -EINVAL;
    int rc = kc_chan_make(out, kind, sizeof(struct kc_chan_ptrmsg), capacity);
    if (rc != 0) return rc;
    struct kc_chan *ch = (struct kc_chan*)(*out);
    ch->ptr_mode = 1;
    ch->capabilities |= KC_CHAN_CAP_PTR;
    /* Zero-copy backend binding is opt-in for now; pointer channels default to classic path. */
    return 0;
}

/**
 * Send a pointer‑descriptor. length must be > 0.
 * Routes to zref backend when bound; otherwise uses the classic queued path.
 */
int kc_chan_send_ptr(kc_chan_t *c, void *ptr, size_t len, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !ptr || len == 0) return -EINVAL;
    if (!ch->ptr_mode) return -EINVAL;
    assert(kcoro_current() != NULL);

    /* If a zero-copy backend is bound, route through the unified descriptor API. */
    if (ch->zc_ops) {
        kc_dbg("chan%p send_ptr route=zref len=%zu", (void*)ch, len);
        kc_zdesc_t d = { .addr = ptr, .len = len };
        return kc_chan_send_desc(c, &d, timeout_ms);
    }

    const struct kc_chan_ptrmsg msg = { .ptr = ptr, .len = len };
    long deadline_ns = 0; const int timed = (timeout_ms > 0);
    if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;

    struct kc_waiter_token send_token;
    kc_waiter_token_reset(&send_token);

again_send_ptr:
    KC_MUTEX_LOCK(&ch->mu);
    if (ch->closed) { ch->send_epipe++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EPIPE; }
    struct kc_wake wake_recv = {0};

    /* Conflated: keep only the latest descriptor */
    if (ch->kind == KC_CONFLATED) {
        memcpy(ch->slot, &msg, sizeof(msg));
        ch->has_value = 1;
        kc_chan_update_send_stats_len_locked(ch, len);
        KC_COND_SIGNAL(&ch->cv_recv);
        wake_recv = kc_chan_wake_recv_locked(ch);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_recv);
        kc_waiter_token_reset(&send_token);
        return 0;
    }

    /* Rendezvous: publish into slot when a receiver is available */
    if (ch->kind == KC_RENDEZVOUS) {
        if (ch->wq_recv_head == NULL || ch->has_value) {
            if (timeout_ms == 0) { ch->send_eagain++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN; }
            if (timeout_ms < 0) {
                int ensure_rc = kc_waiter_token_ensure_enqueued(&send_token, ch, KC_SELECT_CLAUSE_SEND);
                if (ensure_rc != 0) { KC_MUTEX_UNLOCK(&ch->mu); return ensure_rc; }
                KC_MUTEX_UNLOCK(&ch->mu);
                kcoro_park();
                kc_waiter_token_reset(&send_token);
                goto again_send_ptr;
            }
            KC_MUTEX_UNLOCK(&ch->mu);
            if (kc_now_ns() >= deadline_ns) { ch->send_etime++; return KC_ETIME; }
            kcoro_yield();
            goto again_send_ptr;
        }
        memcpy(ch->slot, &msg, sizeof(msg));
        ch->has_value = 1;
        kc_chan_update_send_stats_len_locked(ch, len);
        KC_COND_SIGNAL(&ch->cv_recv);
        wake_recv = kc_chan_wake_recv_locked(ch);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_recv);
        return 0;
    }

    /* Buffered / Unlimited */
    if (timeout_ms == 0) {
        if (ch->count == ch->capacity) {
            if (ch->kind == KC_UNLIMITED) {
                size_t newcap = ch->capacity ? ch->capacity * 2 : KCORO_UNLIMITED_INIT_CAP;
                unsigned char *nbuf = malloc(newcap * ch->elem_sz);
                if (!nbuf) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
                size_t old_cap = ch->capacity; size_t old_mask = ch->mask;
                for (size_t i = 0; i < ch->count; ++i) {
                    size_t idx = old_mask ? ((ch->head + i) & old_mask) : ((ch->head + i) % old_cap);
                    memcpy(nbuf + (i * ch->elem_sz), ch->buf + (idx * ch->elem_sz), ch->elem_sz);
                }
                free(ch->buf);
                ch->buf = nbuf; ch->capacity = newcap; ch->mask = newcap - 1; ch->head = 0; ch->tail = ch->count;
            } else { ch->send_eagain++; KC_MUTEX_UNLOCK(&ch->mu); return KC_EAGAIN; }
        }
    } else if (timeout_ms < 0) {
        if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) {
            struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_SEND);
            if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
            kc_waiter_append(&ch->wq_send_head, &ch->wq_send_tail, w);
            KC_MUTEX_UNLOCK(&ch->mu);
            kcoro_yield();
            goto again_send_ptr;
        }
    } else {
        if (ch->count == ch->capacity && ch->kind != KC_UNLIMITED) {
            KC_MUTEX_UNLOCK(&ch->mu);
            if (kc_now_ns() >= deadline_ns) { ch->send_etime++; return KC_ETIME; }
            kcoro_yield();
            goto again_send_ptr;
        }
    }

    /* enqueue */
    size_t t = kc_ring_idx(ch, ch->tail);
    memcpy(ch->buf + (t * ch->elem_sz), &msg, sizeof(msg));
    size_t next = ch->tail + 1; ch->tail = ch->mask ? (next & ch->mask) : (next % ch->capacity);
    ch->count++;
    kc_chan_update_send_stats_len_locked(ch, len);
    KC_COND_SIGNAL(&ch->cv_recv);
    wake_recv = kc_chan_wake_recv_locked(ch);
    KC_MUTEX_UNLOCK(&ch->mu);
    kc_chan_schedule_wake(wake_recv);
    kc_waiter_token_reset(&send_token);
    return 0;
}

/**
 * Receive a pointer‑descriptor. Returns the pointer and length.
 * Routes to zref backend when bound; otherwise uses the classic queued path.
 */
int kc_chan_recv_ptr(kc_chan_t *c, void **out_ptr, size_t *out_len, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !out_ptr || !out_len) return -EINVAL;
    if (!ch->ptr_mode) return -EINVAL;
    assert(kcoro_current() != NULL);

    /* If a zero-copy backend is bound, route through the unified descriptor API. */
    if (ch->zc_ops) {
        kc_dbg("chan%p recv_ptr route=zref", (void*)ch);
        kc_zdesc_t d = {0};
        int rc = kc_chan_recv_desc(c, &d, timeout_ms);
        if (rc == 0) { *out_ptr = d.addr; *out_len = d.len; }
        return rc;
    }

    long deadline_ns = 0; const int timed = (timeout_ms > 0);
    if (timed) deadline_ns = kc_now_ns() + timeout_ms * 1000000L;

    struct kc_waiter_token recv_token;
    kc_waiter_token_reset(&recv_token);

again_recv_ptr:
    KC_MUTEX_LOCK(&ch->mu);
    struct kc_wake wake_send = {0};

    if (ch->kind == KC_CONFLATED) {
        int rc = 0;
        if (!ch->has_value) {
            if (timeout_ms == 0) {
                ch->recv_eagain++;
                KC_MUTEX_UNLOCK(&ch->mu);
                return KC_EAGAIN;
            } else if (timeout_ms < 0) {
                if (!ch->closed) {
                    struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_RECV);
                    if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
                    kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
                    KC_MUTEX_UNLOCK(&ch->mu);
                    kcoro_yield();
                    goto again_recv_ptr;
                }
            } else {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; }
                kcoro_yield();
                goto again_recv_ptr;
            }
        }
        if (rc == 0 && ch->has_value) {
            struct kc_chan_ptrmsg tmp; memcpy(&tmp, ch->slot, sizeof(tmp)); ch->has_value = 0;
            *out_ptr = tmp.ptr; *out_len = tmp.len;
            kc_chan_update_recv_stats_len_locked(ch, tmp.len);
            KC_COND_SIGNAL(&ch->cv_send);
            wake_send = kc_chan_wake_send_locked(ch);
        } else if (rc == 0 && ch->closed && !ch->has_value) {
            rc = KC_EPIPE;
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_send);
        return rc;
    }

    /* Rendezvous: consume from slot when present */
    if (ch->kind == KC_RENDEZVOUS) {
        for (;;) {
            if (ch->has_value) {
                struct kc_chan_ptrmsg tmp;
                memcpy(&tmp, ch->slot, sizeof(tmp));
                ch->has_value = 0;
                *out_ptr = tmp.ptr;
                *out_len = tmp.len;
                ch->rv_matches++;
                kc_chan_update_recv_stats_len_locked(ch, tmp.len);
                KC_COND_SIGNAL(&ch->cv_send);
                wake_send = kc_chan_wake_send_locked(ch);
                KC_MUTEX_UNLOCK(&ch->mu);
                kc_chan_schedule_wake(wake_send);
                kc_waiter_token_reset(&recv_token);
                return 0;
            }
            if (ch->closed) {
                KC_MUTEX_UNLOCK(&ch->mu);
                kc_waiter_token_reset(&recv_token);
                return KC_EPIPE;
            }
            if (timeout_ms == 0) {
                if (ch->wq_send_head != NULL) {
                    struct kc_wake wake_sender = kc_chan_wake_send_locked(ch);
                    KC_MUTEX_UNLOCK(&ch->mu);
                    kc_chan_schedule_wake(wake_sender);
                    goto again_recv_ptr;
                }
                ch->recv_eagain++;
                KC_MUTEX_UNLOCK(&ch->mu);
                kc_waiter_token_reset(&recv_token);
                return KC_EAGAIN;
            }
            if (timeout_ms < 0) {
                int ensure_rc = kc_waiter_token_ensure_enqueued(&recv_token, ch, KC_SELECT_CLAUSE_RECV);
                if (ensure_rc != 0) {
                    KC_MUTEX_UNLOCK(&ch->mu);
                    return ensure_rc;
                }
                struct kc_wake wake_sender = {0};
                if (ch->wq_send_head != NULL) {
                    wake_sender = kc_chan_wake_send_locked(ch);
                }
                KC_MUTEX_UNLOCK(&ch->mu);
                kc_chan_schedule_wake(wake_sender);
                kcoro_park();
                kc_waiter_token_reset(&recv_token);
                goto again_recv_ptr;
            }
            /* timeout_ms > 0 */
            struct kc_wake wake_sender = {0};
            if (ch->wq_send_head != NULL) {
                wake_sender = kc_chan_wake_send_locked(ch);
            }
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_chan_schedule_wake(wake_sender);
            if (kc_now_ns() >= deadline_ns) { ch->recv_etime++; kc_waiter_token_reset(&recv_token); return KC_ETIME; }
            kcoro_yield();
            goto again_recv_ptr;
        }
    }

    /* Buffered/unlimited */
    if (timeout_ms == 0) {
        if (ch->count == 0) {
            int rcl = ch->closed ? KC_EPIPE : KC_EAGAIN;
            if (rcl == KC_EAGAIN) ch->recv_eagain++; else ch->recv_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return rcl;
        }
    } else if (timeout_ms < 0) {
        if (ch->count == 0 && !ch->closed) {
            struct kc_waiter *w = kc_waiter_new_coro(KC_SELECT_CLAUSE_RECV);
            if (!w) { KC_MUTEX_UNLOCK(&ch->mu); return -ENOMEM; }
            kc_waiter_append(&ch->wq_recv_head, &ch->wq_recv_tail, w);
            KC_MUTEX_UNLOCK(&ch->mu);
            kcoro_yield();
            goto again_recv_ptr;
        }
    } else {
        if (ch->count == 0 && !ch->closed) {
            KC_MUTEX_UNLOCK(&ch->mu);
            if (kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; }
            kcoro_yield();
            goto again_recv_ptr;
        }
    }

    if (ch->count > 0) {
        size_t h = kc_ring_idx(ch, ch->head);
        struct kc_chan_ptrmsg tmp;
        memcpy(&tmp, ch->buf + (h * ch->elem_sz), sizeof(tmp));
        size_t n2 = ch->head + 1;
        ch->head = ch->mask ? (n2 & ch->mask) : (n2 % ch->capacity);
        ch->count--;
        *out_ptr = tmp.ptr; *out_len = tmp.len;
        kc_chan_update_recv_stats_len_locked(ch, tmp.len);
        KC_COND_SIGNAL(&ch->cv_send);
        wake_send = kc_chan_wake_send_locked(ch);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_chan_schedule_wake(wake_send);
        return 0;
    }

    if (ch->closed && ch->count == 0) { KC_MUTEX_UNLOCK(&ch->mu); return KC_EPIPE; }
    KC_MUTEX_UNLOCK(&ch->mu);
    return KC_EAGAIN;
}

int kc_chan_send_ptr_c(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms, const kc_cancel_t *cancel)
{
    if (!cancel) return kc_chan_send_ptr(ch, ptr, len, timeout_ms);
    if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
    const long SLICE_MS = KCORO_CANCEL_SLICE_MS;
    if (timeout_ms == 0) return kc_chan_send_ptr(ch, ptr, len, 0);
    if (timeout_ms < 0) {
        for (;;) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            int rc = kc_chan_send_ptr(ch, ptr, len, SLICE_MS);
            if (rc == 0) return 0;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
        }
    } else {
        long remain = timeout_ms;
        while (remain >= 0) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            long slice = (SLICE_MS < remain) ? SLICE_MS : remain;
            int rc = kc_chan_send_ptr(ch, ptr, len, slice);
            if (rc == 0) return 0;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
            remain -= slice;
            if (remain <= 0) return KC_ETIME;
        }
        return KC_ETIME;
    }
}

int kc_chan_recv_ptr_c(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms, const kc_cancel_t *cancel)
{
    if (!cancel) return kc_chan_recv_ptr(ch, out_ptr, out_len, timeout_ms);
    if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
    const long SLICE_MS = KCORO_CANCEL_SLICE_MS;
    if (timeout_ms == 0) return kc_chan_recv_ptr(ch, out_ptr, out_len, 0);
    if (timeout_ms < 0) {
        for (;;) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            int rc = kc_chan_recv_ptr(ch, out_ptr, out_len, SLICE_MS);
            if (rc == 0) return 0;
            if (rc == KC_EPIPE) return KC_EPIPE;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
        }
    } else {
        long remain = timeout_ms;
        while (remain >= 0) {
            if (kc_cancel_is_set(cancel)) return KC_ECANCELED;
            long slice = (SLICE_MS < remain) ? SLICE_MS : remain;
            int rc = kc_chan_recv_ptr(ch, out_ptr, out_len, slice);
            if (rc == 0) return 0;
            if (rc == KC_EPIPE) return KC_EPIPE;
            if (rc != KC_ETIME && rc != KC_EAGAIN) return rc;
            remain -= slice;
            if (remain <= 0) return KC_ETIME;
        }
        return KC_ETIME;
    }
}
