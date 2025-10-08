• Zero-Spin Coroutine Kernel – Engineering Design Specification (Draft v0.1)
  Targeted toward kcoro token-VM runtime redesign (BizTalk-style correlation engine)

  ———

  ### 0.  Purpose & Goals

  - Primary Objective: Replace the existing work-stealing scheduler with a zero-spin, callback-driven coroutine kernel that runs atop the token
    interpreter.
  - Core Concepts:
      - Parked coroutines are represented by token blocks stored in shared memory.
      - Channels act as correlation hubs: each send/receive generates tickets (correlation IDs) that callbacks use to resume parked coroutines.
      - No thread pool manages ready queues; each OS thread hosts a lightweight micro-kernel (token interpreter + channel ops). Any thread can
        hand off work by publishing token blocks.
      - Zero spinning / zero busy-wait: threads block only via kernel sleeps or user-level callbacks; no loops around condition variables or
        atomic spins.
  - Secondary Objectives:
      - Integrate seamlessly with existing kcoro token VM (token streams + register resume stub).
      - Retain channel semantics (buffered, rendezvous, zero-copy) while eliminating scheduler-induced context switches.
      - Guarantee deterministic, low-overhead resume for I/O-bound workloads (CPU near idle during waits).
      - Provide capacity for multiple OS threads to participate, without central scheduling.

  ———

  ### 1.  Architectural Overview

  1. Micro-Kernel per Thread
      - Each participating OS thread (T) executes a loop:

        while (true):
            token = kernel_queue[T].pop_ready_token()
            if token:
                run_token_stream(token)
                recycle_token(token)
            else:
                sleep_until_callback()   // blocking wait (epoll, kqueue, futex, etc.)
      - run_token_stream calls the token interpreter (kc_vm_execute + kc_vm_apply).
  2. Shared Correlation Table
      - Data structure mapping CorrelationID -> TokenBlock.
      - Each entry contains:

        struct TokenBlock {
            uint64_t correlation_id;
            uint64_t channel_id;
            TokenStream stream;
            RegisterFile regs;
            PayloadDescriptor payload;      // for zero-copy pointer, arena ticket, etc.
            TokenState state;               // { PARKED, READY, COMPLETED }
            AtomicPointer<ThreadQueue> home_queue; // optional hint for locality
        };
      - Allocated from lock-free freelist (Treiber stack) per channel group.
  3. Channels as Coordination Primitives
      - Send/receive operations push TokenBlocks into correlation table.
      - No waiting loops; senders/receivers park immediately after publishing.
      - Matching counterpart uses channel_resume(ticket_id, payload) to resume.

  ———

  ### 2.  Token Block Lifecycle

                          ┌────────────┐
                          │  NEW BLOCK │
                          └─────┬──────┘
                                │ allocate_from_freelist()
                                ▼
                        ┌───────────────┐
                        │  PARKED STATE │  // stored in correlation table
                        └───────┬───────┘
                                │ callback(ticket_id, payload)
                                ▼
                        ┌────────────────┐
                        │  READY STATE   │  // pushed to target thread queue
                        └───────┬────────┘
                                │ thread runs token VM
                                ▼
                        ┌────────────────┐
                        │ COMPLETED STATE│
                        └───────┬────────┘
                                │ recycle_token()
                                ▼
                          ┌────────────┐
                          │ FREELIST   │
                          └────────────┘

  - Allocation: TokenBlock* block = freelist.pop_or_alloc();
  - Parking: correlation_table.insert(block); block->state = PARKED;
  - Callback: resume_token(block_id, payload) sets state=READY and enqueues.
  - Completion: After interpreter finishes, block is reclaimed: freelist.push(block);.

  ———

  ### 3.  Channel Operations (Zero-Spin)

  #### 3.1 Send (Pointer Mode Example)

  send_ptr(channel, pointer, len):
      block = alloc_token_block()
      block->channel_id   = channel.id
      block->payload.ptr  = pointer
      block->payload.len  = len
      block->regs         = capture_current_registers()
      block->token_stream = SEND_PTR_STREAM      // prebuilt token list
      corr_id = allocate_corr_id(channel)

      correlation_table.insert(corr_id, block)
      park_current_coroutine(block)
      return corr_id

  #### 3.2 Receive

  recv_ptr(channel):
      block = alloc_token_block()
      block->channel_id = channel.id
      block->token_stream = RECV_PTR_STREAM
      corr_id = allocate_corr_id(channel)

      correlation_table.insert(corr_id, block)
      park_current_coroutine(block)
      return corr_id

  #### 3.3 Callback / Resume

  Every counterpart (either another coroutine or an external event) invokes:

  channel_resume(channel, corr_id, payload):
      block = correlation_table.remove(corr_id)
      block->payload = payload

      block->state = READY
      target_thread = select_target_thread(block)
      kernel_queue[target_thread].push(block)
      signal_thread(target_thread)   // wake if sleeping

  select_target_thread can use block->home_queue, channel’s affinity, or work stealing heuristics.

  ———

  ### 4.  Kernel Queue & Waking Strategy

  - Each thread has a KernelQueue (lock-free queue or MPSC).
  - push(block) adds a ready token; the thread is woken via OS primitive (eventfd, futex, pipe).
  - pop_ready_token() returns None if empty.
  - No busy waiting: thread immediately blocks when pop_ready_token() returns null.

  Wake mechanism options:

  enum WakeMechanism {
      WAKE_EVENTFD,
      WAKE_FUTEX,
      WAKE_KQUEUE,
      WAKE_SCHED_YIELD, // fallback
  }

  Implementation detail:

  signal_thread(thread_id):
      if queue_was_empty_before_push:
          eventfd_write(thread_eventfd[thread_id], 1);

  ———

  ### 5.  Lock-Free Memory Management

  #### 5.1 TokenBlock Freelist

  struct FreeNode { TokenBlock* block; FreeNode* next; }

  class LockFreeStack {
      atomic<FreeNode*> head;

      TokenBlock* pop_or_alloc() {
          FreeNode* node = head.load();
          while (node && !head.compare_exchange_weak(node, node->next)) {}
          if (node) return node->block;
          return allocate_new_block();
      }

      void push(TokenBlock* block) {
          auto* node = new FreeNode{block, nullptr};
          FreeNode* old_head = head.load();
          do { node->next = old_head; }
          while (!head.compare_exchange_weak(old_head, node));
      }
  };

  - Reclamation: rely on epoch-based scheme (discussed below) to free FreeNode safely.

  #### 5.2 Epoch-Based Reclamation (EBR)

  - Maintain global epoch G.
  - Each thread has thread_epoch.
  - On pop_or_alloc, set thread_epoch = G.
  - On push, tag block with retire_epoch = G + 2; add to retire_list.
  - Background cleanup: when min(thread_epoch) > retire_epoch, recycle nodes.

  Pseudo:

  advance_global_epoch():
      G += 1

  reclaim_retired():
      for each block in retire_list:
          if block.retire_epoch < min_thread_epoch():
              actual_delete(block)

  ———

  ### 6.  Token Streams for Channel Ops

  Pre-defined token sequences (stored in ROM) illustrate the interpreter’s flow; each block references a stream TokenStreamID.

  #### 6.1 Example: SEND_PTR_STREAM

  Token SEND_PTR_STREAM[] = {
      { KC_OP_LOAD_IMM, KC_REG_PTR, 0, correlation_table_entry_ptr },
      { KC_OP_LOAD_IMM, KC_REG_CONT, 0, kcoro_trampoline },
      { KC_OP_END,      0,          0, resume_continuation_address }
  };

  #### 6.2 Example: RECV_PTR_STREAM

  Token RECV_PTR_STREAM[] = {
      { KC_OP_LOAD_IMM, KC_REG_PTR, 0, correlation_table_entry_ptr },
      { KC_OP_LOAD_MEM, DEST=PAYLOAD_REG, flags=CHANNEL_ID, imm=payload_slot },
      { KC_OP_END,      0, 0, resume_continuation_address }
  };

  When the kernel executes kc_vm_execute, it reads these tokens, sets up registers (regs array), and calls kc_vm_apply.

  ———

  ### 7.  API Sketch (C-Level)

  typedef uint64_t kc_token_id_t;

  typedef struct kc_ticket {
      kc_token_id_t      id;
      struct kc_channel *channel;
  } kc_ticket;

  kc_ticket kc_chan_send_async(kc_channel *ch, void *ptr, size_t len);
  kc_ticket kc_chan_recv_async(kc_channel *ch);

  void kc_chan_callback(kc_ticket ticket, kc_payload payload);

  struct kc_runtime_thread {
      KernelQueue queue;
      Eventfd     wake_fd;
      // statistics, etc.
  };

  void kc_runtime_thread_init(kc_runtime_thread *rt);
  void kc_runtime_thread_loop(kc_runtime_thread *rt);

  ———

  ### 8.  Micro-Kernel Loop Pseudocode

  void kernel_loop(kc_runtime_thread* rt) {
      kc_vm_token token;
      while (!rt->shutdown) {
          if (rt->queue.pop(token)) {
              current_token = token;
              kc_vm_execute(token->stream, token->regs);
              recycle_token(token);
          } else {
              wait_on_eventfd(rt->wake_fd);
          }
      }
  }

  ———

  ### 9.  Channels & Callback Flow

  #### 9.1 Channel Creation

  kc_channel* kc_channel_create(kc_channel_kind kind, size_t capacity) {
      auto* ch = allocate_channel_struct();
      ch->kind = kind;
      ch->capacity = capacity;
      ch->freelist = LockFreeStack();
      ch->rendezvous_table = CorrelationTable();
      ch->io_target = select_io_thread(); // optional
      return ch;
  }

  #### 9.2 Async Send (Zero Copy)

  kc_ticket kc_chan_send_ptr_async(kc_channel* ch, void* ptr, size_t len) {
      TokenBlock* block = ch->freelist.pop_or_alloc();
      block->channel_id = ch->id;
      block->payload.ptr = ptr;
      block->payload.len = len;
      block->token_stream = SEND_PTR_STREAM;
      block->regs[KC_REG_SP] = capture_sp();
      block->regs[KC_REG_FP] = capture_fp();
      block->regs[KC_REG_CONT] = resume_address;

      kc_token_id_t id = ch->rendezvous_table.insert(block);
      park_current_coroutine(block);
      return { id, ch };
  }

  #### 9.3 Async Receive

  kc_ticket kc_chan_recv_ptr_async(kc_channel* ch) {
      TokenBlock* block = ch->freelist.pop_or_alloc();
      block->channel_id = ch->id;
      block->token_stream = RECV_PTR_STREAM;
      block->regs[... set up ...];

      kc_token_id_t id = ch->rendezvous_table.insert(block);
      park_current_coroutine(block);
      return { id, ch };
  }

  #### 9.4 Completion Callback

  void kc_chan_callback(kc_ticket ticket, kc_payload payload) {
      kc_channel* ch = ticket.channel;
      TokenBlock* block = ch->rendezvous_table.remove(ticket.id);
      block->payload = payload;

      block->state = READY;
      auto* target_q = choose_kernel_queue(block);
      if (target_q->push(block)) {
          wake_kernel_queue(target_q);
      }
  }

  choose_kernel_queue can prefer the original thread (captured in block->regs or stored as block->home_queue), or can distribute across a pool.

  ———

  ### 10.  Zero-Copy & Arena Integration

  - Channels in pointer mode store kc_zdesc (arena descriptor).
  - block->payload is union: { pointer/len, zdesc, by-value data }
  - Callback sets block->payload with descriptor; interpreter writes to coroutine’s registers (e.g., x0/x1) to deliver data.
  - Memory is not copied—just references passed back.

  ———

  ### 11.  Select / Multi-Wait Semantics

  - select builds a token block with multiple correlation IDs (one per clause).
  - Each clause registers in correlation_table.
  - The first callback to fire sets the block to READY and cancels other clauses by removing them from the table.
  - Implementation:

    struct SelectBlock {
        TokenBlock base;
        vector<KcTicket> clause_tickets;
        atomic<bool> completed;
    };
  - On callback:

    if (!block->completed.exchange(true)):
        block->payload = payload;
        push_to_queue(block)
        cancel_other_clauses()
    else:
        // already resumed; ignore
  - Cancellation simply removes other correlation entries and recycles their blocks.

  ———

  ### 12.  High-Water Mark & Throttling

  - Shared atomic<uint64_t> high_water_mark[channel_id].
  - Each callback increments completion_counter.
  - Writers check completion_counter - in_flight to enforce capacity (backpressure).
  - No thread sleeps—writers park until callback returns.

  Pseudo:

  if (in_flight >= capacity):
      park_token(block)
  else:
      publish_token()

  in_flight increments when token is parked, decrements when callback completes. When arriving team sees capacity available, they push block;
  otherwise it stays parked.

  ———

  ### 13.  Multi-Thread Participation

  - Thread registration: Each OS thread that wants to participate calls kc_runtime_thread_init.
  - When a coroutine parks, we store the originating thread queue in the block; callback tries to resume on the same queue for locality.
  - If thread is offline, fallback to another queue (round-robin).
  - No central scheduler decides—only micro-kernel queue operations.

  ———

  ### 14.  External IO (Networking, etc.)

  - For epoll/kqueue integration:
      - Register file descriptor with callback: epoll_ctl(fd, EPOLLIN, ticket_id)
      - Event loop thread (maybe the same micro-kernel) receives events, translates them into kc_chan_callback(ticket_id, payload).
      - Again, zero spin: the kernel thread blocks on epoll/kqueue until events appear.

  ———

  ### 15.  Error Handling & Cancellation

  - Ticket may be canceled by calling kc_chan_cancel(ticket).
  - Cancellation removes block from table (if still PARKED) and enqueues it with payload=KC_CANCELLED.
  - Interpreter sees cancellation token and unwinds to coroutine, which raises CancellationException or returns error code.
  - Use double-checked state transitions:

    if (block->state == PARKED && block->state.compare_exchange(PARKED, READY)):
        // cancellation succeeded

  ———

  ### 16.  Metrics & Observability

  - Track in shared counters:
      - tokens_allocated, tokens_recycled.
      - callbacks_invoked, callbacks_late.
      - kernel_sleep_time (measure time spent waiting).
  - Provide debug hooks to dump the correlation table and queue sizes.

  ———

  ### 17.  Security/Correctness Considerations

  - Ensure correlation IDs are 64-bit random or monotonic counters to avoid collisions.
  - Validate channel IDs in callbacks to prevent resume hijacking.
  - Use memory barriers (std::atomic_thread_fence(std::memory_order_release/acquire)) around publishing operations.

  ———

  ### 18.  Backward Compatibility & Migration Plan

  1. Phase 1: Introduce token-based channel ops alongside existing scheduler; feature flag to run in BizTalk mode (KC_BIZTOKEN=1).
  2. Phase 2: Deprecate work-stealing scheduler for pointer channels; redirect everything to token kernel.
  3. Phase 3: Remove worker pool entirely once tests verify behavior under load and the new kernel handles all modes (pointer, buffered, select,
     cancellation).

  ———

  ### 19.  Testing Strategy

  - Unit tests:
      - Single-thread send/receive, zero-copy, select; verify payload and state transitions.
  - Integration tests:
      - Multi-thread producers/consumers with random delays; ensure callbacks resume correct coroutines.
  - Stress tests:
      - 1e6 parking/resume cycles; monitor memory usage to confirm no leaks.
  - Performance benchmarks:
      - CPU usage profiling (should be near idle during I/O waits).
      - Throughput comparisons vs previous runtime.

  ———

  ### 20.  Pseudocode Appendix

  20.1 Correlation Table Implementation

  class CorrelationTable {
      struct Entry { kc_token_id_t id; TokenBlock* block; Entry* next; }
      vector<atomic<Entry*>> buckets;

      bool insert(kc_token_id_t id, TokenBlock* block) {
          size_t idx = hash(id) % buckets.size();
          Entry* new_entry = new Entry{id, block, nullptr};
          Entry* head = buckets[idx].load();
          do {
              new_entry->next = head;
          } while (!buckets[idx].compare_exchange_weak(head, new_entry));
          return true;
      }

      TokenBlock* remove(kc_token_id_t id) {
          size_t idx = hash(id) % buckets.size();
          Entry* prev = nullptr;
          Entry* cur = buckets[idx].load();
          while (cur) {
              if (cur->id == id) {
                  TokenBlock* block = cur->block;
                  // CAS removal loop omitted for brevity (use lockless list removal)
                  remove_entry(idx, prev, cur);
                  return block;
              }
              prev = cur;
              cur = cur->next;
          }
          return nullptr;
      }
  };

  20.2 park_current_coroutine

  void park_current_coroutine(TokenBlock* block) {
      kcoro_t* current = kcoro_current();
      current->state = KCORO_PARKED;
      current->parked_block = block;
      block->home_queue = current_thread_queue;
      kcoro_yield(); // token VM will resume once callback occurs
  }

  20.3 resume_token

  void resume_token(TokenBlock* block, kc_payload payload) {
      block->payload = payload;
      block->state = READY;
      KernelQueue* q = block->home_queue;
      if (!q) q = choose_any_queue();
      if (q->push(block)) {
          wake_kernel_queue(q);
      }
  }

  20.4 choose_any_queue

  KernelQueue* choose_any_queue() {
      static atomic<size_t> round_robin{0};
      size_t idx = round_robin.fetch_add(1) % NUM_KERNEL_THREADS;
      return &global_runtime_threads[idx].queue;
  }

  20.5 kernel_sleep logic

  void wait_on_eventfd(eventfd_t fd) {
      eventfd_t val;
      if (eventfd_read(fd, &val) < 0) {
          if (errno == EAGAIN) return;
          // handle errors
      }
  }

  20.6 example callback from network

  void on_socket_read(int fd, kc_ticket ticket) {
      buffer = read_into_shared_buffer(fd);
      kc_payload payload{.ptr=buffer.data, .len=buffer.len};
      kc_chan_callback(ticket, payload);
  }

  20.7 multi-thread park/resume example

  Thread A: send_ptr_async -> correlation ID 42 -> park
  Thread B: receives event with ID 42 -> kc_chan_callback -> queue push -> wake Thread A's kernel loop -> interpreter resumes coroutine

  ———

  ### 21.  Outstanding Questions & Work Items

  - Reclamation policy: finalize between EBR vs hazard pointers depending on performance targets.
  - Channel affinity: allow configuration to keep tokens on same NUMA node; optional cross-node migration.
  - Error propagation: define canonical mapping for cancellation, timeouts, and channel closure within token streams.
  - Tracing hooks: add optional event stream so we can observe correlations in flight (useful for debugging).
  - API compatibility: ensure existing C++ wrappers (kcoro_cpp) can wrap the new async primitives.

  ———

  ### 22.  Next Steps

  1. Create new module core/token_kernel.c implementing correlation table, freelist, and kernel loop.
  2. Refactor kc_chan.c to route async paths through token kernel when KC_BIZTOKEN flag is enabled.
  3. Replace calls to kc_sched_enqueue_ready with ticket publishing.
  4. Rewire bench_chan_metrics to use async send/recv + callback.
  5. Run microbench and channel benchmarks to validate throughput and low CPU usage under I/O wait.
  6. Iterate on memory reclamation after verifying basic functionality.

  ———

  ### 23.  References

  - Kotlin/Native coroutine dispatcher disassembly: tools/kotlin-native-samples/chan/pingpong.S.
  - BizTalk correlation concept: asynchronous message hydration and rehydration via correlation IDs.
  - Lock-free stack (Treiber) design, hazard pointer literature.
  - Existing kcoro rendezvous/select implementation (file references: external/kcoro/core/src/kc_chan.c, external/kcoro/includes).

  ———

  This draft is intended as the baseline engineering spec. Subsequent revisions will flesh out exact data structures, finalize the reclamation
  approach, and codify the ABI between channels and token kernel.
