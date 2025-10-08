# kcoro Runtime Configuration

Runtime configuration is provided via a JSON file (default `kcoro_config.json` in the working directory) or a path specified by the environment variable `KCORO_CONFIG`.

If the file is missing or a field is absent, defaults are applied (zero failure).

## Schema

```
{
  "channel": {
    "metrics": {
      "emit_min_ops": <number >=1>,
      "emit_min_ms":  <number >=0>,
      "auto_enable":  <boolean>,
      "pipe_capacity": <number >=1>
    }
  }
}
```

All sections are optional; nested objects can be partially specified.

## Fields

- channel.metrics.emit_min_ops (default: 1024)
  Minimum total (send+recv) operations delta since the last emitted event before a new metrics event is attempted.

- channel.metrics.emit_min_ms (default: 50)
  Minimum elapsed milliseconds since the previous emitted event. Either this time threshold OR `emit_min_ops` being met will trigger emission.

- channel.metrics.auto_enable (default: false)
  When true, every channel created via `kc_chan_make` automatically allocates a metrics pipe and begins emitting events.

- channel.metrics.pipe_capacity (default: 64)
  Buffered capacity (in events) of the auto-created metrics pipe. Events are dropped (not blocking producers) when the pipe is full.

## Loading Behavior

1. First call to any API that needs configuration triggers lazy load.
2. Load order:
   - Explicit path argument (when calling `kc_runtime_config_init(path)`), else
   - Environment variable `KCORO_CONFIG`, else
   - Fallback file name `kcoro_config.json`.
3. File size is capped at 1MB.
4. Unknown keys are skipped (forward-compatible).

## Examples

### Minimal (defaults)
```
{}
```

### Aggressive emission (lower latency metrics)
```
{
  "channel": {
    "metrics": {
      "emit_min_ops": 256,
      "emit_min_ms": 10
    }
  }
}
```

### Always-on metrics for all channels
```
{
  "channel": {
    "metrics": {
      "auto_enable": true,
      "pipe_capacity": 256
    }
  }
}
```

## Accessors

C APIs (declared in `kcoro_config_runtime.h`):
```
int kc_runtime_config_init(const char *path);
int kc_runtime_config_reload(const char *path);
const struct kc_runtime_config* kc_runtime_config_get(void);
```

## Thread Safety & Overhead
- Reads are lock-free after initial load (simple static state).
- Initialization guarded to prevent recursion; reentrant calls are safe/no-op.
- Reload discards previous state and re-parses.

## Error Handling
- Missing file: silently use defaults.
- Malformed numbers or booleans for known fields: ignore field, keep prior value.
- Oversized file (>1MB): defaults retained; `errno` set (EFBIG) internally.

## Future Extensions
Planned fields (not yet implemented):
- `scheduler`: tuning knobs (work-steal batch sizes, parking thresholds)
- `tracing`: enable/disable structured span emission
- `zero_copy`: thresholds for fallback vs. pointer handoff

### (Planned) Zero‑Copy Backend Policy
The active backend is unified as `zref` (handles rendezvous and queued pointer‑descriptor paths). Code selects it implicitly via `kc_chan_make_ptr()` or explicitly via `kc_chan_enable_zero_copy()` + factory binding if needed. A future configuration hook may allow setting policy per process or per channel group. Example (illustrative only):

```
{
  "zcopy": {
    "default_backend": "ptr",          // or "zref"; external adapters may register custom names
    "size_threshold_bytes": 128,        // hint: below this size prefer copy
    "channel_overrides": {
      "telemetry": { "backend": "ptr" },
      "fastpath":  { "backend": "zref" }
    }
  }
}
```

Warning: until this is implemented, selecting the backend is done with the factory APIs in `kcoro_zcopy.h`.

These will follow same additive, non-breaking schema rules.


---

## Build-time flags, environment toggles, and recommended builds (from orphaned_docs)

This section consolidates concrete build-time macros, make variables, and runtime environment toggles that are part of the kcoro snapshot (2025-09-27). Every item maps to existing code or a guarded placeholder.

