// SPDX-License-Identifier: BSD-3-Clause
#pragma once

#include <pthread.h>
#include <errno.h>
#include <time.h>

#define KC_MUTEX_T            pthread_mutex_t
#define KC_COND_T             pthread_cond_t

#define KC_MUTEX_INIT(m)      pthread_mutex_init((m), NULL)
static inline int kc_posix_cond_init_monotonic(pthread_cond_t *c)
{
    pthread_condattr_t a;
    if (pthread_condattr_init(&a) != 0) return -1;
    /* Best-effort: use MONOTONIC when available */
#if defined(__APPLE__) || defined(__MACH__)
    /* macOS does not expose pthread_condattr_setclock; skip to avoid implicit decl. */
#else
# if defined(_POSIX_TIMERS) && defined(CLOCK_MONOTONIC)
    (void)pthread_condattr_setclock(&a, CLOCK_MONOTONIC);
# endif
#endif
    int rc = pthread_cond_init(c, &a);
    (void)pthread_condattr_destroy(&a);
    return rc;
}

#define KC_COND_INIT(c)       kc_posix_cond_init_monotonic((c))
#define KC_MUTEX_DESTROY(m)    pthread_mutex_destroy((m))
#define KC_COND_DESTROY(c)     pthread_cond_destroy((c))

#define KC_MUTEX_LOCK(m)      pthread_mutex_lock((m))
#define KC_MUTEX_UNLOCK(m)    pthread_mutex_unlock((m))

#define KC_COND_WAIT(c,m)     pthread_cond_wait((c),(m))
#define KC_COND_TIMEDWAIT_ABS(c,m,ts) pthread_cond_timedwait((c),(m),(ts))

#define KC_COND_SIGNAL(c)     pthread_cond_signal((c))
#define KC_COND_BROADCAST(c)  pthread_cond_broadcast((c))

#define KC_ALLOC(n)           malloc((n))
#define KC_FREE(p)            free((p))

#define KC_EAGAIN             (-EAGAIN)
#define KC_EPIPE              (-EPIPE)
#define KC_ETIME              (-ETIME)
#define KC_ECANCELED          (-ECANCELED)
