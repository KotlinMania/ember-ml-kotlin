// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200809L

#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <time.h>

#include "../../include/kcoro.h"
#include "../../include/kcoro_sched.h"
#include "../../include/kcoro_port.h"

struct kc_scope_child;

struct kc_scope {
    kc_cancel_ctx_t cancel_ctx;
    KC_MUTEX_T mu;
    KC_COND_T  cv;
    int shutting_down;
    int child_count;
    struct kc_scope_child *children;
};

enum kc_scope_child_kind {
    KC_SCOPE_CHILD_CORO,
    KC_SCOPE_CHILD_ACTOR
};

struct kc_scope_child {
    enum kc_scope_child_kind kind;
    struct kc_scope *scope;
    union {
        kcoro_t   *coro;
        kc_actor_t actor;
    } u;
    struct kc_scope_child *next;
};

struct kc_scope_coro_wrapper {
    kc_scope_t *scope;
    kcoro_fn_t fn;
    void *arg;
    struct kc_scope_child *child;
};

struct kc_scope_producer_state {
    kc_chan_t *chan;
    kc_producer_fn fn;
    void *user;
};

static void kc_scope_child_complete(kc_scope_t *scope, struct kc_scope_child *child)
{
    if (!scope || !child) return;
    KC_MUTEX_LOCK(&scope->mu);
    struct kc_scope_child **pp = &scope->children;
    while (*pp) {
        if (*pp == child) {
            *pp = child->next;
            scope->child_count--;
            KC_COND_BROADCAST(&scope->cv);
            break;
        }
        pp = &(*pp)->next;
    }
    KC_MUTEX_UNLOCK(&scope->mu);
    free(child);
}

static struct kc_scope_child* kc_scope_child_add(kc_scope_t *scope, enum kc_scope_child_kind kind)
{
    struct kc_scope_child *child = (struct kc_scope_child*)calloc(1, sizeof(*child));
    if (!child) return NULL;
    child->kind = kind;
    child->scope = scope;
    KC_MUTEX_LOCK(&scope->mu);
    child->next = scope->children;
    scope->children = child;
    scope->child_count++;
    KC_MUTEX_UNLOCK(&scope->mu);
    return child;
}

static void kc_scope_coro_entry(void *arg)
{
    struct kc_scope_coro_wrapper *wrap = (struct kc_scope_coro_wrapper*)arg;
    wrap->fn(wrap->arg);
    kc_scope_child_complete(wrap->scope, wrap->child);
    free(wrap);
}

static void kc_scope_actor_on_done(void *arg)
{
    struct kc_scope_child *child = (struct kc_scope_child*)arg;
    if (!child) return;
    kc_scope_child_complete(child->scope, child);
}

static int kc_scope_compute_deadline(long timeout_ms, struct timespec *ts_out)
{
    if (timeout_ms < 0) return 0;
    if (!ts_out) return -EINVAL;
    struct timespec now;
#ifdef CLOCK_MONOTONIC
    clock_gettime(CLOCK_MONOTONIC, &now);
#else
    clock_gettime(CLOCK_REALTIME, &now);
#endif
    long sec = timeout_ms / 1000;
    long nsec = (timeout_ms % 1000) * 1000000L;
    ts_out->tv_sec = now.tv_sec + sec;
    ts_out->tv_nsec = now.tv_nsec + nsec;
    if (ts_out->tv_nsec >= 1000000000L) {
        ts_out->tv_sec++;
        ts_out->tv_nsec -= 1000000000L;
    }
    return 0;
}