### Makefile variables & usage
Build kcoro from repo root:

```
make -C src/kcoro
```

Enable optional diagnostics and pass extra compiler flags:

- KCORO_CTX_DIAGNOSTICS=1
  - Adds diagnostics instrumentation fields and compile-time checks. This macro defines `KCORO_CTX_DIAGNOSTICS` for C source files which adds canary and stack bounds fields to `kcoro` and compiles the helper checks.
  - Example: `make -C src/kcoro KCORO_CTX_DIAGNOSTICS=1`

- CFLAGS
  - The top-level Makefile accepts `CFLAGS` and forwards them to sub-makes. Use this to add sanitizer flags (`-fsanitize=address`, `-fno-omit-frame-pointer`, `-O2`) where desirable.
  - Example with ASAN: `make -C src/kcoro CFLAGS="-O1 -g -fsanitize=address"`

Cross-link: see context-switching diagnostics details in `../context-switching/CONTEXT_SWITCHING.md`.

### Runtime environment toggles
- KCORO_DEBUG_CTX_CHECK
  - When set (non-empty and not '0') and the code was compiled with `KCORO_CTX_DIAGNOSTICS`, runtime validation checks will be performed at key context transitions (create, pre/post resume, post-yield). Violations print diagnostics and abort.
  - Example: `export KCORO_DEBUG_CTX_CHECK=1`

Notes:
- `KCORO_CTX_DIAGNOSTICS` without setting `KCORO_DEBUG_CTX_CHECK` enables the fields but keeps runtime checks off — useful for building CI artifacts where runtime overhead is still undesirable but field layout must exist.
- `KCORO_DEBUG_CTX_CHECK` has no effect unless `KCORO_CTX_DIAGNOSTICS` was compiled in.

### Scheduler & Steal Tunables
- `KC_SCHED_STEAL_SCAN_MAX` (compile-time macro)
  - Upper bound on victim deques probed during a steal attempt. Default is `4` if not set. Lower values reduce probe overhead at the cost of potential fairness under heavy skew.
  - Set via compiler defines, e.g. `CFLAGS="-DKC_SCHED_STEAL_SCAN_MAX=8"`.

### Recommended Build Configs
- Development with diagnostics (fast failure & introspection):
```
make -C src/kcoro KCORO_CTX_DIAGNOSTICS=1 CFLAGS="-O0 -g -fsanitize=address"
export KCORO_DEBUG_CTX_CHECK=1
```

- Production (no diagnostics):
```
make -C src/kcoro CFLAGS="-O2 -DNDEBUG"
```

- CI with UBSAN/ASAN (to find subtle memory issues):
```
make -C src/kcoro KCORO_CTX_DIAGNOSTICS=1 CFLAGS="-O1 -g -fsanitize=address,undefined" \
  && export KCORO_DEBUG_CTX_CHECK=1
```

### Sanity Checks
- When running diagnostic-enabled binaries, run small smoke tests first to ensure aborts are expected and diagnostic prints are visible.
- Use `ulimit -c unlimited` to capture core files on abort for post-mortem analysis.

### Where to Add New Flags
- New config should be a pair: a compile-time macro and a runtime gate where checks are invasive. This pattern ensures production builds remain lean and CI/dev builds can be noisy and safe.

### Deprecated / Not-Exposed Flags
- Do not rely on legacy `KCORO2_INTERNAL_CTX_DEBUG`. It was retired in prior refactor and replaced by the `KCORO_CTX_DIAGNOSTICS` / `KCORO_DEBUG_CTX_CHECK` pattern.

### Future (Roadmap) Controls
- When scheduler is ported into kcoro, we will expose runtime knobs for worker counts (runtime init flag), and optionally an environment variable to force single-threaded deterministic mode for tests.
- Channel & zero-copy backends will introduce capability flags (e.g., `KC_CHAN_CAP_ZERO_COPY`) and backend registration mechanisms; staged docs and flags will be added when ports are implemented.

Generated: 2025-09-27
