/*
 * ARM64/aarch64 context switching for kcoro_cpp
 */
.text
.align 4
.global _kcoro_switch

_kcoro_switch:
    /* x0 = &from->ctx, x1 = &to->ctx (may be NULL for from) */
    cbz x0, 1f
    str x19, [x0, #0x00]
    str x20, [x0, #0x08]
    str x21, [x0, #0x10]
    str x22, [x0, #0x18]
    str x23, [x0, #0x20]
    str x24, [x0, #0x28]
    str x25, [x0, #0x30]
    str x26, [x0, #0x38]
    str x27, [x0, #0x40]
    str x28, [x0, #0x48]
    str x30, [x0, #0x68]
    mov x9, sp
    str x9, [x0, #0x70]
    str x29, [x0, #0x78]
1:
    ldr x19, [x1, #0x00]
    ldr x20, [x1, #0x08]
    ldr x21, [x1, #0x10]
    ldr x22, [x1, #0x18]
    ldr x23, [x1, #0x20]
    ldr x24, [x1, #0x28]
    ldr x25, [x1, #0x30]
    ldr x26, [x1, #0x38]
    ldr x27, [x1, #0x40]
    ldr x28, [x1, #0x48]
    ldr x29, [x1, #0x78]
    ldr x9, [x1, #0x70]
    mov sp, x9
    ldr x9, [x1, #0x68]
    br x9
