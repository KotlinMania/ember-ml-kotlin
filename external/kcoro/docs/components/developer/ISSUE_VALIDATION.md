# Issue Validation Report (Deep Investigation)

This document validates the current open issues (#4–#11) against the repository’s actual code and documentation. Each item lists: summary, evidence, verdict, and recommended action.

Scope: kcoro (C core), kcoro_cpp (C++ layer), python_kcoro (Python layer), and relevant docs/tools.

Last reviewed: 2025-09-29 22:36 local

---

## #4 Performance Gap — Implement work-stealing scheduler in kcoro for multi-core scaling

- Evidence:
  - C scheduler present and active:
    - src/kcoro/include/kcoro_sched.h — unified work-stealing scheduler API (kc_sched_t, kc_spawn, kc_sched_enqueue_ready, drain, stats).
    - src/kcoro/user/src/kc_sched.c — implementation with per-worker deques, stealing (deque_push/steal), global inject queue, ready list.
  - Tests/logs exist in tests_archive for scheduler basics and throughput.
- Findings:
  - Work-stealing exists and is default; ready-queue is mutex-based.
- Verdict: Already implemented (C layer). The performance gap, if any, now centers on the ready-queue lock and heuristics.
- Recommended action: Close or retitle to “Optimize scheduler (lock-free ready queue; stealing heuristics; shutdown determinism).” Track lock-free queue under #6.

## #5 Performance Gap — Implement pointer-descriptor channels in kcoro_cpp for zero-copy throughput

- Evidence:
  - Generic channels template supports pointer descriptors explicitly:
    - size_bytes_default overloads for std::pair<void*,size_t> and ZDesc in src/kcoro_cpp/include/kcoro_cpp/channel.hpp.
    - UnlimitedChannel/BufferedChannel/RendezvousChannel usable with T=std::pair<void*,size_t> or T=ZDesc.
  - Canonical zero-copy descriptor (ZDesc) and backend interface in src/kcoro_cpp/include/kcoro_cpp/core.hpp.
  - Zero-copy specialized channels/backends:
    - src/kcoro_cpp/include/kcoro_cpp/zref.hpp — ZRefRendezvousChannel and ZRefBufferedChannel (descriptor-first).
    - src/kcoro_cpp/src/zref.cpp — backend implementations and region registry helpers.
  - Tools/tests use pointer-descriptor channels:
    - src/kcoro_cpp/tools/chanmon/main.cpp chooses UnlimitedChannel<std::pair<void*,size_t>>.
- Verdict: Implemented (MVP). Pointer-descriptor channels exist and integrate with zero-copy backends.
- Recommended action: Keep issue only if additional perf targets or transport backends (SHM/NIC) are required; otherwise close.

## #6 Performance Gap — Implement lock-free ready queue operations in kcoro

- Evidence:
  - Ready queue in C scheduler is protected by pthread_mutex (rq_mu) and uses an intrusive FIFO:
    - src/kcoro/user/src/kc_sched.c: rq_mu, rq_head/rq_tail; comments “Ready queue helpers.”
  - No lock-free structure is present for ready list.
- Verdict: Valid gap (not implemented).
- Recommended action: Keep open. Scope: replace ready list with lock-free MPSC or intrusive ring with atomic head/tail; ensure ABA safety and correctness with coroutine lifecycle.

## #7 Performance Gap — Implement zero-copy backend registry system in kcoro_cpp

- Evidence:
  - ZCopyRegistry implemented with backend map and region registry:
    - src/kcoro_cpp/include/kcoro_cpp/zref.hpp — class declaration.
    - src/kcoro_cpp/src/zref.cpp — definitions: region_register/incref/decref/deregister/query, meta set/get, aligned allocation.
  - Backend implementations use registry and policy enforcement (format masks):
    - ZRefRendezvous, ZRefBuffered.
- Verdict: Implemented.
- Recommended action: Close or evolve to “add SHM/NIC pluggable backends and persistence.”

## #8 Feature Gap — Implement conflated and unlimited channels in kcoro_cpp

- Evidence:
  - Implementations present:
    - ConflatedChannel<T>, UnlimitedChannel<T> in src/kcoro_cpp/include/kcoro_cpp/channel.hpp.
  - Tests/tools reference unlimited channel:
    - src/kcoro_cpp/tools/tests/test_chan_types.cpp, tools/channel_stress.
- Verdict: Implemented.
- Recommended action: Close. Optionally add more property tests for conflation semantics under contention.

## #9 Feature Gap — Implement advanced timer system in kcoro for deadline-based scheduling

- Evidence:
  - C scheduler now has cooperative timers and coroutine-aware sleep:
    - src/kcoro/include/kcoro_sched.h — public timer API: kc_sched_timer_wake_after/at/cancel, kc_sleep_ms semantics.
    - src/kcoro/user/src/kc_sched.c — timer thread, sorted deadline list, cooperative wake of parked coroutines.
    - src/kcoro/docs/components/scheduler/TIMERS.md — updated documentation on timers.
  - C++ scheduler also has timer support; parity on basic delays is achieved.
- Verdict: Partially implemented (basic cooperative timers done; advanced data structure and deadline-aware ops pending).
- Recommended action: Keep open; evolve to implement min-heap or hierarchical timing wheel and add first-class deadline-aware operations (e.g., timeouts in channel ops) with precision targets.

## #10 Feature Gap — Implement comprehensive channel statistics in kcoro_cpp

- Evidence:
  - Basic counters and snapshot exist in ChannelSnapshot; periodic emission via ChannelMetricsEvent with thresholding (channel.hpp).
  - Docs: src/kcoro/docs/components/channels/CHANNEL_METRICS.md describes broader metrics (histograms, percentiles) not fully implemented.
  - Tools: chanmon reads metrics but advanced percentiles/histograms not visible in code.
- Verdict: Partially implemented. Counters/emit exist; comprehensive suite (histograms/latency) missing.
- Recommended action: Keep open; scope to add low-overhead histograms (HDR or DDSketch), enable/disable gates, exporters.

## #11 Feature Gap — Implement Actor model and structured scopes in kcoro_cpp

- Evidence:
  - No actor/structured scope runtime found under src/kcoro_cpp/include or src/kcoro_cpp/src; tests do not reference actors in C++ layer.
  - Python layer has actor-like constructs, but kcoro_cpp lacks them.
- Verdict: Valid gap (not implemented in kcoro_cpp).
- Recommended action: Keep open. Build atop existing scheduler/channels; define scope { spawn } with cancellation propagation and mailbox backpressure.

---

## Summary Table

- #4 Work-stealing in kcoro: Implemented → Close/retitle to optimization
- #5 Pointer-descriptor channels (kcoro_cpp): Implemented → Close or refine scope
- #6 Lock-free ready queue (kcoro): Not implemented → Keep open
- #7 Zero-copy backend registry (kcoro_cpp): Implemented → Close or extend
- #8 Conflated/unlimited channels (kcoro_cpp): Implemented → Close
- #9 Advanced timers (kcoro): Not implemented → Keep open
- #10 Channel statistics (kcoro_cpp): Partial → Keep open
- #11 Actors & structured scopes (kcoro_cpp): Not implemented → Keep open

## Acceptance Checks (suggested)

- #4: Verify parallel speedup on N=cores workload; ensure shutdown determinism test passes 1e6 tasks.
- #6: TSAN clean under stress; microbench lock-free ready enqueue/dequeue vs mutex baseline.
- #9: Timer precision within 1–2 ms under load; O(log n)/amortized O(1) insertion; cancellation races resolved.
- #10: Overhead <5% enabled, ~0% disabled; export histogram percentiles; chanmon shows deltas and percentiles.
- #11: Sample actor app with graceful shutdown; scope tests for fail-fast and cancellation propagation.

## Next Steps

- Propose closing #4, #5, #7, #8 as done (or retitle as enhancements).
- Prioritize #6 and #9 for core performance and semantics.
- Plan #10 metrics expansion and #11 programming model atop the stabilized scheduler.
