// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200809L

#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <assert.h>
#include <stdio.h>
#include <time.h>
#include <sched.h>

#include "../../include/kcoro.h"
#include "../../include/kcoro_core.h"
#include "../../include/kcoro_sched.h"
#include "../../include/kcoro_token_kernel.h"
#include "../../include/kcoro_config.h"
#include "../../include/kcoro_config_runtime.h"
#include "../../include/kcoro_zcopy.h"
#include "kc_chan_internal.h"
#include "kc_select_internal.h"

/* ------------------------------------------------------------------------- */
/* Metrics helpers (unchanged from legacy path). */

static void kc_chan_note_op_locked(struct kc_chan *ch, int is_send, size_t len)
{
    if (is_send) {
        ch->total_sends++;
        ch->total_bytes_sent += len;
    } else {
        ch->total_recvs++;
        ch->total_bytes_recv += len;
    }
    long now = kc_now_ns();
    if (ch->first_op_time_ns == 0) ch->first_op_time_ns = now;
    ch->last_op_time_ns = now;
}

static int kc_wait_for_token_payload(kc_payload *ack)
{
    if (!ack) return -EINVAL;
    for (;;) {
        int rc = kc_token_kernel_consume_payload(ack);
        if (rc != KC_EAGAIN) {
            return rc;
        }
        sched_yield();
    }
}

unsigned kc_chan_len(kc_chan_t *c)
{
    if (!c) return 0;
    struct kc_chan *ch = (struct kc_chan*)c;
    KC_MUTEX_LOCK(&ch->mu);
    unsigned len = ch->count;
    KC_MUTEX_UNLOCK(&ch->mu);
    return len;
}

/* ------------------------------------------------------------------------- */
/* Pending queue helpers */

static void pending_send_enqueue(struct kc_chan *ch, struct kc_pending_send *node)
{
    node->next = NULL;
    if (ch->token_send_tail) {
        ch->token_send_tail->next = node;
    } else {
        ch->token_send_head = node;
    }
    ch->token_send_tail = node;
}

static struct kc_pending_send *pending_send_dequeue(struct kc_chan *ch)
{
    struct kc_pending_send *node = ch->token_send_head;
    if (!node) return NULL;
    ch->token_send_head = node->next;
    if (!ch->token_send_head) ch->token_send_tail = NULL;
    node->next = NULL;
    return node;
}

static void pending_recv_enqueue(struct kc_chan *ch, struct kc_pending_recv *node)
{
    node->next = NULL;
    if (ch->token_recv_tail) {
        ch->token_recv_tail->next = node;
    } else {
        ch->token_recv_head = node;
    }
    ch->token_recv_tail = node;
}

static struct kc_pending_recv *pending_recv_dequeue(struct kc_chan *ch)
{
    struct kc_pending_recv *node = ch->token_recv_head;
    if (!node) return NULL;
    ch->token_recv_head = node->next;
    if (!ch->token_recv_head) ch->token_recv_tail = NULL;
    node->next = NULL;
    return node;
}

static void pending_send_remove_select(struct kc_chan *ch, kc_select_t *sel, int clause)
{
    struct kc_pending_send **cur = &ch->token_send_head;
    struct kc_pending_send *tail = ch->token_send_tail;
    while (*cur) {
        struct kc_pending_send *node = *cur;
        if (node->role == KC_PENDING_ROLE_SELECT && node->sel == sel && node->clause_index == clause) {
            if (node->ticket.id) kc_token_kernel_cancel(node->ticket, KC_ECANCELED);
            if (node->desc_id) kc_desc_release(node->desc_id);
            *cur = node->next;
            if (tail == node) ch->token_send_tail = NULL;
            free(node);
            return;
        }
        cur = &node->next;
    }
}

static void pending_recv_remove_select(struct kc_chan *ch, kc_select_t *sel, int clause)
{
    struct kc_pending_recv **cur = &ch->token_recv_head;
    struct kc_pending_recv *tail = ch->token_recv_tail;
    while (*cur) {
        struct kc_pending_recv *node = *cur;
        if (node->role == KC_PENDING_ROLE_SELECT && node->sel == sel && node->clause_index == clause) {
            if (node->ticket.id) kc_token_kernel_cancel(node->ticket, KC_ECANCELED);
            if (node->desc_id) kc_desc_release(node->desc_id);
            *cur = node->next;
            if (tail == node) ch->token_recv_tail = NULL;
            free(node);
            return;
        }
        cur = &node->next;
    }
}

static size_t kc_chan_ring_index(const struct kc_chan *ch, size_t idx)
{
    return ch->mask ? (idx & ch->mask) : (idx % (ch->capacity ? ch->capacity : 1));
}

static int kc_chan_expand_ring(struct kc_chan *ch)
{
    size_t newcap = ch->capacity ? ch->capacity * 2 : KCORO_UNLIMITED_INIT_CAP;
    if (newcap == 0) newcap = KCORO_UNLIMITED_INIT_CAP;
    kc_desc_id *newring = calloc(newcap, sizeof(kc_desc_id));
    if (!newring) return -ENOMEM;
    for (size_t i = 0; i < ch->count; ++i) {
        size_t old_idx = kc_chan_ring_index(ch, ch->head + i);
        newring[i] = ch->ring_descs ? ch->ring_descs[old_idx] : 0;
    }
    free(ch->ring_descs);
    ch->ring_descs = newring;
    ch->capacity = newcap;
    ch->mask = (newcap & (newcap - 1)) == 0 ? newcap - 1 : 0;
    ch->head = 0;
    ch->tail = ch->count;
    return 0;
}



/* ------------------------------------------------------------------------- */
/* Wake helpers */

struct kc_wake {
    kcoro_t     *co;
    kc_select_t *sel;
};

static void kc_schedule_wake(struct kc_wake wake)
{
    if (!wake.co) return;
    kcoro_t *co = wake.co;
    if (kcoro_is_parked(co)) kcoro_unpark(co);
    kc_sched_t *sched = kc_sched_current();
    if (!sched) sched = kc_sched_default();
    if (sched) kc_sched_enqueue_ready(sched, co);
    kcoro_release(co);
}

