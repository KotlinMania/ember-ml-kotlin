# Archived kcoro Experiments

This folder contains historical experiments that relied on pthread-based coroutine simulations or
predecessor schedulers. They are retained for reference only and are **not** part of the supported
ARM64 coroutine runtime.

Archived content includes:
- `legacy_pthreads/` – pthread-only actors/channels used before the assembly runtime existed.
- `legacy_ctx/` – early shared-stack context experiments superseded by `kcoro_switch`.
- `scheduler_broken/` – the initial pthread task queue prototype.
- `experimental_async/` – async/await prototypes built on the legacy scheduler.

Please do not build or modify these sources when working on the active coroutine implementation.
