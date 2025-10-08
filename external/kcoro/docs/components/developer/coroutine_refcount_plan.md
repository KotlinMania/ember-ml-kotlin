# Coroutine Lifetime Stabilization Plan

The TLS preservation fix removed one failure vector, but the channel stress tool still trips a crash in `kcoro_resume` after returning from
`kcoro_switch`. The thread backtrace shows workers touching a `kcoro_t` that has already been torn down. We need a structured refcount/retire
flow that ensures coroutines live until **all** workers are done with them.

## Observed symptom
- Headless runs (`./build/kcoro_mon -H -d 1`) intermittently crash with `EXC_BAD_ACCESS` inside `kcoro_resume` at the assignment
  `current_kcoro = yield_co`.
- The faulting address sits in the TLS segment, implying the coroutine’s TLS pointer was stale—likely because the `kcoro_t` object was freed
  or recycled.

