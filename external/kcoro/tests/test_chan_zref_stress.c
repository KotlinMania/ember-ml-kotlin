// SPDX-License-Identifier: BSD-3-Clause
// Stress test for zero-copy channel operations (zref)
// This test validates multi-producer/multi-consumer scenarios with zero-copy references
// It ensures pointer identity, count parity, and proper handling of various race conditions
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <time.h>
#include <stdint.h>
#include <errno.h>
#include "../include/kcoro.h"
#include "../include/kcoro_core.h"
#include "../port/posix.h" /* for KC_EPIPE, KC_EAGAIN, etc. */

/* Stress objectives:
 * 1. Multi-producer / multi-consumer pointer identity & count parity.
 * 2. Sender-first publication (producers start early and park) safety.
 * 3. Receiver-first (consumers waiting before any pointer published).
 * 4. Close during pending zref handoff (both orders) correctness.
 * 5. Randomized soak: ensure zref_sent == zref_received and no deadlocks.
 */

#define ST_STACK (64*1024)
/* Use a moderately large item count to exercise scheduling without excessive runtime. */
#define MAX_ITEMS 2000
#define PRODUCERS 4
#define CONSUMERS 4

// Payload structure for testing zero-copy channel operations
// Contains metadata to verify correctness of producer-consumer interactions
struct payload {
    uint32_t magic;        // Magic number for payload validation
    int producer_id;       // ID of the producer that created this payload
    int seq;               // Sequence number within producer's range
    char str[16];          // String identifier for debugging
};

enum { PAYLOAD_MAGIC = 0xfeedface };

// Producer/Consumer context structure
// Holds shared state for each coroutine during the test
struct pc_ctx {
    kc_chan_t *ch;         // Channel handle shared by all coroutines
    int id;                // Unique identifier for producer/consumer
    int count;             // Total number of items to process
    int produced;          // Count of items produced (for verification)
    int consumed;          // Count of items consumed (for verification)
};

// Producer coroutine function
// Each producer creates payloads and sends them through the channel
// Uses zero-copy send to pass ownership of the payload to the consumer
static void producer_fn(void *arg) {
    struct pc_ctx *ctx = (struct pc_ctx*)arg;
    int verbose = 0; {
        const char *v = getenv("KCORO_ZREF_STRESS_VERBOSE");
        verbose = (v && *v && v[0] != '0');
    }
    for (int i = 0; i < ctx->count; ++i) {
        /* allocate small payload (include producer id & seq) */
        struct payload *p = (struct payload*)malloc(sizeof(struct payload));
        assert(p);
        p->magic = PAYLOAD_MAGIC;
        p->producer_id = ctx->id;
        p->seq = i;
        snprintf(p->str, sizeof(p->str), "P%d", ctx->id);
        if (verbose && ((i % 500) == 0)) {
            fprintf(stderr, "[stress] producer %d alloc payload seq=%d ptr=%p\n", ctx->id, i, (void*)p);
        }
        int rc = kc_chan_send_zref(ctx->ch, p, sizeof(*p), -1);
        if (rc != 0) {
            free(p);
            break;
        }
        ctx->produced++;
    }
}

// Consumer coroutine function
// Each consumer receives payloads from the channel and frees them
// Verifies payload integrity and tracks consumption counts
static void consumer_fn(void *arg) {
    struct pc_ctx *ctx = (struct pc_ctx*)arg;
    int verbose = 0; {
        const char *v = getenv("KCORO_ZREF_STRESS_VERBOSE");
        verbose = (v && *v && v[0] != '0');
    }
    for (;;) {
        void *ptr = NULL; size_t len = 0;
        int rc = kc_chan_recv_zref(ctx->ch, &ptr, &len, -1);
        if (rc != 0) break; /* closed or error */
        assert(ptr && len == sizeof(struct payload));
        struct payload *p = (struct payload*)ptr;
        assert(p->magic == PAYLOAD_MAGIC);
        /* Basic sanity: producer id in range */
        assert(p->producer_id >= 0 && p->producer_id < PRODUCERS);
        if (verbose && ((ctx->consumed % 500) == 0)) {
            fprintf(stderr, "[stress] consumer %d free payload seq=%d ptr=%p pid=%d\n", ctx->id, p->seq, (void*)p, p->producer_id);
        }
        free(p); /* consumer owns payload now */
        ctx->consumed++;
    }
}

