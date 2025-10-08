#pragma once

#include "kcoro_cpp/core.hpp"
#include "kcoro_cpp/coroutine.hpp"
#include "kcoro_cpp/channel.hpp"
#include <string>
#include <unordered_map>
#include <deque>

namespace kcoro_cpp {

// Minimal backend registry
  class ZCopyRegistry {
  public:
    static ZCopyRegistry& instance() { static ZCopyRegistry g; return g; }
    ZCopyRegistry() = default;
  int register_backend(const std::string& name, IZcopyBackend* b) {
    int id = next_id_++; backends_[name] = {id, b}; return id;
  }
  IZcopyBackend* resolve(const std::string& name) const {
    auto it = backends_.find(name); return (it==backends_.end()) ? nullptr : it->second.ops;
  }
  // Region registry (stubbed parity surface)
  using RegionId = uint64_t;
  RegionId region_register(void* base, size_t len);
  bool region_incref(RegionId id);
  bool region_decref(RegionId id);
  bool region_deregister(RegionId id);
  bool region_query(RegionId id, void*& base, size_t& len) const;
  // Meta API
  void set_meta(RegionId id, const RegionMeta& m);
  bool get_meta(RegionId id, RegionMeta& m) const;
  // Aligned allocation helper
  bool alloc_aligned(size_t size, size_t align, void*& base, RegionId& id);
  private:
    mutable std::mutex mu_;
  struct Entry { int id; IZcopyBackend* ops; };
  std::unordered_map<std::string, Entry> backends_;
  int next_id_{0};
  struct Region {
    void* base{}; size_t len{}; uint64_t refs{}; bool dereg{false};
    mutable std::mutex mu; std::condition_variable cv;
    RegionMeta meta{}; bool has_meta{false};
    Region() = default;
    Region(void* b, size_t l, uint64_t r, bool d) : base(b), len(l), refs(r), dereg(d) {}
    Region(const Region&) = delete; Region& operator=(const Region&) = delete;
    Region(Region&& other) noexcept {
      base = other.base; len = other.len; refs = other.refs; dereg = other.dereg;
    }
    Region& operator=(Region&& other) noexcept {
      if (this!=&other) { base=other.base; len=other.len; refs=other.refs; dereg=other.dereg; }
      return *this;
    }
  };
  std::unordered_map<RegionId, Region> regions_;
  RegionId next_region_id_{0};
  };

// ZRef rendezvous backend for descriptor channels
class ZRefRendezvous : public IZcopyBackend {
public:
  ZRefRendezvous(WorkStealingScheduler* sched) : sched_(sched) {}
  int send(void* chan, const ZDesc& d, long tmo_ms) override;
  int recv(void* chan, ZDesc& d, long tmo_ms) override;
  void on_close(void* chan);
  // Optional format policy per channel
  void set_required_format(const RegionMeta& m, FormatMask mask, FormatMode mode) { req_meta_=m; mask_=mask; mode_=mode; }
  // Select integration
  int select_register_recv(void* chan, class ISelect* sel, int clause_index, ZDesc* out);
  int select_register_send(void* chan, class ISelect* sel, int clause_index, const ZDesc* val);
  void select_cancel(void* chan, class ISelect* sel, int clause_index, SelectOp kind);
private:
  struct SelRecv { ISelect* sel; int idx; ZDesc* out; };
  struct SelSend { ISelect* sel; int idx; ZDesc val; };
  struct State { bool ready=false; void* ptr=nullptr; size_t len=0; uint64_t rid=0; uint64_t off=0; Coroutine* parked_sender=nullptr; std::deque<Coroutine*> recv_waiters; std::deque<SelRecv> srecv; std::deque<SelSend> ssend; };
  mutable std::mutex mu_;
  std::unordered_map<void*, State> st_;
  WorkStealingScheduler* sched_{};
  // format policy
  RegionMeta req_meta_{}; FormatMask mask_{0}; FormatMode mode_{FormatMode::Advisory};
};

// ZRef buffered/unlimited via pointer-descriptor buffered channel
class ZRefBuffered : public IZcopyBackend {
public:
  ZRefBuffered(BufferedChannel<ZDesc>* chan) : ch_(chan) {}
  void set_required_format(const RegionMeta& m, FormatMask mask, FormatMode mode) { req_meta_=m; mask_=mask; mode_=mode; }
  int send(void* /*chan*/, const ZDesc& d, long tmo_ms) override {
    if (d.region_id && mask_) {
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
    return ch_->send(d, tmo_ms);
  }
  int recv(void* /*chan*/, ZDesc& d, long tmo_ms) override { return ch_->recv(d, tmo_ms); }
private:
  BufferedChannel<ZDesc>* ch_{};
  RegionMeta req_meta_{}; FormatMask mask_{0}; FormatMode mode_{FormatMode::Advisory};
};

// Convenience channel types
class ZRefRendezvousChannel : public IChannel<ZDesc> {
public:
  explicit ZRefRendezvousChannel(WorkStealingScheduler* sched) : backend_(sched) {}
  void require_format(const RegionMeta& m, FormatMask mask, FormatMode mode) { backend_.set_required_format(m,mask,mode); }
  int send(const ZDesc& d, long tmo_ms) override { return backend_.send(this, d, tmo_ms); }
  int recv(ZDesc& d, long tmo_ms) override { return backend_.recv(this, d, tmo_ms); }
  int send_c(const ZDesc& d, long timeout_ms, const ICancellationToken* cancel) override {
    auto deadline = (timeout_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)timeout_ms * 1000000ULL);
    for(;;){ int rc = backend_.send(this, d, 0); if (rc==0 || rc==KC_EPIPE) return rc; if(cancel && cancel->is_set()) return KC_ECANCELED; if(timeout_ms>=0 && platform::now_ns()>=deadline) return KC_ETIME; auto* cur=Coroutine::current(); if(!cur) return KC_EAGAIN; cur->park(); }
  }
  int recv_c(ZDesc& d, long timeout_ms, const ICancellationToken* cancel) override {
    auto deadline = (timeout_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)timeout_ms * 1000000ULL);
    for(;;){ int rc = backend_.recv(this, d, 0); if (rc==0 || rc==KC_EPIPE) return rc; if(cancel && cancel->is_set()) return KC_ECANCELED; if(timeout_ms>=0 && platform::now_ns()>=deadline) return KC_ETIME; auto* cur=Coroutine::current(); if(!cur) return KC_EAGAIN; cur->park(); }
  }
  void close() override { closed_=true; backend_.on_close(this); }
  size_t size() const override { return 0; }
  // zref rendezvous capabilities
  ChannelSnapshot snapshot() const { ChannelSnapshot s; s.caps = ChannelCaps::ZeroCopy; return s; }
  // Select hooks
  int select_register_recv(ISelect* sel, int clause_index, ZDesc* out) override { return backend_.select_register_recv(this, sel, clause_index, out); }
  int select_register_send(ISelect* sel, int clause_index, const ZDesc* val) override { return backend_.select_register_send(this, sel, clause_index, val); }
  void select_cancel(ISelect* sel, int clause_index, SelectOp kind) override { backend_.select_cancel(this, sel, clause_index, kind); }
private:
  ZRefRendezvous backend_;
  bool closed_{false};
};

