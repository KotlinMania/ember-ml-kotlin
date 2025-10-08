#pragma once

#include "kcoro_cpp/core.hpp"
#include "kcoro_cpp/platform.hpp"
#include <atomic>

extern "C" void* kcoro_switch(void* from_co, void* to_co);

namespace kcoro_cpp {

enum class CoState { CREATED, READY, RUNNING, SUSPENDED, PARKED, FINISHED };

struct CoContext {
  void* reg[32]{}; // layout must match kc_ctx_switch.S expectations
  // Debug instrumentation fields (appended so offsets in assembly remain valid)
  void* lr_shadow{nullptr};
  unsigned long long canary{0};
  void* first_lr{nullptr};
  void* lr_hist[8]{}; // circular/unique record of observed LR values
  unsigned char lr_hist_count{0};
  void* sp_shadow{nullptr};
  void* fp_shadow{nullptr};
};

class Coroutine final : public ICoroutineContext {
public:
  using Fn = void(*)(void*);
  Coroutine(Fn fn, void* arg, std::size_t stack_bytes = 64*1024);
  ~Coroutine() override;

  void resume() override;
  void park() override;
  bool is_parked() const override { return state_ == CoState::PARKED; }
  bool is_finished() const { return state_ == CoState::FINISHED; }
  void set_name(const char* n) override { name_ = n ? n : ""; }

  static Coroutine* current();
  static Coroutine* main();
  static void ensure_main();

private:
  friend void coroutine_trampoline_c();
  friend class WorkStealingScheduler; // allow scheduler access to intrusive fields
  static thread_local Coroutine* tls_current_;
  static thread_local Coroutine* tls_main_;

  CoContext ctx_{};
  platform::MMapStack stack_{};
  CoState state_ { CoState::CREATED };
  Fn fn_ { nullptr };
  void* arg_ { nullptr };
  Coroutine* main_co_ { nullptr };
  std::string name_;
  // Intrusive ready-queue linkage (managed only under scheduler ready list mutex)
  Coroutine* next_ready_ { nullptr };
  bool ready_enqueued_ { false };
public: // narrow debug accessors (keep at end to minimize surface)
  CoContext& debug_ctx() { return ctx_; }
  const CoContext& debug_ctx() const { return ctx_; }
  const std::string& debug_name() const { return name_; }
  CoState debug_state() const { return state_; }
  void* debug_stack_ptr() const { return stack_.ptr; }
  std::size_t debug_stack_size() const { return stack_.size; }
  void* debug_fn_ptr() const { return reinterpret_cast<void*>(fn_); }
};

// Low-level helpers
namespace detail {
  inline uintptr_t align_down(uintptr_t x, std::size_t a) { return x & ~static_cast<uintptr_t>(a - 1); }
}

} // namespace kcoro_cpp