static void complete_select(kc_select_t *sel, int clause, int result)
{
    if (!sel) return;
    if (kc_select_try_complete(sel, clause, result)) {
        kcoro_t *co = kc_select_waiter(sel);
        if (co) {
            kcoro_retain(co);
            kc_schedule_wake((struct kc_wake){ .co = co, .sel = sel });
        }
    }
}

static kc_desc_id kc_chan_create_desc(struct kc_chan *ch, const void *ptr, size_t len)
{
    if (ch->ptr_mode)
        return kc_desc_make_alias((void*)ptr, len);
    return kc_desc_make_copy(ptr, len);
}
static size_t kc_chan_copy_bytes(void *dst, const kc_payload *payload, size_t elem_sz)
{
    size_t copy_len = payload->len < elem_sz ? payload->len : elem_sz;
    if (dst && payload->ptr && copy_len) memcpy(dst, payload->ptr, copy_len);
    else if (dst && copy_len < elem_sz) memset((char*)dst + copy_len, 0, elem_sz - copy_len);
    return copy_len;
}


/* ------------------------------------------------------------------------- */
/* Channel lifecycle */

int kc_chan_make(kc_chan_t **out, int kind, size_t elem_sz, size_t capacity)
{
    if (!out || elem_sz == 0) return -EINVAL;
    struct kc_chan *ch = calloc(1, sizeof(*ch));
    if (!ch) return -ENOMEM;
    KC_MUTEX_INIT(&ch->mu);
    KC_COND_INIT(&ch->cv_send);
    KC_COND_INIT(&ch->cv_recv);
    ch->kind = kind;
    ch->elem_sz = elem_sz;
    if (kind == KC_RENDEZVOUS || kind == KC_CONFLATED) {
        ch->capacity = (kind == KC_CONFLATED) ? 1 : 0;
        ch->ring_descs = NULL;
    } else {
        ch->capacity = capacity ? capacity : 64;
        ch->mask = (ch->capacity & (ch->capacity - 1)) == 0 ? ch->capacity - 1 : 0;
        ch->ring_descs = calloc(ch->capacity, sizeof(kc_desc_id));
        if (!ch->ring_descs) { free(ch); return -ENOMEM; }
    }
    ch->emit_check_mask = 0x3FFUL;
    *out = ch;
    kc_desc_global_init();
    return 0;
}

void kc_chan_destroy(kc_chan_t *c)
{
    if (!c) return;
    struct kc_chan *ch = (struct kc_chan*)c;
    kc_chan_close(c);
    if (ch->ring_descs) {
        for (size_t i = 0; i < ch->capacity; ++i) {
            if (ch->ring_descs[i]) kc_desc_release(ch->ring_descs[i]);
        }
        free(ch->ring_descs);
    }
    KC_MUTEX_DESTROY(&ch->mu);
    KC_COND_DESTROY(&ch->cv_send);
    KC_COND_DESTROY(&ch->cv_recv);
    free(ch);
}

void kc_chan_close(kc_chan_t *c)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    KC_MUTEX_LOCK(&ch->mu);
    if (ch->closed) {
        KC_MUTEX_UNLOCK(&ch->mu);
        return;
    }
    ch->closed = 1;
    struct kc_pending_send *ps = ch->token_send_head;
    struct kc_pending_recv *pr = ch->token_recv_head;
    ch->token_send_head = ch->token_send_tail = NULL;
    ch->token_recv_head = ch->token_recv_tail = NULL;
    KC_MUTEX_UNLOCK(&ch->mu);

    while (ps) {
        struct kc_pending_send *next = ps->next;
        if (ps->role == KC_PENDING_ROLE_CORO && ps->ticket.id) {
            kc_token_kernel_cancel(ps->ticket, KC_EPIPE);
        } else if (ps->role == KC_PENDING_ROLE_SELECT) {
            complete_select(ps->sel, ps->clause_index, KC_EPIPE);
        }
        if (ps->desc_id) kc_desc_release(ps->desc_id);
        free(ps);
        ps = next;
    }
    while (pr) {
        struct kc_pending_recv *next = pr->next;
        if (pr->role == KC_PENDING_ROLE_CORO && pr->ticket.id) {
            kc_token_kernel_cancel(pr->ticket, KC_EPIPE);
        } else if (pr->role == KC_PENDING_ROLE_SELECT) {
            complete_select(pr->sel, pr->clause_index, KC_EPIPE);
        }
        if (pr->desc_id) kc_desc_release(pr->desc_id);
        free(pr);
        pr = next;
    }
    if (ch->rv_slot_desc) {
        kc_desc_release(ch->rv_slot_desc);
        ch->rv_slot_desc = 0;
    }
}

/* ------------------------------------------------------------------------- */
/* Rendezvous pointer helpers */

static void fulfill_coroutine_send(struct kc_pending_send *node, kc_desc_id desc)
{
    kc_payload payload = {0};
    if (kc_desc_payload(desc, &payload) != 0) {
        payload.status = KC_EPIPE;
        payload.ptr = NULL;
        payload.len = 0;
        payload.desc_id = 0;
        kc_token_kernel_callback(node->ticket, payload);
        kc_desc_release(desc);
        free(node);
        return;
    }
    kc_desc_retain(desc);
    payload.desc_id = desc;
    kc_token_kernel_callback(node->ticket, payload);
    kc_desc_release(desc);
    free(node);
}

static void fulfill_coroutine_recv(struct kc_pending_recv *node, kc_desc_id desc)
{
    kc_payload payload = {0};
    if (kc_desc_payload(desc, &payload) != 0) {
        payload.status = KC_EPIPE;
        payload.ptr = NULL;
        payload.len = 0;
        payload.desc_id = 0;
        kc_token_kernel_callback(node->ticket, payload);
        kc_desc_release(desc);
        free(node);
        return;
    }
    kc_desc_retain(desc);
    payload.desc_id = desc;
    kc_token_kernel_callback(node->ticket, payload);
    kc_desc_release(desc);
    free(node);
}

