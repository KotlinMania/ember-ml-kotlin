```markdown
# kcoro Error Semantics (Unified)

Principles
- 0 on success; negative KC_* (mapped to -errno) on failure.
- Try (timeout_ms == 0) → non‑blocking; Infinite (<0) → park until progress; Bounded (>0) → deadline on monotonic clock.
- Cancellation takes precedence over timeout when both are possible.

Return codes
- `KC_EAGAIN` — Would block on try path (no wait performed).
- `KC_ETIME` — Deadline elapsed on bounded wait without progress.
- `KC_ECANCELED` — Cancellation token set before progress.
- `KC_EPIPE` — Channel closed and no partner/data to complete the operation.
- `KC_ENOTSUP` — Feature not enabled/available for this channel/backend.

Copy‑based channel operations

| Operation | Try (0) | Bounded (>0) | Infinite (<0) | Close | Cancel |
|---|---:|---:|---:|---:|---:|
| send(copy) | EAGAIN if full | 0 or ETIME | 0 | EPIPE if closed pre‑delivery | ECANCELED |
| recv(copy) | EAGAIN if empty | 0 or ETIME | 0 | 0 until drained; then EPIPE | ECANCELED |

Descriptor (zref) operations

| Operation | Try (0) | Bounded (>0) | Infinite (<0) | Close | Cancel | Notes |
|---|---:|---:|---:|---:|---:|---|
| send_desc | EAGAIN if not ready | 0 or ETIME | 0 | EPIPE if closed pre‑delivery | ECANCELED | ENOTSUP if backend disabled |
| recv_desc | EAGAIN if not ready | 0 or ETIME | 0 | EPIPE if closed and empty | ECANCELED | ENOTSUP if backend disabled |

Ownership
- On success (0), the receiver obtains the payload or descriptor.
- On failure (any KC_*), the sender retains ownership.

Rendezvous specifics
- Success requires both sides to meet. Sender publish/park and receiver consume/wake are designed for exactly‑once progress and accounting.

Observability
- Successful operations update ops and byte counters exactly once per op; failure counters track EAGAIN/ETIME/EPIPE occurrences and remain visible in snapshots and rate computations.

```
