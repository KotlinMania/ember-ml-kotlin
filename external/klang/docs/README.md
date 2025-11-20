# KLangNative Documentation

KLangNative is a pure Kotlin (native-first) library that provides bit-exact C semantics for porting C code to Kotlin. JS parity is deferred; native performance and ABI fidelity are the priority.

## Getting Started

- **[Overview](general/overview.md)** - Goals, principles, and layout
- **[Build and Run](general/build-and-run.md)** - How to build and run KLangNative
- **[Philosophy and Design](general/philosophy-and-design.md)** - Core design principles

## Core Components

### Memory Management
- **[Global Heap](components/heap/README.md)** - Zero-copy memory arena matching C heap semantics
- **[Allocator (KMalloc)](components/allocator/README.md)** - malloc/calloc/realloc/free implementation
- **[Pointer System](components/pointer-system/README.md)** - C-style pointers (CPointer) over heap addresses

### Bitwise Operations
- **[BitShiftEngine](components/bitshift-engine/README.md)** - Cross-platform deterministic bit shifting with ARITHMETIC and NATIVE modes
  - Provides all bitwise operations (shifts, AND, OR, XOR, masks)
  - Critical for C code porting - **no raw Kotlin bitwise operators allowed**
  - Supports both fast native operations and bit-exact arithmetic mode

### Type System
- **[Integer Types](components/types/integer-types.md)** - C_UInt128, C_Int128, and other extended precision types
- **[Floating Point](components/types/floating-point.md)** - CDouble, CLongDouble with C-compatible behavior

### I/O and Views
- **[Typed I/O and Views](components/io-views/README.md)** - Read/write typed values from heap addresses

## Porting C Code

- **[C to KLangNative Guide](components/porting/README.md)** - Authoritative rules for porting C code
- **[Quick Reference](components/porting/quick-reference.md)** - Quick lookup for common patterns
- **[CLib Strings](general/clib-strings.md)** - String and memory functions (strlen, strcmp, memcpy, etc.)

## Development

- **[Testing Strategy](components/testing/README.md)** - Multiplatform test approach
- **[KDoc Strategy](general/kdoc-strategy.md)** - Documentation standards
- **[Component Architecture](general/component-architecture.md)** - How components interconnect
- **[Roadmap](general/roadmap.md)** - Future development plans
- **[POC Benchmark](general/poc-benchmark.md)** - Performance validation

## Key Principles

### Pure Kotlin Multiplatform
KLangNative is **not** cinterop. It's pure Kotlin code that replicates C semantics exactly, enabling C algorithm ports to work identically across JVM and Native; JS requires a native addon and is currently out-of-scope.

### Zero-Copy Heap Operations
All data lives in GlobalHeap. Variables exist in-place, accessed via typed loads/stores. No copying to/from Kotlin arrays.

### Bit-Exact C Compatibility
- All bitwise operations go through BitShiftEngine
- Hard-coded masks are forbidden - use `getMask(bits)`
- Types prefixed with `C_` map exactly to C types
- Arithmetic mode ensures identical results to C implementations

### Critical Rules

**DO NOT:**
- Use raw Kotlin bitwise operators (`shl`, `shr`, `ushr`, `and`, `or`, `xor`) outside BitShiftEngine
- Use hard-coded bit masks (`0xFF`, `0xFFFF`, etc.)
- Copy data to/from Kotlin arrays for persistent storage
- Add fallback exception handlers that hide bugs

**DO:**
- Use BitShiftEngine for all bitwise operations
- Use `getMask(bits)` for bit-width-safe masking
- Operate directly on heap addresses
- Test both ARITHMETIC and NATIVE modes
- Maintain comprehensive KDoc comments

## Documentation Organization

```
docs/
├── README.md (this file)
├── general/           # Cross-cutting documentation
│   ├── README.md
│   ├── overview.md
│   ├── philosophy-and-design.md
│   ├── component-architecture.md
│   ├── kdoc-strategy.md
│   ├── roadmap.md
│   ├── build-and-run.md
│   ├── clib-strings.md
│   └── poc-benchmark.md
└── components/        # Component-specific docs
    ├── allocator/
    │   └── README.md
    ├── bitshift-engine/
    │   └── README.md
    ├── heap/
    │   └── README.md
    ├── io-views/
    │   └── README.md
    ├── pointer-system/
    │   └── README.md
    ├── porting/
    │   ├── README.md
    │   └── quick-reference.md
    ├── testing/
    │   └── README.md
    └── types/
        ├── integer-types.md
        └── floating-point.md
```

Each component directory contains a README.md with comprehensive documentation. Additional files are provided for complex components with multiple subtopics.
