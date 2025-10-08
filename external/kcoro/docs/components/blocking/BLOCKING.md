# Blocking & IO Integration — Design and Contracts

Abstract: Prevent OS-level blocking calls from stalling scheduler workers by routing IO waits through an evented loop and truly blocking work through a small elastic offload pool. This document is self-contained and describes objectives, design layers, APIs, deadlines/cancellation semantics, data structures, metrics, edge cases, and a testing outline.

## 1. Objectives
- Keep worker threads in the Default dispatcher free from OS blocking (disk, DNS, legacy libs).
- Provide an elastic IO dispatcher/offload pool with deadline and cancellation support.
- Start with explicit opt-in wrappers; avoid heuristic auto-detection in early phases.
- Integrate with structured concurrency: cancellation propagates to offloaded operations; prefer EINTR/timeouts/self-pipe over thread cancellation for interruption.

## 2. Design Overview (Two Layers)
1) IO Dispatcher (cooperative + evented)
- For non-blocking FDs, timers, sockets via platform event APIs (e.g., epoll/kqueue).
- Tasks register interest and suspend; dedicated loop threads poll and resume tasks.

2) Blocking Offload Pool
- Small elastic thread pool executing truly blocking calls (legacy APIs, synchronous disk).
- Exposed via `kc_block_call(fn, arg, timeout_ms, out_status)`; returns immediately and completes via a deferred/promise internally.

Default guidance: prefer non-blocking descriptors with `kc_io_read`/`kc_io_write`/`kc_io_wait_*` to suspend coroutines instead of blocking threads.

## 3. Component Breakdown
| Component     | Description                                  | Notes                                     |
|---------------|----------------------------------------------|-------------------------------------------|
| `kc_io_loop`  | Epoll/kqueue event loop thread set            | Sized ~min(4, cores) or user-defined      |
| Registration  | Add FD + interest mask + continuation        | Edge-triggered epoll for scalability      |
| Timer Wheel   | Deadline structure for delays/timeouts       | Shared timer facility reused by IO waits  |
| Offload Pool  | Worker threads for blocking functions        | Starts lazy; size range [min,max]         |
| Work Item     | {fn,arg,promise,deadline}                    | Returns status via deferred/promise       |

## 4. APIs (Proposed)
```c
// Blocking offload (thread pool)
typedef int (*kc_block_fn)(void *arg); // returns int status
int kc_block_call(kc_block_fn fn, void *arg, long timeout_ms, int *out_status);

// Non-blocking IO registration (will suspend current coroutine)
int kc_io_wait_readable(int fd, long timeout_ms);
int kc_io_wait_writable(int fd, long timeout_ms);

// Convenience wrappers (loop until full transfer or error)
ssize_t kc_io_read(int fd, void *buf, size_t len, long timeout_ms);
ssize_t kc_io_write(int fd, const void *buf, size_t len, long timeout_ms);

// Cancellation-aware variants may accept implicit context/job
```

Status
- The APIs in this document are proposed. They are not yet declared in public headers. Implementations are expected to land under user/src (e.g., kc_io.c) with headers under include/ (e.g., kcoro_io.h).

Roadmap (initial milestones)
- M1: Minimal evented wait helpers: kc_io_wait_readable/writable (cooperative; deadlines observed).
- M2: Convenience read/write wrappers with bounded deadlines.
- M3: Offload pool: kc_block_call with elastic thread pool and deadline support.
- M4: Cancellable variants (optional token parameter patterns consistent with channel *_c APIs).

## 5. Offload Pool Strategy
- Bounded queue (lock-free MPSC) length N (configurable; default 1024). If full, either park cooperatively until space or return -EAGAIN based on flags.
- Threads spawn on demand up to `max_threads` when queue depth exceeds a threshold; idle threads retire after `idle_timeout_ms`.
- Each work item may carry a deadline; if already expired before execution, return -ETIMEDOUT immediately.

## 6. Cancellation Model
- IO loop operations: on cancellation, remove FD interest and resume the coroutine with -KC_ECANCELED.
- Blocking offload tasks: cooperative cancellation only if the function checks a provided token (enhanced signature variant). Baseline: effective pre-dispatch (queue removal) or post-completion only. Non-interruptible native calls are documented as such.
- Future enhancement: wrappers using `ppoll`/`pselect` or self-pipe to awaken on cancel.

## 7. Deadlines & Timeouts
- Timeouts map to an absolute monotonic deadline from context or an explicit parameter.
- IO waits register a timer entry; on fire: remove event, resume coroutine with -ETIMEDOUT.
- Offload pool uses relative timeouts: if function hasn't started by deadline, it's dropped (not executed) and the coroutine resumes with -ETIMEDOUT.

## 8. Epoll/Kqueue Integration (Platform Path)
- Single epoll FD (Linux) or kqueue (BSD/macOS) per IO loop. Events distributed in batches to waiting tasks.
- Task registration object: `{ fd, events, continuation, generation }` stored in a per-fd vector/hash.
- After resumption and partial progress, tasks re-register interest until complete.
- Backpressure: maximum registered FDs per loop is adjustable; counters exposed.

## 9. Data Structures (Sketch)
```c
typedef struct kc_io_wait {
  int fd;
  uint32_t events; // EPOLLIN / EPOLLOUT
  kcoro_t *coro;
  struct kc_io_wait *next;      // bucket chain
  long long deadline_ns;
  struct kc_io_wait *timer_next; // integration with timer facility
} kc_io_wait_t;

typedef struct kc_block_item {
  kc_block_fn fn;
  void *arg;
  long long deadline_ns;
  kc_job_t *job;            // for cancellation visibility
  kc_deferred_t *promise;   // completion target
} kc_block_item_t;
```

## 10. Metrics
- `io_wait_registered`, `io_wait_timeout`, `io_wait_cancel`, `io_events_processed`.
- `block_tasks_submitted`, `block_tasks_rejected`, `block_threads_spawned`, `block_threads_retired`, `block_queue_depth_peak`.

## 11. Edge Cases
- FD closed while awaiting: event returns HUP/ERR → resume with -ECONNRESET (or mapped), configurable.
- Deadline already expired on call: return -ETIMEDOUT without registering.
- Cancellation race with event delivery: whichever dequeues the waiter first wins; the other sees no waiter.
- Offload queue saturation: in blocking mode, coroutine parks; cancellation while parked removes the pending request.

## 12. Testing Matrix
| Scenario                             | Expectation                                              |
|--------------------------------------|----------------------------------------------------------|
| Read readiness immediate             | Returns bytes read without suspension                   |
| Write partial then wait              | Suspends then resumes when writable                     |
| Timeout waiting readable             | Returns -ETIMEDOUT                                      |
| Cancel during wait                   | Returns -KC_ECANCELED                                    |
| Offload quick task                   | Completes with status 0                                 |
| Offload queue full (non-blocking)    | Returns -EAGAIN                                         |
| Offload queue full then frees space  | Task eventually runs                                    |
| Deadline expires before offload start| -ETIMEDOUT; function not executed                       |
| Many concurrent waits (>1000)        | All resume; no leaks; bounded latency                   |
