// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include "../include/kcoro.h"
#include "../include/kcoro_port.h"
#include "../include/kcoro_core.h"

int main(void)
{
    printf("[test] chan_basic: start\n");
    kcoro_create_main();
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make(&ch, KC_BUFFERED, sizeof(int), 2);
    printf("[test] make buffered(2) rc=%d\n", rc);
    assert(rc == 0 && ch);

    int v = 1; rc = kc_chan_send(ch, &v, 0); printf("[test] send1 rc=%d\n", rc); assert(rc == 0);
    v = 2; rc = kc_chan_send(ch, &v, 0); printf("[test] send2 rc=%d\n", rc); assert(rc == 0);
    v = 3; rc = kc_chan_send(ch, &v, 0); printf("[test] send_full rc=%d (expect EAGAIN=%d)\n", rc, KC_EAGAIN); assert(rc == KC_EAGAIN);

    int out = 0; rc = kc_chan_recv(ch, &out, 0); printf("[test] recv1 rc=%d out=%d\n", rc, out); assert(rc == 0 && out == 1);
    rc = kc_chan_recv(ch, &out, 0); printf("[test] recv2 rc=%d out=%d\n", rc, out); assert(rc == 0 && out == 2);

    kc_chan_close(ch); printf("[test] close\n");
    rc = kc_chan_recv(ch, &out, 0); printf("[test] recv_closed_empty rc=%d (expect EPIPE=%d)\n", rc, KC_EPIPE); assert(rc == KC_EPIPE);

    kc_chan_destroy(ch); printf("[test] destroy\n");
    printf("[test] chan_basic: OK\n");
    return 0;
}
