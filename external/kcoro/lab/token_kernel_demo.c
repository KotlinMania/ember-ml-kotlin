// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <string.h>

#include "kcoro_core.h"
#include "kcoro_token_kernel.h"

typedef struct demo_ctx {
    kc_ticket ticket;
} demo_ctx;

static void demo_coroutine(void *arg)
{
    demo_ctx *ctx = (demo_ctx*)arg;
    const char *message = "payload-from-coroutine";
    kc_ticket ticket = kc_token_kernel_publish_send(NULL,
                                                    (void*)message,
                                                    strlen(message) + 1,
                                                    NULL);
    ctx->ticket = ticket;
    printf("[coroutine] published ticket id=%llu, parking...\n",
           (unsigned long long)ticket.id);
    fflush(stdout);
    kcoro_park();

    kc_payload payload = {0};
    int rc = kc_token_kernel_consume_payload(&payload);
    printf("[coroutine] resumed rc=%d payload_ptr=%p len=%zu status=%d\n",
           rc, payload.ptr, payload.len, payload.status);
    fflush(stdout);
}

int main(void)
{
    printf("[main] initializing token kernel...\n");
    kc_token_kernel_global_init();

    kcoro_t *main_co = kcoro_create_main();
    kcoro_set_thread_main(main_co);

    demo_ctx ctx = {0};
    kcoro_t *co = kcoro_create(demo_coroutine, &ctx, 0);
    if (!co) {
        fprintf(stderr, "failed to create coroutine\n");
        return 1;
    }

   printf("[main] first resume\n");
    fflush(stdout);
    kcoro_resume(co);
    printf("[main] returned from first resume? (should not happen)\n");

    printf("[main] coroutine parked with ticket id=%llu\n",
           (unsigned long long)ctx.ticket.id);
    fflush(stdout);

    kc_payload payload = {
        .ptr = (void*)"OK",
        .len = 2,
        .status = 0,
    };
   kc_token_kernel_callback(ctx.ticket, payload);

    printf("[main] resuming coroutine after callback\n");
    fflush(stdout);
    kcoro_resume(co);
    printf("[main] returned from resume\n");
    fflush(stdout);

    kcoro_destroy(co);
    kcoro_destroy(main_co);
    kc_token_kernel_global_shutdown();
    return 0;
}
