#pragma once

#include "kcoro_cpp/core.hpp"
#include "kcoro_cpp/coroutine.hpp"
#include <thread>
#include <mutex>
#include <condition_variable>
#include <deque>
#include <vector>
#include <atomic>
#include <unordered_set>
#include <functional>
#include <queue>
#include <unordered_map>
#include <memory>

namespace kcoro_cpp {

struct Task {
  void (*fn)(void*);
  void* arg;
#ifdef KCORO_CPP_INTERNAL_CTX_DEBUG
  uint32_t magic{0xC0A1FACE};
  uint32_t gen{0};
#endif
};

  class WorkStealingScheduler final : public IScheduler {
  public:
    explicit WorkStealingScheduler(int workers = 0);
    ~WorkStealingScheduler() override;

  void spawn(void (*fn)(void*), void* arg, size_t stack_bytes = 64*1024) override;
  void spawn_co(Coroutine::Fn fn, void* arg, size_t stack_bytes = 64*1024);
    void enqueue_ready(ICoroutineContext* co) override;
    void drain(long timeout_ms) override;

    // Helpers: cooperative yield and sleep
    void yield();
    void wake_after(ICoroutineContext* co, long delay_ms);
    void sleep_ms(long delay_ms);
    void stop_and_join();

    struct TimerHandle {
      uint64_t id{0};
      bool valid() const { return id != 0; }
    };
    using TimerCallback = std::function<void()>;

    TimerHandle schedule_timer_at(uint64_t deadline_ns, TimerCallback cb);
    TimerHandle schedule_timer_after(long delay_ms, TimerCallback cb);
    bool cancel_timer(TimerHandle handle);

private:
    struct Deque {
      std::mutex mu; std::deque<Task> q;
      Deque() = default;
      Deque(const Deque&) = delete; Deque& operator=(const Deque&) = delete;
    };
    struct ReadyList {
      std::mutex mu; Coroutine* head{nullptr}; Coroutine* tail{nullptr}; size_t size{0};
    };
    std::vector<std::thread> threads_;
    std::vector<std::unique_ptr<Deque>> deques_;
    ReadyList ready_;
    std::mutex park_mu_; std::condition_variable park_cv_;
    std::atomic<bool> stop_{false};

    // Timer thread
    struct TimerItem {
      uint64_t id{0};
      uint64_t when_ns{0};
      TimerCallback cb;
      std::atomic<bool> cancelled{false};
    };
    struct TimerCompare {
      bool operator()(const std::shared_ptr<TimerItem>& a,
                      const std::shared_ptr<TimerItem>& b) const {
        return a->when_ns > b->when_ns;
      }
    };
    std::thread timer_thread_;
    std::atomic<bool> timer_started_{false};
    std::mutex timer_mu_; std::condition_variable timer_cv_;
    std::priority_queue<std::shared_ptr<TimerItem>,
                        std::vector<std::shared_ptr<TimerItem>>,
                        TimerCompare> timer_queue_;
    std::unordered_map<uint64_t, std::shared_ptr<TimerItem>> timer_map_;
    std::atomic<uint64_t> next_timer_id_{1};

    // Stats (basic parity)
    std::atomic<uint64_t> stat_tasks_submitted_{0};
    std::atomic<uint64_t> stat_ready_enq_{0};
    std::atomic<uint64_t> stat_steals_{0};
    std::atomic<uint64_t> stat_fastpath_hits_{0};
    std::atomic<uint64_t> stat_fastpath_misses_{0};
    std::atomic<uint64_t> stat_steals_probes_{0};
    std::atomic<uint64_t> stat_steals_failures_{0};
    std::atomic<uint64_t> stat_tasks_completed_{0};

    // Inject queue (bounded ring, grows as needed)
    struct InjectRing {
      std::mutex mu; std::vector<Task> buf; uint32_t cap{0}, head{0}, tail{0};
    } inject_;

    // Per-worker last_task fast-path (mutex-protected)
    std::vector<Task*> last_task_;
    std::vector<std::unique_ptr<std::mutex>> last_task_mu_;

    // Retire list for finished coroutines (delete outside critical paths)
    std::mutex retire_mu_;
    std::vector<Coroutine*> retire_;
    std::unordered_set<Coroutine*> retire_set_;

    void worker_loop(int id);
    bool try_steal(int self, Task& out);
    void timer_loop();
    void ensure_timer_started();
    bool inject_push(const Task& t);
    bool inject_pop(Task& out);
    void inject_grow_locked(uint32_t new_cap);

    // Retire helpers
    void retire_maybe(Coroutine* co);
    void drain_ready_list();

  // Ready list helpers (intrusive FIFO)
  void ready_push_tail(Coroutine* co);
  void ready_push_front(Coroutine* co);
  Coroutine* ready_pop_head();
  bool ready_empty();
  };

struct SchedulerOptions {
  int workers{0};
};

WorkStealingScheduler* sched_init(const SchedulerOptions& opts = {});
void sched_shutdown(WorkStealingScheduler* sched);
WorkStealingScheduler* sched_default();
WorkStealingScheduler* sched_current();
int sched_spawn(WorkStealingScheduler* sched, void (*fn)(void*), void* arg);
int sched_spawn_co(WorkStealingScheduler* sched, Coroutine::Fn fn, void* arg,
                   size_t stack_bytes = 64 * 1024, Coroutine** out_co = nullptr);
void sched_enqueue_ready(WorkStealingScheduler* sched, ICoroutineContext* co);
void sched_yield();
void sched_sleep_ms(int ms);
WorkStealingScheduler::TimerHandle sched_timer_add_at(WorkStealingScheduler* sched,
                                                      uint64_t deadline_ns,
                                                      WorkStealingScheduler::TimerCallback cb);
WorkStealingScheduler::TimerHandle sched_timer_add_after(WorkStealingScheduler* sched,
                                                         long delay_ms,
                                                         WorkStealingScheduler::TimerCallback cb);
bool sched_timer_cancel(WorkStealingScheduler* sched,
                        WorkStealingScheduler::TimerHandle handle);

} // namespace kcoro_cpp
