# ARM64 Strategy — Scope, Guardrails, and Future Work

Objective
- Preserve a tiny, verifiable assembly surface that focuses exclusively on context switching and, if proven necessary by profiling, narrowly scoped data movers. All control-plane logic remains in C for portability and maintainability.

What stays in assembly
- kcoro_switch: register/stack swap as documented in the context-switching component.
- Optional micro-kernels: bounded-size memcpy/memmove tuned for coroutine stack slice copies if profiling identifies them as material (none are in-tree today).

What stays in C
- Scheduling policy, fairness, and ready-queue logic (kc_sched.c).
- Coroutine ownership, lifecycle, trampoline, and parking (kcoro_core.c).
- Channels, select, scopes, zero‑copy backends, and IPC control paths.

Performance posture
- The current switcher already yields ~10 ns coroutine hops under realistic conditions on ARM64. Optimization efforts should begin with algorithmic wins (reduced copying, zero‑copy pointers, wait‑free snapshots) rather than deeper assembly.

Portability posture
- x86_64 or RISC‑V ports need only provide their kcoro_switch and keep the same C control plane. The reg[] index scheme is localized to architecture backends and hidden behind kcoro_core.h.

Safety & testability
- Keep the assembly leaf-like and side‑effect free (no syscalls, no allocation, no locking). This simplifies fuzzing and unit testing of the C plane while leaving the asm with narrow responsibilities validated by functional tests.

When to consider more asm
- Evidence from benchmarks or perf shows that a pure-C hot path is the dominant time sink (≥15–20% of end-to-end). Only then consider:
  - SIMD‑assisted copy helpers for stack slices or descriptor batches.
  - Very small atomic/bitset helpers if compilers fail to generate optimal code.
- Even in such cases, constrain the surface to leaf helpers with stable calling conventions and minimal ABI coupling.

Non-goals
- No attempt to implement scheduler or channel state machines in assembly.
- No inline asm in the C control plane; keep the separation clear for readability.