static void fulfill_select_send(struct kc_chan *ch, struct kc_pending_send *node, kc_desc_id desc)
{
    (void)ch;
    kc_payload payload = {0};
    int rc = kc_desc_payload(desc, &payload);
    struct kc_chan_ptrmsg msg = { .ptr = payload.ptr, .len = payload.len };
    void *dst = kc_select_recv_buffer(node->sel, node->clause_index);
    if (rc == 0 && dst) memcpy(dst, &msg, sizeof(msg));
    complete_select(node->sel, node->clause_index, (rc == 0 && dst) ? 0 : KC_ECANCELED);
    kc_desc_release(desc);
    free(node);
}

static void fulfill_select_recv(struct kc_chan *ch, struct kc_pending_recv *node, kc_desc_id desc)
{
    (void)ch;
    kc_payload payload = {0};
    int rc = kc_desc_payload(desc, &payload);
    struct kc_chan_ptrmsg msg = { .ptr = payload.ptr, .len = payload.len };
    void *dst = kc_select_recv_buffer(node->sel, node->clause_index);
    if (rc == 0 && dst) memcpy(dst, &msg, sizeof(msg));
    complete_select(node->sel, node->clause_index, (rc == 0 && dst) ? 0 : KC_ECANCELED);
    kc_desc_release(desc);
    free(node);
}

/* ------------------------------------------------------------------------- */
/* Pointer send/recv helpers */

static int kc_chan_send_ptr_rendezvous(struct kc_chan *ch, void *ptr, size_t len, long timeout_ms)
{
    kc_desc_id desc = 0;
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->closed) {
            ch->send_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            if (desc) kc_desc_release(desc);
            return KC_EPIPE;
        }
        struct kc_pending_recv *pending = pending_recv_dequeue(ch);
        if (pending) {
            if (!desc) {
                desc = kc_chan_create_desc(ch, ptr, len);
                if (!desc) {
                    KC_MUTEX_UNLOCK(&ch->mu);
                    free(pending);
                    return -ENOMEM;
                }
            }
            kc_chan_note_op_locked(ch, 1, len);
            KC_MUTEX_UNLOCK(&ch->mu);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                fulfill_coroutine_recv(pending, desc);
            } else {
                fulfill_select_recv(ch, pending, desc);
            }
            kc_desc_release(desc);
            return 0;
        }
        if (timeout_ms == 0) {
            ch->send_eagain++;
            fprintf(stderr, "[kc_chan][send_ptr_rv] timeout==0 returning EAGAIN\n");
            KC_MUTEX_UNLOCK(&ch->mu);
            if (desc) kc_desc_release(desc);
            return KC_EAGAIN;
        }
        if (timeout_ms > 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            if (kc_now_ns() >= deadline_ns) {
                ch->send_etime++;
                if (desc) kc_desc_release(desc);
                return KC_ETIME;
            }
            kcoro_yield();
            continue;
        }
        if (!desc) {
            desc = kc_chan_create_desc(ch, ptr, len);
            if (!desc) {
                KC_MUTEX_UNLOCK(&ch->mu);
                return -ENOMEM;
            }
        }
        struct kc_pending_send *node = calloc(1, sizeof(*node));
        if (!node) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return -ENOMEM;
        }
        kc_payload payload = {0};
        if (kc_desc_payload(desc, &payload) != 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            free(node);
            return KC_EPIPE;
        }
        kc_ticket ticket = kc_token_kernel_publish_send(ch, payload.ptr, payload.len, NULL);
        if (ticket.id == 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            free(node);
            return KC_EAGAIN;
        }
        node->kind = KC_PENDING_KIND_PTR;
        node->role = KC_PENDING_ROLE_CORO;
        node->ticket = ticket;
        node->desc_id = desc;
        pending_send_enqueue(ch, node);
        desc = 0;
        KC_MUTEX_UNLOCK(&ch->mu);

        kcoro_park();
        kc_payload ack = {0};
        int rc = kc_wait_for_token_payload(&ack);
        if (ack.desc_id) kc_desc_release(ack.desc_id);
        if (rc < 0) return rc;
        if (ack.status < 0) return ack.status;
        return 0;
    }
}

static int kc_chan_send_ptr_non_rendezvous(struct kc_chan *ch, void *ptr, size_t len, long timeout_ms)
{
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->closed) {
            ch->send_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return KC_EPIPE;
        }
        struct kc_pending_recv *pending = pending_recv_dequeue(ch);
        if (pending) {
            kc_desc_id desc = kc_chan_create_desc(ch, ptr, len);
            if (!desc) {
                KC_MUTEX_UNLOCK(&ch->mu);
                free(pending);
                return -ENOMEM;
            }
            kc_chan_note_op_locked(ch, 1, len);
            KC_MUTEX_UNLOCK(&ch->mu);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                fulfill_coroutine_recv(pending, desc);
            } else {
                fulfill_select_recv(ch, pending, desc);
            }
            kc_desc_release(desc);
            return 0;
        }
        if (ch->kind == KC_CONFLATED) {
            kc_desc_id desc = kc_chan_create_desc(ch, ptr, len);
            if (!desc) {
                KC_MUTEX_UNLOCK(&ch->mu);
                return -ENOMEM;
            }
            if (ch->rv_slot_desc) kc_desc_release(ch->rv_slot_desc);
            ch->rv_slot_desc = desc;
            kc_chan_note_op_locked(ch, 1, len);
            KC_MUTEX_UNLOCK(&ch->mu);
            return 0;
        }
        if (ch->count == ch->capacity) {
            if (ch->kind == KC_UNLIMITED) {
                int grow_rc = kc_chan_expand_ring(ch);
                if (grow_rc != 0) {
                    KC_MUTEX_UNLOCK(&ch->mu);
                    return grow_rc;
                }
            } else {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (timeout_ms == 0) { ch->send_eagain++; return KC_EAGAIN; }
                if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) { ch->send_etime++; return KC_ETIME; }
                kcoro_yield();
                continue;
            }
        }
        if (!ch->ring_descs) {
            KC_MUTEX_UNLOCK(&ch->mu);
            return -EINVAL;
        }
        kc_desc_id desc = kc_chan_create_desc(ch, ptr, len);
        if (!desc) {
            KC_MUTEX_UNLOCK(&ch->mu);
            return -ENOMEM;
        }
        size_t idx = kc_chan_ring_index(ch, ch->tail);
        ch->ring_descs[idx] = desc;
        size_t next = ch->tail + 1;
        ch->tail = ch->mask ? (next & ch->mask) : (next % ch->capacity);
        ch->count++;
        kc_chan_note_op_locked(ch, 1, len);
        KC_MUTEX_UNLOCK(&ch->mu);
        return 0;
    }
}

