/*
 * KCoro Monitor - Real-time TUI for channel + scheduler performance
 * Original channel throughput monitor extended with unified scheduler stats
 * Modes:
 *   - Channel: existing producer/consumer packet benchmark (pps / gbps)
 *   - Tasks:   synthetic task flood measuring scheduler throughput & behavior
 *
 * Key additions (Tasks mode):
 *   - Live tasks/sec (completed per sampling interval)
 *   - Fast-path hit ratio, steal success ratio, inject pulls
 *   - Graph repurposed to show tasks/sec
 *   - Toggle with 't' (or CLI -m tasks)
 */

#define _POSIX_C_SOURCE 199309L
#define _GNU_SOURCE 1

#include <ncurses.h>
#include <pthread.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <time.h>
#include <sys/time.h>
#include <stdio.h>
#include <signal.h>
#include <getopt.h>
#include <sys/resource.h>
#include <stdatomic.h>
#if defined(__APPLE__)
#include <mach/mach.h>
#endif
#include "../../../include/kcoro_bench.h"

#include "kcoro.h" /* ensures struct kc_chan_rate_sample definition available */
#include "kcoro_sched.h"    /* Scheduler stats for tasks mode */
#include "kcoro_core.h"     /* Coroutine core functions */
#include "posix.h"          /* Error codes KC_EPIPE, KC_EAGAIN */

/* Ensure new rate sample API visible even if an older installed kcoro.h was
 * picked up by build system include paths. Fallback local definition mirrors
 * library header and is compiled out when up-to-date header present. */
/* Fallback definition in case build picks an older installed kcoro.h first */
#ifndef KC_CHAN_RATE_SAMPLE_DEFINED
struct kc_chan_rate_sample {
    unsigned long delta_sends;
    unsigned long delta_recvs;
    unsigned long delta_bytes_sent;
    unsigned long delta_bytes_recv;
    unsigned long delta_send_eagain;
    unsigned long delta_recv_eagain;
    unsigned long delta_send_epipe;
    unsigned long delta_recv_epipe;
    unsigned long delta_rv_matches;
    unsigned long delta_rv_cancels;
    unsigned long delta_rv_zdesc_matches;
    double        interval_sec;
    double        sends_per_sec;
    double        recvs_per_sec;
    double        bytes_sent_per_sec;
    double        bytes_recv_per_sec;
};
int kc_chan_compute_rate(const struct kc_chan_snapshot *prev,
                         const struct kc_chan_snapshot *curr,
                         struct kc_chan_rate_sample *out);
#endif

/* Forward declaration (provided by kcoro_core.h) to silence any ordering issues */
void kcoro_yield(void);


#define MAX_HISTORY 100
#define UPDATE_INTERVAL_MS 50
#define STATS_WINDOW_SECS 5
#define KCORO_MON_SCHEMA_VERSION 2

typedef enum {
    MODE_CHANNEL = 0,
    MODE_TASKS   = 1
} monitor_mode_t;

typedef struct {
    double timestamp;
    double pps;
    double gbps;
    double duration_ms;
    double cpu_percent;
    size_t memory_kb;
    int active_producers;
    int active_consumers;
} perf_sample_t;

typedef struct {
    int producers;
    int consumers;
    size_t n_packets;
    size_t capacity;
    size_t packet_size;
    int spin_iters;
    bool running;
    monitor_mode_t mode; /* channel or tasks */
    
    // Performance tracking
    perf_sample_t history[MAX_HISTORY];
    int history_idx;
    int history_count;
    
    // Statistics
    double peak_pps;
    double peak_gbps;
    double avg_pps;
    double avg_gbps;
    
    // Short-term smoothed values for "current" display  
    double smooth_pps;
    double smooth_gbps;
    double smooth_duration;
    
    size_t total_packets;
    
    // Threading
    pthread_t bench_thread;
    pthread_mutex_t stats_lock;
    
    // Benchmark state
    struct {
        double pps;        /* messages per second */
        double gbps;       /* computed bandwidth */
        double duration_s; /* iteration duration */
    } last_result; /* channel mode only */
    bool result_ready;             /* channel mode flag */

    /* Scheduler stats (tasks mode) */
    kc_sched_stats_t prev_sched;   /* previous snapshot */
    double prev_sample_ts;         /* last sample wall clock (sec) */
    double sched_tasks_sec;        /* instantaneous tasks/sec */
    double sched_fastpath_ratio;   /* hits / (hits+misses) */
    double sched_steal_ratio;      /* steals_succeeded / steals_probes */
    unsigned long sched_tasks_submitted_total;
    unsigned long sched_tasks_completed_total;
    
    // UI state
    WINDOW *main_win;
    WINDOW *stats_win;
    WINDOW *graph_win;
    WINDOW *help_win;

    /* Headless / export options */
    bool headless;          /* -H */
    double run_duration_s;  /* -d seconds (0 = indefinite) */
    double start_time;      /* wall clock at start */
    FILE *json_out;         /* -j path (NDJSON) */
    double last_emit_ts;    /* last JSON emission time */
    double emit_interval_s; /* emission cadence (default 0.25s) */

    /* Extended metrics */
    double ema_pps;          /* EMA(alpha=0.25) pps */
    double ema_gbps;         /* EMA(alpha=0.25) gbps */
    double ema_duration_ms;  /* EMA on iteration duration */
    unsigned long mismatch_messages; /* producer message count mismatches */

    /* Channel attributes */
    int           channel_kind;       /* enum kc_kind */
    size_t        channel_capacity;   /* capacity in elements */
    size_t        queue_depth;        /* snapshot count */
    unsigned      channel_caps;       /* KC_CHAN_CAP_* */
    int           channel_closed;     /* 1 if closed */
    int           channel_ptr_mode;   /* 1 if pointer descriptors */
    int           channel_zref_mode;  /* 1 if zero-copy engaged */

    /* Channel failure counters (cumulative totals since start) */
    unsigned long fail_send_eagain_total;
    unsigned long fail_recv_eagain_total;
    unsigned long fail_send_epipe_total;
    unsigned long fail_recv_epipe_total;
    /* Last interval deltas (updated each snapshot compute) */
    unsigned long fail_send_eagain_delta;
    unsigned long fail_recv_eagain_delta;
    unsigned long fail_send_epipe_delta;
    unsigned long fail_recv_epipe_delta;
    /* Channel byte counters (cumulative totals and last-interval deltas) */
    unsigned long bytes_sent_total;
    unsigned long bytes_recv_total;
    unsigned long bytes_sent_delta;
    unsigned long bytes_recv_delta;

    /* Zero-copy counters */
    unsigned long zref_sent_total;
    unsigned long zref_received_total;
    unsigned long zref_aborted_total;
    unsigned long zref_sent_delta;
    unsigned long zref_received_delta;
    unsigned long zref_aborted_delta;

    /* Rendezvous counters */
    unsigned long rv_matches_total;
    unsigned long rv_cancels_total;
    unsigned long rv_zdesc_total;
    unsigned long rv_matches_delta;
    unsigned long rv_cancels_delta;
    unsigned long rv_zdesc_delta;
} monitor_ctx_t;

