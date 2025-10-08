// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200809L

#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <stdatomic.h>

#include "kc_select_internal.h"
#include "../../include/kcoro_sched.h"
#include "../../include/kcoro_port.h"

static int kc_select_reserve(struct kc_select *sel, int additional)
{
    if (!sel || additional <= 0) return 0;
    int need = sel->count + additional;
    if (need <= sel->capacity) return 0;
    int new_cap = sel->capacity ? sel->capacity * 2 : 4;
    while (new_cap < need) new_cap *= 2;
    struct kc_select_clause_internal *nb = realloc(sel->clauses, (size_t)new_cap * sizeof(*nb));
    if (!nb) return -ENOMEM;
    sel->clauses = nb;
    sel->capacity = new_cap;
    return 0;
}

static long long kc_select_now_ns(void)
{
    struct timespec ts;
#ifdef CLOCK_MONOTONIC
    clock_gettime(CLOCK_MONOTONIC, &ts);
#else
    clock_gettime(CLOCK_REALTIME, &ts);
#endif
    return (long long)ts.tv_sec * 1000000000LL + (long long)ts.tv_nsec;
}

int kc_select_create(kc_select_t **out, const kc_cancel_t *cancel)
{
    if (!out) return -EINVAL;
    kc_select_t *sel = calloc(1, sizeof(*sel));
    if (!sel) return -ENOMEM;
    sel->clauses = NULL;
    sel->capacity = 0;
    sel->count = 0;
    sel->cancel = cancel;
    sel->waiter = NULL;
    atomic_store(&sel->state, KC_SELECT_REG);
    atomic_store(&sel->winner_index, -1);
    atomic_store(&sel->result, KC_EAGAIN);
    *out = sel;
    return 0;
}

void kc_select_destroy(kc_select_t *sel)
{
    if (!sel) return;
    free(sel->clauses);
    free(sel);
}

void kc_select_reset(kc_select_t *sel)
{
    if (!sel) return;
    sel->count = 0;
}

void kc_select_reset_state(kc_select_t *sel)
{
    if (!sel) return;
    sel->waiter = NULL;
    atomic_store(&sel->state, KC_SELECT_REG);
    atomic_store(&sel->winner_index, -1);
    atomic_store(&sel->result, KC_EAGAIN);
}

void kc_select_set_waiter(kc_select_t *sel, kcoro_t *co)
{
    if (!sel) return;
    sel->waiter = co;
}

int kc_select_has_waiter(const kc_select_t *sel)
{
    return sel && sel->waiter != NULL;
}

int kc_select_try_complete(kc_select_t *sel, int clause_index, int result)
{
    if (!sel) return 0;
    int expected = KC_SELECT_REG;
    if (atomic_compare_exchange_strong(&sel->state, &expected, KC_SELECT_WIN)) {
        atomic_store(&sel->winner_index, clause_index);
        atomic_store(&sel->result, result);
        /* NOTE: Do NOT unpark here. Caller (channel) will unpark only if the
         * waiter coroutine is actually parked. Immediate completion paths
         * during registration run in the context of the waiter and must NOT
         * enqueue it while it is running (would create duplicate ready queue
         * entries and potential concurrent resumes).
         */
        return 1;
    }
    return 0;
}

void kc_select_get_result(const kc_select_t *sel, int *clause_index, int *result)
{
    if (!sel) return;
    if (clause_index) *clause_index = atomic_load(&((kc_select_t*)sel)->winner_index);
    if (result) *result = atomic_load(&((kc_select_t*)sel)->result);
}

void* kc_select_recv_buffer(kc_select_t *sel, int clause_index)
{
    if (!sel || clause_index < 0 || clause_index >= sel->count) return NULL;
    return sel->clauses[clause_index].data.recv_buf;
}

const void* kc_select_send_buffer(kc_select_t *sel, int clause_index)
{
    if (!sel || clause_index < 0 || clause_index >= sel->count) return NULL;
    return sel->clauses[clause_index].data.send_buf;
}

kcoro_t* kc_select_waiter(const kc_select_t *sel)
{
    return sel ? sel->waiter : NULL;
}

int kc_select_is_completed(const kc_select_t *sel)
{
    if (!sel) return 0;
    int st = atomic_load(&((kc_select_t*)sel)->state);
    return st != KC_SELECT_REG;
}

