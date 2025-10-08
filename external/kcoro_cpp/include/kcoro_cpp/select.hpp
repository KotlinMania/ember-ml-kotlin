#pragma once

#include "kcoro_cpp/core.hpp"
#include "kcoro_cpp/coroutine.hpp"
#include "kcoro_cpp/platform.hpp"
#include <vector>
#include <variant>
#include <atomic>

namespace kcoro_cpp {

template<typename T>
struct RecvClause : public SelectClauseBase {
  IChannel<T>* ch{}; T* out{};
};

template<typename T>
struct SendClause : public SelectClauseBase {
  IChannel<T>* ch{}; const T* val{};
};

class Select : public ISelect {
public:
  explicit Select(const ICancellationToken* ct=nullptr) : cancel_(ct) {}
  void reset() override { clauses_.clear(); state_.store(0); winner_index_ = -1; result_ = KC_EAGAIN; waiter_ = nullptr; }
  void add_clause(SelectClauseBase* c) override { clauses_.push_back(c); }
  int wait(long timeout_ms, int* index_out, int* op_result) override {
    // Fast probe
    for (size_t i=0;i<clauses_.size();++i) {
      auto* c = clauses_[i]; int rc = KC_EAGAIN;
      if (auto* r = dynamic_cast<RecvClause<std::pair<void*,size_t>>*>(c)) {
        std::pair<void*,size_t> tmp; rc = r->ch->recv(tmp, 0); if (rc==0) { *r->out = tmp; if(index_out) *index_out=(int)i; if(op_result) *op_result=rc; return rc; }
      }
      // Generic T paths (requires RTTI): user can use specific Select specializations as needed
    }
    if (timeout_ms == 0) { if(op_result) *op_result = KC_EAGAIN; return KC_EAGAIN; }
    auto* cur = Coroutine::current(); if (!cur) return KC_EAGAIN; waiter_ = cur;
    auto deadline = (timeout_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)timeout_ms * 1000000ULL);
    for (;;) {
      if (state_.load() != 0) { if(index_out) *index_out = winner_index_; if(op_result) *op_result = result_; return result_; }
      if (cancel_ && cancel_->is_set()) { if (op_result) *op_result = KC_ECANCELED; return KC_ECANCELED; }
      // Try again non-blocking then park for a slice
      for (size_t i=0;i<clauses_.size();++i) {
        auto* c = clauses_[i]; int rc = KC_EAGAIN;
        if (auto* r = dynamic_cast<RecvClause<std::pair<void*,size_t>>*>(c)) {
          std::pair<void*,size_t> tmp; rc = r->ch->recv(tmp, 0); if (rc==0) { *r->out = tmp; if(index_out) *index_out=(int)i; if(op_result) *op_result=rc; return rc; }
        }
      }
      if (timeout_ms > 0 && platform::now_ns() >= deadline) { if(op_result) *op_result = KC_ETIME; return KC_ETIME; }
      // Park cooperatively
      cur->park();
      // Woken by a channel completing a clause or spurious wake; check state again
      if (state_.load() != 0) { if(index_out) *index_out = winner_index_; if(op_result) *op_result = result_; return result_; }
    }
  }
  bool try_complete(int clause_index, int result) override {
    int expected = 0; if (state_.compare_exchange_strong(expected, 1)) { winner_index_ = clause_index; result_ = result; return true; } return false;
  }
  ICoroutineContext* waiter() override { return waiter_; }
private:
  const ICancellationToken* cancel_{};
  std::vector<SelectClauseBase*> clauses_;
  std::atomic<int> state_{0};
  int winner_index_{-1};
  int result_{KC_EAGAIN};
  ICoroutineContext* waiter_{};
};

} // namespace kcoro_cpp
