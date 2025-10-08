// SPDX-License-Identifier: BSD-3-Clause
// token_vm_bench.c -- measure token VM resume throughput

#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <inttypes.h>

#include "token_vm.h"

static inline uint64_t cycles_now(void) {
#if defined(__aarch64__)
    uint64_t v;
    __asm__ volatile("mrs %0, cntvct_el0" : "=r"(v));
    return v;
#elif defined(__x86_64__)
    unsigned hi, lo;
    __asm__ volatile("rdtsc" : "=a"(lo), "=d"(hi));
    return ((uint64_t)hi << 32) | lo;
#else
    return 0;
#endif
}

static inline uint64_t cycles_freq_hz(void) {
#if defined(__aarch64__)
    uint64_t v;
    __asm__ volatile("mrs %0, cntfrq_el0" : "=r"(v));
    return v;
#else
    return 0;
#endif
}

static void bench_target(void) { }

int main(void)
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
        { KC_OP_END,      0,           0, (uint64_t)(void*)bench_target },
#elif defined(__x86_64__)
        { KC_OP_LOAD_IMM, KC_REG_R12, 0, 0xaaaa },
        { KC_OP_LOAD_IMM, KC_REG_R13, 0, 0xbbbb },
        { KC_OP_LOAD_IMM, KC_REG_R14, 0, 0xcccc },
        { KC_OP_LOAD_IMM, KC_REG_R15, 0, 0xdddd },
        { KC_OP_END,      0,           0, (uint64_t)(void*)bench_target },
#else
        { KC_OP_END,      0,           0, (uint64_t)(void*)bench_target },
#endif
    };

    const uint64_t iterations = 5ULL * 1000 * 1000;

    uint64_t start_cycles = cycles_now();
    struct timespec ts_start; clock_gettime(CLOCK_MONOTONIC_RAW, &ts_start);
    for (uint64_t i = 0; i < iterations; ++i) {
        void *fn = kc_vm_execute(program, regs);
        if (fn) kc_vm_apply(regs, fn);
    }
    struct timespec ts_end; clock_gettime(CLOCK_MONOTONIC_RAW, &ts_end);
    uint64_t end_cycles = cycles_now();

    double elapsed_sec = (ts_end.tv_sec - ts_start.tv_sec) +
                         (ts_end.tv_nsec - ts_start.tv_nsec) / 1e9;
    double iter_per_sec = iterations / elapsed_sec;
    uint64_t cycles_elapsed = end_cycles - start_cycles;
    double cycles_per_iter = cycles_elapsed > 0 ? (double)cycles_elapsed / iterations : 0.0;

    printf("iterations=%" PRIu64 " time=%.6fs iter/s=%.2f\n",
           iterations, elapsed_sec, iter_per_sec);
    if (cycles_elapsed > 0) {
        printf("cycles/iter=%.2f\n", cycles_per_iter);
    }
    uint64_t freq = cycles_freq_hz();
    if (freq > 0 && cycles_per_iter > 0.0) {
        double expected_iter_per_sec = freq / cycles_per_iter;
        printf("freq=%" PRIu64 " Hz (derived iter/s %.2f)\n", freq, expected_iter_per_sec);
    }
    return 0;
}
