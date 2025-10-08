// SPDX-License-Identifier: BSD-3-Clause
/**
 * @file kcoro.h
 * @brief Public API for kcoro coroutines, channels, actors, select, and stats.
 *
 * -----------------------------------------------------------------------------
 * Header Surface & Optional Items (Read Me First)
 * -----------------------------------------------------------------------------
 * Purpose
 *   This is the primary include for application developers. It declares the
 *   stable coroutine primitives, the unified scheduler facade, channel kinds
 *   and operations, select, cancellation integration points, and statistics.
 *
 * Design principles
 *   - Portable: ANSI C + POSIX only; no OS‑specific kernel headers in core.
 *   - Clear contracts: 0 on success; negative KC_* (mapped to -errno) on error.
 *   - Timeouts: timeout_ms == 0 → try; < 0 → infinite; > 0 → bounded wait.
 *   - Observability: every successful op increments counters; snapshot/rate
 *     helpers expose exact totals and computed rates.
 *
 * Optional/presence macros (why they exist)
 *   - KC_CHAN_SNAPSHOT_DEFINED, KC_CHAN_RATE_SAMPLE_DEFINED
 *     These are presence flags for tools that may build against different
 *     header vintages. In this version they are always defined (=1). They are
 *     harmless and exist only to let external tools feature‑detect without
 *     #ifdef‑ing function names directly.
 *
 * Region and zero‑copy stubs
 *   - The base region APIs may return -ENOTSUP until an adapter is provided.
 *     This keeps the core neutral while allowing out‑of‑tree bridges later.
 *
 * Install guidance
 *   - This header is part of the production public API and should be installed.
 *
 * Reader’s map
 *   - enum kc_kind defines channel coordination/buffering semantics.
 *   - Capability bits advertise optional features (zref, pointer descriptors).
 *   - Pointer‑descriptor helpers are convenience wrappers over the canonical
 *     descriptor APIs declared in kcoro_zcopy.h.
 */
#pragma once

#include <stddef.h>
#include "kcoro_abi.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ABI version moved to kcoro_abi.h (BSD, no libc deps required) */

typedef struct kcoro kcoro_t;
typedef void (*kcoro_fn_t)(void*);

/* Channel kinds */
/**
 * @brief Channel kinds (capacity/coordination semantics).
 */
enum kc_kind {
    KC_RENDEZVOUS = 0,  /**< Sender and receiver meet; no buffering. */
    KC_BUFFERED   = 1,  /**< Bounded ring buffer with given capacity. */
    KC_CONFLATED  = -1, /**< Single slot; latest value overwrites previous. */
    KC_UNLIMITED  = -2  /**< Logically unbounded; grows in segments as needed. */
};

/* Opaque types */
typedef struct kc_chan kc_chan_t;
typedef void* kc_actor_t; /* platform-specific task handle */
typedef struct kc_scope kc_scope_t; /* structured concurrency scope */
typedef struct kc_select kc_select_t; /* select expression */

/* Cancellation token */
typedef struct kc_cancel kc_cancel_t; /* opaque */

int  kc_cancel_init(kc_cancel_t **out);
void kc_cancel_trigger(kc_cancel_t *t);
int  kc_cancel_is_set(const kc_cancel_t *t);
void kc_cancel_destroy(kc_cancel_t *t);

/* Hierarchical cancellation context (Phase 1.5) */
typedef struct kc_cancel_ctx {
    const kc_cancel_t *parent; /* optional */
    kc_cancel_t       *token;  /* owned */
} kc_cancel_ctx_t;

/* Initializes ctx->token; if parent is set, parent cancellation propagates to token. */
int  kc_cancel_ctx_init(kc_cancel_ctx_t *ctx, const kc_cancel_t *parent);
void kc_cancel_ctx_destroy(kc_cancel_ctx_t *ctx);

/**
 * @name Channel API (generic payload copy)
 * @{ */
int  kc_chan_make(kc_chan_t** out, int kind, size_t elem_sz, size_t capacity);
void kc_chan_destroy(kc_chan_t* ch);
int  kc_chan_send(kc_chan_t* ch, const void* msg, long timeout_ms);
int  kc_chan_recv(kc_chan_t* ch, void* out, long timeout_ms);
void kc_chan_close(kc_chan_t* ch);
unsigned kc_chan_len(kc_chan_t* ch);
/** @} */

/**
 * @name Cancellable variants
 * These return KC_ECANCELED promptly when the token is triggered.
 * @{ */
int  kc_chan_send_c(kc_chan_t* ch, const void* msg, long timeout_ms, const kc_cancel_t* cancel);
int  kc_chan_recv_c(kc_chan_t* ch, void* out, long timeout_ms, const kc_cancel_t* cancel);
/** @} */

