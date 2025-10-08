# Jobs and Cancellation

Abstract: This document is the complete specification for the job (structured concurrency) subsystem: data structures, state machine, cancellation propagation, deferred/await semantics, and the API surface required to create, join, cancel, and manage jobs. It includes algorithms and edge-case handling so it can be read independently.

Design status
- This is a design document for a future structured job system. It describes intended data structures, state machines, and APIs. The shipped code today uses scopes and cancellation tokens; jobs are planned.

## Job Model
- `kc_job` object (refcounted) with:
  - State: ACTIVE, CANCELLING, CANCELLED, COMPLETED, FAILED.
  - Parent pointer, intrusive doubly-linked list of children.
  - Cancellation reason / error slot.

Parent ensures all children completed before marking COMPLETE (unless supervisor semantics).

## Launch Semantics
- `kc_scope` begins a job context; any `kc_launch` within inherits parent job.
- Exiting scope waits (join) all non-detached children.
- `kc_async` returns a `kc_deferred` tied to a job child.

## Cancellation Propagation
- `kc_cancel(job)`: atomically transition ACTIVE->CANCELLING.
- Broadcast to children: set cancel flag + schedule wake of cancellable suspension points.
- Suspension points check `kc_job_is_canceled(job)` and abort with `KC_ECANCELED` or propagate error.

## Supervisor Jobs
- Supervisor ignores child failures (child failure does not cancel siblings).
- Regular job: first failing child transitions parent to CANCELLING (unless already in cancellation or supervisor flag set).


## Deferred / Await
- `kc_deferred` states: PENDING, COMPLETED(value), FAILED(error), CANCELED.

## API Sketch
```
kc_job_t* kc_job_current(void);
int kc_scope(kc_job_t* parent, int (*fn)(void*), void* arg, kc_scope_opts_t* opts);
int kc_cancel(kc_job_t* job);
int kc_launch_job(kc_dispatcher_t* disp, kc_job_t* parent, kcoro_fn_t fn, void* arg, size_t stack, kc_job_t** out_job);
```



### 4.8 Error Handling Strategy
- Store first exception/errno in job (unless supervisor) for retrieval.
- Provide `kc_job_result(job, &code)` after join.

### 4.9 Open Questions
| Topic | Pending Decision |
|-------|------------------|
| Child list lock-free vs mutex | Lean toward small spin/mutex first |
| Configurable cancellation handlers | Add later via context element |
| Structured stack traces | Phase after MVP (needs unwind metadata) |

### 4.10 Data Structures (Detailed)
```c
typedef enum {
  KC_JOB_ACTIVE = 0,
  KC_JOB_CANCELLING,
  KC_JOB_CANCELLED,
  KC_JOB_COMPLETED,
  KC_JOB_FAILED
} kc_job_state_t;

typedef struct kc_job kc_job_t;

typedef struct kc_job_child_link {
  kc_job_t *prev, *next; // intrusive list within parent
} kc_job_child_link_t;

typedef struct kc_job_waiter {
  struct kc_job_waiter *next;
  kcoro_t *coro; // parked awaiting completion (join / await deferred)
  uint32_t flags; // e.g. JOIN_WAIT | DEFERRED_WAIT
} kc_job_waiter_t;

struct kc_job {
  _Atomic(kc_job_state_t) state;
  _Atomic(int) refcnt; // +1 parent link, +N external refs, +N joining waiters (if needed)
  kc_job_t *parent;
  kc_job_child_link_t siblings; // (parent child list linkage)
  kc_job_t *first_child; // head of intrusive doubly-linked list
  kc_job_t *last_child;
  uint32_t child_count; // ACTIVE + completed (for debug); can maintain active_children separately
  uint32_t active_children; // decremented on terminal child states
  uint32_t flags; // SUPERVISOR | DETACHED | TIMEOUT_WRAPPER | etc.
  int result_code; // first non-zero / failure (unless supervisor)
  void *result_payload; // optional pointer for deferred value
  int cancel_reason; // errno-style reason (ETIMEDOUT, ECANCELED, user error)
  kc_job_waiter_t *waiters; // singly-linked stack (LIFO) of waiters
  _Atomic(int) cancel_notified; // fast path guard (0->1)
  // Optional optimization: small fixed ring for first few children (avoid heap for small fanout)
};

typedef struct kc_deferred {
  kc_job_t *job; // underlying job representing the async computation
  // Type erasure: user retrieves payload via API specifying expected size/type
} kc_deferred_t;
```