static monitor_ctx_t g_ctx;
static volatile bool g_shutdown = false;

// CPU usage tracking
static double get_cpu_usage(void) {
    static long prev_idle = 0, prev_total = 0;
    FILE *fp = fopen("/proc/stat", "r");
    if (!fp) return 0.0;
    
    char line[256];
    if (!fgets(line, sizeof(line), fp)) {
        fclose(fp);
        return 0.0;
    }
    fclose(fp);
    
    long user, nice, system, idle, iowait, irq, softirq, steal;
    sscanf(line, "cpu %ld %ld %ld %ld %ld %ld %ld %ld",
           &user, &nice, &system, &idle, &iowait, &irq, &softirq, &steal);
    
    long total = user + nice + system + idle + iowait + irq + softirq + steal;
    long idle_time = idle + iowait;
    
    long total_diff = total - prev_total;
    long idle_diff = idle_time - prev_idle;
    
    prev_total = total;
    prev_idle = idle_time;
    
    if (total_diff == 0) return 0.0;
    return 100.0 * (total_diff - idle_diff) / total_diff;
}

// Memory usage tracking
static size_t get_memory_usage(void) {
#if defined(__APPLE__)
    struct mach_task_basic_info info;
    mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;
    if (task_info(mach_task_self(), MACH_TASK_BASIC_INFO,
                  (task_info_t)&info, &count) == KERN_SUCCESS) {
        return (size_t)(info.resident_size / 1024);
    }
    return 0;
#else
    struct rusage usage;
    if (getrusage(RUSAGE_SELF, &usage) == 0) {
        return (size_t)usage.ru_maxrss; // platform dependent units (KB on Linux)
    }
    return 0;
#endif
}

static void sleep_ms(int ms)
{
    if (ms <= 0) return;
    struct timespec ts = { .tv_sec = ms / 1000, .tv_nsec = (long)(ms % 1000) * 1000000L };
    nanosleep(&ts, NULL);
}

// --- Time utility early (needed by benchmark helpers) ---
static double now_sec(void) {
    struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
    return (double)ts.tv_sec + ts.tv_nsec / 1e9;
}

// --- Channel benchmark thread (existing behavior) OR task flood (tasks mode) ---
static void bench_task(void *arg) { (void)arg; /* synthetic no-op task */ }

/* ---------------- Pure Coroutine-based Channel Benchmark ----------------
 * Pure coroutine implementation: benchmark coordinator, producers, and consumers
 * all run as coroutines within the same scheduler - no pthread competition.
 */

typedef struct chan_prod_arg { kc_chan_t *ch; int id; int count; int spin; size_t msg_size; int *sent_counts; } chan_prod_arg_t;
typedef struct chan_cons_arg { kc_chan_t *ch; int producers; int *per_counts; int spin; size_t msg_size; } chan_cons_arg_t;

/* Benchmark coordinator coroutine - runs the actual benchmark iterations */
typedef struct bench_coord_arg {
    monitor_ctx_t *ctx;
} bench_coord_arg_t;

static _Atomic int g_active_producers = 0; /* number of live producer coroutines */
static _Atomic int g_active_consumers = 0; /* number of live consumer coroutines */

static void co_producer(void *arg) {
    chan_prod_arg_t *pa = (chan_prod_arg_t*)arg; 
    /* pointer-first */
    static __thread unsigned char *buf = NULL;
    if (!buf) buf = aligned_alloc(64, pa->msg_size ? pa->msg_size : 64);
    size_t len = pa->msg_size ? pa->msg_size : 64;
    int sent = 0;
    static _Atomic int announced = 0;
    if (announced == 0) {
        announced = 1;
        fprintf(stderr, "[producer-%d] started (count=%d spin=%d msg_size=%zu)\n", pa->id, pa->count, pa->spin, pa->msg_size);
    }
    
    /* Run continuous cycles for persistent benchmarking */
    while (!g_shutdown) {
        for (int i = 0; i < pa->count; i++) {
            for (;;) {
                int rc = kc_chan_send_ptr(pa->ch, buf, len, 0);
                if (rc == 0) { 
                    sent++; 
                    if (pa->sent_counts) pa->sent_counts[pa->id] = sent;
                    if (sent == 1) fprintf(stderr, "[producer-%d] first send (ptr=%p len=%zu)\n", pa->id, buf, len);
                    break; 
                }
                if (rc == KC_EPIPE) { 
                    if (pa->sent_counts) pa->sent_counts[pa->id] = sent; 
                    return; 
                }
                for (int k = 0; k < pa->spin; k++) {
                    rc = kc_chan_send_ptr(pa->ch, buf, len, 0);
                    if (rc == 0) { 
                        sent++; 
                        if (pa->sent_counts) pa->sent_counts[pa->id] = sent;
                        goto sent_label; 
                    }
                    if (rc == KC_EPIPE) { 
                        if (pa->sent_counts) pa->sent_counts[pa->id] = sent; 
                        return; 
                    }
                }
                kcoro_yield();
sent_label: ;
            }
        }
        /* Brief yield between cycles */
        kcoro_yield();
    }
    atomic_fetch_sub(&g_active_producers, 1);
}

static void co_consumer(void *arg) {
    chan_cons_arg_t *ca = (chan_cons_arg_t*)arg; 
    void *ptr = NULL; size_t len = 0;
    static _Atomic int announced = 0;
    if (announced == 0) {
        announced = 1;
        fprintf(stderr, "[consumer] started (producers=%d spin=%d msg_size=%zu)\n", ca->producers, ca->spin, ca->msg_size);
    }
    
    /* Continuous consumption loop for persistent benchmarking */
    while (!g_shutdown) {
        int rc = kc_chan_recv_ptr(ca->ch, &ptr, &len, 0);
        if (rc == 0) {
            /* count generically */
            ca->per_counts[0]++;
        } else if (rc == KC_EPIPE) {
            break; /* Channel closed */
        } else if (rc == KC_EAGAIN) {
            for (int k = 0; k < ca->spin; k++) {
                rc = kc_chan_recv_ptr(ca->ch, &ptr, &len, 0);
                if (rc == 0) { 
                    ca->per_counts[0]++;
                    goto next; 
                }
                if (rc == KC_EPIPE) return;
            }
            kcoro_yield(); /* cooperative yield */
        }
next: ;
    }
    atomic_fetch_sub(&g_active_consumers, 1);
}

