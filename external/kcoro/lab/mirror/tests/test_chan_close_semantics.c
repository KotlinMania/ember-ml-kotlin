// SPDX-License-Identifier: BSD-3-Clause
// Test channel close semantics for kcoro library
// This test verifies proper behavior when channels are closed
#include <stdio.h>
#include <assert.h>
#include <string.h>
#include "../include/kcoro.h"
#include "../include/kcoro_core.h"
#include "../include/kcoro_port.h" /* KC_EPIPE */
#include "../include/kcoro_sched.h"

static void rv_sender_then_close(void *arg) {
    kc_chan_t *ch = (kc_chan_t*)arg;
    char *p = (char*)"closing";
    int rc = kc_chan_send_zref(ch, p, strlen(p)+1, -1);
    assert(rc == 0);
    kc_chan_close(ch);
}

int main(void) {
    printf("[test] close_semantics start\n");
    kcoro_create_main();

    /* Normal buffered channel close */
    /* Test that sending to closed buffered channel returns KC_EPIPE */
    /* Test that receiving from closed channel works for buffered data */
    /* Test that receiving from closed channel returns KC_EPIPE when empty */
    kc_chan_t *buf = NULL; int rc = kc_chan_make(&buf, KC_BUFFERED, sizeof(int), 1); assert(rc==0);
    int v=7; rc = kc_chan_send(buf,&v,0); assert(rc==0);
    kc_chan_close(buf);
    rc = kc_chan_send(buf,&v,0); assert(rc==KC_EPIPE);
    rc = kc_chan_recv(buf,&v,0); assert(rc==0 && v==7);
    rc = kc_chan_recv(buf,&v,0); assert(rc==KC_EPIPE);
    kc_chan_destroy(buf);

    /* Zero-copy rendezvous channel close (cooperative park/unpark) */
    /* Test zero-copy channel behavior when sender closes channel */
    /* Create a coroutine that sends data then closes channel */
    /* Verify that the receiver can get the data and then detect channel closure */
    kc_chan_t *rv = NULL; rc = kc_chan_make_ptr(&rv, KC_RENDEZVOUS, 0); assert(rc==0);
    rc = kc_chan_enable_zero_copy(rv); assert(rc==0);
    kcoro_t *co = kcoro_create(rv_sender_then_close, rv, 64*1024); assert(co);
    /* Run producer until it parks in send_zref */
    kcoro_resume(co);
    void *ptr=NULL; size_t len=0; rc = kc_chan_recv_zref(rv,&ptr,&len,-1); assert(rc==0);
    assert(strcmp((const char*)ptr,"closing")==0);
    /* Resume producer to let it close channel */
    kcoro_resume(co);
    rc = kc_chan_recv_zref(rv,&ptr,&len,0);
    assert(rc==KC_EPIPE);
    struct kc_chan_zstats zs = {0};
    rc = kc_chan_get_zstats(rv,&zs);
    assert(rc == 0);
    assert(zs.zref_sent == 1);
    assert(zs.zref_received == 1);
    kc_chan_destroy(rv);

    printf("[test] close_semantics ok\n");
    return 0;
}
