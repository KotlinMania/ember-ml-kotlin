Updated todo list

Searched text for `_v2|_v3|legacy|deprecated` (`**/coroutines/kcoro/**`), 93 results

Starting (10/13) *Detect duplicate function definitions (kcoro)*

Searched text for `kc_spawn\(` (`**/coroutines/kcoro/**`), 14 results

Searched text for `kc_sched_init\(` (`**/coroutines/kcoro/**`), 15 results

Starting (11/13) *Inventory conditional compile flags (kcoro)*

Searched text for `#if` (`**/coroutines/kcoro/**`), 88 results

Starting (12/13) *Identify redundant build scripts (kcoro)*

Searched for files matching `**/coroutines/kcoro/**/Makefile`, 18 matches

Read [](file:///media/psf/emberstuff/lumen/ai-whisperers-enid-ml/coroutines/kcoro/user/Makefile)

Read [](file:///media/psf/emberstuff/lumen/ai-whisperers-enid-ml/coroutines/kcoro/tests/Makefile)

Read [](file:///media/psf/emberstuff/lumen/ai-whisperers-enid-ml/coroutines/kcoro/tests_archive/Makefile)

Starting (13/13) *Report DRY findings & recommendations (kcoro)*

Preamble: Focusing the DRY audit strictly on the `kcoro` subtree, I’ve gathered duplication signals and am ready to deliver a consolidated findings + action plan.

## DRY Audit (kcoro Only)

### 1. Parallel / Legacy Implementations
Identified archived or alternative implementations that can still create cognitive load:
- `lab/archive/` (legacy pthread, legacy_ctx, experimental_async, scheduler_broken): Clearly archival but still contain function names (`kc_sched_init`, `kc_spawn`) that shadow current ones. Risk: accidental inclusion or grep noise.
- `tests_archive/` duplicates scheduler and channel stress tests similar to new slim `tests/` targets.
- `examples/legacy_client/` includes headers from external legacy integration (diverges from unified API story).

Action options:
1. Add a top-level `ARCHIVE_NOTICE` file in each archived directory clarifying “not built, do not modify, subject to pruning”.
2. Rename key legacy symbols in archived code (optional) or wrap the entire file bodies with `#if 0` blocks plus a comment.
3. For legacy client examples, replace external includes with stubs or move examples beside those sources (or mark as external integration examples).

Priority: Medium (documentation / guard), since not compiled by default.

### 2. Duplicate Public Entry Points
Current public scheduler API wrappers labeled “legacy names”:
- Comments in kc_sched.c mark “legacy names preserved”.
- Potentially overlapping future structured API (jobs/cancellation) may introduce new spawn semantics.

Recommendation: Plan a deprecation path:
- Introduce a standardized naming layer (`kc_launch`) aliasing `kc_spawn` now (if not already).
- Add `__attribute__((deprecated))` only after new structured job launcher (`kc_job_launch` or similar) is implemented— not yet; just note in docs.

Priority: Low (intentional compatibility).

### 3. Conditional Compilation / Alternate Paths
Macros affecting multiple code paths:
- (Removed) `KCORO_DISABLE_AUTO_SCHED`, `KCORO_SINGLE_THREAD_COOP`: Deleted to enforce a single optimal scheduler initialization path.
- `KCORO_DEBUG` runtime env: Debug instrumentation toggled at runtime (no compile-time forks). Replace any remaining compile-time debug macros.
- Channel zero-copy macros: `KC_CHAN_CAP_ZERO_COPY`, `KC_ZREF_MIN_SIZE`. These are configuration thresholds, not alternate algorithms.
- Arch portability macros (`_GNU_SOURCE`, `__x86_64__`, `__i386__`) in benches— fine.
Conclusion: No redundant compiled “second implementation” remains, just feature toggles for debug/perf. No removal needed now; consider a central configuration header referencing them (already partly done in kcoro_config.h) to reduce scattering.

Priority: Low.

### 4. Build Script Redundancy
Distinct Makefiles:
- Makefile: builds library.
- Makefile: minimal test harness.
- Makefile: large bench/test inclusion + TUI recursion.
- Makefile and nested examples each have simple repeated CFLAGS patterns.

Opportunities:
- Create shared fragment (e.g., `mk/common.mk`) defining `CC`, base `CFLAGS`, `LDFLAGS`.
- Archive Makefile could be simplified to a “not built by default” notice, requiring explicit `make -C tests_archive all`.
- Example Makefiles can include `../../mk/example.mk` to unify flags (reduces drift when adding new warning flags or sanitizer settings).

Priority: Medium (improves consistency and future flag changes).

### 5. Tests Duplication / Overlap
New active test: test_sched_basic.c
Archive tests: scheduler basic, throughput, async bench variants.
Recommendation:
- Curate an “active test list” and migrate any still-needed performance/regression logic into a new `bench/` directory (separate from archive).
- Add CI guard to ensure `tests_archive/` is not compiled unless `ENABLE_ARCHIVE=1`.

Priority: Medium— prevents inadvertent regression reliance on stale tests.

### 6. Legacy Context & Async Experiment Code
- `lab/archive/legacy_ctx` (shared-stack context) and `experimental_async` still include macros `KC_SCHED`, `KC_ASYNC_EXPERIMENTAL`.
Risk: Someone defines those inadvertently, resurrecting experimental code.
Mitigation:
- Add a top-of-file fatal compile guard:
  ```
  #error \"legacy_ctx is archived; do not compile directly\"
  ```
  gated behind `#ifdef COMPILE_ARCHIVE`.
- Or wrap entire file bodies in `#if defined(COMPILE_ARCHIVE)`.

Priority: Medium— reduces accidental build linkage.

### 7. External Integration Example Using Legacy Path
Legacy client examples linking to external dependencies:
- This couples the coroutine library examples to external trees; violates principle of a single canonical integration path.
Options:
1. Mark the example “external legacy integration” clearly and move under `examples/legacy_integration/`.
2. Replace with a pure kcoro-based sample or stub out when dependency missing.

Priority: Low-Medium depending on intended audience.

### 8. Documentation Cross-References to Historical Plan
Headers formerly referenced a historical scheduler plan; references were removed and headers updated accordingly.
Action: Update references to the current scheduler design sections by name (even if those sections are not yet merged), and keep content local and self‑contained.

Priority: High (user requested purity; follow through).

### 9. Potential Over-Split Small Headers
Multiple tiny headers (kcoro_task.h, kcoro_core.h, kc_cancel.h, future kc_job.h) may lead to scattered inline logic. Not strictly DRY violation, but consider a public umbrella header (`kcoro_unified.h`) aggregating stable APIs to reduce repetitive includes and divergence in doc comments.

Priority: Low.

### 10. Logging / Debug Macros Consolidation
Currently separate debug macros: `SCHED_DBG`, `ZREF_DBG`, plus unconditional `fprintf` in some archived code (not inspected fully here).
Recommendation: Centralize debug macro pattern in a single header (e.g., `kcoro_debug.h`) controlling category enables; reduces risk of one path forgetting to guard logs.

Priority: Low-Medium.

---

## Recommended Action Plan (Prioritized)

1. Update header references (High)
   - (Completed) Removed historical scheduler plan references; pruned file.
2. Archive Safeguards (Medium)
   - Add compile guard banners or `#if 0` to `lab/archive/*` and `tests_archive/*` code files.
   - Add `ARCHIVE_NOTICE.md` in each archived subtree.
3. Build System Consolidation (Medium)
   - Introduce `kcoro/mk/common.mk` with shared `CC`, baseline flags.
   - Modify Makefile, Makefile, Makefile to include it.
4. Active vs Archive Test Separation (Medium)
   - Introduce `bench/` directory and migrate living benchmarks.
   - Add `ENABLE_ARCHIVE` flag gating tests_archive build.
5. Example Integration Cleanup (Low-Med)
   - Move legacy clients to `examples/legacy_integration/` or annotate their Makefiles with warning banners.
6. Debug Macro Unification (Low-Med)
   - Create `kcoro_debug.h` and refactor current debug macros to category-based approach.

Stretch:
7. Umbrella Header
   - Provide `kcoro_unified.h` including stable headers, deprecating direct scattered includes.

---

## Quick Wins to Implement Next (if you approve)
- Patch header references (task 1).
- Add `ARCHIVE_NOTICE.md` files (task 2 seed).
- Create a `mk/common.mk` and refactor one or two Makefiles as exemplar (task 3 initial).

Let me know which of these you’d like me to execute immediately, or if you want the entire set applied in one pass.

## Mapping to Todo List
Will add new todos for chosen actions once you confirm scope (or I can assume top 1–3 and proceed).

Just tell me: proceed with header reference updates + archive notices + shared make fragment? Or adjust list first.