int kc_scope_init(kc_scope_t **out, const kc_cancel_t *parent)
{
    if (!out) return -EINVAL;
    kc_scope_t *scope = calloc(1, sizeof(*scope));
    if (!scope) return -ENOMEM;
    int rc = kc_cancel_ctx_init(&scope->cancel_ctx, parent);
    if (rc != 0) { free(scope); return rc; }
    if (KC_MUTEX_INIT(&scope->mu) != 0) { kc_cancel_ctx_destroy(&scope->cancel_ctx); free(scope); return -ENOMEM; }
    if (KC_COND_INIT(&scope->cv) != 0) { KC_MUTEX_DESTROY(&scope->mu); kc_cancel_ctx_destroy(&scope->cancel_ctx); free(scope); return -ENOMEM; }
    scope->shutting_down = 0;
    scope->child_count = 0;
    scope->children = NULL;
    *out = scope;
    return 0;
}

const kc_cancel_t* kc_scope_token(const kc_scope_t *scope)
{
    if (!scope) return NULL;
    return scope->cancel_ctx.token;
}

static int kc_scope_collect_actors(kc_scope_t *scope, kc_actor_t **actors_out, int *count_out)
{
    if (!actors_out || !count_out) return -EINVAL;
    int count = 0;
    KC_MUTEX_LOCK(&scope->mu);
    for (struct kc_scope_child *c = scope->children; c; c = c->next) {
        if (c->kind == KC_SCOPE_CHILD_ACTOR && c->u.actor) count++;
    }
    kc_actor_t *actors = NULL;
    if (count > 0) {
        actors = (kc_actor_t*)calloc((size_t)count, sizeof(kc_actor_t));
        if (!actors) {
            KC_MUTEX_UNLOCK(&scope->mu);
            return -ENOMEM;
        }
        int idx = 0;
        for (struct kc_scope_child *c = scope->children; c; c = c->next) {
            if (c->kind == KC_SCOPE_CHILD_ACTOR && c->u.actor) {
                actors[idx++] = c->u.actor;
            }
        }
    }
    KC_MUTEX_UNLOCK(&scope->mu);
    *actors_out = actors;
    *count_out = count;
    return 0;
}

void kc_scope_cancel(kc_scope_t *scope)
{
    if (!scope) return;
    kc_cancel_trigger(scope->cancel_ctx.token);
    KC_MUTEX_LOCK(&scope->mu);
    scope->shutting_down = 1;
    KC_MUTEX_UNLOCK(&scope->mu);

    kc_actor_t *actors = NULL;
    int actor_count = 0;
    if (kc_scope_collect_actors(scope, &actors, &actor_count) == 0 && actors) {
        for (int i = 0; i < actor_count; ++i) {
            kc_actor_cancel(actors[i]);
        }
        free(actors);
    }
}

int kc_scope_launch(kc_scope_t *scope, kcoro_fn_t fn, void *arg,
                    size_t stack_size, kcoro_t **out_co)
{
    if (!scope || !fn) return -EINVAL;

    KC_MUTEX_LOCK(&scope->mu);
    if (scope->shutting_down) {
        KC_MUTEX_UNLOCK(&scope->mu);
        return KC_ECANCELED;
    }
    KC_MUTEX_UNLOCK(&scope->mu);

    struct kc_scope_child *child = kc_scope_child_add(scope, KC_SCOPE_CHILD_CORO);
    if (!child) return -ENOMEM;

    struct kc_scope_coro_wrapper *wrap = (struct kc_scope_coro_wrapper*)calloc(1, sizeof(*wrap));
    if (!wrap) {
        kc_scope_child_complete(scope, child);
        return -ENOMEM;
    }

    wrap->scope = scope;
    wrap->fn = fn;
    wrap->arg = arg;
    wrap->child = child;

    kc_sched_t *sched = kc_sched_default();
    kcoro_t *co = NULL;
    int rc = kc_spawn_co(sched, kc_scope_coro_entry, wrap, stack_size, &co);
    if (rc != 0) {
        kc_scope_child_complete(scope, child);
        free(wrap);
        return rc;
    }
    child->u.coro = co;
    if (out_co) *out_co = co;
    return 0;
}

