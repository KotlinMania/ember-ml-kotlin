# kcoro Engineering Journal

Chronological notes capturing design decisions, implementation steps, and validation results. This journal complements PLAN.md and the design docs.

## 2025‑09‑22 — zref-only backend, tests green, docs polish

- Removed the separate ptr backend; unified on a single “zref” backend that implements rendezvous and queued descriptor paths.
- Canonicalized on descriptor APIs (`kc_chan_send_desc/_recv_desc` + `_c` variants); `*_ptr` / `*_zref` functions are thin wrappers that compose `kc_zdesc`.
- Purged compile‑time debug forks (`KCORO_DEBUG_SCHED`, `ZREF_DBG*`) and fossil `#if 0` blocks; kept runtime `KCORO_DEBUG` logging.
- Fixed pointer rendezvous lifecycle in tests via deterministic drain/join; default test suite is green across buffered/rv/zref/timeout/scheduler.
- Docstring pass across headers and kc_zcopy.c (2–3 line explanations for constants; clear file‑level prologues). Added OS‑neutral prologue to `kcoro_port.h`.
- Updated PLAN.md with a status section and focused N1/N2 next steps.

## 2025‑09‑21 — Zero‑Copy Surface Unified; Monitor Uses Real Stats (update)

- Centralized zero‑copy under a neutral descriptor (`kc_zdesc`) and backend vtable in `kcoro_zcopy.h`.
- Implemented backend factory and a single built‑in backend in `kc_zcopy.c`:
  - `zref` unified backend:
    - Rendezvous: hand‑to‑hand
    - Buffered/Unlimited/Conflated: queued descriptor path (former ptr path)
- `kc_chan.c` pointer‑first ops update bytes/ops via shared helpers; rendezvous routes through backend.
- kcoro_chanmon now reads `kc_chan_snapshot()` and uses `kc_chan_compute_rate()` to show true PPS/GBPS and failure deltas. Headless NDJSON output added.
- Added “pre‑kill” hook before builds/tests to eliminate lockups from lingering test binaries; wrapped each test with a timeout; heavy stress kept under `test-full` with a longer timeout.
- Tests passing in default suite: buffered pointer basic; zref basic; zref timeout; failure counters; scheduler basic.
- Ptr rendezvous test: still failing on length assertion; root cause narrowed to rendezvous wake + capability bit; fixed recv wake (pop from `wq_recv_head`) and set ZERO_COPY bit on backend bind; remaining issue under investigation.
- Performance spot‑check (local): headless chanmon with P=1/C=1, N=20000, size=512 shows ~78 Gbps sustained (single‑process synthetic scenario).

Next:
- Fix pointer rendezvous waiter lifecycle (single free owner in wake helpers); re‑enable pointer RV test by default.
- Document adapter guidelines and region API expectations.
- Optional: CI guard against non‑POSIX or kernel headers in core; sanitizer run for waiter misuse.

## 2025‑09‑20 — Metrics & Monitor Migration

- Refactored monitor to use snapshot + rate helper instead of ad-hoc loops.
- Ensured per-op counters update on every success (ops and bytes) so deltas are precise.
- Added failure counters to the snapshot; monitor displays deltas alongside PPS/GBPS.

---
Notes
- Dates reflect repository local time. Entries summarize the primary user-visible changes plus engineering guardrails added for stability.

## 2025‑10‑06 — Rendezvous Waiter Strategy Musings

Today’s deep dive needed to be captured in narrative form, so I sat down and transcribed my own train of thought verbatim:

> Here’s how I’d push past the Kotlin template and solve the rendezvous bug while strengthening kcoro’s zero-copy story:
> 
> - Treat waiters as first-class descriptors. Define a `kc_waiter_token` (kind, coroutine, zref flags, cancellation hooks, enqueue link) plus an `enum kc_waiter_status { KC_WAITER_INIT, ENQUEUED, CLAIMED, CANCELLED }`. Every enqueue returns the token; the receive path becomes “construct token → call `kc_chan_waiter_publish()` → branch on returned status”. No ad-hoc `int` flags, so the “declare `waiter_enqueued`” issue disappears.
> 
> - Give rendezvous its own cell record. For `capacity == 0`, manage a single `kc_rv_cell` with a state enum (`EMPTY`, `SENDER_READY`, `RECEIVER_READY`, `MATCHED`, `CANCELLED`). Sender publishes a descriptor (payload or `kc_zdesc`) and advances to `SENDER_READY`; receiver CAS-es to `MATCHED` and consumes the descriptor. Because the state encodes “waiter present?”, there’s no separate boolean to forget.
> 
> - Make zero-copy first-class: extend the cell payload to `union { kc_payload ptr; kc_zdesc z; }` plus a type tag. Rendezvous matches can move `kc_zdesc` ownership without staging into a copy buffer. Buffered channels can hold either raw bytes or descriptors, so zref stays seamless.
> 
> - Collapse send/receive bookkeeping into a single waiter stack. A helper like `kc_chan_waiter_scope`, initialized with the token, registers cancellation and ensures `ENQUEUED` or `CLAIMED` translates into the right release; if the token never leaves `INIT`, no cleanup occurs. That scope guard replaces scattered flips of `waiter_enqueued`.
> 
> - Harmonize buffering modes. Build a “slot engine” inspired by Kotlin’s segments but adapted for zero-copy: each slot holds `{ state, waiter*, payload union }`. Rendezvous uses slot #0; buffered/unlimited map indices to slots; zref descriptors ride in the same union.
> 
> - Integrate cancellation strongly. Each `kc_waiter_token` gets a pointer to its cancellation thunk. That thunk can inspect the token status and perform precise removal or undelivered-element callbacks, making cancellation and cleanup independent of any forgotten boolean.
> 
> - Finally, mirror Kotlin’s cell state machine in a shared helper (`kc_chan_cell_try_resume()`), but add zero-copy-specific transitions (pointer hand-off, descriptor reuse). With the state machine owning all enqueue/dequeue signaling, there’s no room for stray flags.
> 
> This architecture fixes the compilation failure by eliminating the stray `waiter_enqueued` flag, and it pushes kcoro ahead of Kotlin by making zero-copy semantics and multi-type slots native concepts rather than bolt-ons.

