# kcoro Rendezvous Handoff — 2025-10-06

I sat down today to write the handoff I wish I had found when I opened this tree. Kotlin’s segmented channel machine is pushing ~22 GB/s on the SWAR rig; our C port has the raw ingredients to match it, but the rendezvous waiter seam is fraying. This document is the living starting point for the rewrite. It mixes narrative context with hard facts so the next specialist can pick up the tools without guesswork.

## Baseline Signals
- **Build/tests**: `make -C external/kcoro tests` currently dies because the top-level Makefile still points `CORE_DIR := user`, but the active sources live under `core/`. Fixing that path is prerequisite for any automated regression guard.
- **Known bug**: `external/kcoro/core/src/kc_chan.c:1518` references `waiter_enqueued` without a declaration inside the rendezvous receive loop; the compiler catches it before runtime does. This flag is also the last vestige of the pre-slot architecture and is the spark for this plan.
- **Kotlin reference**: `tools/kotlinx.coroutines/kotlinx-coroutines-core/common/src/channels/BufferedChannel.kt` stores waiters directly in segmented cell state (`Waiter`, `WaiterEB`). No side flags. Cancellation and rendezvous run through the same state machine.
- **Zero-copy contract**: `external/kcoro/docs/components/zero-copy/ZERO_COPY.md` codifies that rendezvous hand-offs can move ownership of `kc_zdesc` without touching bytes. Any redesign must preserve those invariants.

## Component Deep Dive

### 1. Coroutine Core & Assembly
- **Files**: `external/kcoro/core/src/kcoro_core.c`, `external/kcoro/arch/aarch64/kc_ctx_switch.S`, docs under `docs/components/arch/aarch64/`.
- **State**: Assembly leaf already “diamond sharp”: saves x19–x28, LR, SP, FP, branches to the continuation. No FP state yet; no channel knowledge embedded.
- **Action**: Leave the switcher alone. Focus on keeping reg layout documentation synchronized when we add other architectures. Core needs better regression tests (see Build/tests note).

### 2. Scheduler & Timers
- **Files**: `external/kcoro/core/src/kc_sched.c`, `docs/components/scheduler/ARCHITECTURE.md`, `TIMERS.md`.
- **State**: Work-stealing pool with Chase–Lev deques, inject queue, cooperative timers via background thread. TLS hand-off and ready queue fairness are still mid-migration per PLAN.md.
- **Action**: After waiter cleanup, tackle PLAN.md items #1–#3 (retain/release ordering, cancellation hook parity, TLS audit). Ready queue remains mutexed; issue #6 in `ISSUE_VALIDATION.md` tracks the lock-free replacement.

### 3. Channels & Select
- **Files**: `external/kcoro/core/src/kc_chan.c`, `docs/components/channels/CHANNELS_ALGORITHM.md`, `EVOLUTION_PLAN.md`.
- **State**: Current C path mixes legacy boolean flags (`waiter_enqueued`) with the new destination-based flow. Kotlin’s segmented approach is the target; Evolution Plan phases C.1–C.8 lay the roadmap but we have not unified rendezvous with the slot engine yet.
- **Action (immediate)**:
  1. Replace the boolean flag with a `kc_waiter_token` descriptor and explicit status enum (`INIT`, `ENQUEUED`, `CLAIMED`, `CANCELLED`).
  2. Introduce a rendezvous cell record (`kc_rv_cell`) that owns the state machine (`EMPTY`, `SENDER_READY`, `RECEIVER_READY`, `MATCHED`, `CANCELLED`).
  3. Wire cancellation through token helpers so every path uses one ownership rule.
- **Action (phase-aligned)**: Kick off Evolution Plan C.1 (shadow indices) in parallel to prepare for a unified slot engine that handles rendezvous and buffered modes identically.

### 4. Zero-Copy & Descriptor Backends
- **Files**: `external/kcoro/core/src/kcoro_zcopy.c`, headers under `include/kcoro_zcopy.h`, design doc `ZERO_COPY.md`.
- **State**: Rendezvous already supports descriptor handoff, but only through a single slot and `has_value` flag. Buffered zero-copy relies on parallel data structures.
- **Action**: When the rendezvous cell record lands, back it with a payload union `{kc_payload, kc_zdesc}` and tag so zero-copy and copy semantics run through identical code. Ensure `kc_chan_get_zstats` increments survive the refactor.

