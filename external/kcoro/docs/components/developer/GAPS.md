# kcoro GAPS — What’s Missing, Why It Matters, and How We’ll Prototype It

This document is a clean, human‑readable plan. It explains what functionality is still missing, why each capability matters, and how we will prototype solutions under `lab/` before promoting them into the core library.

## Executive Summary

kcoro has strong building blocks (coroutines, channels, cancellation, zref) but needs a battle‑ready distributed story: coroutine‑native IPC, transport abstraction, multiplexing/ordering and flow control, and remote select readiness. We will deliver these incrementally, proving them in labs first.

## Architectural Principles

- Core constraints: ANSI C + POSIX only; BSD‑3‑Clause license. No shared‑memory backends in core.
- Design: channels match waiters with minimal locking; the coroutine engine decides dispatch (inline vs scheduler).
- Transport layering: IPC/networking carry channel semantics without changing application APIs.

## Scope Boundaries

Out of scope for core
- Shared‑memory region backends; any kernel‑adjacent optimizations (separate projects only).
- Shipping TCP/QUIC/multicast in core. Those live as out‑of‑tree adapters over a small vtable.

Production readiness
- Functional: remote ops mirror local semantics (return codes, timeouts, cancel, close).
- Performance: no head‑of‑line blocking; fairness across many channels; bounded memory.
- Operational: clean boundaries; minimal default logging; standard observability hooks.

---

## Docs & Quality Gaps (Fix Before Ship)

- Constant/enum coverage:
  - Ensure every public constant, enum, and feature flag in `include/` has a 2–3 line purpose note and usage guidance.
- Error semantics quick‑ref:
  - Add a one‑pager with unified tables for send/recv (copy and zref), including try/timeout/cancel/close cases.
- Install surface:
  - Provide an install manifest listing stable headers. Exclude labs, benches, internal headers, and port backends from installation.
- Include hygiene:
  - Add a static check in CI to fail any core file that includes kernel or platform‑specific headers (`linux/*`, non‑portable `sys/*` beyond POSIX). Keep core strictly ANSI/POSIX.
- Logging policy:
  - Reaffirm runtime logging only (`KCORO_DEBUG`); purge compile‑time debug forks.

Comparative insights (generic, no external naming)
- Selection fairness:
  - Current default favors first observed ready clause; consider an opt‑in unbiased/round‑robin variant for contention scenarios.
- Undelivered element hooks:
  - For conflated/close paths, consider an optional tooling‑only callback to observe drops for diagnostics (ownership stays with producer unless delivery succeeded).
- Overflow policy documentation:
  - Ensure BUFFERED and CONFLATED semantics (overwrite vs block) are clearly documented for integrators; add examples.

Acceptance Criteria for These Gaps
- Unbiased select mode demonstrates fair winner distribution in contention tests without regressing default mode.
- Undelivered callback fires exactly once per drop; documented ownership and lifetime rules; zero surprises in shutdown paths.
- Overflow policy pages include examples and return code tables; tests cover SUSPEND/DROP_OLDEST/DROP_LATEST.

Exit criteria
- Lint verifies doc coverage for constants/enums in headers; CI blocks on violations.
- CI job enforces include hygiene and runs sanitizers on the default test suite.
- `make install` produces a minimal, stable header set.

## Phase N1 — Coroutine‑Native IPC (POSIX Sockets)

Problem
- The current IPC path uses blocking bridges (pthread condvars). It cannot exploit cooperative scheduling, multiplexed in‑flight requests, or fair queuing.

Approach
- One full‑duplex connection per peer. Two coroutines per connection: `rx_co` (frame reader/dispatcher) and `tx_co` (frame serializer). Both are non‑blocking; on EAGAIN they yield/park rather than blocking threads.
- RPC correlation via 32‑bit `req_id`: each request carries a unique `req_id`; the server echoes it. Clients await completion through a small awaiter map keyed by `req_id`.
- Preserve channel semantics end‑to‑end: map EAGAIN/ETIME/ECANCELED/EPIPE exactly.

Interfaces (lab draft)
```c
typedef struct kc_rpc_future kc_rpc_future_t;

int kc_rpc_send_async(kc_ipc_conn_t *c,
                      uint16_t cmd, uint32_t chan_id,
                      const void *payload, size_t len,
                      uint32_t *out_req_id,
                      kc_rpc_future_t **out_future);

int kc_rpc_await(kc_rpc_future_t *fut, long timeout_ms,
                 const kc_cancel_t *cancel,
                 int *out_rc, void **out_payload, size_t *out_len);
```

Exit criteria
- Many concurrent RPCs complete correctly; cancellations resolve promptly; no worker thread blocks on I/O; POSIX transport passes tests.

---

## Phase N2 — Transport Vtable (Pluggable Backends)

Problem
- We must keep channel/IPC logic stable while enabling new transports (TCP/TLS, QUIC, multicast) out‑of‑tree.

Approach
- Define a tiny vtable: `connect/listen/accept`, `send_frame/recv_frame` (non‑blocking), and `poll_handle()`.
- Implement POSIX UDS first. Labs call through the vtable; future adapters plug in without touching higher layers.

