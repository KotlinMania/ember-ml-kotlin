// SPDX-License-Identifier: BSD-3-Clause
#pragma once
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif

/*
 * -----------------------------------------------------------------------------
 * Runtime configuration (JSON; optional)
 * -----------------------------------------------------------------------------
 * Purpose
 *   Provide a minimal, dependency‑free way to adjust a few runtime knobs. The
 *   parser is deliberately tiny and schema‑restricted for predictability.
 *
 * Optional by design
 *   Applications may ignore this facility entirely; defaults are safe. When
 *   used, values are loaded once (or on reload), cached, and queried cheaply.
 *
 * Schema (see CONFIGURATION.md) — example snippet:
 * {
 *   "channel": {"metrics": {
 *       "emit_min_ops":  128,
 *       "emit_min_ms":   250,
 *       "auto_enable":   true,
 *       "pipe_capacity": 4096
 *   }}
 * }
 *
 * Install guidance
 *   - This header is part of the production public API and should be installed.
 */

struct kc_runtime_config {
    unsigned long chan_metrics_emit_min_ops;     /* >=1 */
    long          chan_metrics_emit_min_ms;      /* >=0 */
    int           chan_metrics_auto_enable;      /* boolean */
    size_t        chan_metrics_pipe_capacity;    /* >=1 */
};

/* Initialize from path (NULL => env KCORO_CONFIG, else fallback "kcoro_config.json").
 * Safe to call multiple times (idempotent); returns 0 or -errno. */
int kc_runtime_config_init(const char *path);
/* Reload forcibly (drops previous, re-parses). */
int kc_runtime_config_reload(const char *path);
/* Access current config (initializes lazily with defaults if not yet loaded). */
const struct kc_runtime_config* kc_runtime_config_get(void);

#ifdef __cplusplus
}
#endif
