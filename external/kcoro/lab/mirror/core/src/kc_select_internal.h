// SPDX-License-Identifier: BSD-3-Clause
#pragma once

#include "../../include/kcoro.h"
#include "../../include/kcoro_core.h"

struct kc_select_clause_internal {
    enum kc_select_clause_kind kind;
    kc_chan_t *chan;
    union {
        void       *recv_buf;
        const void *send_buf;
    } data;
};

enum kc_select_state {
    KC_SELECT_REG = 0,
    KC_SELECT_WIN = 1,
    KC_SELECT_CANCELED = 2,
    KC_SELECT_TIMED_OUT = 3
};

struct kc_select {
    struct kc_select_clause_internal *clauses;
    int count;
    int capacity;
    const kc_cancel_t *cancel;
    kcoro_t *waiter;
    _Atomic int state;
    _Atomic int winner_index;
    _Atomic int result;
};

void kc_select_set_waiter(kc_select_t *sel, kcoro_t *co);
int  kc_select_has_waiter(const kc_select_t *sel);
int  kc_select_try_complete(kc_select_t *sel, int clause_index, int result);
void kc_select_get_result(const kc_select_t *sel, int *clause_index, int *result);
void* kc_select_recv_buffer(kc_select_t *sel, int clause_index);
const void* kc_select_send_buffer(kc_select_t *sel, int clause_index);
void kc_select_reset_state(kc_select_t *sel);
kcoro_t* kc_select_waiter(const kc_select_t *sel);
int  kc_select_is_completed(const kc_select_t *sel);

int  kc_chan_select_register_recv(kc_chan_t *chan, kc_select_t *sel, int clause_index);
int  kc_chan_select_register_send(kc_chan_t *chan, kc_select_t *sel, int clause_index);
void kc_chan_select_cancel(kc_chan_t *chan, kc_select_t *sel, int clause_index, enum kc_select_clause_kind kind);
