// SPDX-License-Identifier: BSD-3-Clause
#if defined(__aarch64__)
    .text
    .align 2
    .globl _kc_vm_capture
_kc_vm_capture:
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
    str x1,  [x0, #0x68]
    mov x9, sp
    str x9, [x0, #0x70]
    str x29, [x0, #0x78]
    ret

    .globl _kc_vm_apply
_kc_vm_apply:
    ldr x19, [x0, #0x00]
    ldr x20, [x0, #0x08]
    ldr x21, [x0, #0x10]
    ldr x22, [x0, #0x18]
    ldr x23, [x0, #0x20]
    ldr x24, [x0, #0x28]
    ldr x25, [x0, #0x30]
    ldr x26, [x0, #0x38]
    ldr x27, [x0, #0x40]
    ldr x28, [x0, #0x48]
    ldr x30, [x0, #0x68]
    ldr x9,  [x0, #0x70]
    mov sp, x9
    ldr x29, [x0, #0x78]
    br x30
#else
void kc_vm_capture(uint64_t *regs) {(void)regs;}
void kc_vm_apply(uint64_t *regs, void (*fn)(void)) {(void)regs; fn();}
#endif
