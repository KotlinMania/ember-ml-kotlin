// SPDX-License-Identifier: BSD-3-Clause
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#include <stdatomic.h>

#include "../../include/kcoro.h"
#include "../../include/kcoro_port.h"
#include "../../include/kcoro_sched.h"

struct kc_actor_state {
    kc_actor_ctx_t ctx;
    void *buf;
    /* coroutine & scheduler */
    kcoro_t *co;
    kc_sched_t *sched;
    /* lifecycle */
    atomic_int stop;
    int done;
    KC_MUTEX_T mu;
    KC_COND_T  cv;
    const kc_cancel_t *cancel; /* optional */
    void (*on_done)(void *arg);
    void *on_done_arg;
};

static void kc_actor_coro(void *arg)
{
    struct kc_actor_state *st = (struct kc_actor_state*)arg;
    for (;;) {
        if (atomic_load(&st->stop)) break;
        if (st->cancel && kc_cancel_is_set(st->cancel)) break;

        /* Non-blocking receive loop with cooperative yield */
        int rc = 0;
        if (st->cancel)
            rc = kc_chan_recv_c(st->ctx.chan, st->buf, 0, st->cancel);
        else
            rc = kc_chan_recv(st->ctx.chan, st->buf, 0);

        if (rc == 0) {
            if (st->ctx.process) st->ctx.process(st->buf, st->ctx.user);
            /* Yield to let others run */
            kcoro_yield();
            continue;
        }
        if (rc == KC_EPIPE || rc == KC_ECANCELED) {
            break;
        }
        /* EAGAIN or ETIME: yield and retry */
        kcoro_yield();
    }
    KC_MUTEX_LOCK(&st->mu);
    st->done = 1;
    KC_COND_BROADCAST(&st->cv);
    void (*cb)(void*) = st->on_done;
    void *cb_arg = st->on_done_arg;
    st->on_done = NULL;
    st->on_done_arg = NULL;
    KC_MUTEX_UNLOCK(&st->mu);
    if (cb) cb(cb_arg);
}

kc_actor_t kc_actor_start(const kc_actor_ctx_t *ctx)
{
    if (!ctx || !ctx->chan || !ctx->msg_size) return NULL;
    struct kc_actor_state *st = calloc(1, sizeof(*st));
    if (!st) return NULL;
    
    st->ctx = *ctx;
    atomic_store(&st->stop, 0);
    st->cancel = NULL;
    st->done = 0;
    st->on_done = NULL;
    st->on_done_arg = NULL;
    st->sched = kc_sched_default();
    if (KC_MUTEX_INIT(&st->mu) != 0) { free(st); return NULL; }
    if (KC_COND_INIT(&st->cv) != 0) { KC_MUTEX_DESTROY(&st->mu); free(st); return NULL; }
    
    st->buf = malloc(ctx->msg_size);
    if (!st->buf) { KC_COND_DESTROY(&st->cv); KC_MUTEX_DESTROY(&st->mu); free(st); return NULL; }

    if (kc_spawn_co(st->sched, kc_actor_coro, st, 0, &st->co) != 0) {
        free(st->buf);
        KC_COND_DESTROY(&st->cv);
        KC_MUTEX_DESTROY(&st->mu);
        free(st);
        return NULL;
    }
    return (kc_actor_t)st;
}

void kc_actor_stop(kc_actor_t actor)
{
    struct kc_actor_state *st = (struct kc_actor_state*)actor;
    if (!st) return;

    atomic_store(&st->stop, 1);
    /* Wait for coroutine to finish */
    KC_MUTEX_LOCK(&st->mu);
    while (!st->done) { KC_COND_WAIT(&st->cv, &st->mu); }
    void (*cb)(void*) = st->on_done;
    void *cb_arg = st->on_done_arg;
    st->on_done = NULL;
    st->on_done_arg = NULL;
    KC_MUTEX_UNLOCK(&st->mu);
    if (cb) cb(cb_arg);

    free(st->buf);
    KC_COND_DESTROY(&st->cv);
    KC_MUTEX_DESTROY(&st->mu);
    free(st);
}

kc_actor_t kc_actor_start_ex(const kc_actor_ctx_ex_t *ctx)
{
    if (!ctx) return NULL;
    kc_actor_t a = kc_actor_start(&ctx->base);
    if (!a) return NULL;
    struct kc_actor_state *st = (struct kc_actor_state*)a;
    st->cancel = ctx->cancel;
    return a;
}

void kc_actor_cancel(kc_actor_t actor)
{
    struct kc_actor_state *st = (struct kc_actor_state*)actor;
    if (!st) return;
    if (st->cancel) kc_cancel_trigger((kc_cancel_t*)st->cancel);
    atomic_store(&st->stop, 1);
    KC_MUTEX_LOCK(&st->mu);
    while (!st->done) { KC_COND_WAIT(&st->cv, &st->mu); }
    void (*cb)(void*) = st->on_done;
    void *cb_arg = st->on_done_arg;
    st->on_done = NULL;
    st->on_done_arg = NULL;
    KC_MUTEX_UNLOCK(&st->mu);
    if (cb) cb(cb_arg);
    free(st->buf);
    KC_COND_DESTROY(&st->cv);
    KC_MUTEX_DESTROY(&st->mu);
    free(st);
}

void kc_actor_on_done(kc_actor_t actor, void (*cb)(void *), void *arg)
{
    struct kc_actor_state *st = (struct kc_actor_state*)actor;
    if (!st) return;

    KC_MUTEX_LOCK(&st->mu);
    if (st->done) {
        KC_MUTEX_UNLOCK(&st->mu);
        if (cb) cb(arg);
        return;
    }
    st->on_done = cb;
    st->on_done_arg = arg;
    KC_MUTEX_UNLOCK(&st->mu);
}
