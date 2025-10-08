# Coroutine Context Model

Abstract: This document fully specifies `kc_context_t` and its operations (add/get/retain/release), data structures, invariants, and usage patterns. It is self‑contained and includes algorithms and edge cases so it can be read independently.

## 1. Goals
- Provide immutable, persistent key/value context attached to a coroutine.
- Allow efficient lookup (O(n) with small n; target ≤ 6 typical) and structural sharing.
- Embed runtime elements: dispatcher, job, cancellation token/deadline, tracing metadata.
- Support user‑defined keys without global registries (pointer identity keys).

## 2. Concept Overview
`kc_context_t` is an immutable aggregate of elements. Each element is `(key -> value, drop, flags)` where the key’s identity is the address of a static `kc_ctx_key_t`. Adding an element produces a new context object that references (shares) the previous one (persistent list) or copies into a small array for locality (small‑object optimization).

## 3. Data Structures
```c
// key identity: address uniqueness
typedef struct kc_ctx_key { const char *name; } kc_ctx_key_t;

typedef void (*kc_ctx_drop_fn)(void *value);

typedef struct kc_ctx_elem {
  const kc_ctx_key_t *key;
  void *value;
  kc_ctx_drop_fn drop; // optional
  uint32_t flags;      // reserved (e.g., NO_INHERIT)
} kc_ctx_elem_t;

typedef struct kc_context kc_context_t;

struct kc_context {
  _Atomic(int) refcnt;
  uint8_t size;      // number of elements in array tail
  uint8_t cap;       // capacity of inline array region
  uint8_t depth;     // structural depth (for debug)
  uint8_t flags;     // reserved
  kc_context_t *parent;   // shared tail (NULL if root)
  kc_ctx_elem_t elems[];  // flexible array (size 'size')
};
```

Growth strategy: if `size < SMALL_CTX_INLINE_MAX (5)` allocate a new object copying existing inline elems plus new; else allocate an object with the new elem and set `parent` to the previous context (avoids full copy). Lookup scans the local array first, then walks the parent chain.

## 4. Core Operations (Pseudo‑Code)
Add element / replace existing:
```c
kc_context_t* kc_context_add(kc_context_t *base, const kc_ctx_key_t *key,
                              void *value, kc_ctx_drop_fn drop) {
  if (!base) base = kc_context_empty();
  // Check existing in local array for replacement
  for (int i=0;i<base->size;i++) if (base->elems[i].key==key) {
      // create copy with replaced element
      return clone_with_replacement(base,i,key,value,drop);
  }
  if (base->size < base->cap && base->parent==NULL) {
      // fast path enlarge inline array
      return clone_with_append(base,key,value,drop);
  }
  return allocate_layer(key,value,drop, base); // parent link
}
```

Lookup:
```c
void* kc_context_get(const kc_context_t *ctx, const kc_ctx_key_t *key) {
  for (const kc_context_t *c=ctx; c; c=c->parent) {
     for (int i=0;i<c->size;i++) if (c->elems[i].key==key) return c->elems[i].value;
  }
  return NULL;
}
```

Reference management: contexts are reference‑counted; additions create new objects referencing the prior context (inc ref). Destruction decrements recursively when `refcnt` hits zero, calling each element’s drop exactly once for its stored value.

## 5. Reserved / Built‑In Keys
| Key | Name | Description | Required |
|-----|------|-------------|----------|
| `KC_CTX_KEY_DISPATCHER` | "dispatcher" | Active dispatcher binding | Yes |
| `KC_CTX_KEY_JOB`        | "job"       | Current job pointer       | Yes |
| `KC_CTX_KEY_CANCEL`     | "cancel"    | Cancellation token (may alias job) | Yes |
| `KC_CTX_KEY_DEADLINE`   | "deadline"  | Absolute monotonic deadline (int64 ns) | Optional |
| `KC_CTX_KEY_TRACE_ID`   | "trace_id"  | 128‑bit trace identifier pointer | Optional |

## 6. Interaction with Jobs and Scheduler
- On launching a coroutine, its initial context is `(parent_context + dispatcher + job + cancel(if distinct) + inherited user keys excluding NO_INHERIT)`.
- Context is captured at suspension; resumption uses the stored pointer (no recomputation).
- Dispatcher switching produces a derived context layering a new dispatcher key; resumption obeys the new binding.

## 7. API Surface (Proposed)
```c
// Key declarations (defined in library)
extern const kc_ctx_key_t KC_CTX_KEY_DISPATCHER;
extern const kc_ctx_key_t KC_CTX_KEY_JOB;
extern const kc_ctx_key_t KC_CTX_KEY_CANCEL;
extern const kc_ctx_key_t KC_CTX_KEY_DEADLINE;
extern const kc_ctx_key_t KC_CTX_KEY_TRACE_ID;

kc_context_t* kc_context_empty(void);
kc_context_t* kc_context_add(kc_context_t *base, const kc_ctx_key_t *key, void *value, kc_ctx_drop_fn drop);
void*         kc_context_get(const kc_context_t *ctx, const kc_ctx_key_t *key);
kc_context_t* kc_context_replace(kc_context_t *base, const kc_ctx_key_t *key, void *value, kc_ctx_drop_fn drop);
kc_context_t* kc_context_retain(kc_context_t *ctx);
void          kc_context_release(kc_context_t *ctx);

// Convenience typed helpers
int kc_context_get_deadline_ns(const kc_context_t *ctx, long long *out_ns);

// Coroutine interaction
kc_context_t* kc_context_current(void); // borrowed pointer
int kc_context_with(kc_context_t *ctx, kcoro_fn_t fn, void *arg); // run fn under ctx (push/pop)
```

## 8. Complexity & Performance
- Add/replace: O(k) copy of small arrays (k ≤ 6) or O(1) allocate layer.
- Lookup: worst‑case O(k * depth). Depth typically 0–2 before a flattening heuristic might trigger.

## 9. Invariants
1) No duplicate keys within any lookup path (nearest wins).
2) Each value drop is called exactly once when the owning context is destroyed.
3) `kc_context_current()` always contains dispatcher & job keys for the executing coroutine.
4) Depth grows by at most 1 per additive operation unless replacement is used.
5) A layer with `parent!=NULL` typically has `size==1` (layer form) unless flattened.

## 10. Edge Cases
- Replacing dispatcher while job is cancelling: allowed; cancellation path unaffected (job/cancel keys pinned).
- Adding `deadline` multiple times: last added nearest layer wins; timer checks current value at suspension.
- Using freed key objects is undefined; keys must have static storage duration.
- NO_INHERIT flag marks elements not copied to child launches (future).

## 11. Example Usage
```c
// Define a custom key
static const kc_ctx_key_t MY_SERVICE_KEY = { .name = "svc" };

struct service_handle *svc = acquire_service();
kc_context_t *ctx2 = kc_context_add(kc_context_current(), &MY_SERVICE_KEY, svc, (kc_ctx_drop_fn)release_service);
kc_context_with(ctx2, run_subtask, NULL);
kc_context_release(ctx2); // drops layer; svc released after subtask completes & refs drop
```

## 12. Testing Matrix
| Scenario | Expectation |
|----------|-------------|
| Add custom key & lookup | Value returned; refcounts correct |
| Replace existing key | New value visible; old drop called once |
| Layer depth > threshold flatten | Lookup cost stable (instrument) |
| Dispatcher override | Task executes on new dispatcher |
| Deadline retrieval absent | Returns ENOENT |
| Context with cancelling job | Cancellation visible via keys |
| Concurrent additions in separate coroutines | Isolation; no cross contamination |
