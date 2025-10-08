/*
 * ARM64/aarch64 context switching for kcoro
 *
 * This file implements the low-level register/stack switch primitive `kcoro`:
 *     void* kcoro(kcoro_t* from_co, kcoro_t* to_co);
 *
 * Conceptually:
 *   - Save the current coroutine's callee-saved registers and stack pointer
 *     into from_co->reg[]
 *   - Restore the target coroutine's registers and stack pointer from
 *     to_co->reg[]
 *   - Branch to to_co->reg[RETADDR] (the saved continuation / entry point)
 *
 * Stack model:
 *   libkcoro uses a shared stack. Higher-level C code copies the active slice
 *   of the stack between a coroutine-private save buffer and the shared stack
 *   before/after calling `kcoro`. This assembly switches only registers and SP.
 *
 * Register layout in kcoro_t.reg for ARM64 (indices into void* reg[32]):
 *   reg[ 0.. 9] : x19..x28           (callee-saved)
 *   reg[15]     : x29 (frame pointer)
 *   reg[13]     : x30 (return addr)  (a.k.a. link register / continuation)
 *   reg[14]     : sp  (stack pointer)
 *
 * Byte offsets = index * 8 (because reg[] is an array of void* on 64-bit):
 *   x19  @ 0x00  (reg[0])     ... x28 @ 0x48 (reg[9])
 *   x30  @ 0x68  (reg[13])
 *   sp   @ 0x70  (reg[14])
 *   x29  @ 0x78  (reg[15])
 *
 * ABI notes:
 *   - ARM64 requires 16-byte stack alignment on public interfaces. The C
 *     runtime sets up align_retptr accordingly; this routine preserves SP.
 *   - We only touch callee-saved registers; caller-saved regs/args are the
 *     responsibility of the surrounding C code.
 *   - We do not save/restore FP/SIMD state here; libkcoro avoids using it on
 *     ARM64 (kcoro_save_fpucw_mxcsr is a no-op stub).
 *
 * License:
 *   Inspired by work done on libkcoro, Sen Han <00hnes@gmail.com>
 *   
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

#ifndef KC_VM_SWITCH
GLOBAL(kcoro)
FUNC_TYPE(LABEL(kcoro))
GLOBAL(kcoro_switch)
FUNC_TYPE(LABEL(kcoro_switch))

LABEL(kcoro):
LABEL(kcoro_switch):
    /*
     * ARM64 calling convention:
     *   x0 = from_co (source context)
     *   x1 = to_co   (target context)
     *
     * Save from_co callee-saved registers and SP, then restore to_co's.
     * Finally, branch to to_co's saved continuation (reg[13]).
     */

    /* Save from_co context */
    /* Save callee-saved registers at indices 0-9 */
    str x19, [x0, #0x00]        /* reg[0] */
    str x20, [x0, #0x08]        /* reg[1] */
    str x21, [x0, #0x10]        /* reg[2] */
    str x22, [x0, #0x18]        /* reg[3] */
    str x23, [x0, #0x20]        /* reg[4] */
    str x24, [x0, #0x28]        /* reg[5] */
    str x25, [x0, #0x30]        /* reg[6] */
    str x26, [x0, #0x38]        /* reg[7] */
    str x27, [x0, #0x40]        /* reg[8] */
    str x28, [x0, #0x48]        /* reg[9] */
    
    /* Save special registers at their designated indices */
    str x30, [x0, #0x68]        /* save lr (link register) as continuation at reg[13] */
    mov x9, sp
    str x9, [x0, #0x70]         /* save sp at reg[14] */
    str x29, [x0, #0x78]        /* save x29 (frame pointer) at reg[15] */
    
    /* Load to_co context */
    /* Restore callee-saved registers from indices 0-9 */
    ldr x19, [x1, #0x00]        /* reg[0] */
    ldr x20, [x1, #0x08]        /* reg[1] */
    ldr x21, [x1, #0x10]        /* reg[2] */
    ldr x22, [x1, #0x18]        /* reg[3] */
    ldr x23, [x1, #0x20]        /* reg[4] */
    ldr x24, [x1, #0x28]        /* reg[5] */
    ldr x25, [x1, #0x30]        /* reg[6] */
    ldr x26, [x1, #0x38]        /* reg[7] */
    ldr x27, [x1, #0x40]        /* reg[8] */
    ldr x28, [x1, #0x48]        /* reg[9] */
    
    /* Restore special registers */
    ldr x29, [x1, #0x78]        /* restore x29 (frame pointer) from reg[15] */
    ldr x9, [x1, #0x70]         /* load sp from reg[14] */
    mov sp, x9
    
    /* Jump to target continuation */
    ldr x9, [x1, #0x68]         /* load continuation (lr) from reg[13] */
    br x9

FUNC_SIZE(LABEL(kcoro), .-LABEL(kcoro))
FUNC_SIZE(LABEL(kcoro_switch), .-LABEL(kcoro_switch))
#endif /* KC_VM_SWITCH */

GLOBAL(kcoro_save_fpucw_mxcsr)
FUNC_TYPE(LABEL(kcoro_save_fpucw_mxcsr))

LABEL(kcoro_save_fpucw_mxcsr):
    /* ARM64 doesn't have FPU control words.
     * This is intentionally a no-op to avoid touching FP/SIMD in kernel-like
     * environments or when not required in userland. */
    ret

FUNC_SIZE(LABEL(kcoro_save_fpucw_mxcsr), .-LABEL(kcoro_save_fpucw_mxcsr))

GLOBAL(kcoro_funcp_protector_asm)
FUNC_TYPE(LABEL(kcoro_funcp_protector_asm))

LABEL(kcoro_funcp_protector_asm):
    /* Ensure LR is preserved while we call into C helpers */
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    
    bl LABEL(kcoro_funcp_protector)
    bl LABEL(abort)
    
    /* Should never reach here */
    ldp x29, x30, [sp], #16
    ret

FUNC_SIZE(LABEL(kcoro_funcp_protector_asm), .-LABEL(kcoro_funcp_protector_asm))

#ifdef __APPLE__
#undef FUNC_TYPE
#undef FUNC_SIZE
#undef GLOBAL
#undef LABEL
#endif
