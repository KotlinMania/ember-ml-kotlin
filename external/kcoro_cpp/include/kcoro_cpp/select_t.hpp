#pragma once

#include "kcoro_cpp/select.hpp"
#include "kcoro_cpp/channel.hpp"
#include "kcoro_cpp/platform.hpp"
#include <vector>

namespace kcoro_cpp {

// Type-safe Select for a single value type T
enum class SelectPolicy { FirstWins, Randomized };

template<typename T>
class SelectT {
public:
  explicit SelectT(const ICancellationToken* ct=nullptr, SelectPolicy policy=SelectPolicy::FirstWins)
  : sel_(ct), policy_(policy) {}

  // Add a receive clause: channel and destination reference
  void add_recv(IChannel<T>* ch, T* out) { clauses_.push_back({SelectOp::Recv, ch, out, nullptr}); }
  // Add a send clause: channel and value reference
  void add_send(IChannel<T>* ch, const T* val) { clauses_.push_back({SelectOp::Send, ch, nullptr, val}); }

  // Wait for one clause to complete; returns rc (0 on success or negative KC_*), sets winner index
  int wait(long timeout_ms, int* winner_index=nullptr) {
    sel_.reset();
    // Register all clauses
    int immediate_idx = -1; int immediate_rc = KC_EAGAIN;
    // Maybe randomize order
    if (policy_ == SelectPolicy::Randomized && clauses_.size()>1) {
      auto seed = (uint64_t)platform::now_ns();
      for (size_t i=0;i<clauses_.size();++i) {
        seed ^= (seed << 13); seed ^= (seed >> 7); seed ^= (seed << 17);
        size_t j = (size_t)(seed % clauses_.size()); std::swap(clauses_[i], clauses_[j]);
      }
    }
    for (size_t i=0;i<clauses_.size();++i) {
      const Entry& e = clauses_[i]; int rc = KC_EAGAIN;
      if (e.kind == SelectOp::Recv) rc = e.ch->select_register_recv(&sel_, (int)i, e.out);
      else rc = e.ch->select_register_send(&sel_, (int)i, e.val);
      if (rc != KC_EAGAIN) { immediate_idx = (int)i; immediate_rc = rc; break; }
    }
    if (immediate_idx >= 0) {
      // Cancel the rest
      for (size_t i=0;i<clauses_.size();++i) {
        if ((int)i == immediate_idx) continue;
        sel_.try_complete((int)i, KC_EAGAIN); // mark others non-winning
        clauses_[i].ch->select_cancel(&sel_, (int)i, clauses_[i].kind);
      }
      if (winner_index) *winner_index = immediate_idx; return immediate_rc;
    }

    int idx=-1, rc=KC_EAGAIN;
    rc = sel_.wait(timeout_ms, &idx, nullptr);
    // Cancel non-winners
    for (size_t i=0;i<clauses_.size();++i) if ((int)i != idx) clauses_[i].ch->select_cancel(&sel_, (int)i, clauses_[i].kind);
    if (winner_index) *winner_index = idx; return rc;
  }

  void clear() { clauses_.clear(); sel_.reset(); }
  size_t size() const { return clauses_.size(); }
  bool remove(size_t idx) { if (idx>=clauses_.size()) return false; clauses_.erase(clauses_.begin()+idx); return true; }

private:
  struct Entry { SelectOp kind; IChannel<T>* ch; T* out; const T* val; };
  Select sel_;
  std::vector<Entry> clauses_;
  SelectPolicy policy_;
};

} // namespace kcoro_cpp
