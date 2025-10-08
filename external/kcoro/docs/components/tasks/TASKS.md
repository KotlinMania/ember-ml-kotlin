# TODO: Structured Task System (Future Work)

Previous experimental task APIs (kcoro_task.h / kc_task.c) were removed to keep the
public surface minimal and avoid promising semantics that were not yet implemented.
This document captures the intended direction so the design is not lost.

## Goals
- Provide a first-class task abstraction distinct from raw coroutines for higher-level orchestration.
- Integrate cancellation, deadlines, and structured concurrency (scoped lifetimes).
- Allow parking/waking tasks without falling back to OS thread blocking.
- Timer-based sleeping (efficient wheel or min-heap integration with scheduler loop).

## Proposed Core Types
- `kc_task_t`: Opaque handle owning a coroutine and metadata.
- `kc_task_ctx_t`: Worker-local context (current task, scheduler reference, worker id).
- `kc_task_group_t`: Aggregate for structured concurrency (joins, cancellation fan-out).

## API Sketch (Not Implemented)
```
int kc_task_spawn(kc_sched_t* s, kc_task_fn fn, void* arg, kc_task_t** out);
void kc_task_cancel(kc_task_t* t);
int kc_task_join(kc_task_t* t, long timeout_ms);
int kc_task_sleep_cancellable(long timeout_ms, const kc_cancel_t* cancel);
```

## Scheduler Integration
- Maintain a ready list of tasks per worker (reuse existing coroutine ready queue initially).
- Augment `kc_sched_t` with an optional timer min-heap; sleeping tasks inserted with wake time.
- Worker loop checks earliest wake time before parking; timeouts trigger re-queue.

## Cancellation & Blocking
- Channels to register waiting task handles; on wake/cancel propagate state change.
- Cancellation token fan-out to all tasks in a group; join returns `KC_ECANCELED` if aborted.

## Migration Path
1. Reintroduce `kc_task_t` as a thin wrapper around existing `kcoro_t`.
2. Implement sleep via simple `nanosleep` while proving API shape (MVP), then replace with timer wheel.
3. Add cancellation + join semantics.
4. Optimize with parking queues and lock minimization.

## Non-Goals (Initial)
- Priority-based scheduling (can layer later).
- Cross-scheduler task migration.
- Debug/trace API (defer until stable semantics).

---
Last updated: 2025-09-19
