#include "kcoro_cpp/scheduler.hpp"
#include <algorithm>
#include <chrono>
#include <utility>

namespace {
using namespace std::chrono;

inline uint64_t steady_now_ns() {
  return duration_cast<nanoseconds>(steady_clock::now().time_since_epoch()).count();
}

thread_local kcoro_cpp::WorkStealingScheduler* tls_current_sched = nullptr;

std::mutex g_default_sched_mu;
kcoro_cpp::WorkStealingScheduler* g_default_sched = nullptr;
}

namespace kcoro_cpp {
WorkStealingScheduler::WorkStealingScheduler(int workers) {
  int hw = std::max(1, (int)std::thread::hardware_concurrency());
  int n = (workers <= 0) ? hw : workers;
  deques_.reserve(n);
  for (int i = 0; i < n; ++i) deques_.emplace_back(std::make_unique<Deque>());
  // init per-worker last_task fast-path
  last_task_.assign(n, nullptr);
  last_task_mu_.clear(); last_task_mu_.reserve(n);
  for (int i=0;i<n;++i) last_task_mu_.emplace_back(std::make_unique<std::mutex>());
  // init inject ring
  {
    std::lock_guard<std::mutex> lk(inject_.mu);
    inject_.cap = 2048; inject_.buf.resize(inject_.cap); inject_.head = inject_.tail = 0;
  }
  for (int i = 0; i < n; ++i) threads_.emplace_back([this, i]{ worker_loop(i); });
}

WorkStealingScheduler::~WorkStealingScheduler() {
  stop_and_join();
}

void WorkStealingScheduler::spawn(void (*fn)(void*), void* arg, size_t) {
  if (stop_.load()) return;
  Task t{fn, arg
#ifdef KCORO_CPP_CTX_DIAGNOSTICS
    ,0xC0A1FACE, 0
#endif
  };
  static std::atomic<int> rr{0};
  int idx = rr.fetch_add(1) % (int)deques_.size();
  // Try last_task fast path; else inject queue; else fallback to deque
  bool fast = false;
  {
    std::lock_guard<std::mutex> lk(*last_task_mu_[idx]);
    if (last_task_[idx] == nullptr) { last_task_[idx] = new Task(t); fast = true; }
  }
  if (fast) {
    ++stat_fastpath_hits_;
  } else {
    ++stat_fastpath_misses_;
    if (!inject_push(t)) {
      std::lock_guard<std::mutex> lg(deques_[idx]->mu);
      deques_[idx]->q.emplace_back(t);
    }
  }
  park_cv_.notify_one();
  ++stat_tasks_submitted_;
}

void WorkStealingScheduler::spawn_co(Coroutine::Fn fn, void* arg, size_t stack_bytes) {
  // Encapsulate: create coroutine and schedule a small task that resumes it.
  Coroutine* co = new Coroutine(fn, arg, stack_bytes);
  enqueue_ready(co);
}

void WorkStealingScheduler::enqueue_ready(ICoroutineContext* co) {
  if (stop_.load()) return;
  auto* c = dynamic_cast<Coroutine*>(co);
  if (!c) throw Error("enqueue_ready expects kcoro_cpp::Coroutine");
  ready_push_tail(c);
  park_cv_.notify_one();
  ++stat_ready_enq_;
}

bool WorkStealingScheduler::try_steal(int self, Task& out) {
  int n = (int)deques_.size();
  for (int k = 1; k < std::min(n, 4); ++k) {
    int victim = (self + k) % n;
    std::lock_guard<std::mutex> lg(deques_[victim]->mu);
    auto& dq = deques_[victim]->q;
    if (!dq.empty()) { out = dq.front(); dq.pop_front(); return true; }
    ++stat_steals_probes_;
  }
  ++stat_steals_failures_;
  return false;
}

void WorkStealingScheduler::worker_loop(int id) {
  tls_current_sched = this;
  // Ensure this worker thread has a bootstrap main coroutine for parking/resume
  Coroutine::ensure_main();
  while (!stop_.load()) {
    // 1) Ready coroutines
    Coroutine* co = ready_pop_head();
    if (co) {
      co->resume();
      if (!co->is_parked() && co->is_finished()) { retire_maybe(co); }
      continue;
    }

    // 2) Local tasks
    Task t{}; bool have=false;
    {
      std::lock_guard<std::mutex> lg(deques_[id]->mu);
      auto& dq = deques_[id]->q; if (!dq.empty()) { t = dq.back(); dq.pop_back(); have=true; }
    }
    if (have) {
#ifdef KCORO_CPP_CTX_DIAGNOSTICS
  if (t.magic != 0xC0A1FACE) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] bad magic local deque t=%p magic=%x\n", (void*)&t, t.magic); abort(); }
  if (!t.fn) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] null fn local deque\n"); abort(); }
#endif
  t.fn(t.arg); ++stat_tasks_completed_; continue; }

