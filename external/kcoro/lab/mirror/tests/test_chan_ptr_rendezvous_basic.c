// SPDX-License-Identifier: BSD-3-Clause
// Test rendezvous channel operations with pointer data for kcoro library
// This test verifies basic rendezvous channel functionality with pointer-based data
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include "../include/kcoro.h"
#include "../include/kcoro_core.h"

/* Producer function that sends 10 "rv" strings through the channel */
static void producer(void *arg)
{
    kc_chan_t *ch = (kc_chan_t*)arg;
    const char *m = "rv";
    for (int i=0;i<10;i++) {
        int rc = kc_chan_send_ptr(ch, (void*)m, strlen(m), -1);
        printf("producer send #%d rc=%d\n", i, rc);
    }
}

/* Consumer function that receives 10 "rv" strings from the channel */
static void consumer(void *arg)
{
    kc_chan_t *ch = (kc_chan_t*)arg;
    for (int i=0;i<10;i++) {
        printf("consumer attempt #%d\n", i);
        void *p=NULL; size_t l=0;
        int rc = kc_chan_recv_ptr(ch, &p, &l, -1);
        printf("consumer recv #%d rc=%d len=%zu\n", i, rc, l);
        assert(l == strlen("rv"));
    }
}

int main(void)
{
    kcoro_create_main();
    /* Create a rendezvous channel with zero capacity */
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make_ptr(&ch, KC_RENDEZVOUS, 0);
    assert(rc == 0);
    kcoro_t *pco = kcoro_create(producer, ch, 64*1024); assert(pco);
    kcoro_t *cco = kcoro_create(consumer, ch, 64*1024); assert(cco);

    for (int i = 0; i < 10; ++i) {
        kcoro_resume(pco);
        kcoro_resume(cco);
    }
    kcoro_resume(pco);
    kcoro_resume(cco);

    struct kc_chan_snapshot snap={0};
    rc = kc_chan_snapshot(ch, &snap);
    assert(rc==0);
    printf("[ptr rv] snapshot sends=%lu recvs=%lu bytes=%lu\n",
           snap.total_sends, snap.total_recvs, snap.total_bytes_sent);
    /* Validate that all operations completed successfully */
    assert(snap.total_sends >= 10 && snap.total_recvs >= 10);
    printf("[ptr rv] ok sends=%lu recvs=%lu bytes=%lu\n",
           snap.total_sends, snap.total_recvs, snap.total_bytes_sent);

    /* Graceful shutdown */
    kc_chan_close(ch);
    kc_chan_destroy(ch);
    kcoro_destroy(pco);
    kcoro_destroy(cco);
    return 0;
}