### 4.11 State Machine & Transitions
States are terminal except ACTIVE & CANCELLING. Once leaving ACTIVE you never return. FAILED & CANCELLED are terminal error varieties; COMPLETED is success.

Valid transitions:
```
ACTIVE -> COMPLETED   (normal return)
ACTIVE -> FAILED      (explicit failure path / panic capture)
ACTIVE -> CANCELLING  (parent or external cancellation request)
CANCELLING -> CANCELLED (no failure; cooperative cancellation)
CANCELLING -> FAILED    (task observes cancellation but reports failure / exception)
ACTIVE -> CANCELLING -> (FAILED|CANCELLED) -> (parent aggregates) -> (parent decides outcome)
```
Job final observable state collapses to one of COMPLETED, FAILED, CANCELLED.

Parent completion rule: A non-supervisor parent enters CANCELLING upon first child FAILED (or CANCELLED if propagating), then waits for all active children to reach terminal before computing its own state (FAILED if any child FAILED; CANCELLED if cancellation reason not associated with child failure; otherwise COMPLETED).

### 4.12 Concurrency & Locking Model
Goal: Minimize global locks; each job owns a small critical section.

Initial approach (Phase S3):
1. Child list + waiters protected by a lightweight spin-mutex (`kc_spin_t`) in the job (fast path: small contention expected).
2. State transitions via atomic CAS loops; locking only for list / waiter modification.
3. Cancellation broadcast iterates children under lock snapshotting pointers then releasing lock before recursively cancelling to avoid deep lock nesting (prevent ABA by retaining refs before unlock).
4. Reference counts ensure that a job object lives while: (a) in ACTIVE/CANCELLING, (b) externally referenced (deferred handle), (c) there exist waiters or child still linking to parent.

Potential future optimization:
- Convert child list to lock-free MPSC append + hazard pointers if contention measured high.

### 4.13 Algorithms (Pseudo-Code)

Creation (attach child):
```c
kc_job_t *kc_job_create(kc_job_t *parent, uint32_t flags) {
  j = alloc_job();
  j->state = KC_JOB_ACTIVE;
  j->refcnt = 1; // external handle
  j->parent = parent;
  if (parent) {
     inc(parent->refcnt); // parent holds ref until child terminal
     lock(parent);
     insert_tail(parent->child_list, j);
     parent->active_children++;
     unlock(parent);
  }
  return j;
}
```

Completion (normal path at coroutine return):
```c
void kc_job_complete(kc_job_t *j, int rc, void *payload) {
  if (rc != 0) {
    // failure path
    if (atomic_cas(&j->state, KC_JOB_ACTIVE, KC_JOB_FAILED)) {
       j->result_code = rc;
    } else if (atomic_load(&j->state) == KC_JOB_CANCELLING) {
       // escalate failure preference
       atomic_store(&j->state, KC_JOB_FAILED);
       j->result_code = rc;
    }
  } else {
    if (atomic_cas(&j->state, KC_JOB_ACTIVE, KC_JOB_COMPLETED)) {
       j->result_code = 0;
    } else if (atomic_load(&j->state) == KC_JOB_CANCELLING) {
       // cancellation already in flight; leave state (CANCELLING -> CANCELLED handled elsewhere)
    }
  }
  kc_job_try_finalize(j);
}
```

Cancellation request:
```c
bool kc_job_request_cancel(kc_job_t *j, int reason) {
  s = atomic_load(&j->state);
  while (s == KC_JOB_ACTIVE) {
     if (atomic_cas(&j->state, KC_JOB_ACTIVE, KC_JOB_CANCELLING)) {
        j->cancel_reason = reason;
        broadcast_children(j); // propagate
        wake_waiters_if_needed(j); // e.g., if they wait for completion and should observe cancellation early? (policy: they still wait until terminal)
        return true;
     }
     s = atomic_load(&j->state);
  }
  return false; // already not ACTIVE
}
```

