# ARM64 Context Switch — Register Layout, Offsets, and ABI Notes

This file documents the exact contract between the coroutine core (kcoro_core.c) and the ARM64 assembly switcher (arch/aarch64/kc_ctx_switch.S). It is derived from the code and intended to be authoritative for ARM64 ports.

Hot path entry points
- kcoro and kcoro_switch are synonymous entry labels that implement the same routine. C calls kcoro_switch(from, to).

Calling convention
- x0 = from_co (struct kcoro*)
- x1 = to_co   (struct kcoro*)

Saved/restored state (callee-saved only)
- General-purpose callee-saved registers x19…x28.
- Link register x30 (used as continuation address).
- Stack pointer sp.
- Frame pointer x29.
- No FP/SIMD state is saved or restored; kcoro_save_fpucw_mxcsr is a documented no-op on ARM64.

reg[] index mapping (struct kcoro.reg)
- reg[0..9]   = x19..x28
- reg[13]     = x30 (link register / continuation)
- reg[14]     = sp  (stack pointer)
- reg[15]     = x29 (frame pointer)

Byte offsets (8 bytes per entry on a 64‑bit target)
- x19 @ 0x00 (reg[0]) … x28 @ 0x48 (reg[9])
- x30 @ 0x68 (reg[13])
- sp  @ 0x70 (reg[14])
- x29 @ 0x78 (reg[15])

Switch sequence (simplified)
1) Save from->x19..x28.
2) Save from->x30 into reg[13]; save from->sp into reg[14]; save from->x29 into reg[15].
3) Load to->x19..x28, to->x29, and to->sp.
4) Load to->x30 from reg[13] and branch via `br x9` to the continuation.

Stack model
- kcoro_core allocates a private stack per coroutine via mmap(). kcoro_create seeds SP and LR (kcoro_trampoline) into reg[] so the first kcoro_switch enters the trampoline.
- The assembly respects the 16‑byte ARM64 stack alignment; kcoro_core pre-aligns SP during creation.

Portability & policy
- All scheduling, policy, and synchronization logic stays in C. The assembly routine is strictly a register/stack switch to keep maintenance portable and the hot path as small as possible.

Diagnostics & safety
- kcoro_funcp_protector_asm preserves LR while calling kcoro_funcp_protector, which aborts when a coroutine function returns to nowhere (programmer error), ensuring crashes are explicit in dev/testing builds.