    // 2b) Per-worker fast-path
    Task* lt = nullptr;
    { std::lock_guard<std::mutex> lk(*last_task_mu_[id]); lt = last_task_[id]; last_task_[id] = nullptr; }
    if (lt) { Task tmp = *lt; delete lt;
#ifdef KCORO_CPP_CTX_DIAGNOSTICS
  if (tmp.magic != 0xC0A1FACE) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] bad magic fastpath tmp=%p magic=%x\n", (void*)&tmp, tmp.magic); abort(); }
  if (!tmp.fn) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] null fn fastpath\n"); abort(); }
#endif
  tmp.fn(tmp.arg); ++stat_tasks_completed_; continue; }

    // 3) Steal
    if (try_steal(id, t)) { ++stat_steals_;
#ifdef KCORO_CPP_CTX_DIAGNOSTICS
  if (t.magic != 0xC0A1FACE) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] bad magic stolen task=%p magic=%x\n", (void*)&t, t.magic); abort(); }
  if (!t.fn) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] null fn stolen\n"); abort(); }
#endif
  t.fn(t.arg); ++stat_tasks_completed_; continue; }

    // 4) Inject queue
    if (inject_pop(t)) {
#ifdef KCORO_CPP_CTX_DIAGNOSTICS
  if (t.magic != 0xC0A1FACE) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] bad magic inject t=%p magic=%x\n", (void*)&t, t.magic); abort(); }
  if (!t.fn) { fprintf(stderr, "[kcoro_cpp][TASK][FATAL] null fn inject\n"); abort(); }
#endif
  t.fn(t.arg); ++stat_tasks_completed_; continue; }

    // 5) Park briefly
    std::unique_lock<std::mutex> lk(park_mu_);
    park_cv_.wait_for(lk, std::chrono::milliseconds(1));
  }
  tls_current_sched = nullptr;
}

void WorkStealingScheduler::drain(long timeout_ms) {
  using namespace std::chrono;
  auto deadline = (timeout_ms < 0) ? time_point<steady_clock>::max() : steady_clock::now() + milliseconds(timeout_ms);
  for (;;) {
    bool empty_ready, empty_all=true;
    { std::lock_guard<std::mutex> lg(ready_.mu); empty_ready = (ready_.head==nullptr); }
    for (auto& d : deques_) {
      std::lock_guard<std::mutex> lg(d->mu);
      if (!d->q.empty()) { empty_all=false; break; }
    }
    if (empty_ready && empty_all) return;
    if (steady_clock::now() > deadline) return;
    std::this_thread::sleep_for(std::chrono::milliseconds(2));
  }
}

void WorkStealingScheduler::yield() {
  auto* cur = Coroutine::current();
  if (!cur) return;
  enqueue_ready(cur);
  cur->park();
}

void WorkStealingScheduler::wake_after(ICoroutineContext* co, long delay_ms) {
  if (stop_.load()) return;
  auto* coroutine = dynamic_cast<Coroutine*>(co);
  if (!coroutine) throw Error("wake_after expects kcoro_cpp::Coroutine");
  schedule_timer_after(delay_ms, [this, coroutine]() {
    enqueue_ready(coroutine);
  });
}

void WorkStealingScheduler::sleep_ms(long delay_ms) {
  auto* cur = Coroutine::current();
  if (!cur) { std::this_thread::sleep_for(std::chrono::milliseconds(delay_ms)); return; }
  wake_after(cur, delay_ms);
  cur->park();
}

WorkStealingScheduler::TimerHandle
WorkStealingScheduler::schedule_timer_at(uint64_t deadline_ns, TimerCallback cb) {
  if (!cb || stop_.load()) return {};
  ensure_timer_started();
  auto item = std::make_shared<TimerItem>();
  item->when_ns = deadline_ns;
  item->cb = std::move(cb);
  item->id = next_timer_id_.fetch_add(1, std::memory_order_relaxed);
  {
    std::lock_guard<std::mutex> lk(timer_mu_);
    timer_map_[item->id] = item;
    timer_queue_.push(item);
    timer_cv_.notify_all();
  }
  return TimerHandle{item->id};
}

WorkStealingScheduler::TimerHandle
WorkStealingScheduler::schedule_timer_after(long delay_ms, TimerCallback cb) {
  if (!cb || stop_.load()) return {};
  uint64_t when = steady_now_ns();
  if (delay_ms > 0) when += static_cast<uint64_t>(delay_ms) * 1000000ULL;
  return schedule_timer_at(when, std::move(cb));
}

