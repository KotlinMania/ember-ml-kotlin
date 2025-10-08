// SPDX-License-Identifier: BSD-3-Clause
// Test rendezvous channel operations with pointer data for kcoro library
// This test verifies basic rendezvous channel functionality with pointer-based data
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "../include/kcoro.h"
#include "../include/kcoro_sched.h"
#include "../include/kcoro_core.h"
#include "../core/src/kc_chan_internal.h"
#include <unistd.h>

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
    /* Create a rendezvous channel with zero capacity */
    kc_chan_t *ch = NULL;
    int rc = kc_chan_make_ptr(&ch, KC_RENDEZVOUS, 0);
    assert(rc == 0);
    kcoro_t *pco=NULL, *cco=NULL;
    kc_sched_t *s = kc_sched_default();
    assert(s);
    /* Spawn producer and consumer coroutines */
    rc = kc_spawn_co(s, producer, ch, 0, &pco); assert(rc == 0);
    rc = kc_spawn_co(s, consumer, ch, 0, &cco); assert(rc == 0);
    /* Wait up to ~2s for producers/consumers to make progress */
    struct kc_chan_snapshot snap={0};
    int tries=0;
    for (;;) {
        usleep(20000);
        rc = kc_chan_snapshot(ch, &snap); assert(rc==0);
        printf("[debug] tries=%d sends=%lu recvs=%lu\n", tries, snap.total_sends, snap.total_recvs);
        /* Check if all sends and receives completed */
        if (snap.total_sends >= 10 && snap.total_recvs >= 10) break;
        if (++tries > 100) break; /* ~2s */
    }
    if (snap.total_sends < 10 || snap.total_recvs < 10) {
        struct kc_chan *internal = (struct kc_chan*)ch;
        fprintf(stderr,
                "[ptr rv][debug] sends=%lu recvs=%lu ready=%d has_value=%d send_wait=%p recv_wait=%p\n",
                snap.total_sends, snap.total_recvs,
                internal ? internal->zref_ready : -1,
                internal ? internal->has_value : -1,
                internal ? (void*)internal->wq_send_head : NULL,
                internal ? (void*)internal->wq_recv_head : NULL);
    }
    printf("[ptr rv] snapshot sends=%lu recvs=%lu bytes=%lu\n",
           snap.total_sends, snap.total_recvs, snap.total_bytes_sent);
    /* Validate that all operations completed successfully */
    assert(snap.total_sends >= 10 && snap.total_recvs >= 10);
    printf("[ptr rv] ok sends=%lu recvs=%lu bytes=%lu\n",
           snap.total_sends, snap.total_recvs, snap.total_bytes_sent);
    /* Graceful shutdown: close channel and best-effort drain scheduler */
    kc_chan_close(ch);
    if (s) (void)kc_sched_drain(s, 500 /* ms */);
    return 0;
}