### 5. Cancellation, Scopes, Actors
- **Files**: `external/kcoro/core/src/kc_cancel.c`, `kc_scope.c`, `kc_actor.c`, docs under `components/cancellation/` and `/scopes/`.
- **State**: C core exposes cancellable send/recv; PLAN.md calls for parity with Kotlin’s `invokeOnCancellation`. Actors in C are present; C++ layer still lacks them (#11 in issue log).
- **Action**: The new `kc_waiter_token` must install cancellation handlers at allocation time, mirroring Kotlin’s `Waiter.invokeOnCancellation`. Document the hook in `CANCELLATION.md` once implemented. Defer C++ actors until C rendezvous stabilizes.

### 6. Metrics & Observability
- **Files**: `CHANNEL_METRICS.md`, `chanmon`, metrics snapshot helpers in `kc_chan_metrics.c` (once tests build).
- **State**: Snapshot API is specified but partially implemented; drop/cancel counters are planned.
- **Action**: As soon as the waiter rewrite lands, add `cancelled_waiters` increments inside token cancellation, then wire snapshots to expose the counter so chanmon can validate parity against Kotlin channel monitor.

### 7. IPC & Transport
- **Files**: `ipc/posix`, docs under `components/ipc/`.
- **State**: POSIX TLV bridge works with current rendezvous semantics. Future multiplexer (N1–N4) assumes waiters can safely park; cancellation correctness is prerequisite.
- **Action**: No change until core channels stabilize. Once tokens exist, audit IPC wake paths to ensure they invoke `kc_chan_waiter_scope` helpers rather than manipulating queues directly.

### 8. kcoro_cpp Layer & Bindings
- **Files**: `external/kcoro_cpp/*`, docs under `components/developer/ISSUE_VALIDATION.md`.
- **State**: Zero-copy channels exist; actors/scopes missing; metrics partial. C++ depends on C waiter semantics for pointer descriptors.
- **Action**: Expose the new token descriptors through the C API so C++ wrappers can surface them (useful for tooling). Plan C++ actor work after the refactor and scheduler updates.

### 9. Tooling, Labs, Tests
- **Files**: `external/kcoro/lab/*`, `chanmon`, `tests`.
- **State**: Stress tools exist but build system mispoints to `user`. No automated gate is running right now.
- **Action**: Fix Makefile path, resurrect tests, and add a rendezvous regression that covers zero-copy, cancellation, and select. Tie chanmon’s rendezvous metrics to the new token counters.

### 10. Documentation & Research Logs
- **Files**: `docs/components/*`, `developer/JOURNAL.md`, `PLAN.md`.
- **State**: Documentation is up to date through 2025-09-22 entries, including the new journal note about the rendezvous redesign. PLAN.md already calls out waiter retention parity.
- **Action**: Once the token/cell refactor lands, update `CHANNELS_ALGORITHM.md` and `PLAN.md` to reference the new state machine and remove references to legacy flags.

## Immediate Execution Plan
1. **Fix Build Harness**: Update `external/kcoro/Makefile` (`CORE_DIR := core`) so `make tests` reaches real sources. Re-enable the existing unit/stress targets.
2. **Introduce Token Type**: Define `struct kc_waiter_token` and helper API (`kc_waiter_token_init`, `kc_waiter_publish`, `kc_waiter_cancel`). Replace `waiter_enqueued` and raw queue manipulations in rendezvous receive/send paths with the helper.
3. **Rendezvous Cell Refactor**: Encapsulate slot state (`kc_rv_cell`), replace `ch->has_value` with state enum, and funnel both copy and zero-copy payloads through the cell. Guarantee state transitions mirror Kotlin (`EMPTY → SENDER_READY → MATCHED`, etc.).
4. **Cancellation Wiring**: Hook `kc_cancel_t` into the token so cancellation removes the waiter once and records `cancelled_waiters`.
5. **Tests & Metrics**: After refactor, run updated test suite, stress with chanmon, and snapshot metrics to verify cancellation/resume counts match Kotlin-style expectations.

## Open Questions
- Should we stage the refactor behind a compile-time flag to keep legacy path available for bisecting? (My vote: no—legacy path already fails to compile; focus on the new engine.)
- Do we port the Kotlin segmented ring wholesale or adapt the Evolution Plan’s `Slot Cell` concept? (Proposal: adapt the plan—start with a single rendezvous cell, then move to indices so buffered channels share the code.)
- How soon do we need x86_64/RISC-V switchers? (Not blocking for rendezvous, but document assumptions in `ARM64_STRATEGY.md` per component owners.)

## Final Words for the Next Pair of Hands
Kotlin earned the 22 GB/s trophy by eliminating side flags and trusting a single state machine. Our C port is poised to do the same once the rendezvous cell exists and cancellation hooks are first-class. This plan gets us there: fix the build harness, replace ad-hoc booleans with tokens, fold rendezvous into the slot engine, and surface metrics so chanmon can prove parity. When that’s done, the scheduler and zero-copy specialists can tune their layers knowing the foundation is as sharp as the ARM64 switcher.

---

## 2025-10-06 Night Run — Token VM Mirror & BizTalk Analogies

I took the BizTalk cue and pushed the lab one layer closer to the “allocation table” dream. Instead of spraying context switches everywhere, the token interpreter now just stamps register tickets and hands them to a dedicated resume stub—think of it as a miniature record in a channel allocation table. The AArch64 implementation lives in `external/kcoro/lab/token_vm_apply.S`: it loads x19–x28 from the staged table, branches to the trampoline, then restores the caller’s callee-saved registers so the scheduler stays untouched. On the benchmark (`external/kcoro/lab/build/lab_token_vm_bench`) that new resume path hits ~168.76 M resumes/sec (~5.93 cycles/resume) on the M3 Pro—roughly double the earlier C-only interpreter.

To make the concept tangible I also cloned a kcoro “mirror” inside the lab (`token_vm_mirror.c`). It is a deliberately tiny scheduler: a ready queue, a pointer-only channel with zero-copy semantics, and two producers + two consumers trading 1 KB descriptors. With the new resume stub the mirror chews through 2,000 messages (1,000 per consumer) without touching a memcpy, printing:

```
consumer[0] received=1000 sum=499500
consumer[1] received=1000 sum=499500
total received=2000 total sum=999000
```

The BizTalk analogy paid off. Treating each coroutine as a “record” in a RAM-backed allocation table lets us slot tokens (register tickets) and descriptors side by side. Blocking producers simply yield while their records stay resident; when consumers drain the queue the scheduler requeues the waiting records without copying payloads. The next step is to graft the real kcoro channel code onto this lab skeleton so the same ticketing system drives rendezvous send/recv in production.
