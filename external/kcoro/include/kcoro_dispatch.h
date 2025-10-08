// SPDX-License-Identifier: BSD-3-Clause
#pragma once

#include <stddef.h>
#include "kcoro_sched.h"
#include "kcoro_core.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct kc_dispatcher kc_dispatcher_t;

kc_dispatcher_t* kc_dispatcher_retain(kc_dispatcher_t* dispatcher);
void kc_dispatcher_release(kc_dispatcher_t* dispatcher);

kc_dispatcher_t* kc_dispatcher_default(void);
kc_dispatcher_t* kc_dispatcher_io(void);
kc_dispatcher_t* kc_dispatcher_new(int workers);

kc_sched_t* kc_dispatcher_scheduler(kc_dispatcher_t* dispatcher);

int kc_dispatcher_spawn(kc_dispatcher_t* dispatcher, kc_task_fn fn, void* arg);
int kc_dispatcher_spawn_co(kc_dispatcher_t* dispatcher,
                           kcoro_fn_t fn,
                           void* arg,
                           size_t stack_size,
                           kcoro_t** out_co);

#ifdef __cplusplus
}
#endif
