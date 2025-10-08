# Structured Scopes — Ownership, Cancellation, and Teardown Semantics

Scopes provide structured concurrency: they own children (coroutines and actors), propagate cancellation, and support waiting for completion with a deadline. This document reflects user/src/kc_scope.c.

Data structures
- kc_scope_t
  - cancel_ctx: kc_cancel_ctx_t with an owned token; allows parent→child propagation via kc_scope_token().
  - mu / cv: mutex and condvar guarding child list and shutdown state.
  - shutting_down: bool set once cancel/teardown begins; blocks new launches.
  - child_count: current number of registered children.
  - children: singly-linked list of kc_scope_child nodes.
- kc_scope_child
  - kind: CORO or ACTOR.
  - u: { coro: kcoro_t* | actor: kc_actor_t }.
  - scope pointer and next link.
- Auxiliary wrappers for launching coroutines (kc_scope_coro_wrapper) and producers (kc_scope_producer_state).

Creation and token
- kc_scope_init(&out,parent_token): allocates scope, initializes cancel_ctx with optional parent; returns scope. kc_scope_token(scope) returns the scope’s cancel token for passing to children APIs.

Cancellation
- kc_scope_cancel(scope): triggers the scope’s token (kc_cancel_trigger), marks shutting_down=1, and cancels all registered actors by calling kc_actor_cancel on each. Child coroutines observe cancel via their own logic (not forcefully preempted).

Launch and child registration
- kc_scope_launch(scope, fn, arg, stack, &out_co):
  - Validates scope not shutting down; allocates a kc_scope_child(kind=CORO) and a small wrapper struct.
  - Spawns a coroutine (kc_spawn_co) that calls user fn(arg) inside kc_scope_coro_entry, then removes the child from the list and frees the wrapper.
  - Returns the coroutine handle through out_co.
- kc_scope_actor(scope, ctx):
  - Augments ctx with the scope’s cancel token and starts an actor (kc_actor_start_ex).
  - Registers the actor as a child and installs an on_done callback so the child is removed on exit.
- kc_scope_produce(scope, kind, elem_sz, capacity, fn, user):
  - Creates a channel, spawns a producer coroutine that runs fn(chan,user) and then closes the channel, and returns the channel to the caller.

Waiting for children
- kc_scope_wait_all(scope, timeout_ms):
  - timeout_ms = 0 → returns 0 if no children, KC_EAGAIN otherwise.
  - timeout_ms > 0 → waits on cv until child_count==0 or absolute deadline elapses (KC_ETIME).
  - timeout_ms < 0 → waits indefinitely.

Thread-safety and invariants
- All mutations of the child list and child_count are guarded by mu.
- Children are removed exactly once by kc_scope_child_complete; the on_done callback for actors routes through this function.
- New launches are rejected once shutting_down is set to prevent races during teardown.

Error handling
- Allocation failures return -ENOMEM; invalid arguments return -EINVAL.
- Launch after shutdown returns KC_ECANCELED to match cancel-first semantics.

Notes
- Scopes do not join or cancel child coroutines automatically on kc_scope_cancel; they rely on user code and cancellation-aware APIs (channels/select) to observe the token and exit cooperatively.





## API (overview)

Canonical surface (from headers):
```c
#include "kcoro.h"

int   kc_scope_init(kc_scope_t **out, const kc_cancel_t *parent);
void  kc_scope_cancel(kc_scope_t *scope);
int   kc_scope_launch(kc_scope_t *scope, kcoro_fn_t fn, void *arg,
                      size_t stack_size, kcoro_t **out_co);
kc_actor_t kc_scope_actor(kc_scope_t *scope, const kc_actor_ctx_t *ctx);
kc_chan_t* kc_scope_produce(kc_scope_t *scope, int kind, size_t elem_sz, size_t capacity,
                            kc_producer_fn fn, void *user);
int   kc_scope_wait_all(kc_scope_t *scope, long timeout_ms);
void  kc_scope_destroy(kc_scope_t *scope);
const kc_cancel_t* kc_scope_token(const kc_scope_t *scope);
```

Usage sketch:
```c
kc_scope_t *scope = NULL;
if (kc_scope_init(&scope, /*parent*/ NULL) == 0) {
  kcoro_t *co = NULL;
  kc_scope_launch(scope, my_fn, my_arg, 0, &co);

  kc_actor_ctx_t actx = { .chan = ch, .msg_size = sizeof(Msg), .timeout_ms = -1, .process = on_msg, .user = NULL };
  kc_actor_t a = kc_scope_actor(scope, &actx);

  /* ... */
  kc_scope_wait_all(scope, 1000);
  kc_scope_destroy(scope);
}
```

Semantics (brief):
- Initializes a scope with an optional parent token; children inherit cancellation via kc_scope_token.
- Launch runs work within the scope and registers it as a child; kc_scope_actor wires the scope token into the actor.
- wait_all cooperatively waits for children to finish (timeout/cancel semantics as documented).
- cancel triggers scope cancellation and rejects further launches.
