// SPDX-License-Identifier: BSD-3-Clause
#pragma once
/*
 * kcoro_zcopy.h - Optional Zero-Copy & Region Abstractions (Neutral Layer)
 * -----------------------------------------------------------------------
 * This header intentionally avoids including or referencing any non‑BSD‑compatible
 * licenses or platform‑specific kernel/networking headers. It exposes a minimal,
 * portable interface the runtime
 * uses to provide zero-copy channel operations (zref) and future shared memory
 * region registration.
 *
 * Integrations (custom DMA pools, shared memory segments, user‑space memory arenas)
 * adapt by registering regions or by directly invoking kc_chan_send_zref with
 * a (ptr,len) pair. The kcoro core will not infer provenance or attempt to
 * free memory; ownership and recycling semantics are up to the integration.
 *
 * Licensing Boundary:
 *  - This file is BSD-3-Clause.
 *  - External adapters that depend on platform-specific facilities must reside
 *    in separate translation units and may be licensed differently. Only the
 *    neutral pointer handoff crosses into kcoro.
 */

/*
 * -----------------------------------------------------------------------------
 * Header Surface & Optional Items (Read Me First)
 * -----------------------------------------------------------------------------
 * Purpose
 *   This header exposes the public, portable surface for zero‑copy operations
 *   (zref) and future region registration. It is self‑contained and free of
 *   platform headers. The core library ships a single unified backend named
 *   "zref" that implements both rendezvous hand‑to‑hand and queued descriptor
 *   paths.
 *
 * Compatibility convenience macro
 *   Re‑export of KC_CHAN_CAP_ZERO_COPY
 *      - What: a convenience re‑export so users who include only this header
 *        can feature‑detect zref support without also including kcoro.h.
 *      - Core usage: duplicates the define in kcoro.h, guarded by #ifndef to
 *        avoid redefinition.
 *      - Production policy: Retaining the re‑export reduces include friction.
 *        If you want a single source of truth, include kcoro.h and remove the
 *        re‑export here.
 *
 * Install surface guidance
 *   This header is intended to be part of the installed public API set. It does
 *   not depend on lab/tooling headers and avoids OS‑specific language.
 *
 * Reader’s map
 *   - @defgroup kcoro_zcopy introduces the portable surface and conventions.
 *   - kc_zdesc is the common descriptor; addr+len today, region fields reserved.
 *   - kc_zcopy_backend_ops is the minimal vtable for pluggable backends.
 *   - kc_chan_send_desc / kc_chan_recv_desc are the canonical APIs for zcopy.
 */

#include <stddef.h>
#include <stdint.h>
#include "kcoro.h" /* bring in core types */