int kc_chan_send_ptr(kc_chan_t *c, void *ptr, size_t len, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !ptr || len == 0) return -EINVAL;
    if (!ch->ptr_mode) return -EINVAL;

    if (ch->kind == KC_RENDEZVOUS)
        return kc_chan_send_ptr_rendezvous(ch, ptr, len, timeout_ms);
    return kc_chan_send_ptr_non_rendezvous(ch, ptr, len, timeout_ms);
}

static int kc_chan_recv_ptr_rendezvous(struct kc_chan *ch, void **out_ptr, size_t *out_len, long timeout_ms)
{
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        struct kc_pending_send *pending = pending_send_dequeue(ch);
        if (pending) {
            kc_payload payload = {0};
            int rc = kc_desc_payload(pending->desc_id, &payload);
            if (rc != 0) {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (pending->role == KC_PENDING_ROLE_CORO) {
                    kc_token_kernel_callback(pending->ticket, (kc_payload){ .ptr = NULL, .len = 0, .status = KC_EPIPE, .desc_id = 0 });
                } else {
                    complete_select(pending->sel, pending->clause_index, KC_EPIPE);
                }
                kc_desc_release(pending->desc_id);
                free(pending);
                return KC_EPIPE;
            }
            kc_chan_note_op_locked(ch, 0, payload.len);
            kc_chan_note_op_locked(ch, 1, payload.len);
            *out_ptr = payload.ptr;
            *out_len = payload.len;
            KC_MUTEX_UNLOCK(&ch->mu);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                fulfill_coroutine_send(pending, pending->desc_id);
            } else {
                fulfill_select_send(ch, pending, pending->desc_id);
            }
            return 0;
        }
        if (ch->closed) {
            ch->recv_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return KC_EPIPE;
        }
        if (timeout_ms == 0) {
            ch->recv_eagain++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return KC_EAGAIN;
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) {
            ch->recv_etime++;
            return KC_ETIME;
        }
        kcoro_yield();
    }
}

static int kc_chan_recv_ptr_non_rendezvous(struct kc_chan *ch, void **out_ptr, size_t *out_len, long timeout_ms)
{
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->kind == KC_CONFLATED) {
            if (ch->rv_slot_desc) {
                kc_desc_id desc = ch->rv_slot_desc;
                ch->rv_slot_desc = 0;
                kc_payload payload = {0};
                int rc = kc_desc_payload(desc, &payload);
                if (rc != 0) {
                    KC_MUTEX_UNLOCK(&ch->mu);
                    kc_desc_release(desc);
                    return KC_EPIPE;
                }
                kc_chan_note_op_locked(ch, 0, payload.len);
                KC_MUTEX_UNLOCK(&ch->mu);
                *out_ptr = payload.ptr;
                *out_len = payload.len;
                kc_desc_release(desc);
                return 0;
            }
            if (ch->closed) {
                ch->recv_epipe++;
                KC_MUTEX_UNLOCK(&ch->mu);
                return KC_EPIPE;
            }
            KC_MUTEX_UNLOCK(&ch->mu);
            if (timeout_ms == 0) { ch->recv_eagain++; return KC_EAGAIN; }
            if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; }
            kcoro_yield();
            continue;
        }

        if (ch->count > 0) {
            size_t idx = kc_chan_ring_index(ch, ch->head);
            kc_desc_id desc = ch->ring_descs[idx];
            ch->ring_descs[idx] = 0;
            size_t next = ch->head + 1;
            ch->head = ch->mask ? (next & ch->mask) : (next % ch->capacity);
            ch->count--;
            kc_payload payload = {0};
            int rc = kc_desc_payload(desc, &payload);
            if (rc != 0) {
                KC_MUTEX_UNLOCK(&ch->mu);
                kc_desc_release(desc);
                return KC_EPIPE;
            }
            kc_chan_note_op_locked(ch, 0, payload.len);
            KC_MUTEX_UNLOCK(&ch->mu);
            *out_ptr = payload.ptr;
            *out_len = payload.len;
            kc_desc_release(desc);
            return 0;
        }

        if (ch->closed) {
            ch->recv_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return KC_EPIPE;
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        if (timeout_ms == 0) { ch->recv_eagain++; return KC_EAGAIN; }
        if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; }
        kcoro_yield();
    }
}

int kc_chan_recv_ptr(kc_chan_t *c, void **out_ptr, size_t *out_len, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !out_ptr || !out_len) return -EINVAL;
    if (!ch->ptr_mode) return -EINVAL;

    if (ch->kind == KC_RENDEZVOUS)
        return kc_chan_recv_ptr_rendezvous(ch, out_ptr, out_len, timeout_ms);
    return kc_chan_recv_ptr_non_rendezvous(ch, out_ptr, out_len, timeout_ms);
}

static int kc_chan_send_bytes_rendezvous(struct kc_chan *ch, const void *msg, long timeout_ms)
{
    kc_desc_id desc = kc_chan_create_desc(ch, msg, ch->elem_sz);
    if (!desc) return -ENOMEM;
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->closed) {
            ch->send_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return KC_EPIPE;
        }
        struct kc_pending_recv *pending = pending_recv_dequeue(ch);
        if (pending) {
            kc_chan_note_op_locked(ch, 1, ch->elem_sz);
            KC_MUTEX_UNLOCK(&ch->mu);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                fulfill_coroutine_recv(pending, desc);
            } else {
                fulfill_select_recv(ch, pending, desc);
            }
            kc_desc_release(desc);
            return 0;
        }
        if (timeout_ms == 0) {
            ch->send_eagain++;
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return KC_EAGAIN;
        }
        if (timeout_ms > 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            if (kc_now_ns() >= deadline_ns) {
                ch->send_etime++;
                kc_desc_release(desc);
                return KC_ETIME;
            }
            kcoro_yield();
            continue;
        }
        kc_payload payload = {0};
        if (kc_desc_payload(desc, &payload) != 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return KC_EPIPE;
        }
        struct kc_pending_send *node = calloc(1, sizeof(*node));
        if (!node) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return -ENOMEM;
        }
        kc_ticket ticket = kc_token_kernel_publish_send(ch, payload.ptr, payload.len, NULL);
        if (ticket.id == 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            free(node);
            return KC_EAGAIN;
        }
        node->kind = KC_PENDING_KIND_BYTES;
        node->role = KC_PENDING_ROLE_CORO;
        node->ticket = ticket;
        node->desc_id = desc;
        kc_pending_send_append(&ch->token_send_head, &ch->token_send_tail, node);
        desc = 0;
        KC_MUTEX_UNLOCK(&ch->mu);

        kcoro_park();
        kc_payload ack = {0};
        int rc = kc_wait_for_token_payload(&ack);
        if (ack.desc_id) kc_desc_release(ack.desc_id);
        if (rc < 0) return rc;
        if (ack.status < 0) return ack.status;
        return 0;
    }
}

