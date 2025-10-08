// SPDX-License-Identifier: BSD-3-Clause
/*
 * Simple test to validate basic coroutine functionality 
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "kcoro_core.h"

void simple_coro_fn(void* arg) {
    int id = *(int*)arg;
    printf("[Coro %d] Starting\n", id);
    kcoro_yield();
    printf("[Coro %d] After first yield\n", id);
    kcoro_yield();  
    printf("[Coro %d] After second yield\n", id);
}

int main() {
    printf("🧪 Simple kcoro Test\n");
    printf("===================\n");
    
    /* Create main coroutine */
    kcoro_t* main_co = kcoro_create_main();
    if (!main_co) {
        fprintf(stderr, "❌ Failed to create main coroutine\n");
        return 1;
    }
    printf("[Main] Created main coroutine: %p\n", (void*)main_co);
    
    /* Create a simple coroutine */
    int coro_id = 42;
    kcoro_t* co = kcoro_create(simple_coro_fn, &coro_id, 8192);
    if (!co) {
        fprintf(stderr, "❌ Failed to create coroutine\n");
        kcoro_destroy(main_co);
        return 1;
    }
    printf("[Main] Created coroutine: %p\n", (void*)co);
    
    /* Set up yield target */
    co->main_co = main_co;
    
    /* Test execution */
    printf("[Main] Starting coroutine...\n");
    kcoro_resume(co);
    printf("[Main] First resume completed\n");
    
    printf("[Main] Resuming again...\n");
    kcoro_resume(co);
    printf("[Main] Second resume completed\n");
    
    printf("[Main] Final resume...\n");
    kcoro_resume(co);
    printf("[Main] Coroutine state: %d\n", co->state);
    
    /* Cleanup */
    kcoro_destroy(co);
    kcoro_destroy(main_co);
    
    printf("✅ Test completed\n");
    return 0;
}
