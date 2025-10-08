#include "kcoro_cpp/dispatcher.hpp"

#include <algorithm>
#include <atomic>
#include <mutex>

namespace {
using namespace kcoro_cpp;

std::mutex g_dispatcher_mu;
WorkStealingScheduler* g_default_dispatcher = nullptr;
WorkStealingScheduler* g_io_dispatcher = nullptr;

inline int suggest_io_workers() {
  int hw = std::max(1, (int)std::thread::hardware_concurrency());
  return std::max(hw, 64);
}

} // namespace

namespace kcoro_cpp {

WorkStealingScheduler* dispatcher_new(int workers) {
  if (workers <= 0) workers = std::max(1, (int)std::thread::hardware_concurrency());
  return new WorkStealingScheduler(workers);
}

WorkStealingScheduler* dispatcher_default() {
  std::lock_guard<std::mutex> lk(g_dispatcher_mu);
  if (!g_default_dispatcher) {
    g_default_dispatcher = sched_default();
  }
  return g_default_dispatcher;
}

WorkStealingScheduler* dispatcher_io() {
  std::lock_guard<std::mutex> lk(g_dispatcher_mu);
  if (!g_io_dispatcher) {
    g_io_dispatcher = new WorkStealingScheduler(suggest_io_workers());
  }
  return g_io_dispatcher;
}

} // namespace kcoro_cpp
