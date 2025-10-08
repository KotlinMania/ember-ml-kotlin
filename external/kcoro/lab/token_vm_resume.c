// SPDX-License-Identifier: BSD-3-Clause
// token_vm_resume.c -- prototype interpreter-driven register restore
// This lab tool demonstrates how a tiny token stream can stage coroutine
// register state without emitting executable code.

#include <stdio.h>
#include <stdint.h>
#include <string.h>

#include "token_vm.h"

// Helper that prints the "live" machine registers.
static void dump_machine_registers(void)
{
#if defined(__aarch64__)
    uint64_t vals[KC_REG_MAX];
    __asm__ volatile(
        "mov %0, x19\n\t"
        "mov %1, x20\n\t"
        "mov %2, x21\n\t"
        "mov %3, x22\n\t"
        "mov %4, x23\n\t"
        "mov %5, x24\n\t"
        "mov %6, x25\n\t"
        "mov %7, x26\n\t"
        "mov %8, x27\n\t"
        "mov %9, x28\n\t"
        : "=r"(vals[0]), "=r"(vals[1]), "=r"(vals[2]), "=r"(vals[3]),
          "=r"(vals[4]), "=r"(vals[5]), "=r"(vals[6]), "=r"(vals[7]),
          "=r"(vals[8]), "=r"(vals[9]));
    printf("[cpu] x19=%#llx x20=%#llx x21=%#llx x22=%#llx\n",
           (unsigned long long)vals[0], (unsigned long long)vals[1],
           (unsigned long long)vals[2], (unsigned long long)vals[3]);
    printf("      x23=%#llx x24=%#llx x25=%#llx x26=%#llx\n",
           (unsigned long long)vals[4], (unsigned long long)vals[5],
           (unsigned long long)vals[6], (unsigned long long)vals[7]);
    printf("      x27=%#llx x28=%#llx\n",
           (unsigned long long)vals[8], (unsigned long long)vals[9]);
#elif defined(__x86_64__)
    uint64_t vals[KC_REG_MAX];
    __asm__ volatile(
        "mov %%r12, %0\n\t"
        "mov %%r13, %1\n\t"
        "mov %%r14, %2\n\t"
        "mov %%r15, %3\n\t"
        "mov %%rbx, %4\n\t"
        "mov %%rbp, %5\n\t"
        : "=r"(vals[0]), "=r"(vals[1]), "=r"(vals[2]), "=r"(vals[3]),
          "=r"(vals[4]), "=r"(vals[5]));
    printf("[cpu] r12=%#llx r13=%#llx r14=%#llx r15=%#llx\n",
           (unsigned long long)vals[0], (unsigned long long)vals[1],
           (unsigned long long)vals[2], (unsigned long long)vals[3]);
    printf("      rbx=%#llx rbp=%#llx\n",
           (unsigned long long)vals[4], (unsigned long long)vals[5]);
#else
    puts("[cpu] register dump not available on this arch");
#endif
}

// Interpreter ----------------------------------------------------------------
void *kc_vm_execute(const kc_token *tokens, uint64_t *regs)
{
    const kc_token *pc = tokens;
    for (;;) {
        switch (pc->op) {
        case KC_OP_LOAD_IMM:
            if (pc->dst < KC_REG_MAX) {
                regs[pc->dst] = pc->imm;
            }
            break;
        case KC_OP_LOAD_MEM:
            if (pc->dst < KC_REG_MAX && pc->imm) {
                regs[pc->dst] = *(const uint64_t *)(uintptr_t)pc->imm;
            }
            break;
        case KC_OP_END:
            return (void *)(uintptr_t)pc->imm;
        default:
            fprintf(stderr, "unknown opcode %u\n", pc->op);
            return NULL;
        }
        pc++;
    }
}

// Demo target that prints the logical state and a live register dump.
void demo_target(void)
{
    printf("[target] executing with live register state:\n");
    dump_machine_registers();
}

int kc_token_vm_run_demo(void)
{
    uint64_t regs[KC_REG_MAX];
    memset(regs, 0, sizeof(regs));
#if defined(__aarch64__)
    uint64_t cur_sp, cur_fp;
    __asm__ volatile("mov %0, sp" : "=r"(cur_sp));
    __asm__ volatile("mov %0, x29" : "=r"(cur_fp));
    regs[KC_REG_SP] = cur_sp;
    regs[KC_REG_FP] = cur_fp;
#elif defined(__x86_64__)
    uint64_t cur_sp, cur_bp;
    __asm__ volatile("mov %%rsp, %0" : "=r"(cur_sp));
    __asm__ volatile("mov %%rbp, %0" : "=r"(cur_bp));
    regs[KC_REG_SP] = cur_sp;
    regs[KC_REG_FP] = cur_bp;
#endif

    const kc_token program[] = {
#if defined(__aarch64__)
        { KC_OP_LOAD_IMM, KC_REG_X19, 0, 0x1111 },
        { KC_OP_LOAD_IMM, KC_REG_X20, 0, 0x2222 },
        { KC_OP_LOAD_IMM, KC_REG_X21, 0, 0x3333 },
        { KC_OP_LOAD_IMM, KC_REG_X22, 0, 0x4444 },
        { KC_OP_END,      0,            0, (uint64_t)(void*)demo_target },
#elif defined(__x86_64__)
        { KC_OP_LOAD_IMM, KC_REG_R12, 0, 0xaaaa },
        { KC_OP_LOAD_IMM, KC_REG_R13, 0, 0xbbbb },
        { KC_OP_LOAD_IMM, KC_REG_R14, 0, 0xcccc },
        { KC_OP_LOAD_IMM, KC_REG_R15, 0, 0xdddd },
        { KC_OP_END,      0,           0, (uint64_t)(void*)demo_target },
#else
        { KC_OP_END,      0,           0, (uint64_t)(void*)demo_target },
#endif
    };

    printf("[vm] staging coroutine context via token stream...\n");
    void *fn = kc_vm_execute(program, regs);
    if (fn) {
        kc_vm_apply(regs, fn);
    }

    return 0;
}
