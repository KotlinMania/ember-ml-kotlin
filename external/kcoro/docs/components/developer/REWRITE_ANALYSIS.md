```markdown
# kcoro Rewrite Audit (2025-09-17)

## Executive Summary
- The checked-in kcoro runtime is the cooperative M:N design that ships today: `kcoro_core.c` drives coroutines on pthread worker threads via the ARM64 assembly fast-path in `arch/aarch64/kc_ctx_switch.S`.
- The repository previously contained archived compatibility sources (shared-stack experiments). Those files have been removed from the active tree and are retained in `src/kcoro/lab/archive_removed/legacy-compat-2025-09-29/` for legal/history review. Active code and deliverables must remain free of third-party runtime sources that could affect licensing.
- Within `kcoro/`, there are no alternate files like `kcoro_ctx.c` or `kcoro_stack.c`; all active coroutine logic is the private-stack clean-room implementation. Future work must continue to avoid incorporating legacy compatibility sources.
- Channels, cancellation, scopes, actors, select, and the distributed IPC layer all have functioning C sources under `user/src/` and `ipc/posix/src/`. They rely on the kcoro core and were not removed during the rewrite.
- Several subsystems are only partially integrated: channel wait paths fall back to `kcoro_yield()` spin/yield loops for timeout handling, `kc_task.c` is a stub, and the scheduler offers a single global FIFO queue without work stealing.

## Capability Matrix (Ground Truth — 2025-09-17)
| Area | Status | Notes / Evidence |
|------|--------|------------------|
| Coroutine core (ARM64, private stack) | ✅ Implemented | `user/src/kcoro_core.c`, `arch/aarch64/kc_ctx_switch.S`; `mmap` stacks, TLS current-co tracking |
| Legacy shared-stack port (historical) | ⚠️ Archived (do not ship) | Historical ARM64 compatibility sources are retained for internal review only. These files were moved to `src/kcoro/lab/archive_removed/legacy-compat-2025-09-29/`. Do not include these sources in shipped deliverables. |
| Other architectures for kcoro | ❌ Missing | No `x86_64`/`riscv` switchers; build will fail off-ARM64 |
| Scheduler scaffold | ✅ Basic FIFO M:N | `user/src/kc_sched.c` pthread worker pool + ready queue; no work stealing |
| Coroutine parking API | ⚠️ Partial | `kcoro_park/unpark` exist, but channels/select mostly use yield loops instead of parking |
| Channels (buffered/unlimited/rendezvous/conflated) | ✅ Implemented | `user/src/kc_chan.c`; unlimited auto-grows, rendezvous/conflated paths handled |
| Channel wait queues | ⚠️ Partial | Waiters stored per-channel, but timed waits poll; notifications enqueue without state checks |
| Select (`kc_select.c`) | ⚠️ Partial | Registers with channels; infinite waits park, timed waits poll/yield; cancellation checked manually |
| Cancellation tokens/contexts | ✅ Implemented | `user/src/kc_cancel.c`; hierarchical propagation via linked children |
| Structured scopes | ✅ Implemented | `user/src/kc_scope.c`; tracks child coroutines/actors, auto-cancels on destroy |
| Actor helpers | ✅ Implemented | `user/src/kc_actor.c`; coroutine-backed loop with optional cancellation |
| Task abstraction (`kc_task.c`) | ❌ Skeleton only | Functions exist but just call `sched_yield()`; unused by scheduler |
| Distributed IPC | ✅ Implemented | `ipc/posix/src/*.c`; TLV protocol, remote channel ops |
| Bench/tests harness | ⚠️ Present but brittle | `tests/run`, `tests/*.c`, bench logs/scripts exist; some commands reference missing build outputs |
| Documentation | ❌ Out-of-date | Many docs still describe legacy shared-stack internals or refer to upstream-inspired dispatcher work. Update docs to reflect the active private-stack kcoro implementation and remove direct comparisons to archived compatibility experiments. |

## Implementation Highlights


```
# kcoro Rewrite Audit (2025-09-17)

## Executive Summary
- The checked-in kcoro runtime is the cooperative M:N design that ships today: `kcoro_core.c` drives coroutines on pthread worker threads via the ARM64 assembly fast-path in `arch/aarch64/kc_ctx_switch.S`.
- The repository previously contained a legacy shared-stack runtime under historical lab subtrees (with a compatibility façade and compatibility labs), but kcoro is the clean-room replacement. The legacy subtree is retained only for historical/legal reference and must not ship in BSD-only deliverables. See `src/kcoro/lab/archive_removed/legacy-compat-2025-09-29/` for neutral archived copies.
- Within `kcoro/`, there are no alternate files like `kcoro_ctx.c` or `kcoro_stack.c`; all active coroutine logic is the private-stack clean-room implementation. Future work must continue to avoid incorporating legacy shared-stack sources.
- Channels, cancellation, scopes, actors, select, and the distributed IPC layer all have functioning C sources under `user/src/` and `ipc/posix/src/`. They rely on the kcoro core and were not removed during the rewrite.
- Several subsystems are only partially integrated: channel wait paths fall back to `kcoro_yield()` spin/yield loops for timeout handling, `kc_task.c` is a stub, and the scheduler offers a single global FIFO queue without work stealing.

## Capability Matrix (Ground Truth — 2025-09-17)
| Area | Status | Notes / Evidence |
|------|--------|------------------|
| Coroutine core (ARM64, private stack) | ✅ Implemented | `user/src/kcoro_core.c`, `arch/aarch64/kc_ctx_switch.S`; `mmap` stacks, TLS current-co tracking |
| Legacy shared-stack port (historical) | ⚠️ Archived (do not ship) | Historical shared-stack compatibility sources and assembly switchers (ARM64) were present in earlier lab subtrees; retained only for historical comparison—kcoro deliverables must remain free of legacy third-party runtime sources. Neutral copies are in `src/kcoro/lab/archive_removed/legacy-compat-2025-09-29/`. |
| Other architectures for kcoro | ❌ Missing | No `x86_64`/`riscv` switchers; build will fail off-ARM64 |
| Scheduler scaffold | ✅ Basic FIFO M:N | `user/src/kc_sched.c` pthread worker pool + ready queue; no work stealing |
| Coroutine parking API | ⚠️ Partial | `kcoro_park/unpark` exist, but channels/select mostly use yield loops instead of parking |
| Channels (buffered/unlimited/rendezvous/conflated) | ✅ Implemented | `user/src/kc_chan.c`; unlimited auto-grows, rendezvous/conflated paths handled |
| Channel wait queues | ⚠️ Partial | Waiters stored per-channel, but timed waits poll; notifications enqueue without state checks |
| Select (`kc_select.c`) | ⚠️ Partial | Registers with channels; infinite waits park, timed waits poll/yield; cancellation checked manually |
| Cancellation tokens/contexts | ✅ Implemented | `user/src/kc_cancel.c`; hierarchical propagation via linked children |
| Structured scopes | ✅ Implemented | `user/src/kc_scope.c`; tracks child coroutines/actors, auto-cancels on destroy |
| Actor helpers | ✅ Implemented | `user/src/kc_actor.c`; coroutine-backed loop with optional cancellation |
| Task abstraction (`kc_task.c`) | ❌ Skeleton only | Functions exist but just call `sched_yield()`; unused by scheduler |
| Distributed IPC | ✅ Implemented | `ipc/posix/src/*.c`; TLV protocol, remote channel ops |
| Bench/tests harness | ⚠️ Present but brittle | `tests/run`, `tests/*.c`, bench logs/scripts exist; some commands reference missing build outputs |
| Documentation | ❌ Out-of-date | Many docs still describe legacy-era internals or reference upstream-inspired dispatcher work |

## Implementation Highlights

### kcoro Runtime (`kcoro/`)
- **Coroutine core**: Allocates private `mmap` stacks (default 64 KiB) and tracks the running coroutine via TLS (`current_kcoro`). Exposes `kcoro_resume`, `kcoro_yield`, `kcoro_yield_to`, `kcoro_park`, `kcoro_unpark`.
- **Assembly fast-path**: `arch/aarch64/kc_ctx_switch.S` saves/restores callee-saved registers and the stack pointer. A single path exists today (ARM64) with no guard pages or FPU context preservation.
- **Scheduler**: `kc_sched.c` launches pthread workers, each creating a main coroutine (`kcoro_create_main`) and resuming ready coroutines from a mutex-protected FIFO. Legacy `kc_spawn` task queue remains; higher-level task APIs (`kc_task.c`) are not wired in.
- **Channels & select**: `kc_chan.c` implements buffered, unlimited, rendezvous, and conflated semantics with per-channel waiter queues. Wakers enqueue coroutines on the scheduler, but timed waits rely on spin/yield loops. `kc_select.c` registers clauses with channels, parks only for infinite waits, and polls for deadlines/cancellation; APIs intentionally mirror modern coroutine ergonomics while remaining a clean-room design.
- **Cancellation & scopes**: `kc_cancel.c` implements cascaded tokens via linked child lists. `kc_scope.c` tracks child coroutines/actors with mutex/condvar and cancels children on destroy.
- **Actors**: `kc_actor.c` spawns coroutine workers over channels, with optional cancellation tokens and completion callbacks.
- **IPC**: `ipc/posix/src/` sends KCORO TLV commands over UNIX sockets for distributed channels; non-blocking helpers mirror README claims.

### Archived compatibility subtree
 The repository previously contained archived compatibility code (shared-stack experiments and legacy compatibility artifacts). Those sources have been moved out of the active tree and are retained in `src/kcoro/lab/archive_removed/legacy-compat-2025-09-29/` for legal/history review. Active documentation and build targets should not reference those files.

## Gaps & Recommended Work
1. **Generalize architecture support**: add x86_64 (and others) context switchers for kcoro, or detect architecture at configure time and fail fast with messaging.
2. **Integrate parking in channels/select**: replace yield loops with `kcoro_park()` + `kcoro_unpark()` to avoid busy waits and improve fairness; audit wake paths for state safety.
3. **Decide fate of `kc_task` layer**: either wire it into scheduler queues (proper task blocking/resume) or remove the unused abstraction to reduce confusion.
4. **Scheduler improvements**: consider per-worker queues, work stealing, and realistic backpressure; global FIFO is a contention point under load.
5. **Timeout accuracy**: compute timers without re-locking channels every slice; avoid coarse cancellation slices and repeated `kc_now_ns()` calls.
6. **Testing/bench harness**: fix scripts referencing missing binaries; add CI-friendly unit tests for channel/select semantics and cancellation propagation across scheduler threads.
7. **Documentation refresh**: update repository documentation to explain the current kcoro implementation and the archived legacy subtree; remove comparisons to external runtimes and keep the clean‑room licensing stance clear.
8. **Diagnostics & guard rails**: add stack guard pages, optional tracing/logging, and assertions for misuse (e.g., channel send outside coroutine context) to catch issues earlier.
9. **Legacy integration decision**: clarify long-term support expectations for the legacy compatibility subtree; either fold required features into kcoro or explicitly mark the legacy subtree as frozen with tests ensuring it still builds.

## Conclusion
The kcoro directory contains the actively maintained private-stack coroutine runtime along with channels, actors, scopes, and distributed IPC. The repository also retains an archived ARM64 shared-stack port for historical comparison, but kcoro deliverables must remain free of legacy third-party runtimes to honour licensing goals. No evidence shows that kcoro rewrote over the archived files—rather, the new runtime lives alongside the legacy subtree. Future work should focus on tightening kcoro’s integrations (parking, scheduling policies, task layer), broadening platform support, refreshing documentation, and deciding how the archived compatibility port fits into ongoing maintenance.

### Licensing Guardrails
- Maintain kcoro as a clean‑room BSD implementation; documentation should stand on its own without tying semantics to any specific external runtime.
- Clearly label the archived legacy subtree as non-shipping and keep tooling/tests that prevent it from sneaking into builds.
- Keep attribution factual and license‑aware when discussing generic coroutine patterns; avoid implying code provenance from other projects.
