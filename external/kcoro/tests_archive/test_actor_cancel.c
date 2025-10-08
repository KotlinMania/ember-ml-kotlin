// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <assert.h>
#include <time.h>
#include "../include/kcoro.h"
#include "../include/kcoro_port.h"

static void msleep(int ms) {
    struct timespec ts; ts.tv_sec = ms/1000; ts.tv_nsec = (ms%1000)*1000000L; nanosleep(&ts, NULL);
}

static int proc(const void* msg, void* user) {
    (void)msg; (void)user; return 0;
}

int main(void)
{
    printf("[test] actor_cancel: start\n");
    kc_cancel_t *tok = NULL; int irc = kc_cancel_init(&tok); printf("[test] cancel_init rc=%d\n", irc); assert(irc == 0);
    kc_chan_t *ch = NULL; int mrc = kc_chan_make(&ch, KC_RENDEZVOUS, sizeof(int), 0); printf("[test] chan_make rendezvous rc=%d\n", mrc); assert(mrc == 0);

    kc_actor_ctx_ex_t ex = {0};
    ex.base.chan = ch; ex.base.msg_size = sizeof(int); ex.base.timeout_ms = -1; ex.base.process = proc; ex.base.user = NULL;
    ex.cancel = tok;
    kc_actor_t a = kc_actor_start_ex(&ex);
    printf("[test] actor_start_ex handle=%p\n", (void*)a);
    assert(a != NULL);

    msleep(100);
    printf("[test] cancel actor\n");
    kc_actor_cancel(a);

    kc_chan_destroy(ch);
    kc_cancel_destroy(tok);
    printf("[test] actor_cancel: OK\n");
    return 0;
}
