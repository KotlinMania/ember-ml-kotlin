# Scheduler V2 Architecture

Abstract: This document describes the scheduler architecture and algorithms used by kcoro: worker topology, work-stealing loop, dispatchers, memory strategy, metrics, and diagnostic hooks. It is self-contained for the scheduler component — all algorithmic detail and configuration notes needed to implement or reason about the scheduler are included here.

## 1. Scheduler V2 Architecture (Completed Draft)

### 1.1 Core Components
- Workers: `kc_worker` with lock-free Chase-Lev deque.
- Global Inject Queue: MPSC for external submissions.
- Dispatchers: strategy objects mapping submit -> queue.
- Task Envelope: wraps `kcoro_t` + metadata (flags, dispatcher, priority).

### 1.2 Work-Stealing Loop
1. Drain inject queue (bounded N items).
2. Pop local deque bottom (LIFO) for cache locality.
3. On empty, attempt steal from random victim top (FIFO for fairness).
4. Park if all stealing attempts fail (futex/condvar) until new work arrives.

### 1.3 Dispatchers
| Dispatcher | Description | Parallelism | Notes |
|------------|-------------|-------------|-------|
| Default | CPU-bound tasks | #cores | Backed by the scheduler singleton returned from `kc_sched_default()` / `dispatcher_default()` |
| IO | Blocking / high-latency ops | ≥ max(cores, 64) | New dispatcher built on demand (`kc_dispatcher_io()` / `kcoro_cpp::dispatcher_io()`); uses separate work-stealing pool so blocking calls do not starve CPU work |
| Custom | Caller-specified pool | User hint | `kc_dispatcher_new(workers)` / `kcoro_cpp::dispatcher_new(workers)` expose dedicated pools for bespoke policies |
| Single (future) | Serialized tasks | 1 | Placeholder for future single-thread affinity helpers |
| Unconfined (advanced) | Inline until first suspension then re-dispatch | N/A | Matches the upstream `Dispatchers.Unconfined`; deliberate opt-in, not yet implemented in kcoro |

**Thread ownership rules (mirrors upstream dispatchers)**

- Every dispatcher owns exactly one `kc_sched_t`/`WorkStealingScheduler`; ready-queue mutations and coroutine state transitions must occur on the owning worker thread. The default dispatcher lazily wraps the global scheduler singleton, while the IO dispatcher holds a dedicated pool sized for blocking workloads.
- `kcoro_resume`, `kcoro_yield`, and `kc_sched_enqueue_ready` are being hardened so only the owning dispatcher mutates `kcoro_t.state`, `main_co`, and ready-list linkage. Other threads interact by enqueueing through the dispatcher APIs instead of touching fields directly. This mirrors the reference guidance that resumptions happen via the dispatcher rather than arbitrary threads.
- Dispatchers are reference-counted (C) or long-lived singletons (C++). Always call `kc_dispatcher_release` when done to shut down private pools; shared defaults remain alive for the process lifetime.

The dispatcher APIs are now shared between the C runtime (`external/kcoro`) and the C++ runtime (`external/kcoro_cpp`), ensuring native interop can select the same default vs. IO pools across both implementations.

### 1.4 Preemption / Fairness
- Cooperative at suspension points (channel ops, delay, await, explicit yield).
- Optional soft budget counter per run segment: if exceeded re-enqueue to tail to prevent monopolization.

### 1.5 Memory Strategy
- Per-worker slabs for task envelopes.
- Stack caching (LRU bounded) to reduce mmap churn.
- Diagnostics build guard pages and checks are compiled with `KCORO_CTX_DIAGNOSTICS`; runtime checks enable via `KCORO_DEBUG_CTX_CHECK`.

### 1.6 Metrics (Initial Set)
- `tasks_submitted`, `tasks_completed`.
- `steal_attempts`, `steal_successes`.
- `avg_run_ticks`, `max_run_ticks` (sampled).
- `inject_queue_overflows`.
- `park_events`, `unpark_events`.

### 1.7 Debug / Diagnostics
- Event ring buffer (lock-free) with sampled entries (type, ts, worker, task id).
- Dump API: `kc_sched_debug_dump(FILE*)`.
- Optional slow-task detector (> configurable tick threshold).

### 1.8 Configuration & Diagnostic Findings
This scheduler component document includes recommended build/runtime toggles and a short summary of a past ready-queue bug and its fix so implementers can reproduce diagnostic steps and avoid regressions.

- Tunables (from repository configuration notes):
	- `KC_SCHED_STEAL_SCAN_MAX` (compile-time macro) — upper bound on victim deques probed during a steal attempt. Default: `4`. Set via compiler defines (e.g., `-DKC_SCHED_STEAL_SCAN_MAX=8`).
	- Recommended diagnostic build: `make -C src/kcoro KCORO_CTX_DIAGNOSTICS=1 CFLAGS="-O1 -g -fsanitize=address,undefined"` and `export KCORO_DEBUG_CTX_CHECK=1` to surface context/state violations.