/* Simple continuous benchmark using inherent channel stats */
static void co_benchmark_coordinator(void *arg) {
    bench_coord_arg_t *barg = (bench_coord_arg_t*)arg;
    monitor_ctx_t *ctx = barg->ctx;
    /* We may not yet be running inside a worker thread that has tls_current_sched
     * set (depending on how the coordinator coroutine was enqueued). If the TLS
     * lookup fails, fall back to the global default scheduler so that subsequent
     * kc_spawn_co calls actually enqueue producer/consumer coroutines. Without
     * this fallback the spawn attempts silently fail (NULL sched) and no traffic
     * is ever generated -> all snapshots show zero (only recv EAGAIN spam). */
    kc_sched_t *sched = kc_sched_current();
    if (!sched) {
        sched = kc_sched_default();
    }
    
    /* Start library-provided benchmark and use its channel for stats */
    kc_bench_params_t bp = {
        .kind = KC_BUFFERED,
        .capacity = ctx->capacity,
        .producers = ctx->producers,
        .consumers = ctx->consumers,
        .packets_per_cycle = (ctx->n_packets > 0 ? ctx->n_packets : 100000),
        .spin_iters = ctx->spin_iters,
        .packet_size = ctx->packet_size,
        .pointer_mode = 1
    };
    kc_bench_handle_t *bh = NULL;
    kc_chan_t *persistent_ch = NULL;
    if (kc_bench_chan_start(&bp, &bh, &persistent_ch) != 0 || !persistent_ch) {
        fprintf(stderr, "[coord][ERR] failed to start bench\n");
        return;
    }
    
    int producers = ctx->producers > 0 ? ctx->producers : 1;
    int consumers = ctx->consumers > 0 ? ctx->consumers : 1;
    int per_prod = ctx->n_packets / producers;
    
    /* Allocate persistent producer/consumer args */
    chan_cons_arg_t *cargs = calloc((size_t)consumers, sizeof(chan_cons_arg_t));
    chan_prod_arg_t *pargs = calloc((size_t)producers, sizeof(chan_prod_arg_t));
    int *per_counts = calloc((size_t)producers, sizeof(int));
    int *sent_counts = calloc((size_t)producers, sizeof(int));
    
    if (!cargs || !pargs || !per_counts || !sent_counts) {
        kc_chan_destroy(persistent_ch);
        free(cargs); free(pargs); free(per_counts); free(sent_counts);
        return;
    }
    
    /* Setup persistent producer/consumer args */
    for (int i = 0; i < consumers; i++) {
        cargs[i] = (chan_cons_arg_t){ .ch=persistent_ch, .producers=producers, .per_counts=per_counts, .spin=ctx->spin_iters, .msg_size=ctx->packet_size };
    }
    for (int i = 0; i < producers; i++) {
        pargs[i] = (chan_prod_arg_t){ .ch=persistent_ch, .id=i, .count=per_prod, .spin=ctx->spin_iters, .msg_size=ctx->packet_size, .sent_counts=sent_counts };
    }
    
    /* Spawn continuous producers and consumers AS COROUTINES.
     * Using kc_spawn previously scheduled plain worker tasks (no coroutine context),
     * causing kc_chan_send/recv assert(kcoro_current()!=NULL) to be a no-op under NDEBUG
     * and preventing proper cooperative parking. Using kc_spawn_co gives each producer
     * / consumer its own coroutine stack and allows blocking channel semantics if we
     * later switch away from try_* APIs. */
    int spawned_consumers = 0, spawned_producers = 0;
    for (int i = 0; i < consumers; i++) {
        if (kc_spawn_co(sched, (kcoro_fn_t)co_consumer, &cargs[i], 0, NULL) != 0) {
            fprintf(stderr, "[coord][WARN] failed to spawn consumer %d (sched=%p)\n", i, (void*)sched);
        } else {
            spawned_consumers++;
            atomic_fetch_add(&g_active_consumers, 1);
        }
    }
    for (int i = 0; i < producers; i++) {
        if (kc_spawn_co(sched, (kcoro_fn_t)co_producer, &pargs[i], 0, NULL) != 0) {
            fprintf(stderr, "[coord][WARN] failed to spawn producer %d (sched=%p)\n", i, (void*)sched);
        } else {
            spawned_producers++;
            atomic_fetch_add(&g_active_producers, 1);
        }
    }
    fprintf(stderr, "[coord] spawned %d/%d consumers and %d/%d producers (sched=%p)\n", spawned_consumers, consumers, spawned_producers, producers, (void*)sched);
    
    struct kc_chan_snapshot last_snap; memset(&last_snap, 0, sizeof(last_snap));
    
    while (!g_shutdown) {
        if (!ctx->running) { 
            kcoro_yield(); 
            continue; 
        }
        
        if (ctx->mode == MODE_CHANNEL) {
            struct kc_chan_snapshot snap;
            if (kc_chan_snapshot(persistent_ch, &snap) == 0) {
                struct kc_chan_rate_sample rate;
                kc_chan_compute_rate(&last_snap, &snap, &rate);

                double msgs = (rate.delta_recvs ? (double)rate.delta_recvs : (double)rate.delta_sends);
                double interval = rate.interval_sec > 0.0 ? rate.interval_sec : 1e-6;
                double pps = msgs / interval;
                double gbps = (pps * (double)ctx->packet_size * 8.0) / 1e9;

                unsigned long zref_sent_delta = snap.zref_sent - last_snap.zref_sent;
                unsigned long zref_recv_delta = snap.zref_received - last_snap.zref_received;
                unsigned long zref_abort_delta = snap.zref_aborted_close - last_snap.zref_aborted_close;
                unsigned long rv_match_delta  = snap.rv_matches - last_snap.rv_matches;
                unsigned long rv_cancel_delta = snap.rv_cancels - last_snap.rv_cancels;
                unsigned long rv_zdesc_delta = snap.rv_zdesc_matches - last_snap.rv_zdesc_matches;

                pthread_mutex_lock(&ctx->stats_lock);
                ctx->channel_kind       = snap.kind;
                ctx->channel_capacity   = snap.capacity;
                ctx->queue_depth        = snap.count;
                ctx->channel_caps       = snap.capabilities;
                ctx->channel_closed     = snap.closed;
                ctx->channel_ptr_mode   = snap.ptr_mode;
                ctx->channel_zref_mode  = snap.zref_mode;

                ctx->fail_send_eagain_delta = rate.delta_send_eagain;
                ctx->fail_recv_eagain_delta = rate.delta_recv_eagain;
                ctx->fail_send_epipe_delta  = rate.delta_send_epipe;
                ctx->fail_recv_epipe_delta  = rate.delta_recv_epipe;
                ctx->fail_send_eagain_total = snap.send_eagain;
                ctx->fail_recv_eagain_total = snap.recv_eagain;
                ctx->fail_send_epipe_total  = snap.send_epipe;
                ctx->fail_recv_epipe_total  = snap.recv_epipe;

                ctx->bytes_sent_delta = rate.delta_bytes_sent;
                ctx->bytes_recv_delta = rate.delta_bytes_recv;
                ctx->bytes_sent_total = snap.total_bytes_sent;
                ctx->bytes_recv_total = snap.total_bytes_recv;

                ctx->zref_sent_delta     = zref_sent_delta;
                ctx->zref_received_delta = zref_recv_delta;
                ctx->zref_aborted_delta  = zref_abort_delta;
                ctx->zref_sent_total     = snap.zref_sent;
                ctx->zref_received_total = snap.zref_received;
                ctx->zref_aborted_total  = snap.zref_aborted_close;

                ctx->rv_matches_delta = rv_match_delta;
                ctx->rv_cancels_delta = rv_cancel_delta;
                ctx->rv_zdesc_delta   = rv_zdesc_delta;
                ctx->rv_matches_total = snap.rv_matches;
                ctx->rv_cancels_total = snap.rv_cancels;
                ctx->rv_zdesc_total   = snap.rv_zdesc_matches;

                ctx->total_packets = snap.total_sends;

                if (rate.delta_sends || rate.delta_recvs) {
                    ctx->last_result.pps = pps;
                    ctx->last_result.gbps = gbps;
                    ctx->last_result.duration_s = interval;
                    ctx->result_ready = true;
                }
                pthread_mutex_unlock(&ctx->stats_lock);

                last_snap = snap;
            }
            
        } else { /* MODE_TASKS */
            /* Submit task batch for scheduler benchmark */
            enum { TASK_BATCH = 2048 };
            for (int i = 0; i < TASK_BATCH && ctx->running && !g_shutdown; ++i) {
                kc_spawn(sched, bench_task, NULL);
            }
        }
        
        /* Sample interval */
        for (int i = 0; i < 50 && !g_shutdown; i++) { /* ~50ms */
            kcoro_yield();
        }
    }
    
    /* Cleanup */
    kc_bench_chan_stop(bh);
}

