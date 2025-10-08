# IPC Overview — POSIX TLV Bridge for Distributed Channels

Abstract: This document describes the distributed IPC layer that allows kcoro channels to span processes or hosts via a thin, POSIX‑portable bridge. It focuses on the wire protocol, client/server roles, API contours, error mapping, invariants, and testing guidance. It is self‑contained and does not reference other documentation files.

## 1. Objectives
- Provide a coroutine‑friendly client library and server that expose channel semantics over IPC without leaking transport details into kcoro core.
- Preserve kcoro’s error semantics (0 on success; negative errno‑style codes on failure).
- Keep the bridge portable (POSIX sockets) and license‑clean (BSD). Any platform‑specific adapters live out of tree.

## 2. Architecture (High‑Level)
- Client: a small C library that issues channel operations by encoding TLV requests and parsing TLV replies. It integrates with coroutines by avoiding blocking the scheduler worker when possible.
- Server: a coroutine‑based loop that executes channel operations on behalf of clients, returning TLV replies that mirror kcoro return codes and payload conventions.
- Transport: UNIX domain sockets (UDS) for local IPC. The same TLV format can be carried over TCP for remote deployments if desired by adapters.

## 3. TLV Protocol (Concept)
Each frame is a sequence of TLVs (Type, Length, Value). Types include (illustrative):
- MAKE, OPEN, SEND, RECV, CLOSE, SNAPSHOT, RESULT
- Channel parameters (KIND, ELEM_SZ, CAPACITY), identifiers (CHAN_ID), and bytes payloads for copy paths.

Rules:
- A request must include an operation TLV (e.g., SEND) and required operands (e.g., CHAN_ID, payload).
- The server replies with a RESULT TLV carrying the operation’s return code and any result payload (e.g., bytes received).
- All numeric fields use a fixed endianness (documented in headers; typically little‑endian).

## 4. API Contours (Client Side)
Selected operations (shape only; names are illustrative):
- make/open:
  - `int kc_ipc_chan_make(int kind, size_t elem_sz, size_t capacity, uint64_t *out_id);`
  - `int kc_ipc_chan_open(uint64_t id, int *out_fd_or_handle);`
- copy ops:
  - `ssize_t kc_ipc_chan_send(uint64_t id, const void *buf, size_t len, long timeout_ms);`
  - `ssize_t kc_ipc_chan_recv(uint64_t id, void *buf, size_t len, long timeout_ms);`
- control:
  - `int kc_ipc_chan_close(uint64_t id);`
  - `int kc_ipc_chan_snapshot(uint64_t id, struct kc_chan_snapshot *out);`

Notes:
- Error mapping mirrors local semantics: EAGAIN for try, ETIME for bounded deadline, ECANCELED when cancellation is observed, EPIPE when closed without progress, ENOTSUP for unsupported features.
- The client library translates its C return values to the RESULT TLV and vice versa.

## 5. Server Responsibilities
- Accept connections and parse requests into channel operations.
- Execute operations inside coroutines to preserve cooperative semantics.
- Guarantee that every request receives exactly one RESULT reply (even on error).
- Enforce bounds and validity (e.g., maximum payload sizes, valid CHAN_IDs).

## 6. Deadlines & Cancellation
- Timeouts: requests include an absolute or relative deadline; the server enforces it and returns -ETIME when exceeded.
- Cancellation: a request may carry a cancellation flag or token reference; the server checks it at suspension points and returns -ECANCELED promptly.

## 7. Invariants & Safety
- Exactly‑once reply: for every request, one and only one RESULT is sent.
- No buffer overflows: lengths are validated before copying. Paths use safe bounded copies.
- Channel identity: server maintains a map from CHAN_ID to live channel objects; IDs are not reused until safe.
- Error semantics are preserved across the wire; no translation beyond errno mapping occurs.

## 8. Observability
- Optional debug logging gated by a runtime flag (e.g., KCORO_DEBUG) prints decoded TLVs and outcomes.
- Counters: requests processed, timeouts, cancellations, bytes transferred.

