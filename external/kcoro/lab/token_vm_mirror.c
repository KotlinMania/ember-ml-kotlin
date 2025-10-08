// SPDX-License-Identifier: BSD-3-Clause
// token_vm_mirror.c -- miniature coroutine runtime using token VM

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>

#include "token_vm.h"

typedef struct mirror_coroutine mirror_coroutine;
typedef void (*mirror_func)(mirror_coroutine *, void *);

typedef enum {
    MIRROR_READY = 0,
    MIRROR_RUNNING,
    MIRROR_BLOCKED,
    MIRROR_DONE
} mirror_status;

typedef struct {
    mirror_coroutine *items[64];
    size_t head;
    size_t tail;
    size_t count;
} mirror_queue;

typedef struct mirror_scheduler {
    mirror_queue ready;
} mirror_scheduler;

struct mirror_coroutine {
    mirror_scheduler *sched;
    mirror_status     status;
    uint64_t          regs[KC_REG_MAX];
    kc_token          program[2];
    mirror_func       func;
    void             *arg;
};

static void queue_init(mirror_queue *q)
{
    memset(q, 0, sizeof(*q));
}

static void queue_push(mirror_queue *q, mirror_coroutine *co)
{
    if (q->count == sizeof(q->items) / sizeof(q->items[0])) {
        fprintf(stderr, "[mirror] ready queue overflow\n");
        abort();
    }
    q->items[q->tail] = co;
    q->tail = (q->tail + 1) % (sizeof(q->items) / sizeof(q->items[0]));
    q->count++;
}

static mirror_coroutine *queue_pop(mirror_queue *q)
{
    if (q->count == 0) return NULL;
    mirror_coroutine *co = q->items[q->head];
    q->head = (q->head + 1) % (sizeof(q->items) / sizeof(q->items[0]));
    q->count--;
    return co;
}

static void mirror_sched_init(mirror_scheduler *sched)
{
    queue_init(&sched->ready);
}

static void mirror_sched_push_ready(mirror_scheduler *sched, mirror_coroutine *co)
{
    queue_push(&sched->ready, co);
}

static void mirror_yield(mirror_coroutine *co)
{
    if (co->status == MIRROR_RUNNING) {
        co->status = MIRROR_READY;
    }
}

static void mirror_complete(mirror_coroutine *co)
{
    co->status = MIRROR_DONE;
}

static void mirror_trampoline(void)
{
    mirror_coroutine *co = NULL;
#if defined(__aarch64__)
    __asm__ volatile("mov %0, x19" : "=r"(co));
#elif defined(__x86_64__)
    __asm__ volatile("mov %%r12, %0" : "=r"(co));
#endif
    if (!co) return;

    co->status = MIRROR_RUNNING;
    co->func(co, co->arg);
    if (co->status == MIRROR_RUNNING) {
        co->status = MIRROR_DONE;
    }
}

static void mirror_spawn(mirror_scheduler *sched, mirror_coroutine *co,
                         mirror_func fn, void *arg)
{
    memset(co, 0, sizeof(*co));
    co->sched = sched;
    co->func  = fn;
    co->arg   = arg;
    co->status = MIRROR_READY;
#if defined(__aarch64__)
    uint64_t cur_sp, cur_fp;
    __asm__ volatile("mov %0, sp" : "=r"(cur_sp));
    __asm__ volatile("mov %0, x29" : "=r"(cur_fp));
    co->regs[KC_REG_SP] = cur_sp;
    co->regs[KC_REG_FP] = cur_fp;
#elif defined(__x86_64__)
    uint64_t cur_sp, cur_bp;
    __asm__ volatile("mov %%rsp, %0" : "=r"(cur_sp));
    __asm__ volatile("mov %%rbp, %0" : "=r"(cur_bp));
    co->regs[KC_REG_SP] = cur_sp;
    co->regs[KC_REG_FP] = cur_bp;
#endif
    co->program[0] = (kc_token){ KC_OP_LOAD_IMM, KC_REG_PTR, 0,
                                 (uint64_t)(uintptr_t)co };
    co->program[1] = (kc_token){ KC_OP_END, 0, 0,
                                 (uint64_t)(uintptr_t)mirror_trampoline };
    mirror_sched_push_ready(sched, co);
}

typedef struct {
    void  **slots;
    size_t  capacity;
    size_t  head;
    size_t  tail;
    size_t  count;
} mirror_channel;

static int mirror_chan_init(mirror_channel *ch, size_t capacity)
{
    memset(ch, 0, sizeof(*ch));
    ch->slots = calloc(capacity, sizeof(void *));
    if (!ch->slots) return -1;
    ch->capacity = capacity;
    return 0;
}