/* Emit current metrics as NDJSON (channel mode) */
static void emit_json_channel(monitor_ctx_t *ctx, const perf_sample_t *sample) {
    if (!ctx->json_out || !sample) return;
    /* Use smoothed metrics plus instantaneous */
    fprintf(ctx->json_out,
        "{\"schema\":%d,\"ts\":%.6f,\"mode\":\"channel\",\"pps\":%.6f,\"gbps\":%.6f,"
        "\"smooth_pps\":%.6f,\"smooth_gbps\":%.6f,\"duration_ms\":%.3f,"
        "\"cpu\":%.2f,\"mem_kb\":%zu,\"producers\":%d,\"consumers\":%d,"
        "\"kind\":%d,\"capacity\":%zu,\"depth\":%zu,\"capabilities\":%u,"
        "\"closed\":%d,\"ptr_mode\":%d,\"zref_mode\":%d,"
        "\"fail_send_eagain_total\":%lu,\"fail_recv_eagain_total\":%lu,"
        "\"fail_send_epipe_total\":%lu,\"fail_recv_epipe_total\":%lu,"
        "\"fail_send_eagain_delta\":%lu,\"fail_recv_eagain_delta\":%lu,"
        "\"fail_send_epipe_delta\":%lu,\"fail_recv_epipe_delta\":%lu,"
        "\"bytes_sent_total\":%lu,\"bytes_recv_total\":%lu,"
        "\"bytes_sent_delta\":%lu,\"bytes_recv_delta\":%lu,"
        "\"zref_sent_total\":%lu,\"zref_received_total\":%lu,\"zref_aborted_total\":%lu,"
        "\"zref_sent_delta\":%lu,\"zref_received_delta\":%lu,\"zref_aborted_delta\":%lu,"
        "\"rv_matches_total\":%lu,\"rv_cancels_total\":%lu,\"rv_zdesc_total\":%lu,"
        "\"rv_matches_delta\":%lu,\"rv_cancels_delta\":%lu,\"rv_zdesc_delta\":%lu}\n",
        KCORO_MON_SCHEMA_VERSION,
        sample->timestamp,
        sample->pps,
        sample->gbps,
        ctx->smooth_pps,
        ctx->smooth_gbps,
        sample->duration_ms,
        sample->cpu_percent,
        sample->memory_kb,
        sample->active_producers,
        sample->active_consumers,
        ctx->channel_kind,
        ctx->channel_capacity,
        ctx->queue_depth,
        ctx->channel_caps,
        ctx->channel_closed,
        ctx->channel_ptr_mode,
        ctx->channel_zref_mode,
        ctx->fail_send_eagain_total,
        ctx->fail_recv_eagain_total,
        ctx->fail_send_epipe_total,
        ctx->fail_recv_epipe_total,
        ctx->fail_send_eagain_delta,
        ctx->fail_recv_eagain_delta,
        ctx->fail_send_epipe_delta,
        ctx->fail_recv_epipe_delta,
        ctx->bytes_sent_total,
        ctx->bytes_recv_total,
        ctx->bytes_sent_delta,
        ctx->bytes_recv_delta,
        ctx->zref_sent_total,
        ctx->zref_received_total,
        ctx->zref_aborted_total,
        ctx->zref_sent_delta,
        ctx->zref_received_delta,
        ctx->zref_aborted_delta,
        ctx->rv_matches_total,
        ctx->rv_cancels_total,
        ctx->rv_zdesc_total,
        ctx->rv_matches_delta,
        ctx->rv_cancels_delta,
        ctx->rv_zdesc_delta);
    fflush(ctx->json_out);
}

static void emit_json_tasks(monitor_ctx_t *ctx, const perf_sample_t *sample) {
    if (!ctx->json_out || !sample) return;
    fprintf(ctx->json_out,
        "{\"schema\":%d,\"ts\":%.6f,\"mode\":\"tasks\",\"tasks_sec\":%.6f,\"smooth_tasks_sec\":%.6f,"
        "\"fastpath_ratio\":%.4f,\"steal_ratio\":%.4f}\n",
        KCORO_MON_SCHEMA_VERSION,
        sample->timestamp,
        sample->pps, /* reused as tasks/sec */
        ctx->smooth_pps, /* smoothing reused field */
        ctx->sched_fastpath_ratio,
        ctx->sched_steal_ratio);
    fflush(ctx->json_out);
}

static void* benchmark_thread(void* arg) {
    monitor_ctx_t *ctx = (monitor_ctx_t*)arg;
    
    /* Spawn the benchmark coordinator as a coroutine - no pthread competition */
    kc_sched_t *sched = kc_sched_default();
    
    /* Allocate coordinator arg on heap to avoid stack lifetime issues */
    bench_coord_arg_t *coord_arg = malloc(sizeof(bench_coord_arg_t));
    if (!coord_arg) return NULL;
    coord_arg->ctx = ctx;
    
    /* Spawn the benchmark coordinator as a real coroutine (required for cooperative kcoro_yield) */
    kc_spawn_co(sched, (kcoro_fn_t)co_benchmark_coordinator, coord_arg, 0, NULL);
    
    /* This pthread now just sleeps and lets the coroutine scheduler do all the work */
    while (!g_shutdown) {
        sleep_ms(100); /* 100ms - minimal impact, just keeps thread alive */
    }
    
    /* Note: coord_arg is intentionally leaked - coroutine may still be running */
    return NULL;
}

