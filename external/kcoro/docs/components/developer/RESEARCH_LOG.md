# Research Log

This file contains the research log entries and hardening notes captured in the top-level research notes.

---

# kcoro Research Log — 2025-09-11

This log documents the approaches taken, issues encountered, and test results while hardening kcoro and adding Phase 1 (cancellation) along with a disciplined tests/ setup.

## Summary
- Implemented Phase 0 hardening and visibility improvements (destructors, clock choice, IPC bounds, server replies, example debugability).
- Implemented Phase 1 cancellation token and cancellable channel/actor APIs.
- Added a `tests/` folder with initial unit tests for channels and cancellation.
- Verified example `distributed_channels` runs to completion with `--debug` and clean shutdown.

## Changes Implemented
- Port layer
  - Added destructor macros and documented them: `KC_MUTEX_DESTROY`, `KC_COND_DESTROY`.
    - coroutines/kcoro/include/kcoro_port.h:5
    - coroutines/kcoro/port/posix.h:6
  - Extended error map with `KC_ECANCELED`.
    - coroutines/kcoro/port/posix.h:26
- Channels
  - Non-blocking recv on closed/empty now returns `KC_EPIPE` (was `KC_EAGAIN`).
    - coroutines/kcoro/user/src/kc_chan.c:218
  - Cancellable wrappers `kc_chan_send_c()` / `kc_chan_recv_c()` added.
    - coroutines/kcoro/user/src/kc_chan.c:280
  - Timeouts use CLOCK_MONOTONIC consistently.
    - coroutines/kcoro/user/src/kc_chan.c:37
- Actors
  - Extended start `kc_actor_start_ex()` (optional cancel token), `kc_actor_cancel()` convenience, prompt exit on `-ECANCELED`.
    - coroutines/kcoro/user/src/kc_actor.c:9
- Cancellation token
  - `kc_cancel_t` (init/trigger/is_set/destroy) with wakeups via broadcast.
    - coroutines/kcoro/user/src/kc_cancel.c
  - Public API declarations.
    - coroutines/kcoro/include/kcoro.h:11
- IPC POSIX
  - Validated `sockaddr_un` path length; return `-ENAMETOOLONG`.
    - coroutines/kcoro/ipc/posix/src/kcoro_ipc_posix.c:50
  - Server handlers always reply with result TLVs; defer destroy to allow draining.
    - coroutines/kcoro/ipc/posix/src/kcoro_ipc_server.c:1
- Example
  - `distributed_channels`: `--debug` flag, threaded server worker, attach-by-ID (`kc_ipc_chan_open`), and graceful shutdown.
    - coroutines/kcoro/examples/distributed_channels/main.c:1

## Issues Encountered and Resolutions
1) Actor stop race and hangs
- Symptom: actor threads could block indefinitely in `kc_chan_recv()`; stop flag wasn’t sufficient.
- Fixes:
  - Interim: actor recv uses short poll when no cancel token.
  - Phase 1: integrated `kc_cancel_t` and `kc_actor_start_ex()`/`kc_actor_cancel()`. Receives return `-ECANCELED` promptly when triggered.

2) Resource cleanup leaks
- Symptom: mutex/cond not destroyed; failure paths leaked actor mutex.
- Fixes: introduce destructor macros in port; destroy in `kc_chan_destroy()` and actor paths.

3) IPC sockaddr_un overflow risk
- Symptom: path length unchecked; risk of overflow.
- Fix: validate against `sizeof(sun_path)-1`; return `-ENAMETOOLONG`.

4) Clock mismatch for timed waits
- Symptom: condvars configured for MONOTONIC; timeouts sometimes computed using REALTIME.
- Fix: use CLOCK_MONOTONIC for deadlines to match condvar attr.

5) Inconsistent error semantics
- Symptom: mixed returns and missing EPIPE on closed empty channel (non-blocking).
- Fix: normalized to negative errno; closed+empty non-blocking recv returns `-EPIPE`; server replies always include `RESULT` TLV.

6) Distributed example didn’t exit consistently
- Symptom: messages kept flowing after producer closure; server threads didn’t terminate cleanly.
- Fixes:
  - Recv on closed+empty returns `-EPIPE`; consumer loop terminates.
  - Server worker exits on transport error (e.g., `-ECONNRESET`), threads detached; parent cleans up tmp files.
  - Added `--debug` to surface control flow.

7) Missing tests led to regressions
- Symptom: unknown errors slipped in without isolation.
- Fix: created `tests/` with basic unit tests and Makefile.

## Tests
- Location: coroutines/kcoro/tests
- Build: `make -C coroutines/kcoro/user && make -C coroutines/kcoro/tests`

- test_chan_basic
  - Verifies buffered channel send/recv, `EAGAIN` on full, `EPIPE` on non-blocking recv after close.
  - Status: PASS.

- test_cancel_recv
  - Verifies `kc_chan_recv_c()` returns `-ECANCELED` when token is triggered under infinite timeout.
  - Status: PASS.