Child terminal accounting & parent finalize:
```c
void kc_job_on_child_terminal(kc_job_t *parent, kc_job_t *child) {
  lock(parent);
  parent->active_children--;
  // if failure and parent supervisor flag not set and parent still ACTIVE -> move to CANCELLING
  if (!SUPERVISOR(parent) && child->state == KC_JOB_FAILED) {
     kc_job_request_cancel(parent, child->result_code);
  }
  bool done = (parent->active_children == 0) && is_terminal(parent->state);
  if (done) move_parent_from_active_if_needed(parent); // if still ACTIVE with zero children -> COMPLETED
  // capture waiters list if now terminal
  waiters = done ? detach_waiters(parent) : NULL;
  unlock(parent);
  if (waiters) wake_all(waiters);
  dec_ref(parent); // release child-held ref
}
```

Finalize job after self terminal:
```c
void kc_job_try_finalize(kc_job_t *j) {
  kc_job_state_t s = atomic_load(&j->state);
  if (s == KC_JOB_CANCELLING) {
     // decide terminal variant
     if (j->result_code != 0) {
        atomic_store(&j->state, KC_JOB_FAILED);
     } else {
        atomic_store(&j->state, KC_JOB_CANCELLED);
     }
  }
  // detach from parent & inform parent active_children--
  if (j->parent) kc_job_on_child_terminal(j->parent, j);
  // wake own waiters
  lock(j);
  waiters = detach_waiters(j);
  unlock(j);
  wake_all(waiters);
  dec_ref(j); // drop self execution ref
}
```

Await / join registration:
```c
int kc_job_join(kc_job_t *j) {
  for (;;) {
    s = atomic_load(&j->state);
    if (is_terminal(s)) return translate_state(s, j->result_code, j->cancel_reason);
    park_current_as_waiter(j); // adds waiter under lock then parks
  }
}
```

### 4.14 Deferred Semantics
`kc_async` creates child job + coroutine; returns `kc_deferred_t` retaining a ref. Await logic: call `kc_job_join(deferred->job)`, then retrieve payload via `kc_deferred_get`. Producer sets `result_payload` *before* calling `kc_job_complete` to avoid race with waiter.

### 4.15 Cancellation Semantics & Guarantees
- After `kc_cancel(job)` returns true: all future suspension points in that job must observe cancellation (bound by memory ordering of atomic store). Use release store on transition to CANCELLING; suspension points load acquire.
- Propagation breadth-first logically but implemented depth-first recursion; ordering not guaranteed.
- Cancellation is cooperative: if a coroutine spins w/o suspension points it can delay cancellation observation (documented). Optionally add soft budget instrumentation to encourage fairness.

### 4.16 Invariants
1. If `state` in {COMPLETED, FAILED, CANCELLED} then `active_children == 0`.
2. Parent holds +1 ref per active child; released exactly once in `kc_job_on_child_terminal`.
3. Waiters only exist while job non-terminal OR before wake dispatch; after detach, `waiters` list is NULL.
4. A job transitions through at most one non-terminal intermediate state (CANCELLING); no cycles.
5. For non-supervisor parents: if any child FAILED then parent final state is FAILED (unless parent itself externally cancelled earlier and records CANCELLED reason first — policy: failure dominates cancellation unless reason==ETIMEDOUT).

### 4.17 Edge Cases & Handling
- Self-cancel: job requests its own cancellation (e.g., timeout scope) -> transition ACTIVE->CANCELLING then finalize path sets CANCELLED (unless failure recorded).
- Parent cancelled after child failure in flight: both CANCELLED and FAILED candidates -> choose FAILED (failure dominance rule).
- Timeout vs explicit cancel race: first successful CAS to CANCELLING wins; second observes already CANCELLING and does not overwrite cancel_reason unless reason precedence (ETIMEDOUT < explicit user reason).
- Detached child: parent does not wait; parent does NOT hold ref; detached child has no propagation upward (its failure does not affect parent). Flag `DETACHED` bypasses adding to child list.
- Awaiter cancellation (if we add ability to cancel join): removing waiter from list must be O(1) (use stack removal with mark-bit or convert to singly-linked list with logical removal flag). MVP: no cancellable join; join is non-cancellable.

