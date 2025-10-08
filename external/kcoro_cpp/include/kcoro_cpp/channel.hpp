#pragma once

#include "kcoro_cpp/core.hpp"
#include "kcoro_cpp/coroutine.hpp"
#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/logger.hpp"
#include <mutex>
#include <deque>
#include <optional>
#include <atomic>
#include "kcoro_cpp/platform.hpp"

namespace kcoro_cpp {

// Rendezvous (0-buffer) channel
struct ChannelSnapshot {
  unsigned long total_sends{};
  unsigned long total_recvs{};
  unsigned long total_bytes_sent{};
  unsigned long total_bytes_recv{};
  unsigned long total_eagain{};
  unsigned long total_etime{};
  unsigned long total_ecanceled{};
  unsigned long total_epipe{};
  long long first_op_time_ns{};
  long long last_op_time_ns{};
  ChannelCaps caps{ChannelCaps::None};
};

inline void compute_rates(const ChannelSnapshot& a, const ChannelSnapshot& b,
                          double& sends_per_sec, double& recvs_per_sec, double& interval_sec) {
  interval_sec = (b.last_op_time_ns - a.last_op_time_ns) / 1e9;
  if (interval_sec <= 0) interval_sec = 1e-6;
  sends_per_sec = (b.total_sends - a.total_sends) / interval_sec;
  recvs_per_sec = (b.total_recvs - a.total_recvs) / interval_sec;
}

struct ChannelMetricsConfig {
  unsigned long emit_min_ops{1024};
  long emit_min_ms{50};
};

struct ChannelMetricsEvent {
  void* chan{}; // identity
  uint32_t version{1};
  unsigned long total_sends{};
  unsigned long total_recvs{};
  unsigned long total_bytes_sent{};
  unsigned long total_bytes_recv{};
  unsigned long delta_sends{};
  unsigned long delta_recvs{};
  unsigned long delta_bytes_sent{};
  unsigned long delta_bytes_recv{};
  long long first_op_time_ns{};
  long long last_op_time_ns{};
  long long emit_time_ns{};
};

template<typename T>
inline size_t size_bytes_default(const T&) { return sizeof(T); }
inline size_t size_bytes_default(const std::pair<void*,size_t>& p) { return p.second; }
inline size_t size_bytes_default(const ZDesc& z) { return z.len; }

// Rendezvous (0-buffer) channel
template<typename T>
class RendezvousChannel : public IChannel<T> {
public:
  void set_metrics_pipe(IChannel<ChannelMetricsEvent>* pipe, ChannelMetricsConfig cfg={}) { std::lock_guard<std::mutex> lk(mu_); metrics_pipe_=pipe; metrics_cfg_=cfg; }
  explicit RendezvousChannel(WorkStealingScheduler* sched) : sched_(sched) {}
  ~RendezvousChannel() override = default;

  int send(const T& val, long timeout_ms) override {
    static std::atomic<int> dbg_s{0}; if (dbg_s++ < 2) { std::printf("[rv.send] enter\n"); }
    std::unique_lock<std::mutex> lk(mu_);
    if (closed_) { ++snap_.total_epipe; log_warn("buffered send observed closed channel"); return KC_EPIPE; }
    if (!recv_waiters_.empty()) {
      auto rw = recv_waiters_.front(); recv_waiters_.pop_front();
      *rw.slot = val; // transfer
      bump_send(size_bytes_default(val));
      if (rw.is_select) { if (rw.sel->try_complete(rw.idx, 0)) if (auto* w = rw.sel->waiter()) { lk.unlock(); sched_->enqueue_ready(static_cast<Coroutine*>(w)); return 0; } }
      if (rw.co) { lk.unlock(); sched_->enqueue_ready(rw.co); return 0; }
      return 0;
    }
    if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    // park current coroutine until a receiver arrives
    auto* cur = Coroutine::current();
    if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; } // not in coroutine: refuse block
    SendWait sw{false, cur, nullptr, -1, val};
    send_waiters_.push_back(sw);
    lk.unlock(); cur->park();
    // resumed by a receiver
    bump_send(size_bytes_default(val));
    return 0;
  }

