# kcoro Plan

## Current Focus (2025-10-05)

- **Match upstream rendezvous lifecycle:** Mirror the retain/release ordering observed in the reference native coroutine runtime (`BufferedChannel.send/receive`, `CancellableContinuationImpl`) so coroutines remain owned until dequeue completes.
- **Waiter cancellation parity:** Install per-cell cancellation handlers that perform the same `Segment.onCancellation` flow (CAS to `INTERRUPTED_*`, wait for expand completion, invoke undelivered hooks).
- **Scheduler TLS hand-off:** Align worker TLS updates with the reference dispatcher pattern (`main_co` swap, continuation restore before release) to stop racey releases.
- **Documentation alignment:** Capture the runtime-derived invariants in the architecture docs (channels + scheduler) so future changes stay in lock-step with the reference implementation.

## Next Actions

1. **Channel wake/retain patch** (in-flight): teach `kc_chan_schedule_wake` and rendezvous receive paths to retain until resume succeeds; release only after queue removal. Cover both waiter representations (`Waiter` and `WaiterEB`).
2. **Cancellation hook wiring:** extend waiter creation helpers to call a shared `kc_waiter_install_cancel` that mirrors the upstream `invokeOnCancellation(segment, index)` semantics.
3. **Scheduler TLS audit:** document and implement the explicit `main_co` swap + TLS store performed by the upstream runtime when resuming continuations.
4. **ASan regression suite:** once the above lands, rerun `test_chan_ptr_rendezvous_basic` (and friends) under ASan/TSan. Promote the rendezvous test from excluded to default.
5. **Docs & code comments:** keep `CHANNELS_ALGORITHM.md` and `scheduler/ARCHITECTURE.md` in sync with the findings (already started in this iteration). Reference the native sample/disassembly path (`tools/kotlin-native-samples/chan`).

## Watch List / Follow Ups

- Evaluate whether additional assembly changes are required after we align C code. Current data shows the ISA emitted by the upstream toolchain is informative, but we expect to stay in C for fixes.
- Track upstream native runtime releases for allocator or coroutine lifecycle changes that could impact parity checks.
- Consider scripting the sample build/disassembly so we can diff upstream output as part of CI when that toolchain bumps.