static int kc_chan_send_bytes_non_rendezvous(struct kc_chan *ch, const void *msg, long timeout_ms)
{
    kc_desc_id desc = kc_chan_create_desc(ch, msg, ch->elem_sz);
    if (!desc) return -ENOMEM;
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->closed) {
            ch->send_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return KC_EPIPE;
        }
        struct kc_pending_recv *pending = pending_recv_dequeue(ch);
        if (pending) {
            kc_chan_note_op_locked(ch, 1, ch->elem_sz);
            KC_MUTEX_UNLOCK(&ch->mu);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                fulfill_coroutine_recv(pending, desc);
            } else {
                fulfill_select_recv(ch, pending, desc);
            }
            kc_desc_release(desc);
            return 0;
        }
        if (ch->kind == KC_CONFLATED) {
            if (ch->rv_slot_desc) kc_desc_release(ch->rv_slot_desc);
            ch->rv_slot_desc = desc;
            kc_chan_note_op_locked(ch, 1, ch->elem_sz);
            KC_MUTEX_UNLOCK(&ch->mu);
            return 0;
        }
        if (ch->count == ch->capacity) {
            if (ch->kind == KC_UNLIMITED) {
                int grow_rc = kc_chan_expand_ring(ch);
                if (grow_rc != 0) {
                    KC_MUTEX_UNLOCK(&ch->mu);
                    kc_desc_release(desc);
                    return grow_rc;
                }
            } else {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (timeout_ms == 0) { ch->send_eagain++; kc_desc_release(desc); return KC_EAGAIN; }
                if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) { ch->send_etime++; kc_desc_release(desc); return KC_ETIME; }
                kcoro_yield();
                continue;
            }
        }
        size_t idx = kc_chan_ring_index(ch, ch->tail);
        ch->ring_descs[idx] = desc;
        size_t next = ch->tail + 1;
        ch->tail = ch->mask ? (next & ch->mask) : (next % ch->capacity);
        ch->count++;
        kc_chan_note_op_locked(ch, 1, ch->elem_sz);
        KC_MUTEX_UNLOCK(&ch->mu);
        return 0;
    }
}

static int kc_chan_recv_bytes_rendezvous(struct kc_chan *ch, void *out, long timeout_ms)
{
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        struct kc_pending_send *pending = pending_send_dequeue(ch);
        if (pending) {
            kc_payload payload = {0};
            int rc = kc_desc_payload(pending->desc_id, &payload);
            if (rc != 0) {
                KC_MUTEX_UNLOCK(&ch->mu);
                if (pending->role == KC_PENDING_ROLE_CORO) {
                    kc_token_kernel_callback(pending->ticket, (kc_payload){ .ptr = NULL, .len = 0, .status = KC_EPIPE, .desc_id = 0 });
                } else {
                    complete_select(pending->sel, pending->clause_index, KC_EPIPE);
                }
                kc_desc_release(pending->desc_id);
                free(pending);
                return KC_EPIPE;
            }
            kc_chan_note_op_locked(ch, 0, payload.len);
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_chan_copy_bytes(out, &payload, ch->elem_sz);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                fulfill_coroutine_send(pending, pending->desc_id);
            } else {
                fulfill_select_send(ch, pending, pending->desc_id);
            }
            return 0;
        }
        if (ch->closed) {
            ch->recv_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return KC_EPIPE;
        }
        if (timeout_ms == 0) {
            ch->recv_eagain++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return KC_EAGAIN;
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) {
            ch->recv_etime++;
            return KC_ETIME;
        }
        kcoro_yield();
    }
}

static int kc_chan_recv_bytes_non_rendezvous(struct kc_chan *ch, void *out, long timeout_ms)
{
    long deadline_ns = (timeout_ms > 0) ? kc_now_ns() + timeout_ms * 1000000L : 0;

    for (;;) {
        KC_MUTEX_LOCK(&ch->mu);
        if (ch->kind == KC_CONFLATED) {
            if (ch->rv_slot_desc) {
                kc_desc_id desc = ch->rv_slot_desc;
                ch->rv_slot_desc = 0;
                kc_payload payload = {0};
                int rc = kc_desc_payload(desc, &payload);
                if (rc != 0) {
                    KC_MUTEX_UNLOCK(&ch->mu);
                    kc_desc_release(desc);
                    return KC_EPIPE;
                }
                kc_chan_note_op_locked(ch, 0, payload.len);
                KC_MUTEX_UNLOCK(&ch->mu);
                kc_chan_copy_bytes(out, &payload, ch->elem_sz);
                kc_desc_release(desc);
                return 0;
            }
            if (ch->closed) {
                ch->recv_epipe++;
                KC_MUTEX_UNLOCK(&ch->mu);
                return KC_EPIPE;
            }
            KC_MUTEX_UNLOCK(&ch->mu);
            if (timeout_ms == 0) { ch->recv_eagain++; return KC_EAGAIN; }
            if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; }
            kcoro_yield();
            continue;
        }

        if (ch->count > 0) {
            size_t idx = kc_chan_ring_index(ch, ch->head);
            kc_desc_id desc = ch->ring_descs[idx];
            ch->ring_descs[idx] = 0;
            size_t next = ch->head + 1;
            ch->head = ch->mask ? (next & ch->mask) : (next % ch->capacity);
            ch->count--;
            kc_payload payload = {0};
            int rc = kc_desc_payload(desc, &payload);
            if (rc != 0) {
                KC_MUTEX_UNLOCK(&ch->mu);
                kc_desc_release(desc);
                return KC_EPIPE;
            }
            kc_chan_note_op_locked(ch, 0, payload.len);
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_chan_copy_bytes(out, &payload, ch->elem_sz);
            kc_desc_release(desc);
            return 0;
        }

        if (ch->closed) {
            ch->recv_epipe++;
            KC_MUTEX_UNLOCK(&ch->mu);
            return KC_EPIPE;
        }
        KC_MUTEX_UNLOCK(&ch->mu);
        if (timeout_ms == 0) { ch->recv_eagain++; return KC_EAGAIN; }
        if (timeout_ms > 0 && kc_now_ns() >= deadline_ns) { ch->recv_etime++; return KC_ETIME; }
        kcoro_yield();
    }
}
/* ------------------------------------------------------------------------- */
/* Generic send/recv stubs */