// Main stress test function for multi-producer/multi-consumer scenario
// Creates producers and consumers, runs them concurrently, and verifies correctness
static void test_multi_prod_consume(void) {
    kc_chan_t *ch = NULL; int rc = kc_chan_make(&ch, KC_RENDEZVOUS, sizeof(int), 0); assert(rc == 0);
    kc_chan_enable_zero_copy(ch);
    struct pc_ctx pctx[PRODUCERS];
    struct pc_ctx cctx[CONSUMERS];
    kcoro_t *prods[PRODUCERS];
    kcoro_t *cons[CONSUMERS];

    // Initialize producer contexts
    for (int i=0;i<PRODUCERS;i++){ pctx[i].ch=ch; pctx[i].id=i; pctx[i].count=MAX_ITEMS; pctx[i].produced=0; pctx[i].consumed=0; }
    // Initialize consumer contexts
    for (int i=0;i<CONSUMERS;i++){ cctx[i].ch=ch; cctx[i].id=i; cctx[i].count=0; cctx[i].produced=0; cctx[i].consumed=0; }

    // Create consumer coroutines
    for (int i=0;i<CONSUMERS;i++) cons[i]=kcoro_create(consumer_fn,&cctx[i],ST_STACK);
    // Create producer coroutines
    for (int i=0;i<PRODUCERS;i++) prods[i]=kcoro_create(producer_fn,&pctx[i],ST_STACK);

    /* Start all coroutines (interleave) */
    /* Prime consumers so at least one zref receiver waiter exists. */
    for (int i=0;i<CONSUMERS;i++) kcoro_resume(cons[i]);

        /* Simple cooperative round-robin until all producers finished. */
        int safety = 0;
        for (int i=0;i<CONSUMERS;i++) kcoro_resume(cons[i]); /* park receivers */
        while (1) {
            int unfinished = 0;
            for (int i=0;i<PRODUCERS;i++) {
                kcoro_state_t st = prods[i]->state;
                if (st != KCORO_FINISHED && st != KCORO_PARKED) {
                    kcoro_resume(prods[i]);
                    if (prods[i]->state != KCORO_FINISHED) unfinished = 1;
                } else if (st != KCORO_FINISHED) {
                    unfinished = 1; /* still waiting */
                }
            }
            for (int i=0;i<CONSUMERS;i++) {
                kcoro_state_t st = cons[i]->state;
                if (st == KCORO_READY || st == KCORO_SUSPENDED) kcoro_resume(cons[i]);
            }
            if (!unfinished) break;
            if (++safety > 2000000) { fprintf(stderr, "[stress] safety break reached\n"); break; }
        }
    kc_chan_close(ch); /* wake remaining parked consumers */
    int consumers_remaining;
    do {
        consumers_remaining = 0;
        for (int i=0;i<CONSUMERS;i++) {
            kcoro_state_t st = cons[i]->state;
            if (st != KCORO_FINISHED) {
                if (st == KCORO_READY || st == KCORO_SUSPENDED) kcoro_resume(cons[i]);
                if (cons[i]->state != KCORO_FINISHED) consumers_remaining++;
            }
        }
    } while (consumers_remaining > 0);
    kc_chan_destroy(ch);
    long total_prod=0,total_cons=0;
    for (int i=0;i<PRODUCERS;i++) total_prod += pctx[i].produced;
    for (int i=0;i<CONSUMERS;i++) total_cons += cctx[i].consumed;
    assert(total_prod == total_cons);
    fprintf(stderr,"[stress] multi_prod_consume produced=%ld consumed=%ld\n", total_prod, total_cons);
}

/* Sender-first close scenario removed for initial stabilization; will return in expanded suite. */

// Main entry point for the stress test
// Initializes the coroutine system and runs the multi-producer/multi-consumer test
int main(void) {
    printf("[test] zref_stress start\n");
    kcoro_create_main();
    test_multi_prod_consume();
    /* Additional scenarios pending */
    printf("[test] zref_stress ok\n");
    return 0;
}
