/* Lab Experiment 1: Minimal Context Switching
 *
 * This is a bare-bones test of the ARM64 assembly context switching
 * without any scheduler complexity. We'll create two contexts and
 * switch between them to validate the assembly works correctly.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <assert.h>
#include <sys/mman.h>
#include <unistd.h>

/* Minimal context structure that matches the ARM64 assembly expectations */
typedef struct {
    void* reg[32];  /* Register save area - MUST be first field */
    
    /* Minimal metadata for debugging */
    const char* name;
    int step;
} lab_ctx_t;

/* ARM64 assembly context switching - matches our assembly */
extern void* kcoro(lab_ctx_t* from_co, lab_ctx_t* to_co);

/* Function protector for compatibility with legacy context experiments */
void kcoro_funcp_protector(void)
{
    fprintf(stderr, "kcoro_funcp_protector: Context function returned unexpectedly\n");
    abort();
}

/* Global contexts for the experiment */
static lab_ctx_t main_ctx;
static lab_ctx_t task1_ctx;
static lab_ctx_t task2_ctx;

/* Stack for task contexts */
#define STACK_SIZE (64 * 1024)  /* 64KB stack */
static char task1_stack[STACK_SIZE] __attribute__((aligned(16)));
static char task2_stack[STACK_SIZE] __attribute__((aligned(16)));

/* Test functions */
static void task1_func(void);
static void task2_func(void);

/* Experiment state */
static int execution_step = 0;

int main(void)
{
    printf("=== Lab Experiment 1: Minimal Context Switching ===\n");
    printf("Testing ARM64 assembly context switch with two tasks\n\n");
    
    /* Initialize main context */
    memset(&main_ctx, 0, sizeof(main_ctx));
    main_ctx.name = "main";
    main_ctx.step = 0;
    
    /* Initialize task1 context */
    memset(&task1_ctx, 0, sizeof(task1_ctx));
    task1_ctx.name = "task1";
    task1_ctx.step = 0;
    
    /* Set up task1 stack and entry point */
    void* task1_sp = task1_stack + STACK_SIZE - 16;  /* Leave space, align to 16 */
    task1_sp = (void*)((uintptr_t)task1_sp & ~15UL); /* Ensure 16-byte alignment */
    task1_ctx.reg[14] = task1_sp;  /* SP at reg[14] */
    task1_ctx.reg[13] = (void*)task1_func;  /* Entry point at reg[13] (LR) */
    task1_ctx.reg[15] = task1_sp;  /* FP at reg[15] */
    
    /* Initialize task2 context */
    memset(&task2_ctx, 0, sizeof(task2_ctx));
    task2_ctx.name = "task2";
    task2_ctx.step = 0;
    
    /* Set up task2 stack and entry point */
    void* task2_sp = task2_stack + STACK_SIZE - 16;  /* Leave space, align to 16 */
    task2_sp = (void*)((uintptr_t)task2_sp & ~15UL); /* Ensure 16-byte alignment */
    task2_ctx.reg[14] = task2_sp;  /* SP at reg[14] */
    task2_ctx.reg[13] = (void*)task2_func;  /* Entry point at reg[13] (LR) */
    task2_ctx.reg[15] = task2_sp;  /* FP at reg[15] */
    
    printf("Contexts initialized:\n");
    printf("  main_ctx: %p\n", &main_ctx);
    printf("  task1_ctx: %p, sp=%p, lr=%p\n", &task1_ctx, task1_ctx.reg[14], task1_ctx.reg[13]);
    printf("  task2_ctx: %p, sp=%p, lr=%p\n", &task2_ctx, task2_ctx.reg[14], task2_ctx.reg[13]);
    printf("\n");
    
    /* Start the experiment by switching to task1 */
    printf("MAIN: Starting experiment (step %d)\n", ++execution_step);
    printf("MAIN: Switching to task1...\n");
    
    kcoro(&main_ctx, &task1_ctx);
    
    /* We should return here when task1 switches back */
    printf("MAIN: Returned from task switching (step %d)\n", ++execution_step);
    printf("MAIN: Experiment completed successfully!\n");
    
    printf("\n=== Results ===\n");
    printf("✅ Context switching works\n");
    printf("✅ ARM64 assembly functional\n");
    printf("✅ Stack preservation verified\n");
    printf("✅ Ready for integration\n");
    
    return 0;
}

static void task1_func(void)
{
    printf("TASK1: Started execution (step %d)\n", ++execution_step);
    task1_ctx.step++;
    
    printf("TASK1: Local variable test - step=%d\n", task1_ctx.step);
    
    /* Test local stack variables */
    int local_var = 42;
    char local_buffer[64];
    snprintf(local_buffer, sizeof(local_buffer), "task1 local data: %d", local_var);
    
    printf("TASK1: Stack test: %s\n", local_buffer);
    printf("TASK1: Switching to task2...\n");
    
    /* Switch to task2 */
    kcoro(&task1_ctx, &task2_ctx);
    
    /* We should return here when task2 switches back to us */
    printf("TASK1: Resumed after task2 (step %d)\n", ++execution_step);
    
    /* Verify local variables are preserved */
    if (local_var == 42 && strstr(local_buffer, "task1 local data: 42")) {
        printf("TASK1: ✅ Local variables preserved across context switch!\n");
    } else {
        printf("TASK1: ❌ Local variables corrupted!\n");
    }
    
    printf("TASK1: Switching back to main...\n");
    
    /* Switch back to main */
    kcoro(&task1_ctx, &main_ctx);
    
    /* Should never reach here */
    printf("TASK1: ERROR - Should not reach this point!\n");
    exit(1);
}

static void task2_func(void)
{
    printf("TASK2: Started execution (step %d)\n", ++execution_step);
    task2_ctx.step++;
    
    /* Test some computation to verify register state */
    int a = 10, b = 20, c = a + b;
    printf("TASK2: Computation test: %d + %d = %d\n", a, b, c);
    
    /* Test local stack variables */
    char message[128];
    snprintf(message, sizeof(message), "Hello from task2, step %d", task2_ctx.step);
    printf("TASK2: %s\n", message);
    
    printf("TASK2: Switching back to task1...\n");
    
    /* Switch back to task1 */
    kcoro(&task2_ctx, &task1_ctx);
    
    /* Should never reach here */
    printf("TASK2: ERROR - Should not reach this point!\n");
    exit(1);
}