int kc_chan_send(kc_chan_t *ch, const void *msg, long timeout_ms)
{
    if (!ch || !msg) return -EINVAL;
    struct kc_chan *chan = (struct kc_chan*)ch;
    if (chan->ptr_mode) return -EINVAL;
    if (chan->kind == KC_RENDEZVOUS)
        return kc_chan_send_bytes_rendezvous(chan, msg, timeout_ms);
    return kc_chan_send_bytes_non_rendezvous(chan, msg, timeout_ms);
}

int kc_chan_recv(kc_chan_t *ch, void *out, long timeout_ms)
{
    if (!ch || !out) return -EINVAL;
    struct kc_chan *chan = (struct kc_chan*)ch;
    if (chan->ptr_mode) return -EINVAL;
    if (chan->kind == KC_RENDEZVOUS)
        return kc_chan_recv_bytes_rendezvous(chan, out, timeout_ms);
    return kc_chan_recv_bytes_non_rendezvous(chan, out, timeout_ms);
}

int kc_chan_send_c(kc_chan_t *ch, const void *msg, long timeout_ms, const kc_cancel_t *cancel)
{
    if (cancel && kc_cancel_is_set(cancel)) return KC_ECANCELED;
    return kc_chan_send(ch, msg, timeout_ms);
}

int kc_chan_recv_c(kc_chan_t *ch, void *out, long timeout_ms, const kc_cancel_t *cancel)
{
    if (cancel && kc_cancel_is_set(cancel)) return KC_ECANCELED;
    return kc_chan_recv(ch, out, timeout_ms);
}

int kc_chan_send_ptr_c(kc_chan_t *c, void *ptr, size_t len, long timeout_ms, const kc_cancel_t *cancel)
{
    if (cancel && kc_cancel_is_set(cancel)) return KC_ECANCELED;
    return kc_chan_send_ptr(c, ptr, len, timeout_ms);
}

int kc_chan_recv_ptr_c(kc_chan_t *c, void **out_ptr, size_t *out_len, long timeout_ms, const kc_cancel_t *cancel)
{
    if (cancel && kc_cancel_is_set(cancel)) return KC_ECANCELED;
    return kc_chan_recv_ptr(c, out_ptr, out_len, timeout_ms);
}

/* ------------------------------------------------------------------------- */
/* Select registration (rendezvous pointer only). */

int kc_chan_select_register_recv(kc_chan_t *c, kc_select_t *sel, int clause_index)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !sel) return -EINVAL;

    KC_MUTEX_LOCK(&ch->mu);
    if (ch->kind == KC_RENDEZVOUS) {
        struct kc_pending_send *pending = pending_send_dequeue(ch);
        if (pending) {
            kc_payload payload = {0};
            int rc = kc_desc_payload(pending->desc_id, &payload);
            KC_MUTEX_UNLOCK(&ch->mu);
            if (rc != 0) {
                if (pending->role == KC_PENDING_ROLE_CORO) {
                    kc_token_kernel_callback(pending->ticket, (kc_payload){ .ptr = NULL, .len = 0, .status = KC_EPIPE, .desc_id = 0 });
                } else {
                    complete_select(pending->sel, pending->clause_index, KC_EPIPE);
                }
                kc_desc_release(pending->desc_id);
                free(pending);
                return KC_EPIPE;
            }
            kc_chan_note_op_locked(ch, 0, payload.len);
            struct kc_chan_ptrmsg msg = { .ptr = payload.ptr, .len = payload.len };
            void *dst = kc_select_recv_buffer(sel, clause_index);
            if (dst) memcpy(dst, &msg, sizeof(msg));
            complete_select(sel, clause_index, dst ? 0 : KC_ECANCELED);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                fulfill_coroutine_send(pending, pending->desc_id);
            } else {
                fulfill_select_send(ch, pending, pending->desc_id);
            }
            return 0;
        }
        struct kc_pending_recv *node = calloc(1, sizeof(*node));
        if (!node) {
            KC_MUTEX_UNLOCK(&ch->mu);
            return -ENOMEM;
        }
        node->kind = KC_PENDING_KIND_PTR;
        node->role = KC_PENDING_ROLE_SELECT;
        node->sel = sel;
        node->clause_index = clause_index;
        node->desc_id = 0;
        pending_recv_enqueue(ch, node);
        KC_MUTEX_UNLOCK(&ch->mu);
        return KC_EAGAIN;
    }

    if (ch->kind == KC_CONFLATED && ch->rv_slot_desc) {
        kc_desc_id desc = ch->rv_slot_desc;
        ch->rv_slot_desc = 0;
        kc_payload payload = {0};
        int rc = kc_desc_payload(desc, &payload);
        if (rc != 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return KC_EPIPE;
        }
        kc_chan_note_op_locked(ch, 0, payload.len);
        void *dst = kc_select_recv_buffer(sel, clause_index);
        if (ch->ptr_mode) {
            struct kc_chan_ptrmsg msg = { .ptr = payload.ptr, .len = payload.len };
            if (dst) memcpy(dst, &msg, sizeof(msg));
        } else {
            if (dst) kc_chan_copy_bytes(dst, &payload, ch->elem_sz);
        }
        complete_select(sel, clause_index, dst ? 0 : KC_ECANCELED);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_desc_release(desc);
        return dst ? 0 : KC_ECANCELED;
    }

    if (ch->ring_descs && ch->count > 0) {
        size_t idx = kc_chan_ring_index(ch, ch->head);
        kc_desc_id desc = ch->ring_descs[idx];
        ch->ring_descs[idx] = 0;
        size_t next = ch->head + 1;
        ch->head = ch->mask ? (next & ch->mask) : (next % ch->capacity);
        ch->count--;
        kc_payload payload = {0};
        int rc = kc_desc_payload(desc, &payload);
        if (rc != 0) {
            KC_MUTEX_UNLOCK(&ch->mu);
            kc_desc_release(desc);
            return KC_EPIPE;
        }
        kc_chan_note_op_locked(ch, 0, payload.len);
        void *dst = kc_select_recv_buffer(sel, clause_index);
        if (ch->ptr_mode) {
            struct kc_chan_ptrmsg msg = { .ptr = payload.ptr, .len = payload.len };
            if (dst) memcpy(dst, &msg, sizeof(msg));
        } else {
            if (dst) kc_chan_copy_bytes(dst, &payload, ch->elem_sz);
        }
        complete_select(sel, clause_index, dst ? 0 : KC_ECANCELED);
        KC_MUTEX_UNLOCK(&ch->mu);
        kc_desc_release(desc);
        return dst ? 0 : KC_ECANCELED;
    }

    struct kc_pending_recv *node = calloc(1, sizeof(*node));
    if (!node) {
        KC_MUTEX_UNLOCK(&ch->mu);
        return -ENOMEM;
    }
    node->kind = KC_PENDING_KIND_PTR;
    node->role = KC_PENDING_ROLE_SELECT;
    node->sel = sel;
    node->clause_index = clause_index;
    node->desc_id = 0;
    pending_recv_enqueue(ch, node);
    KC_MUTEX_UNLOCK(&ch->mu);
    return KC_EAGAIN;
}


