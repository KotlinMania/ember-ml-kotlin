# kcoro Lab Experiment: Minimal Context Switching

This directory hosts the active kcoro lab experiments that exercise the real ARM64 coroutine runtime.

## Active Experiments

1. **lab_minimal_switch.c** – bare-minimum coroutine context switch driven by `kcoro_switch`.
2. **lab_stack_test.c** – stack save/restore validation on the shared-stack model.  
3. **lab_debug_setup.c** – debug scaffolding for context setup using the current runtime.

> **Note:** Archived compatibility experiments (including legacy shared-stack artifacts) have been moved out of the active tree to `lab/archive_removed/` to avoid shipping external runtime sources. They are retained only for legal/history review.

## Build

```bash
make lab_minimal_switch
./build/lab_minimal_switch
```

## Goals

- Validate the ARM64 assembly fast path (`kcoro_switch`).
- Exercise stack save/restore mechanics before wiring higher-level features.
- Provide small, focused harnesses for debugging the live coroutine runtime.
