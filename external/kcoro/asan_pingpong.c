#include "kcoro_sched.h"
#include "kcoro.h"
#include <stdio.h>
#include <stdlib.h>

typedef struct {
    kc_chan_t *forward;
    kc_chan_t *backward;
    int iterations;
} shared_t;

typedef struct {
    shared_t *shared;
    int role; /* 0 = ping, 1 = pong */
} worker_args_t;

static void ping_fn(void *arg) {
    worker_args_t *w = (worker_args_t *)arg;
    shared_t *shared = w->shared;
    int value = 0;
    for (int i = 0; i < shared->iterations; ++i) {
        value = i;
        int rc = kc_chan_send(shared->forward, &value, -1);
        if (rc != 0) {
            fprintf(stderr, "ping send failed rc=%d\n", rc);
            break;
        }
        rc = kc_chan_recv(shared->backward, &value, -1);
        if (rc != 0) {
            fprintf(stderr, "ping recv failed rc=%d\n", rc);
            break;
        }
    }
}

static void pong_fn(void *arg) {
    worker_args_t *w = (worker_args_t *)arg;
    shared_t *shared = w->shared;
    int value = 0;
    for (int i = 0; i < shared->iterations; ++i) {
        int rc = kc_chan_recv(shared->forward, &value, -1);
        if (rc != 0) {
            fprintf(stderr, "pong recv failed rc=%d\n", rc);
            break;
        }
        rc = kc_chan_send(shared->backward, &value, -1);
        if (rc != 0) {
            fprintf(stderr, "pong send failed rc=%d\n", rc);
            break;
        }
    }
}

int main(void) {
    kc_sched_opts_t opts = {0};
    opts.workers = 2;
    kc_sched_t *sched = kc_sched_init(&opts);
    if (!sched) {
        fprintf(stderr, "kc_sched_init failed\n");
        return 1;
    }

    kc_chan_t *forward = NULL;
    kc_chan_t *backward = NULL;
    int rc = kc_chan_make(&forward, KC_RENDEZVOUS, sizeof(int), 0);
    if (rc != 0 || !forward) {
        fprintf(stderr, "forward channel init failed rc=%d\n", rc);
        return 2;
    }
    rc = kc_chan_make(&backward, KC_RENDEZVOUS, sizeof(int), 0);
    if (rc != 0 || !backward) {
        fprintf(stderr, "backward channel init failed rc=%d\n", rc);
        return 3;
    }

    const int iterations = 200000;
    shared_t shared = { forward, backward, iterations };

    worker_args_t ping_args = { &shared, 0 };
    worker_args_t pong_args = { &shared, 1 };

    kcoro_t *ping_co = NULL;
    kcoro_t *pong_co = NULL;
    rc = kc_spawn_co(sched, ping_fn, &ping_args, 64 * 1024, &ping_co);
    if (rc != 0) {
        fprintf(stderr, "spawn ping failed rc=%d\n", rc);
        return 4;
    }
    rc = kc_spawn_co(sched, pong_fn, &pong_args, 64 * 1024, &pong_co);
    if (rc != 0) {
        fprintf(stderr, "spawn pong failed rc=%d\n", rc);
        return 5;
    }

    fprintf(stderr, "ping_co=%p pong_co=%p\n", (void*)ping_co, (void*)pong_co);


    rc = kc_sched_drain(sched, -1);
    if (rc != 0) {
        fprintf(stderr, "kc_sched_drain rc=%d\n", rc);
    }

    kc_chan_destroy(forward);
    kc_chan_destroy(backward);
    kc_sched_shutdown(sched);
    return 0;
}