int kc_chan_select_register_send(kc_chan_t *c, kc_select_t *sel, int clause_index)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !sel) return -EINVAL;
    if (ch->kind != KC_RENDEZVOUS) {
        // TODO(arena): Align send-side select support with buffered/unlimited
        // queues after they adopt arena descriptors. Stub keeps the lab honest.
        return -ENOTSUP;
    }

    KC_MUTEX_LOCK(&ch->mu);
    struct kc_pending_recv *pending = pending_recv_dequeue(ch);
    if (pending) {
        const void *src = kc_select_send_buffer(sel, clause_index);
        if (!src) {
            KC_MUTEX_UNLOCK(&ch->mu);
            complete_select(sel, clause_index, KC_ECANCELED);
            if (pending->desc_id) kc_desc_release(pending->desc_id);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                kc_token_kernel_callback(pending->ticket, (kc_payload){ .ptr = NULL, .len = 0, .status = KC_ECANCELED, .desc_id = 0 });
            } else {
                complete_select(pending->sel, pending->clause_index, KC_ECANCELED);
            }
            free(pending);
            return KC_ECANCELED;
        }
        const struct kc_chan_ptrmsg *msg = src;
        kc_desc_id desc = kc_chan_create_desc(ch, msg->ptr, msg->len);
        if (!desc) {
            KC_MUTEX_UNLOCK(&ch->mu);
            complete_select(sel, clause_index, KC_EPIPE);
            if (pending->desc_id) kc_desc_release(pending->desc_id);
            if (pending->role == KC_PENDING_ROLE_CORO) {
                kc_token_kernel_callback(pending->ticket, (kc_payload){ .ptr = NULL, .len = 0, .status = KC_EPIPE, .desc_id = 0 });
            } else {
                complete_select(pending->sel, pending->clause_index, KC_EPIPE);
            }
            free(pending);
            return KC_EPIPE;
        }
        kc_chan_note_op_locked(ch, 1, msg->len);
        KC_MUTEX_UNLOCK(&ch->mu);
        if (pending->role == KC_PENDING_ROLE_CORO) {
            fulfill_coroutine_recv(pending, desc);
        } else {
            fulfill_select_recv(ch, pending, desc);
        }
        complete_select(sel, clause_index, 0);
        return 0;
    }
    struct kc_pending_send *node = calloc(1, sizeof(*node));
    if (!node) {
        KC_MUTEX_UNLOCK(&ch->mu);
        return -ENOMEM;
    }
    node->kind = KC_PENDING_KIND_PTR;
    node->role = KC_PENDING_ROLE_SELECT;
    node->sel = sel;
    node->clause_index = clause_index;
    const void *src = kc_select_send_buffer(sel, clause_index);
    if (src) {
        const struct kc_chan_ptrmsg *msg = src;
        node->desc_id = kc_chan_create_desc(ch, msg->ptr, msg->len);
        if (!node->desc_id) {
            KC_MUTEX_UNLOCK(&ch->mu);
            free(node);
            return -ENOMEM;
        }
    } else {
        node->desc_id = 0;
    }
    pending_send_enqueue(ch, node);
    KC_MUTEX_UNLOCK(&ch->mu);
    return KC_EAGAIN;
}

void kc_chan_select_cancel(kc_chan_t *c, kc_select_t *sel, int clause_index, enum kc_select_clause_kind kind)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !sel) return;
    KC_MUTEX_LOCK(&ch->mu);
    if (kind == KC_SELECT_CLAUSE_SEND) pending_send_remove_select(ch, sel, clause_index);
    else pending_recv_remove_select(ch, sel, clause_index);
    KC_MUTEX_UNLOCK(&ch->mu);
}

/* ------------------------------------------------------------------------- */
/* Pointer-channel creation */

int kc_chan_make_ptr(kc_chan_t **out, int kind, size_t capacity)
{
    int rc = kc_chan_make(out, kind, sizeof(struct kc_chan_ptrmsg), capacity);
    if (rc == 0) {
        struct kc_chan *ch = (struct kc_chan*)(*out);
        ch->ptr_mode = 1;
        ch->capabilities |= KC_CHAN_CAP_PTR;
    }
    return rc;
}

