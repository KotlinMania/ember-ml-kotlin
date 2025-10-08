// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <assert.h>
#include <string.h>
#include "../include/kcoro.h"
#include "../include/kcoro_core.h"
#include "../include/kcoro_port.h" /* for KC_EAGAIN / KC_EPIPE */

int main(void) {
    printf("[test] buffered_basic start\n");
    kcoro_create_main();
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make(&ch, KC_BUFFERED, sizeof(int), 2);
    assert(rc == 0 && ch);
    int v = 11; rc = kc_chan_send(ch, &v, 0); assert(rc == 0);
    v = 22; rc = kc_chan_send(ch, &v, 0); assert(rc == 0);
    v = 33; rc = kc_chan_send(ch, &v, 0); assert(rc == KC_EAGAIN);
    int out = 0; rc = kc_chan_recv(ch, &out, 0); assert(rc == 0 && out == 11);
    rc = kc_chan_recv(ch, &out, 0); assert(rc == 0 && out == 22);
    kc_chan_close(ch);
    rc = kc_chan_recv(ch, &out, 0); assert(rc == KC_EPIPE);
    kc_chan_destroy(ch);
    printf("[test] buffered_basic ok\n");
    return 0;
}
