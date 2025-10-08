// SPDX-License-Identifier: BSD-3-Clause
/* Archived experimental async actor benchmark. See README.md. */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <stdatomic.h>

#include "kcoro.h"
#include "kc_actor_bench.h"

#ifdef KC_SCHED
#include "kcoro_sched.h"
#include "kcoro_async.h"
#include "kcoro_task.h"
#endif

/* Forward declaration preserved for archival */
int run_actor_benchmark_async(const kc_bench_config_t* config);

/*
 * Note: Original implementation was moved here for archival.
 * If needed for experimentation, copy the previous source content
 * and build with KC_SCHED=1 KC_ASYNC_EXPERIMENTAL=1.
 */

int run_actor_benchmark_async(const kc_bench_config_t* config)
{
    (void)config;
    fprintf(stderr, "run_actor_benchmark_async: archived experiment; see lab/experimental_async/README.md\n");
    return -1;
}