kc_actor_t kc_scope_actor(kc_scope_t *scope, const kc_actor_ctx_t *ctx)
{
    if (!scope || !ctx) return NULL;

    KC_MUTEX_LOCK(&scope->mu);
    if (scope->shutting_down) {
        KC_MUTEX_UNLOCK(&scope->mu);
        return NULL;
    }
    KC_MUTEX_UNLOCK(&scope->mu);

    kc_actor_ctx_ex_t ex = { .base = *ctx, .cancel = scope->cancel_ctx.token };
    kc_actor_t actor = kc_actor_start_ex(&ex);
    if (!actor) return NULL;

    struct kc_scope_child *child = kc_scope_child_add(scope, KC_SCOPE_CHILD_ACTOR);
    if (!child) {
        kc_actor_cancel(actor);
        return NULL;
    }
    child->u.actor = actor;
    kc_actor_on_done(actor, kc_scope_actor_on_done, child);
    return actor;
}

static void kc_scope_producer_entry(void *arg)
{
    struct kc_scope_producer_state *st = (struct kc_scope_producer_state*)arg;
    if (st->fn) st->fn(st->chan, st->user);
    kc_chan_close(st->chan);
    free(st);
}

kc_chan_t* kc_scope_produce(kc_scope_t *scope, int kind, size_t elem_sz, size_t capacity,
                            kc_producer_fn fn, void *user)
{
    if (!scope || !fn) return NULL;
    kc_chan_t *ch = NULL;
    if (kc_chan_make(&ch, kind, elem_sz, capacity) != 0) return NULL;

    struct kc_scope_producer_state *state = calloc(1, sizeof(*state));
    if (!state) {
        kc_chan_destroy(ch);
        return NULL;
    }
    state->chan = ch;
    state->fn = fn;
    state->user = user;

    int rc = kc_scope_launch(scope, kc_scope_producer_entry, state, 0, NULL);
    if (rc != 0) {
        kc_chan_destroy(ch);
        free(state);
        return NULL;
    }
    return ch;
}

int kc_scope_wait_all(kc_scope_t *scope, long timeout_ms)
{
    if (!scope) return -EINVAL;
    if (timeout_ms == 0) {
        KC_MUTEX_LOCK(&scope->mu);
        int rc = (scope->child_count == 0) ? 0 : KC_EAGAIN;
        KC_MUTEX_UNLOCK(&scope->mu);
        return rc;
    }

    struct timespec ts;
    struct timespec *deadline = NULL;
    if (timeout_ms > 0) {
        if (kc_scope_compute_deadline(timeout_ms, &ts) != 0) return -EINVAL;
        deadline = &ts;
    }

    KC_MUTEX_LOCK(&scope->mu);
    int rc = 0;
    while (scope->child_count > 0) {
        if (timeout_ms < 0) {
            KC_COND_WAIT(&scope->cv, &scope->mu);
        } else {
            int wait_rc = KC_COND_TIMEDWAIT_ABS(&scope->cv, &scope->mu, deadline);
            if (wait_rc == ETIMEDOUT) { rc = KC_ETIME; break; }
        }
    }
    KC_MUTEX_UNLOCK(&scope->mu);
    return rc;
}

void kc_scope_destroy(kc_scope_t *scope)
{
    if (!scope) return;
    kc_scope_cancel(scope);
    kc_scope_wait_all(scope, -1);
    KC_MUTEX_LOCK(&scope->mu);
    struct kc_scope_child *c = scope->children;
    scope->children = NULL;
    scope->child_count = 0;
    KC_MUTEX_UNLOCK(&scope->mu);
    while (c) {
        struct kc_scope_child *next = c->next;
        if (c->kind == KC_SCOPE_CHILD_ACTOR && c->u.actor) {
            kc_actor_stop(c->u.actor);
        }
        free(c);
        c = next;
    }
    KC_COND_DESTROY(&scope->cv);
    KC_MUTEX_DESTROY(&scope->mu);
    kc_cancel_ctx_destroy(&scope->cancel_ctx);
    free(scope);
}
