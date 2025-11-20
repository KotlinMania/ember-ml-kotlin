# KLang Type System

KLang provides C-compatible types that replicate exact C semantics in pure Kotlin multiplatform code.

## Type Categories

### Integer Types
- **[Integer Types Documentation](integer-types.md)** - Extended precision integers
  - `C_UInt128` - Unsigned 128-bit integer (matches C `__uint128`)
  - `C_Int128` - Signed 128-bit integer (matches C `__int128`)
  - `SwAR128` - SIMD-within-a-register 128-bit operations (experimental)

### Floating-Point Types
- **[Floating-Point Documentation](floating-point.md)** - C-compatible floating-point
  - `CDouble` - IEEE-754 binary64 (matches C `double`)
  - `CLongDouble` - Extended precision floating-point with configurable profiles
  - `CFloat128` - Quadruple precision (planned)

## Naming Convention

All C-compatible types are prefixed with `C_` to indicate they directly correspond to C types:
- `C_UInt128` ↔ `__uint128` or `unsigned __int128`
- `C_Int128` ↔ `__int128`

This distinguishes them from Kotlin native types and makes the C correspondence explicit.

## Design Principles

1. **Bit-Exact Compatibility** - Types produce identical results to C implementations
2. **Zero-Copy Operations** - All operations work directly on GlobalHeap without copying
3. **Platform Independence** - Same behavior on JVM, Native, and JS
4. **Profile Support** - Floating-point types support different precision profiles matching various C implementations

## See Also

- [Heap Documentation](../heap/README.md) - Memory layout and zero-copy operations
- [BitShiftEngine](../bitshift-engine/README.md) - Bitwise operations used by types
- [I/O and Views](../io-views/README.md) - Reading and writing typed values
