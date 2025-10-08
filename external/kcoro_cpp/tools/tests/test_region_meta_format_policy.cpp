// Zero-Copy Region Meta and Format Policy Tests (C++)
#include <cassert>
#include <cstdlib>
#include <iostream>
#include "kcoro_cpp/core.hpp"
#include "kcoro_cpp/zref.hpp"
#include "kcoro_cpp/scheduler.hpp"

using namespace kcoro_cpp;

static void test_region_meta_basic() {
  std::cout << "=== C++ Test: Region Meta Basic ===\n";
  auto& reg = ZCopyRegistry::instance();
  void* base = nullptr; ZCopyRegistry::RegionId id=0;
  bool ok = reg.alloc_aligned(4096, 64, base, id); assert(ok && id!=0);
  RegionMeta m{}; m.dtype=DType::FP16; m.elem_bits=16; m.align_bytes=64; m.stride_bytes=128; m.ndims=2; m.dims[0]=32; m.dims[1]=64;
  reg.set_meta(id, m);
  RegionMeta got{}; assert(reg.get_meta(id, got));
  assert(got.dtype==DType::FP16 && got.elem_bits==16 && got.align_bytes==64 && got.stride_bytes==128 && got.ndims==2 && got.dims[0]==32 && got.dims[1]==64);
  reg.region_deregister(id); std::free(base);
}

static void test_format_policy_strict() {
  std::cout << "=== C++ Test: Format Policy Strict ===\n";
  auto& reg = ZCopyRegistry::instance(); WorkStealingScheduler sched(1);
  ZRefBufferedChannel ch(&sched, 8);
  RegionMeta req{}; req.dtype=DType::FP32; req.elem_bits=32; req.align_bytes=32; req.stride_bytes=0; ch.require_format(req, FMT_DTYPE|FMT_ELEMBITS|FMT_ALIGN, FormatMode::Strict);
  void* b1=nullptr; ZCopyRegistry::RegionId id1=0; reg.alloc_aligned(1024, 32, b1, id1); reg.set_meta(id1, req); ZDesc d1{b1,1024,id1,0,0}; assert(ch.send(d1,0)==0);
  void* b2=nullptr; ZCopyRegistry::RegionId id2=0; reg.alloc_aligned(1024, 32, b2, id2); RegionMeta mm=req; mm.dtype=DType::FP16; reg.set_meta(id2, mm); ZDesc d2{b2,1024,id2,0,0}; assert(ch.send(d2,0)==KC_EINVAL);
  void* b3=nullptr; ZCopyRegistry::RegionId id3=0; reg.alloc_aligned(1024, 16, b3, id3); RegionMeta ua=req; ua.align_bytes=16; reg.set_meta(id3, ua); ZDesc d3{b3,1024,id3,0,0}; assert(ch.send(d3,0)==KC_EINVAL);
  reg.region_deregister(id1); reg.region_deregister(id2); reg.region_deregister(id3); std::free(b1); std::free(b2); std::free(b3);
}

static void test_format_policy_advisory() {
  std::cout << "=== C++ Test: Format Policy Advisory ===\n";
  auto& reg = ZCopyRegistry::instance(); WorkStealingScheduler sched(1);
  ZRefRendezvousChannel ch(&sched);
  RegionMeta req{}; req.dtype=DType::U32; req.elem_bits=32; req.align_bytes=16; ch.require_format(req, FMT_DTYPE|FMT_ALIGN, FormatMode::Advisory);
  void* b=nullptr; ZCopyRegistry::RegionId id=0; reg.alloc_aligned(1024, 8, b, id); RegionMeta am=req; am.dtype=DType::U16; am.align_bytes=8; reg.set_meta(id, am);
  ZDesc d{b,1024,id,0,0}; int rc = ch.send(d,0); assert(rc==0);
  reg.region_deregister(id); std::free(b);
}

int main(){
  test_region_meta_basic();
  test_format_policy_strict();
  test_format_policy_advisory();
  std::cout << "All C++ region meta/format tests passed\n";
  return 0;
}