bool WorkStealingScheduler::cancel_timer(TimerHandle handle) {
  if (!handle.valid()) return false;
  std::lock_guard<std::mutex> lk(timer_mu_);
  auto it = timer_map_.find(handle.id);
  if (it == timer_map_.end()) return false;
  it->second->cancelled.store(true, std::memory_order_release);
  timer_cv_.notify_all();
  return true;
}

void WorkStealingScheduler::timer_loop() {
  for (;;) {
    std::shared_ptr<TimerItem> item;
    {
      std::unique_lock<std::mutex> lk(timer_mu_);
      auto fetch_next = [&]() -> std::shared_ptr<TimerItem> {
        for (;;) {
          if (stop_.load()) return nullptr;
          if (timer_queue_.empty()) {
            timer_cv_.wait(lk);
            continue;
          }
          auto top = timer_queue_.top();
          if (top->cancelled.load(std::memory_order_acquire)) {
            timer_queue_.pop();
            timer_map_.erase(top->id);
            continue;
          }
          uint64_t now = steady_now_ns();
          if (top->when_ns > now) {
            timer_cv_.wait_for(lk, std::chrono::nanoseconds(top->when_ns - now));
            continue;
          }
          timer_queue_.pop();
          timer_map_.erase(top->id);
          return top;
        }
      };
      item = fetch_next();
      if (!item && stop_.load()) break;
    }
    if (!item) continue;
    if (!item->cancelled.load(std::memory_order_acquire)) {
      item->cb();
    }
  }
}

void WorkStealingScheduler::stop_and_join() {
  if (stop_.exchange(true)) return; // already stopped
  park_cv_.notify_all();
  if (timer_started_.load()) {
    std::lock_guard<std::mutex> lk(timer_mu_);
    timer_cv_.notify_all();
  }
  for (auto& t : threads_) if (t.joinable()) t.join();
  if (timer_started_.load() && timer_thread_.joinable()) timer_thread_.join();
  {
    std::lock_guard<std::mutex> lk(timer_mu_);
    timer_map_.clear();
    timer_queue_ = decltype(timer_queue_)();
  }
  // Drain ready list nodes
  drain_ready_list();
  // Delete retired coroutines
  {
    std::lock_guard<std::mutex> lk(retire_mu_);
    for (auto* c : retire_) delete c; retire_.clear(); retire_set_.clear();
  }
}

void WorkStealingScheduler::ensure_timer_started() {
  bool expected = false;
  if (timer_started_.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
    timer_thread_ = std::thread([this]{ timer_loop(); });
  }
}

bool WorkStealingScheduler::inject_push(const Task& t) {
  std::lock_guard<std::mutex> lk(inject_.mu);
  if (inject_.cap == 0) { inject_.cap = 2048; inject_.buf.resize(inject_.cap); inject_.head = inject_.tail = 0; }
  uint32_t next = (inject_.tail + 1) % inject_.cap;
  if (next == inject_.head) {
    inject_grow_locked(inject_.cap * 2);
    next = (inject_.tail + 1) % inject_.cap;
  }
  inject_.buf[inject_.tail] = t; inject_.tail = next; return true;
}

bool WorkStealingScheduler::inject_pop(Task& out) {
  std::lock_guard<std::mutex> lk(inject_.mu);
  if (inject_.cap == 0 || inject_.head == inject_.tail) return false;
  out = inject_.buf[inject_.head];
  inject_.head = (inject_.head + 1) % inject_.cap;
  return true;
}

void WorkStealingScheduler::inject_grow_locked(uint32_t new_cap) {
  if (new_cap <= inject_.cap) return;
  std::vector<Task> nb(new_cap);
  uint32_t i = 0, h = inject_.head;
  while (h != inject_.tail) { nb[i++] = inject_.buf[h]; h = (h + 1) % inject_.cap; }
  inject_.buf.swap(nb);
  inject_.cap = new_cap; inject_.head = 0; inject_.tail = i;
}

void WorkStealingScheduler::retire_maybe(Coroutine* co) {
  if (!co) return;
  std::lock_guard<std::mutex> lk(retire_mu_);
  if (retire_set_.insert(co).second) {
    retire_.push_back(co);
  }
}

void WorkStealingScheduler::drain_ready_list() {
  for (;;) {
    Coroutine* c = nullptr;
    {
      std::lock_guard<std::mutex> lg(ready_.mu);
      c = ready_.head; if (!c) break; ready_.head = c->next_ready_; if (!ready_.head) ready_.tail = nullptr; c->next_ready_ = nullptr; c->ready_enqueued_ = false; --ready_.size;
    }
    (void)c; // coroutine lifetime managed elsewhere
  }
}

