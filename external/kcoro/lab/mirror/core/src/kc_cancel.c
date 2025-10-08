// SPDX-License-Identifier: BSD-3-Clause
#include <stdlib.h>
#include <stdatomic.h>

#include "../../include/kcoro_port.h"
#include "../../include/kcoro.h"

struct kc_cancel_child { struct kc_cancel *child; struct kc_cancel_child *next; };

struct kc_cancel {
    atomic_int  state;  /* 0 = active, 1 = cancelled */
    KC_MUTEX_T  mu;
    KC_COND_T   cv;
    struct kc_cancel_child *children; /* linked children for propagation */
};

int kc_cancel_init(kc_cancel_t **out)
{
    if (!out) return -EINVAL;
    struct kc_cancel *t = KC_ALLOC(sizeof(*t));
    if (!t) return -ENOMEM;
    atomic_store(&t->state, 0);
    KC_MUTEX_INIT(&t->mu);
    KC_COND_INIT(&t->cv);
    *out = (kc_cancel_t*)t;
    return 0;
}

void kc_cancel_trigger(kc_cancel_t *h)
{
    if (!h) return;
    struct kc_cancel *t = (struct kc_cancel*)h;
    int expected = 0;
    if (atomic_compare_exchange_strong(&t->state, &expected, 1)) {
        KC_MUTEX_LOCK(&t->mu);
        KC_COND_BROADCAST(&t->cv);
        /* propagate to children */
        for (struct kc_cancel_child *ln = t->children; ln; ln = ln->next) {
            atomic_store(&ln->child->state, 1);
            /* cascade broadcast to wake any waiters on the child */
            KC_MUTEX_LOCK(&ln->child->mu);
            KC_COND_BROADCAST(&ln->child->cv);
            KC_MUTEX_UNLOCK(&ln->child->mu);
        }
        KC_MUTEX_UNLOCK(&t->mu);
    }
}

int kc_cancel_is_set(const kc_cancel_t *h)
{
    if (!h) return 0;
    const struct kc_cancel *t = (const struct kc_cancel*)h;
    return atomic_load(&t->state) != 0;
}

int kc_cancel_wait(const kc_cancel_t *h, long timeout_ms)
{
    if (!h) return -EINVAL;
    
    struct kc_cancel *t = (struct kc_cancel*)h; /* Remove const for locking */
    
    /* Quick check without lock */
    if (atomic_load(&t->state) != 0) {
        return 0; /* Already cancelled */
    }
    
    KC_MUTEX_LOCK(&t->mu);
    
    int rc = 0;
    if (timeout_ms < 0) {
        /* Infinite wait */
        while (atomic_load(&t->state) == 0) {
            KC_COND_WAIT(&t->cv, &t->mu);
        }
    } else {
        /* Timed wait: use MONOTONIC when available to match cond attr */
        struct timespec ts;
#ifdef CLOCK_MONOTONIC
        clock_gettime(CLOCK_MONOTONIC, &ts);
#else
        clock_gettime(CLOCK_REALTIME, &ts);
#endif
        long sec = timeout_ms / 1000;
        long nsec = (timeout_ms % 1000) * 1000000L;
        ts.tv_sec += sec;
        ts.tv_nsec += nsec;
        if (ts.tv_nsec >= 1000000000L) { 
            ts.tv_sec++; 
            ts.tv_nsec -= 1000000000L; 
        }
        
        while (atomic_load(&t->state) == 0) {
            int wait_rc = KC_COND_TIMEDWAIT_ABS(&t->cv, &t->mu, &ts);
            if (wait_rc == ETIMEDOUT) {
                rc = KC_ETIME;
                break;
            }
        }
    }
    
    KC_MUTEX_UNLOCK(&t->mu);
    return rc;
}

void kc_cancel_destroy(kc_cancel_t *h)
{
    if (!h) return;
    struct kc_cancel *t = (struct kc_cancel*)h;
    /* free child list (links only; children own themselves) */
    KC_MUTEX_LOCK(&t->mu);
    struct kc_cancel_child *p = t->children;
    t->children = NULL;
    KC_MUTEX_UNLOCK(&t->mu);
    while (p) { struct kc_cancel_child *n = p->next; KC_FREE(p); p = n; }
    KC_MUTEX_DESTROY(&t->mu);
    KC_COND_DESTROY(&t->cv);
    KC_FREE(t);
}

static int kc_cancel_link_child(struct kc_cancel *parent, struct kc_cancel *child)
{
    if (!parent || !child) return -EINVAL;
    /* If parent is already cancelled, just cancel child now (no link). */
    if (atomic_load(&parent->state) != 0) {
        atomic_store(&child->state, 1);
        KC_MUTEX_LOCK(&child->mu);
        KC_COND_BROADCAST(&child->cv);
        KC_MUTEX_UNLOCK(&child->mu);
        return 0;
    }
    struct kc_cancel_child *ln = KC_ALLOC(sizeof(*ln));
    if (!ln) return -ENOMEM;
    ln->child = child; ln->next = NULL;
    KC_MUTEX_LOCK(&parent->mu);
    ln->next = parent->children;
    parent->children = ln;
    KC_MUTEX_UNLOCK(&parent->mu);
    return 0;
}

static void kc_cancel_unlink_child(struct kc_cancel *parent, struct kc_cancel *child)
{
    if (!parent || !child) return;
    KC_MUTEX_LOCK(&parent->mu);
    struct kc_cancel_child **pp = &parent->children;
    while (*pp) {
        if ((*pp)->child == child) {
            struct kc_cancel_child *dead = *pp;
            *pp = dead->next;
            KC_MUTEX_UNLOCK(&parent->mu);
            KC_FREE(dead);
            return;
        }
        pp = &(*pp)->next;
    }
    KC_MUTEX_UNLOCK(&parent->mu);
}

int kc_cancel_ctx_init(kc_cancel_ctx_t *ctx, const kc_cancel_t *parent)
{
    if (!ctx) return -EINVAL;
    ctx->parent = parent;
    ctx->token = NULL;
    kc_cancel_t *child = NULL;
    int rc = kc_cancel_init(&child);
    if (rc) return rc;
    ctx->token = child;
    if (parent) {
        rc = kc_cancel_link_child((struct kc_cancel*)parent, (struct kc_cancel*)child);
        if (rc) { kc_cancel_destroy(child); ctx->token = NULL; return rc; }
    }
    return 0;
}

void kc_cancel_ctx_destroy(kc_cancel_ctx_t *ctx)
{
    if (!ctx) return;
    if (ctx->parent && ctx->token) {
        kc_cancel_unlink_child((struct kc_cancel*)ctx->parent, (struct kc_cancel*)ctx->token);
    }
    if (ctx->token) kc_cancel_destroy(ctx->token);
    ctx->token = NULL; ctx->parent = NULL;
}