### 4.18 Testing Matrix
| Scenario | Expectation |
|----------|-------------|
| Simple scope with 3 children completes | Parent COMPLETED; child results aggregated |
| Child failure (non-supervisor) | Parent FAILED; siblings cancelled |
| Child failure (supervisor) | Parent COMPLETED if no other failures |
| External cancel before children start | All children CANCELLED; parent CANCELLED |
| Timeout fires first | Scope CANCELLED (ETIMEDOUT) |
| Deferred value await success | Await returns 0 + payload accessible |
| Deferred failure | Await returns error code; payload NULL |
| Cancellation during await | Await returns ECANCELED |
| Nested scopes with inner failure | Propagation correct depth-first |
| Detached child failure | Parent unaffected |
| Race: failure vs external cancel | Failure dominates final state |
| Many siblings (1k) cancel stress | No leaks, O(n) completion time |
| Waiters storm (32 awaits) | All awakened exactly once |

### 4.19 Phase S3 Implementation Slice
Scope of S3 (Structured jobs + cancellation phase):
- Implement `kc_job_t` structure, creation, refcount, attach/detach.
- Implement cancellation & propagation (recursive simple).
- Implement join (`kc_job_join`).
- Add `kc_async` + `kc_deferred_t` minimal (value pointer only, no typed wrappers).
- Add timeout wrapper using a provisional global timer wheel stub / fallback to coarse sleep thread (upgrade in S4).
- Provide minimal tests aligning with Testing Matrix rows 1–8, 10.

Deferrals to later phases:
- Cancellable joins (S4/5), structured stack traces, lock-free child list optimization, advanced handler context elements.

### 4.20 Complexity Notes
- Space: `kc_job_t` ~ (cache-line + small pointers). Target ≤ 128 bytes in release.
- Operations: child attach O(1); cancellation broadcast O(children); join path O(1) uncontended; wake-all O(waiters).

### 4.21 Observability Hooks (Preview)
Insert trace events: JOB_CREATE, JOB_STATE, JOB_CANCEL_REQ, JOB_FINALIZE, JOB_CHILD_ATTACH, JOB_WAIT_REGISTER, JOB_WAIT_WAKE.

---
Completion of Section 4 indicates design readiness; implementation may adjust micro-details but invariants & semantics are stable.


## Proposed Public API Surface (Jobs & Deferred) — Design Only

Note: This section sketches prospective APIs. They are not implemented today and do not appear in public headers; the current code uses scopes and cancellation tokens (see kc_scope_* and kc_cancel_* in headers). Return conventions would follow the project standard: 0 on success; negative errno-style codes on failure.

```
int kc_launch(kcoro_fn_t fn, void *arg);
int kc_async(kcoro_fn_t fn, void *arg, kc_deferred_t **out_def);
int kc_await(kc_deferred_t *def, void **out_payload);
int kc_cancel_current(void);
int kc_cancel_job(kc_job_t *job, int reason);
```

Notes
- kc_launch: fire-and-forget child coroutine in the current job/context.
- kc_async/kc_await: structured async with a deferred handle; await joins the underlying job and optionally yields a payload pointer.
- kc_cancel_current/kc_cancel_job: cooperative cancellation; suspension points observe cancellation promptly.


## Observability & Metrics

This component exposes counters and trace events to aid debugging and performance analysis.

Counters (initial set)
- jobs_created
- cancellations_requested
- jobs_failed
- jobs_completed
- jobs_cancelled
- waiters_registered
- waiters_woken

Events (examples)
- JOB_CREATE, JOB_STATE, JOB_CANCEL_REQ, JOB_FINALIZE, JOB_CHILD_ATTACH, JOB_WAIT_REGISTER, JOB_WAIT_WAKE.

Notes
- Counters increment exactly once per successful logical event.
- Event emission is cheap and optional in release builds; enable only when diagnostics are needed.


## Performance Targets

- Job creation: <= 450 ns (stretch 300 ns)
- Deferred await overhead (cached): <= 150 ns (stretch 100 ns)
