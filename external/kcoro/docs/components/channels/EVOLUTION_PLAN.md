# Channel Architecture Evolution (Clean-Room Plan)

This runtime will evolve its channel implementation through staged, internally original algorithms inspired by external open-source design principles (monotonic reservation indices, policy-driven overflow, suspension safety). We intentionally avoid copying external identifiers or layouts; we re-express concepts and map them to kcoro-specific data fields and phases.

## Goals
1. Converge all channel capacity modes (rendezvous, bounded, unlimited, conflated) onto a single reservation model for ordering & observability.
2. Add selectable overflow behaviors (suspend, drop newest, drop oldest) with explicit counters and callbacks.
3. Provide low-cost snapshot & push metrics (already partially implemented) enriched with overflow/failure visibility.
4. Introduce an optional lock-free buffered backend once baseline mutex path semantics & metrics are stable.
5. Supply structured debug/invariant dump instrumentation aiding stress/fuzz validation.

## Core Concepts (kcoro Naming)
| Concept | Description |
|---------|-------------|
| Reservation Index | 64-bit monotonically increasing counter claimed by each send or receive operation to establish ordering without global locking (shadowed beside legacy counters initially). |
| Slot Cell | A ring entry containing either payload metadata, a suspended waiter token, or a terminal marker. |
| Overflow Policy | Enum influencing behavior when buffer full at reservation time (block, discard attempted element, evict oldest). |
| Snapshot Block | Packed metrics struct returned by `kc_chan_snapshot()` containing cumulative counters & failure/drop data. |

## Evolution Phases (C.*)
| Phase | Focus | Brief |
|-------|-------|-------|
| C.1 | Shadow Indices | Introduce `send_idx`, `recv_idx` updated with existing lock path for occupancy derivation. |
| C.2 | Overflow Policies | Implement policy enum + drop-oldest/newest logic + counters. |
| C.3 | Snapshot & Fail Counters | Public snapshot API + metrics pipe version bump. |
| C.4 | Debug Dump | `kc_chan_dump()` one-line structural summary; optional extended view behind config. |
| C.5 | Lock-Reduced | Replace size arithmetic with index & mask, still under mutex for correctness. |
| C.6 | Lock-Free Prototype | Atomic state machine for cells; fallback to mutex path via creation flag. |
| C.7 | Segmented Growth | Optional dynamic segment chain for large/unlimited buffers. |
| C.8 | Fairness & Backoff | Round-robin / backoff heuristics to reduce producer starvation. |

## Metrics Additions
New counters: `drops_oldest`, `drops_newest`, `undelivered`, `cancelled_waiters`. Snapshot computes occupancy via indices. Event stream extended with policy & drop deltas (versioned). All additions are additive; existing consumers remain compatible until they opt into new version.

## Overflow Behavior Matrix
| Condition | SUSPEND | DROP_NEWEST | DROP_OLDEST |
|-----------|---------|-------------|-------------|
| Buffer full, new send | Block or timeout/cancel later | Increment newest drop counter; treat send as success | Increment oldest drop counter; logically advance receive index; store payload |
| Close during wait | Wake waiter `-EPIPE` | Same | Same |
| Cancelled waiter removal | Increment `cancelled_waiters` | Same | Same |

## Debug Dump (Illustrative)
```
chan=0x1234 cap=64 policy=DROP_OLDEST occ=34 send_idx=1234 recv_idx=1200 drops_oldest=2 drops_newest=0 undelivered=0 canc_wait=1 state=OPEN
```

## Lock-Free Prototype Outline
Cell state enum (conceptual): EMPTY, PAYLOAD, WAITER_SEND, WAITER_RECV, CLOSED. Operations reserve index, map to cell, attempt CAS to publish state; fast path matches opposing waiter/payload. Cancellation transitions WAITER_* → CANCELLED and increments metrics. Memory reclamation deferred until segmented phase; initial prototype uses single fixed ring.

## Acceptance Criteria (Selected)
- C.2: Policy tests verify counter accuracy & no indefinite spin.
- C.3: Snapshot is wait-free (relaxed atomic loads only) and returns monotonically increasing send/recv counts.
- C.6: Lock-free path outperforms mutex channel ≥10% throughput under multi-producer/consumer benchmark.
- C.8: Starvation bounded (max consecutive sends by one producer below configured fairness threshold in synthetic test harness).

## Risks & Mitigations
| Risk | Mitigation |
|------|-----------|
| Atomic hotspot on indices | Per-thread delta batching post initial profiling (defer until needed). |
| Segmentation complexity vs gains | Gate behind config; keep single-ring lock-free fallback. |
| Counter version skew | Embed `metrics_version` in snapshot & event headers, reject mismatches gracefully. |

## Immediate Implementation Tasks
1. Add internal shadow indices + stub dump function (no external API yet).
2. Define overflow policy enum + update creation path & config parser.
3. Extend metrics struct & pipe event version with drop/failure counters.
4. Implement snapshot API using existing mutex-protected counters (prereq for C.5 refactor).

All changes preserve existing blocking semantics until explicit policy selection or feature flag enables the lock-reduced/lock-free backend.

