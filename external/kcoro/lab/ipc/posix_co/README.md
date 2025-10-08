# lab/ipc/posix_co — Coroutine‑Native IPC Harness

Scope
- Minimal client/server using POSIX domain sockets with rx/tx coroutines (non‑blocking) and request/response correlation (`req_id`).

Plan
- Phase A: echo `req_id` end‑to‑end, blocking client, coroutine server (baseline)
- Phase B: non‑blocking rx/tx coroutines on both sides with yield/park
- Phase C: fair tx queue across channels; credit window stubs

Non‑Goals
- No new transport here; POSIX only. TCP/QUIC adapters live out‑of‑tree.

Run
- TBD after harness sources land.

