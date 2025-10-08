# Token Kernel Integration Journal — 2025-10-06

Tonight was all about proof, not polish. We set out to thread the BizTalk-style correlation engine into kcoro and, in the process, we touched nearly every sharp edge of the runtime. Here’s the unvarnished story so far.

## Early sketches and false starts

1. **Token VM everywhere (first attempt).**
   - The plan was seductive: replace `kc_ctx_switch.S` with the token interpreter, measure the 11-cycle resumes, and never look back.
   - It “worked” in microbench; our 5M-iteration loop lit up the profiler with numbers we wanted to believe.
   - Reality: the moment we parked a real coroutine (`kcoro_park()`), we returned to garbage. `lab_simple_park_demo` crashed before printing “after park.” We were saving the caller’s LR from inside `kcoro_switch` and then returning to ourselves. Instant infinite tail recursion.
   - Conclusion: microbench throughput is meaningless if basic semantics fail. We parked the token VM switch for later and rolled back to the known-good assembly until we could prove correctness.

2. **Token VM apply stub edits.**
   - Repeated edits to `_kc_vm_apply` (move `sp`, load registers, `blr x1`, etc.). Every small tweak improved something but not enough. The crash persisted because the continuation pointer was still wrong.

3. **Breakpoint archaeology with lldb.**
   - Break on `_kc_vm_apply`, dump registers, realize LR and SP were being restored, but the return address kept pointing at `kcoro_switch` instead of the caller.
   - Attempted to read back `to_co->reg[13]` (LR) from LLDB and even simple `memory read -f A` commands failed—because we had momentarily “captured” a pointer that didn’t exist.

4. **`__builtin_return_address(1)` experiment.**
   - Tried to ask the compiler for the caller’s caller. Clang flagged it as unsafe (`-Wframe-address`). Makes sense: the optimizer may inline or tail-call-away the frames. Dropped the idea.

5. **Manual LR capture via inline asm.**
   - New idea: read LR in `kc_ctx_switch_vm.c` with `asm("mov %0, lr")`. That gave us a deterministic pointer to the call site. Still, the real issue remained: parking without a scheduler meant we had to deal with “returning to main” ourselves.

## Token kernel scaffolding

6. **Freelist + correlation table.**
   - Introduced `kc_token_kernel.c` with: (a) atomic counter for IDs, (b) bucketed hash table, (c) freelist guarded by a mutex for now. State machine: `NEW -> PARKED -> READY -> COMPLETED`.
   - Added coroutine payload fields (`token_payload_ptr`, `token_payload_len`, `status`) and a flag `token_payload_ready` so resumed coroutines can pull the event data.

7. **Callback path.**
   - `kc_token_kernel_callback(ticket, payload)` stores payload into the parked coroutine, sets the flag, and calls `kcoro_unpark(co)`; on cancel we mark status and also `kcoro_unpark`.

8. **Demo harness.**
   - `lab_token_kernel_demo`: coroutine publishes a ticket, parks, we simulate a callback, and the coroutine prints the payload and status. Great confirmation that the kernel control plane works, even before changing channels.
   - `lab_simple_park_demo`: sanity test to make sure `kcoro_resume`/`kcoro_park` still work. After reverting to assembly for the switch, this demo now prints all expected lines.

9. **Token kernel numbers (for the record).**
   - Token resume microbench (with interpreter) still hit ~109M resumes/sec (~9.1 cycles). Once the switch is stable, those numbers will be meaningful.

## Context switch post-mortem

10. **Why the crashes happened.**
    - `kc_vm_capture` saved LR into `reg[13]`, but `kcoro_switch` never populated it with the caller’s LR—only with the internal helper. Restoring those values spiraled back into `kcoro_switch` forever.
    - After reverting to the original assembly, determinism returned. To resurrect the token switch, we’ll need to capture LR before the helper call so that `reg[13]` points to the call site in `kcoro_resume`.

11. **Rolling back for stability.**
    - Removed `KC_VM_SWITCH` flag from the mirror build and resumed using `arch/aarch64/kc_ctx_switch.S`. Token-based switch files remain in the tree for future iteration once the functional work is done.

## What’s next (strictly without token switch)

12. **Integrate callback path into channels.**
    - Hook `kc_chan_send_ptr`, `kc_chan_recv_ptr`, and select clause helpers to publish tickets and park immediately instead of enqueuing waiters that require scheduler threads.

13. **Resume from callbacks.**
    - Teach channel wake paths to invoke `kc_token_kernel_callback(ticket, payload)` so parked coroutines wake via `kcoro_unpark` exactly like the demo.

14. **Benchmark harness.**
    - Update `bench_chan_metrics` to use the async API and re-run the throughput tests. Expect CPU near idle during waits—no spins, no worker threads.

