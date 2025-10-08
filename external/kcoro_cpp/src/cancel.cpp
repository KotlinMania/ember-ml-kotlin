#include "kcoro_cpp/cancel.hpp"

#include <algorithm>
#include <chrono>

using namespace kcoro_cpp;

namespace {

using kWaitClock = std::chrono::steady_clock;

} // namespace

CancellationToken::CancellationToken()
    : CancellationToken(std::make_shared<State>(), nullptr) {}

CancellationToken::CancellationToken(CancellationToken* parent)
    : CancellationToken(std::make_shared<State>(), parent) {}

CancellationToken::CancellationToken(std::shared_ptr<State> state, CancellationToken* parent)
    : state_(std::move(state)), parent_(nullptr) {
  if (!state_) state_ = std::make_shared<State>();
  if (parent) {
    attach_to_parent(parent);
  }
}

CancellationToken::~CancellationToken() {
  detach_from_parent();
}

void CancellationToken::attach_to_parent(CancellationToken* parent) {
  if (!parent) return;
  parent_ = parent;

  // If parent already canceled, inherit cancellation immediately.
  if (parent->is_set()) {
    propagate_cancel_from_parent();
    parent_ = nullptr;
    return;
  }

  auto parent_state = parent->state_;
  {
    std::lock_guard<std::mutex> lk(parent_state->mu);
    if (parent_state->canceled.load(std::memory_order_acquire)) {
      parent_ = nullptr;
    } else {
      parent_state->children.push_back(this);
    }
  }

  if (!parent_) {
    propagate_cancel_from_parent();
  }
}

void CancellationToken::detach_from_parent() {
  auto* parent = parent_;
  if (!parent) return;

  auto parent_state = parent->state_;
  std::lock_guard<std::mutex> lk(parent_state->mu);
  auto& vec = parent_state->children;
  vec.erase(std::remove(vec.begin(), vec.end(), this), vec.end());
  parent_ = nullptr;
}

void CancellationToken::collect_children(std::vector<CancellationToken*>& out) {
  std::lock_guard<std::mutex> lk(state_->mu);
  out.assign(state_->children.begin(), state_->children.end());
}

void CancellationToken::propagate_cancel_from_parent() {
  bool expected = false;
  if (!state_->canceled.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
    return;
  }

  state_->cv.notify_all();

  std::vector<CancellationToken*> children;
  collect_children(children);
  for (auto* child : children) {
    if (child) child->propagate_cancel_from_parent();
  }
}

void CancellationToken::trigger() {
  bool expected = false;
  if (!state_->canceled.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
    return;
  }

  state_->cv.notify_all();

  std::vector<CancellationToken*> children;
  collect_children(children);
  for (auto* child : children) {
    if (child) child->propagate_cancel_from_parent();
  }
}

bool CancellationToken::is_set() const {
  return state_->canceled.load(std::memory_order_acquire);
}

int CancellationToken::wait(long timeout_ms) const {
  if (is_set()) return 0;

  std::unique_lock<std::mutex> lk(state_->mu);
  if (timeout_ms < 0) {
    state_->cv.wait(lk, [&]{ return state_->canceled.load(std::memory_order_acquire); });
    return 0;
  }

  auto deadline = kWaitClock::now() + std::chrono::milliseconds(timeout_ms);
  while (!state_->canceled.load(std::memory_order_acquire)) {
    if (state_->cv.wait_until(lk, deadline) == std::cv_status::timeout) {
      if (!state_->canceled.load(std::memory_order_acquire)) {
        return KC_ETIME;
      }
      break;
    }
  }
  return 0;
}
