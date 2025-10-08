# KCoro Ready-Queue Root Cause Analysis

## Context
- Crash during headless monitor (`kcoro_mon -H -d N`) with inconsistent tx/rx counters.
- Sanitizer reports showed frees on non-malloc’d addresses and UAF in `kc_sched.c`.

## Instrumentation Steps
1. **Sanitised rebuild** of `vendor/kcoro/user` and `kcoro_mon` with `-fsanitize=address`.
2. Created minimalist headless harness (`asan_chan_stress.c`) to sanity-check general coroutine paths (it ran clean; issue localized to monitor).
3. Added `KC_SCHED_DEBUG` logging hooks to `kc_sched.c` to trace enqueue/dequeue lifetimes.
4. Captured monitor logs:
   - `KC_SCHED_DEBUG=1 ./build/kcoro_mon -H -d 2 > /tmp/kcoro_mon_debug.log`
   - Observed the same queue node pointer freed by one worker while another still ran `tail->next = n`.

## Findings
- Ready queue used heap-allocated `struct sched_ready` nodes. After pop, the node was freed immediately.
- Another worker thread still held `rq_tail` pointing to the freed memory; first coroutine resumed would trigger a write into recycled memory.
- Allocator reused the slab for coroutine stacks, explaining ASan reports of `free()` on unmapped stack addresses.

## Fix Implementation
- Converted ready queue to an intrusive list on `kcoro_t->next`; no heap nodes, no double-touching freed memory.
- Clarified owning scheduler on spawn and cleaned up remaining coroutines during shutdown.
- Hardened `kcoro_resume` so context switch always has a valid "from" coroutine (fallback to `main_co`).
- Retained `KC_SCHED_DEBUG` (env var) for future tracing.

## Validation
- `KC_SCHED_DEBUG=1 ./build/kcoro_mon -H -d 10`: no ASan reports, logs show unique coroutine addresses reused cleanly.
- `vendor/kcoro/tests/asan_chan_stress`: still passes.
- Commit `793f490` documents the final fix.

## Next Steps
- Merge upstream; remove temporary harness if not needed.
- Monitor the other fork for the same intrusive queue fix.
