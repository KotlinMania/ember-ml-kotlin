# External Adapter Guidelines (Zero‑Copy Backends & IPC)

Scope
- How to integrate an external producer/consumer (“adapter”) with kcoro’s zero‑copy and IPC surfaces without linking platform‑specific headers into core.

Principles
- Core remains ANSI/POSIX and BSD‑licensed; adapters live out‑of‑tree and depend on their own SDKs.
- The adapter translates its native buffers to `kc_zdesc` and registers a backend via the factory.

Backend registration
```
typedef struct kc_zcopy_backend_ops {
  int  (*attach)(kc_chan_t *ch, const void *opts);
  void (*detach)(kc_chan_t *ch);
  int  (*send)(kc_chan_t *ch, const kc_zdesc_t *d, long tmo_ms);
  int  (*recv)(kc_chan_t *ch, kc_zdesc_t *d, long tmo_ms);
  int  (*send_c)(kc_chan_t *ch, const kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct);
  int  (*recv_c)(kc_chan_t *ch, kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct);
} kc_zcopy_backend_ops_t;

kc_zcopy_backend_id kc_zcopy_register(const char *name,
                                      const kc_zcopy_backend_ops_t *ops,
                                      uint32_t caps);
```

Attach/detach
- `attach` may allocate per‑channel state (rings, freelists). Keep it O(1) and idempotent.
- `detach` tears down cleanly only after the channel is closed or no inflight ops remain.

Send/recv semantics
- Return 0 on success; negative KC_* on failure (KC_EAGAIN, KC_ETIME, KC_ECANCELED, KC_EPIPE, KC_ENOTSUP).
- Do not free or reallocate user memory; ownership remains with producer until success.
- Bounded waits should yield/park cooperatively; never busy‑spin.

Descriptor mapping
- Fill `kc_zdesc{ addr,len }` for local pointers. For exported arenas, fill `region_id,offset,len` and ensure the receiver knows how to translate.
- Validate length against adapter limits; reject zero length.

IPC adapters (outline)
- Use the transport vtable to send/recv frames non‑blocking.
- Maintain a per‑connection rx/tx coroutine pair; do not block worker threads in I/O.
- Correlate requests with `req_id`; echo `req_id` in replies.

Error mapping table (adapter view)
| Condition | Return |
|---|---|
| Would block now | KC_EAGAIN |
| Deadline elapsed | KC_ETIME |
| Cancellation token set | KC_ECANCELED |
| Channel closed pre‑delivery | KC_EPIPE |
| Feature not available | KC_ENOTSUP |

Performance checklist
- Avoid copying payloads; enqueue only descriptors.
- Batch small frames where allowed by transport (but preserve ordering).
- Use a bounded credit window to prevent unbounded memory use.

Testing checklist
- Ownership: producer retains on failure; consumer receives exactly once on success.
- Timeouts: bounded waits return KC_ETIME under contention.
- Cancellation: `_c` variants return KC_ECANCELED promptly.
- Close: EPIPE paths covered; no leaks after detach.

License hygiene
- Do not include adapter SDK headers in kcoro core. Keep all adapter code in its own repository or module with its own license.