// Add performance sample to history
static void add_sample(monitor_ctx_t *ctx, const perf_sample_t *sample) {
    ctx->history[ctx->history_idx] = *sample;
    ctx->history_idx = (ctx->history_idx + 1) % MAX_HISTORY;
    if (ctx->history_count < MAX_HISTORY) {
        ctx->history_count++;
    }
    
    // Update peak values
    if (sample->pps > ctx->peak_pps) ctx->peak_pps = sample->pps;
    if (sample->gbps > ctx->peak_gbps) ctx->peak_gbps = sample->gbps;
    
    // Update totals
    ctx->total_packets += ctx->n_packets;
    
    // Calculate running averages (long-term)
    double sum_pps = 0, sum_gbps = 0;
    int count = ctx->history_count;
    for (int i = 0; i < count; i++) {
        sum_pps += ctx->history[i].pps;
        sum_gbps += ctx->history[i].gbps;
    }
    ctx->avg_pps = sum_pps / count;
    ctx->avg_gbps = sum_gbps / count;
    
    // Calculate short-term smoothed values (last 5 samples for "current")
    int smooth_window = (count < 5) ? count : 5;
    double smooth_sum_pps = 0, smooth_sum_gbps = 0, smooth_sum_duration = 0;
    for (int i = 0; i < smooth_window; i++) {
        int idx = (ctx->history_idx - 1 - i + MAX_HISTORY) % MAX_HISTORY;
        if (idx >= 0 && idx < count) {
            smooth_sum_pps += ctx->history[idx].pps;
            smooth_sum_gbps += ctx->history[idx].gbps;
            smooth_sum_duration += ctx->history[idx].duration_ms;
        }
    }
    ctx->smooth_pps = smooth_sum_pps / smooth_window;
    ctx->smooth_gbps = smooth_sum_gbps / smooth_window;
    ctx->smooth_duration = smooth_sum_duration / smooth_window;
}

// Draw performance graph
static void draw_graph(WINDOW *win, const monitor_ctx_t *ctx) {
    int height, width;
    getmaxyx(win, height, width);
    
    werase(win);
    box(win, 0, 0);
    
    if (ctx->mode == MODE_CHANNEL)
        mvwprintw(win, 0, 2, " Channel Performance Graph ");
    else
        mvwprintw(win, 0, 2, " Scheduler Tasks/sec Graph ");
    
    if (ctx->history_count < 2) {
        mvwprintw(win, height/2, width/2-5, "Collecting data...");
        wrefresh(win);
        return;
    }
    
    // Find max values for scaling (pps reused for tasks/sec in tasks mode)
    double max_pps = 0;
    for (int i = 0; i < ctx->history_count; i++) {
        if (ctx->history[i].pps > max_pps) max_pps = ctx->history[i].pps;
    }
    
    if (max_pps == 0) max_pps = 1;
    
    int graph_height = height - 4;
    int graph_width = width - 4;
    
    // Draw PPS graph
    for (int x = 0; x < graph_width && x < ctx->history_count - 1; x++) {
        int idx = (ctx->history_idx - ctx->history_count + x + MAX_HISTORY) % MAX_HISTORY;
        int next_idx = (idx + 1) % MAX_HISTORY;
        
        double pps = ctx->history[idx].pps;
        double next_pps = ctx->history[next_idx].pps;
        
        int y = graph_height - (int)(pps * graph_height / max_pps);
        int next_y = graph_height - (int)(next_pps * graph_height / max_pps);
        
        y = y < 1 ? 1 : (y > graph_height ? graph_height : y);
        next_y = next_y < 1 ? 1 : (next_y > graph_height ? graph_height : next_y);
        
        mvwaddch(win, y + 1, x + 2, '*');
        
        // Draw line to next point
        if (abs(next_y - y) <= 1) {
            mvwaddch(win, next_y + 1, x + 3, '*');
        } else {
            int steps = abs(next_y - y);
            for (int s = 1; s < steps; s++) {
                int interp_y = y + s * (next_y - y) / steps;
                mvwaddch(win, interp_y + 1, x + 2 + s * graph_width / (ctx->history_count - 1) / steps, '|');
            }
        }
    }
    
    // Draw scale
    if (ctx->mode == MODE_CHANNEL)
        mvwprintw(win, height - 1, 2, "Max: %.1f Mpps", max_pps / 1e6);
    else
        mvwprintw(win, height - 1, 2, "Max: %.1f M tasks/s", max_pps / 1e6);
    
    wrefresh(win);
}

// Draw statistics window
static void draw_stats(WINDOW *win, const monitor_ctx_t *ctx) {
    werase(win);
    box(win, 0, 0);
    
    if (ctx->mode == MODE_CHANNEL)
        mvwprintw(win, 0, 2, " Channel Performance Statistics ");
    else
        mvwprintw(win, 0, 2, " Scheduler Performance Statistics ");
    
    pthread_mutex_lock((pthread_mutex_t*)&ctx->stats_lock);
    double result_pps = ctx->last_result.pps;
    double result_gbps = ctx->last_result.gbps; 
    pthread_mutex_unlock((pthread_mutex_t*)&ctx->stats_lock);
    
    int y = 2;
    if (ctx->mode == MODE_CHANNEL) {
        mvwprintw(win, y++, 2, "Current Performance (5-sample avg):");
        mvwprintw(win, y++, 4, "PPS: %12.3f M", ctx->smooth_pps / 1e6);
    mvwprintw(win, y++, 4, "Gbps: %11.3f", ctx->smooth_gbps);
    mvwprintw(win, y++, 4, "EMA PPS: %9.3f M  EMA Gbps: %.3f", ctx->ema_pps/1e6, ctx->ema_gbps);
        mvwprintw(win, y++, 4, "Duration: %7.3f ms", ctx->smooth_duration);
        y++;
        mvwprintw(win, y++, 2, "Instantaneous:");
        mvwprintw(win, y++, 4, "PPS: %12.3f M", result_pps / 1e6);
    mvwprintw(win, y++, 4, "Gbps: %11.3f", result_gbps);
    mvwprintw(win, y++, 4, "Mismatch Msgs: %lu", ctx->mismatch_messages);
        y++;
        mvwprintw(win, y++, 2, "Failures (totals):");
        mvwprintw(win, y++, 4, "send EAGAIN: %10lu  recv EAGAIN: %10lu", ctx->fail_send_eagain_total, ctx->fail_recv_eagain_total);
        mvwprintw(win, y++, 4, "send EPIPE : %10lu  recv EPIPE : %10lu", ctx->fail_send_epipe_total, ctx->fail_recv_epipe_total);
        mvwprintw(win, y++, 2, "Last Interval Failures:");
        mvwprintw(win, y++, 4, "sEAGAIN: %-8lu rEAGAIN: %-8lu sEPIPE: %-8lu rEPIPE: %-8lu",
                  ctx->fail_send_eagain_delta, ctx->fail_recv_eagain_delta,
                  ctx->fail_send_epipe_delta, ctx->fail_recv_epipe_delta);
    y++;
    mvwprintw(win, y++, 2, "Bytes (totals): sent=%lu recv=%lu", ctx->bytes_sent_total, ctx->bytes_recv_total);
    mvwprintw(win, y++, 2, "Last Interval Bytes: sent=%lu recv=%lu (%.2f / %.2f MB)",
          ctx->bytes_sent_delta, ctx->bytes_recv_delta,
          ctx->bytes_sent_delta / (1024.0*1024.0), ctx->bytes_recv_delta / (1024.0*1024.0));
        y++;
        mvwprintw(win, y++, 2, "Channel State: kind=%d depth=%zu cap=%zu caps=0x%x ptr=%d zref=%d closed=%d",
                  ctx->channel_kind, ctx->queue_depth, ctx->channel_capacity,
                  ctx->channel_caps, ctx->channel_ptr_mode, ctx->channel_zref_mode, ctx->channel_closed);
        mvwprintw(win, y++, 2, "Zero-Copy Totals: sent=%lu recv=%lu abort=%lu", ctx->zref_sent_total, ctx->zref_received_total, ctx->zref_aborted_total);
        mvwprintw(win, y++, 2, "Zero-Copy Delta: sent=%lu recv=%lu abort=%lu",
                  ctx->zref_sent_delta, ctx->zref_received_delta, ctx->zref_aborted_delta);
        mvwprintw(win, y++, 2, "Rendezvous Totals: matches=%lu cancels=%lu desc=%lu",
                  ctx->rv_matches_total, ctx->rv_cancels_total, ctx->rv_zdesc_total);
        mvwprintw(win, y++, 2, "Rendezvous Delta: matches=%lu cancels=%lu desc=%lu",
                  ctx->rv_matches_delta, ctx->rv_cancels_delta, ctx->rv_zdesc_delta);
        y++;
        mvwprintw(win, y++, 2, "Peak Performance:");
        mvwprintw(win, y++, 4, "PPS: %12.3f M", ctx->peak_pps / 1e6);
        mvwprintw(win, y++, 4, "Gbps: %11.3f", ctx->peak_gbps);
        y++;
        mvwprintw(win, y++, 2, "Average Performance:");
        mvwprintw(win, y++, 4, "PPS: %12.3f M", ctx->avg_pps / 1e6);
        mvwprintw(win, y++, 4, "Gbps: %11.3f", ctx->avg_gbps);
    } else {
        mvwprintw(win, y++, 2, "Current Throughput (5-sample avg):");
        mvwprintw(win, y++, 4, "Tasks/s: %9.3f M", ctx->smooth_pps / 1e6);
        mvwprintw(win, y++, 4, "Fast-path hit: %6.2f%%", ctx->sched_fastpath_ratio * 100.0);
        mvwprintw(win, y++, 4, "Steal success: %6.2f%%", ctx->sched_steal_ratio * 100.0);
        y++;
        mvwprintw(win, y++, 2, "Instantaneous:");
        mvwprintw(win, y++, 4, "Tasks/s: %9.3f M", ctx->sched_tasks_sec / 1e6);
        mvwprintw(win, y++, 4, "Submitted: %lu", ctx->sched_tasks_submitted_total);
        mvwprintw(win, y++, 4, "Completed: %lu", ctx->sched_tasks_completed_total);
        y++;
        mvwprintw(win, y++, 2, "Peak / Average:");
        mvwprintw(win, y++, 4, "Peak Tasks/s: %8.3f M", ctx->peak_pps / 1e6);
        mvwprintw(win, y++, 4, "Avg Tasks/s:  %8.3f M", ctx->avg_pps / 1e6);
    }
    
    y++;
    mvwprintw(win, y++, 2, "System Status:");
    mvwprintw(win, y++, 4, "CPU: %11.1f%%", get_cpu_usage());
    mvwprintw(win, y++, 4, "Memory: %8zu KB", get_memory_usage());
    if (ctx->mode == MODE_CHANNEL) {
        mvwprintw(win, y++, 4, "Total Packets: %zu", ctx->total_packets);
        mvwprintw(win, y++, 4, "EMA PPS: %9.3f M", ctx->ema_pps/1e6);
    }
    else
        mvwprintw(win, y++, 4, "Idle Workers (approx): N/A");
    
    wrefresh(win);
}