/* Non-blocking convenience wrappers */
static inline int kc_chan_try_send(kc_chan_t* ch, const void* msg) {
    return kc_chan_send(ch, msg, 0);
}
static inline int kc_chan_try_recv(kc_chan_t* ch, void* out) {
    return kc_chan_recv(ch, out, 0);
}

/* Actor API */
typedef int (*kc_actor_fn)(const void* msg, void* user);

typedef struct kc_actor_ctx {
    kc_chan_t   *chan;       /* channel to consume */
    size_t       msg_size;   /* bytes; must match channel elem size */
    int          timeout_ms; /* -1=forever, 0=nonblocking */
    kc_actor_fn  process;    /* called for each received message */
    void        *user;       /* opaque */
} kc_actor_ctx_t;

kc_actor_t kc_actor_start(const kc_actor_ctx_t* ctx);
void       kc_actor_stop(kc_actor_t actor);

/* Extended actor API with cancellation token */
typedef struct kc_actor_ctx_ex {
    kc_actor_ctx_t      base;
    const kc_cancel_t  *cancel; /* optional */
} kc_actor_ctx_ex_t;

kc_actor_t kc_actor_start_ex(const kc_actor_ctx_ex_t* ctx);
void       kc_actor_cancel(kc_actor_t actor); /* triggers token if present, else fallback stop */
void       kc_actor_on_done(kc_actor_t actor, void (*cb)(void *arg), void *arg);

typedef int (*kc_producer_fn)(kc_chan_t *ch, void *user);
typedef int (*kc_transform_fn)(const void *in, void *out, void *user);

int  kc_scope_init(kc_scope_t **out, const kc_cancel_t *parent);
void kc_scope_cancel(kc_scope_t *scope);
int  kc_scope_launch(kc_scope_t *scope, kcoro_fn_t fn, void *arg,
                     size_t stack_size, kcoro_t **out_co);
kc_actor_t kc_scope_actor(kc_scope_t *scope, const kc_actor_ctx_t *ctx);
kc_chan_t* kc_scope_produce(kc_scope_t *scope, int kind, size_t elem_sz, size_t capacity,
                            kc_producer_fn fn, void *user);
int  kc_scope_wait_all(kc_scope_t *scope, long timeout_ms);
void kc_scope_destroy(kc_scope_t *scope);
const kc_cancel_t* kc_scope_token(const kc_scope_t *scope);

/* Select / multiplexing API */
/** Select clause kinds for kc_select_t. */
enum kc_select_clause_kind {
    KC_SELECT_CLAUSE_RECV = 0, /**< Receive clause */
    KC_SELECT_CLAUSE_SEND = 1, /**< Send clause    */
};

int  kc_select_create(kc_select_t **out, const kc_cancel_t *cancel);
void kc_select_destroy(kc_select_t *sel);
void kc_select_reset(kc_select_t *sel);
int  kc_select_add_recv(kc_select_t *sel, kc_chan_t *chan, void *out);
int  kc_select_add_send(kc_select_t *sel, kc_chan_t *chan, const void *msg);
int  kc_select_wait(kc_select_t *sel, long timeout_ms, int *selected_index, int *op_result);

/* --- Zero-Copy Channel Extensions (Phase Z) ---------------------------------
 * Portable surface: these APIs do not expose platform‑specific kernel or
 * networking types. Integrations (shared memory regions, custom DMA pools)
 * adapt by supplying plain (ptr,len) pairs via zref ops.
 *
 * Design notes:
 *  - Opt-in via channel capability flag; existing channels unchanged.
 *  - No implicit buffer allocation or free; ownership transfer contract is
 *    documented in design docs (Phase Z).
 *  - Region APIs are forward-declared; base implementation may return
 *    -ENOTSUP until a region backend is provided.
 */

/* Capability flag query (bitmask). Additional flags may follow. */
unsigned kc_chan_capabilities(kc_chan_t *ch);

/* Capability bits (public so callers can feature‑detect). */
/**
 * Channel supports zero‑copy (zref) operations.
 * Set when a zcopy backend is bound. Feature‑detect with
 * kc_chan_capabilities(ch) & KC_CHAN_CAP_ZERO_COPY.
 */
#define KC_CHAN_CAP_ZERO_COPY   (1u<<0)
/**
 * Channel element is a pointer‑descriptor (ptr,len).
 * Indicates make_ptr semantics: the queue stores small (ptr,len) records,
 * not payload copies; zref may route via backend when enabled.
 */
#define KC_CHAN_CAP_PTR         (1u<<1)

/* Zero-copy send/recv (rendezvous or buffered). Returns 0 on success, negative errno.
 * On success kc_chan_recv_zref stores pointer/length; caller owns pointer until
 * it either recycles/frees or forwards again. Timeout/cancel semantics mirror
 * kc_chan_send/kc_chan_recv. */
