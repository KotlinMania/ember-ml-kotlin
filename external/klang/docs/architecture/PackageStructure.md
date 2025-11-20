# KLang Package Structure

## Overview

KLang follows a clear organizational structure that separates public packages from internal implementation details. This design enables:

- **Clear Boundaries**: Public packages are stable and documented
- **Internal Flexibility**: Implementation details can evolve without affecting consumers
- **Direct Imports**: Users import directly from component packages
- **Maintainability**: Related functionality is logically grouped

## Package Organization

```
ai.solace.klang/
├── bitwise/               # PUBLIC - Bit manipulation and shift operations
│   ├── BitShiftEngine.kt  # Arithmetic-mode bit shifting
│   ├── CFloat32.kt        # 32-bit IEEE-754 float
│   ├── SwAR.kt           # SIMD Within A Register operations
│   └── ... (other bitwise utilities)
│
├── fp/                    # PUBLIC - Floating point types
│   ├── CDouble.kt        # 64-bit double
│   ├── CFloat16.kt       # 16-bit half precision
│   ├── CBF16.kt          # Brain Float 16
│   ├── CFloat128.kt      # 128-bit quad precision
│   └── CLongDouble.kt    # Platform long double
│
├── int/                   # PUBLIC - Extended precision integers
│   ├── C_UInt128.kt      # Unsigned 128-bit integer
│   ├── C_Int128.kt       # Signed 128-bit integer
│   └── hpc/              # High-performance computing variants
│       └── HeapUInt128.kt
│
├── mem/                   # PUBLIC - Memory management
│   ├── GlobalArrayHeap.kt # Global heap allocator
│   ├── KMalloc.kt        # malloc/free implementation
│   ├── CLib.kt           # C standard library functions
│   ├── CString.kt        # Null-terminated strings
│   └── CScalars.kt       # Heap-backed scalar variables
│
├── common/               # PUBLIC - Shared utilities
│   ├── StatOps.kt
│   └── StructLayout.kt
│
├── stringshift/          # PUBLIC - String manipulation
│   └── HexShift.kt
│
├── internal/             # INTERNAL - May change without notice
│   ├── runtime/
│   │   └── Runtime.kt    # AbstractRuntime for transpiled C code
│   ├── symbols/
│   │   └── CLibSymbols.kt # C function symbols
│   ├── CStringExt.kt     # CPointer extensions for strings
│   └── PointerExtensions.kt # CPointer utility methods
│
└── KLangExports.kt       # Main library documentation
```


## Public Package Usage

### Importing Types

Users import directly from component packages:

```kotlin
import ai.solace.klang.bitwise.CFloat32
import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.fp.CDouble
import ai.solace.klang.int.C_UInt128
import ai.solace.klang.mem.GlobalHeap

// Use the types
val x = CFloat32.fromFloat(3.14f)
val y = C_UInt128.fromLongs(0, 1)
val ptr = GlobalHeap.mallocBytes(100)
```

### Public Packages

**ai.solace.klang.bitwise**
- `CFloat32`, `BitShiftEngine`, `BitShiftConfig`, `BitwiseOps`
- `BitPrimitives`, `Float32Math`, `Float64Math`
- `SwAR`, `SwAR128`, `DoubleDouble`

**ai.solace.klang.fp**
- `CDouble`, `CFloat16`, `CBF16`, `CFloat128`, `CLongDouble`

**ai.solace.klang.int**
- `C_UInt128`, `C_Int128`

**ai.solace.klang.mem**
- `CPointer<T>`, `GlobalHeap`, `KMalloc`, `CLib`, `CString`
- `CByteVar`, `CShortVar`, `CIntVar`, `CLongVar`, `CFloatVar`, `CDoubleVar`

**ai.solace.klang.common**
- `StatOps`, `StructLayout`, `ZlibLogger`

**ai.solace.klang.stringshift**
- `HexShift`

## Internal Implementation

### When to Use Internal Packages

Internal packages are for:
- Library implementation details
- Runtime support for transpiled C code
- Internal utilities and helpers
- Experimental or unstable features

### Accessing Internal APIs

If you must use internal APIs (not recommended):

```kotlin
import ai.solace.klang.internal.runtime.AbstractRuntime
import ai.solace.klang.internal.symbols.strdupCString
```

**Warning**: Internal APIs may change at any time without notice.

## Design Principles

### 1. Package Stability

Public packages follow semantic versioning:
- **Major version**: Breaking changes to public packages
- **Minor version**: New features, backward compatible
- **Patch version**: Bug fixes only

Internal packages may change without notice.

### 2. Zero-Copy Operations

All operations work directly on the heap:
- No intermediate allocations
- Pointers are Int offsets into ByteArray
- Results written in-place when possible

### 3. C Compatibility

Types match C semantics exactly:
- Same bit patterns as C types
- Same arithmetic behavior (including overflow)
- Same memory layout for structs

### 4. Multiplatform Support

All code is pure Kotlin/Common:
- No platform-specific implementations
- Same behavior on JVM, JS, Native
- No native interop required

## Migration from Old Structure

Previously, KLang used an `api` package with type aliases. The new structure uses direct imports.

### Before
```kotlin
import ai.solace.klang.api.CFloat32
import ai.solace.klang.api.GlobalHeap
```

### After
```kotlin
import ai.solace.klang.bitwise.CFloat32
import ai.solace.klang.mem.GlobalHeap
```

## Adding New Public Types

When adding a new type to the library:

1. Implement the type in the appropriate component package (`bitwise/`, `fp/`, `int/`, `mem/`, etc.)
2. Document the type thoroughly with KDoc
3. Add usage examples
4. Update this document

## Performance Considerations

The package structure has zero runtime overhead:
- Direct imports resolve to actual classes
- No wrapper objects or delegation
- Direct function calls after inlining

## Questions?

See `docs/architecture/` for detailed component documentation.
