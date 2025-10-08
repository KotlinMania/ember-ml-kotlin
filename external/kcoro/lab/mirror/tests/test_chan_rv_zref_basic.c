// SPDX-License-Identifier: BSD-3-Clause
// Test rendezvous channel zero-copy operations for kcoro library
// This test verifies basic rendezvous channel functionality with zero-copy data transfer
#include <stdio.h>
#include <assert.h>
#include <string.h>
#include "../include/kcoro.h"
#include "../include/kcoro_core.h"
#include "../include/kcoro_port.h"

/* Producer function that sends zero-copy data through the channel */
static void producer(void *arg) {
    kc_chan_t *ch = (kc_chan_t*)arg;
    /* Create payload string - using static storage for identity check */
    char *payload = (char*)"hello-zero-copy";
    /* Send zero-copy data through channel */
    int rc = kc_chan_send_zref(ch, payload, strlen(payload)+1, -1);
    assert(rc == 0);
}

int main(void) {
    printf("[test] rv_zref_basic start\n");
    /* Initialize main coroutine context */
    kcoro_create_main();
    kc_chan_t *ch = NULL;
    /* Create a rendezvous pointer channel with zero capacity */
    int rc = kc_chan_make_ptr(&ch, KC_RENDEZVOUS, 0);
    assert(rc == 0 && ch);
    /* Enable zero-copy mode for the channel */
    rc = kc_chan_enable_zero_copy(ch); assert(rc == 0);

    /* Create producer coroutine */
    kcoro_t *co = kcoro_create(producer, ch, 64*1024); assert(co);

    /* Start producer so it can publish the pointer */
    /* Note: cooperative scheduling requires explicit resume */
    kcoro_resume(co);
    void *ptr = NULL; size_t len = 0;
    /* Receive zero-copy data from channel */
    rc = kc_chan_recv_zref(ch, &ptr, &len, -1); assert(rc == 0);
    /* Validate received data */
    assert(ptr && len == strlen("hello-zero-copy") + 1);
    assert(strcmp((const char*)ptr, "hello-zero-copy") == 0);

    /* Producer already ran to completion during first resume */

    /* Clean up channel */
    kc_chan_close(ch);
    kc_chan_destroy(ch);
    printf("[test] rv_zref_basic ok\n");
    return 0;
}