15. **Reintroduce token switch (when ready).**
    - Once the channel flow is stable, revisit the token-based `kcoro_switch`. Capture LR via inline asm, write it to the coroutine’s register file, and have `kc_vm_apply` branch to it. Gate the experimental path behind a feature flag so we can compare both.

## Notes and observations

16. **LLDB tips gathered along the way:**
    - `register read x1` on AArch64 to check continuations.
    - `bt` after each `process continue` to confirm stack trails.
    - `expr *(void**)((uint64_t)$x0 + 0x68)` fails if the pointer is invalid—use `memory read` carefully.

17. **Plain assembly works (again).**
    - After the rollback, `lab_simple_park_demo` prints:
      ```
      [main] resuming
      [co] before park
      [main] first resume return
      [co] after park
      [main] second resume return
      ```
      That confirms the default behavior is intact.

18. **Token kernel demo output now:**
      ```
      [main] initializing token kernel...
      [main] first resume
      [coroutine] published ticket id=1, parking...
      [main] returned from first resume? (should not happen)
      [main] coroutine parked with ticket id=1
      [main] resuming coroutine after callback
      [coroutine] resumed rc=0 payload_ptr=0x... len=2 status=0
      [main] returned from resume
      ```
      The “returned from first resume?” line is expected in the absence of a scheduler we haven’t written yet. Once callbacks go through the channel code proper, this will become the new handshake point.

19. **Here’s what matters:**
    - Microbench wins mean nothing unless functional tests pass.
    - Running crash repros in LLDB (`b kc_vm_apply`, `register read`, `expr`) is invaluable.
    - Going deep (like tonight) fixes core issues permanently; the eventual token switch will be all the better for it.

---

That’s tonight’s brain dump. Next entry should capture the channel callback rotary, minimal scheduler removal, and the first meaningful benchmarks under the zero-spin design. Stay tuned.

## 2025-10-06 — Late-Night Follow Up

- Hardened `kc_token_kernel_global_init()` so the `initialized` flag only flips to ready *after* the freelist and hash buckets are built. Previous logic marked the kernel as initialized during setup, letting a racing thread dereference `g_kernel.buckets == NULL`. The new three-state flag (UNINITIALIZED → IN_PROGRESS → READY) spins on `sched_yield()` until the allocator finishes or retries.
- The shutdown path now checks for READY explicitly and resets the flag with release ordering, allowing subsequent initializations if the lab tears the kernel down between demos.
- Re-ran the usual harness (`make -C external/kcoro/lab/mirror/core all`, `lab_simple_park_demo`, `lab_token_kernel_demo`) to confirm the rendezvous path still wakes correctly after the memory-ordering tweak.

Next: wire `kc_chan_select_register_{recv,send}` into the ticket queues so select clauses ride the same callback flow without touching the old waiter lists.

## 2025-10-06 — Select Integration Pass 1

- Introduced select-aware pending nodes in `kc_chan.c`. Rendezvous pointer sends/receives now store select clauses in the token queues instead of the legacy waiter lists, but they still complete by calling `kc_select_try_complete` before dispatching the callback.
- Pending nodes carry either a published ticket (direct coroutine) or a `(kc_select_t*, clause)` pair (select). The send/recv fast paths branch accordingly: direct waiters still wake via `kc_token_kernel_callback`; select clauses copy into the clause buffer, complete the select, and queue the waiter through `kc_chan_schedule_wake`.
- Updated cancellation/close paths to scrub the new queues and invoke `kc_token_kernel_cancel` only for entries that actually published tickets. Select clauses (ticket id 0) are simply freed and bump rendezvous cancel counters.
- `kc_chan_select_register_{recv,send}` now appends rendezvous pointer clauses to the pending queues rather than the wait lists, keeping the coroutine parked with zero spinning.
- Rebuilt the mirror core and reran `lab_simple_park_demo` / `lab_token_kernel_demo` to confirm no regressions.

Next: extend the same pending-path logic to buffered modes (or gracefully fall back), then adapt the benchmarks to exercise the callback-driven rendezvous.

## 2025-10-06 — MLX-Style Arena Plan