- test_actor_cancel
  - Starts a rendezvous actor with infinite timeout and a token, then cancels; actor exits.
  - Status: PASS.

- test_cancel_ctx
  - Verifies hierarchical cancellation: parent token cancels child context; child cancellable recv returns `-ECANCELED`.
  - Status: PASS.

- test_stress_pc
  - 4 producers × 4 consumers × ~100k messages; counts match; no deadlocks.
  - Status: PASS.

Benchmarks
- bench_latency (rendezvous ping‑pong)
  - Reports round‑trip ns and derived one‑way ns.
- bench_throughput (buffered P×C)
  - Reports msgs/sec; accepts P/C/msgs/capacity as args.

Runner defaults & improvements
- Tests always run with verbose logging; console is filtered and truncated to a small number of lines by default to control output volume.
- Full, unfiltered logs are always saved under `tests/logs/<timestamp>/`.
- Use `--full` to disable filtering/truncation on console when deep‑debugging locally.

## Example Run (Distributed Channels)
- Build: `make -C coroutines/kcoro/examples/distributed_channels`
- Run: `./coroutines/kcoro/examples/distributed_channels/build/distributed_channels --debug`
- Result: Producer, consumer, server start; messages 0–9 delivered; producer closes; consumer drains; example prints “Example Complete” and exits. Debug logs show server handler activity and clean connection teardown.

## Lessons Learned / Approach
- Adopt consistent error semantics and propagate them faithfully across layers.
- Provide debug toggles (`--debug`, `KCORO_DEBUG`) to accelerate diagnosis.
- Integrate cancellation into blocking operations instead of relying on external flags.
- Add disciplined tests early; isolate core algorithms (channels, cancel, actors) before integrating IPC.

## Next Steps
- Phase 2: Job tree (structured concurrency) with cancel propagation; tests for cascading cancellation.
- Phase 3: Select/multiplexing (mutex/cond baseline); tests for atomic choice, cancel, timeout.
- Add more channel tests: conflated, rendezvous edge cases, unlimited growth, timed boundaries, on-drop behavior (planned).
- Optional: TLV buffer pool to reduce malloc/free churn in IPC.

## Related Plan
- See the plan for details.

---

# kcoro Engineering Journal (migrated entries)

## 2025‑09‑22 — zref-only backend, tests green, docs polish

- Removed the separate ptr backend; unified on a single “zref” backend that implements rendezvous and queued descriptor paths.
- Canonicalized on descriptor APIs (`kc_chan_send_desc/_recv_desc` + `_c` variants); `*_ptr` / `*_zref` functions are thin wrappers that compose `kc_zdesc`.
- Purged compile‑time debug forks (`KCORO_DEBUG_SCHED`, `ZREF_DBG*`) and fossil `#if 0` blocks; kept runtime `KCORO_DEBUG` logging.
- Fixed pointer rendezvous lifecycle in tests via deterministic drain/join; default test suite is green across buffered/rv/zref/timeout/scheduler.
- Docstring pass across headers and kc_zcopy.c (2–3 line explanations for constants; clear file‑level prologues). Added OS‑neutral prologue to `kcoro_port.h`.
- Updated the plan with a status section and focused N1/N2 next steps.

## 2025‑09‑21 — Zero‑Copy Surface Unified; Monitor Uses Real Stats (update)

- Centralized zero‑copy under a neutral descriptor (`kc_zdesc`) and backend vtable in `kcoro_zcopy.h`.
- Implemented backend factory and a single built‑in backend in `kcoro_zcopy.c`:
  - `zref` unified backend:
    - Rendezvous: hand‑to‑hand
    - Buffered/Unlimited/Conflated: queued descriptor path (former ptr path)
- `kc_chan.c` pointer‑first ops update bytes/ops via shared helpers; rendezvous routes through backend.
- kcoro_chanmon now reads `kc_chan_snapshot()` and uses `kc_chan_compute_rate()` to show true PPS/GBPS and failure deltas. Headless NDJSON output added.
- Added “pre‑kill” hook before builds/tests to eliminate lockups from lingering test binaries; wrapped each test with a timeout; heavy stress kept under `test-full` with a longer timeout.
- Tests passing in default suite: buffered pointer basic; zref basic; zref timeout; failure counters; scheduler basic.
- Ptr rendezvous test: still failing on length assertion; root cause narrowed to rendezvous wake + capability bit; fixed recv wake (pop from `wq_recv_head`) and set ZERO_COPY bit on backend bind; remaining issue under investigation.
- Performance spot‑check (local): headless chanmon with P=1/C=1, N=20000, size=512 shows ~78 Gbps sustained (single‑process synthetic scenario).

## 2025‑09‑20 — Metrics & Monitor Migration

- Refactored monitor to use snapshot + rate helper instead of ad‑hoc loops.
- Ensured per‑op counters update on every success (ops and bytes) so deltas are precise.
- Added failure counters to the snapshot; monitor displays deltas alongside PPS/GBPS.

---
