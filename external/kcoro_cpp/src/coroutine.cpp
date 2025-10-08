// Internal debug instrumentation is always compiled; runtime enabled via env var KCORO_CPP_DEBUG_CTX_CHECK.
// (Formerly gated by KCORO_CPP_INTERNAL_CTX_DEBUG macro, now retired.)
#include "kcoro_cpp/coroutine.hpp"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <array>
#include <mutex>

using namespace kcoro_cpp;

extern "C" void* kcoro_switch(void* from_co, void* to_co);

namespace kcoro_cpp { void coroutine_trampoline_c(); }

thread_local Coroutine* Coroutine::tls_current_ = nullptr;
thread_local Coroutine* Coroutine::tls_main_ = nullptr;

Coroutine* Coroutine::current() { return tls_current_; }
Coroutine* Coroutine::main() { return tls_main_; }

namespace {
// Environment flag checked once per process
static bool ctx_check_enabled() {
  static int v = [](){ const char* e = std::getenv("KCORO_CPP_DEBUG_CTX_CHECK"); return (e && *e && *e!='0') ? 1 : 0; }();
  return v == 1;
}

// Canary constant
constexpr unsigned long long CTX_CANARY = 0xC07EC07EULL ^ 0x9E3779B185EBCA87ULL;

struct ExecRange { const void* lo; const void* hi; };
static ExecRange g_exec_range = [](){
  const void* f1 = (const void*)&ctx_check_enabled;
  const void* f2 = (const void*)&kcoro_cpp::coroutine_trampoline_c;
  const void* arr[] = {f1,f2};
  const void* lo = arr[0]; const void* hi = arr[0];
  for (auto* p : arr) { if (p < lo) lo = p; if (p > hi) hi = p; }
  return ExecRange{lo, hi};
}();
inline bool ptr_in_exec(const void* p) { return p >= g_exec_range.lo && p <= g_exec_range.hi; }
inline void maybe_widen_exec_range(const void* p) {
  if (!p) return; if (ptr_in_exec(p)) return; uintptr_t up = (uintptr_t)p; if (up < 0x1000) return; if ((up & 0x3)!=0) return; // simple plausibility
  // widen conservatively within 64MB window
  constexpr uintptr_t WINDOW = 64ULL * 1024 * 1024;
  uintptr_t lo = (uintptr_t)g_exec_range.lo; uintptr_t hi = (uintptr_t)g_exec_range.hi;
  if (up + WINDOW < lo || up > hi + WINDOW) return; // too far
  if (up < lo) g_exec_range.lo = (const void*)(up & ~uintptr_t(0xFFF));
  if (up > hi) g_exec_range.hi = (const void*)((up + 0xFFF) & ~uintptr_t(0xFFF));
  fprintf(stderr, "[kcoro_cpp][CTX] EXEC_RANGE_WIDEN new_lo=%p new_hi=%p via=%p\n", g_exec_range.lo, g_exec_range.hi, p);
}

inline void ctx_validate(kcoro_cpp::Coroutine* co, const char* phase) {
  if (!ctx_check_enabled() || !co) return;
  void* lr_slot = co->debug_ctx().reg[13];
  void* sp_slot = co->debug_ctx().reg[14];
  void* fp_slot = co->debug_ctx().reg[15];
  if (co->debug_ctx().canary == 0 && co->debug_fn_ptr()==nullptr) {
    // Bootstrap main coroutine: lazily initialize canary now.
    co->debug_ctx().canary = CTX_CANARY;
    co->debug_ctx().lr_shadow = lr_slot;
    return; // don't treat as error on first observation
  }
  auto fatal_snapshot = [&](const char* tag, const char* extra_fmt = nullptr, auto... extra_args) {
    fprintf(stderr, "[kcoro_cpp][CTX][FATAL] tag=%s phase=%s co=%p state=%d name=%s stack=%p fn=%p canary=%llx lr_slot=%p shadow=%p first_lr=%p hist_count=%u\n",
            tag, phase, (void*)co, (int)co->debug_state(), co->debug_name().c_str(), co->debug_stack_ptr(), co->debug_fn_ptr(), (unsigned long long)co->debug_ctx().canary,
            lr_slot, co->debug_ctx().lr_shadow, co->debug_ctx().first_lr, co->debug_ctx().lr_hist_count);
    for (unsigned i=0;i<co->debug_ctx().lr_hist_count;i++) {
      fprintf(stderr, "  lr_hist[%u]=%p\n", i, co->debug_ctx().lr_hist[i]);
    }
    if constexpr (sizeof...(extra_args) > 0) {
      fprintf(stderr, "  detail: ");
      fprintf(stderr, extra_fmt, extra_args...);
      fprintf(stderr, "\n");
    }
    abort();
  };
  if (co->debug_ctx().canary != CTX_CANARY) {
    fatal_snapshot("CANARY", "expected=%llx got=%llx", (unsigned long long)CTX_CANARY, (unsigned long long)co->debug_ctx().canary);
  }
  if (!ptr_in_exec(lr_slot)) {
    maybe_widen_exec_range(lr_slot);
    if (!ptr_in_exec(lr_slot)) fatal_snapshot("LR_RANGE");
  }
  if (co->debug_fn_ptr() && !ptr_in_exec(co->debug_fn_ptr())) {
    maybe_widen_exec_range(co->debug_fn_ptr());
    if (!ptr_in_exec(co->debug_fn_ptr())) fatal_snapshot("FN_RANGE");
  }
  // Initialize first_lr
  if (!co->debug_ctx().first_lr) co->debug_ctx().first_lr = lr_slot;
  bool seen=false; for (unsigned i=0;i<co->debug_ctx().lr_hist_count;i++) if (co->debug_ctx().lr_hist[i]==lr_slot) { seen=true; break; }
  if (!seen && co->debug_ctx().lr_hist_count < sizeof(co->debug_ctx().lr_hist)/sizeof(co->debug_ctx().lr_hist[0])) {
    co->debug_ctx().lr_hist[co->debug_ctx().lr_hist_count++] = lr_slot;
  }
  // Stack bounds & alignment (skip for bootstrap main with no allocated stack)
  if (co->debug_stack_ptr()) {
    auto stack_base = reinterpret_cast<uintptr_t>(co->debug_stack_ptr());
    auto stack_size = co->debug_stack_size();
    auto stack_top  = stack_base + stack_size; // one past highest usable
    uintptr_t spv = reinterpret_cast<uintptr_t>(sp_slot);
    if (spv < stack_base) fatal_snapshot("SP_UNDERFLOW", "sp=%p base=%p size=%zu", sp_slot, (void*)stack_base, stack_size);
    if (spv > stack_top) fatal_snapshot("SP_ABOVE_TOP", "sp=%p top=%p base=%p size=%zu", sp_slot, (void*)stack_top, (void*)stack_base, stack_size);
    if ((spv & 0xF) != 0) fatal_snapshot("SP_ALIGN", "sp=%p", sp_slot);
    co->debug_ctx().sp_shadow = sp_slot; co->debug_ctx().fp_shadow = fp_slot;
  }
}

inline void ctx_record_lr(kcoro_cpp::Coroutine* co) {
  if (!ctx_check_enabled() || !co) return; co->debug_ctx().lr_shadow = co->debug_ctx().reg[13];
}
}

