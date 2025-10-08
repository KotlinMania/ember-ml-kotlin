# Timers — Cooperative Sleep and Wake in the Scheduler

Abstract: Timers provide coroutine‑native suspension and deadline handling in the kcoro C scheduler. This document describes the timer APIs and how they interact with the scheduler to suspend and resume work without blocking worker threads.

## Public APIs (C)
```
// Sleep helper (cooperative when inside kcoro worker)
void kc_sleep_ms(int ms);

// Timer primitives (cooperative wakeups)
typedef struct kc_timer_handle { unsigned long long id; } kc_timer_handle_t;

kc_timer_handle_t kc_sched_timer_wake_after(kc_sched_t* s, kcoro_t* co, long delay_ms);
kc_timer_handle_t kc_sched_timer_wake_at(kc_sched_t* s, kcoro_t* co, unsigned long long deadline_ns);
int kc_sched_timer_cancel(kc_sched_t* s, kc_timer_handle_t h);
```

## Behavior
- kc_sleep_ms: If called from a coroutine running on a kcoro worker, parks the coroutine and schedules a wake after ms; no worker thread is blocked. If called outside the scheduler (no coroutine context), it falls back to nanosleep on the thread.
- kc_sched_timer_wake_after / kc_sched_timer_wake_at: Schedule a parked coroutine (or any coroutine object) to be enqueued as ready at/after the specified time. Cancellation is best‑effort and may race with the wake firing.
- Time base: Deadlines use CLOCK_MONOTONIC for comparisons; timed waits in the timer thread use CLOCK_REALTIME for portability with pthread_cond_timedwait.

## Usage Examples
```c
// Cooperative sleep inside a coroutine
static void worker(void* arg) {
  for (int i = 0; i < 10; ++i) {
    // do work...
    kc_sleep_ms(5); // parks coroutine cooperatively
  }
}

// Schedule a specific coroutine to wake later
kc_sched_t* s = kc_sched_default();
kcoro_t* co = NULL;
// spawn returns the coroutine via kc_spawn_co(..., &co)
kc_spawn_co(s, my_coro_fn, NULL, 64*1024, &co);
// Park it somewhere in logic, then:
kc_sched_timer_wake_after(s, co, 100 /* ms */);
```

## Implementation Notes
- The current implementation uses a background timer thread and a sorted singly‑linked list ordered by absolute deadline. Future work may migrate to a min‑heap or hierarchical timing wheel for better scalability at large timer counts.
- The timer thread wakes due timers and enqueues their coroutines onto the scheduler’s ready queue; actual resume happens in worker threads.
- Cancellation: `kc_sched_timer_cancel` attempts to remove the timer from the list; if the timer is already firing, cancellation may have no effect.

## Edge Cases
- Non‑positive delays: `kc_sleep_ms(0)` returns immediately (no sleep); negative values are treated as zero.
- Already‑expired deadlines passed to `kc_sched_timer_wake_at` result in the coroutine being enqueued promptly on the next timer thread iteration.
- Spurious wake safety: each timer fires at most once; additional wakeups are ignored.