  int recv(T& out, long timeout_ms) override {
    static std::atomic<int> dbg_r{0}; if (dbg_r++ < 2) { std::printf("[rv.recv] enter\n"); }
    std::unique_lock<std::mutex> lk(mu_);
    if (!send_waiters_.empty()) {
      auto sw = send_waiters_.front(); send_waiters_.pop_front();
      out = sw.val; // take value
      bump_recv(size_bytes_default(out));
      if (sw.is_select) { if (sw.sel->try_complete(sw.idx, 0)) if (auto* w = sw.sel->waiter()) { lk.unlock(); sched_->enqueue_ready(static_cast<Coroutine*>(w)); return 0; } }
      if (sw.co) { lk.unlock(); sched_->enqueue_ready(sw.co); return 0; }
      return 0;
    }
    if (closed_) { ++snap_.total_epipe; return KC_EPIPE; }
    if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    auto* cur = Coroutine::current();
    if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; }
    RecvWait rw{false, cur, nullptr, -1, &out};
    recv_waiters_.push_back(rw);
    lk.unlock(); cur->park();
    bump_recv(size_bytes_default(out));
    return 0;
  }

  // Cancellable variants
  int send_c(const T& val, long timeout_ms, const ICancellationToken* cancel) override {
    // Delegate to send with cooperative wait and cancel
    std::unique_lock<std::mutex> lk(mu_);
    if (closed_) return KC_EPIPE;
    if (!recv_waiters_.empty()) {
      auto* co = recv_waiters_.front().co; auto* slot = recv_waiters_.front().slot;
      recv_waiters_.pop_front(); *slot = val; bump_send(size_bytes_default(val)); lk.unlock(); sched_->enqueue_ready(co); return 0;
    }
    if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    auto* cur = Coroutine::current(); if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; }
    send_waiters_.push_back({false, cur, nullptr, -1, val});
    auto deadline = (timeout_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)timeout_ms * 1000000ULL);
    for(;;){ lk.unlock(); cur->park(); if(cancel && cancel->is_set()) { ++snap_.total_ecanceled; return KC_ECANCELED; } if(timeout_ms>=0 && platform::now_ns()>=deadline) { ++snap_.total_etime; return KC_ETIME; } // resumed => success
      bump_send(size_bytes_default(val)); return 0; }
  }
  int recv_c(T& out, long timeout_ms, const ICancellationToken* cancel) override {
    std::unique_lock<std::mutex> lk(mu_);
    if (!send_waiters_.empty()) { auto sw = send_waiters_.front(); send_waiters_.pop_front(); out = sw.val; bump_recv(size_bytes_default(out)); lk.unlock(); sched_->enqueue_ready(sw.co); return 0; }
    if (closed_) { ++snap_.total_epipe; return KC_EPIPE; }
    if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    auto* cur = Coroutine::current(); if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; }
    recv_waiters_.push_back({false, cur, nullptr, -1, &out});
    auto deadline = (timeout_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)timeout_ms * 1000000ULL);
    for(;;){ lk.unlock(); cur->park(); if(cancel && cancel->is_set()) return KC_ECANCELED; if(timeout_ms>=0 && platform::now_ns()>=deadline) return KC_ETIME; bump_recv(size_bytes_default(out)); return 0; }
  }

  void close() override {
    std::lock_guard<std::mutex> lk(mu_);
    closed_ = true;
    // Wake everyone with EPIPE semantics: for now, just schedule; callers will observe failure on next op
    for (auto& w: recv_waiters_) sched_->enqueue_ready(w.co);
    for (auto& w: send_waiters_) sched_->enqueue_ready(w.co);
    recv_waiters_.clear(); send_waiters_.clear();
  }

  size_t size() const override { return 0; }
  ChannelSnapshot snapshot() const {
    std::lock_guard<std::mutex> lk(mu_);
    return snap_;
  }

  // Select registration (overrides)
  int select_register_recv(ISelect* sel, int clause_index, T* out) override;
  int select_register_send(ISelect* sel, int clause_index, const T* val) override;
  void select_cancel(ISelect* sel, int clause_index, SelectOp kind) override;

private:
  struct RecvWait { bool is_select{false}; Coroutine* co{}; ISelect* sel{}; int idx{-1}; T* slot{}; };
  struct SendWait { bool is_select{false}; Coroutine* co{}; ISelect* sel{}; int idx{-1}; T val{}; };
  WorkStealingScheduler* sched_{};
  mutable std::mutex mu_;
  bool closed_{false};
  std::deque<RecvWait> recv_waiters_;
  std::deque<SendWait> send_waiters_;
  ChannelSnapshot snap_{};
  IChannel<ChannelMetricsEvent>* metrics_pipe_{nullptr};
  ChannelMetricsConfig metrics_cfg_{};
  unsigned long last_emit_sends_{0}, last_emit_recvs_{0}, last_emit_bytes_sent_{0}, last_emit_bytes_recv_{0};
  long long last_emit_time_ns_{0};
  inline void bump_send(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_sends; snap_.total_bytes_sent += bytes; maybe_emit(now); }
  inline void bump_recv(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_recvs; snap_.total_bytes_recv += bytes; maybe_emit(now); }
  inline void maybe_emit(long long now){ if(!metrics_pipe_) return; unsigned long delta_ops=(snap_.total_sends - last_emit_sends_)+(snap_.total_recvs - last_emit_recvs_); long long since_ns= now - last_emit_time_ns_; if(delta_ops < metrics_cfg_.emit_min_ops && since_ns < metrics_cfg_.emit_min_ms*1000000LL) return; ChannelMetricsEvent ev{}; ev.chan=this; ev.total_sends=snap_.total_sends; ev.total_recvs=snap_.total_recvs; ev.total_bytes_sent=snap_.total_bytes_sent; ev.total_bytes_recv=snap_.total_bytes_recv; ev.delta_sends=snap_.total_sends - last_emit_sends_; ev.delta_recvs=snap_.total_recvs - last_emit_recvs_; ev.delta_bytes_sent=snap_.total_bytes_sent - last_emit_bytes_sent_; ev.delta_bytes_recv=snap_.total_bytes_recv - last_emit_bytes_recv_; ev.first_op_time_ns=snap_.first_op_time_ns; ev.last_op_time_ns=snap_.last_op_time_ns; ev.emit_time_ns=now; (void)metrics_pipe_->send(ev, 0); last_emit_sends_=ev.total_sends; last_emit_recvs_=ev.total_recvs; last_emit_bytes_sent_=ev.total_bytes_sent; last_emit_bytes_recv_=ev.total_bytes_recv; last_emit_time_ns_=now; }
};

