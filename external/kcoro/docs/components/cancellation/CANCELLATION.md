# Cancellation — Tokens, Contexts, and Polling Semantics

This document captures the cancellation semantics and primitives used across kcoro components (channels, select, scopes, and cancellable APIs).

Tokens and contexts
- `kc_cancel_t` is a triggerable cancellation token.
- `kc_cancel_ctx_t` chains a child token to a parent for hierarchical propagation (parent→child).

Contracts
- Cancellation is cooperative: blocking operations check tokens at well‑defined points and return `KC_ECANCELED` promptly when set.
- Precedence: when both timeout and cancellation are in play, cancellation takes precedence.

Polling cadence
- When a backend lacks a native cancellable primitive, `_c` variants poll cancellation at `KCORO_CANCEL_SLICE_MS` intervals (bounded yielding; no busy wait). This provides timely cancellation without busy‑spinning.

Select & cancellation interaction
- Select observes cancellation precedence: if a cancellation token is triggered while a select is waiting, the select returns `KC_ECANCELED` and unregisters outstanding clause registrations.
- Select implementations often integrate cancellation checks into the registration/wait loop so that cancellation can short‑circuit blocking waits and ensure exactly‑once semantics for wake/unlink paths.

Scope propagation
- Structured scopes (`kc_scope_t`) embed a cancellation context and expose a scope token that child coroutines and actors inherit so parent cancellation propagates to children.
- `kc_scope_cancel()` triggers the scope token, marks shutdown, and cancels registered actors; coroutines are expected to observe the token and exit cooperatively.

Edge cases
- Cancellation during provisional reservations (e.g., select or zref provisional slots) must roll back any tentative state before returning ownership or freeing waiter nodes.
- Cancellation racing with close/timeout must obey precedence rules and avoid double‑free or dangling‑pointer exposures.

API notes (examples)
- `kc_cancel_init/trigger/is_set/destroy`
- `kc_cancel_ctx_init/destroy` — chain parent→child propagation
- `_c` suffixed operations (e.g., `kc_chan_send_c`) indicate cancellable variants that poll the cancellation token.

Usage sketch
```c
#include "kcoro.h"

kc_cancel_t *t = NULL;
if (kc_cancel_init(&t) == 0) {
  /* pass t into *_c operations; they will return -KC_ECANCELED promptly */
  int rc = kc_chan_recv_c(ch, &msg, -1, t);
  kc_cancel_trigger(t);
  kc_cancel_destroy(t);
}
```

Acceptance
- Cancellation must be observed promptly (within a bounded poll slice) and must not leak waiter nodes or leave provisional channel state inconsistent.
