Experimental Async/Await Channels (Archived)

Status
- Experimental scaffold for async/await channel operations and an async actor benchmark.
- Not built by default; requires explicit opt-in and scheduler support.
- Kept for reference; superseded by the current coroutine core + scheduler work.

Contents
- kc_async.c                 — async channel API scaffold (kcoro_async.h)
- kc_actor_bench_async.c     — benchmark that exercised async API
- kc_actor_bench_async.h     — header for the async benchmark

Build Notes (not recommended for production)
- Requires: KC_SCHED=1 KC_ASYNC_EXPERIMENTAL=1
- Example (from repo root):
  - make -C coroutines/kcoro/user KC_SCHED=1 CFLAGS="-DKC_ASYNC_EXPERIMENTAL=1"
  - make -C coroutines/kcoro/tests KC_SCHED=1 KC_ASYNC_EXPERIMENTAL=1

Limitations
- No proper park/wake integration with the scheduler (tasks are not parked on channel queues).
- Timeout handling is incomplete.
- API surface is unstable and gated by KC_ASYNC_EXPERIMENTAL.

Rationale for Archiving
- The current direction favors implementing park/wake semantics directly in the channel layer
  and integrating with the scheduler’s task queues, instead of a separate async API.

