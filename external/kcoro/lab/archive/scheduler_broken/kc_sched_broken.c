// SPDX-License-Identifier: BSD-3-Clause
/* Archived prototype of a scheduler integration attempt. See README.md. */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#define _POSIX_C_SOURCE 200809L

#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <sched.h>

#include "kcoro_sched.h"

typedef struct kc_task_node {
    kc_task_fn fn;
    void *arg;
    struct kc_task_node *next;
} kc_task_node_t;

struct kc_sched {
    int            workers;
    pthread_t     *threads;
    int            stop;

    pthread_mutex_t mu;
    pthread_cond_t  cv;
    kc_task_node_t *head;
    kc_task_node_t *tail;
};

static void* kc_worker_main(void *arg)
{
    kc_sched_t *s = (kc_sched_t*)arg;
    for (;;) {
        pthread_mutex_lock(&s->mu);
        while (!s->stop && s->head == NULL) {
            pthread_cond_wait(&s->cv, &s->mu);
        }
        if (s->stop && s->head == NULL) {
            pthread_mutex_unlock(&s->mu);
            break;
        }
        kc_task_node_t *n = s->head;
        if (n) {
            s->head = n->next;
            if (s->head == NULL) s->tail = NULL;
        }
        pthread_mutex_unlock(&s->mu);
        if (n) {
            n->fn(n->arg);
            free(n);
        }
    }
    return NULL;
}

kc_sched_t* kc_sched_init(const kc_sched_opts_t *opts)
{
    kc_sched_t *s = (kc_sched_t*)calloc(1, sizeof(*s));
    if (!s) return NULL;
    int ncpu = (int)sysconf(_SC_NPROCESSORS_ONLN);
    int n = (opts && opts->workers > 0) ? opts->workers : (ncpu > 0 ? ncpu : 1);
    if (n < 1) n = 1;
    s->workers = n;
    pthread_mutex_init(&s->mu, NULL);
    pthread_cond_init(&s->cv, NULL);
    s->threads = (pthread_t*)calloc((size_t)n, sizeof(pthread_t));
    if (!s->threads) { pthread_mutex_destroy(&s->mu); pthread_cond_destroy(&s->cv); free(s); return NULL; }
    for (int i = 0; i < n; ++i) {
        if (pthread_create(&s->threads[i], NULL, kc_worker_main, s) != 0) {
            s->workers = i; kc_sched_shutdown(s); return NULL;
        }
    }
    return s;
}

void kc_sched_shutdown(kc_sched_t *s)
{
    if (!s) return;
    pthread_mutex_lock(&s->mu);
    s->stop = 1;
    pthread_cond_broadcast(&s->cv);
    pthread_mutex_unlock(&s->mu);
    for (int i = 0; i < s->workers; ++i) { if (s->threads[i]) pthread_join(s->threads[i], NULL); }
    kc_task_node_t *n = s->head; while (n) { kc_task_node_t *nx = n->next; free(n); n = nx; }
    pthread_mutex_destroy(&s->mu);
    pthread_cond_destroy(&s->cv);
    free(s->threads);
    free(s);
}

int kc_spawn(kc_sched_t *s, kc_task_fn fn, void *arg)
{
    if (!s || !fn) return -1;
    kc_task_node_t *n = (kc_task_node_t*)malloc(sizeof(*n));
    if (!n) return -1;
    n->fn = fn; n->arg = arg; n->next = NULL;
    pthread_mutex_lock(&s->mu);
    if (s->tail) s->tail->next = n; else s->head = n;
    s->tail = n;
    pthread_cond_signal(&s->cv);
    pthread_mutex_unlock(&s->mu);
    return 0;
}

void kc_yield(void) { sched_yield(); }
void kc_sleep_ms(int ms) { if (ms > 0) { struct timespec ts = { ms/1000, (ms%1000)*1000000L }; nanosleep(&ts, NULL); } }

