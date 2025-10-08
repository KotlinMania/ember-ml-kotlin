# Context Switching (component summary)

Abstract: This document is a self-contained summary of the coroutine context-switching design and implementation considerations required by the project. It describes the expected assembly primitives, stack layout, portability considerations, testing harnesses, and key invariants that implementers and reviewers need to know. It intentionally contains the minimal design and implementation notes so that this component folder stands alone.

Key points
- Primitive: a small, verifiable assembly primitive performs the low-level context switch (save/restore callee-saved regs, stack pointer, instruction pointer). Current production implementation: ARM64 assembly (`arch/aarch64/kc_ctx_switch.S`) with a C wrapper.
- Stack model: each coroutine gets a private stack (mmap). Stack caching (LRU bounded) is used in scheduler to reduce mmap churn. Guard pages and stack checks are compiled with `KCORO_CTX_DIAGNOSTICS`; runtime checks enable when `KCORO_DEBUG_CTX_CHECK` is set.
- Caller/callee responsibilities: switch primitive must preserve ABI, maintain a valid "from" coroutine (fallback to `main_co`), and ensure registers and stack are consistent for unwinding and debugging.
- Portability approach: keep assembly surface minimal — only switch primitive in arch-specific file; all control logic remains in C.
- Test harnesses: `lab_minimal_switch.c`, `phase1/test_phase1_coroutines.c`, and `test_kcoro_production.c` exercise the context switch for correctness across architectures.
- Interaction with scheduler: workers call `kcoro_resume` to resume coroutines; coroutine returns must follow a safe destruction pattern to avoid use-after-free during concurrent resumes.

API excerpts (what consumers need):
- `kc_context_t` is the runtime coroutine context abstraction (see `include/kc_context.h` for public prototypes): `kc_context_empty`, `kc_context_add`, `kc_context_get`, `kc_context_retain`, `kc_context_release`, `kc_context_current`, `kc_context_with`.

Invariants and safety
- A valid "from" coroutine must be present when resuming; restore fallback if none.
- Stack guard pages and debug checks help catch stack-corruption issues early.
- Assembly switch must be small and auditable; avoid embedding complex logic.

Lifecycle & State Machine
- The coroutine lifecycle is the authoritative state machine implementers should follow. States: CREATED -> READY -> RUNNING -> SUSPENDED -> PARKED -> FINISHED.
- Typical transitions:
	- CREATED -> (first `kcoro_resume`) -> RUNNING
	- RUNNING -> `kcoro_yield()` -> SUSPENDED -> (resume) -> RUNNING
	- RUNNING -> `kcoro_park()` -> PARKED -> `kcoro_unpark()` -> READY -> (scheduler resumes) -> RUNNING
	- RUNNING -> function returns -> FINISHED

API Contracts (concise)
The following API behavior is required by higher-level components and tests. Include these contracts in any public header docs to keep components independent:
- `kcoro_create_main()` — initialize the main coroutine for a worker thread; returns NULL on OOM.
- `kcoro_create(fn, arg, stack_size)` — allocate coroutine structure and private stack (mmap); returns NULL on OOM or mmap failure.
- `kcoro_resume(co)` — switch to `co` if not FINISHED; no-op if `co` is NULL or FINISHED. Caller becomes SUSPENDED and target becomes RUNNING. Resume must preserve a valid "from" coroutine (fallback to `main_co`).
- `kcoro_yield()` — yield back to the current `main_co`; marks caller SUSPENDED and restores RUNNING to main.
- `kcoro_yield_to(target)` — direct yield to `target` (use sparingly; scheduler typically resumes via queues).
- `kcoro_park()` / `kcoro_unpark()` — cooperative park/unpark semantics: `park` marks PARKED and switches to main; `unpark` transitions to READY and enqueues when a scheduler exists.
- `kcoro_destroy(co)` — unmap stack (if non-main) and free the structure; callers must ensure no other worker can still reference `co` (see core refcount/retire guidance below).

