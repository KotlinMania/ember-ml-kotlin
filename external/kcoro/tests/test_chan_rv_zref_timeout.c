// SPDX-License-Identifier: BSD-3-Clause
// Test rendezvous channel zero-copy timeout behavior for kcoro library
// This test verifies that non-blocking receive on empty rendezvous channel returns KC_EAGAIN
#include <stdio.h>
#include <assert.h>
#include <string.h>
#include "../include/kcoro.h"
#include "../include/kcoro_core.h"
#include "../include/kcoro_port.h" /* KC_EAGAIN */

int main(void) {
    printf("[test] rv_zref_timeout start\n");
    /* Initialize main coroutine context */
    kcoro_create_main();
    kc_chan_t *ch = NULL;
    /* Create a rendezvous channel with zero capacity */
    int rc = kc_chan_make(&ch, KC_RENDEZVOUS, sizeof(int), 0); assert(rc == 0);
    /* Enable zero-copy mode for the channel */
    rc = kc_chan_enable_zero_copy(ch); assert(rc == 0);

    /* Attempt to receive zero-copy data with timeout=0 (non-blocking) */
    /* Since no sender has published data yet, this should return KC_EAGAIN */
    void *ptr = NULL; size_t len = 0;
    rc = kc_chan_recv_zref(ch, &ptr, &len, 0); /* non-blocking, no sender yet */
    assert(rc == KC_EAGAIN);

    /* Clean up */
    kc_chan_destroy(ch);
    printf("[test] rv_zref_timeout ok\n");
    return 0;
}