Exit criteria
- lab IPC runs unchanged via the vtable; POSIX backend serves as the reference.

---

## Phase N3 — Multiplexing & Per‑Channel Ordering

Problem
- One connection must serve many logical channels fairly, without starvation or pathological tail latency.

Approach
- Fair transmit queue across channels (round‑robin or deficit‑round‑robin).
- Per‑channel sequence number carried on frames for observability and, in future datagram transports, gap detection.

Exit criteria
- Under stress, channels share bandwidth fairly; per‑channel seq is monotonic on stream transports; latency stays within budget.

---

## Phase N4 — Flow Control (Credit Windows)

Problem
- Without backpressure, fast senders can cause head‑of‑line blocking or unbounded memory when receivers are slow.

Approach
- Per‑channel credit windows. Sender consumes credits on send; receiver grants credits as it dequeues.
- Piggy‑back credit updates where possible to minimize extra frames.

Exit criteria
- Memory usage remains bounded; throughput remains stable with mixed fast/slow channels.

---

## Phase N5 — Remote Select Readiness

Goal
- Selection should feel identical for local and remote channels. Clients subscribe to readiness; the first ready clause wins via a claim protocol.

Event publication (lab draft)
```c
typedef enum {
    KC_SELECT_SEND_READY,
    KC_SELECT_RECV_READY,
    KC_SELECT_CLOSED
} kc_select_event_type_t;

typedef struct kc_select_subscription {
    uint32_t channel_id;
    uint32_t select_id;
    kc_select_event_type_t event_mask;
} kc_select_subscription_t;
```

Proto extensions (reserved until implemented)
```c
enum kcoro_cmd {
    KCORO_CMD_SELECT_REGISTER = 32,
    KCORO_CMD_SELECT_CANCEL   = 33,
    KCORO_CMD_SELECT_NOTIFY   = 34,
    KCORO_CMD_SELECT_CLAIM    = 35,
};
```

Exit criteria
- Multi‑clause remote selects resolve promptly; no double‑resume; cancellation/timeouts mirror local select.

---

## Risks & Mitigations

Head‑of‑line blocking
- Mitigate with fair tx queue and per‑channel credit windows; isolate slow channels.

Over‑dispatching
- Prefer inline resumption for short continuations; enqueue on scheduler when fairness or safety requires it. Validate with microbenchmarks.

Future transport compatibility
- Keep the wire framing transport‑agnostic. Use per‑channel seq and gap detection for datagrams; define a clean vtable.

Integration complexity
- Integrate incrementally with mock transports; add automated regression and perf testing in labs.

---

## Lab Prototypes

- `lab/ipc/posix_co/`: coroutine‑native IPC harness (rx/tx coroutines, awaiter map, req_id, cancel/timeout).
- `lab/ipc/sim/`: impairment simulator (latency/jitter/drop/dup/OOO) to test seq/credit logic.
- `lab/select/`: remote readiness publisher + claim protocol under contention.
- `lab/mux/`: multiplexing and credit windows; fairness and memory‑bound tests.

Graduation
- D1 (N1): req_id correlation + rx/tx coroutines (PASS: concurrency & cancel)
- D2 (N2): transport vtable + POSIX backend (PASS: labs unchanged via vtable)
- D3 (N3/N4): per‑channel seq + credits + readiness (PASS: fairness, no drops)
- D4: docs + examples; promote stable pieces to core.


---

## Gap Analysis Snapshot (2025-09-27)

The following section is transferred from `src/orphaned_docs/GAP_ANALYSIS.md` to keep a dated, granular snapshot of the state during the rewrite.

# kcoro Gap Analysis (2025-09-27)

Purpose: Enumerate every legacy claim vs. what kcoro actually includes today. Drives rewrite scope and prevents aspirational leakage into new docs.

Legend: P = Present (implemented in shared legacy code and used), Pa = Partial (usable but incomplete), M = Missing (absent), F = Future candidate (explicitly planned but not started), O = Omit (will not be pursued / out of scope for now).

## 1. Coroutine Core
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Private-stack coroutines with create/resume/yield/yield_to/destroy | P | `kcoro_core.c` shared + tests | Document in COROUTINE_DESIGN.md |
| Guard page for stack overflow | P | Modified allocation in shared create path (guard + mprotect) | Document + note single guard page |
| Canary & SP bounds instrumentation | P | `KCORO_CTX_DIAGNOSTICS` additions | Document diagnostics + runtime env gate |
| Multi-arch switchers | M | Only ARM64 assembly present | Roadmap entry (F) |
| LR history / advanced tracing | M | Not implemented | O (no doc until exists) |

## 2. Scheduler
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Work-stealing deques + fast-path slot | Pa | Header metrics reference; need to confirm underlying file; assumed shared from legacy | Verify source before deep design doc; minimal mention until local copy or audit snippet added |
| Random victim bounded probe stealing | Pa | Macros & stats placeholders | Mark partial; roadmap reliability tests |
| Per-worker ready dequeue + inject queue | Pa | Referenced in legacy docs, not isolated here | Document as "shared legacy scheduler (partial audit)" |
| Timer wheel integration | M | Timer token types only | Roadmap (F) |
| Inline vs enqueue continuation policy | Pa | Mentioned; not verified in code path audit | Include only if code confirms; else mark TBD |