Trampoline & Switcher notes
- `kcoro_trampoline` is the canonical entry used to start coroutine functions: it sets state RUNNING, invokes `fn(arg)`, marks the coroutine FINISHED on return, and switches back to `main_co`. If a coroutine function ever returns past the trampoline unexpectedly, a protector will abort to avoid undefined behaviour.
- Assembly switcher constraints: save/restore callee-saved registers, SP, and FP. Do not modify signal masks, TLS, or FP/SIMD state in the minimal switcher; document any future extension carefully.




## Performance Targets

- Coroutine context switch: <= 180 ns (stretch 120 ns)


## Core Types & Layout (struct kcoro)

A minimal subset of the coroutine header that the switcher and guards rely on. Diagnostics fields are compiled under KCORO_CTX_DIAGNOSTICS.

```
struct kcoro {
    void   *reg[32];         /* register save area; ABI-dependent; must be first */
    /* Metadata */
    int      state;          /* CREATED, READY, RUNNING, SUSPENDED, PARKED, FINISHED */
    void   (*fn)(void *);    /* entry function; NULL for main */
    void    *arg;            /* user argument */
    uint64_t id;             /* monotonic id (0 = main) */
    struct kcoro *main_co;   /* yield target (typically main) */
    void    *stack_ptr;      /* raw mapping base (includes guard) */
    size_t   stack_size;     /* total mapping size */
    const char *name;        /* optional label */
#ifdef KCORO_CTX_DIAGNOSTICS
    uint64_t  canary;            /* integrity sentinel */
    void     *stack_usable_base; /* first usable byte after guard */
    size_t    stack_usable_size; /* usable bytes (excludes guard) */
#endif
};
```

Register mapping is architecture-specific. See arch/aarch64/CONTEXT_SWITCH.md for the ARM64 mapping (e.g., reg[14]=SP, reg[15]=FP, reg[13]=LR).

## Stack Allocation & Protection

Creation path provisions a private stack per coroutine:
1) Choose usable size (default 64 KiB) rounded to page size.
2) Map (guard_page + usable_region) with mmap(RW).
3) mprotect the low page to PROT_NONE to create a downward guard.
4) Compute usable_base = raw + page_size; seed SP to top of usable region (aligned), FP to a sensible value; seed LR to kcoro_trampoline.

Rationale: ARM64 stacks grow downward. A single low guard traps the first page breach. Upward overflow is not presently trapped.

## Trampoline & Switch (execution flow)

kcoro_trampoline sets state RUNNING, calls fn(arg), marks FINISHED on return, then switches back to main_co. Returning past trampoline triggers a protector that aborts (programmer error in tests/dev).

The assembly switch saves/restores callee-saved registers, SP/FP, and the continuation (LR); it does not touch FP/SIMD, TLS, or signal masks.

## Diagnostics (KCORO_CTX_DIAGNOSTICS + KCORO_DEBUG_CTX_CHECK)

Build-time: compile with KCORO_CTX_DIAGNOSTICS to add checks and extra fields. Runtime: enable checks by setting KCORO_DEBUG_CTX_CHECK to a non-empty, non-"0" value.

Checks fire at:
- Creation (seed canary; validate SP within [stack_usable_base, stack_usable_base + stack_usable_size]).
- Pre-resume (validate "from" and target; state sanity).
- Post-resume / post-yield (SP bounds and canary).

Failure modes:
- Canary mismatch → abort with diagnostics.
- SP out of range → abort.
- Guard page breach → SIGSEGV before diagnostics (expected).

Cross-link: see components/configuration/CONFIGURATION.md for build/run incantations.

## Parking & Scheduler Interaction (recap)

kcoro_park marks RUNNING→PARKED and switches to main; kcoro_unpark marks PARKED→READY and enqueues via the scheduler when present. Manual resume loops are acceptable in tests; production resumes typically flow through ready queues.
