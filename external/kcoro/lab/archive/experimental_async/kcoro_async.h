// SPDX-License-Identifier: BSD-3-Clause
#pragma once

/*
 * EXPERIMENTAL ASYNC API - NOT YET IMPLEMENTED
 * 
 * ASYNC-ONLY: This header requires KC_SCHED=1 AND KC_ASYNC_EXPERIMENTAL=1.
 * No fallback implementations - fails fast at compile time.
 * 
 * This header defines the future async/await API for kcoro channels.
 * These functions are NOT yet implemented and will cause linker errors.
 * 
 * Current status: Scaffolding only - implementations coming soon
 */

#ifndef KC_SCHED
#error "kcoro_async.h requires KC_SCHED=1 - build with scheduler support"
#endif

#ifndef KC_ASYNC_EXPERIMENTAL  
#error "kcoro_async.h requires KC_ASYNC_EXPERIMENTAL=1 - define to acknowledge experimental status"
#endif

#include "kcoro.h"
#include "kcoro_task.h"

/*
 * Coroutine-aware channel operations
 * 
 * These functions implement true async/await patterns:
 * - When operations would block, they yield the current task
 * - Tasks are resumed when the operation can proceed
 * - No thread blocking - enables M:N scheduling
 */

/* Async channel send - yields if channel full, resumes when space available */
int kc_chan_send_async(kc_chan_t* ch, const void* data);

/* Async channel receive - yields if no data, resumes when data available */
int kc_chan_recv_async(kc_chan_t* ch, void* data);

/* Try async send - returns KC_EAGAIN if would block, doesn't yield */
int kc_chan_try_send_async(kc_chan_t* ch, const void* data);

/* Try async receive - returns KC_EAGAIN if would block, doesn't yield */
int kc_chan_try_recv_async(kc_chan_t* ch, void* data);

/* 
 * Channel with async/await semantics and timeout
 * Returns:
 * - 0: Success
 * - KC_EAGAIN: Would block (for try_ variants)
 * - KC_ETIME: Timeout expired
 * - KC_EPIPE: Channel closed
 * - KC_ECANCELED: Task was canceled
 */
int kc_chan_send_timeout_async(kc_chan_t* ch, const void* data, int timeout_ms);
int kc_chan_recv_timeout_async(kc_chan_t* ch, void* data, int timeout_ms);