- Switch channel payloads to arena-backed descriptors. Each token ticket points at a block descriptor (`base_ptr`, `len`, `epoch`, refcount) in a shared arena; no in-flight copies.
- Send path records the descriptor, increments its refcount, and enqueues the ticket. Receiver wakes with the same descriptor and calls `kc_token_kernel_consume_payload()` -> `kc_payload_id` (refcount-aware).
- Cancellation/timeout just decrements the refcount and enqueues a canceled status; close drains queues and releases remaining descriptors.
- Add explicit `retain/release` APIs so callers can forward descriptors or hold them past the receive call; once all refs drop, the arena recycles the block (epochs + background sweep).
- Integrate IPC: descriptors serialize the shared-memory handle, offset, and length; remote ACK triggers `release_payload()` on both sides to keep pressure bounded.
- Build the arena/descriptor layer first, then retrofit rendezvous pointer channels to pass descriptors instead of copying `kc_chan_ptrmsg`.
- After pointer channels work, extend the same descriptors to zero-copy (zdesc) and buffered/unlimited modes so every blocking path is ticket-based with zero copies.

Next: design the arena metadata tables (block headers, free lists), wire the descriptor struct into `kc_token_kernel_callback`, and replace the pending queue payload fields with descriptor IDs.

## 2025-10-07 — Pending-Queue Rewrite (Lab Mirror)

- Replaced the waiter/condvar scaffolding in `kc_chan_internal.h` with `kc_pending_{send,recv}` structures that carry role, kind, ticket, and select metadata. `struct kc_chan` now tracks only token-backed queues.
- Nuked the legacy waiter lists from the lab mirror `kc_chan.c` and rebuilt rendezvous pointer send/recv/select on top of the pending queues. `kc_token_kernel_publish_{send,recv}` is now the only blocking path; callbacks deliver straight through `fulfill_coroutine_*` or select helpers.
- Stubbed buffered/unlimited/byte/zref paths with explicit `-ENOTSUP` so no one accidentally uses the half-migrated logic; metrics and zero-copy APIs currently return empty data. Lab demos still pass, confirming the rendezvous path remains healthy.
- Added a temporary placeholder `kc_zcopy.c` that returns `-ENOTSUP` while we slot in the arena-backed descriptors. Restored lab builds (`make -C external/kcoro/lab/mirror/core all`) and reran the demos to validate the new single-path rendezvous.

