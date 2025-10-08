// SPDX-License-Identifier: BSD-3-Clause
#pragma once

/*
 * kc_blocking.h - Blocking offload & non-blocking IO wait APIs
 *
 * Design reference: SCHEDULER_DESIGN.md – Blocking & IO Integration (legacy V2 draft removed).
 * Implementation phases: S2 (basic offload), S4 (epoll IO), S5 (enhanced cancel).
 */

#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef int (*kc_block_fn)(void *arg); /* returns status code */

/* Offload a blocking function to the blocking pool.
 * timeout_ms < 0 => infinite.
 * Returns 0 on success and stores function return value into *out_status.
 * Returns -ETIMEDOUT if not started by deadline or timed out waiting.
 */
int kc_block_call(kc_block_fn fn, void *arg, long timeout_ms, int *out_status);

/* Wait until fd readable / writable or timeout/cancel.
 * timeout_ms < 0 => infinite.
 * Returns 0 on readiness, -ETIMEDOUT, -KC_ECANCELED, or errno negative.
 */
int kc_io_wait_readable(int fd, long timeout_ms);
int kc_io_wait_writable(int fd, long timeout_ms);

/* Convenience read/write wrappers (attempt full transfer unless error). */
ssize_t kc_io_read(int fd, void *buf, size_t len, long timeout_ms);
ssize_t kc_io_write(int fd, const void *buf, size_t len, long timeout_ms);

#ifdef __cplusplus
}
#endif
