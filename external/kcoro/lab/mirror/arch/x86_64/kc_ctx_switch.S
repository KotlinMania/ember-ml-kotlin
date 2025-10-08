/*
 * x86_64 System V context switching for kcoro
 *
 * Implements the low-level primitive:
 *     void* kcoro_switch(kcoro_t* from_co, kcoro_t* to_co);
 *
 * Contract (matches kcoro_core.c expectations):
 *   kcoro_t has `void* reg[32]` as its first field. Indices used on x86_64:
 *     reg[ 0..5] : r12, r13, r14, r15, rbx, rbp   (callee-saved)
 *     reg[13]    : rip (continuation to jump/return to)
 *     reg[14]    : rsp (stack pointer)
 *     reg[15]    : rbp (frame/base pointer)
 *
 * Save from_co's callee-saved regs, RIP (from [rsp]), and RSP/RBP.
 * Restore to_co's RSP/RBP and callee-saved regs, then transfer control to
 * its saved continuation. To make the continuation look like a normal call
 * target w.r.t. ABI stack alignment, we push the saved rip and `ret` to it.
 *
 * Notes:
 *  - System V AMD64: caller ensures 16-byte alignment before `call`. At function
 *    entry, after the return address is pushed, RSP % 16 == 8. By pushing the
 *    saved continuation and using `ret`, we preserve the expected entry state.
 *  - We do not save/restore XMM/FPU state here; kcoro avoids relying on it across
 *    switches (mirrors the ARM64 implementation policy).
 */

.text
.align 4
#ifdef __APPLE__
#define FUNC_TYPE(name)
#define FUNC_SIZE(name, expr)
#define GLOBAL(name) .globl _##name
#define LABEL(name) _##name
#else
#define FUNC_TYPE(name) .type name, %function
#define FUNC_SIZE(name, expr) .size name, expr
#define GLOBAL(name) .globl name
#define LABEL(name) name
#endif

/* Indices expressed as byte offsets (index * 8) */
.set REG_R12,   0x00   /* reg[0]  */
.set REG_R13,   0x08   /* reg[1]  */
.set REG_R14,   0x10   /* reg[2]  */
.set REG_R15,   0x18   /* reg[3]  */
.set REG_RBX,   0x20   /* reg[4]  */
.set REG_RBP,   0x28   /* reg[5]  */
.set REG_RIP,   0x68   /* reg[13] */
.set REG_RSP,   0x70   /* reg[14] */
.set REG_FP,    0x78   /* reg[15] (rbp mirror for parity with ARM mapping) */

GLOBAL(kcoro)
FUNC_TYPE(LABEL(kcoro))
GLOBAL(kcoro_switch)
FUNC_TYPE(LABEL(kcoro_switch))

/* System V: rdi=from_co, rsi=to_co */
LABEL(kcoro):
LABEL(kcoro_switch):
    /* Save callee-saved registers into from_co->reg[] */
    movq %r12, REG_R12(%rdi)
    movq %r13, REG_R13(%rdi)
    movq %r14, REG_R14(%rdi)
    movq %r15, REG_R15(%rdi)
    movq %rbx, REG_RBX(%rdi)
    movq %rbp, REG_RBP(%rdi)

    /* Save return address (current continuation) from [rsp] into reg[13] */
    movq (%rsp), %rax
    movq %rax, REG_RIP(%rdi)

    /* Save stack pointers */
    movq %rsp, %rax
    movq %rax, REG_RSP(%rdi)
    /* Mirror rbp in reg[15] for parity with kcoro_core.c setup */
    movq %rbp, %rax
    movq %rax, REG_FP(%rdi)

    /* Restore to_co state */
    /* Load stack pointers first */
    movq REG_RSP(%rsi), %rsp
    movq REG_FP(%rsi),  %rbp

    /* Restore callee-saved regs */
    movq REG_R12(%rsi), %r12
    movq REG_R13(%rsi), %r13
    movq REG_R14(%rsi), %r14
    movq REG_R15(%rsi), %r15
    movq REG_RBX(%rsi), %rbx
    /* rbp already restored */

    /* Transfer to continuation: push RIP then ret to keep ABI entry semantics */
    movq REG_RIP(%rsi), %rax
    pushq %rax
    ret

FUNC_SIZE(LABEL(kcoro), .-LABEL(kcoro))
FUNC_SIZE(LABEL(kcoro_switch), .-LABEL(kcoro_switch))

/* Optional stub: no-op FPU control save (matches ARM64 policy) */
GLOBAL(kcoro_save_fpucw_mxcsr)
FUNC_TYPE(LABEL(kcoro_save_fpucw_mxcsr))
LABEL(kcoro_save_fpucw_mxcsr):
    ret
FUNC_SIZE(LABEL(kcoro_save_fpucw_mxcsr), .-LABEL(kcoro_save_fpucw_mxcsr))

/* Protector thunk that calls into C helper and aborts */
GLOBAL(kcoro_funcp_protector_asm)
FUNC_TYPE(LABEL(kcoro_funcp_protector_asm))
LABEL(kcoro_funcp_protector_asm):
    /* Preserve rbp and call C helper */
    pushq %rbp
    movq %rsp, %rbp
#ifdef __APPLE__
    callq _kcoro_funcp_protector
    callq _abort
#else
    callq kcoro_funcp_protector
    callq abort
#endif
    /* Should not return, but restore frame if it does */
    leave
    ret

#ifdef __APPLE__
#undef FUNC_TYPE
#undef FUNC_SIZE
#undef GLOBAL
#undef LABEL
#endif
