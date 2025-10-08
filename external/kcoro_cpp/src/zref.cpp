#include "kcoro_cpp/zref.hpp"
#include <mutex>

using namespace kcoro_cpp;

// ZCopyRegistry region lifecycle -------------------------------------------------

ZCopyRegistry::RegionId ZCopyRegistry::region_register(void* base, size_t len) {
  std::lock_guard<std::mutex> lk(mu_);
  RegionId id = ++next_region_id_;
  regions_.emplace(id, Region(base, len, 1, false));
  return id;
}

bool ZCopyRegistry::region_incref(RegionId id) {
  std::lock_guard<std::mutex> lk(mu_);
  auto it = regions_.find(id); if (it==regions_.end()) return false; if (it->second.dereg) return false; it->second.refs++; return true;
}

bool ZCopyRegistry::region_decref(RegionId id) {
  std::unique_lock<std::mutex> lk(mu_);
  auto it = regions_.find(id); if (it==regions_.end()) return false; if (it->second.refs==0) return false; it->second.refs--; if (it->second.refs==0 && it->second.dereg) { it->second.cv.notify_all(); }
  return true;
}

bool ZCopyRegistry::region_deregister(RegionId id) {
  std::unique_lock<std::mutex> lk(mu_);
  auto it = regions_.find(id); if (it==regions_.end()) return false;
  auto& r = it->second;
  r.dereg = true;
  // Drop the owner's reference held since registration
  if (r.refs > 0) r.refs--;
  while (r.refs != 0) { r.cv.wait(lk); }
  regions_.erase(it); return true;
}

bool ZCopyRegistry::region_query(RegionId id, void*& base, size_t& len) const {
  std::lock_guard<std::mutex> lk(mu_);
  auto it = regions_.find(id); if (it==regions_.end()) return false; base = it->second.base; len = it->second.len; return true;
}

void ZCopyRegistry::set_meta(RegionId id, const RegionMeta& m) {
  std::lock_guard<std::mutex> lk(mu_);
  auto it = regions_.find(id); if (it==regions_.end()) return; 
  std::lock_guard<std::mutex> lk2(it->second.mu);
  it->second.meta = m; it->second.has_meta = true;
}

bool ZCopyRegistry::get_meta(RegionId id, RegionMeta& m) const {
  std::lock_guard<std::mutex> lk(mu_);
  auto it = regions_.find(id); if (it==regions_.end()) return false; 
  std::lock_guard<std::mutex> lk2(it->second.mu);
  if (!it->second.has_meta) return false; m = it->second.meta; return true;
}

bool ZCopyRegistry::alloc_aligned(size_t size, size_t align, void*& base, RegionId& id) {
  if (align==0) align=64; void* p=nullptr; 
#if defined(_MSC_VER)
  p = _aligned_malloc(size, align); if (!p) return false;
#else
  if (posix_memalign(&p, align, size) != 0) return false;
#endif
  base = p; id = region_register(p, size); return id != 0;
}

int ZRefRendezvous::send(void* ch, const ZDesc& d, long tmo_ms) {
  if (d.region_id && mask_!=0) {
    RegionMeta m{}; if (ZCopyRegistry::instance().get_meta(d.region_id, m)) {
      bool mismatch=false;
      if ((mask_ & FMT_DTYPE)    && m.dtype != req_meta_.dtype) mismatch=true;
      if ((mask_ & FMT_ELEMBITS) && m.elem_bits != req_meta_.elem_bits) mismatch=true;
      if ((mask_ & FMT_ALIGN)    && m.align_bytes < req_meta_.align_bytes) mismatch=true;
      if ((mask_ & FMT_STRIDE)   && m.stride_bytes != req_meta_.stride_bytes) mismatch=true;
      if ((mask_ & FMT_DIMS)) { if (m.ndims != req_meta_.ndims) mismatch=true; else for (uint8_t i=0;i<m.ndims && !mismatch;i++) if (m.dims[i]!=req_meta_.dims[i]) mismatch=true; }
      if ((mask_ & FMT_LAYOUT)   && m.layout != req_meta_.layout) mismatch=true;
      if (mismatch && mode_==FormatMode::Strict) return KC_EINVAL;
    }
  }
  auto deadline = (tmo_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)tmo_ms * 1000000ULL);
  for (;;) {
    Coroutine* cur = Coroutine::current(); if (!cur) return KC_EAGAIN;
    bool park = false;
    {
      std::lock_guard<std::mutex> lk(mu_);
      auto& s = st_[ch];
      if (!s.ready) {
        // If a receiver is already waiting, deliver immediately
        if (!s.recv_waiters.empty()) {
          auto* rcv = s.recv_waiters.front(); s.recv_waiters.pop_front();
          s.ptr = d.addr; s.len = d.len; s.rid = d.region_id; s.off = d.offset; s.ready = false; // consumed immediately
          sched_->enqueue_ready(rcv);
          return 0;
        }
        s.ptr = d.addr; s.len = d.len; s.rid = d.region_id; s.off = d.offset; s.ready = true; s.parked_sender = cur; park = true;
      } else {
        park = true; // payload present, wait for consumption
      }
    }
    if (park) {
      if (tmo_ms >= 0) {
        uint64_t now = platform::now_ns(); if (now >= deadline) return KC_ETIME;
        long remain_ms = (long)((deadline - now)/1000000ULL); if (remain_ms<1) remain_ms=1; sched_->wake_after(cur, remain_ms);
      }
      cur->park();
      if (tmo_ms >= 0 && platform::now_ns() >= deadline) return KC_ETIME;
      // if resumed by receiver, success
      std::lock_guard<std::mutex> lk(mu_);
      auto it = st_.find(ch);
      if (it==st_.end()) return KC_EPIPE;
      if (!it->second.ready && it->second.parked_sender==nullptr) return 0;
    }
  }
}