// Buffered (bounded ring) channel
template<typename T>
  class BufferedChannel : public IChannel<T> {
  public:
  void set_metrics_pipe(IChannel<ChannelMetricsEvent>* pipe, ChannelMetricsConfig cfg={}) { std::lock_guard<std::mutex> lk(mu_); metrics_pipe_=pipe; metrics_cfg_=cfg; }
  BufferedChannel(WorkStealingScheduler* sched, size_t capacity)
  : sched_(sched), cap_(capacity?capacity:64), buf_(cap_) {}

  int send(const T& val, long timeout_ms) override {
    static std::atomic<int> dbg_s{0}; if (dbg_s++ < 2) { std::printf("[unlim.send] enter\n"); }
    std::unique_lock<std::mutex> lk(mu_);
    if (closed_) { ++snap_.total_epipe; return KC_EPIPE; }
    if (count_ < cap_) {
      // enqueue
      buf_[(head_ + count_) % cap_] = val; ++count_;
      // wake a receiver if any
      bump_send(size_bytes_default(val));
      if (!select_recv_waiters_.empty()) { auto [s,i,out] = select_recv_waiters_.front(); select_recv_waiters_.pop_front(); *out = buf_[head_]; head_=(head_+1)%cap_; --count_; bump_recv(size_bytes_default(*out)); if (s->try_complete(i,0)) if (auto* w=s->waiter()) { lk.unlock(); sched_->enqueue_ready(static_cast<Coroutine*>(w)); } }
      else if (!recv_waiters_.empty()) { auto co = recv_waiters_.front(); recv_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co); }
      return 0;
    }
    if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    auto* cur = Coroutine::current(); if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; }
    send_waiters_.push_back(cur);
    lk.unlock(); cur->park();
    // resumed; try again once
    return send(val, 0);
  }

  int recv(T& out, long timeout_ms) override {
    static std::atomic<int> dbg_r{0}; if (dbg_r++ < 2) { std::printf("[unlim.recv] enter\n"); }
    std::unique_lock<std::mutex> lk(mu_);
    if (count_ > 0) {
      out = buf_[head_]; head_ = (head_ + 1) % cap_; --count_;
      bump_recv(size_bytes_default(out));
      if (!send_waiters_.empty()) { auto co = send_waiters_.front(); send_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co); }
      return 0;
    }
    if (closed_) { ++snap_.total_epipe; log_warn("buffered recv observed closed channel"); return KC_EPIPE; }
    if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    auto* cur = Coroutine::current(); if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; }
    recv_waiters_.push_back(cur);
    lk.unlock(); cur->park();
    return recv(out, 0);
  }

  int send_c(const T& val, long timeout_ms, const ICancellationToken* cancel) override {
    std::unique_lock<std::mutex> lk(mu_);
    if (closed_) { ++snap_.total_epipe; log_warn("buffered send_c observed closed channel"); return KC_EPIPE; }
    if (count_ < cap_) { buf_[(head_ + count_) % cap_] = val; ++count_; bump_send(size_bytes_default(val)); if(!recv_waiters_.empty()){auto co=recv_waiters_.front(); recv_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co);} return 0; }
    if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    auto* cur = Coroutine::current(); if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; }
    send_waiters_.push_back(cur);
    auto deadline=(timeout_ms<0)?(uint64_t)(-1):(platform::now_ns()+(uint64_t)timeout_ms*1000000ULL);
    for(;;){ lk.unlock(); cur->park(); if(cancel && cancel->is_set()) { auto count = ++snap_.total_ecanceled; if ((count % 1000)==1) log_debug("buffered send_c cancel observed (sampled)"); return KC_ECANCELED; } if(timeout_ms>=0 && platform::now_ns()>=deadline) { auto count = ++snap_.total_etime; if ((count % 1000)==1) log_debug("buffered send_c timeout (sampled)"); return KC_ETIME; } return send(val,0);}  
  }
  int recv_c(T& out, long timeout_ms, const ICancellationToken* cancel) override {
    std::unique_lock<std::mutex> lk(mu_);
    if (count_ > 0) { out=buf_[head_]; head_=(head_+1)%cap_; --count_; bump_recv(size_bytes_default(out)); if(!send_waiters_.empty()){ auto co=send_waiters_.front(); send_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co);} return 0; }
    if (closed_) { ++snap_.total_epipe; log_warn("buffered recv_c observed closed channel"); return KC_EPIPE; } if (timeout_ms==0) { ++snap_.total_eagain; return KC_EAGAIN; } auto* cur=Coroutine::current(); if(!cur) { ++snap_.total_eagain; return KC_EAGAIN; } recv_waiters_.push_back(cur);
    auto deadline=(timeout_ms<0)?(uint64_t)(-1):(platform::now_ns()+(uint64_t)timeout_ms*1000000ULL);
    for(;;){ lk.unlock(); cur->park(); if(cancel && cancel->is_set()) { auto count = ++snap_.total_ecanceled; if ((count % 1000)==1) log_debug("buffered recv_c cancel observed (sampled)"); return KC_ECANCELED; } if(timeout_ms>=0 && platform::now_ns()>=deadline) { auto count = ++snap_.total_etime; if ((count % 1000)==1) log_debug("buffered recv_c timeout (sampled)"); return KC_ETIME; } return recv(out,0);}  
  }

  void close() override {
    std::lock_guard<std::mutex> lk(mu_);
    closed_ = true;
    log_info("buffered channel closed; waking waiters");
    for (auto* co : recv_waiters_) sched_->enqueue_ready(co);
    for (auto* co : send_waiters_) sched_->enqueue_ready(co);
    recv_waiters_.clear(); send_waiters_.clear();
  }

  size_t size() const override { std::lock_guard<std::mutex> lk(mu_); return count_; }
  ChannelSnapshot snapshot() const { std::lock_guard<std::mutex> lk(mu_); return snap_; }

  // Select registration (overrides)
  int select_register_recv(ISelect* sel, int clause_index, T* out) override;
  int select_register_send(ISelect* sel, int clause_index, const T* val) override;
  void select_cancel(ISelect* sel, int clause_index, SelectOp kind) override;

  private:
  WorkStealingScheduler* sched_{};
  mutable std::mutex mu_;
  bool closed_{false};
  size_t cap_{}; size_t head_{0}; size_t count_{0};
  std::vector<T> buf_;
  std::deque<Coroutine*> recv_waiters_;
  std::deque<Coroutine*> send_waiters_;
  std::deque<std::tuple<ISelect*,int,T*>> select_recv_waiters_;
  std::deque<std::tuple<ISelect*,int,T>> select_send_waiters_;
  ChannelSnapshot snap_{};
  IChannel<ChannelMetricsEvent>* metrics_pipe_{nullptr};
  ChannelMetricsConfig metrics_cfg_{};
  unsigned long last_emit_sends_{0}, last_emit_recvs_{0}, last_emit_bytes_sent_{0}, last_emit_bytes_recv_{0};
  long long last_emit_time_ns_{0};
  inline void bump_send(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_sends; snap_.total_bytes_sent += bytes; maybe_emit(now); }
  inline void bump_recv(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_recvs; snap_.total_bytes_recv += bytes; maybe_emit(now); }
  inline void maybe_emit(long long now){ if(!metrics_pipe_) return; unsigned long delta_ops=(snap_.total_sends-last_emit_sends_)+(snap_.total_recvs-last_emit_recvs_); long long since_ns=now-last_emit_time_ns_; if(delta_ops<metrics_cfg_.emit_min_ops && since_ns<metrics_cfg_.emit_min_ms*1000000LL) return; ChannelMetricsEvent ev{}; ev.chan=this; ev.total_sends=snap_.total_sends; ev.total_recvs=snap_.total_recvs; ev.total_bytes_sent=snap_.total_bytes_sent; ev.total_bytes_recv=snap_.total_bytes_recv; ev.delta_sends=snap_.total_sends-last_emit_sends_; ev.delta_recvs=snap_.total_recvs-last_emit_recvs_; ev.delta_bytes_sent=snap_.total_bytes_sent-last_emit_bytes_sent_; ev.delta_bytes_recv=snap_.total_bytes_recv-last_emit_bytes_recv_; ev.first_op_time_ns=snap_.first_op_time_ns; ev.last_op_time_ns=snap_.last_op_time_ns; ev.emit_time_ns=now; (void)metrics_pipe_->send(ev,0); last_emit_sends_=ev.total_sends; last_emit_recvs_=ev.total_recvs; last_emit_bytes_sent_=ev.total_bytes_sent; last_emit_bytes_recv_=ev.total_bytes_recv; last_emit_time_ns_=now; }
  };