int kc_chan_send_zref(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms);
int kc_chan_send_zref_c(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms, const kc_cancel_t *cancel);
int kc_chan_recv_zref(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms);
int kc_chan_recv_zref_c(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms, const kc_cancel_t *cancel);

/* Shared Region Registration (Phase Z.3+, forward declarations). A region is a
 * stable memory area whose pointer+offset can be handed off instead of copying.
 * Base implementation may stub these (return -ENOTSUP). */
typedef struct kc_region kc_region_t; /* opaque */

/**
 * Region flags (reserved for future NUMA/hugepage hints).
 * Placeholder for adapter‑specific policies; core treats KC_REGION_F_NONE as
 * the only defined value today.
 */
#define KC_REGION_F_NONE        0u

int  kc_region_register(kc_region_t **out, void *addr, size_t len, unsigned flags);
int  kc_region_deregister(kc_region_t *reg); /* blocks until no in-flight refs */
/* Optional: export an ID for IPC; -ENOTSUP if not implemented. */
int  kc_region_export_id(const kc_region_t *reg, unsigned long *out_id);

/* Associate a channel with zero-copy capability after creation (optional).
 * Returns 0 if enabled, -EINVAL if channel kind incompatible, -EBUSY if already in use. */
int  kc_chan_enable_zero_copy(kc_chan_t *ch);

/* Introspection counters (may return 0 if unsupported). */
struct kc_chan_zstats {
    unsigned long zref_sent;
    unsigned long zref_received;
    unsigned long zref_fallback_small;
    unsigned long zref_fallback_capacity;
    unsigned long zref_canceled;
    unsigned long zref_aborted_close;
};
int kc_chan_get_zstats(kc_chan_t *ch, struct kc_chan_zstats *out);

/* ---------------- Pointer-First Descriptor API (Phase P1) -----------------
 * Rationale: For very high throughput (100 Gbps+ full duplex target) we avoid
 * per-element copying by making the canonical element a (ptr,len) tuple.
 * This differs from zero-copy rendezvous (zref) which is restricted to
 * rendezvous channels; pointer descriptor channels work across all kinds
 * (buffered, unlimited, conflated, rendezvous) using normal queueing while
 * only copying the small descriptor struct. Statistics account bytes using
 * the provided length rather than sizeof(struct kc_chan_ptrmsg).
 * Mixing pointer descriptor ops with the generic kc_chan_send/recv APIs is
 * disallowed (returns -EINVAL) once ptr mode is enabled.
 */
struct kc_chan_ptrmsg { void *ptr; size_t len; };

/**
 * @brief Create a pointer‑descriptor channel and mark KC_CHAN_CAP_PTR.
 * Semantics of kind/capacity mirror kc_chan_make.
 */
int kc_chan_make_ptr(kc_chan_t **out, int kind, size_t capacity);

/**
 * @brief Send/receive a pointer‑descriptor (len > 0).
 * Returns 0 or negative KC_*; timeout semantics match kc_chan_send/recv.
 */
int kc_chan_send_ptr(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms);
int kc_chan_recv_ptr(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms);

/* Cancellable variants. */
int kc_chan_send_ptr_c(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms, const kc_cancel_t *cancel);
int kc_chan_recv_ptr_c(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms, const kc_cancel_t *cancel);

/** @name Channel throughput statistics */
struct kc_chan_stats {
    unsigned long total_sends;        /* Total successful sends */
    unsigned long total_recvs;        /* Total successful receives */
    unsigned long total_bytes_sent;   /* Total bytes sent through channel */
    unsigned long total_bytes_recv;   /* Total bytes received from channel */
    long          first_op_time_ns;   /* Timestamp of first operation */
    long          last_op_time_ns;    /* Timestamp of last operation */
    double        send_rate_ops_sec;  /* Computed send rate (ops/sec) */
    double        recv_rate_ops_sec;  /* Computed recv rate (ops/sec) */
    double        send_rate_bytes_sec; /* Computed send rate (bytes/sec) */
    double        recv_rate_bytes_sec; /* Computed recv rate (bytes/sec) */
    double        duration_sec;       /* Duration between first and last op */
};
int kc_chan_get_stats(kc_chan_t *ch, struct kc_chan_stats *out);

/**
 * @brief Comprehensive instantaneous snapshot (low overhead lock + memcpy).
 * Fail‑fast policy: this struct must always be available to dependents; no
 * conditional fallback allowed.
 */
#undef KC_CHAN_SNAPSHOT_DEFINED
/**
 * Presence flag for struct kc_chan_snapshot.
 * Always defined (=1) in this version; tools may use it to guard newer fields
 * when supporting older header vintages.
 */
