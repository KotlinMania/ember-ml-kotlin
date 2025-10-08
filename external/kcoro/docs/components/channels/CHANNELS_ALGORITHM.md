# Channels — Clean‑Room Algorithmic Overview (As‑Built)

Scope
- Conceptual model of kcoro channels and select. This is a clean‑room description of the runtime’s as‑built algorithms; it does not quote or derive code from other projects.

Notation
- Sender (S): producer side of a channel.
- Receiver (R): consumer side of a channel.
- Rendezvous slot (Z): single handoff cell used when capacity = 0.
- Buffer ring (B): circular queue of slots when capacity > 0.
- Waiter queues (WqS, WqR): per-side queues of suspended coroutines (WqS holds senders; WqR holds receivers).
- Deadline (D): absolute monotonic time when bounded waits give up with ETIME.
- Cancel (C): cancellation token that aborts waits with ECANCELED.
- “Park” means suspend current coroutine; “Unpark(x)” means make coroutine x ready.

Features snapshot (non‑duplicative):
- Kinds: rendezvous, bounded, conflated, and unlimited.
- Blocking operations cooperate via scheduler wait queues; timeouts use monotonic deadlines; cancellation returns `KC_ECANCELED`. Non‑blocking try variants never block and return precise status.
- Zero‑copy handoff supported.
- Inherent metrics with snapshot/push surfaces.
- Forward evolution plan and overflow policies.

---

## 1. Rendezvous (capacity = 0)

State
- Z has fields: ready(bool), elem (for copy) or (addr,len) for zref, epoch(u64).
- Per‑side queues: WqS (senders), WqR (receivers).

Send (copy or zref)
1) Lock; if closed → EPIPE.
2) If Z.ready == 1 → enqueue waiter(WqS) and Park (infinite) or Yield until D (bounded) → retry/ETIME.
3) If WqR non‑empty → pop first receiver r; publish elem into Z; set ready=1; Unpark(r); return 0.
4) Else (no receiver yet):
   - Infinite: publish elem to Z, set ready=1, enqueue self to WqS, Park; on wake, clear expected flag; if closed and still ready → EPIPE; return 0.
   - Bounded/Try: do not publish early; enqueue self (bounded yields) or return EAGAIN. Retry until D.

Receive
1) Lock; if Z.ready == 1 → consume elem; clear ready; update last_consumed_epoch; pop one sender s from WqS if present and Unpark(s); return 0.
2) If closed and empty → EPIPE.
3) If WqS empty → enqueue self to WqR and Park (infinite) or bounded‑yield until D (bounded). On timeout → ETIME; on cancel → ECANCELED.

Exactly‑once rules
- Only the receiver clears Z.ready and unparks exactly one sender.
- Only the sender that published early parks; it is awakened by the receiver that consumes.

---

## 2. Bounded Buffer (capacity = N > 0)

State
- Ring B with head/tail indices and count; WqS/WqR waiters.

Send
1) If count < N: enqueue elem; count++; if WqR non‑empty → pop one and Unpark(). Return 0.
2) Else if WqR non‑empty: perform direct hand‑off (publish elem to the waiting receiver’s slot) and Return 0.
3) Else: Try → EAGAIN; Bounded → bounded‑yield until D then ETIME; Infinite → enqueue waiter in WqS and Park.

Receive
1) If count > 0: dequeue elem; count--; if WqS non‑empty → pop one and Unpark(). Return 0.
2) Else if WqS non‑empty: perform direct hand‑off from pending sender. Return 0.
3) Else: Try → EAGAIN; Bounded → bounded‑yield until D then ETIME; Infinite → enqueue waiter in WqR and Park.

---

## 3. Conflated (latest‑value)

State
- Single slot S; WqR for receivers; WqS rarely used (only for strict rendezvous emulation).

Send
- Overwrite S with latest elem; if any receiver waiting → Unpark one. Send never blocks (unless policy says otherwise).

Receive
- If S present → take and Return 0; else follow bounded/infinite/try rules with WqR as for other kinds.

Note
- If overflow policy is DROP_* (see Plan), Conflated is a special case of DROP_OLDEST with capacity 1.

---

## 4. Select (multi‑clause)

Claim protocol
1) Register a single waiter token across candidate channels (WqS/WqR as appropriate). The token contains a shared “claimed” flag and the winning index.
2) A channel that becomes ready attempts to atomically set “claimed(index)”.
3) Winner: unlink token from other queues and resume the continuation (Unpark or inline resume policy). Losers: see the claim and unlink silently (no resume).

Fairness
- Default policy favors first observed ready clause in probe order. An unbiased/round‑robin policy can randomize or rotate the starting index.