class ZRefBufferedChannel : public IChannel<ZDesc> {
public:
  ZRefBufferedChannel(WorkStealingScheduler* sched, size_t cap)
  : buffered_(sched, cap), backend_(&buffered_) {}
  void require_format(const RegionMeta& m, FormatMask mask, FormatMode mode) { backend_.set_required_format(m,mask,mode); }
  int send(const ZDesc& d, long tmo_ms) override { return backend_.send(this, d, tmo_ms); }
  int recv(ZDesc& d, long tmo_ms) override { return backend_.recv(this, d, tmo_ms); }
  void close() override { buffered_.close(); }
  size_t size() const override { return buffered_.size(); }
  ChannelSnapshot snapshot() const { auto s = buffered_.snapshot(); s.caps |= ChannelCaps::ZeroCopy; return s; }
  int send_c(const ZDesc& d, long timeout_ms, const ICancellationToken* cancel) override {
    auto deadline = (timeout_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)timeout_ms * 1000000ULL);
    for(;;){ int rc = backend_.send(this, d, 0); if (rc==0 || rc==KC_EPIPE) return rc; if(cancel && cancel->is_set()) return KC_ECANCELED; if(timeout_ms>=0 && platform::now_ns()>=deadline) return KC_ETIME; auto* cur=Coroutine::current(); if(!cur) return KC_EAGAIN; cur->park(); }
  }
  int recv_c(ZDesc& d, long timeout_ms, const ICancellationToken* cancel) override {
    auto deadline = (timeout_ms < 0) ? (uint64_t)(-1) : (platform::now_ns() + (uint64_t)timeout_ms * 1000000ULL);
    for(;;){ int rc = backend_.recv(this, d, 0); if (rc==0 || rc==KC_EPIPE) return rc; if(cancel && cancel->is_set()) return KC_ECANCELED; if(timeout_ms>=0 && platform::now_ns()>=deadline) return KC_ETIME; auto* cur=Coroutine::current(); if(!cur) return KC_EAGAIN; cur->park(); }
  }
  // Select hooks delegate directly (typed)
  int select_register_recv(ISelect* sel, int clause_index, ZDesc* out) override { return buffered_.select_register_recv(sel, clause_index, out); }
  int select_register_send(ISelect* sel, int clause_index, const ZDesc* val) override { return buffered_.select_register_send(sel, clause_index, val); }
  void select_cancel(ISelect* sel, int clause_index, SelectOp kind) override { buffered_.select_cancel(sel, clause_index, kind); }
private:
  BufferedChannel<ZDesc> buffered_;
  ZRefBuffered backend_;
};

} // namespace kcoro_cpp
