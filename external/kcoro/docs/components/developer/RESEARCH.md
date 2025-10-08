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

