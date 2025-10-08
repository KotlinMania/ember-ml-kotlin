// SPDX-License-Identifier: BSD-3-Clause
#pragma once

/*
 * kcoro porting layer (header‑only contract)
 * -----------------------------------------
 * Purpose
 *   Define the minimum set of synchronization and error macros the core needs,
 *   without importing platform headers into public API files. Ports implement
 *   these macros and types, allowing the core to remain strictly ANSI/POSIX.
 *
 * Optional items (implementation‑dependent)
 *   - KC_CLOCK_REALTIME: supply if your port needs a clock identifier for
 *     timed waits; otherwise ignore.
 *   - KC_ALLOC/KC_FREE: supply if you want to route allocations; otherwise the
 *     core uses malloc/free directly.
 *
 * Contract (must provide before including kcoro sources that use sync):
 *   Types:  KC_MUTEX_T, KC_COND_T
 *   Init:   KC_MUTEX_INIT, KC_COND_INIT, KC_MUTEX_DESTROY, KC_COND_DESTROY
 *   Locks:  KC_MUTEX_LOCK, KC_MUTEX_UNLOCK
 *   Waits:  KC_COND_WAIT, KC_COND_TIMEDWAIT_ABS, KC_COND_SIGNAL, KC_COND_BROADCAST
 *   Errors: KC_EAGAIN, KC_EPIPE, KC_ETIME, KC_ECANCELED (negative values)
 *
 * Install guidance
 *   - This header is part of the production public API and should be installed.
 *     A small POSIX reference port lives in kcoro/port/posix.h.
 */

// SPDX-License-Identifier: BSD-3-Clause
#pragma once
/**
 * @file kcoro_port.h
 * @brief Port selection indirection for kcoro core (OS‑neutral interface).
 *
 * Purpose
 *   Keep the public surface strictly portable while allowing a small, isolated
 *   "port" header to provide mutex/condvar/time primitives. The default is a
 *   POSIX implementation; projects may override with their own by defining
 *   KCORO_PORT_HEADER to a compatible header path at build time.
 *
 * Notes
 *   - This header intentionally avoids naming any operating system. The core
 *     library consumes only ANSI C and POSIX‑style primitives via the port
 *     layer. Any platform‑specific code belongs under `port/` (or out‑of‑tree).
 */

#ifndef KCORO_PORT_HEADER
#  include "../port/posix.h"
#else
#  include KCORO_PORT_HEADER
#endif