**Next steps**
1. Implement the arena allocator + ticket retain/release (`ARENA_ARCH_PLAN.md` #1) and swap the pending queue payload fields to descriptor IDs.
2. Bring buffered/unlimited/conflated send/recv (and their select clauses) onto the same pending queues so the `-ENOTSUP` stubs disappear (#2).
3. Layer the token-kernel worker loop and expose arena/queue metrics to chanmon (#3/#4). 

## 2025-10-07 — Rendezvous on Descriptors

- Added the shared descriptor table (`kc_desc.{c,h}`) with refcounts, alias/copy helpers, and hooked it into the lab build.
- Extended `kc_payload`, `kcoro_t`, and `kc_token_kernel` so token callbacks carry `desc_id` alongside `ptr/len/status` and the consumer releases the descriptor deterministically.
- Reworked rendezvous pointer send/recv + select clauses to hand out descriptor IDs instead of raw structs; pending nodes now store `desc_id`, and close/cancel paths release refs rather than touching legacy buffers.
- Removed the last waiter-era copies (slot buffers, pending `struct kc_chan_ptrmsg` scratch) from the lab mirror: every rendezvous wake now resolves through `kc_desc_payload()` and token callbacks.
- Lab demos rebuilt (`make -C external/kcoro/lab/mirror/core all`, `lab_simple_park_demo`, `lab_token_kernel_demo`) to verify the descriptor-backed rendezvous path.

**Next**: port buffered/unlimited pointer modes onto the same descriptor queues, then tackle the arena allocator for byte/zref payloads and the token-worker loop.

## 2025-10-08 — Pointer Channels Unified; Arena Skeleton

- Buffered/unlimited/conflated pointer channels now store descriptor IDs (`ring_descs` for rings, `rv_slot_desc` for conflated) and share the same pending-queue wake paths; select/cancel/close release refs consistently across kinds.
- Added a minimal `kc_arena` wrapper and taught `kc_desc` to track arena-backed allocations, prepping for byte/zref descriptors.
- Build & lab demos stay green on the refactored pointer pipeline (`make -C external/kcoro/lab/mirror/core all`; lab demos).

Next: swap byte-channel send/recv stubs for arena-backed descriptors, then re-enable the zero-copy backend on the unified flow.

## 2025-10-08 — Byte Channels on the Descriptor Path

- Replaced `kc_chan_send/_recv` stubs so rendezvous, buffered/unlimited, and conflated byte channels publish arena-backed descriptors into the same pending queues/rings, copying back into user buffers only when returning to callers.
- Select clauses for byte channels now hydrate from descriptors just like pointer clauses; close/cancel paths release refs consistently.
- Updated `ARENA_ARCH_PLAN.md` to reflect pointer + byte integration; zero-copy backend wiring remains ahead.

Next: wire up the zref backend (kc_zcopy.c) and plumb `kc_chan_send_zref/_recv_zref` through the unified descriptor flow, then tackle the token worker loop/metrics.

## 2025-10-08 — Zref Backend Restored

- Implemented a minimal in-process `zref` backend in `kc_zcopy.c`: it binds automatically via `kc_chan_enable_zero_copy`, and `kc_chan_send_zref/_recv_zref` now wrap the descriptor pipeline instead of returning `-ENOTSUP`.
- The backend aliases descriptors when the arena owns the copy and forwards foreign pointers as zero-copy references, so rendezvous + buffered paths share one surface. Byte channels still copy into arena storage before handing descriptors out.
- Updated the arena plan to mark the zero-copy milestone as complete; the remaining TODO is richer lifecycle/metrics once the worker loop lands.

Next: finish the token kernel worker so callbacks drain off-thread, then surface queue metrics to chanmon.

## 2025-10-08 — Token Worker Thread Online

- Replaced the inline `kc_token_kernel_callback` wake with a dedicated worker thread. Callbacks now enqueue `kc_token_block`s onto a guarded ready queue; the worker hydrates payload fields, unparks the owning coroutine, and returns the block to the freelist.
- Cancellations feed the same queue, ensuring that every consumer sees results through the same ordering point. Descriptor releases still happen at cancel time so the worker never touches stale ids.
- Hardened init/shutdown: the worker launches during `kc_token_kernel_global_init()`, a stop flag wakes it during shutdown, and we join the thread before destroying queues/buckets/freelists.
- Left a TODO hook for `blk->resume_pc`; interpreter jump support will plug in once the coroutine runtime exposes the entry point.

Next: expose worker/arena metrics to chanmon, add stress tests that exercise mixed success/cancel flows, and revisit the ready queue for a lock-free variant once correctness is locked down.

## 2025-10-08 — Snapshot & Zstats Restored

- `kc_chan_snapshot()` now exposes the real counters (sends/recvs/bytes/failures/zref) for every channel kind; `kc_chan_compute_rate()` mirrors the production implementation and `kc_chan_get_zstats()` reports zref usage.
- The mirror tests assert the metrics again: failure counters, buffered pointer throughput, rendezvous pointer totals, and zero-copy close semantics now validate the live stats instead of skipping.
- Pointer rendezvous coverage no longer depends on the default scheduler; the test drives producer/consumer coroutines directly with `kcoro_create` so the token worker handshake is exercised deterministically.

**Still ahead**
- Fold the new snapshot fields into chanmon/bench output (queue depth, tokens outstanding once the worker exposes backlog).
- Extend the stats to include arena depth / spill counters once the allocator grows past the stub.
- Add stress coverage for zero-copy fallback paths (small payload copies, capacity exhaustion) so the zref counters stay honest.

## 2025-10-09 — Instrumentation Reality Check

- What’s working: core APIs now report real counters; unit tests cover rendezvous, buffered pointer, failure deltas, and zref send/recv. The token worker and descriptor flow are resilient under the lab suite.
- What isn’t done: chanmon and bench still default to buffered pointer workloads, so rendezvous/zref counters stay at zero in those live runs. Arena backlog / spill telemetry remains unimplemented, and we have no stress that mixes zero-copy success + fallback + cancel to keep the numbers honest.
- Next actions: add rendezvous/zero-copy modes to the bench + monitor, plumb arena/ticket depth once the allocator lands, and build targeted stress tests for zero-copy fallback. Until then, treat the synthetic Gb/s figures as lab throughput only.

### What I Struggled With

- **Tooling drift:** Wiring `kc_chan_snapshot()` back up was easy; discovering chanmon/bench were glued to buffered pointer flows was not. Every time I expected live rendezvous numbers, I ran into hidden assumptions—scheduler dependence, Linux-only helpers, missing benchmark switches. Each fix uncovered another layer I hadn’t budgeted for.
- **Zero-copy fallbacks:** Counting aliasing is trivial; counting “almost zero-copy” isn’t. The moment I tried to track small-payload fallbacks or cancellations cleanly, the code threatened to devolve into flags and special cases. I still owe us counters + stress that hit those paths so we know the numbers aren’t lies.
- **Arena telemetry:** I’ve been talking about BizTalk-style visibility for weeks, but the allocator still isn’t exposing depth/backlog. Every attempt to wire it into chanmon ended with “we don’t actually track that yet.” It’s on me to stop trying to paper over it and instead finish the allocator work.

I’m leaving this here as a reminder: no more leaning on synthetic runs or buffered shortcuts when we talk about observability. Until the tooling exercises rendezvous + zero-copy for real, I’ll call it out explicitly.
