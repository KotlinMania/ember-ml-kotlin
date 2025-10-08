#pragma once

#ifdef __cplusplus
extern "C" {
#endif

typedef void* kcoro_scheduler_handle;
typedef void (*kcoro_task_fn)(void*);

kcoro_scheduler_handle kcoro_cpp_sched_init(int workers);
void kcoro_cpp_sched_shutdown(kcoro_scheduler_handle sched);
kcoro_scheduler_handle kcoro_cpp_sched_default(void);
kcoro_scheduler_handle kcoro_cpp_sched_io(void);
int kcoro_cpp_sched_spawn(kcoro_scheduler_handle sched, kcoro_task_fn fn, void* arg, unsigned long stack_bytes);
int kcoro_cpp_sched_spawn_co(kcoro_scheduler_handle sched, kcoro_task_fn fn, void* arg, unsigned long stack_bytes);
void kcoro_cpp_sched_yield(void);
void kcoro_cpp_sched_sleep_ms(int ms);

#ifdef __cplusplus
}
#endif