int ZRefRendezvous::recv(void* ch, ZDesc& d, long tmo_ms) {
  auto deadline = (tmo_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)tmo_ms * 1000000ULL);
  for (;;) {
    Coroutine* cur = Coroutine::current(); if (!cur) return KC_EAGAIN;
    Coroutine* parked_sender = nullptr; bool wait=false; bool from_select_sender=false; SelSend sel_sender{};
    {
      std::lock_guard<std::mutex> lk(mu_);
      auto& s = st_[ch];
      if (s.ready) {
        d.addr = s.ptr; d.len = s.len; d.region_id = s.rid; d.offset = s.off; s.ready = false; parked_sender = s.parked_sender; s.parked_sender = nullptr; 
        if (!parked_sender && !s.ssend.empty()) { sel_sender = s.ssend.front(); s.ssend.pop_front(); from_select_sender = true; }
      } else {
        // no payload, queue receiver
        s.recv_waiters.push_back(cur); wait = true;
      }
    }
    if (!wait) { if (parked_sender) sched_->enqueue_ready(parked_sender); if (from_select_sender) { if (sel_sender.sel->try_complete(sel_sender.idx, 0)) if (auto* w=sel_sender.sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); } return 0; }
    if (tmo_ms >= 0) {
      uint64_t now = platform::now_ns(); if (now >= deadline) return KC_ETIME;
      long remain_ms = (long)((deadline - now)/1000000ULL); if (remain_ms<1) remain_ms=1; sched_->wake_after(cur, remain_ms);
    }
    cur->park();
    if (tmo_ms >= 0 && platform::now_ns() >= deadline) return KC_ETIME;
  }
}

void ZRefRendezvous::on_close(void* ch) {
  std::lock_guard<std::mutex> lk(mu_);
  auto it = st_.find(ch);
  if (it == st_.end()) return;
  if (it->second.parked_sender) sched_->enqueue_ready(it->second.parked_sender);
  for (auto* r : it->second.recv_waiters) sched_->enqueue_ready(r);
  for (auto& sr : it->second.srecv) { sr.sel->try_complete(sr.idx, KC_EPIPE); if (auto* w = sr.sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); }
  for (auto& ss : it->second.ssend) { ss.sel->try_complete(ss.idx, KC_EPIPE); if (auto* w = ss.sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w)); }
  st_.erase(it);
}

int ZRefRendezvous::select_register_recv(void* ch, ISelect* sel, int clause_index, ZDesc* out) {
  std::lock_guard<std::mutex> lk(mu_);
  auto& s = st_[ch];
  // Prefer matching a pending select sender if present
  if (!s.ssend.empty()) {
    auto ss = s.ssend.front(); s.ssend.pop_front();
    out->addr = ss.val.addr; out->len = ss.val.len; out->region_id = ss.val.region_id; out->offset = ss.val.offset; out->flags = ss.val.flags;
    if (sel->try_complete(clause_index, 0)) if (auto* w = sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    if (ss.sel->try_complete(ss.idx, 0)) if (auto* w = ss.sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    return 0;
  }
  // Otherwise, consume non-select sender if one published
  if (s.ready) {
    out->addr = s.ptr; out->len = s.len; out->region_id = s.rid; out->offset = s.off; out->flags = 0; s.ready = false;
    Coroutine* to_wake = s.parked_sender; s.parked_sender = nullptr;
    if (sel->try_complete(clause_index, 0)) if (auto* w = sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    if (to_wake) sched_->enqueue_ready(to_wake);
    return 0;
  }
  // No counterpart yet: register as select receiver
  s.srecv.push_back({sel, clause_index, out});
  return KC_EAGAIN;
}

int ZRefRendezvous::select_register_send(void* ch, ISelect* sel, int clause_index, const ZDesc* val) {
  std::lock_guard<std::mutex> lk(mu_);
  auto& s = st_[ch];
  // Prefer matching a pending select receiver
  if (!s.srecv.empty()) {
    auto sr = s.srecv.front(); s.srecv.pop_front();
    sr.out->addr = val->addr; sr.out->len = val->len;
    if (sr.sel->try_complete(sr.idx, 0)) if (auto* w = sr.sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    if (sel->try_complete(clause_index, 0)) if (auto* w = sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    return 0;
  }
  // Or a plain waiting receiver
  if (!s.recv_waiters.empty()) {
    auto* rcv = s.recv_waiters.front(); s.recv_waiters.pop_front();
    // Deliver directly to rcv by publishing then waking
    s.ptr = val->addr; s.len = val->len; s.rid = val->region_id; s.off = val->offset; s.ready = false; s.parked_sender = nullptr;
    sched_->enqueue_ready(rcv);
    if (sel->try_complete(clause_index, 0)) if (auto* w = sel->waiter()) sched_->enqueue_ready(static_cast<Coroutine*>(w));
    return 0;
  }
  // No counterpart: enqueue pending select sender only (do not publish globally)
  s.ssend.push_back({sel, clause_index, *val});
  return KC_EAGAIN;
}

void ZRefRendezvous::select_cancel(void* ch, ISelect* sel, int clause_index, SelectOp kind) {
  std::lock_guard<std::mutex> lk(mu_);
  auto& s = st_[ch];
  if (kind == SelectOp::Recv) {
    for (auto it = s.srecv.begin(); it != s.srecv.end(); ++it) {
      if (it->sel == sel && it->idx == clause_index) { s.srecv.erase(it); break; }
    }
  } else {
    for (auto it = s.ssend.begin(); it != s.ssend.end(); ++it) {
      if (it->sel == sel && it->idx == clause_index) { s.ssend.erase(it); break; }
    }
  }
}
