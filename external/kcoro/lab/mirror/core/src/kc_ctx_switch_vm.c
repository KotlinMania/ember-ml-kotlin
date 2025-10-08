// SPDX-License-Identifier: BSD-3-Clause
#define _GNU_SOURCE 1
#include <stdint.h>
#include "kcoro_core.h"

#if defined(__aarch64__)
#define KC_REG_LR 13
#else
#define KC_REG_LR 10 /* placeholder for non-arm builds */
#endif

extern void kc_vm_capture(uint64_t *regs, void *retaddr);
extern void kc_vm_apply(uint64_t *regs, void (*fn)(void));

void* kcoro_switch(kcoro_t* from_co, kcoro_t* to_co)
{
    if (from_co) {
        void *retaddr;
#if defined(__aarch64__)
        __asm__("mov %0, lr" : "=r"(retaddr));
#else
        retaddr = __builtin_return_address(0);
#endif
        kc_vm_capture((uint64_t*)from_co->reg, retaddr);
    }
    if (!to_co) {
        return NULL;
    }
    uint64_t *regs = (uint64_t*)to_co->reg;
    void (*cont)(void) = (void(*)(void))(uintptr_t)regs[KC_REG_LR];
    kc_vm_apply(regs, cont);
    return NULL;
}
