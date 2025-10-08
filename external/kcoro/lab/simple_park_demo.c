#include <stdio.h>
#include "kcoro_core.h"

static void simple_co(void *arg)
{
    printf("[co] before park\n");
    kcoro_park();
    printf("[co] after park\n");
}

int main(void)
{
    kcoro_t *main_co = kcoro_create_main();
    kcoro_set_thread_main(main_co);

    kcoro_t *co = kcoro_create(simple_co, NULL, 0);
    printf("[main] resuming\n");
    kcoro_resume(co);
    printf("[main] first resume return\n");
    kcoro_resume(co);
    printf("[main] second resume return\n");
    kcoro_destroy(co);
    kcoro_destroy(main_co);
    return 0;
}
