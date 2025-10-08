// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <assert.h>
#include <time.h>
#include "../include/kcoro.h"
#include "../include/kcoro_port.h"

static void msleep(int ms) {
    struct timespec ts; ts.tv_sec = ms/1000; ts.tv_nsec = (ms%1000)*1000000L; nanosleep(&ts, NULL);
}

int main(void)
{
    printf("[test] cancel_recv: start\n");
    kc_cancel_t *tok = NULL; int irc = kc_cancel_init(&tok); printf("[test] cancel_init rc=%d\n", irc); assert(irc == 0);
    kc_chan_t *ch = NULL; int mrc = kc_chan_make(&ch, KC_BUFFERED, sizeof(int), 1); printf("[test] chan_make rc=%d\n", mrc); assert(mrc == 0);

    // Start a timer and cancel shortly after
    msleep(100);
    printf("[test] trigger cancel\n");
    kc_cancel_trigger(tok);

    int out = 0;
    int rc = kc_chan_recv_c(ch, &out, -1, tok);
    printf("[test] recv_c rc=%d (expect ECANCELED=%d)\n", rc, KC_ECANCELED);
    assert(rc == KC_ECANCELED);

    kc_cancel_destroy(tok);
    kc_chan_destroy(ch);
    printf("[test] cancel_recv: OK\n");
    return 0;
}