Timeouts & cancellation
- Select observes the earliest deadline across clauses; cancellation token wake returns ECANCELED.

---

## 5. Waiter Lifecycle & Safety

Creation
- A waiter is allocated when a coroutine must block; it carries: kind(SEND/RECV), clause index (select), pointer to coroutine, and a magic/tombstone for diagnostics.

Ownership
- Exactly‑once: the wake path that resumes a waiter owns freeing it. Select losers unlink without freeing (winner’s wake frees).

Disposal
- On close: drain WqS/WqR, resume waiters with EPIPE, and free their nodes via a single disposal site (preventing double‑free).

API Contracts (channel primitives)

Header references (orientation)
- Public prototypes live in src/kcoro/include/kcoro.h.
- Zero‑copy descriptor APIs live in src/kcoro/include/kcoro_zcopy.h.

Canonical signatures (from headers)
```c
#include "kcoro.h"

int  kc_chan_make(kc_chan_t **out, int kind, size_t elem_sz, size_t capacity);
void kc_chan_destroy(kc_chan_t *ch);
int  kc_chan_send(kc_chan_t *ch, const void *msg, long timeout_ms);
int  kc_chan_recv(kc_chan_t *ch, void *out, long timeout_ms);
void kc_chan_close(kc_chan_t *ch);
int  kc_chan_send_c(kc_chan_t *ch, const void *msg, long timeout_ms, const kc_cancel_t *cancel);
int  kc_chan_recv_c(kc_chan_t *ch, void *out, long timeout_ms, const kc_cancel_t *cancel);
```

Descriptor (zero‑copy) API excerpt
```c
#include "kcoro_zcopy.h"

typedef struct kc_zdesc { void *addr; size_t len; uint64_t region_id; uint64_t offset; uint32_t flags; } kc_zdesc_t;
int kc_chan_send_desc(kc_chan_t *ch, const kc_zdesc_t *d, long timeout_ms);
int kc_chan_recv_desc(kc_chan_t *ch, kc_zdesc_t *d, long timeout_ms);
```

The channel component exposes the following expected behaviors which callers and other components rely on. These contracts are required to keep the channel component self-contained and authoritative:
- `kc_chan_send(ch, elem, timeout_ms)` — attempt to send an element. On success returns 0. On non-blocking try returns `KC_EAGAIN`. On bounded timeout returns `KC_ETIME`. If the channel is closed before a match and no transfer can be performed, return `KC_EPIPE`.
- `kc_chan_recv(ch, out_elem, timeout_ms)` — attempt to receive an element and place it into `out_elem`. Return codes mirror `kc_chan_send` semantics.
- `kc_chan_send_zref` / `kc_chan_recv_zref` — zero-copy variants that transfer pointer ownership by descriptor; on success return 0 and transfer ownership to the receiver. On unsupported backend return `KC_ENOTSUP`.
- `kc_chan_close(ch)` — mark channel closed; wake all waiters and return EPIPE where appropriate; subsequent sends fail fast with `KC_EPIPE`.

Waiter lifecycle (expanded)
- Allocation: a waiter node is created when a coroutine must block (send or receive). The node includes kind, clause/select index if relevant, coroutine pointer, magic/tombstone for diagnostics, and optional zref flags.
- Enqueue: the waiter is appended to the corresponding WqS/WqR list while the channel lock is held.
- Wake owner: the wake path that resumes a waiter is responsible for freeing its node. This exact-ownership rule prevents double-free and ensures deterministic cleanup.
- Select interaction: when a select registers a waiter across multiple channels, a single token object is used and the winner clears other registrations. Losing registrations are unlinked without freeing the shared token; the winner’s wake frees the token.


---

## 6. Dispatch (where resumed work runs)

Inline vs enqueue
- Short continuations may resume inline on the current worker to minimize latency; otherwise they are enqueued on the ready queue to preserve fairness. This is an internal policy; API semantics are unchanged.

---

## 7. Error Semantics (recap)

Try → EAGAIN; Bounded deadline → ETIME; Cancel → ECANCELED; Close on empty/no match → EPIPE; Unsupported feature → ENOTSUP. Ownership remains with the sender on failure.

---

## 8. ASCII State Sketches

Rendezvous send (infinite)
```
LOCK
if closed → EPIPE
if ready → enqueue self in WqS; UNLOCK; PARK → wake → LOCK; post‑check close; UNLOCK; return
if WqR non‑empty → publish; ready=1; Unpark(r); UNLOCK; return 0
// sender‑first publish
publish; ready=1; enqueue self WqS; UNLOCK; PARK → wake → LOCK; clear expect; UNLOCK; return 0
```

