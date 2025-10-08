#include "kcoro_cpp/logger.hpp"
#include <chrono>
#include <cstdio>
#include "kcoro_cpp/channel.hpp"

namespace kcoro_cpp {

namespace {
constexpr size_t kLogChannelCapacity = 1024;
}

Logger& Logger::instance() {
  static Logger g;
  return g;
}

Logger::Logger()
    : sched_(sched_default()),
      chan_(std::make_unique<BufferedChannel<std::string>>(sched_, kLogChannelCapacity)) {}

void Logger::start_worker() {
  bool expected = false;
  if (!worker_started_.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
    return;
  }
  sched_spawn_co(sched_, worker_trampoline, this, 64 * 1024);
}

void Logger::submit(LogLevel level, std::string message) {
  start_worker();
  char stamp[64];
  auto now = std::chrono::system_clock::now();
  auto tt = std::chrono::system_clock::to_time_t(now);
  std::tm tm{};
#if defined(_WIN32)
  localtime_s(&tm, &tt);
#else
  localtime_r(&tt, &tm);
#endif
  auto us = std::chrono::duration_cast<std::chrono::microseconds>(now.time_since_epoch()) % 1000000;
  std::snprintf(stamp, sizeof(stamp), "%02d:%02d:%02d.%06lld",
                tm.tm_hour, tm.tm_min, tm.tm_sec, static_cast<long long>(us.count()));

  const char* lvl = "INFO";
  switch (level) {
    case LogLevel::Info: lvl = "INFO"; break;
    case LogLevel::Warn: lvl = "WARN"; break;
    case LogLevel::Error: lvl = "ERROR"; break;
    case LogLevel::Debug: lvl = "DEBUG"; break;
  }

  std::string line;
  line.reserve(message.size() + 64);
  line.append(stamp).append(" [").append(lvl).append("] ").append(message);

  int rc = chan_->send(line, 0);
  if (rc == KC_EAGAIN) {
    // drop best-effort log when channel saturated
  }
}

void Logger::flush() {
  draining_.store(true, std::memory_order_release);
  chan_->send("", 0);
}

void Logger::worker_trampoline(void* arg) {
  static_cast<Logger*>(arg)->worker_loop();
}

void Logger::worker_loop() {
  std::string line;
  while (true) {
    int rc = chan_->recv(line, 100);
    if (rc == 0) {
      if (!line.empty()) {
        std::fwrite(line.data(), 1, line.size(), stderr);
        std::fwrite("\n", 1, 1, stderr);
        std::fflush(stderr);
      }
      continue;
    }
    if (rc == KC_EPIPE) break;
    if (draining_.load(std::memory_order_acquire) && chan_->size() == 0) break;
  }
}

void log_message(LogLevel level, const std::string& message) {
  Logger::instance().submit(level, message);
}

} // namespace kcoro_cpp