// Draw main window with configuration
static void draw_main(WINDOW *win, const monitor_ctx_t *ctx) {
    werase(win);
    box(win, 0, 0);
    
    if (ctx->mode == MODE_CHANNEL)
        mvwprintw(win, 0, 2, " KCoro Channel Monitor - ARM64 Performance ");
    else
        mvwprintw(win, 0, 2, " KCoro Scheduler Task Benchmark ");
    
    int y = 2;
    if (ctx->mode == MODE_CHANNEL) {
        mvwprintw(win, y++, 2, "Configuration (Channel):");
        mvwprintw(win, y++, 4, "Producers: %d", ctx->producers);
        mvwprintw(win, y++, 4, "Consumers: %d", ctx->consumers);
        mvwprintw(win, y++, 4, "Packets/Run: %zu", ctx->n_packets);
        mvwprintw(win, y++, 4, "Channel Capacity: %zu", ctx->capacity);
        mvwprintw(win, y++, 4, "Packet Size: %zu bytes", ctx->packet_size);
        mvwprintw(win, y++, 4, "Spin Iterations: %d", ctx->spin_iters);
    } else {
        mvwprintw(win, y++, 2, "Configuration (Tasks):");
        mvwprintw(win, y++, 4, "Workers: auto (scheduler default)");
        mvwprintw(win, y++, 4, "Task Batch: 2048 submits/loop");
        mvwprintw(win, y++, 4, "Task Body: no-op (overhead focus)");
        mvwprintw(win, y++, 4, "Sample Interval: %d ms", UPDATE_INTERVAL_MS);
    }
    
    y++;
    mvwprintw(win, y++, 2, "Status: %s", ctx->running ? "RUNNING" : "STOPPED");
    
    if (ctx->mode == MODE_CHANNEL && ctx->result_ready) {
        pthread_mutex_lock((pthread_mutex_t*)&ctx->stats_lock);
        mvwprintw(win, y++, 2, "Last Result: %.3f Mpps, %.3f Gbps", 
                  ctx->last_result.pps / 1e6, ctx->last_result.gbps);
        pthread_mutex_unlock((pthread_mutex_t*)&ctx->stats_lock);
    } else if (ctx->mode == MODE_TASKS) {
        mvwprintw(win, y++, 2, "Last Sample Tasks/s: %.3f M", ctx->sched_tasks_sec / 1e6);
        mvwprintw(win, y++, 2, "Fast-path: %.2f%%  Steal: %.2f%%", ctx->sched_fastpath_ratio*100.0, ctx->sched_steal_ratio*100.0);
    }
    
    wrefresh(win);
}

// Draw help window
static void draw_help(WINDOW *win) {
    werase(win);
    box(win, 0, 0);
    
    mvwprintw(win, 0, 2, " Controls ");
    
    mvwprintw(win, 2, 2, "q - Quit");
    mvwprintw(win, 3, 2, "r - Toggle Run/Stop");
    mvwprintw(win, 4, 2, "c - Clear statistics");
    mvwprintw(win, 5, 2, "t - Toggle Channel/Tasks");
    mvwprintw(win, 6, 2, "h - Toggle help");
    
    wrefresh(win);
}

// Initialize ncurses UI
static void init_ui(monitor_ctx_t *ctx) {
    initscr();
    cbreak();
    noecho();
    nodelay(stdscr, TRUE);
    curs_set(0);
    
    int height, width;
    getmaxyx(stdscr, height, width);
    
    // Create windows
    ctx->main_win = newwin(height/2, width/2, 0, 0);
    ctx->stats_win = newwin(height/2, width/2, 0, width/2);
    ctx->graph_win = newwin(height/2, width, height/2, 0);
    ctx->help_win = newwin(8, 20, 2, width - 22);
    
    refresh();
}

// Cleanup UI
static void cleanup_ui(monitor_ctx_t *ctx) {
    if (ctx->main_win) delwin(ctx->main_win);
    if (ctx->stats_win) delwin(ctx->stats_win);
    if (ctx->graph_win) delwin(ctx->graph_win);
    if (ctx->help_win) delwin(ctx->help_win);
    endwin();
}

