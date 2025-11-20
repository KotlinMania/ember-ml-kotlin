KLang General Documentation
===========================

## Overview & Architecture
- [overview.md](overview.md) — Goals, principles, layout
- [philosophy-and-design.md](philosophy-and-design.md) — Design principles and rationale
- [component-architecture.md](component-architecture.md) — Component interconnections and data flow

## Development
- [build-and-run.md](build-and-run.md) — Gradle tasks, targets, entry point
- [kdoc-strategy.md](kdoc-strategy.md) — KDoc documentation standards
- [roadmap.md](roadmap.md) — Near-term work and defaults

## Library Features
- [clib-strings.md](clib-strings.md) — CLib, CString, FastMem/FastStringMem
- [poc-benchmark.md](poc-benchmark.md) — Actor/zero-copy/native shift benchmark

## Component Documentation

For detailed component documentation, see:
- [Memory Management](../components/heap/README.md) - GlobalHeap, KMalloc, CPointer
- [Bitwise Operations](../components/bitshift-engine/README.md) - BitShiftEngine and ArrayBitShifts
- [Type System](../components/types/) - Integer and floating-point types
- [I/O and Views](../components/io-views/README.md) - Typed loads/stores
- [Porting Guide](../components/porting/README.md) - C to KLang migration
- [Testing](../components/testing/README.md) - Test architecture

## Key Achievements

- **198 tests** (100% pass rate on macOS ARM64)
- **Zero-copy operations** across all major components
- **Cross-platform determinism** (JS, Native)
- **Production-ready** memory management
- **IEEE-754 compliant** floating-point operations
