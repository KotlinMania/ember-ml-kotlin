#pragma once

#include "kcoro_cpp_all.hpp"

extern "C" {

inline kcoro_cpp::WorkStealingScheduler* kcoro_cpp_sched_init_bridge(int workers) {
    kcoro_cpp::SchedulerOptions opts{};
    opts.workers = workers;
    return kcoro_cpp::sched_init(opts);
}

inline void kcoro_cpp_sched_shutdown_bridge(kcoro_cpp::WorkStealingScheduler* sched) {
    kcoro_cpp::sched_shutdown(sched);
}

inline kcoro_cpp::WorkStealingScheduler* kcoro_cpp_sched_default_bridge() {
    return kcoro_cpp::sched_default();
}

inline kcoro_cpp::WorkStealingScheduler* kcoro_cpp_sched_current_bridge() {
    return kcoro_cpp::sched_current();
}

inline int kcoro_cpp_sched_spawn_bridge(kcoro_cpp::WorkStealingScheduler* sched,
                                        void (*fn)(void*), void* arg, size_t stack_bytes) {
    return kcoro_cpp::sched_spawn(sched, fn, arg);
}

inline int kcoro_cpp_sched_spawn_co_bridge(kcoro_cpp::WorkStealingScheduler* sched,
                                           kcoro_cpp::Coroutine::Fn fn,
                                           void* arg,
                                           size_t stack_bytes,
                                           kcoro_cpp::Coroutine** out_co) {
    return kcoro_cpp::sched_spawn_co(sched, fn, arg, stack_bytes, out_co);
}

inline void kcoro_cpp_sched_enqueue_ready_bridge(kcoro_cpp::WorkStealingScheduler* sched,
                                                 kcoro_cpp::ICoroutineContext* co) {
    kcoro_cpp::sched_enqueue_ready(sched, co);
}

inline void kcoro_cpp_sched_yield_bridge() {
    kcoro_cpp::sched_yield();
}

inline void kcoro_cpp_sched_sleep_ms_bridge(int ms) {
    kcoro_cpp::sched_sleep_ms(ms);
}

}