#define KC_CHAN_SNAPSHOT_DEFINED 1
struct kc_chan_snapshot {
    void         *chan;             /* identity */
    int           kind;             /* enum kc_kind or >0 capacity alias */
    size_t        elem_sz;
    size_t        capacity;         /* 0 for rendezvous / conflated */
    size_t        count;            /* queue depth (1 if conflated has value) */
    unsigned      capabilities;     /* KC_CHAN_CAP_* */
    int           closed;           /* 1 if closed */
    int           zref_mode;        /* 1 if zero-copy path engaged */
    int           ptr_mode;         /* 1 if pointer-descriptor channel */

    /* Success counters */
    unsigned long total_sends;
    unsigned long total_recvs;
    unsigned long total_bytes_sent;
    unsigned long total_bytes_recv;
    long          first_op_time_ns;
    long          last_op_time_ns;

    /* Failure counters */
    unsigned long send_eagain;
    unsigned long send_etime;
    unsigned long send_epipe;
    unsigned long recv_eagain;
    unsigned long recv_etime;
    unsigned long recv_epipe;

    /* Zero-copy counters */
    unsigned long zref_sent;
    unsigned long zref_received;
    unsigned long zref_aborted_close;

    /* Rendezvous counters */
    unsigned long rv_matches;
    unsigned long rv_cancels;
    unsigned long rv_zdesc_matches;

    /* Derived */
    double        duration_sec;
};

int kc_chan_snapshot(kc_chan_t *ch, struct kc_chan_snapshot *out);

/* Rate sampling helper: compares two snapshots (earlier -> later) and computes
 * deltas and per-second rates without the caller needing to duplicate logic.
 * Contract:
 *  - earlier may be zeroed (all fields 0) to indicate "no previous sample";
 *    in that case rates are computed using the later snapshot's intrinsic
 *    duration_sec if available, else 0.
 *  - If delta_time_sec resolves to <=0 (e.g. extremely fast successive calls),
 *    a fallback minimum interval of 1 microsecond is used to avoid division by
 *    zero and preserve burst visibility.
 */
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
    double        interval_sec;     /* effective interval (>=1e-6 when deltas>0) */
    double        sends_per_sec;
    double        recvs_per_sec;
    double        bytes_sent_per_sec;
    double        bytes_recv_per_sec;
};
/**
 * Presence flag for kc_chan_compute_rate helper.
 * Always defined (=1) in this version; lets tools feature‑detect rate support
 * without hard #ifdefs on symbol names.
 */
#define KC_CHAN_RATE_SAMPLE_DEFINED 1

int kc_chan_compute_rate(const struct kc_chan_snapshot *prev,
                         const struct kc_chan_snapshot *curr,
                         struct kc_chan_rate_sample *out);

/* ------------------------- Metrics Pipe (Phase M1) -------------------------
 * Optional per-channel live metrics event stream. When enabled, the channel
 * pushes periodic aggregate+delta events into an internal buffered channel.
 * Overhead when disabled: one NULL branch per send/recv fast-path.
 *
 * Emission policy (initial implementation): emit when either:
 *   - (delta_sends + delta_recvs) >= KC_CHAN_METRICS_EMIT_MIN_OPS, OR
 *   - now - last_emit_time_ns >= KC_CHAN_METRICS_EMIT_MIN_NS
 * Constants are intentionally conservative to cap overhead; future phases
 * may expose tuning knobs or adaptive heuristics.
 */
struct kc_chan_metrics_event {
    void         *chan;              /* identity (raw pointer) */
    unsigned long total_sends;
    unsigned long total_recvs;
    unsigned long total_bytes_sent;
    unsigned long total_bytes_recv;
    unsigned long delta_sends;       /* since previous emitted event */
    unsigned long delta_recvs;
    unsigned long delta_bytes_sent;
    unsigned long delta_bytes_recv;
    long          first_op_time_ns;
    long          last_op_time_ns;
    long          emit_time_ns;      /* timestamp of emission */
};

/* Enable metrics pipe; creates (if absent) an internal buffered channel whose
 * element type is kc_chan_metrics_event. Caller receives the pipe channel
 * pointer (ownership shared; destroying original channel does NOT auto-destroy
 * the pipe). capacity==0 => default (64). Returns 0 or -errno.
 */
int kc_chan_enable_metrics_pipe(kc_chan_t *ch, kc_chan_t **out_pipe, size_t capacity);

/* Disable metrics pipe (stop emitting). Does NOT destroy the pipe channel. */
int kc_chan_disable_metrics_pipe(kc_chan_t *ch);

/* Query current metrics pipe (NULL if disabled). */
kc_chan_t *kc_chan_metrics_pipe(kc_chan_t *ch);

#ifdef __cplusplus
}
#endif
