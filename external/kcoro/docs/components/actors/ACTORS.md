# Actors — Lifecycle, Cancellation, and Scope Integration

An actor is a long‑running coroutine that consumes messages from a channel and invokes a user callback for each message. This document explains the code‑level design based on user/src/kc_actor.c and the public headers.

Actor state (kc_actor.c)
- struct kc_actor_state
  - ctx: kc_actor_ctx_t (public) { kc_chan_t* chan; size_t msg_size; int (*process)(const void* msg, void* user); void* user; }
  - buf: malloc(msg_size) scratch buffer reused for each receive.
  - co: coroutine running the actor loop.
  - sched: kc_sched_t* owner scheduler (kc_sched_default()).
  - stop: atomic int flag to request stop.
  - done: int set to 1 on exit; guarded by mu/cv for waiters.
  - mu/cv: mutex/condvar pair to coordinate teardown and callbacks.
  - cancel: optional kc_cancel_t* honoring cooperative cancellation.
  - on_done/on_done_arg: optional callback invoked exactly once upon exit.

Core loop (kc_actor_coro)
- While not stop and not cancel:
  - Receive with zero timeout (non‑blocking path):
    - If cancel is set: return (exit loop).
    - Try kc_chan_recv(_c) with timeout=0; if success, process the message and kcoro_yield() to cooperate; if EAGAIN/ETIME, kcoro_yield() and retry; if EPIPE/ECANCELED, exit.
- On exit: set done=1, signal cv, capture and null out on_done, then invoke callback outside the lock.

Note: The process callback returns an int; the current implementation ignores this return value.

APIs
- kc_actor_start(ctx): allocates state, scheduler, buffer, and spawns kc_actor_coro as a coroutine (kc_spawn_co). Returns kc_actor_t handle.
- kc_actor_start_ex(ctx_ex): like start but also sets ctx_ex.cancel on the actor state to honor external cancellation.
- kc_actor_on_done(actor, cb, arg): registers a completion callback. If already done, calls cb(arg) immediately; otherwise stores it and kc_actor_coro will invoke it exactly once during teardown.
- kc_actor_stop(actor): sets stop=1, waits until done via cv, invokes on_done (if still set), frees resources.
- kc_actor_cancel(actor): if cancel token present, kc_cancel_trigger(); sets stop=1; waits until done; invokes on_done; frees resources. This is used by scopes to propagate cancellation.

Semantics & error mapping
- Processing function is called exactly once per successfully received element.
- Receive errors:
  - KC_EAGAIN/KC_ETIME: treated as transient lack of data; actor yields and retries.
  - KC_EPIPE: upstream closed without more data; actor exits cleanly.
  - KC_ECANCELED: cancellation requested; actor exits.

Backpressure & fairness
- The actor yields cooperatively after each processed message (kcoro_yield), allowing other coroutines to run. It does not busy‑spin; it relies on the scheduler’s ready queue fairness.

Scope integration
- kc_scope_actor(scope, ctx): wraps kc_actor_start_ex with the scope’s cancellation token and registers an on_done callback that removes the child entry from the scope when the actor exits. This ensures scopes can cancel actors and wait for them in kc_scope_wait_all.

Testing guidelines
- Verify on_done is called exactly once.
- Cancel while blocked in receive returns promptly and produces no further callbacks.
- Stop without cancel drains no additional messages and shuts down promptly.
- Scope cancellation propagates to actors created within the scope.

Example (ctx-based API)
```c
#include "kcoro.h"

static int process_one(const void *msg, void *user) {
    /* process message */
    return 0;
}

kc_actor_ctx_t ctx = {
  .chan = my_channel,
  .msg_size = sizeof(MyMsg),
  .timeout_ms = 0,         /* non-blocking + cooperative yield */
  .process = process_one,
  .user = NULL,
};

kc_actor_t a = kc_actor_start(&ctx);
/* ... later ... */
kc_actor_stop(a);
```