Coroutine::Coroutine(Fn fn, void* arg, std::size_t stack_bytes) : fn_(fn), arg_(arg) {
  if (!tls_main_ && fn) {
    // bootstrap a main coroutine for this thread only when creating first real coroutine
    tls_main_ = new Coroutine(nullptr, nullptr, 0);
    tls_main_->state_ = CoState::RUNNING;
    tls_main_->main_co_ = nullptr;
    tls_current_ = tls_main_;
  }
  if (!fn_) return; // internal main

  stack_ = platform::MMapStack::allocate(stack_bytes ? stack_bytes : 64*1024);
  // Prepare initial context: set SP/FP and LR to trampoline
  uintptr_t top = reinterpret_cast<uintptr_t>(stack_.ptr) + stack_.size;
  top = detail::align_down(top, 16);
  top -= 16; // space

  // SP at reg[14], FP at reg[15], LR/continuation at reg[13]
  ctx_.reg[14] = reinterpret_cast<void*>(top);
  ctx_.reg[15] = reinterpret_cast<void*>(top);
  ctx_.reg[13] = reinterpret_cast<void*>(coroutine_trampoline_c);
  if (ctx_check_enabled()) { ctx_.canary = CTX_CANARY; ctx_record_lr(this); }
  state_ = CoState::READY;
  main_co_ = tls_current_ ? tls_current_ : tls_main_;
}

Coroutine::~Coroutine() {
  if (fn_) {
    stack_.release();
  } else {
    // internal main
  }
}

void Coroutine::resume() {
  if (state_ == CoState::FINISHED || this == tls_current_) return;
  Coroutine* from = tls_current_;
  ctx_validate(this, "pre-resume");
  tls_current_ = this;
  CoState prev_from = from ? from->state_ : CoState::RUNNING;
  if (from && from != this) from->state_ = CoState::SUSPENDED;
  state_ = CoState::RUNNING;
  kcoro_switch(from ? &from->ctx_ : nullptr, &this->ctx_);
  // when we return, control is back in 'from'
  if (from) ctx_validate(from, "post-switch-return");
  ctx_validate(this, "post-resume");
  if (ctx_check_enabled()) ctx_record_lr(this);
  tls_current_ = from;
  if (from) from->state_ = prev_from;
}

void Coroutine::park() {
  if (!tls_current_ || !tls_main_) return;
  Coroutine* cur = tls_current_;
  cur->state_ = CoState::PARKED;
  tls_current_ = tls_main_;
  tls_main_->state_ = CoState::RUNNING;
  ctx_validate(cur, "pre-park");
  kcoro_switch(&cur->ctx_, &tls_main_->ctx_);
  // resumed
  ctx_validate(cur, "post-park");
  if (ctx_check_enabled()) ctx_record_lr(cur);
  tls_current_ = cur;
  cur->state_ = CoState::RUNNING;
}

static thread_local kcoro_cpp::Coroutine* g_tramp_cur = nullptr;

void kcoro_cpp::coroutine_trampoline_c() {
  Coroutine* cur = Coroutine::current();
  assert(cur && cur->fn_);
  cur->state_ = CoState::RUNNING;
  cur->fn_(cur->arg_);
  cur->state_ = CoState::FINISHED;
  // (Optional) could record LR histogram here if desired
  // yield back to main
  if (Coroutine::main()) {
    kcoro_switch(&cur->ctx_, &Coroutine::main()->ctx_);
  }
  // Should not reach here
  std::abort();
}

void Coroutine::ensure_main() {
  if (!tls_main_) {
    tls_main_ = new Coroutine(nullptr, nullptr, 0);
    tls_main_->state_ = CoState::RUNNING;
    tls_main_->main_co_ = nullptr;
    tls_current_ = tls_main_;
  }
}