/* ------------------------------------------------------------------------- */
/* Stats and metrics stubs */

int kc_chan_get_stats(kc_chan_t *c, struct kc_chan_stats *out)
{
    if (!c || !out) return -EINVAL;
    // TODO(metrics): Rehydrate stats once the unified path updates counters for
    // every channel kind. Returning zeros prevents stale waiter-era data from
    // leaking into tools.
    memset(out, 0, sizeof(*out));
    return 0;
}

int kc_chan_snapshot(kc_chan_t *c, struct kc_chan_snapshot *out)
{
    if (!c || !out) return -EINVAL;
    struct kc_chan *ch = (struct kc_chan*)c;
    KC_MUTEX_LOCK(&ch->mu);
    memset(out, 0, sizeof(*out));
    out->chan = ch;
    out->kind = ch->kind;
    out->elem_sz = ch->elem_sz;
    out->capacity = ch->capacity;
    if (ch->kind == KC_CONFLATED) {
        out->count = ch->rv_slot_desc ? 1 : 0;
    } else {
        out->count = ch->count;
    }
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

    if (ch->first_op_time_ns && ch->last_op_time_ns >= ch->first_op_time_ns) {
        long duration_ns = ch->last_op_time_ns - ch->first_op_time_ns;
        out->duration_sec = (double)duration_ns / 1e9;
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

    out->delta_sends = curr->total_sends - prev->total_sends;
    out->delta_recvs = curr->total_recvs - prev->total_recvs;
    out->delta_bytes_sent = curr->total_bytes_sent - prev->total_bytes_sent;
    out->delta_bytes_recv = curr->total_bytes_recv - prev->total_bytes_recv;
    out->delta_send_eagain = curr->send_eagain - prev->send_eagain;
    out->delta_recv_eagain = curr->recv_eagain - prev->recv_eagain;
    out->delta_send_epipe = curr->send_epipe - prev->send_epipe;
    out->delta_recv_epipe = curr->recv_epipe - prev->recv_epipe;
    out->delta_rv_matches = curr->rv_matches - prev->rv_matches;
    out->delta_rv_cancels = curr->rv_cancels - prev->rv_cancels;
    out->delta_rv_zdesc_matches = curr->rv_zdesc_matches - prev->rv_zdesc_matches;

    double interval = 0.0;
    if (prev->duration_sec > 0.0 && curr->duration_sec >= prev->duration_sec) {
        interval = curr->duration_sec - prev->duration_sec;
    } else if (curr->duration_sec > 0.0) {
        interval = curr->duration_sec;
    }
    if (interval <= 0.0) interval = 1e-6; /* clamp to 1 microsecond */

    out->interval_sec = interval;
    double inv = 1.0 / interval;
    out->sends_per_sec = out->delta_sends * inv;
    out->recvs_per_sec = out->delta_recvs * inv;
    out->bytes_sent_per_sec = out->delta_bytes_sent * inv;
    out->bytes_recv_per_sec = out->delta_bytes_recv * inv;
    return 0;
}

int kc_chan_enable_metrics_pipe(kc_chan_t *ch, kc_chan_t **out_pipe, size_t capacity)
{
    (void)ch; (void)out_pipe; (void)capacity;
    // TODO(metrics): Restore metrics pipe emission once counters and arena stats
    // are accurate under the new token path.
    return -ENOTSUP;
}

int kc_chan_disable_metrics_pipe(kc_chan_t *ch)
{
    (void)ch;
    // TODO(metrics): Complement kc_chan_enable_metrics_pipe with a real tear-down
    // when metrics piping is reintroduced.
    return -ENOTSUP;
}

kc_chan_t *kc_chan_metrics_pipe(kc_chan_t *ch)
{
    (void)ch;
    return NULL;
}

/* ------------------------------------------------------------------------- */
/* Zero-copy stubs */

unsigned kc_chan_capabilities(kc_chan_t *ch)
{
    if (!ch) return 0;
    struct kc_chan *c = (struct kc_chan*)ch;
    return c->capabilities;
}

int kc_chan_enable_zero_copy(kc_chan_t *ch)
{
    if (!ch) return -EINVAL;
    kc_zcopy_backend_id id = kc_zcopy_resolve("zref");
    if (id < 0) return id;
    return kc_chan_enable_zero_copy_backend(ch, id, NULL);
}

int kc_chan_send_zref(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms)
{
    if (!ch || !ptr) return -EINVAL;
    kc_zdesc_t d = { .addr = ptr, .len = len };
    return kc_chan_send_desc(ch, &d, timeout_ms);
}

int kc_chan_send_zref_c(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms, const kc_cancel_t *cancel)
{
    if (cancel && kc_cancel_is_set(cancel)) return KC_ECANCELED;
    return kc_chan_send_zref(ch, ptr, len, timeout_ms);
}

int kc_chan_recv_zref(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms)
{
    if (!ch || !out_ptr || !out_len) return -EINVAL;
    kc_zdesc_t d = {0};
    int rc = kc_chan_recv_desc(ch, &d, timeout_ms);
    if (rc == 0) {
        *out_ptr = d.addr;
        *out_len = d.len;
    }
    return rc;
}

int kc_chan_recv_zref_c(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms, const kc_cancel_t *cancel)
{
    if (cancel && kc_cancel_is_set(cancel)) return KC_ECANCELED;
    return kc_chan_recv_zref(ch, out_ptr, out_len, timeout_ms);
}

int kc_chan_get_zstats(kc_chan_t *ch, struct kc_chan_zstats *out)
{
    if (!ch || !out) return -EINVAL;
    struct kc_chan *c = (struct kc_chan*)ch;
    KC_MUTEX_LOCK(&c->mu);
    out->zref_sent = c->zref_sent;
    out->zref_received = c->zref_received;
    out->zref_fallback_small = c->zref_fallback_small;
    out->zref_fallback_capacity = c->zref_fallback_capacity;
    out->zref_canceled = c->zref_canceled;
    out->zref_aborted_close = c->zref_aborted_close;
    KC_MUTEX_UNLOCK(&c->mu);
    return 0;
}

/* ------------------------------------------------------------------------- */
