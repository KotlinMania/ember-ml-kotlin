// SPDX-License-Identifier: BSD-3-Clause

#include "kcoro_dispatch.h"

#include <stdlib.h>
#include <pthread.h>
#include <stdatomic.h>
#include <unistd.h>
#include <limits.h>

struct kc_dispatcher {
    kc_sched_t* sched;
    _Atomic int refcount;
    int owns_sched;
};

typedef struct kc_dispatcher kc_dispatcher_impl_t;

static kc_dispatcher_impl_t* kc_dispatcher_alloc(kc_sched_t* sched, int owns_sched) {
    if (!sched) return NULL;
    kc_dispatcher_impl_t* disp = (kc_dispatcher_impl_t*)malloc(sizeof(kc_dispatcher_impl_t));
    if (!disp) {
        if (owns_sched) {
            kc_sched_shutdown(sched);
        }
        return NULL;
    }
    disp->sched = sched;
    atomic_init(&disp->refcount, 1);
    disp->owns_sched = owns_sched;
    return disp;
}

kc_dispatcher_t* kc_dispatcher_new(int workers) {
    kc_sched_opts_t opts = {0};
    opts.workers = workers;
    kc_sched_t* sched = kc_sched_init(&opts);
    if (!sched) return NULL;
    return kc_dispatcher_alloc(sched, 1);
}

kc_dispatcher_t* kc_dispatcher_retain(kc_dispatcher_t* dispatcher) {
    if (!dispatcher) return NULL;
    atomic_fetch_add_explicit(&dispatcher->refcount, 1, memory_order_relaxed);
    return dispatcher;
}

void kc_dispatcher_release(kc_dispatcher_t* dispatcher) {
    if (!dispatcher) return;
    if (atomic_fetch_sub_explicit(&dispatcher->refcount, 1, memory_order_acq_rel) == 1) {
        if (dispatcher->owns_sched && dispatcher->sched) {
            kc_sched_shutdown(dispatcher->sched);
        }
        free(dispatcher);
    }
}

static long kc_suggest_ncpu(void) {
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    if (n < 1) n = sysconf(_SC_NPROCESSORS_CONF);
    if (n < 1) n = 1;
    if (n > INT_MAX) n = INT_MAX;
    return n;
}

static pthread_mutex_t g_dispatch_mu = PTHREAD_MUTEX_INITIALIZER;
static kc_dispatcher_impl_t* g_default_dispatcher = NULL;
static kc_dispatcher_impl_t* g_io_dispatcher = NULL;

kc_dispatcher_t* kc_dispatcher_default(void) {
    pthread_mutex_lock(&g_dispatch_mu);
    if (!g_default_dispatcher) {
        kc_sched_t* sched = kc_sched_default();
        g_default_dispatcher = kc_dispatcher_alloc(sched, 0);
    }
    kc_dispatcher_t* result = kc_dispatcher_retain(g_default_dispatcher);
    pthread_mutex_unlock(&g_dispatch_mu);
    return result;
}

kc_dispatcher_t* kc_dispatcher_io(void) {
    pthread_mutex_lock(&g_dispatch_mu);
    if (!g_io_dispatcher) {
        long ncpu = kc_suggest_ncpu();
        int workers = (int)ncpu;
        if (workers < 1) workers = 1;
        if (workers < 64) workers = 64;
        kc_dispatcher_t* disp = kc_dispatcher_new(workers);
        g_io_dispatcher = disp;
    }
    kc_dispatcher_t* result = kc_dispatcher_retain(g_io_dispatcher);
    pthread_mutex_unlock(&g_dispatch_mu);
    return result;
}

kc_sched_t* kc_dispatcher_scheduler(kc_dispatcher_t* dispatcher) {
    if (!dispatcher) return NULL;
    return dispatcher->sched;
}

int kc_dispatcher_spawn(kc_dispatcher_t* dispatcher, kc_task_fn fn, void* arg) {
    if (!dispatcher || !fn) return -1;
    return kc_spawn(dispatcher->sched, fn, arg);
}

int kc_dispatcher_spawn_co(kc_dispatcher_t* dispatcher,
                           kcoro_fn_t fn,
                           void* arg,
                           size_t stack_size,
                           kcoro_t** out_co) {
    if (!dispatcher || !fn) return -1;
    return kc_spawn_co(dispatcher->sched, fn, arg, stack_size, out_co);
}
