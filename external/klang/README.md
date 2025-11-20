# KLangNative

**Native-first, C-layout systems programming library in pure Kotlin** — optimized for deterministic, bit-exact low‑level primitives on Kotlin/Native (and JVM). JavaScript parity is out-of-scope until a Node/WASM addon exists.

## What is KLangNative?

KLangNative is **not** a cinterop wrapper or FFI layer. It's a **pure Kotlin implementation** that precisely replicates C's bitwise operations, memory model, and numeric behavior—with a priority on native performance and ABI fidelity. JS support is deferred; native heap and C/Metal interop are the focus.

## Why KLangNative Exists

Multiple large-scale C-to-Kotlin porting projects failed or took months to debug due to subtle behavioral differences between Kotlin and C:

- **16-bit C code** had different sign extension and overflow behavior
- **Double-double floating point** algorithms broke due to Kotlin's multiple rounding steps
- **Cryptographic implementations** produced incorrect results from platform-specific bitwise operations
- **High-precision computation (HPC)** code couldn't be reliably ported
- **Mark Adler's compression algorithms** relied on specific C bit manipulation semantics

KLangNative solves these issues by implementing C semantics in pure, portable Kotlin with a priority on native performance and ABI fidelity.

## Why KLangNative?

KLangNative bridges the gap between Kotlin's high-level abstractions and C's low-level control, enabling:

- **Exact C behavior**: Bit-exact replication of C operations in pure Kotlin
- **Cross-platform determinism (native/JVM)**: Identical behavior across Kotlin/Native and JVM with explicit little-endian semantics
- **C code porting**: Direct mapping of C memory models, pointer arithmetic, and type semantics
- **Performance focus**: Native-speed operations; arithmetic mode available when bit-exact C compatibility is required

## Key Innovations

### 1. Single-Heap Memory Model
All C-style memory lives in a single `GlobalHeap` backed by a pure Kotlin `ByteArray`. Pointers are `Int` byte offsets, providing:
- **16-byte aligned allocator** (`KMalloc`) with coalescing, splitting, and size-class bins
- **Deterministic typed I/O**: All loads/stores are little-endian across platforms
- **Complete libc surface**: `malloc/calloc/realloc/free`, `strlen/strcmp/memcpy/memmove`, etc.

### 2. Intent-Driven Floating Point
Unlike C's platform-dependent `long double`, KLang provides explicit control:
- **`CDouble`**: IEEE-754 binary64 (exact on all targets)
- **`CLongDouble`**: Selectable flavors — `DOUBLE64`, `EXTENDED80`, or `IEEE128`
- **`CFloat128`**: Double-double arithmetic (~106-bit mantissa precision)

**Benchmark Results** (100M summations of 1e-8, expected: 1.0):
```
Simple double:       1.000000082740371 (error: 8.27e-08)  
Kahan compensated:   1.000000000000000 (error: 1.11e-16, 1.8x slower)
KLang double-double: 1.000000000000000 (error: 4.44e-31, 2.1x slower)
```

Double-double provides **~15 orders of magnitude** better precision than compensated summation while maintaining consistent behavior across all platforms.

### 3. BitShift Engine with Dual Modes
A unified shift interface supporting both performance and C-exact determinism:
- **NATIVE mode**: Fast path using Kotlin's native `shl/shr/ushr` operations (use only after validation)
- **ARITHMETIC mode**: Pure multiply/divide/add operations that exactly replicate C behavior across all platforms
- **Forbidden practices**: Raw Kotlin bitwise operators and hard-coded masks are prohibited outside BitShiftEngine to ensure C compatibility
- **Array operations**: Efficient multi-limb shifts for arbitrary-precision arithmetic

**Why this matters**: Kotlin's `shl`, `shr`, and bitwise operators have platform-specific behavior that breaks C algorithm ports. A single misplaced `and 0xFF` can cause cryptographic or compression algorithms to produce different results on different platforms.