static void mirror_chan_destroy(mirror_channel *ch)
{
    free(ch->slots);
    memset(ch, 0, sizeof(*ch));
}

static int mirror_chan_send(mirror_channel *ch, void *ptr)
{
    if (ch->count == ch->capacity) return 0;
    ch->slots[ch->tail] = ptr;
    ch->tail = (ch->tail + 1) % ch->capacity;
    ch->count++;
    return 1;
}

static int mirror_chan_recv(mirror_channel *ch, void **out)
{
    if (ch->count == 0) return 0;
    void *ptr = ch->slots[ch->head];
    ch->head = (ch->head + 1) % ch->capacity;
    ch->count--;
    *out = ptr;
    return 1;
}

static void mirror_scheduler_run(mirror_scheduler *sched)
{
    while (sched->ready.count > 0) {
        mirror_coroutine *co = queue_pop(&sched->ready);
        if (!co) continue;
        void *fn = kc_vm_execute(co->program, co->regs);
        if (fn) {
            kc_vm_apply(co->regs, fn);
        }
        switch (co->status) {
        case MIRROR_READY:
            mirror_sched_push_ready(sched, co);
            break;
        case MIRROR_BLOCKED:
            // remain blocked until external wake
            break;
        case MIRROR_DONE:
            break;
        case MIRROR_RUNNING:
            // should not happen; treat as done to avoid spin
            co->status = MIRROR_DONE;
            break;
        }
    }
}

struct producer_state {
    mirror_channel *chan;
    uint64_t       *values;
    size_t          next;
    size_t          limit;
};

static void producer_fn(mirror_coroutine *co, void *arg)
{
    struct producer_state *ps = arg;
    if (ps->next >= ps->limit) {
        mirror_complete(co);
        return;
    }

    uint64_t *value = &ps->values[ps->next];
    *value = (uint64_t)ps->next;
    if (!mirror_chan_send(ps->chan, value)) {
        mirror_yield(co);
        return;
    }

    ps->next++;
    mirror_yield(co);
}

struct consumer_state {
    mirror_channel *chan;
    size_t          target;
    size_t          received;
    uint64_t        sum;
};

static void consumer_fn(mirror_coroutine *co, void *arg)
{
    struct consumer_state *cs = arg;
    if (cs->received >= cs->target) {
        mirror_complete(co);
        return;
    }

    void *payload = NULL;
    if (!mirror_chan_recv(cs->chan, &payload)) {
        mirror_yield(co);
        return;
    }

    uint64_t *value = (uint64_t *)payload;
    cs->sum += *value;
    cs->received++;
    mirror_yield(co);
}

int main(void)
{
    mirror_scheduler sched;
    mirror_sched_init(&sched);

    mirror_channel chan;
    if (mirror_chan_init(&chan, 8) != 0) {
        fprintf(stderr, "failed to init channel\n");
        return 1;
    }

    const size_t producer_count = 2;
    const size_t items_per_producer = 1000;
    const size_t consumer_count = 2;

    mirror_coroutine producers[producer_count];
    mirror_coroutine consumers[consumer_count];

    struct producer_state prod_state[producer_count];
    struct consumer_state cons_state[consumer_count];

    for (size_t i = 0; i < producer_count; ++i) {
        prod_state[i].chan   = &chan;
        prod_state[i].limit  = items_per_producer;
        prod_state[i].next   = 0;
        prod_state[i].values = calloc(items_per_producer, sizeof(uint64_t));
        if (!prod_state[i].values) {
            fprintf(stderr, "alloc fail\n");
            return 1;
        }
        mirror_spawn(&sched, &producers[i], producer_fn, &prod_state[i]);
    }

    for (size_t i = 0; i < consumer_count; ++i) {
        cons_state[i].chan     = &chan;
        cons_state[i].target   = items_per_producer;
        cons_state[i].received = 0;
        cons_state[i].sum      = 0;
        mirror_spawn(&sched, &consumers[i], consumer_fn, &cons_state[i]);
    }

    mirror_scheduler_run(&sched);

    uint64_t total_sum = 0;
    size_t total_recv = 0;
    for (size_t i = 0; i < consumer_count; ++i) {
        printf("consumer[%zu] received=%zu sum=%" PRIu64 "\n",
               i, cons_state[i].received, cons_state[i].sum);
        total_sum += cons_state[i].sum;
        total_recv += cons_state[i].received;
    }

    printf("total received=%zu total sum=%" PRIu64 "\n", total_recv, total_sum);

    for (size_t i = 0; i < producer_count; ++i) {
        free(prod_state[i].values);
    }
    mirror_chan_destroy(&chan);
    return 0;
}