int kc_select_add_recv(kc_select_t *sel, kc_chan_t *chan, void *out)
{
    if (!sel || !chan || !out) return -EINVAL;
    int rc = kc_select_reserve(sel, 1);
    if (rc != 0) return rc;
    sel->clauses[sel->count].kind = KC_SELECT_CLAUSE_RECV;
    sel->clauses[sel->count].chan = chan;
    sel->clauses[sel->count].data.recv_buf = out;
    sel->count++;
    return 0;
}

int kc_select_add_send(kc_select_t *sel, kc_chan_t *chan, const void *msg)
{
    if (!sel || !chan || !msg) return -EINVAL;
    int rc = kc_select_reserve(sel, 1);
    if (rc != 0) return rc;
    sel->clauses[sel->count].kind = KC_SELECT_CLAUSE_SEND;
    sel->clauses[sel->count].chan = chan;
    sel->clauses[sel->count].data.send_buf = msg;
    sel->count++;
    return 0;
}

static void kc_select_cancel_all(kc_select_t *sel)
{
    if (!sel) return;
    for (int i = 0; i < sel->count; ++i) {
        kc_chan_select_cancel(sel->clauses[i].chan, sel, i, sel->clauses[i].kind);
    }
}

int kc_select_wait(kc_select_t *sel, long timeout_ms, int *selected_index, int *op_result)
{
    if (!sel) return -EINVAL;
    if (sel->count == 0) return -EINVAL;

    /* Fast probe */
    for (int i = 0; i < sel->count; ++i) {
        struct kc_select_clause_internal *cl = &sel->clauses[i];
        int rc = (cl->kind == KC_SELECT_CLAUSE_RECV)
            ? kc_chan_recv(cl->chan, cl->data.recv_buf, 0)
            : kc_chan_send(cl->chan, cl->data.send_buf, 0);
        if (rc != KC_EAGAIN) {
            if (selected_index) *selected_index = i;
            if (op_result) *op_result = rc;
            return rc;
        }
    }

    if (timeout_ms == 0) {
        if (op_result) *op_result = KC_EAGAIN;
        return KC_EAGAIN;
    }

    kc_select_reset_state(sel);
    kcoro_t *waiter = kcoro_current();
    if (!waiter) return -EINVAL;
    kc_select_set_waiter(sel, waiter);

    for (int i = 0; i < sel->count; ++i) {
        struct kc_select_clause_internal *cl = &sel->clauses[i];
        int rc = (cl->kind == KC_SELECT_CLAUSE_RECV)
            ? kc_chan_select_register_recv(cl->chan, sel, i)
            : kc_chan_select_register_send(cl->chan, sel, i);
        if (rc != KC_EAGAIN) {
            kc_select_try_complete(sel, i, rc);
            break;
        }
    }

    int st = atomic_load(&sel->state);
    int infinite = (timeout_ms < 0) && (sel->cancel == NULL);
    long long deadline_ns = -1;
    if (timeout_ms > 0) deadline_ns = kc_select_now_ns() + (long long)timeout_ms * 1000000LL;

    if (st == KC_SELECT_REG) {
        if (infinite) {
            kcoro_park();
        } else {
            /* Fallback legacy yield loop for timeout/cancel cases */
            for (;;) {
                st = atomic_load(&sel->state);
                if (st != KC_SELECT_REG) break;
                if (sel->cancel && kc_cancel_is_set(sel->cancel)) {
                    int expected = KC_SELECT_REG;
                    if (atomic_compare_exchange_strong(&sel->state, &expected, KC_SELECT_CANCELED)) {
                        atomic_store(&sel->winner_index, -1);
                        atomic_store(&sel->result, KC_ECANCELED);
                    }
                    break;
                }
                if (timeout_ms > 0) {
                    long long now = kc_select_now_ns();
                    if (now >= deadline_ns) {
                        int expected2 = KC_SELECT_REG;
                        if (atomic_compare_exchange_strong(&sel->state, &expected2, KC_SELECT_TIMED_OUT)) {
                            atomic_store(&sel->winner_index, -1);
                            atomic_store(&sel->result, KC_ETIME);
                        }
                        break;
                    }
                }
                kcoro_yield();
            }
        }
    }

    /* Read result */
    int final_result = atomic_load(&sel->result);
    int win_idx = atomic_load(&sel->winner_index);
    if (selected_index) *selected_index = win_idx;
    if (op_result) *op_result = final_result;

    kc_select_cancel_all(sel); /* remove any outstanding registrations */
    /* Do not reset state here; leave terminal state until reuse or destroy to avoid races */
    sel->waiter = NULL;
    return final_result;
}
