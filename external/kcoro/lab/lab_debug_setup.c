/* Lab Experiment 4: Debug Context Setup
 *
 * This experiment focuses on getting the basic context setup right
 * without complex stack save/restore initially.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <assert.h>

/* Simple context for debugging */
typedef struct {
    void* reg[32];              /* Register save area */
    const char* name;
    void (*fp)(void);           /* Function pointer */
} debug_ctx_t;

extern void* kcoro(debug_ctx_t* from_co, debug_ctx_t* to_co);

void kcoro_funcp_protector(void)
{
    printf("kcoro_funcp_protector called - task finished normally\n");
    exit(0);  /* Normal exit when task completes */
}

static debug_ctx_t main_ctx;
static debug_ctx_t task_ctx;

/* Simple stack for task */
static char task_stack[8192] __attribute__((aligned(16)));

static void simple_task(void);

int main(void)
{
    printf("=== Lab Experiment 4: Debug Context Setup ===\n");
    printf("Debugging context initialization and basic switch\n\n");
    
    /* Initialize main context */
    memset(&main_ctx, 0, sizeof(main_ctx));
    main_ctx.name = "main";
    
    /* Initialize task context with careful stack setup */
    memset(&task_ctx, 0, sizeof(task_ctx));
    task_ctx.name = "task";
    task_ctx.fp = simple_task;
    
    /* Set up stack carefully - following ARM64 ABI */
    uintptr_t stack_top = (uintptr_t)(task_stack + sizeof(task_stack));
    
    /* Align to 16 bytes (ARM64 requirement) */
    stack_top = stack_top & ~0xFUL;
    
    /* Leave space for function protector return */
    stack_top -= 16;
    
    /* Set up context registers */
    task_ctx.reg[14] = (void*)stack_top;           /* SP */
    task_ctx.reg[15] = (void*)stack_top;           /* FP */
    task_ctx.reg[13] = (void*)simple_task;         /* LR (entry point) */
    
    printf("Context setup:\n");
    printf("  main_ctx: %p\n", &main_ctx);
    printf("  task_ctx: %p\n", &task_ctx);
    printf("  stack: %p - %p (%zu bytes)\n", task_stack, task_stack + sizeof(task_stack), sizeof(task_stack));
    printf("  stack_top: 0x%lx\n", stack_top);
    printf("  SP: %p, FP: %p, LR: %p\n", 
           task_ctx.reg[14], task_ctx.reg[15], task_ctx.reg[13]);
    
    printf("\nMAIN: Switching to task...\n");
    
    /* Switch to task */
    void* result = kcoro(&main_ctx, &task_ctx);
    
    printf("MAIN: Returned from task, result=%p\n", result);
    printf("MAIN: Test completed successfully!\n");
    
    return 0;
}

static void simple_task(void)
{
    printf("TASK: Started execution successfully!\n");
    printf("TASK: This proves basic context switching works\n");
    
    /* Test that we can use the stack */
    int local_var = 42;
    printf("TASK: Local variable test: %d\n", local_var);
    
    printf("TASK: Returning to main...\n");
    
    /* Return to main */
    kcoro(&task_ctx, &main_ctx);
    
    /* Should not reach here */
    printf("TASK: ERROR - Should not reach here!\n");
    exit(1);
}
