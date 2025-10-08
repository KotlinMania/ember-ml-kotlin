// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <assert.h>
#include <time.h>
#include "../include/kcoro.h"
#include "../include/kcoro_port.h"

static void msleep(int ms) { struct timespec ts; ts.tv_sec = ms/1000; ts.tv_nsec = (ms%1000)*1000000L; nanosleep(&ts, NULL); }

int main(void)
{
    printf("[test] cancel_ctx: start\n");
    kc_cancel_t *parent = NULL; int prc = kc_cancel_init(&parent); printf("[test] parent_init rc=%d\n", prc); assert(prc == 0);
    kc_cancel_ctx_t ctx = {0}; int crc = kc_cancel_ctx_init(&ctx, parent); printf("[test] ctx_init rc=%d\n", crc); assert(crc == 0);

    kc_chan_t *ch = NULL; int mrc = kc_chan_make(&ch, KC_BUFFERED, sizeof(int), 1); printf("[test] chan_make rc=%d\n", mrc); assert(mrc == 0);

    // Trigger parent cancel and ensure child token observes it
    msleep(50);
    printf("[test] trigger parent\n");
    kc_cancel_trigger(parent);

    int out = 0; int rc = kc_chan_recv_c(ch, &out, -1, ctx.token);
    printf("[test] recv_c (child token) rc=%d (expect ECANCELED=%d)\n", rc, KC_ECANCELED);
    assert(rc == KC_ECANCELED);

    kc_chan_destroy(ch);
    kc_cancel_ctx_destroy(&ctx);
    kc_cancel_destroy(parent);
    printf("[test] cancel_ctx: OK\n");
    return 0;
}