#ifdef __cplusplus
extern "C" {
#endif

/* Region opaque forward declaration duplicates kcoro.h to allow use without
 * pulling entire kcoro.h if desired. */
struct kc_region; /* forward */

typedef struct kc_region kc_region_t;

/* Re-export capability bits for convenience when only this header is included. */
/**
 * Capability bit: channel supports zero‑copy (zref) operations.
 * Re‑exported for convenience so zcopy users can feature‑detect without
 * including kcoro.h. Equivalent to the definition in kcoro.h.
 */
#ifndef KC_CHAN_CAP_ZERO_COPY
#define KC_CHAN_CAP_ZERO_COPY (1u<<0)
#endif

/* Helper inline: returns non-zero if channel supports zero-copy. */
static inline int kc_chan_is_zero_copy(kc_chan_t *ch) {
    return (kc_chan_capabilities(ch) & KC_CHAN_CAP_ZERO_COPY) != 0;
}

/* Heuristic helper removed: callers should implement their own size threshold
 * policy if they wish to switch between copy and zero‑copy locally. */

/* ========================= Zero-Copy Backend Factory ===================== */
/**
 * @defgroup kcoro_zcopy Zero-Copy (zref) Surface
 * Portable descriptor‑based APIs and a small backend vtable. The core ships a
 * single unified backend named "zref" which implements both rendezvous
 * hand‑to‑hand and queued pointer‑descriptor paths. External adapters can
 * register their own backends out‑of‑tree without pulling platform headers
 * into kcoro.
 *
 * @section zref_overview What is “zref”?
 * zref is our zero‑copy rendezvous protocol and its generalisation. In the
 * rendezvous kind, sender and receiver meet in the middle: the sender publishes
 * a (ptr,len) without copying the payload, the receiver observes the same
 * pointer, and only then do both sides continue. In queued kinds, zref falls
 * back to copying only the small descriptor into a ring — payload memory is
 * never owned by the channel. That keeps the hot path singular while scaling to
 * buffered/unlimited/conflated modes.
 *
 * Key properties (for practitioners)
 * - Ownership: the producer owns payload memory throughout; the channel never
 *   frees user buffers.
 * - Accounting: bytes come from the descriptor length on every successful op;
 *   counters and rates remain exact regardless of backend.
 * - Cancellation/Timeout: descriptor APIs conform to the library’s contract
 *   (0 on success; negative KC_* on error) with consistent try/infinite/bounded
 *   semantics.
 * - Pluggability: backends are swappable at runtime via a small registry; kcoro
 *   remains a pure user‑mode library with no kernel dependencies.
 *
 * Conventions
 * - Return 0 on success; negative KC_* (mapped to -errno) on failure.
 * - Timeouts: timeout_ms == 0 → try; < 0 → infinite; > 0 → bounded wait.
 * - Cancellation: _c variants return KC_ECANCELED when the token is set.
 * - Bytes accounted from kc_zdesc::len on every successful op.
 */

/**
 * @brief Common zero-copy descriptor.
 *
 * Today only addr + len are required. Region fields are reserved for future
 * shared-memory or adapter backends and remain inert in the unified "zref"
 * backend.
 */
typedef struct kc_zdesc {
    void       *addr;      /* optional fast-path local address */
    size_t      len;       /* payload length */
    uint64_t    region_id; /* optional: opaque region identifier */
    uint64_t    offset;    /* optional: offset within region */
    uint32_t    flags;     /* reserved for future use */
} kc_zdesc_t;

/**
 * @brief Backend vtable for zero-copy operations.
 *
 * The channel router ensures per-op statistics are updated by calling shared
 * helpers. Implementations must not free payload memory; ownership remains
 * with the caller/integration.
 */
typedef struct kc_zcopy_backend_ops {
    int  (*attach)(kc_chan_t *ch, const void *opts); /* set up per-channel state */
    void (*detach)(kc_chan_t *ch);                   /* tear down per-channel state */
    int  (*send)(kc_chan_t *ch, const kc_zdesc_t *d, long tmo_ms);
    int  (*recv)(kc_chan_t *ch, kc_zdesc_t *d, long tmo_ms);
    int  (*send_c)(kc_chan_t *ch, const kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct);
    int  (*recv_c)(kc_chan_t *ch, kc_zdesc_t *d, long tmo_ms, const kc_cancel_t *ct);
} kc_zcopy_backend_ops_t;

/** Small integer key assigned at registration. */
typedef int kc_zcopy_backend_id;

/**
 * @brief Register a backend under a stable name.
 * @param name backend name (e.g., "zref")
 * @param ops  vtable of operations (non-null)
 * @param caps capability bits (KC_CHAN_CAP_ZERO_COPY, etc.)
 * @return backend id (>=0) or negative KC_* on error
 */
kc_zcopy_backend_id kc_zcopy_register(const char *name,
                                      const kc_zcopy_backend_ops_t *ops,
                                      uint32_t caps);

/**
 * @brief Resolve backend id by name.
 * @return backend id (>=0) or negative KC_* if not found/invalid.
 */
kc_zcopy_backend_id kc_zcopy_resolve(const char *name);

/**
 * @brief Enable a registered backend on a channel.
 * Binds the vtable and sets KC_CHAN_CAP_ZERO_COPY on the channel.
 * @param ch  channel handle
 * @param id  backend id returned by kc_zcopy_register/resolve
 * @param opts optional pointer to backend-specific per-channel options
 * @return 0 on success; negative KC_* on error
 */
int kc_chan_enable_zero_copy_backend(kc_chan_t *ch,
                                     kc_zcopy_backend_id id,
                                     const void *opts);

/**
 * @brief Send/receive using a descriptor (canonical zcopy API).
 *
 * These route to the active backend when `ch` has a backend bound (zc_ops),
 * otherwise return -ENOTSUP. On success they update channel byte/ops counters.
 * @return 0 on success; negative KC_* on error (KC_EAGAIN, KC_ETIME, KC_EPIPE).
 */
int kc_chan_send_desc(kc_chan_t *ch, const kc_zdesc_t *d, long timeout_ms);
int kc_chan_recv_desc(kc_chan_t *ch, kc_zdesc_t *d, long timeout_ms);
/** Cancellable variants; return KC_ECANCELED if token is set. */
int kc_chan_send_desc_c(kc_chan_t *ch, const kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct);
int kc_chan_recv_desc_c(kc_chan_t *ch, kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct);


#ifdef __cplusplus
}
#endif
