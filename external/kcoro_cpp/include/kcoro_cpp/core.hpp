// kcoro_cpp: C++-first coroutine system with inheritable types (scheduler, channel, select)
#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <memory>
#include <stdexcept>

namespace kcoro_cpp {

struct Error : std::runtime_error { using std::runtime_error::runtime_error; };

// Error codes (negative like -errno style)
constexpr int KC_EAGAIN    = -11;   // try would block
constexpr int KC_EPIPE     = -32;   // closed
constexpr int KC_ETIME     = -62;   // timeout
constexpr int KC_ECANCELED = -125;  // canceled
constexpr int KC_ENOTSUP   = -95;   // not supported

// Capabilities
enum class ChannelCaps : uint32_t {
  None      = 0,
  Ptr       = 1u << 0,  // pointer/len
  ZeroCopy  = 1u << 1,  // zref backend active
};
inline ChannelCaps operator|(ChannelCaps a, ChannelCaps b){ return ChannelCaps(uint32_t(a)|uint32_t(b)); }
inline ChannelCaps& operator|=(ChannelCaps& a, ChannelCaps b){ a = a|b; return a; }

// Cancellation ---------------------------------------------------------------
struct ICancellationToken {
  virtual ~ICancellationToken() = default;
  virtual void trigger() = 0;
  virtual bool is_set() const = 0;
  virtual int wait(long timeout_ms) const = 0;
};

// Coroutine context ----------------------------------------------------------
class ICoroutineContext {
public:
  virtual ~ICoroutineContext() = default;
  virtual void resume() = 0;            // switch into this context
  virtual void park() = 0;              // transition to parked
  virtual bool is_parked() const = 0;   // parked state
  virtual void set_name(const char*) = 0;
};

// Scheduler ------------------------------------------------------------------
class IScheduler {
public:
  virtual ~IScheduler() = default;
  virtual void spawn(void (*fn)(void*), void* arg, size_t stack_bytes = 64*1024) = 0;
  virtual void enqueue_ready(ICoroutineContext* co) = 0;
  virtual void drain(long timeout_ms) = 0;
};

// Channel base ---------------------------------------------------------------
enum class SelectOp; // fwd decl for IChannel
template<typename T>
class IChannel {
public:
  virtual ~IChannel() = default;
  // 0 on success; negative error codes on failure
  virtual int send(const T& val, long timeout_ms = -1) = 0;
  virtual int recv(T& out, long timeout_ms = -1) = 0;
  // Cancellable variants (optional token)
  virtual int send_c(const T& val, long timeout_ms, const ICancellationToken* cancel) = 0;
  virtual int recv_c(T& out, long timeout_ms, const ICancellationToken* cancel) = 0;
  virtual void close() = 0;
  virtual size_t size() const = 0;
  // Select registration hooks
  virtual int select_register_recv(class ISelect* sel, int clause_index, T* out) = 0;
  virtual int select_register_send(class ISelect* sel, int clause_index, const T* val) = 0;
  virtual void select_cancel(class ISelect* sel, int clause_index, SelectOp kind) = 0;
};

// Select ---------------------------------------------------------------------
enum class SelectOp { Recv, Send };

struct SelectClauseBase {
  virtual ~SelectClauseBase() = default;
};

class ISelect {
public:
  virtual ~ISelect() = default;
  virtual void reset() = 0;
  virtual void add_clause(SelectClauseBase* c) = 0;
  virtual int wait(long timeout_ms, int* index_out, int* op_result) = 0;
  virtual bool try_complete(int clause_index, int result) = 0;
  virtual ICoroutineContext* waiter() = 0;
};

// Zero-copy descriptor -------------------------------------------------------
struct ZDesc {
  void*    addr{};   // pointer to payload
  size_t   len{};    // length in bytes
  uint64_t region_id{};
  uint64_t offset{};
  uint32_t flags{};
};

// DType and RegionMeta (for SIMD/layout-aware zero-copy)
enum class DType : uint32_t { Unspec=0, U8,S8, U16,S16, FP16,BF16, U32,S32, FP32, U64,S64, FP64, U128, Opaque128 };

struct RegionMeta {
  DType    dtype{DType::Unspec};
  uint32_t elem_bits{0};
  uint32_t align_bytes{0};
  uint32_t stride_bytes{0};
  uint8_t  ndims{0};
  uint64_t dims[4]{};
  uint32_t layout{0};
  uint32_t flags{0};
};

using FormatMask = uint64_t;
constexpr FormatMask FMT_DTYPE    = 1ull<<0;
constexpr FormatMask FMT_ELEMBITS = 1ull<<1;
constexpr FormatMask FMT_ALIGN    = 1ull<<2;
constexpr FormatMask FMT_STRIDE   = 1ull<<3;
constexpr FormatMask FMT_DIMS     = 1ull<<4;
constexpr FormatMask FMT_LAYOUT   = 1ull<<5;

enum class FormatMode : uint32_t { Advisory=0, Strict=1 };

// Map to KC_* style error codes used in channels
constexpr int KC_EINVAL = -22;

class IZcopyBackend {
public:
  virtual ~IZcopyBackend() = default;
  virtual int send(void* chan, const ZDesc& d, long tmo_ms) = 0;
  virtual int recv(void* chan, ZDesc& d, long tmo_ms) = 0;
};

} // namespace kcoro_cpp
