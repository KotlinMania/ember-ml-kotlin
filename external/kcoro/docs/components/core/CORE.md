# Coroutine Core — As‑Built, Code‑Backed Design

Abstract: This document describes the internal coroutine object model (kcoro), lifecycle, state machine, trampoline/protector, and integration with the minimal context‑switch primitive. It is a clean‑room explanation based on the code in user/src and include/ headers. It stands alone; names are provided only for orientation.

## 1) Coroutine Core (kcoro_core.c, kcoro_core.h)

Structure (struct kcoro in include/kcoro_core.h)
- reg[32]: register save area used by the ARM64 switcher.
  - Indices: x19..x28 → reg[0..9], LR(x30) → reg[13], SP → reg[14], FP(x29) → reg[15].
- state: KCORO_CREATED | KCORO_READY | KCORO_RUNNING | KCORO_SUSPENDED | KCORO_PARKED | KCORO_FINISHED.
- fn, arg: entry function and its argument.
- id: unique 64‑bit id (increments via __sync_fetch_and_add).
- main_co: coroutine to yield back to (the worker’s main coroutine).
- scheduler: owning kc_sched_t pointer when scheduled.
- stack_ptr/stack_size: private stack allocated with mmap; 16‑byte aligned SP.
- next/prev: ready‑queue links; name: optional string for debugging.

Lifecycle
- kcoro_create(fn,arg,stack): allocates struct, mmaps a private stack (default 64 KiB if 0), aligns SP, and seeds reg[13]=kcoro_trampoline, reg[14]=SP, reg[15]=FP. State=CREATED.
- kcoro_resume(co): switches from current to co via kcoro_switch. State transitions: caller→SUSPENDED, target→RUNNING; upon return, restore current and mark RUNNING.
- kcoro_yield(): switch back to main_co; marks current SUSPENDED, main RUNNING, then resumes later and restores RUNNING.
- kcoro_yield_to(target): direct yield to another coroutine (rare path; scheduling typically resumes via queues).
- kcoro_park(): mark current PARKED and switch to main; later kcoro_unpark sets state=READY and enqueues if a scheduler is active.
- kcoro_destroy(co): unmaps private stack and frees struct. Coroutines owned by a scheduler are destroyed by the scheduler once resumption completes.
- kcoro_current(): TLS pointer to current coroutine; kcoro_create_main(): constructs a special “main” coroutine per worker thread.

Trampoline & Protector
- kcoro_trampoline: internal entry that calls fn(arg), then marks FINISHED and switches back to main; if user code ever returns past the trampoline unexpectedly, a protector aborts to avoid undefined behaviour.
- kcoro_funcp_protector_asm: assembly stub that preserves LR while calling kcoro_funcp_protector (diagnostics).

## 2) Refcount / Retire Queue (Lifecycle Hardening)

Goal: avoid use‑after‑free races observed in stress runs when one thread frees a coroutine while another still references it.

Plan
1. Add an atomic refcount to kcoro_t. Owners (scheduler enqueue, worker resume, retire queue) increment while holding a reference and decrement when done.
2. Replace immediate frees with a retire queue: instead of destroying inside the worker return path, push kcoro_t* onto a per‑scheduler retire list protected by a mutex.
3. A cleanup stage drains the retire list and calls kcoro_destroy only when the refcount reaches zero. This guarantees no worker holds a pointer to freed memory during concurrent resumes.
4. Special‑case main coroutines to maintain a permanent reference while their hosting thread is alive.

Notes
- Fast resume/yield paths remain lightweight (only atomic refcount ops); heavier reclamation work is deferred to the cleanup path.
- In debug builds, assertions ensure `refcount >= 1` for any coroutine reachable from queues; negative refcounts or double‑frees assert.

Diagnostic validation notes
- Repro: a headless monitor run combined with an ASan harness previously exposed UAF in the ready‑queue path.
- Validation: after converting the ready queue to intrusive links and adding the retire/refcount scheme, repeated runs produced no ASan reports; addresses remained stable and reuse patterns were clean.

## 3) Context Switch Integration (kc_ctx_switch.S quick view)

- kcoro_switch(from,to) saves callee‑saved regs and SP into from->reg[], restores to->reg[], and branches to to->reg[13] (continuation).
- Only callee‑saved GPRs are touched; no FP/SIMD state is saved in the current ARM64 path.
- Stack alignment and calling convention are respected; SP is restored exactly as saved.

## 4) State Machine & Invariants (recap)

States
- CREATED -> READY -> RUNNING -> (SUSPENDED | PARKED) -> RUNNING -> FINISHED.

Invariants
1. A parked coroutine is enqueued only after state is set to READY.
2. Coroutines are resumed by the thread that owns their main_co.
3. Destroy happens after completion and only when no other owner holds a reference (via retire queue policy).

## 5) API Surface (selected)

- kcoro_create / kcoro_destroy
- kcoro_resume / kcoro_yield / kcoro_yield_to
- kcoro_park / kcoro_unpark
- kcoro_current / kcoro_create_main

## 6) Portability & Security Notes

- The control logic is in C; only the minimal register swap lives in an arch‑specific file. Other architectures can reuse the same core by providing the switch primitive.
- Stack guard pages may be enabled in debug builds to detect overflow early.


## Performance Targets

- Coroutine spawn (empty body): <= 2.5 µs (stretch 1.2 µs)
- Memory per parked coroutine (including stack): <= 8 KB (stretch 4 KB)