## 9. Edge Cases
- Client disconnect mid‑request: server aborts processing and releases any provisional state; no reply is attempted on dead sockets.
- Oversized payload: server rejects with -EMSGSIZE.
- Unknown CHAN_ID: server returns -ENOENT.
- Close races: operations in flight either complete or return -EPIPE after the close is acknowledged.

## 10. Testing Outline
- Loopback smoke test: make/open/send/recv/close; verify RESULT codes.
- Timeout test: bounded recv with no producer; expect -ETIME.
- Cancellation test: trigger cancellation during wait; expect -ECANCELED.
- Snapshot test: server returns plausible counters and state.
- Error mapping: invalid CHAN_ID returns -ENOENT; oversized payload returns -EMSGSIZE.

## 11. Deployment Notes
- UNIX domain sockets are preferred for low overhead and permission control (filesystem path length validated; errors map to -ENAMETOOLONG when applicable).
- Remote operation over TCP can reuse the same TLV protocol with authentication handled by the adapter.

Status: alpha; details may evolve with implementation. The intent is to keep the bridge thin, portable, and faithful to kcoro semantics.

## 12. Wire Commands (Illustrative)
- CHAN_MAKE: attributes KIND (u32), ELEM_SIZE (u32), CAPACITY (u32). Reply: CHAN_ID (u32) + RESULT (i32).
- CHAN_OPEN: attributes CHAN_ID (u32). Reply: RESULT (i32).
- CHAN_SEND: attributes CHAN_ID (u32), TIMEOUT_MS (u32), ELEMENT (bytes[elem_sz]). Reply: RESULT (i32).
- CHAN_RECV: attributes CHAN_ID (u32), TIMEOUT_MS (u32). Reply: RESULT (i32) and, on success, ELEMENT.
- CHAN_CLOSE: attributes CHAN_ID (u32). Reply: RESULT (i32).

## 13. POSIX Backend Implementation Notes
- Transport: UNIX domain sockets with simple framing; TLVs are big‑ or little‑endian as declared by the binding. Bounds are validated before decode.
- Registry: server maintains a map of {id → channel, kind, elem_sz}. IDs monotonically increase and are not reused prematurely.
- Execution: channel operations run within coroutines, preserving cooperative semantics. The server coordinates completion back to the IO thread and crafts a single RESULT reply per request.
- Error policy: malformed frames map to -EPROTO; unknown commands are rejected; oversize elements map to -EMSGSIZE; unknown channel IDs map to -ENOENT.

## 14. Semantics & Guarantees (Recap)
- Timeouts are enforced on the server side using the provided deadline; results propagate as -ETIME on expiry.
- Cancellation is observed at suspension points when supplied by the client; results propagate as -ECANCELED.
- Ordering is per connection for submission; completion order may differ per channel depending on wait semantics.
- Payload sizes are validated; ELEMENT TLV length must equal the declared elem_sz for the channel.

## 15. Roadmap (Phased)
- N1 — Coroutine‑Native IPC over POSIX sockets: non‑blocking RX/TX coroutines, request IDs, deadline/cancellation propagation.
- N2 — Transport Vtable: pluggable backends with a common ops table; POSIX UDS first.
- N3 — Ordering/Reliability/Flow Control: sequence numbers for diagnostics, acknowledgment or credit windows.
- N4 — Multiplexing & Remote Select: many logical channels per connection, readiness events to support selection without polling.
- N5 — Out‑of‑Tree Transports: TCP/TLS, UDP multicast, QUIC; adapters negotiate capabilities and remain outside the core.

## 16. Build & Test Notes (Example)
- Build the POSIX IPC static library:
  - `make -C src/kcoro/ipc/posix`
  - This produces `src/kcoro/ipc/posix/libkcoro_ipc_posix.a`.
- Link your client against kcoro headers and the IPC library (example):
  - `cc -Isrc/kcoro/include -o ipc_client ipc_client.c src/kcoro/ipc/posix/libkcoro_ipc_posix.a`
- Run a local server (per your environment) on a UNIX socket path, then exercise make/open/send/recv/close over IPC and validate RESULT codes and payloads.
- Ensure that invalid CHAN_ID, malformed frames, and oversized elements map to the documented error codes.
