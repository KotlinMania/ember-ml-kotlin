#include "kcoro_cpp_bridge_c.h"
#include "kcoro_cpp/scheduler.hpp"
#include "kcoro_cpp/dispatcher.hpp"

extern "C" {

kcoro_scheduler_handle kcoro_cpp_sched_init(int workers) {
    kcoro_cpp::SchedulerOptions opts{};
    opts.workers = workers;
    auto sched = kcoro_cpp::sched_init(opts);
    return static_cast<kcoro_scheduler_handle>(sched);
}

void kcoro_cpp_sched_shutdown(kcoro_scheduler_handle sched) {
    if (!sched) return;
    kcoro_cpp::sched_shutdown(static_cast<kcoro_cpp::WorkStealingScheduler*>(sched));
}

kcoro_scheduler_handle kcoro_cpp_sched_default(void) {
    auto sched = kcoro_cpp::sched_default();
    return static_cast<kcoro_scheduler_handle>(sched);
}

kcoro_scheduler_handle kcoro_cpp_sched_io(void) {
    auto sched = kcoro_cpp::dispatcher_io();
    return static_cast<kcoro_scheduler_handle>(sched);
}

int kcoro_cpp_sched_spawn(kcoro_scheduler_handle sched, kcoro_task_fn fn, void* arg, unsigned long stack_bytes) {
    if (!sched || !fn) return -1;
    return kcoro_cpp::sched_spawn(static_cast<kcoro_cpp::WorkStealingScheduler*>(sched), fn, arg);
}

int kcoro_cpp_sched_spawn_co(kcoro_scheduler_handle sched, kcoro_task_fn fn, void* arg, unsigned long stack_bytes) {
    if (!sched || !fn) return -1;
    return kcoro_cpp::sched_spawn_co(static_cast<kcoro_cpp::WorkStealingScheduler*>(sched), fn, arg, stack_bytes, nullptr);
}

void kcoro_cpp_sched_yield(void) {
    kcoro_cpp::sched_yield();
}

void kcoro_cpp_sched_sleep_ms(int ms) {
    kcoro_cpp::sched_sleep_ms(ms);
}

}
