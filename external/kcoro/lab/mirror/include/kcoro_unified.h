// SPDX-License-Identifier: BSD-3-Clause
#pragma once

/*
 * Unified kcoro API
 * -----------------
 * Purpose
 *   One include for the core coroutine and scheduler functionality. Handy for
 *   applications that want the essentials without remembering individual files.
 *
 * Status & install guidance
 *   - This header simply aggregates stable headers; it is safe to install.
 *   - Experimental task layers were removed to keep the surface crisp until a
 *     full structured task system lands.
 *
 * Notes
 *   The stable primitives here are sufficient to build high‑performance
 *   pipelines and actors while preserving clear semantics.
 */


#include "kcoro.h"
#include "kcoro_sched.h"

/* NOTE: Historical experimental task APIs (kc_task_yield, kc_task_sleep_ms, …)
 * were removed as incomplete and unused. See src/kcoro/docs/components/developer/MAINTENANCE.md
 * for planned structured task API design notes.
 */

/*
 * Documentation:
 * 
 * Basic usage:
 *   #include "kcoro_unified.h"
 *   
 *   void my_task(void* arg) {
 *       for (int i = 0; i < 10; i++) {
 *           printf("Working... %d\n", i);
 *           kc_yield();         // Let other tasks run
 *           kc_sleep_ms(100);   // Sleep for 100ms
 *       }
 *   }
 * 
 * Advanced usage previously referenced task‑aware APIs which are now removed.
 * Future work: structured task contexts, cancellation integration, and a
 * scheduler timer wheel to eliminate any coarse sleeps in tooling.
 */
