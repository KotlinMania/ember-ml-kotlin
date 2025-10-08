// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <assert.h>
#include <stdatomic.h>
#include <time.h>

#include "../include/kcoro.h"
#include "../include/kcoro_sched.h"

struct select_ctx {
    kc_chan_t *chan_a;
    kc_chan_t *chan_b;
    atomic_int start_select;
    atomic_int done;
    int expect_value;
    int selected_index;
    int rc;
    int value;
};

static void sleep_millis(int ms)
{
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (long)(ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

static void wait_for_flag(atomic_int *flag)
{
    while (atomic_load_explicit(flag, memory_order_acquire) == 0) {
        kcoro_yield();
    }
}

static void select_recv_task(void *arg)
{
    struct select_ctx *ctx = (struct select_ctx*)arg;
    kc_select_t *sel = NULL;
    int rc = kc_select_create(&sel, NULL);
    assert(rc == 0 && sel);

    int out_a = 0;
    int out_b = 0;

    wait_for_flag(&ctx->start_select);

    rc = kc_select_add_recv(sel, ctx->chan_a, &out_a);
    assert(rc == 0);
    rc = kc_select_add_recv(sel, ctx->chan_b, &out_b);
    assert(rc == 0);

    int sel_index = -1;
    int op_result = 0;
    rc = kc_select_wait(sel, -1, &sel_index, &op_result);

    ctx->rc = rc;
    ctx->selected_index = sel_index;
    ctx->value = (sel_index == 0) ? out_a : out_b;
    atomic_store_explicit(&ctx->done, 1, memory_order_release);

    kc_select_destroy(sel);
}

static void sender_immediate_task(void *arg)
{
    struct select_ctx *ctx = (struct select_ctx*)arg;
    int value = ctx->expect_value;
    int rc = kc_chan_send(ctx->chan_b, &value, -1);
    assert(rc == 0);
    atomic_store_explicit(&ctx->start_select, 1, memory_order_release);
}

static void sender_delayed_task(void *arg)
{
    struct select_ctx *ctx = (struct select_ctx*)arg;
    atomic_store_explicit(&ctx->start_select, 1, memory_order_release);
    kc_sleep_ms(10);
    int value = ctx->expect_value;
    int rc = kc_chan_send(ctx->chan_a, &value, -1);
    assert(rc == 0);
}

static void run_select_immediate(void)
{
    printf("[test] select_immediate: start\n");
    kc_sched_opts_t opts = { .workers = 2 };
    kc_sched_t *sched = kc_sched_init(&opts);
    assert(sched);

    kc_chan_t *chan_a = NULL;
    kc_chan_t *chan_b = NULL;
    assert(kc_chan_make(&chan_a, KC_BUFFERED, sizeof(int), 1) == 0);
    assert(kc_chan_make(&chan_b, KC_BUFFERED, sizeof(int), 1) == 0);

    struct select_ctx ctx = {
        .chan_a = chan_a,
        .chan_b = chan_b,
        .expect_value = 123,
    };
    atomic_init(&ctx.start_select, 0);
    atomic_init(&ctx.done, 0);

    assert(kc_spawn_co(sched, select_recv_task, &ctx, 0, NULL) == 0);
    assert(kc_spawn_co(sched, sender_immediate_task, &ctx, 0, NULL) == 0);

    while (atomic_load_explicit(&ctx.done, memory_order_acquire) == 0) {
        sleep_millis(1);
    }

    assert(ctx.rc == 0);
    assert(ctx.selected_index == 1);
    assert(ctx.value == ctx.expect_value);

    kc_chan_destroy(chan_a);
    kc_chan_destroy(chan_b);
    kc_sched_shutdown(sched);
    printf("[test] select_immediate: OK\n");
}

static void run_select_suspend(void)
{
    printf("[test] select_suspend: start\n");
    kc_sched_opts_t opts = { .workers = 2 };
    kc_sched_t *sched = kc_sched_init(&opts);
    assert(sched);

    kc_chan_t *chan_a = NULL;
    kc_chan_t *chan_b = NULL;
    assert(kc_chan_make(&chan_a, KC_RENDEZVOUS, sizeof(int), 0) == 0);
    assert(kc_chan_make(&chan_b, KC_RENDEZVOUS, sizeof(int), 0) == 0);

    struct select_ctx ctx = {
        .chan_a = chan_a,
        .chan_b = chan_b,
        .expect_value = 321,
    };
    atomic_init(&ctx.start_select, 0);
    atomic_init(&ctx.done, 0);

    assert(kc_spawn_co(sched, select_recv_task, &ctx, 0, NULL) == 0);
    assert(kc_spawn_co(sched, sender_delayed_task, &ctx, 0, NULL) == 0);

    while (atomic_load_explicit(&ctx.done, memory_order_acquire) == 0) {
        sleep_millis(1);
    }

    assert(ctx.rc == 0);
    assert(ctx.selected_index == 0);
    assert(ctx.value == ctx.expect_value);

    kc_chan_destroy(chan_a);
    kc_chan_destroy(chan_b);
    kc_sched_shutdown(sched);
    printf("[test] select_suspend: OK\n");
}

int main(void)
{
    run_select_immediate();
    run_select_suspend();
    printf("[test] select_basic: OK\n");
    return 0;
}
