cmake_minimum_required(VERSION 3.18)
project(kcoro_cpp C CXX ASM)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB KCORO_CPP_SOURCES
  src/*.cpp
)

add_library(kcoro_cpp STATIC ${KCORO_CPP_SOURCES} arch/kc_ctx_switch.S)
target_include_directories(kcoro_cpp PUBLIC ${CMAKE_CURRENT_LIST_DIR}/include)
target_link_libraries(kcoro_cpp PUBLIC pthread)

if(KCORO_CPP_ASAN)
  target_compile_options(kcoro_cpp PUBLIC -fsanitize=address -fno-omit-frame-pointer -O1 -g)
  target_link_options(kcoro_cpp PUBLIC -fsanitize=address)
endif()

install(TARGETS kcoro_cpp ARCHIVE DESTINATION lib)
install(DIRECTORY ${CMAKE_CURRENT_LIST_DIR}/include/ DESTINATION include)

add_subdirectory(tools/chanmon)
add_subdirectory(tools/pingpong)
add_subdirectory(tools/zref_bench)
add_subdirectory(tools/tests)
add_subdirectory(tools/channel_stress)
option(KCORO_CPP_ASAN "Build kcoro_cpp with AddressSanitizer" OFF)

# -----------------------------------------------------------------------------
# Instrumentation / Hardening Controls
# KCORO_CPP_INTERNAL_CTX_DEBUG (compile definition) enables additional runtime
# context integrity diagnostics (canary, LR history, SP/FP bounds, exec range
# widening) that were used to root-cause historical memory corruption and UAF
# classes in earlier scheduler/channel implementations. These checks are
# intended for:
#   - Forensic reproduction of elusive crashes (enable at runtime via env)
#   - Stress/CI builds looking for silent corruption
#   - Differential runs versus "clean" production config
# They add per-context fields and perform extra validation on each context
# switch. Overhead: small additional memory in CoContext; runtime cost only
# when env var KCORO_CPP_DEBUG_CTX_CHECK is set (otherwise early-return).
#
# HOW TO USE:
#   cmake -DKCORO_CPP_CTX_DIAGNOSTICS=ON .. && make
#   KCORO_CPP_DEBUG_CTX_CHECK=1 ./your_binary
#
# SAFE FOR PRODUCTION?  Recommended OFF by default to minimize binary surface.
# Guard page stacks are ALWAYS enabled (structural safety); diagnostics are
# additive and optional.
# -----------------------------------------------------------------------------
option(KCORO_CPP_CTX_DIAGNOSTICS "Enable coroutine context diagnostics (adds canary/LR/stack checks; activate at runtime with KCORO_CPP_DEBUG_CTX_CHECK=1)" OFF)
if(KCORO_CPP_CTX_DIAGNOSTICS)
  target_compile_definitions(kcoro_cpp PUBLIC KCORO_CPP_INTERNAL_CTX_DEBUG=1)
endif()

# Provide a helper target that prints available hardening options.
add_custom_target(kcoro_cpp-help ALL
  COMMAND ${CMAKE_COMMAND} -E echo "kcoro_cpp build options:"
  COMMAND ${CMAKE_COMMAND} -E echo "  -DKCORO_CPP_ASAN=ON             : AddressSanitizer build"
  COMMAND ${CMAKE_COMMAND} -E echo "  -DKCORO_CPP_CTX_DIAGNOSTICS=ON   : Compile context integrity instrumentation (enable at runtime with KCORO_CPP_DEBUG_CTX_CHECK=1)"
  COMMAND ${CMAKE_COMMAND} -E echo "Environment flags:"
  COMMAND ${CMAKE_COMMAND} -E echo "  KCORO_CPP_DEBUG_CTX_CHECK=1      : Turn on runtime context validation (when compiled with diagnostics)."
  VERBATIM
)
