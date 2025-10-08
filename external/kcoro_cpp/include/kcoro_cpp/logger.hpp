#pragma once

#include "kcoro_cpp/scheduler.hpp"
#include <string>
#include <atomic>
#include <memory>

namespace kcoro_cpp {

template<typename T>
class BufferedChannel;

enum class LogLevel {
  Info,
  Warn,
  Error,
  Debug
};

class Logger {
public:
  static Logger& instance();

  void submit(LogLevel level, std::string message);
  void flush();

  Logger(const Logger&) = delete;
  Logger& operator=(const Logger&) = delete;

private:
  Logger();
  void start_worker();
  static void worker_trampoline(void* arg);
  void worker_loop();

  WorkStealingScheduler* sched_{};
  std::unique_ptr<BufferedChannel<std::string>> chan_;
  std::atomic<bool> worker_started_{false};
  std::atomic<bool> draining_{false};
};

void log_message(LogLevel level, const std::string& message);
inline void log_info(const std::string& message) { log_message(LogLevel::Info, message); }
inline void log_warn(const std::string& message) { log_message(LogLevel::Warn, message); }
inline void log_error(const std::string& message) { log_message(LogLevel::Error, message); }
inline void log_debug(const std::string& message) { log_message(LogLevel::Debug, message); }

} // namespace kcoro_cpp
