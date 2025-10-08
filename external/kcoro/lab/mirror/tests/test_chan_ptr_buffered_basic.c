// SPDX-License-Identifier: BSD-3-Clause
// Test buffered channel operations with pointer data for kcoro library
// This test verifies basic buffered channel functionality with pointer-based data
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "../include/kcoro.h"
#include "../include/kcoro_sched.h"
#include "../include/kcoro_core.h"
#include <unistd.h>

/* Runner function that performs channel send and receive operations */
static void runner(void *arg)
{
    kc_chan_t *ch = (kc_chan_t*)arg;
    const char *m1 = "hello";
    const char *m2 = "world";
    /* Send two strings through the channel */
    (void)kc_chan_send_ptr(ch, (void*)m1, strlen(m1), 0);
    (void)kc_chan_send_ptr(ch, (void*)m2, strlen(m2), 0);
    /* Receive the strings back from the channel */
    void *p=NULL; size_t len=0;
    (void)kc_chan_recv_ptr(ch, &p, &len, 0);
    (void)kc_chan_recv_ptr(ch, &p, &len, 0);
}

int main(void)
{
    /* Create a buffered channel with capacity 64 */
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make_ptr(&ch, KC_BUFFERED, 64);
    assert(rc == 0 && ch);
    /* Get default scheduler and spawn coroutine */
    kc_sched_t *s = kc_sched_default(); assert(s);
    kcoro_t *co=NULL; rc = kc_spawn_co(s, runner, ch, 0, &co); assert(rc==0);
    /* Allow worker thread to run */
    usleep(100000);

    /* Take snapshot of channel statistics */
    struct kc_chan_snapshot snap = {0};
    rc = kc_chan_snapshot(ch, &snap);
    assert(rc == 0);
    printf("[ptr buffered] snapshot sends=%lu recvs=%lu bytes_sent=%lu bytes_recv=%lu\n",
           snap.total_sends, snap.total_recvs, snap.total_bytes_sent, snap.total_bytes_recv);
    /* Validate snapshot data */
    assert(snap.total_sends >= snap.total_recvs);
    assert(snap.total_bytes_sent > 0);
    assert(snap.total_bytes_recv == snap.total_bytes_sent);

    printf("[ptr buffered] ok sends=%lu recvs=%lu bytes=%lu\n",
           snap.total_sends, snap.total_recvs, snap.total_bytes_sent);
    if (s) (void)kc_sched_drain(s, 200);
    kc_chan_destroy(ch);
    return 0;
}
