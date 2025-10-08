# Select API — Multi‑Clause Choice with Cancellation & Timeouts

This document describes the select facility implemented in user/src/kc_select.c. Select allows a coroutine to wait on multiple channel operations (send and/or receive) and complete whichever can proceed first, with optional timeout and cancellation.

Public surface (selected)
- kc_select_create(&sel, cancel): allocate select object with an optional cancellation token.
- kc_select_destroy(sel): free resources.
- kc_select_reset(sel): clear clause list (retain capacity).
- kc_select_add_recv(sel, ch, out_buf): append a receive clause.
- kc_select_add_send(sel, ch, in_buf): append a send clause.
- kc_select_wait(sel, timeout_ms, &win_index, &op_result): execute selection; returns the winning operation’s result code and writes the winning clause index.

Canonical prototypes (from headers):
```c
#include "kcoro.h"

int  kc_select_create(kc_select_t **out, const kc_cancel_t *cancel);
void kc_select_destroy(kc_select_t *sel);
void kc_select_reset(kc_select_t *sel);
int  kc_select_add_recv(kc_select_t *sel, kc_chan_t *chan, void *out);
int  kc_select_add_send(kc_select_t *sel, kc_chan_t *chan, const void *msg);
int  kc_select_wait(kc_select_t *sel, long timeout_ms, int *selected_index, int *op_result);
```

Quick start example (sketch)
```c
#include "kcoro.h"

kc_select_t *sel = NULL;
int rc = kc_select_create(&sel, NULL);
if (rc != 0) { /* handle error */ }

kc_chan_t *a = /* ... */;
kc_chan_t *b = /* ... */;
int val_a = 0;
int val_b = 0;

kc_select_reset(sel);
kc_select_add_recv(sel, a, &val_a);
kc_select_add_recv(sel, b, &val_b);

int win = -1, op_rc = KC_EAGAIN;
rc = kc_select_wait(sel, /*timeout_ms*/ 1000, &win, &op_rc);
/* rc is the select call result; op_rc is the winner operation’s result */
```
Notes:
- timeout_ms: 0 → try; <0 → infinite; >0 → bounded wait.
- Cancellation (optional) is provided at kc_select_create and observed during kc_select_wait.

Internal structures (kc_select_internal.h)
- struct kc_select_clause_internal { kind (RECV/SEND), kc_chan_t* chan, union { void* recv_buf; const void* send_buf; } }
- struct kc_select {
  - clauses: dynamic array (realloc‑grown) of clause_internal.
  - count/capacity: active clauses and allocation.
  - cancel: optional kc_cancel_t* observed during wait.
  - waiter: kcoro_t* pointer to the waiting coroutine.
  - state: atomic enum { REG, WIN, CANCELED, TIMED_OUT }.
  - winner_index: atomic int written once by the winner path.
  - result: atomic int with the channel op’s status (0 or negative KC_*).
}

Execution algorithm (kc_select_wait)
1) Fast probe loop: for each clause, call non‑blocking try op (timeout=0). If any returns != KC_EAGAIN, finish immediately with that index/result.
2) If timeout_ms == 0 and nothing was ready, return KC_EAGAIN immediately.
3) Prepare to block: reset internal state (state=REG, winner_index=-1, result=KC_EAGAIN); record the waiter coroutine (kcoro_current). 
4) Registration pass: for each clause, call channel registration helpers:
   - recv: kc_chan_select_register_recv(chan, sel, i)
   - send: kc_chan_select_register_send(chan, sel, i)
   Each helper either (a) returns KC_EAGAIN and leaves a waiter token enqueued on the channel’s WqR/WqS, or (b) completes immediately with a real result, in which case kc_select_try_complete(sel, i, rc) is called.
5) Waiting policy:
   - If timeout_ms < 0 and no cancel token: park the coroutine (kcoro_park). The winning channel will unpark when it claims the select.
   - Otherwise, enter a cooperative yield loop that polls state, cancel, and a deadline computed from timeout_ms (CLOCK_MONOTONIC). On cancel, transition to CANCELED (KC_ECANCELED). On deadline, transition to TIMED_OUT (KC_ETIME). Between checks, call kcoro_yield() to cooperate with the scheduler.
6) Completion: read result and winner_index atomically, cancel outstanding registrations across other channels (kc_chan_select_cancel for each), clear waiter pointer, and return the result.

Claim protocol
- kc_select_try_complete(sel, i, rc) attempts to change state from REG→WIN atomically; only one winner succeeds. The winner writes winner_index=i and result=rc. Channels must not unpark the waiter in the registration fast path (when select is still running in the caller); they only unpark when the waiter is actually parked (registration in channel code accounts for this).

Fairness & ordering
- Probe order defines the bias. The current implementation is biased towards earlier clauses when multiple are ready at the same time. A future option may rotate the starting index for unbiased behavior.

Timeout & cancellation behavior
- Cancellation (if provided) is observed promptly in the bounded loop; result=KC_ECANCELED.
- Deadline uses a monotonic clock; result=KC_ETIME on expiry.

Memory ownership & reuse
- kc_select_reset clears the clause count for reuse; allocated storage is retained and grown geometrically as needed.
- No per‑wait heap allocation is performed by the select core beyond clause array growth.

Error mapping
- Invalid args → -EINVAL. Empty clause list → -EINVAL. Ready path returns 0 (success) or a negative KC_* consistent with channel semantics (KC_EAGAIN never reaches the caller unless timeout_ms==0 on the select call).