// Signal handler
static void signal_handler(int sig) {
    (void)sig;
    g_shutdown = true;
}

// Main UI loop
static void ui_loop(monitor_ctx_t *ctx) {
    bool show_help = false;
    
    while (!g_shutdown) {
        if (ctx->headless) {
            /* In headless mode we bypass ncurses input but still process samples */
        }
        int ch = -1;
        if (!ctx->headless) {
            ch = getch();
        }
        
        switch (ch) {
        case 'q':
        case 'Q':
            g_shutdown = true;
            break;
        case 'r':
        case 'R':
            ctx->running = !ctx->running;
            break;
        case 'c':
        case 'C':
            // Clear statistics
            pthread_mutex_lock(&ctx->stats_lock);
            ctx->history_count = 0;
            ctx->history_idx = 0;
            ctx->peak_pps = 0;
            ctx->peak_gbps = 0;
            ctx->total_packets = 0;
            pthread_mutex_unlock(&ctx->stats_lock);
            break;
        case 'h': case 'H': show_help = !show_help; break;
        case 't': case 'T':
            ctx->mode = (ctx->mode == MODE_CHANNEL) ? MODE_TASKS : MODE_CHANNEL;
            /* Reset statistics when switching modes */
            pthread_mutex_lock(&ctx->stats_lock);
            ctx->history_count = ctx->history_idx = 0;
            ctx->peak_pps = ctx->peak_gbps = 0;
            ctx->avg_pps = ctx->avg_gbps = 0;
            ctx->smooth_pps = ctx->smooth_gbps = 0;
            ctx->total_packets = 0;
            ctx->result_ready = false;
            memset(&ctx->prev_sched, 0, sizeof(ctx->prev_sched));
            ctx->prev_sample_ts = now_sec();
            pthread_mutex_unlock(&ctx->stats_lock);
            break;
        }
        
        // Update performance sample
        if (ctx->mode == MODE_CHANNEL && ctx->result_ready) {
            perf_sample_t sample;
            
            pthread_mutex_lock(&ctx->stats_lock);
            sample.pps = ctx->last_result.pps;
            sample.gbps = ctx->last_result.gbps;
            sample.duration_ms = ctx->last_result.duration_s * 1000.0;
            sample.timestamp = time(NULL);
            sample.cpu_percent = get_cpu_usage();
            sample.memory_kb = get_memory_usage();
            sample.active_producers = ctx->producers;
            sample.active_consumers = ctx->consumers;
            pthread_mutex_unlock(&ctx->stats_lock);
            
            add_sample(ctx, &sample);
            /* Update EMA (alpha=0.25) */
            const double alpha = 0.25;
            if (ctx->ema_pps == 0) {
                ctx->ema_pps = sample.pps;
                ctx->ema_gbps = sample.gbps;
                ctx->ema_duration_ms = sample.duration_ms;
            } else {
                ctx->ema_pps = alpha * sample.pps + (1.0 - alpha) * ctx->ema_pps;
                ctx->ema_gbps = alpha * sample.gbps + (1.0 - alpha) * ctx->ema_gbps;
                ctx->ema_duration_ms = alpha * sample.duration_ms + (1.0 - alpha) * ctx->ema_duration_ms;
            }
            ctx->result_ready = false; /* consume one-shot */
            if (ctx->headless) {
                double now = now_sec();
                if (ctx->json_out && (now - ctx->last_emit_ts) >= ctx->emit_interval_s) {
                    emit_json_channel(ctx, &sample);
                    ctx->last_emit_ts = now;
                }
            }
        } else if (ctx->mode == MODE_TASKS) {
            /* Sample scheduler stats every loop */
            kc_sched_t *sched = kc_sched_default();
            kc_sched_stats_t cur; memset(&cur, 0, sizeof(cur));
            kc_sched_get_stats(sched, &cur);
            double now = now_sec();
            if (ctx->prev_sample_ts == 0) ctx->prev_sample_ts = now;
            double dt = now - ctx->prev_sample_ts;
            if (dt > 0.0005) { /* avoid divide by near-zero */
                unsigned long d_completed = cur.tasks_completed - ctx->prev_sched.tasks_completed;
                double tasks_sec = (double)d_completed / dt;
                ctx->sched_tasks_sec = tasks_sec;
                /* Build synthetic perf_sample mapping tasks/sec -> pps field */
                perf_sample_t sample = {0};
                sample.pps = tasks_sec; /* reuse graph */
                sample.gbps = 0; sample.duration_ms = dt * 1000.0; sample.timestamp = now;
                add_sample(ctx, &sample);
                const double alpha = 0.25;
                if (ctx->ema_pps == 0) ctx->ema_pps = sample.pps; else ctx->ema_pps = alpha * sample.pps + (1.0-alpha)*ctx->ema_pps;
                if (ctx->headless) {
                    if (ctx->json_out && (now - ctx->last_emit_ts) >= ctx->emit_interval_s) {
                        emit_json_tasks(ctx, &sample);
                        ctx->last_emit_ts = now;
                    }
                }
                unsigned long fp_total = cur.fastpath_hits + cur.fastpath_misses;
                ctx->sched_fastpath_ratio = fp_total ? (double)cur.fastpath_hits / (double)fp_total : 0.0;
                ctx->sched_steal_ratio = cur.steals_probes ? (double)cur.steals_succeeded / (double)cur.steals_probes : 0.0;
                ctx->sched_tasks_submitted_total = cur.tasks_submitted;
                ctx->sched_tasks_completed_total = cur.tasks_completed;
                ctx->prev_sched = cur;
                ctx->prev_sample_ts = now;
            }
        }
        
        if (!ctx->headless) {
            // Draw UI
            draw_main(ctx->main_win, ctx);
            draw_stats(ctx->stats_win, ctx);
            draw_graph(ctx->graph_win, ctx);
            if (show_help) draw_help(ctx->help_win);
            sleep_ms(UPDATE_INTERVAL_MS);
        } else {
            /* Headless pacing */
            sleep_ms(UPDATE_INTERVAL_MS);
        }

        /* Headless duration check */
        if (ctx->headless && ctx->run_duration_s > 0) {
            double elapsed = now_sec() - ctx->start_time;
            if (elapsed >= ctx->run_duration_s) { g_shutdown = true; }
        }
    }
}

// Usage information
static void usage(const char *prog) {
    printf("Usage: %s [OPTIONS]\n", prog);
    printf("Options:\n");
    printf("  -P, --producers N    Number of producer threads (default: 2)\n");
    printf("  -C, --consumers N    Number of consumer threads (default: 2)\n");
    printf("  -N, --packets N      Number of packets per run (default: 200000)\n");
    printf("  -c, --capacity N     Channel capacity (default: 16384)\n");
    printf("  -k, --packet-size N  Packet size in bytes (default: 1500)\n");
    printf("  -s, --spin N         Spin iterations (default: 4096)\n");
    printf("  -m, --mode M         Mode: channel|tasks (default: channel)\n");
    printf("  -H, --headless       Headless mode (no TUI, useful with -j/-d)\n");
    printf("  -d, --duration SEC   Run duration in seconds (headless)\n");
    printf("  -j, --json PATH      NDJSON export file\n");
    printf("\nEMA smoothing (alpha=0.25) active; mismatch messages tracked.\n");
    printf("  -h, --help           Show this help\n");
    printf("\nOptimal ARM64 configuration: -P 2 -C 2 -N 200000 -c 16384 -s 4096 -k 1500\n");
}

