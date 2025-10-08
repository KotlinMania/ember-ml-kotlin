# kcoro Status Snapshot — Architecture & Feature Readiness

Core
- Coroutine core (kcoro_core.c) with private stacks per coroutine (mmap), ARM64 context switcher; states: CREATED/READY/RUNNING/SUSPENDED/PARKED/FINISHED; park/unpark supported. Trampoline hardens exit behavior.
- Scheduler (kc_sched.c) unified work‑stealing pool with global ready queue, per‑worker deque, fastpath handoff slot, and inject queue; worker owns a main coroutine. Drain/shutdown APIs provided.
- Context model: immutable key/value context carried with coroutines; includes dispatcher, job, cancellation and optional deadline keys; structural sharing with small-array optimization.

Channels & Select
- Channels (kc_chan.c + kc_chan_internal.h): rendezvous, bounded buffer, conflated, and unlimited kinds. Waiter queues (WqS/WqR) for cooperative blocking; direct hand‑offs under contention; error semantics: KC_EAGAIN/ETIME/ECANCELED/EPIPE.
- Zero‑copy (kc_zcopy.c) pointer handoff for rendezvous; descriptor path staged for buffered kinds. Ownership invariants enforced; counters present.
- Inherent metrics: total ops/bytes, first/last op timestamps, failure counters; optional push via per‑channel metrics pipe; snapshot/rate APIs available (kc_chan_snapshot, kc_chan_compute_rate; presence flags always defined).
- Select (kc_select.c): multi‑clause send/recv with cancellation and timeouts; fast probe → registration pass → park/yield loop → winner claim; unbiased policy planned.

Structured concurrency
- Scopes (kc_scope.c): own cancellation context; track child coroutines and actors; blocking wait_all with absolute deadline; scoped producer helper returns a channel and auto‑closes on completion.
- Actors (kc_actor.c): coroutine wrapper around receive/process loop with on_done callback and optional cancellation; integrates with scopes.
- Tasks (future): a structured task system design exists; not implemented yet.

IPC
- POSIX IPC bridge for distributed channels (kcoro_ipc_chan.c / kcoro_ipc_server.c): TLV protocol; server runs channel ops inside coroutines to preserve semantics; client exposes make/open/send/recv.

Performance posture
- Context switch on ARM64 has been measured locally to be on the order of a few tens of nanoseconds in optimized builds on modern desktop CPUs; exact numbers vary by CPU, compiler, and flags. Fairness is maintained via ready‑queue re‑enqueue. Optimization focus is on algorithmic improvements: waiter integration for select, zero‑copy descriptor rings, low‑overhead metrics.

Work in flight (high level)
- Select→channel waiter integration; overflow policies (SUSPEND/DROP_NEWEST/DROP_OLDEST) with counters; metrics snapshot/push APIs; zero‑copy phases Z.1–Z.3; scheduler tuning after these land.

Quality & testing
- Unit tests cover buffered/rendezvous/zero‑copy basics and close/timeout paths. Stress harnesses exist for zref and channel throughput. Further tests will validate unbiased select and overflow policies.

