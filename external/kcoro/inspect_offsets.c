#include <stdio.h>
#include "kcoro_core.h"

int main(void) {
    printf("sizeof(kcoro_t)=%zu\n", sizeof(kcoro_t));
    printf("offsetof(state)=%zu\n", offsetof(kcoro_t, state));
    printf("offsetof(fn)=%zu\n", offsetof(kcoro_t, fn));
    printf("offsetof(arg)=%zu\n", offsetof(kcoro_t, arg));
    printf("offsetof(id)=%zu\n", offsetof(kcoro_t, id));
    printf("offsetof(main_co)=%zu\n", offsetof(kcoro_t, main_co));
    printf("offsetof(scheduler)=%zu\n", offsetof(kcoro_t, scheduler));
    printf("offsetof(stack_ptr)=%zu\n", offsetof(kcoro_t, stack_ptr));
    printf("offsetof(stack_size)=%zu\n", offsetof(kcoro_t, stack_size));
    printf("offsetof(next)=%zu\n", offsetof(kcoro_t, next));
    return 0;
}
