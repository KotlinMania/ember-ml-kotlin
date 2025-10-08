# Unified Token Queue + Arena Architecture Tasks

This note captures the concrete architectural work required to finish the "one true path" channel stack with the MLX-inspired arena.

## 1. Arena-Backed Descriptor Layer *(in progress)*
- ✅ `kc_desc` now keeps arena metadata (owner flag, arena id/len) and rendezvous pointer paths consume descriptors only.
- ⏳ Replace direct `malloc` in `kc_desc_make_copy` with `kc_arena_alloc` for byte payloads (currently placeholder wrapper).
- ⏳ Add checksum/generation fields so stale tickets are rejected during hydrate once worker loop lands.

## 2. Channel Integration (All Kinds)
- ✅ Rendezvous + buffered/unlimited pointer channels use descriptor queues (`kc_chan_send_ptr/_recv_ptr`, select paths updated).
- ✅ Byte channels now route through arena-backed descriptors in send/recv/select paths.
- ✅ Zero-copy (zref): bound the arena-backed backend and re-enabled descriptor APIs.

## 3. Token Kernel Worker Loop
- ✅ Introduced a dedicated worker thread that drains token events instead of invoking callbacks inline.
- ✅ Added a ready queue between channel wake sites and the worker so rendezvous hot paths stay short (currently mutex/cond; revisit for lock-free variant later).
- ⏳ Extend the worker to handle timeouts, cancellations, and arena compaction triggers.

## 4. Instrumentation & Tooling
- ✅ `kc_chan_snapshot()` now surfaces core counters (success/failure/bytes/zref); arena depth/backlog metrics remain TODO.
- Teach chanmon/bench harness to read the new fields and emit BizTalk-style queue diagnostics.
- Add an admin dump (`kc_arena_dump()` or chanmon command) that lists live tickets, page chains, and refcounts.

## 5. Persistence & Spill Hooks
- Layer an optional write-ahead log behind the arena allocator for crash recovery experiments.
- Support pluggable spill targets (shared memory, file-backed mmap) when RAM pressure exceeds thresholds.
- Document durability guarantees and how tooling replays the log to rehydrate pending tickets.

## 6. Bench & Test Coverage
- Rebuild buffered/unlimited latency + throughput benches on the unified path (no legacy waiters).
- Add unit tests that assert descriptor retain/release on send/recv, select win, cancel, close, and timeout.
- Stress-test arena compaction with mixed small/large payloads to watch for fragmentation regressions.

---
**Status:** Pointer/byte channels ride arena-backed descriptors; zref backend, worker loop, and baseline snapshots are online; chanmon tooling and advanced worker features remain.