### 4. C-Compatible Type System
All C types have Klang equivalents with exact semantic matching:
- **Standard integers**: `C_UInt8`, `C_UInt16`, `C_UInt32`, `C_UInt64` (and signed variants)
- **Extended integers**: `C_UInt128`, `C_Int128` (matching GCC/Clang's `__uint128` and `__int128`)
- **SwAR128**: Experimental SIMD-within-a-register 128-bit operations (Klang's own invention)
- **Floating point**: `CFloat`, `CDouble`, `CLongDouble`, `CFloat128` with configurable precision profiles

All types use heap-based storage for zero-copy operations and exact C memory layout.

## Platform Support

- **Native**: macOS (ARM64, x64), Linux (x64, ARM64), Windows (x64 via MinGW)
- **JVM**: runs with identical semantics via the pure Kotlin implementation
- **JavaScript**: currently out-of-scope; requires a Node/WASM addon for zero-copy, C-layout buffers

## Use Cases

- **C code porting**: Port C libraries and algorithms to pure Kotlin multiplatform with confidence
- **Cross-platform numeric algorithms**: Implement once in C-style, get identical results everywhere
- **Legacy code migration**: Migrate 16-bit or 32-bit C code without behavioral regressions
- **High-precision computing**: Extended precision without platform-specific dependencies or GMP
- **Cryptography**: Deterministic operations critical for security implementations
- **Compression algorithms**: Port zlib, bzip2, or similar with exact C behavior
- **Systems programming in Kotlin**: Memory management, pointer arithmetic, bit manipulation

## Architecture Highlights

**Memory Management**
- `GlobalArrayHeap`: Single expandable heap with typed load/store operations
- `KMalloc`: Production-ready allocator with coalescing and fragmentation control
- `CPointer<T>`: Type-safe pointer abstraction over Int offsets

**Numerical Computing**
- `BitShiftEngine`: Configurable shift strategies (native vs arithmetic)
- `ArrayBitShifts`: Efficient multi-limb operations for arbitrary-precision math
- `HeapUInt128`: 128-bit unsigned integer with full arithmetic support
- `CFloat128`: Double-double precision (~31 decimal digits)

**C Library Compatibility**
- String operations: `strlen`, `strcmp`, `strcpy`, `strncpy`, `strchr`, `strstr`
- Memory operations: `memcpy`, `memmove`, `memset`, `memchr`, `memcmp`
- All operations use word-at-a-time loops for high throughput

## Documentation

See `docs/` for architecture, heap, bitshift engine, porting guide, and testing strategy. Start at `docs/README.md`.

## Building

### Requirements

- JDK 11 or higher
- Gradle 8.x (wrapper included)

### Build Commands

```bash
# Build all targets
./gradlew build

# Build web/JS target
./gradlew jsWeb

# Build native executable (macOS ARM64)
./gradlew buildMac

# Run native executable
./gradlew run

# Build Docker image (Linux ARM64)
./gradlew buildDockerImage
```

### Testing

```bash
# Run all tests
./gradlew test

# Run JS tests only
./gradlew jsTest

# Run native tests (macOS ARM64)
./gradlew macosArm64Test
```

## Project Structure

```
src/
├── commonMain/kotlin/ai/solace/klang/
│   ├── mem/              # Memory management (GlobalHeap, KMalloc, CLib)
│   ├── bitwise/          # BitShiftEngine, array operations
│   ├── fp/               # Floating-point types (CDouble, CLongDouble, CFloat128)
│   ├── int/hpc/          # HeapUInt128 and arbitrary-precision integers
│   └── stringshift/      # String manipulation utilities
├── jsMain/              # JavaScript-specific implementations
└── nativeMain/          # Native platform implementations

tools/                   # C reference implementations for validation
docs/klang/             # Comprehensive documentation
```

## Examples

### Memory Allocation and Pointers
```kotlin
import ai.solace.klang.mem.*

// Initialize heap
GlobalHeap.init(4096)

// Allocate memory
val ptr = Runtime.kmalloc(256)
ptr.store(0x12345678)
val value = ptr.load()

// C-style string operations
val str = "Hello, KLang!".toCString()
val len = CLib.strlen(str)

Runtime.kfree(ptr)
```

### High-Precision Arithmetic
```kotlin
import ai.solace.klang.fp.CFloat128

// Double-double precision
val a = CFloat128(1.0 / 3.0)  // ~106-bit mantissa
val b = CFloat128(2.0 / 3.0)
val sum = a + b
// Result is accurate to ~31 decimal digits
```

### Arbitrary-Precision Shifts
```kotlin
import ai.solace.klang.bitwise.*

// Configure shift mode
BitShiftConfig.defaultMode = BitShiftMode.NATIVE

// Shift large limb arrays
val limbs = intArrayOf(0x1234, 0xABCD, 0x00FF, 0xC0DE)
val shifted = ArrayBitShifts.shl16LEInPlace(limbs, bits = 37)
```

## Performance

KLang is designed for production use with performance characteristics competitive with native C implementations:

- **Native mode shifts**: Near-zero overhead vs Kotlin's built-in operations
- **Allocator**: O(1) small allocations via size-class bins, efficient coalescing
- **String operations**: Word-at-a-time processing (4-8 bytes per iteration)
- **Double-double arithmetic**: 2-3x slower than double, 15+ orders of magnitude more precise

See [`docs/klang/11-poc-benchmark.md`](docs/klang/11-poc-benchmark.md) for detailed performance analysis.

## Contributing

Contributions welcome! Areas of interest:
- Additional C library functions
- Platform-specific optimizations
- Extended precision rounding modes
- Documentation and examples

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
