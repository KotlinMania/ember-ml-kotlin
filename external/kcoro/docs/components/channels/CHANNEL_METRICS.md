# Inherent Channel Metrics Architecture

This section defines the first‑class, always‑on metrics model for channels; it formalizes prior exploratory notes into a phased, production‑viable plan targeting both user‑space observability and optional external adapter bridges.

## Goals (What “Inherent” Means)
Counters advance automatically with every successful logical operation (send / receive, copy or zero‑copy) without relying on benchmark harnesses, coordinator loops, or mode‑specific code. They are cheap enough to leave enabled in production and expose consistent, versioned surfaces for:
1. Direct snapshots (pull)
2. Event stream subscription (push)
3. Shared memory / external adapter export (bridge)

## Requirements
Functional: each successful send/recv increments exactly once; counters persist through close; snapshot is O(1); optional push model; future kernel bridge readiness.
Non‑functional: ≤ a few extra instructions on locked path; no heap alloc per op; concurrency safety; backward compatibility with `kc_chan_get_stats()`.
Extensibility: room for latency histograms, error counters, sharding, and delta exports without ABI churn.

## Layered Architecture
| Layer | Purpose | Notes |
|-------|---------|-------|
| 0 Core Counters | Always-on cumulative fields | Minimal additions to `struct kc_chan` (sends, recvs, bytes, first/last time, failures/timeouts) |
| 1 Snapshot Surface | Cheap read API | `kc_chan_snapshot()` returns packed `kc_chan_metrics_t` |
| 2 Subscription Events | Push deltas | Aggregator coroutine emits `kc_chan_metrics_event` onto a global channel |
| 3 Adapter Bridge | External consumer | memfd/shared memory or DMA‑style frame emission |
| 4 Advanced | Latency / hist / sharding | Optional, gated by profile flags |

## Data Structures
```
#define KC_CHAN_METRICS_VERSION 1
typedef struct kc_chan_metrics {
    uint16_t version, flags; uint32_t reserved;
    uint64_t total_sends, total_recvs;
    uint64_t total_bytes_sent, total_bytes_recv;
    uint64_t first_op_time_ns, last_op_time_ns;
    uint64_t failed_sends_epipe, failed_recvs_epipe;
    uint64_t timeouts_send, timeouts_recv;
} kc_chan_metrics_t;
```
Event delta (push model):
```
struct kc_chan_metrics_event {
    uint64_t chan_id, seq, ts_ns;
    uint64_t delta_sends, delta_recvs;
    uint64_t delta_bytes_s, delta_bytes_r;
};
```

## Public API (Draft)
```
int kc_chan_snapshot(kc_chan_t *ch, kc_chan_metrics_t *out);
typedef struct kc_chan_metrics_sub { uint64_t interval_ns; } kc_chan_metrics_sub_t;
int kc_chan_metrics_subscribe(kc_chan_t *ch, const kc_chan_metrics_sub_t* opt);
int kc_chan_metrics_unsubscribe(kc_chan_t *ch);
int kc_metrics_init(void);
kc_chan_t *kc_metrics_event_channel(void);
```

## Increment Semantics
Performed inside the channel mutex (already acquired on success paths). Failure/timeout counters either move into the locked region or use relaxed atomics for early exits. Optional Phase 3 introduces per‑CPU shards folded at snapshot time.

## Aggregator Coroutine
Maintains registry of subscribed channels (export flag + interval). Awakens on a cadence equal to the smallest requested interval bucket, snapshots each, computes deltas, and publishes events. Consumers derive pps/gbps externally (no division on producer path).

## External Adapter Bridge (Forward View)
Expose a versioned metrics page (header + slot array) or stream frames via an external adapter. Reserve space for future capability bits (e.g., zero‑copy region counts). Negotiate version/capabilities on attach.

## Phased Delivery
1. Solidify & Fix (blocking send/recv in monitor; ensure increments; introduce snapshot + new counters; keep `kc_chan_get_stats()` as shim).
2. Event Export (subscription API + aggregator + debug NDJSON emitter).
3. Performance (sharding, relaxed atomics where profitable).
4. Adapter Bridge (shared memory or DMA‑style exporter).
5. Advanced Metrics (latency EWMA, histograms, burst detection windows).

## Immediate Monitor Remediation
Replace busy `try_send/try_recv` loops with blocking (`timeout_ms = -1`) to guarantee progress and validate counter growth before layering export complexity.

## Open Risks / Considerations
- High fan‑in channels may still contend on a single mutex: sharding recommended once validated.
- Event backlog risk if consumer is slow; mitigate with bounded buffered channel + drop policy.
- Version skew across processes when introducing kernel bridge—solve with version gating in shared header.

## Next Steps (Engineering Tasks)
1. Implement blocking monitor producers/consumers (Phase 0).
2. Add extended counters + `kc_chan_snapshot` (Phase 1a).
3. Refactor monitor to use snapshot; print deltas for validation (Phase 1b).
4. Prototype aggregator coroutine skeleton (Phase 2 preview) behind build flag.