// ---------------- Ready list helpers (intrusive linked-list FIFO) --------------------
void WorkStealingScheduler::ready_push_tail(Coroutine* co) {
  std::lock_guard<std::mutex> lg(ready_.mu);
  if (co->ready_enqueued_) return; // avoid double-enqueue
  co->next_ready_ = nullptr;
  co->ready_enqueued_ = true;
  if (!ready_.tail) { ready_.head = ready_.tail = co; }
  else { ready_.tail->next_ready_ = co; ready_.tail = co; }
  ++ready_.size;
}

void WorkStealingScheduler::ready_push_front(Coroutine* co) {
  std::lock_guard<std::mutex> lg(ready_.mu);
  if (co->ready_enqueued_) return;
  co->next_ready_ = ready_.head;
  co->ready_enqueued_ = true;
  ready_.head = co; if (!ready_.tail) ready_.tail = co; ++ready_.size;
}

Coroutine* WorkStealingScheduler::ready_pop_head() {
  std::lock_guard<std::mutex> lg(ready_.mu);
  Coroutine* c = ready_.head; if (!c) return nullptr;
  ready_.head = c->next_ready_; if (!ready_.head) ready_.tail = nullptr; c->next_ready_ = nullptr; c->ready_enqueued_ = false; --ready_.size; return c;
}

bool WorkStealingScheduler::ready_empty() {
  std::lock_guard<std::mutex> lg(ready_.mu);
  return ready_.head == nullptr;
}

WorkStealingScheduler* sched_init(const SchedulerOptions& opts) {
  return new WorkStealingScheduler(opts.workers);
}

void sched_shutdown(WorkStealingScheduler* sched) {
  if (!sched) return;
  {
    std::lock_guard<std::mutex> lk(g_default_sched_mu);
    if (sched == g_default_sched) {
      g_default_sched = nullptr;
    }
  }
  sched->stop_and_join();
  delete sched;
}

WorkStealingScheduler* sched_default() {
  std::lock_guard<std::mutex> lk(g_default_sched_mu);
  if (!g_default_sched) {
    g_default_sched = new WorkStealingScheduler();
  }
  return g_default_sched;
}

WorkStealingScheduler* sched_current() {
  return tls_current_sched;
}

static WorkStealingScheduler* resolve_sched(WorkStealingScheduler* sched) {
  return sched ? sched : sched_default();
}

int sched_spawn(WorkStealingScheduler* sched, void (*fn)(void*), void* arg) {
  auto* target = resolve_sched(sched);
  if (!target || !fn) return -1;
  target->spawn(fn, arg);
  return 0;
}

int sched_spawn_co(WorkStealingScheduler* sched, Coroutine::Fn fn, void* arg,
                   size_t stack_bytes, Coroutine** out_co) {
  auto* target = resolve_sched(sched);
  if (!target || !fn) return -1;
  auto* co = new Coroutine(fn, arg, stack_bytes);
  if (out_co) *out_co = co;
  target->enqueue_ready(co);
  return 0;
}

void sched_enqueue_ready(WorkStealingScheduler* sched, ICoroutineContext* co) {
  auto* target = resolve_sched(sched);
  if (!target) return;
  target->enqueue_ready(co);
}

void sched_yield() {
  if (auto* sched = sched_current()) { sched->yield(); return; }
  if (auto* cur = Coroutine::current()) { cur->park(); return; }
  std::this_thread::yield();
}

void sched_sleep_ms(int ms) {
  if (auto* sched = sched_current()) { sched->sleep_ms(ms); return; }
  std::this_thread::sleep_for(std::chrono::milliseconds(ms));
}

WorkStealingScheduler::TimerHandle sched_timer_add_at(WorkStealingScheduler* sched,
                                                      uint64_t deadline_ns,
                                                      WorkStealingScheduler::TimerCallback cb) {
  auto* target = resolve_sched(sched);
  if (!target) return {};
  return target->schedule_timer_at(deadline_ns, std::move(cb));
}

WorkStealingScheduler::TimerHandle sched_timer_add_after(WorkStealingScheduler* sched,
                                                         long delay_ms,
                                                         WorkStealingScheduler::TimerCallback cb) {
  auto* target = resolve_sched(sched);
  if (!target) return {};
  return target->schedule_timer_after(delay_ms, std::move(cb));
}

bool sched_timer_cancel(WorkStealingScheduler* sched,
                        WorkStealingScheduler::TimerHandle handle) {
  auto* target = resolve_sched(sched);
  if (!target) return false;
  return target->cancel_timer(handle);
}

} // namespace kcoro_cpp