Rendezvous recv
```
LOCK
if ready → take; ready=0; Unpark(one from WqS); UNLOCK; return 0
if closed → UNLOCK; EPIPE
enqueue self WqR; UNLOCK; PARK → wake → retry
```

Bounded send (try)
```
LOCK
if count<N → push; if WqR non‑empty Unpark(1); UNLOCK; return 0
if WqR non‑empty → direct hand‑off; UNLOCK; return 0
UNLOCK; return EAGAIN
```

---

## 9. Internal Structures (as of kc_chan_internal.h)

This section summarizes the key fields of the internal channel object to clarify how the algorithms are realized. Names here mirror the C structure for unambiguous mapping.

- mu, cv_send, cv_recv: mutex/condvars; condvars are used only for legacy/bridge paths; coroutine‑native operations prefer waiter queues and park/unpark.
- closed: terminal flag; close drains waiters with EPIPE.
- kind/capacity/elem_sz/mask: shape of the channel. mask is capacity-1 when power‑of‑two for cheap modulo via bit‑and.
- Ring buffer: buf (capacity*elem_sz), head/tail/count. Head is next recv index; tail is next send index.
- Conflated: slot (single element storage) + has_value flag.
- Waiter queues (WqS/WqR): singly‑linked FIFO per side, with head/tail pointers and best‑effort counters waiters_send / waiters_recv (hints only).
- Capabilities: capabilities bitmask; zref_mode toggles rendezvous pointer handoff.
- Rendezvous zref scratch: zref_ptr, zref_len, zref_ready, zref_sender_waiter_expected, zref_epoch, zref_last_consumed_epoch.
- Zref counters: zref_sent, zref_received, zref_fallback_small, zref_fallback_capacity, zref_canceled, zref_aborted_close.
- Inherent metrics: total_sends/recvs, total_bytes_sent/recv, first/last_op_time_ns; metrics_pipe and last_emit_* fields for push.
- Failure counters: send_eagain/etime/epipe, recv_eagain/etime/epipe.
- Emission pacing: ops_since_emit_check, emit_check_mask (power‑of‑two mask controlling periodic emission decisions under lock).
- Pointer‑descriptor mode: ptr_mode indicates elems are pointer messages; zero‑copy backend vtable (zc_ops, zc_priv, zc_backend_id) binds a runtime backend when enabled.

Invariants
- Waiter ownership is exactly‑once: the wake path frees its waiter node; close drains via a single disposal site.
- Rendezvous ready is cleared only by receivers; exactly one sender is unparked per take.
- On cancel/timeout, ownership remains with the sender and any provisional reservation is rolled back before returning to user code.
- Suspension installs a per-cell cancellation hook that CASes the cell state to `INTERRUPTED_*`, waits for buffer maintenance to finish, and only then releases the waiter. This keeps cancellation linearizable with respect to buffer expansion.
- Retain/release ordering mirrors the upstream native `BufferedChannel`: the waiter remains referenced until the receive/send resume path marks the cell `DONE_RCV` or `BUFFERED`, preventing use-after-free.

## 10. Failure Counters & Return Codes

Return codes
- KC_EAGAIN: non‑blocking try path would block (no structural changes made).
- KC_ETIME: bounded wait elapsed; no data transferred.
- KC_ECANCELED: cancellation token observed while waiting; no data transferred.
- KC_EPIPE: closed and no progress possible (e.g., recv on empty & closed, send on closed before matching a receiver).
- KC_ENOTSUP: feature or mode not available for this channel.

Counters (best effort; read via snapshot APIs described in the metrics component)
- send_* and recv_* failure counters increment on corresponding early exits.
- zref_* counters document zero‑copy usage and fallbacks.
- total_* counters advance only on successful logical operations (copy or zero‑copy).

## 11. Fairness, Direct Handoffs, and Conflation Nuances

- Direct handoff: when a sender finds WqR non‑empty on a full bounded buffer, the implementation may bypass the ring and publish directly to the receiver’s slot, reducing latency and avoiding double buffering.
- Conflated channels: sends overwrite the single slot and wake one receiver if present; sends never block under the default policy. This behaves like DROP_OLDEST(capacity=1) in an overflow‑policy view.
- Probe order and wait queues preserve intuitive FIFO for blocked operations on each side; select’s bias is captured in its own document.

---

This document is normative for channel/select behavior in kcoro; it is a clean‑room description of the algorithms that the code implements.


## Performance Targets

- Channel rendezvous send/recv: <= 350 ns (stretch 250 ns)