// Select registration for BufferedChannel
template<typename T>
int BufferedChannel<T>::select_register_recv(ISelect* sel, int clause_index, T* out) {
  std::unique_lock<std::mutex> lk(mu_);
  if (count_ > 0) {
    *out = buf_[head_]; head_ = (head_ + 1) % cap_; --count_; bump_recv(size_bytes_default(*out));
    // if any select send waiters exist and there is room, enqueue one
    if (!select_send_waiters_.empty() && count_ < cap_) {
      auto [s,i,v] = select_send_waiters_.front(); select_send_waiters_.pop_front(); buf_[(head_ + count_) % cap_] = v; ++count_; bump_send(size_bytes_default(v)); if (s->try_complete(i,0)) if (auto* w=s->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    }
    if (sel->try_complete(clause_index, 0)) if (auto* w = sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    return 0;
  }
  select_recv_waiters_.push_back({sel, clause_index, out});
  return KC_EAGAIN;
}

template<typename T>
int BufferedChannel<T>::select_register_send(ISelect* sel, int clause_index, const T* val) {
  std::unique_lock<std::mutex> lk(mu_);
  if (count_ < cap_) {
    buf_[(head_ + count_) % cap_] = *val; ++count_; bump_send(size_bytes_default(*val));
    // wake a pending select receiver if any
    if (!select_recv_waiters_.empty()) {
      auto [s,i,out] = select_recv_waiters_.front(); select_recv_waiters_.pop_front(); *out = buf_[head_]; head_=(head_+1)%cap_; --count_; bump_recv(size_bytes_default(*out)); if (s->try_complete(i,0)) if (auto* w=s->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    } else if (!recv_waiters_.empty()) { auto* co = recv_waiters_.front(); recv_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co); }
    if (sel->try_complete(clause_index, 0)) if (auto* w=sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    return 0;
  }
  select_send_waiters_.push_back({sel, clause_index, *val});
  return KC_EAGAIN;
}

template<typename T>
void BufferedChannel<T>::select_cancel(ISelect* sel, int clause_index, SelectOp kind) {
  std::lock_guard<std::mutex> lk(mu_);
  if (kind == SelectOp::Recv) {
    for (auto it = select_recv_waiters_.begin(); it != select_recv_waiters_.end(); ++it) {
      if (std::get<0>(*it) == sel && std::get<1>(*it) == clause_index) { select_recv_waiters_.erase(it); break; }
    }
  } else {
    for (auto it = select_send_waiters_.begin(); it != select_send_waiters_.end(); ++it) {
      if (std::get<0>(*it) == sel && std::get<1>(*it) == clause_index) { select_send_waiters_.erase(it); break; }
    }
  }
}

// Select registration for Rendezvous
template<typename T>
int RendezvousChannel<T>::select_register_recv(ISelect* sel, int clause_index, T* out) {
  std::unique_lock<std::mutex> lk(mu_);
  if (!send_waiters_.empty()) {
    auto sw = send_waiters_.front(); send_waiters_.pop_front(); *out = sw.val; bump_recv(size_bytes_default(*out));
    // wake sender
    if (sw.is_select) { if (sw.sel->try_complete(sw.idx, 0)) if (auto* w = sw.sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); }
    else if (sw.co) { lk.unlock(); sched_->enqueue_ready(sw.co); }
    // complete this select
    if (sel->try_complete(clause_index, 0)) if (auto* w = sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    return 0;
  }
  // enqueue select recv waiter
  recv_waiters_.push_back({true, nullptr, sel, clause_index, out});
  return KC_EAGAIN;
}

template<typename T>
int RendezvousChannel<T>::select_register_send(ISelect* sel, int clause_index, const T* val) {
  std::unique_lock<std::mutex> lk(mu_);
  if (!recv_waiters_.empty()) {
    auto rw = recv_waiters_.front(); recv_waiters_.pop_front(); *rw.slot = *val; bump_send(size_bytes_default(*val));
    // wake receiver
    if (rw.is_select) { if (rw.sel->try_complete(rw.idx, 0)) if (auto* w = rw.sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); }
    else if (rw.co) { lk.unlock(); sched_->enqueue_ready(rw.co); }
    // complete this select
    if (sel->try_complete(clause_index, 0)) if (auto* w = sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    return 0;
  }
  send_waiters_.push_back({true, nullptr, sel, clause_index, *val});
  return KC_EAGAIN;
}

template<typename T>
void RendezvousChannel<T>::select_cancel(ISelect* sel, int clause_index, SelectOp kind) {
  std::lock_guard<std::mutex> lk(mu_);
  if (kind == SelectOp::Recv) {
    for (auto it = recv_waiters_.begin(); it != recv_waiters_.end(); ++it) {
      if (it->is_select && it->sel == sel && it->idx == clause_index) { recv_waiters_.erase(it); break; }
    }
  } else {
    for (auto it = send_waiters_.begin(); it != send_waiters_.end(); ++it) {
      if (it->is_select && it->sel == sel && it->idx == clause_index) { send_waiters_.erase(it); break; }
    }
  }
}

// Conflated (latest-value) channel
template<typename T>
  class ConflatedChannel : public IChannel<T> {
  public:
  void set_metrics_pipe(IChannel<ChannelMetricsEvent>* pipe, ChannelMetricsConfig cfg={}) { std::lock_guard<std::mutex> lk(mu_); metrics_pipe_=pipe; metrics_cfg_=cfg; }
  explicit ConflatedChannel(WorkStealingScheduler* sched) : sched_(sched) {}
  int send(const T& val, long) override {
    std::unique_lock<std::mutex> lk(mu_);
    slot_ = val; has_ = true; bump_send(size_bytes_default(val));
    if (!recv_waiters_.empty()) { auto co = recv_waiters_.front(); recv_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co); }
    return 0;
  }
  int recv(T& out, long timeout_ms) override {
    std::unique_lock<std::mutex> lk(mu_);
    if (has_) { out = *slot_; has_ = false; bump_recv(size_bytes_default(out)); return 0; }
    if (closed_) { ++snap_.total_epipe; return KC_EPIPE; } if (timeout_ms == 0) { ++snap_.total_eagain; return KC_EAGAIN; }
    auto* cur = Coroutine::current(); if (!cur) { ++snap_.total_eagain; return KC_EAGAIN; } recv_waiters_.push_back(cur); lk.unlock(); cur->park(); return recv(out, 0);
  }
  int send_c(const T& val, long, const ICancellationToken*) override { return send(val, -1); }
  int recv_c(T& out, long timeout_ms, const ICancellationToken* cancel) override {
    std::unique_lock<std::mutex> lk(mu_);
    if (has_) { out = *slot_; has_ = false; bump_recv(size_bytes_default(out)); return 0; }
    if (closed_) { ++snap_.total_epipe; return KC_EPIPE; } if (timeout_ms==0) { ++snap_.total_eagain; return KC_EAGAIN; } auto* cur=Coroutine::current(); if(!cur) { ++snap_.total_eagain; return KC_EAGAIN; } recv_waiters_.push_back(cur);
    auto deadline=(timeout_ms<0)?(uint64_t)(-1):(platform::now_ns()+(uint64_t)timeout_ms*1000000ULL);
    for(;;){ lk.unlock(); cur->park(); if(cancel && cancel->is_set()) { ++snap_.total_ecanceled; return KC_ECANCELED; } if(timeout_ms>=0 && platform::now_ns()>=deadline) { ++snap_.total_etime; return KC_ETIME; } return recv(out,0);}  
  }
  void close() override { std::lock_guard<std::mutex> lk(mu_); closed_=true; for(auto* co: recv_waiters_) sched_->enqueue_ready(co); recv_waiters_.clear(); }
  size_t size() const override { return has_?1:0; }
  ChannelSnapshot snapshot() const { std::lock_guard<std::mutex> lk(mu_); return snap_; }

  // Select registration (overrides)
  int select_register_recv(ISelect* sel, int clause_index, T* out) override;
  int select_register_send(ISelect* sel, int clause_index, const T* val) override;
  void select_cancel(ISelect* sel, int clause_index, SelectOp kind) override;
private:
  WorkStealingScheduler* sched_{}; mutable std::mutex mu_{}; bool closed_{false};
  std::optional<T> slot_{}; bool has_{false}; std::deque<Coroutine*> recv_waiters_{}; std::deque<std::tuple<ISelect*,int,T*>> select_recv_waiters_{}; ChannelSnapshot snap_{};
  IChannel<ChannelMetricsEvent>* metrics_pipe_{nullptr}; ChannelMetricsConfig metrics_cfg_{};
  unsigned long last_emit_sends_{0}, last_emit_recvs_{0}, last_emit_bytes_sent_{0}, last_emit_bytes_recv_{0}; long long last_emit_time_ns_{0};
  inline void bump_send(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_sends; snap_.total_bytes_sent += bytes; maybe_emit(now); }
  inline void bump_recv(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_recvs; snap_.total_bytes_recv += bytes; maybe_emit(now); }
  inline void maybe_emit(long long now){ if(!metrics_pipe_) return; unsigned long delta_ops=(snap_.total_sends-last_emit_sends_)+(snap_.total_recvs-last_emit_recvs_); long long since_ns=now-last_emit_time_ns_; if(delta_ops<metrics_cfg_.emit_min_ops && since_ns<metrics_cfg_.emit_min_ms*1000000LL) return; ChannelMetricsEvent ev{}; ev.chan=this; ev.total_sends=snap_.total_sends; ev.total_recvs=snap_.total_recvs; ev.total_bytes_sent=snap_.total_bytes_sent; ev.total_bytes_recv=snap_.total_bytes_recv; ev.delta_sends=snap_.total_sends-last_emit_sends_; ev.delta_recvs=snap_.total_recvs-last_emit_recvs_; ev.delta_bytes_sent=snap_.total_bytes_sent-last_emit_bytes_sent_; ev.delta_bytes_recv=snap_.total_bytes_recv-last_emit_bytes_recv_; ev.first_op_time_ns=snap_.first_op_time_ns; ev.last_op_time_ns=snap_.last_op_time_ns; ev.emit_time_ns=now; (void)metrics_pipe_->send(ev,0); last_emit_sends_=ev.total_sends; last_emit_recvs_=ev.total_recvs; last_emit_bytes_sent_=ev.total_bytes_sent; last_emit_bytes_recv_=ev.total_bytes_recv; last_emit_time_ns_=now; }
  };

template<typename T>
int ConflatedChannel<T>::select_register_recv(ISelect* sel, int clause_index, T* out) {
  std::unique_lock<std::mutex> lk(mu_);
  if (has_) { *out = *slot_; has_ = false; bump_recv(size_bytes_default(*out)); if (sel->try_complete(clause_index,0)) if (auto* w=sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); return 0; }
  select_recv_waiters_.push_back({sel, clause_index, out}); return KC_EAGAIN;
}

template<typename T>
int ConflatedChannel<T>::select_register_send(ISelect* sel, int clause_index, const T* val) {
  std::unique_lock<std::mutex> lk(mu_); slot_ = *val; has_ = true; bump_send(size_bytes_default(*val));
  if (!select_recv_waiters_.empty()) { auto [s,i,out] = select_recv_waiters_.front(); select_recv_waiters_.pop_front(); *out = *slot_; has_ = false; bump_recv(size_bytes_default(*out)); if (s->try_complete(i,0)) if (auto* w=s->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); }
  else if (!recv_waiters_.empty()) { auto* co = recv_waiters_.front(); recv_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co); }
  if (sel->try_complete(clause_index,0)) if (auto* w=sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
  return 0;
}

template<typename T>
void ConflatedChannel<T>::select_cancel(ISelect* sel, int clause_index, SelectOp kind) {
  if (kind==SelectOp::Send) return; std::lock_guard<std::mutex> lk(mu_);
  for (auto it = select_recv_waiters_.begin(); it != select_recv_waiters_.end(); ++it) {
    if (std::get<0>(*it)==sel && std::get<1>(*it)==clause_index) { select_recv_waiters_.erase(it); break; }
  }
}

// Unlimited channel (growing deque)
template<typename T>
  class UnlimitedChannel : public IChannel<T> {
  public:
  void set_metrics_pipe(IChannel<ChannelMetricsEvent>* pipe, ChannelMetricsConfig cfg={}) { std::lock_guard<std::mutex> lk(mu_); metrics_pipe_=pipe; metrics_cfg_=cfg; }
  explicit UnlimitedChannel(WorkStealingScheduler* sched) : sched_(sched) {}
  int send(const T& val, long) override { std::unique_lock<std::mutex> lk(mu_); q_.push_back(val); bump_send(size_bytes_default(val)); if(!recv_waiters_.empty()){auto co=recv_waiters_.front(); recv_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co);} return 0; }
  int recv(T& out, long timeout_ms) override { std::unique_lock<std::mutex> lk(mu_); if(!q_.empty()){ out=q_.front(); q_.pop_front(); bump_recv(size_bytes_default(out)); return 0;} if(closed_) { ++snap_.total_epipe; return KC_EPIPE; } if(timeout_ms==0) { ++snap_.total_eagain; return KC_EAGAIN; } auto* cur=Coroutine::current(); if(!cur) { ++snap_.total_eagain; return KC_EAGAIN; } recv_waiters_.push_back(cur); lk.unlock(); cur->park(); return recv(out,0);} 
  int send_c(const T& val, long, const ICancellationToken*) override { return send(val, -1); }
  int recv_c(T& out, long timeout_ms, const ICancellationToken* cancel) override { std::unique_lock<std::mutex> lk(mu_); if(!q_.empty()){ out=q_.front(); q_.pop_front(); bump_recv(size_bytes_default(out)); return 0;} if(closed_) { ++snap_.total_epipe; return KC_EPIPE; } if(timeout_ms==0) { ++snap_.total_eagain; return KC_EAGAIN; } auto* cur=Coroutine::current(); if(!cur) { ++snap_.total_eagain; return KC_EAGAIN; } recv_waiters_.push_back(cur); auto deadline=(timeout_ms<0)?(uint64_t)(-1):(platform::now_ns()+(uint64_t)timeout_ms*1000000ULL); for(;;){ lk.unlock(); cur->park(); if(cancel && cancel->is_set()) { ++snap_.total_ecanceled; return KC_ECANCELED; } if(timeout_ms>=0 && platform::now_ns()>=deadline) { ++snap_.total_etime; return KC_ETIME; } return recv(out,0);} }
  void close() override { std::lock_guard<std::mutex> lk(mu_); closed_=true; for(auto* co: recv_waiters_) sched_->enqueue_ready(co); recv_waiters_.clear(); }
  size_t size() const override { std::lock_guard<std::mutex> lk(mu_); return q_.size(); }
  ChannelSnapshot snapshot() const { std::lock_guard<std::mutex> lk(mu_); return snap_; }

  // Select registration (overrides)
  int select_register_recv(ISelect* sel, int clause_index, T* out) override;
  int select_register_send(ISelect* sel, int clause_index, const T* val) override;
  void select_cancel(ISelect* sel, int clause_index, SelectOp kind) override;
private:
  WorkStealingScheduler* sched_{}; mutable std::mutex mu_{}; bool closed_{false}; std::deque<T> q_{}; std::deque<Coroutine*> recv_waiters_{}; std::deque<std::tuple<ISelect*,int,T*>> select_recv_waiters_{}; ChannelSnapshot snap_{};
  IChannel<ChannelMetricsEvent>* metrics_pipe_{nullptr}; ChannelMetricsConfig metrics_cfg_{};
  unsigned long last_emit_sends_{0}, last_emit_recvs_{0}, last_emit_bytes_sent_{0}, last_emit_bytes_recv_{0}; long long last_emit_time_ns_{0};
  inline void bump_send(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_sends; snap_.total_bytes_sent += bytes; maybe_emit(now); }
  inline void bump_recv(size_t bytes){ auto now=platform::now_ns(); if(snap_.first_op_time_ns==0) snap_.first_op_time_ns=now; snap_.last_op_time_ns=now; ++snap_.total_recvs; snap_.total_bytes_recv += bytes; maybe_emit(now); }
  inline void maybe_emit(long long now){ if(!metrics_pipe_) return; unsigned long delta_ops=(snap_.total_sends-last_emit_sends_)+(snap_.total_recvs-last_emit_recvs_); long long since_ns=now-last_emit_time_ns_; if(delta_ops<metrics_cfg_.emit_min_ops && since_ns<metrics_cfg_.emit_min_ms*1000000LL) return; ChannelMetricsEvent ev{}; ev.chan=this; ev.total_sends=snap_.total_sends; ev.total_recvs=snap_.total_recvs; ev.total_bytes_sent=snap_.total_bytes_sent; ev.total_bytes_recv=snap_.total_bytes_recv; ev.delta_sends=snap_.total_sends-last_emit_sends_; ev.delta_recvs=snap_.total_recvs-last_emit_recvs_; ev.delta_bytes_sent=snap_.total_bytes_sent-last_emit_bytes_sent_; ev.delta_bytes_recv=snap_.total_bytes_recv-last_emit_bytes_recv_; ev.first_op_time_ns=snap_.first_op_time_ns; ev.last_op_time_ns=snap_.last_op_time_ns; ev.emit_time_ns=now; (void)metrics_pipe_->send(ev,0); last_emit_sends_=ev.total_sends; last_emit_recvs_=ev.total_recvs; last_emit_bytes_sent_=ev.total_bytes_sent; last_emit_bytes_recv_=ev.total_bytes_recv; last_emit_time_ns_=now; }
  };

template<typename T>
int UnlimitedChannel<T>::select_register_recv(ISelect* sel, int clause_index, T* out) {
  std::unique_lock<std::mutex> lk(mu_); if (!q_.empty()) { *out=q_.front(); q_.pop_front(); bump_recv(size_bytes_default(*out)); if (sel->try_complete(clause_index,0)) if (auto* w=sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); return 0; } select_recv_waiters_.push_back({sel,clause_index,out}); return KC_EAGAIN;
}

template<typename T>
int UnlimitedChannel<T>::select_register_send(ISelect* sel, int clause_index, const T* val) {
  std::unique_lock<std::mutex> lk(mu_); q_.push_back(*val); bump_send(size_bytes_default(*val)); if(!select_recv_waiters_.empty()){ auto [s,i,out]=select_recv_waiters_.front(); select_recv_waiters_.pop_front(); *out=q_.front(); q_.pop_front(); bump_recv(size_bytes_default(*out)); if(s->try_complete(i,0)) if(auto* w=s->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); } else if(!recv_waiters_.empty()){ auto* co=recv_waiters_.front(); recv_waiters_.pop_front(); lk.unlock(); sched_->enqueue_ready(co);} if(sel->try_complete(clause_index,0)) if(auto* w=sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); return 0;
}

template<typename T>
void UnlimitedChannel<T>::select_cancel(ISelect* sel, int clause_index, SelectOp kind) {
  if (kind==SelectOp::Send) return; std::lock_guard<std::mutex> lk(mu_); for(auto it=select_recv_waiters_.begin(); it!=select_recv_waiters_.end(); ++it){ if(std::get<0>(*it)==sel && std::get<1>(*it)==clause_index){ select_recv_waiters_.erase(it); break; } }
}

} // namespace kcoro_cpp