### Lab follow-up (same day)
- Added `lab/lab_waiter_token.c` and supporting Makefile wiring to prototype the waiter token + rendezvous cell state machine independent of the production channel code.
- Extended the lab to track cancellation/match counters and emulate zero-copy descriptors via a payload union, mirroring the invariants from `ZERO_COPY.md`.
- Scenarios covered: sender-first hand-off, receiver cancellation, sender matching a parked receiver, and a synthetic zref hand-off. Each prints state + metrics so we can diff against Kotlin traces as we iterate.
- Binary: `make -C external/kcoro/lab build/lab_waiter_token && external/kcoro/lab/build/lab_waiter_token`. Output confirms the token helper logic before integration.

### Kotlin parity notes (2025-10-06 evening)
- Re-read `BufferedChannel.kt` (latest snapshot under `tools/kotlinx.coroutines/…`) to confirm Kotlin’s rendezvous semantics: every send stores the element into the segment, transitions cell state via CAS, and either buffers (`BUFFERED`) or resumes a waiting receiver (`Waiter`). No side flags or pointer tricks—just the cell state machine.
- Receivers mirror the flow: they CAS empty cells to install a waiter, or, when a sender already published, move the state to `DONE_RCV` and pull the element back out. Cancellation paths rewrite the cell state to `INTERRUPTED_*` with the same CAS helpers.
- Action plan captured: introduce a `kc_rv_cell` struct in C mirroring the state machine (state enum, payload union, metrics), drive send/recv through helper functions, and let the zero-copy backend register descriptors when needed. Buffered and rendezvous paths will eventually converge on the same slot engine, just like Kotlin’s segments.

## 2025-10-07 — Arena Allocation + BizTalk Notes

- Studied Microsoft BizTalk’s MessageBox architecture. Core ideas worth borrowing: durably log every “message” fragment, hydrate/dehydrate orchestration state via the database, and scale out by sharding MessageBoxes with redundant host instances and DB clustering. Reliability comes from keeping the queue inspectable and recoverable.
- For kcoro/zref we keep the hot path in RAM, but we mimic the “fragment ledger”: carve a RAM arena into fixed-size pages, track allocations via a ticket table, and let kernel-style workers hydrate/cancel payloads by pointer. Optional journaling later could give BizTalk-grade durability without mucking up the fast path.
- Drafted component-aligned pseudocode to capture the structure:

```
# Zero-Copy Arena & Allocation Table
initialize_zref_arena(total_bytes, page_size)
publish_to_rendezvous(cell, payload_bytes)
hydrate_ticket(ticket_id)
release_ticket(ticket_id)

# Scheduler / Kernel Workers
kernel_worker(): loop SEND/RECEIVE/CANCEL events, call the helpers, emit metrics

# Metrics / Observability
snapshot_metrics(), dump_allocation_table()

# Zero-Copy Backend Registration
register_external_region(ptr, length, flags)
```

- Key mapping to our components:
  * **Zero-Copy & Descriptor backends** own the arena and ticket lifecycle.
  * **Channels & Select** enqueue tickets in rendezvous cells instead of raw buffers.
  * **Scheduler & Timers** hydrate tickets when receivers wake, just like BizTalk hosts pulling from MessageBox.
  * **Metrics & Observability** expose match/cancel/zdesc counters for chanmon/admin tooling.
  * **Cancellation/Scopes/Actors** release tickets through the same path, keeping cancellation semantics consistent.
  * **IPC & Transport** can register external regions so shared-memory transports participate without copies.

- BizTalk analogies that stay relevant:
  * Message fragments ↔ arena pages; tickets ↔ correlation IDs.
  * Hydration/dehydration ↔ retaining/releasing waiters via kernel workers.
  * Host cluster ↔ multiple scheduler instances watching the same queue with redundant arena guards.
  * MessageBox inspection ↔ dump_allocation_table / metrics snapshot for tooling.

- Next investigation items to reach BizTalk-level reliability without losing nanosecond performance:
  * Nail down the allocator (buddy/slab) and ticket bit layout so handles stay ≤16 bytes.
  * Prototype how we’d journal allocation events if we want persistence or replication.
  * Extend chanmon to read the arena/ticket metrics, giving us the BizTalk-style queue inspector.
  * Design cancellation integration so scope/actor layers can hydrate/dehydrate tickets cleanly under heavy load.
