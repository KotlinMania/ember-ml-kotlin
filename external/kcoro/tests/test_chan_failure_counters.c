// SPDX-License-Identifier: BSD-3-Clause
// Test channel failure counters for kcoro library
// This test validates that channel failure counters (EAGAIN, EPIPE) are
// reflected in kc_chan_snapshot after inducing those conditions.
#include <stdio.h>
#include <assert.h>
#include <string.h>
#include <unistd.h>
#include "../include/kcoro.h"
#include "../include/kcoro_core.h"
#include "../include/kcoro_port.h" /* KC_EAGAIN / KC_EPIPE */

/* This test validates that channel failure counters (EAGAIN, EPIPE) are
 * reflected in kc_chan_snapshot after inducing those conditions. */
int main(void) {
    printf("[test] failure_counters start\n");
    kcoro_create_main();

    /* Create a buffered channel with capacity 1 for testing */
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make(&ch, KC_BUFFERED, sizeof(int), 1);
    assert(rc == 0 && ch);

    /* Take initial snapshot of channel counters */
    struct kc_chan_snapshot snap0; memset(&snap0, 0, sizeof(snap0));
    rc = kc_chan_snapshot(ch, &snap0); assert(rc == 0);

    /* Fill channel (capacity 1) to force an EAGAIN on next send */
    /* Send first value to fill the channel */
    int v = 42; rc = kc_chan_send(ch, &v, 0); assert(rc == 0);
    /* Send second value which should fail with EAGAIN since channel is full */
    v = 43; rc = kc_chan_send(ch, &v, 0); assert(rc == KC_EAGAIN);

    /* Take snapshot after EAGAIN condition */
    struct kc_chan_snapshot snap1; memset(&snap1, 0, sizeof(snap1));
    rc = kc_chan_snapshot(ch, &snap1); assert(rc == 0);
    /* Verify that send_eagain counter was incremented */
    assert(snap1.send_eagain >= snap0.send_eagain + 1);

    /* Close channel to force EPIPE on subsequent operations */
    /* Close the channel to test EPIPE condition */
    kc_chan_close(ch);
    /* Send to closed channel should return EPIPE */
    v = 99; rc = kc_chan_send(ch, &v, 0); assert(rc == KC_EPIPE);
    /* Receive from closed channel */
    int out = 0; rc = kc_chan_recv(ch, &out, 0); /* may be 0 if buffered element still there or KC_EPIPE */
    (void)out; /* not asserting value; focus on counters */

    /* Take final snapshot after EPIPE conditions */
    struct kc_chan_snapshot snap2; memset(&snap2, 0, sizeof(snap2));
    rc = kc_chan_snapshot(ch, &snap2); assert(rc == 0);
    /* Verify that send_epipe counter was incremented */
    assert(snap2.send_epipe >= snap1.send_epipe + 1);
    /* recv_epipe increments only when attempting to recv and channel closed & empty */
    if (kc_chan_len(ch) == 0) {
        assert(snap2.recv_epipe >= snap1.recv_epipe);
    }

    kc_chan_destroy(ch);
    printf("[test] failure_counters ok\n");
    return 0;
}
