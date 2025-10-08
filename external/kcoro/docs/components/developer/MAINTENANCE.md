# Maintenance & DRY Audit

This file collects DRY audit findings and recommended actions specific to the `kcoro` subtree.

---

## DRY Audit Summary (kcoro Only)

Preamble: Focusing the DRY audit strictly on the `kcoro` subtree, the following consolidated findings and recommended action plan were captured.

### 1. Parallel / Legacy Implementations
Identified archived or alternative implementations that still create cognitive load:
- `lab/archive/` (legacy pthread, legacy_ctx, experimental_async, scheduler_broken): archival but still contain function names (e.g., `kc_sched_init`, `kc_spawn`) that shadow current ones. Risk: accidental inclusion or grep noise.
- `tests_archive/` duplicates scheduler and channel stress tests similar to new slim `tests/` targets.
- `examples/legacy_client/` includes headers from external legacy integration (diverges from unified API story).

Action options:
1. Add a top-level `ARCHIVE_NOTICE` file in each archived directory clarifying "not built, do not modify, subject to pruning".
2. Rename key legacy symbols in archived code (optional) or wrap the entire file bodies with `#if 0` blocks plus a comment.
3. For legacy client examples, replace external includes with stubs or move examples beside those sources (or mark as external integration example).

Priority: Medium (documentation / guard).

### 2. Duplicate Public Entry Points
Current public scheduler API wrappers labeled “legacy names”:
- Comments in `kc_sched.c` mark “legacy names preserved”.
- Potentially overlapping future structured API (jobs/cancellation) may introduce new spawn semantics.

Recommendation: Plan a deprecation path:
- Introduce a standardized naming layer (`kc_launch`) aliasing `kc_spawn` now (if not already).
- Add `__attribute__((deprecated))` only after new structured job launcher (`kc_job_launch`) exists—note this for future deprecation.

Priority: Low.

### 3. Conditional Compilation / Alternate Paths
Macros affecting multiple code paths:
- (Removed) `KCORO_DISABLE_AUTO_SCHED`, `KCORO_SINGLE_THREAD_COOP`: Deleted to enforce a single optimal scheduler initialization path.
- `KCORO_DEBUG` runtime env: Debug instrumentation toggled at runtime (no compile-time forks). Replace remaining compile-time debug macros.
- Channel zero-copy macros: `KC_CHAN_CAP_ZERO_COPY`, `KC_ZREF_MIN_SIZE` are configuration thresholds, not alternate algorithms.
- Arch portability macros (`_GNU_SOURCE`, `__x86_64__`, `__i386__`) in benches—acceptable.

Conclusion: No redundant compiled “second implementation” remains, just feature toggles. Consider central configuration header to reduce scattering.

Priority: Low.

### 4. Build Script Redundancy
Distinct Makefiles with repeated `CFLAGS`/`LDFLAGS` patterns across library, tests, and example Makefiles.

Opportunities:
- Create shared fragment (e.g., `mk/common.mk`) defining `CC`, base `CFLAGS`, `LDFLAGS`.
- Archive Makefile could be simplified to a “not built by default” notice.
- Example Makefiles can include `../../mk/example.mk` to unify flags.

Priority: Medium.

### 5. Tests Duplication / Overlap
Archive tests: scheduler basic, throughput, async bench variants.

Recommendation:
- Curate an “active test list” and migrate any still-needed performance/regression logic into a new `bench/` directory (separate from archive).
- Add CI guard to ensure `tests_archive/` is not compiled unless `ENABLE_ARCHIVE=1`.

Priority: Medium.

### 6. Legacy Context & Async Experiment Code
- `lab/archive/legacy_ctx` and `experimental_async` include macros `KC_SCHED`, `KC_ASYNC_EXPERIMENTAL`.

Mitigation:
- Add a top-of-file compile guard or wrap content in `#if defined(COMPILE_ARCHIVE)`.

Priority: Medium.

### 7. External Integration Example Using Legacy Path
Legacy client examples linking to external trees couples samples to external dependencies.

Options:
- Move legacy clients to `examples/legacy_integration/` or annotate their Makefiles with warning banners.

Priority: Low-Medium.

### 8. Documentation Cross-References to Historical Plan
Headers formerly referenced a historical scheduler plan; references were removed; update remaining references to the current scheduler design sections where appropriate.

Priority: High.

### 9. Potential Over-Split Small Headers
Consider providing `kcoro_unified.h` umbrella header to reduce include scattering and ensure stable API surface.

Priority: Low.

### 10. Logging / Debug Macros Consolidation
Create `kcoro_debug.h` providing category-based debug macros (SCHED_DBG, ZREF_DBG, etc.) and refactor existing ad-hoc macros.

Priority: Low-Medium.

---

## Recommended Action Plan (Prioritized)
1. Update header references (High) — completed where reported.
2. Archive Safeguards (Medium) — add `ARCHIVE_NOTICE.md` files and compile guards.
3. Build System Consolidation (Medium) — create `mk/common.mk` and refactor Makefiles.
4. Active vs Archive Test Separation (Medium) — migrate benches, add `ENABLE_ARCHIVE` guard.
5. Example Integration Cleanup (Low-Med) — move or annotate external integration examples.
6. Debug Macro Unification (Low-Med) — consolidate debug macros.

Quick wins:
- Add `ARCHIVE_NOTICE.md` files (I can add these now).
- Create an exemplar `mk/common.mk` and update one example Makefile as a template (I can do that next if you want).

---

If you want me to implement the quick wins now, say which ones (archive notices / mk/common.mk exemplar). I can apply them immediately and remove the top-level `DRY_AUDIT.md` once you confirm.
