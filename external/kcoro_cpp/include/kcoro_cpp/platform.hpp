#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <stdexcept>
#include <chrono>
#include <sys/mman.h>
#include <unistd.h>

namespace kcoro_cpp::platform {

inline std::size_t page_size() {
  long p = ::sysconf(_SC_PAGESIZE);
  return p > 0 ? static_cast<std::size_t>(p) : 4096u;
}

struct MMapStack {
  // ptr/size describe the USABLE stack memory (excluding guard page)
  void* ptr{};
  std::size_t size{};
  // raw mapping base/size include guard page so we can munmap properly
  void* raw_ptr{};
  std::size_t raw_size{};
  static MMapStack allocate(std::size_t bytes) {
    std::size_t ps = page_size();
    if (bytes == 0) bytes = ps; // ensure at least one page usable
    std::size_t usable = (bytes + ps - 1) & ~(ps - 1);
    std::size_t total = usable + ps; // one guard page at low address
    void* mem = ::mmap(nullptr, total, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (mem == MAP_FAILED) throw std::runtime_error("mmap stack failed");
    // Protect the first (lowest) page as guard (stack grows downward)
    if (::mprotect(mem, ps, PROT_NONE) != 0) {
      ::munmap(mem, total);
      throw std::runtime_error("mprotect guard page failed");
    }
    void* usable_base = static_cast<char*>(mem) + ps;
    return {usable_base, usable, mem, total};
  }
  void release() {
    if (raw_ptr && raw_size) ::munmap(raw_ptr, raw_size);
    ptr = nullptr; size = 0; raw_ptr = nullptr; raw_size = 0;
  }
};

inline std::uint64_t now_ns() {
  using namespace std::chrono;
  return duration_cast<nanoseconds>(steady_clock::now().time_since_epoch()).count();
}

} // namespace kcoro_cpp::platform