- Notable historical bug (ready-queue UAF) and fix (short):
	- Symptom: ASan reports frees on non-malloc’d addresses and use-after-free during headless monitor runs; observed when one worker freed a ready-queue node while another still referenced it.
	- Root cause: ready queue used separate heap-allocated `struct sched_ready` nodes; a pop+free race allowed another worker to retain a pointer into freed memory.
	- Fix implemented: converted ready queue to be intrusive (use `kcoro_t->next`), eliminating separate heap nodes and preventing double-touching of freed memory; also hardened `kcoro_resume` to guarantee a valid "from" coroutine (fallback to `main_co`).

### 1.9 Reference Alignment Notes (2025‑10)
- **TLS hand-off:** The upstream native workers store the currently running continuation in a TLS slot (`main_co`) before resuming the target coroutine. When the call returns, they restore the previous `main_co`. Our `worker_main` must perform the same sequence: set `co->main_co = w->main_co`, retain the coroutine, and restore TLS before releasing.
- **Continuation retention:** The disassembly confirms that resumptions retain the continuation until `CancellableContinuationImpl#getResult` re-queues the next segment. In kcoro this translates to retaining the coroutine when enqueuing it and releasing only after `kcoro_resume` finishes and the state transitions out of RUNNING.
- **Wake ownership:** The reference dispatcher always enqueues wakes through the owning scheduler; ad-hoc thread resumes are forbidden. `kc_sched_enqueue_ready` now mirrors this rule—only the owning worker toggles `co->state` and manipulates ready queues.
- **Reference sample:** The reference build/disassembly lives at `tools/kotlin-native-samples/chan/pingpong.kexe`. Re-run it after the upstream toolchain bumps to keep parity.





## Control & Compile-Time Flags

The scheduler auto-initializes a singleton worker pool on first use. Historical compile-time macros that disabled or altered this behavior were removed to enforce a single optimal code path and reduce conditional complexity.

Goals
- Provide a predictable, fully featured work-stealing scheduler by default.
- Minimize conditional branches and legacy fallbacks (clarity and performance).
- Retain opt-in debug instrumentation without affecting release performance.

Active Flags
| Macro | Effect | Default |
|-------|--------|---------|
| KCORO_DEBUG | Enables runtime debug logging via kc_dbg(...). | Unset |

Default Behavior (no flags)
- kc_sched_default() lazily constructs a singleton scheduler (workers = online CPUs).
- Channel wake paths always obtain/create the default scheduler and enqueue ready coroutines—no silent READY marking or dropped wakes.
- Work stealing, fast-path last-task slot, inject queue, and ready queue are always active.

Debug Mode Behavior (runtime)
- When KCORO_DEBUG=1 is set in the environment: additional scheduler debug logging appears. Assertions remain enabled in debug builds; there are no compile-time debug forks.

Migration Notes
- Remove any uses of legacy macros that attempted to disable auto scheduling; they are obsolete.
- Cooperative single-thread testing can be done by creating a scheduler with one worker (e.g., opts.workers = 1) if deterministic execution is desirable.

Future Considerations
- Potential runtime API to reconfigure worker count (would require quiescence handling).
- Pluggable scheduling policies (priority/latency vs throughput) behind a stable interface.
- Optional timer wheel coroutine for deadline handling; bounded waits consult it to avoid busy sleep.


## Performance Targets

Baseline targets (release build on modern desktop CPUs):
- Work steal latency (idle victim): <= 2 µs (stretch 1 µs)
- Scheduler throughput (CPU-bound tasks, 1k ops): 10M ops/sec (stretch 15M)

Measurement plan
- Provide a micro-bench harness (bench directory) using a monotonic clock.
- Include warm-up to prime caches; report median, p95, p99.

Optimization levers
- Tune stack cache sizes.
- Inline hot-path scheduler operations judiciously.
- Avoid false sharing; align hot counters to cache lines.
- Consider per-worker slabs/allocators for scheduler-owned objects.

Regression guard
- Track rolling baselines in JSON; fail CI if >20% regression is detected for guarded metrics.


### Coroutine Interaction (Integration Notes)
- kcoro_unpark transitions PARKED→READY and calls `kc_sched_enqueue_ready` when the scheduler is present; otherwise it is a safe no-op and the coroutine remains READY until explicitly resumed.
- Tests may use manual `kcoro_resume` loops without a scheduler; production flows typically rely on ready queues and workers driving RUNNING coroutines.
