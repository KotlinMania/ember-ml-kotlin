// SPDX-License-Identifier: BSD-3-Clause
/* Unified work-stealing scheduler (pure merged implementation). */
#define _GNU_SOURCE 1
#include <pthread.h>
#include <stdlib.h>
#include <stdint.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#ifdef __linux__
#include <sys/sysinfo.h>
#endif
#include <stdatomic.h>
#include <errno.h>
#include <time.h>
#include <sched.h>
#include <stdbool.h>

#include "kcoro_sched.h"

#ifndef __linux__
static int kc_get_nprocs(void)
{
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    if (n < 1) n = sysconf(_SC_NPROCESSORS_CONF);
    if (n < 1) n = 1;
    if (n > INT_MAX) n = INT_MAX;
    return (int)n;
}
#else
#define kc_get_nprocs get_nprocs
#endif

#include "kcoro_core.h"

static int kc_sched_debug_enabled(void)
{
    static int cached = -1;
    if (__builtin_expect(cached == -1, 0)) {
        const char *env = getenv("KC_SCHED_DEBUG");
        cached = (env && *env && env[0] != '0');
    }
    return cached;
}

#define KC_SCHED_DEBUG(fmt, ...)                                                     \
    do {                                                                             \
        if (kc_sched_debug_enabled()) {                                             \
            fprintf(stderr, "[kcoro][sched] " fmt "\n", ##__VA_ARGS__);         \
        }                                                                            \
    } while (0)

/* Debug logging: production code uses runtime logging (KCORO_DEBUG env) if needed.
 * No compile-time debug split macros. */

/* ---- Timers (ported from C++ scheduler concept) ---- */

typedef struct kc_timer_item {
    uint64_t id;                /* timer id */
    uint64_t when_ns;           /* absolute deadline (CLOCK_MONOTONIC) */
    kcoro_t* co;                /* coroutine to wake */
    int cancelled;              /* best-effort cancellation flag */
    struct kc_timer_item* next; /* singly-linked list, sorted by when_ns */
} kc_timer_item_t;

static inline uint64_t kc_now_ns(void)
{
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

static void kc_timer_ensure_started(struct kc_sched *s);
static void* kc_timer_main(void *arg);

/* ---- Internal Types ---- */

typedef void (*sched_task_fn)(void *arg); /* internal task fn */

typedef struct sched_task { sched_task_fn fn; void *arg; } sched_task_t;

typedef struct kc_deque {
    pthread_mutex_t mu;
    sched_task_t *buf;
    uint32_t cap;
    uint32_t head; /* steal from head */
    uint32_t tail; /* owner push/pop tail */
    _Atomic(uint32_t) len; /* approximate length */
} kc_deque_t;

static int deque_init(kc_deque_t *d, uint32_t cap) {
    memset(d, 0, sizeof(*d));
    if (cap==0) cap = 1024;
    d->buf = (sched_task_t*)calloc(cap, sizeof(sched_task_t));
    if (!d->buf) return -1;
    d->cap = cap; atomic_store(&d->len, 0); pthread_mutex_init(&d->mu, NULL); return 0;
}
static void deque_destroy(kc_deque_t *d){ if(!d) return; free(d->buf); pthread_mutex_destroy(&d->mu);} 
static int deque_push(kc_deque_t *d, sched_task_fn fn, void *arg){
    pthread_mutex_lock(&d->mu);
    uint32_t next=(d->tail+1)%d->cap;
    if(next==d->head){
    uint32_t ncap=d->cap*2; sched_task_t *nbuf=(sched_task_t*)calloc(ncap,sizeof(sched_task_t));
        if(!nbuf){pthread_mutex_unlock(&d->mu);return -1;}
        uint32_t i=0,h=d->head; while(h!=d->tail){ nbuf[i++]=d->buf[h]; h=(h+1)%d->cap; }
        d->head=0; d->tail=i; free(d->buf); d->buf=nbuf; d->cap=ncap; next=(d->tail+1)%d->cap;
    }
    d->buf[d->tail].fn=fn; d->buf[d->tail].arg=arg; d->tail=next; atomic_fetch_add(&d->len,1); pthread_mutex_unlock(&d->mu); return 0;
}
static int deque_pop_owner(kc_deque_t *d, sched_task_t *out){ pthread_mutex_lock(&d->mu); if(d->head==d->tail){ pthread_mutex_unlock(&d->mu); return 0;} uint32_t new_tail=(d->tail + d->cap -1)%d->cap; *out=d->buf[new_tail]; d->tail=new_tail; atomic_fetch_sub(&d->len,1); pthread_mutex_unlock(&d->mu); return 1; }
static int deque_steal(kc_deque_t *d, sched_task_t *out){ pthread_mutex_lock(&d->mu); if(d->head==d->tail){ pthread_mutex_unlock(&d->mu); return 0;} *out=d->buf[d->head]; d->head=(d->head+1)%d->cap; atomic_fetch_sub(&d->len,1); pthread_mutex_unlock(&d->mu); return 1; }

typedef struct sched_worker {
    pthread_t thr; int id; struct kc_sched *sched; kc_deque_t dq; _Atomic(sched_task_t*) last_task; kcoro_t *main_co;
} sched_worker_t;

struct kc_sched { /* unified */
    int workers; sched_worker_t *w; _Atomic(int) stop;
    _Atomic(unsigned long) tasks_submitted, tasks_completed;
    _Atomic(unsigned long) steals_probes, steals_succeeded, steals_failures;
    _Atomic(unsigned long) fastpath_hits, fastpath_misses, inject_pulls, donations;
    pthread_mutex_t park_mu; pthread_cond_t park_cv; _Atomic(int) idle_workers;
    pthread_mutex_t inject_mu; sched_task_t *inject_buf; uint32_t inject_cap, inject_head, inject_tail;
    pthread_mutex_t rq_mu; kcoro_t *rq_head, *rq_tail;
    /* Timer subsystem */
    pthread_mutex_t timer_mu; pthread_cond_t timer_cv;
    kc_timer_item_t* timer_head;
    pthread_t timer_thr;
    _Atomic(int) timer_started;
    _Atomic(uint64_t) next_timer_id;
};

static __thread struct kc_sched *tls_current_sched = NULL;

/* Ready queue helpers */
static void rq_push_locked(struct kc_sched *s, kcoro_t *co)
{
    if (!co) return;
    if (co->ready_enqueued) {
        KC_SCHED_DEBUG("skip enqueue co=%p (already enqueued)", (void*)co);
        return;
    }
    if (co->next) {
        KC_SCHED_DEBUG("enqueue co=%p while next=%p -- clearing", (void*)co, (void*)co->next);
    }
    co->next = NULL;
    kcoro_t *tail = s->rq_tail;
    KC_SCHED_DEBUG("push co=%p prev_tail=%p head=%p", (void*)co, (void*)tail, (void*)s->rq_head);
    if (tail) tail->next = co; else s->rq_head = co;
    s->rq_tail = co;
    co->ready_enqueued = true;
}

static kcoro_t* rq_pop_locked(struct kc_sched *s)
{
    kcoro_t *co = s->rq_head;
    if (co) {
        kcoro_t *next = co->next;
        KC_SCHED_DEBUG("pop co=%p next=%p head=%p tail=%p", (void*)co, (void*)next, (void*)s->rq_head, (void*)s->rq_tail);
        s->rq_head = next;
        if (!s->rq_head) s->rq_tail = NULL;
        co->next = NULL;
        co->ready_enqueued = false;
    }
    return co;
}

/* Inject queue */
static int inject_init(struct kc_sched *s, uint32_t cap){ if(cap==0) cap=2048; s->inject_buf=(sched_task_t*)calloc(cap,sizeof(sched_task_t)); if(!s->inject_buf) return -1; s->inject_cap=cap; s->inject_head=s->inject_tail=0; pthread_mutex_init(&s->inject_mu,NULL); return 0; }
static void inject_destroy(struct kc_sched *s){ if(s->inject_buf) free(s->inject_buf); pthread_mutex_destroy(&s->inject_mu);} 
static int inject_push(struct kc_sched *s, sched_task_fn fn, void *arg){ pthread_mutex_lock(&s->inject_mu); uint32_t next=(s->inject_tail+1)%s->inject_cap; if(next==s->inject_head){ uint32_t ncap=s->inject_cap*2; sched_task_t *nbuf=(sched_task_t*)calloc(ncap,sizeof(sched_task_t)); if(!nbuf){ pthread_mutex_unlock(&s->inject_mu); return -1;} uint32_t i=0,h=s->inject_head; while(h!=s->inject_tail){ nbuf[i++]=s->inject_buf[h]; h=(h+1)%s->inject_cap;} s->inject_head=0; s->inject_tail=i; free(s->inject_buf); s->inject_buf=nbuf; s->inject_cap=ncap; next=(s->inject_tail+1)%s->inject_cap;} s->inject_buf[s->inject_tail].fn=fn; s->inject_buf[s->inject_tail].arg=arg; s->inject_tail=next; pthread_mutex_unlock(&s->inject_mu); return 0; }
static int inject_pop(struct kc_sched *s, sched_task_t *out){ pthread_mutex_lock(&s->inject_mu); if(s->inject_head==s->inject_tail){ pthread_mutex_unlock(&s->inject_mu); return 0;} *out=s->inject_buf[s->inject_head]; s->inject_head=(s->inject_head+1)%s->inject_cap; pthread_mutex_unlock(&s->inject_mu); return 1; }

/* PRNG */
static inline uint32_t ws_rand(uint32_t *state){ uint32_t x=*state; x^=x<<13; x^=x>>17; x^=x<<5; return *state = x?x:0x12345678u; }

#ifndef KC_SCHED_STEAL_SCAN_MAX
#define KC_SCHED_STEAL_SCAN_MAX 4
#endif

static void* worker_main(void *arg){
    sched_worker_t *w = (sched_worker_t*)arg;
    struct kc_sched *s = w->sched;
    tls_current_sched = s;
    w->main_co = kcoro_create_main();
    if (!w->main_co) {
        KC_SCHED_DEBUG("worker %d failed to create main coroutine", w->id);
        return NULL;
    }
    kcoro_set_thread_main(w->main_co);
    sched_task_t task;
    uint32_t rng = (uint32_t)((intptr_t)w ^ 0x9e3779b9u);
    while (!atomic_load(&s->stop)) {
    sched_task_t *slot = atomic_exchange_explicit(&w->last_task, NULL, memory_order_acq_rel);
        if (slot) {
            slot->fn(slot->arg);
            free(slot);
            atomic_fetch_add(&s->fastpath_hits, 1);
            atomic_fetch_add(&s->tasks_completed, 1);
            continue;
        }
        if (deque_pop_owner(&w->dq, &task)) {
            task.fn(task.arg);
            atomic_fetch_add(&s->tasks_completed, 1);
            continue;
        }
        pthread_mutex_lock(&s->rq_mu);
        kcoro_t *co = rq_pop_locked(s);
        pthread_mutex_unlock(&s->rq_mu);
        if (co) {
            KC_SCHED_DEBUG("worker %d resume co=%p state=%d tail=%p head=%p", w->id, (void*)co, co->state, (void*)s->rq_tail, (void*)s->rq_head);
            int expected = 0;
            if (!atomic_compare_exchange_strong_explicit(&co->running_flag, &expected, 1,
                                                         memory_order_acq_rel, memory_order_relaxed)) {
                pthread_mutex_lock(&s->rq_mu);
                kcoro_retain(co);
                rq_push_locked(s, co);
                pthread_mutex_unlock(&s->rq_mu);
                kcoro_release(co);
                continue;
            }

            co->main_co = w->main_co;
            kcoro_set_thread_main(w->main_co);
            co->scheduler = (kcoro_sched_t*)s;
            kcoro_resume(co);
            if (co->state == KCORO_READY || co->state == KCORO_SUSPENDED) {
                kcoro_retain(co);
                pthread_mutex_lock(&s->rq_mu);
                rq_push_locked(s, co);
                pthread_mutex_unlock(&s->rq_mu);
                atomic_store_explicit(&co->running_flag, 0, memory_order_release);
                kcoro_release(co);
            } else if (co->state == KCORO_FINISHED) {
                atomic_store_explicit(&co->running_flag, 0, memory_order_release);
                kcoro_release(co);
            } else {
                atomic_store_explicit(&co->running_flag, 0, memory_order_release);
                kcoro_release(co);
            }
            continue;
        }
        if (inject_pop(s, &task)) {
            task.fn(task.arg);
            atomic_fetch_add(&s->inject_pulls, 1);
            atomic_fetch_add(&s->tasks_completed, 1);
            continue;
        }
        int found = 0;
        for (int attempt = 0; attempt < KC_SCHED_STEAL_SCAN_MAX; ++attempt) {
            uint32_t r32 = ws_rand(&rng);
            int victim = (w->id + 1 + (int)(r32 % (uint32_t)(s->workers - 1))) % s->workers;
            if (victim == w->id) continue;
            kc_deque_t *vd = &s->w[victim].dq;
            if (atomic_load_explicit(&vd->len, memory_order_relaxed) == 0) continue;
            atomic_fetch_add(&s->steals_probes, 1);
            sched_task_t stolen;
            if (deque_steal(vd, &stolen)) {
                atomic_fetch_add(&s->steals_succeeded, 1);
                stolen.fn(stolen.arg);
                atomic_fetch_add(&s->tasks_completed, 1);
                found = 1;
                break;
            } else {
                atomic_fetch_add(&s->steals_failures, 1);
            }
        }
        if (found) {
            continue;
        }
        atomic_fetch_add(&s->idle_workers, 1);
        pthread_mutex_lock(&s->park_mu);
        struct timespec ts;
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_nsec += 5 * 1000 * 1000;
        if (ts.tv_nsec >= 1000000000L) { ts.tv_sec++; ts.tv_nsec -= 1000000000L; }
        if (!atomic_load(&s->stop)) pthread_cond_timedwait(&s->park_cv, &s->park_mu, &ts);
        pthread_mutex_unlock(&s->park_mu);
        atomic_fetch_sub(&s->idle_workers, 1);
    }
    sched_task_t *slot_rem = atomic_exchange_explicit(&w->last_task, NULL, memory_order_acq_rel);
    if (slot_rem) {
        slot_rem->fn(slot_rem->arg);
        free(slot_rem);
        atomic_fetch_add(&s->fastpath_hits, 1);
        atomic_fetch_add(&s->tasks_completed, 1);
    }
    while (deque_pop_owner(&w->dq, &task)) {
        task.fn(task.arg);
        atomic_fetch_add(&s->tasks_completed, 1);
    }
    if (w->main_co) {
        KC_SCHED_DEBUG("worker %d destroy main_co=%p", w->id, (void*)w->main_co);
        kcoro_destroy(w->main_co);
        w->main_co = NULL;
    }
    tls_current_sched = NULL;
    return NULL;
}

/* ---- Timer implementation ---- */

static void* kc_timer_main(void *arg)
{
    struct kc_sched *s = (struct kc_sched*)arg;
    for (;;) {
        pthread_mutex_lock(&s->timer_mu);
        // Wait until there is at least one timer or stop requested
        while (!s->timer_head && !atomic_load(&s->stop)) {
            pthread_cond_wait(&s->timer_cv, &s->timer_mu);
        }
        if (atomic_load(&s->stop)) {
            pthread_mutex_unlock(&s->timer_mu);
            break;
        }
        // Drop canceled items at head to maintain accurate earliest deadline
        while (s->timer_head && s->timer_head->cancelled) {
            kc_timer_item_t* del = s->timer_head; s->timer_head = del->next; free(del);
        }
        uint64_t now = kc_now_ns();
        uint64_t when = s->timer_head ? s->timer_head->when_ns : now + 1000000000ull;
        if (s->timer_head && s->timer_head->when_ns > now) {
            // Sleep until the earliest deadline or stop
            struct timespec ts;
            uint64_t delta_ns = when - now;
            clock_gettime(CLOCK_REALTIME, &ts);
            // convert delta_ns to absolute timespec on CLOCK_REALTIME for timedwait
            ts.tv_sec += (time_t)(delta_ns / 1000000000ull);
            long add_ns = (long)(delta_ns % 1000000000ull);
            ts.tv_nsec += add_ns;
            if (ts.tv_nsec >= 1000000000L) { ts.tv_sec++; ts.tv_nsec -= 1000000000L; }
            pthread_cond_timedwait(&s->timer_cv, &s->timer_mu, &ts);
            pthread_mutex_unlock(&s->timer_mu);
            continue;
        }
        // Pop all due timers (skip canceled)
        kc_timer_item_t *due_head = NULL, *due_tail = NULL;
        now = kc_now_ns();
        while (s->timer_head && s->timer_head->when_ns <= now) {
            kc_timer_item_t* it = s->timer_head;
            s->timer_head = it->next;
            if (!it->cancelled) {
                it->next = NULL;
                if (!due_head) due_head = it; else due_tail->next = it;
                due_tail = it;
            } else {
                free(it);
            }
        }
        pthread_mutex_unlock(&s->timer_mu);
        // Enqueue ready outside timer lock
        while (due_head) {
            kc_timer_item_t* it = due_head; due_head = it->next;
            if (it->co) kc_sched_enqueue_ready(s, it->co);
            free(it);
        }
    }
    return NULL;
}

static void kc_timer_ensure_started(struct kc_sched *s)
{
    int started = atomic_load(&s->timer_started);
    if (started) return;
    pthread_mutex_lock(&s->park_mu); // reuse park_mu to serialize start once
    if (!atomic_load(&s->timer_started)) {
        pthread_mutex_init(&s->timer_mu, NULL);
        pthread_cond_init(&s->timer_cv, NULL);
        s->timer_head = NULL;
        if (pthread_create(&s->timer_thr, NULL, kc_timer_main, s) == 0) {
            atomic_store(&s->timer_started, 1);
        }
    }
    pthread_mutex_unlock(&s->park_mu);
}

/* ---- Public Creation / Shutdown (legacy names preserved) ---- */

kc_sched_t* kc_sched_init(const kc_sched_opts_t *opts){
    struct kc_sched *s=(struct kc_sched*)calloc(1,sizeof(*s));
    if(!s) return NULL;
    int ncpu=kc_get_nprocs();
    int n=(opts && opts->workers>0)? opts->workers : (ncpu>0?ncpu:1);
    if(n<1) n=1; if(n>256) n=256; s->workers=n;
    pthread_mutex_init(&s->park_mu,NULL);
    pthread_cond_init(&s->park_cv,NULL);
    pthread_mutex_init(&s->rq_mu,NULL);
    /* Initialize timer subsystem state explicitly */
    atomic_store(&s->timer_started, 0);
    atomic_store(&s->next_timer_id, 0);
    s->timer_head = NULL;
    s->timer_thr = (pthread_t)0;
    /* Ready queue init */
    s->rq_head = NULL; s->rq_tail = NULL;
    /* Workers */
    s->w=(sched_worker_t*)calloc((size_t)n,sizeof(sched_worker_t));
    if(!s->w){ free(s); return NULL; }
    for(int i=0;i<n;i++){
        sched_worker_t *w=&s->w[i];
        w->id=i; w->sched=s; atomic_store(&w->last_task,NULL);
        if(deque_init(&w->dq,256)!=0){}
        if(pthread_create(&w->thr,NULL,worker_main,w)!=0){}
    }
    if(inject_init(s,0)!=0){}
    return s;
}

void kc_sched_shutdown(kc_sched_t *s){
    if(!s) return;
    /* Request stop */
    atomic_store(&s->stop,1);
    /* Wake all worker threads */
    pthread_mutex_lock(&s->park_mu);
    pthread_cond_broadcast(&s->park_cv);
    pthread_mutex_unlock(&s->park_mu);
    /* Wake timer thread if started */
    if (atomic_load(&s->timer_started)) {
        pthread_mutex_lock(&s->timer_mu);
        pthread_cond_broadcast(&s->timer_cv);
        pthread_mutex_unlock(&s->timer_mu);
    }
    /* Join workers */
    for(int i=0;i<s->workers;i++){
        if(s->w[i].thr) pthread_join(s->w[i].thr,NULL);
        if(s->w[i].main_co){ kcoro_destroy(s->w[i].main_co); s->w[i].main_co=NULL; }
        deque_destroy(&s->w[i].dq);
    }
    /* Join timer thread and cleanup timers */
    if (atomic_load(&s->timer_started)) {
        pthread_join(s->timer_thr, NULL);
        pthread_mutex_lock(&s->timer_mu);
        kc_timer_item_t* it = s->timer_head; s->timer_head = NULL;
        pthread_mutex_unlock(&s->timer_mu);
        while (it) { kc_timer_item_t* n = it->next; free(it); it = n; }
        pthread_mutex_destroy(&s->timer_mu);
        pthread_cond_destroy(&s->timer_cv);
    }
    /* Destroy remaining ready coroutines */
    pthread_mutex_lock(&s->rq_mu);
    kcoro_t *co=s->rq_head; while(co){ kcoro_t *next=co->next; co->next=NULL; kcoro_destroy(co); co=next; }
    s->rq_head=s->rq_tail=NULL;
    pthread_mutex_unlock(&s->rq_mu);
    pthread_mutex_destroy(&s->park_mu);
    pthread_cond_destroy(&s->park_cv);
    pthread_mutex_destroy(&s->rq_mu);
    inject_destroy(s);
    free(s->w);
    free(s);
}

int kc_spawn(kc_sched_t *s, kc_task_fn fn, void *arg){ if(!s||!fn) return -1; static _Atomic(unsigned) rr=0; unsigned idx=atomic_fetch_add(&rr,1)%(unsigned)s->workers; sched_worker_t *w=&s->w[idx]; uint32_t qlen=atomic_load_explicit(&w->dq.len,memory_order_relaxed); const uint32_t DONATE_THRESHOLD=64; if(qlen>DONATE_THRESHOLD){ if(inject_push(s, fn, arg)!=0) return -1; atomic_fetch_add(&s->tasks_submitted,1); if(atomic_load(&s->idle_workers)>0){ pthread_mutex_lock(&s->park_mu); pthread_cond_signal(&s->park_cv); pthread_mutex_unlock(&s->park_mu);} return 0; } sched_task_t *t=(sched_task_t*)malloc(sizeof(sched_task_t)); if(!t) return -1; t->fn=(sched_task_fn)fn; t->arg=arg; sched_task_t *expected=NULL; if(!atomic_compare_exchange_strong_explicit(&w->last_task,&expected,t,memory_order_release,memory_order_relaxed)){ atomic_fetch_add(&s->fastpath_misses,1); free(t); if(deque_push(&w->dq,(sched_task_fn)fn,arg)!=0) return -1; } else { /* fast-path success counts as submission */ } atomic_fetch_add(&s->tasks_submitted,1); if(atomic_load(&s->idle_workers)>0){ pthread_mutex_lock(&s->park_mu); pthread_cond_signal(&s->park_cv); pthread_mutex_unlock(&s->park_mu);} return 0; }

void kc_yield(void){
    kcoro_t* cur = kcoro_current();
    kc_sched_t* s = kc_sched_current();
    if (cur && s) {
        /* Cooperative yield: switch back to main; worker loop will re-enqueue if still runnable */
        kcoro_yield();
        return;
    }
    /* Fallback: thread yield */
    sched_yield();
}
void kc_sleep_ms(int ms){
    if (ms <= 0) return;
    kcoro_t* cur = kcoro_current();
    kc_sched_t* s = kc_sched_current();
    if (cur && s) {
        /* Cooperative sleep: schedule wake and park */
        (void)kc_sched_timer_wake_after(s, cur, ms);
        kcoro_park();
        return;
    }
    /* Fallback: thread sleep */
    struct timespec ts; ts.tv_sec = ms/1000; ts.tv_nsec = (ms%1000)*1000000L; nanosleep(&ts, NULL);
}

kc_timer_handle_t kc_sched_timer_wake_after(kc_sched_t* s, kcoro_t* co, long delay_ms)
{
    kc_timer_handle_t h = {0}; if (!s || !co) return h; if (delay_ms < 0) delay_ms = 0;
    kc_timer_ensure_started(s);
    uint64_t deadline = kc_now_ns() + (uint64_t)delay_ms * 1000000ull;
    kc_timer_item_t* item = (kc_timer_item_t*)calloc(1, sizeof(kc_timer_item_t));
    if (!item) return h;
    uint64_t id = atomic_fetch_add(&s->next_timer_id, 1) + 1;
    item->id = id; item->when_ns = deadline; item->co = co; item->cancelled = 0; item->next = NULL;
    pthread_mutex_lock(&s->timer_mu);
    int head_changed = 0;
    if (!s->timer_head || deadline < s->timer_head->when_ns) {
        item->next = s->timer_head; s->timer_head = item; head_changed = 1;
    } else {
        kc_timer_item_t* cur = s->timer_head;
        while (cur->next && cur->next->when_ns <= deadline) cur = cur->next;
        item->next = cur->next; cur->next = item;
    }
    if (head_changed) pthread_cond_signal(&s->timer_cv);
    pthread_mutex_unlock(&s->timer_mu);
    h.id = id; return h;
}

kc_timer_handle_t kc_sched_timer_wake_at(kc_sched_t* s, kcoro_t* co, unsigned long long deadline_ns)
{
    kc_timer_handle_t h = {0}; if (!s || !co) return h;
    kc_timer_ensure_started(s);
    kc_timer_item_t* item = (kc_timer_item_t*)calloc(1, sizeof(kc_timer_item_t));
    if (!item) return h;
    uint64_t id = atomic_fetch_add(&s->next_timer_id, 1) + 1;
    item->id = id; item->when_ns = (uint64_t)deadline_ns; item->co = co; item->cancelled = 0; item->next = NULL;
    pthread_mutex_lock(&s->timer_mu);
    int head_changed = 0;
    if (!s->timer_head || item->when_ns < s->timer_head->when_ns) {
        item->next = s->timer_head; s->timer_head = item; head_changed = 1;
    } else {
        kc_timer_item_t* cur = s->timer_head;
        while (cur->next && cur->next->when_ns <= item->when_ns) cur = cur->next;
        item->next = cur->next; cur->next = item;
    }
    if (head_changed) pthread_cond_signal(&s->timer_cv);
    pthread_mutex_unlock(&s->timer_mu);
    h.id = id; return h;
}

int kc_sched_timer_cancel(kc_sched_t* s, kc_timer_handle_t h)
{
    if (!s || h.id == 0) return 0;
    pthread_mutex_lock(&s->timer_mu);
    kc_timer_item_t* prev = NULL; kc_timer_item_t* cur = s->timer_head;
    while (cur) {
        if (cur->id == h.id) {
            int head_cancelled = (prev == NULL);
            if (prev) prev->next = cur->next; else s->timer_head = cur->next;
            free(cur);
            if (head_cancelled) pthread_cond_signal(&s->timer_cv);
            pthread_mutex_unlock(&s->timer_mu);
            return 1;
        }
        prev = cur; cur = cur->next;
    }
    pthread_mutex_unlock(&s->timer_mu);
    return 0;
}

/* ---- Coroutine API (legacy names) ---- */
int kc_spawn_co(kc_sched_t* s, kcoro_fn_t fn, void* arg, size_t stack_size, kcoro_t** out_co){
    if(!s||!fn) return -1;
    kcoro_t *co=kcoro_create(fn,arg,stack_size);
    if(!co) return -1;
    co->scheduler = (kcoro_sched_t*)s;
    if(out_co) *out_co=co;
    /* Ready queue takes ownership; retain before enqueue so rq_pop_locked releases the queue hold. */
    kcoro_retain(co);
    pthread_mutex_lock(&s->rq_mu);
    rq_push_locked(s,co);
    pthread_mutex_unlock(&s->rq_mu);
    if(atomic_load(&s->idle_workers)>0){ pthread_mutex_lock(&s->park_mu); pthread_cond_signal(&s->park_cv); pthread_mutex_unlock(&s->park_mu);} return 0; }
void kc_sched_enqueue_ready(kc_sched_t* s, kcoro_t* co)
{
    if (!s || !co) return;
    pthread_mutex_lock(&s->rq_mu);
    if (co->state == KCORO_RUNNING || co->ready_enqueued) {
        pthread_mutex_unlock(&s->rq_mu);
        return;
    }
    if (co->state == KCORO_FINISHED) {
        KC_SCHED_DEBUG("skip enqueue finished co=%p", (void*)co);
        pthread_mutex_unlock(&s->rq_mu);
        return;
    }
    if (co->state != KCORO_READY && co->state != KCORO_RUNNING) {
        co->state = KCORO_READY;
    }
    kcoro_retain(co);
    co->scheduler = (kcoro_sched_t*)s;
    rq_push_locked(s, co);
    pthread_mutex_unlock(&s->rq_mu);
    if (atomic_load(&s->idle_workers) > 0) {
        pthread_mutex_lock(&s->park_mu);
        pthread_cond_signal(&s->park_cv);
        pthread_mutex_unlock(&s->park_mu);
    }
}
kc_sched_t* kc_sched_current(void){ return tls_current_sched; }

/* ---- Default singleton ---- */
kc_sched_t* kc_sched_default(void){
    static kc_sched_t *g = NULL;
    static pthread_mutex_t mu = PTHREAD_MUTEX_INITIALIZER;
    if (__builtin_expect(g != NULL, 1)) return g;
    pthread_mutex_lock(&mu);
    if (!g) g = kc_sched_init(NULL);
    pthread_mutex_unlock(&mu);
    return g;
}

void kc_sched_get_stats(kc_sched_t *s, kc_sched_stats_t *out){ if(!s||!out) return; out->tasks_submitted=atomic_load(&s->tasks_submitted); out->tasks_completed=atomic_load(&s->tasks_completed); out->steals_probes=atomic_load(&s->steals_probes); out->steals_succeeded=atomic_load(&s->steals_succeeded); out->steals_failures=atomic_load(&s->steals_failures); out->fastpath_hits=atomic_load(&s->fastpath_hits); out->fastpath_misses=atomic_load(&s->fastpath_misses); out->inject_pulls=atomic_load(&s->inject_pulls); out->donations=atomic_load(&s->donations); }

static int approx_idle(struct kc_sched *s){
    /* Check global inject queue */
    int inject_empty;
    pthread_mutex_lock(&s->inject_mu);
    inject_empty = (s->inject_head == s->inject_tail);
    pthread_mutex_unlock(&s->inject_mu);

    /* Check ready queue */
    int rq_empty;
    pthread_mutex_lock(&s->rq_mu);
    rq_empty = (s->rq_head == NULL);
    pthread_mutex_unlock(&s->rq_mu);

    /* Check each worker's deque and last_task */
    for (int i = 0; i < s->workers; i++){
        sched_worker_t *w = &s->w[i];
        if (atomic_load_explicit(&w->dq.len, memory_order_relaxed) != 0) return 0;
        if (atomic_load_explicit(&w->last_task, memory_order_acquire) != NULL) return 0;
    }

    /* All workers idle? */
    int idle = (atomic_load(&s->idle_workers) == s->workers);
    return (inject_empty && rq_empty && idle) ? 1 : 0;
}

int kc_sched_drain(struct kc_sched *s, long timeout_ms){
    if (!s) return -1;
    if (timeout_ms == 0) return approx_idle(s) ? 0 : -ETIME;
    long waited = 0;
    const long slice = 5; /* ms */
    if (timeout_ms < 0){
        for(;;){ if (approx_idle(s)) return 0; kc_sleep_ms((int)slice); }
    }
    while (waited <= timeout_ms){
        if (approx_idle(s)) return 0;
        kc_sleep_ms((int)slice);
        waited += slice;
    }
    return -ETIME;
}
