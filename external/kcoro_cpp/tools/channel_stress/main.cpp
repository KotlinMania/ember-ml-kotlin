#include "kcoro_cpp/channel.hpp"
#include "kcoro_cpp/logger.hpp"
#include "kcoro_cpp/scheduler.hpp"
#include <atomic>
#include <chrono>
#include <string>
#include <thread>
#include <vector>

#if defined(__unix__) || defined(__APPLE__)
#include <sys/resource.h>
#endif

using namespace kcoro_cpp;

namespace {

void enable_core_dumps() {
#if defined(__unix__) || defined(__APPLE__)
  rlimit rl;
  rl.rlim_cur = RLIM_INFINITY;
  rl.rlim_max = RLIM_INFINITY;
  setrlimit(RLIMIT_CORE, &rl);
#endif
}

struct ProducerCtx {
  BufferedChannel<int>* chan;
  int messages;
  int timeout_ms;
  std::atomic<int>* produced;
  std::atomic<int>* timeouts;
};

struct ConsumerCtx {
  BufferedChannel<int>* chan;
  int timeout_ms;
  std::atomic<int>* consumed;
  std::atomic<int>* timeouts;
  std::atomic<int>* epipe;
};

void producer_fn(void* arg) {
  auto* ctx = static_cast<ProducerCtx*>(arg);
  for (int i = 0; i < ctx->messages; ++i) {
    int rc;
    while ((rc = ctx->chan->send(i, ctx->timeout_ms)) == KC_EAGAIN) {
      kcoro_cpp::sched_yield();
    }
    if (rc == KC_ETIME) {
      ctx->timeouts->fetch_add(1, std::memory_order_relaxed);
      --i;
      continue;
    }
    if (rc == 0) {
      ctx->produced->fetch_add(1, std::memory_order_relaxed);
    } else if (rc == KC_EPIPE) {
      return;
    }
  }
}

void consumer_fn(void* arg) {
  auto* ctx = static_cast<ConsumerCtx*>(arg);
  for (;;) {
    int value;
    int rc = ctx->chan->recv(value, ctx->timeout_ms);
    if (rc == 0) {
      ctx->consumed->fetch_add(1, std::memory_order_relaxed);
      continue;
    }
    if (rc == KC_EPIPE) {
      ctx->epipe->fetch_add(1, std::memory_order_relaxed);
      return;
    }
    if (rc == KC_ETIME) {
      ctx->timeouts->fetch_add(1, std::memory_order_relaxed);
    }
    kcoro_cpp::sched_yield();
  }
}

} // namespace

int main(int argc, char** argv) {
  enable_core_dumps();

  int producer_count = 8;
  int consumer_count = 8;
  int messages_per_producer = 200000;
  int timeout_ms = 5;
  size_t channel_capacity = 256;

  if (argc > 1) producer_count = std::atoi(argv[1]);
  if (argc > 2) consumer_count = std::atoi(argv[2]);
  if (argc > 3) messages_per_producer = std::atoi(argv[3]);

  auto* sched = sched_default();
  BufferedChannel<int> channel(sched, channel_capacity);

  std::atomic<int> produced{0};
  std::atomic<int> consumed{0};
  std::atomic<int> prod_timeouts{0};
  std::atomic<int> cons_timeouts{0};
  std::atomic<int> cons_epipe{0};

  std::vector<ProducerCtx> prod_ctxs(producer_count);
  std::vector<ConsumerCtx> cons_ctxs(consumer_count);

  for (int i = 0; i < producer_count; ++i) {
    prod_ctxs[i] = {&channel, messages_per_producer, timeout_ms,
                    &produced, &prod_timeouts};
    sched_spawn_co(sched, producer_fn, &prod_ctxs[i], 64 * 1024);
  }

  for (int i = 0; i < consumer_count; ++i) {
    cons_ctxs[i] = {&channel, timeout_ms, &consumed, &cons_timeouts, &cons_epipe};
    sched_spawn_co(sched, consumer_fn, &cons_ctxs[i], 64 * 1024);
  }

  const int expected_messages = producer_count * messages_per_producer;

  auto start = std::chrono::steady_clock::now();
  while (consumed.load(std::memory_order_relaxed) < expected_messages) {
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }

  channel.close();
  sched->drain(1000);

  auto end = std::chrono::steady_clock::now();
  double seconds = std::chrono::duration_cast<std::chrono::duration<double>>(end - start).count();

  log_info("kcoro_cpp buffered channel stress summary:");
  log_info(" producers=" + std::to_string(producer_count) +
           " consumers=" + std::to_string(consumer_count) +
           " messages_per_producer=" + std::to_string(messages_per_producer));
  log_info(" produced=" + std::to_string(produced.load()) +
           " consumed=" + std::to_string(consumed.load()));
  log_info(" producer_timeouts=" + std::to_string(prod_timeouts.load()) +
           " consumer_timeouts=" + std::to_string(cons_timeouts.load()) +
           " consumer_epipe=" + std::to_string(cons_epipe.load()));
  log_info(" elapsed=" + std::to_string(seconds) +
           "s throughput=" + std::to_string(seconds > 0 ? consumed.load() / (seconds * 1e6) : 0.0) + " M msg/s");

  Logger::instance().flush();

  sched_shutdown(sched);
  return 0;
}

