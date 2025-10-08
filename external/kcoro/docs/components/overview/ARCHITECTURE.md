# System Architecture

This document summarizes kcoro's system architecture and project layout.

## High-level Diagram

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ External        │    │   IPC Bridge    │    │   User-space    │
│ Adapter         │────▶│  (POSIX, C)    │────▶│   Processing    │
│ (Optional)      │    │  TLV Protocol   │    │   (kcoro)       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Adapter Ring   │    │  Distributed    │    │   Coroutines    │
│  (Optional)     │    │  Channels       │    │   + Channels    │
│  (Out‑of‑tree)  │    │  (kcoro IPC)    │    │   (kcoro)       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Components

- External adapters (optional): out‑of‑tree producers/consumers that live in separate repositories to preserve licensing boundaries.
- IPC Layer: POSIX socket‑based bridge in C with a TLV protocol (reference implementation in `ipc/posix/`).
- User Space: kcoro coroutines implement concurrent processing pipelines.

## Project Layout

### Core Library (BSD License)
- `include/` — Public API headers (BSD)
- `include/kcoro_port.h` — Porting contract; selects port header (default: POSIX)
- `port/posix.h` — Reference POSIX port (pthread/condvar)
- `user/` — coroutine runtime, channel/actor implementations, scheduler (M:N on pthread workers)
- `arch/` — architecture-specific context switchers (ARM64 currently implemented)
- `proto/` — Transport-agnostic protocol headers (BSD)

### Distributed IPC System
- `ipc/posix/` — POSIX socket-based distributed channels

### External Adapters
- Optional kernel‑space or user‑space adapters may provide data sources/sinks. These are not part of kcoro core and must keep their own licenses.

### Implementation reference

#### Scope
- This document explains how kcoro works, end‑to‑end, as it is implemented today. It is self‑contained and avoids references to any external runtime. The core is strictly ANSI C + POSIX, portable, and BSD‑licensed.

API prototypes (canonical, excerpted from headers)
- int  kc_chan_make(kc_chan_t **out, int kind, size_t elem_sz, size_t capacity);
- int  kc_chan_send(kc_chan_t *ch, const void *msg, long timeout_ms);
- int  kc_chan_recv(kc_chan_t *ch, void *out, long timeout_ms);
- void kc_chan_close(kc_chan_t *ch);

#### Design Tenets
- Cooperative, explicit: coroutines yield control; the scheduler times nothing preemptively.
- Single, canonical surface: channels and zero‑copy use one descriptor API; counters are exact.
- Always portable: no platform‑specific headers in core; a thin port layer provides mutex/cond/time.
- Observability first: every successful op updates counters once; rate math is centralized.

#### Glossary (quick)
- Coroutine: user‑space fiber scheduled cooperatively.
- Task: runnable unit bound to a scheduler worker; coroutines run as tasks.
- Channel: typed queue with four kinds (RENDEZVOUS, BUFFERED, CONFLATED, UNLIMITED).
- zref: unified zero‑copy backend that hands off pointer descriptors without copying payloads.

## Repository summary

kcoro — Coroutine Channels and Actors for High-Performance Computing (BSD)

kcoro is a compact, high-performance C coroutine runtime providing channels, actors, and zero-copy descriptors. The core is strictly ANSI/POSIX and OS‑neutral. Optional adapters (kept out‑of‑tree) may feed or consume channel traffic, but they are not part of the kcoro core.

Status: alpha (API subject to change)

### Highlights
- Zero‑copy surface unified behind a neutral descriptor + backend factory with a single built‑in “zref” backend. Pointer mode covers all channel kinds; zref covers rendezvous and queued descriptors. Per‑op counters update bytes/ops identically across paths.
- `kcoro_chanmon` (TUI) reads `kc_chan_snapshot` + `kc_chan_compute_rate` and reports PPS/GBPS derived from true byte counts.

