#pragma once

#include "kcoro_cpp/core.hpp"
#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <vector>

namespace kcoro_cpp {

// Hierarchical cancellation token modeled after kcoro's kc_cancel.
class CancellationToken : public ICancellationToken {
public:
  CancellationToken();
  explicit CancellationToken(CancellationToken* parent);
  ~CancellationToken() override;

  CancellationToken(const CancellationToken&) = delete;
  CancellationToken& operator=(const CancellationToken&) = delete;

  void trigger() override;
  bool is_set() const override;
  int wait(long timeout_ms) const override;  // 0 on cancel, KC_ETIME on timeout

  CancellationToken* parent() const { return parent_; }

private:
  struct State {
    std::atomic<bool> canceled{false};
    mutable std::mutex mu;
    std::condition_variable cv;
    std::vector<CancellationToken*> children;
  };

  explicit CancellationToken(std::shared_ptr<State> state, CancellationToken* parent);

  void attach_to_parent(CancellationToken* parent);
  void detach_from_parent();
  void propagate_cancel_from_parent();
  void collect_children(std::vector<CancellationToken*>& out);

  std::shared_ptr<State> state_;
  CancellationToken* parent_{nullptr};
};

} // namespace kcoro_cpp