## 3. Channels & Select
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Four channel kinds (RENDEZVOUS/BUFFERED/CONFLATED/UNLIMITED) | M | No sources in kcoro | Explicitly mark absent in README & future CHANNELS doc once ported |
| Waiter queues with parking for infinite waits | M | Absent | F |
| Select clause registration & exact-once claim | M | Absent | F |
| Zero-copy descriptor send/recv integrated with channels | M | Absent | F |

## 4. Zero-Copy / Region Registry
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Descriptor API (kc_chan_send_desc/recv_desc) | M | Not in kcoro | F |
| Backend factory registration | M | Not in kcoro | F |
| Region ID registration & refcounting | M | Not in kcoro | F |
| Pointer rendezvous path | M | Not in kcoro | F |

## 5. Cancellation / Scopes / Actors
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Hierarchical cancellation (parent→child) | M | No cancellation sources in kcoro | F |
| Scopes managing child coroutines | M | Absent | F |
| Actor helper wrappers | M | Absent | F |
| Cancellation precedence (cancel > timeout) | M | Absent | Reference only in roadmap |

## 6. Error Semantics
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Unified negative errno mapping (EAGAIN/ETIME/ECANCELED/EPIPE/ENOTSUP) | M (in kcoro) | Core files here don't expose ops returning those; exist only in legacy channels | ERROR_SEMANTICS.md will scope only currently used return codes (likely 0 / internal errors) until channel port |

## 7. Metrics & Observability
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Scheduler counters (tasks_submitted, steals_*) | Pa | API in `kcoro_sched.h` | Include current subset; mark unverified counters clearly |
| Event ring / tracing | M | Not present | F |
| Per-op channel counters | M | Channels absent | F |

## 8. Configuration / Build
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Compile-time deterministic config (macros) | P | Build flag `KCORO_CTX_DIAGNOSTICS` | Document in CONFIGURATION.md |
| Runtime debug toggle env (KCORO_DEBUG_CTX_CHECK) | P | Diagnostic code branch | Document |
| Additional runtime tunables (steal scan, etc.) | Pa | `KC_SCHED_STEAL_SCAN_MAX` macro exists | Document with default & override guidance |

## 9. Reference Runtime Parity
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| CoroutineContext key/value storage | M | Not implemented | Remove from design claims; possible roadmap item |
| Structured jobs / builders auto-closing | M | Not implemented here | Roadmap (F) |
| Dispatcher variants (IO, Unconfined, Single) | M | Not implemented | F |

## 10. Safety / Diagnostics
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Stack overflow detection via guard page | P | mprotect low page | Document |
| Canary corruption abort | P | diag check code | Document |
| SP bounds validation | P | diag code | Document |
| Frame pointer / LR history capture | M | Not present | O for now |

## 11. Testing
| Claim | Status | Evidence | Action |
|-------|--------|----------|--------|
| Full channel & select test suite | M | Not present | Roadmap |
| Basic coroutine tests | P | `test_simple.c`, production test | Keep & add diagnostics smoke test |
| Benchmark harness / perf counters | M | Simple loop only | Roadmap placeholder |

## 12. Summary of Required New Docs
| Doc | Scope | Notes |
|-----|-------|-------|
| COROUTINE_DESIGN.md | Core structure, ABI assumptions, diagnostics | Avoid scheduling & channels except references |
| SCHEDULER_DESIGN.md | Minimal: clarify shared dependency + what’s actually used | Delay deep internals until in-tree copy or verified audit |
| CHANNELS.md (future) | Defer until channel code port | Placeholder stub maybe |
| ZERO_COPY.md | Defer; create stub summarizing absence | Roadmap pointer |
| ERROR_SEMANTICS.md | Present return codes only | Expand later |
| CONFIGURATION.md | Build flags + env vars + macro override examples |  |
| CODING_STYLE.md | Concrete style rules from current code |  |
| ROADMAP.md | Table of F items with acceptance criteria |  |
| DIAGNOSTICS.md | Build/runtime usage, failure modes, examples | Could merge into configuration; decide soon |

## 13. Immediate Actions (Accepted)
1. Write COROUTINE_DESIGN.md referencing gap table links.
2. Minimal SCHEDULER_DESIGN.md (explicitly states reused implementation + pending isolation task).
3. CONFIGURATION.md + DIAGNOSTICS.md.
4. ERROR_SEMANTICS.md (slim, current only). 
5. ROADMAP.md referencing each F status with acceptance tests.
6. Add diagnostics smoke test.

## 14. Out-of-Scope / Deferred
- Advanced tracing (LR history, event ring) until instrumentation layer is designed.
- Region registry & zero-copy until channels exist locally.
- Reference context/dispatcher compatibility until base feature ports complete.

---
Generated: 2025-09-27. Update whenever implementation state changes.
