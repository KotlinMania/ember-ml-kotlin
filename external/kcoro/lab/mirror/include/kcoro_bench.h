// SPDX-License-Identifier: BSD-3-Clause
#pragma once

#include <stddef.h>
#include <stdint.h>
#include "kcoro.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
 * -----------------------------------------------------------------------------
 * Lab/Tooling Header (not core API)
 * -----------------------------------------------------------------------------
 * Purpose
 *   Minimal harness to drive channel microbenchmarks from lab tools and the
 *   TUI. This header is not part of the core library’s stability promise and
 *   is typically not installed. It exists to keep bench code decoupled from
 *   the library surface while remaining nearby for maintainers.
 *
 * Install guidance
 *   Do not install as part of the production public headers. Tools in this
 *   repository include it directly from the tree.
 */

/** Opaque handle returned by kc_bench_chan_start(). */
typedef struct kc_bench_handle kc_bench_handle_t; /* opaque */

typedef struct kc_bench_params {
    int     kind;               /* enum kc_kind or >0 buffered capacity alias (KC_BUFFERED typical) */
    size_t  capacity;           /* queue capacity for buffered */
    int     producers;          /* number of producers */
    int     consumers;          /* number of consumers */
    int     packets_per_cycle;  /* number of sends per producer per cycle */
    int     spin_iters;         /* spin attempts before yield on EAGAIN */
    size_t  packet_size;        /* logical payload size (bytes) for ptr-mode */
    int     pointer_mode;       /* 1 = pointer-descriptor mode, 0 = int payload */
} kc_bench_params_t;

/* Starts a channel benchmark workload as coroutines on kc_sched_default().
 * Returns a handle for shutdown and the channel pointer used for stats. */
int kc_bench_chan_start(const kc_bench_params_t *p,
                        kc_bench_handle_t **out_handle,
                        kc_chan_t **out_chan);

/* Signals shutdown, closes channel, joins coroutines, and destroys resources. */
void kc_bench_chan_stop(kc_bench_handle_t *h);

#ifdef __cplusplus
}
#endif
