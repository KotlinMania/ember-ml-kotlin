// SPDX-License-Identifier: BSD-3-Clause
#pragma once

/*
 * -----------------------------------------------------------------------------
 * kcoro build‑time tunables (portable)
 * -----------------------------------------------------------------------------
 * Purpose
 *   These macros control conservative defaults for cancellation slicing,
 *   initial ring capacities, and (optionally) lab IPC transport sizing.
 *   Runtime policy lives in kcoro_config_runtime.h.
 *
 * Core vs. tooling distinction
 *   Used by core library:
 *     - KCORO_CANCEL_SLICE_MS: slice cadence for cancellable ops when backends
 *       lack a native cancellable primitive.
 *     - KCORO_UNLIMITED_INIT_CAP: initial capacity when KC_UNLIMITED first
 *       grows.
 *
 *   Used by lab/tools (not by core):
 *     - KCORO_IPC_BACKLOG: listen backlog in sample IPC tool.
 *     - KCORO_IPC_MAX_TLV_ELEM: TLV element size bound in IPC transport.
 *
 * Production policy
 *   If you export an “installed” header set, you may keep this file as part of
 *   the public surface; the tool‑only tunables are harmless. For maximum
 *   separation, you can relocate IPC‑specific defines under the IPC module’s
 *   own header without affecting the core.
 */

/* Cancellation slice in kc_chan_*_c (ms):
 * Cancellable ops poll cancellation at this cadence when the backend lacks a
 * native cancellable primitive. Lower values react faster at the cost of more
 * wakeups. */
/**
 * Cancellable op slice (ms). When backends lack native cancellation, _c
 * variants poll at this cadence to react to tokens without busy waiting.
 */
#ifndef KCORO_CANCEL_SLICE_MS
#define KCORO_CANCEL_SLICE_MS 50
#endif

/* Initial capacity for UNLIMITED channels when first growth triggers. */
/**
 * Initial ring capacity chosen when KC_UNLIMITED first grows.
 * A conservative default that avoids early reallocations while keeping
 * memory overhead modest; override at build time if needed.
 */
#ifndef KCORO_UNLIMITED_INIT_CAP
#define KCORO_UNLIMITED_INIT_CAP 64
#endif

/* IPC listen backlog (tooling).
 * Not used by the core; affects only the optional IPC samples. */
/**
 * Listen backlog for the IPC lab tools.
 * Not used by the core library; affects only the sample POSIX IPC server
 * shipped in the repository.
 */
#ifndef KCORO_IPC_BACKLOG
#define KCORO_IPC_BACKLOG 8
#endif

/* Max single TLV element payload (transport uses uint16 length). */
/**
 * Maximum single TLV payload size for the IPC transport.
 * Constrained by the uint16 length field; tooling can lower it to bound
 * allocation size during fuzzing or stress.
 */
#ifndef KCORO_IPC_MAX_TLV_ELEM
#define KCORO_IPC_MAX_TLV_ELEM 65535
#endif