int main(int argc, char *argv[]) {
    // Initialize context with optimal defaults
    memset(&g_ctx, 0, sizeof(g_ctx));
    g_ctx.producers = 2;
    g_ctx.consumers = 2;
    g_ctx.n_packets = 200000;
    g_ctx.capacity = 16384;
    g_ctx.packet_size = 1500;
    g_ctx.spin_iters = 4096;
    g_ctx.running = true;
    g_ctx.mode = MODE_CHANNEL; /* default */
    g_ctx.prev_sample_ts = 0.0;
    
    // Parse command line arguments
    static struct option long_options[] = {
        {"producers", required_argument, 0, 'P'},
        {"consumers", required_argument, 0, 'C'},
        {"packets", required_argument, 0, 'N'},
        {"capacity", required_argument, 0, 'c'},
        {"packet-size", required_argument, 0, 'k'},
        {"spin", required_argument, 0, 's'},
        {"mode", required_argument, 0, 'm'},
        {"headless", no_argument, 0, 'H'},
        {"duration", required_argument, 0, 'd'},
        {"json", required_argument, 0, 'j'},
        {"help", no_argument, 0, 'h'},
        {0, 0, 0, 0}
    };
    
    int c;
    while ((c = getopt_long(argc, argv, "P:C:N:c:k:s:m:Hd:j:h", long_options, NULL)) != -1) {
        switch (c) {
        case 'P':
            g_ctx.producers = atoi(optarg);
            break;
        case 'C':
            g_ctx.consumers = atoi(optarg);
            break;
        case 'N':
            g_ctx.n_packets = atoi(optarg);
            break;
        case 'c':
            g_ctx.capacity = atoi(optarg);
            break;
        case 'k':
            g_ctx.packet_size = atoi(optarg);
            break;
        case 's':
            g_ctx.spin_iters = atoi(optarg);
            break;
        case 'm':
            if (strcasecmp(optarg, "tasks") == 0) g_ctx.mode = MODE_TASKS;
            else g_ctx.mode = MODE_CHANNEL;
            break;
        case 'H':
            g_ctx.headless = true; break;
        case 'd':
            g_ctx.run_duration_s = atof(optarg); break;
        case 'j':
            g_ctx.json_out = fopen(optarg, "a");
            if (!g_ctx.json_out) { perror("json open"); return 2; }
            setvbuf(g_ctx.json_out, NULL, _IOLBF, 0); /* line buffered */
            break;
        case 'h':
            usage(argv[0]);
            return 0;
        default:
            usage(argv[0]);
            return 1;
        }
    }
    
    // Setup signal handlers
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    
    // Initialize mutex
    pthread_mutex_init(&g_ctx.stats_lock, NULL);
    
    g_ctx.emit_interval_s = 0.25;
    g_ctx.start_time = now_sec();
    if (!g_ctx.headless) init_ui(&g_ctx);
    
    // Launch benchmark coordinator as a coroutine (no pthread competition)
    kc_sched_t *sched = kc_sched_default();
    bench_coord_arg_t *coord_arg = malloc(sizeof(bench_coord_arg_t));
    if (!coord_arg) return 2;
    coord_arg->ctx = &g_ctx;
    kc_spawn_co(sched, (kcoro_fn_t)co_benchmark_coordinator, coord_arg, 0, NULL);
    
    // Run UI loop
    ui_loop(&g_ctx);
    
    // Cleanup
    g_ctx.running = false;
    /* Coordinator coroutine will observe g_shutdown and exit on its own. */
    if (!g_ctx.headless) cleanup_ui(&g_ctx);
    pthread_mutex_destroy(&g_ctx.stats_lock);
    
    /* Emit JSON summary if requested */
    if (g_ctx.json_out) {
        if (g_ctx.mode == MODE_CHANNEL) {
            fprintf(g_ctx.json_out,
                "{\"schema\":%d,\"type\":\"summary\",\"mode\":\"channel\",\"peak_pps\":%.6f,\"peak_gbps\":%.6f,\"avg_pps\":%.6f,\"avg_gbps\":%.6f,\"ema_pps\":%.6f,\"ema_gbps\":%.6f,\"mismatch_msgs\":%lu,"
                "\"fail_send_eagain_total\":%lu,\"fail_recv_eagain_total\":%lu,\"fail_send_epipe_total\":%lu,\"fail_recv_epipe_total\":%lu,"
                "\"bytes_sent_total\":%lu,\"bytes_recv_total\":%lu}\n",
                KCORO_MON_SCHEMA_VERSION,
                g_ctx.peak_pps, g_ctx.peak_gbps, g_ctx.avg_pps, g_ctx.avg_gbps, g_ctx.ema_pps, g_ctx.ema_gbps, g_ctx.mismatch_messages,
                g_ctx.fail_send_eagain_total, g_ctx.fail_recv_eagain_total, g_ctx.fail_send_epipe_total, g_ctx.fail_recv_epipe_total,
                g_ctx.bytes_sent_total, g_ctx.bytes_recv_total);
        } else {
            fprintf(g_ctx.json_out,
                "{\"schema\":%d,\"type\":\"summary\",\"mode\":\"tasks\",\"peak_tasks_sec\":%.6f,\"avg_tasks_sec\":%.6f,\"ema_tasks_sec\":%.6f,\"fastpath_ratio\":%.4f,\"steal_ratio\":%.4f}\n",
                KCORO_MON_SCHEMA_VERSION,
                g_ctx.peak_pps, g_ctx.avg_pps, g_ctx.ema_pps, g_ctx.sched_fastpath_ratio, g_ctx.sched_steal_ratio);
        }
        fflush(g_ctx.json_out);
        fclose(g_ctx.json_out);
    }

    /* Always print textual summary */
    if (g_ctx.mode == MODE_CHANNEL) {
        printf("KCoro Channel Monitor - ARM64 Performance Test Complete\n");
        printf("Peak Performance: %.3f Mpps, %.3f Gbps (EMA %.3f Mpps, %.3f Gbps) MismatchMsgs=%lu\n",
               g_ctx.peak_pps / 1e6, g_ctx.peak_gbps, g_ctx.ema_pps/1e6, g_ctx.ema_gbps, g_ctx.mismatch_messages);
    } else {
        printf("KCoro Scheduler Task Benchmark Complete\n");
        printf("Peak Tasks/sec: %.3f M (avg %.3f M, EMA %.3f M) Fastpath=%.2f%% Steal=%.2f%%\n",
               g_ctx.peak_pps / 1e6, g_ctx.avg_pps / 1e6, g_ctx.ema_pps/1e6,
               g_ctx.sched_fastpath_ratio*100.0, g_ctx.sched_steal_ratio*100.0);
    }
    
    return 0;
}
