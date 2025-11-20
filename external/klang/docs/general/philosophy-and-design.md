KLang Philosophy and Design Principles
======================================

## What KLang Is

KLang is a **pure Kotlin multiplatform library** that provides exact C semantics in idiomatic Kotlin code. It is **not**:
- A cinterop wrapper
- An FFI layer
- A JNI bridge
- A binding to native C libraries

KLang implements C behavior from scratch in pure, portable Kotlin that runs identically on JVM, JavaScript, and Native platforms.

## Why KLang Exists

### The C-to-Kotlin Porting Problem

Multiple large-scale projects attempted to port C code to Kotlin and failed or required months of debugging:

1. **16-bit C Code Ports**
   - Sign extension behavior differed
   - Overflow handling was inconsistent
   - Bit manipulation produced different results

2. **Double-Double Floating Point**
   - Kotlin performs multiple rounding steps during operations
   - C performs single rounding at the end
   - High-precision algorithms (106-bit mantissa) broke completely

3. **Cryptographic Implementations**
   - Platform-specific bitwise operations produced different hashes
   - Security vulnerabilities introduced by incorrect bit behavior
   - Test vectors passed on one platform but failed on others

4. **High-Precision Computing (HPC)**
   - Arbitrary-precision arithmetic depended on exact carry/overflow behavior
   - Limb-based multi-precision math broke due to platform differences
   - Accumulation errors from rounding differences ruined results

5. **Mark Adler's Compression Algorithms**
   - zlib, gzip implementations relied on specific C bit manipulation
   - Word-at-a-time loops needed exact C semantics
   - Checksum calculations depended on overflow behavior

### Root Causes

Kotlin's design decisions cause subtle incompatibilities with C:

#### 1. Platform-Specific Bitwise Operators

```kotlin
// Kotlin's shl/shr/ushr behavior varies by platform
val shifted = value shl 8  // Different on JS vs JVM vs Native
val masked = value and 0xFF  // Breaks on different bit widths
```

**Why this breaks C ports:**
- JavaScript has 32-bit limitations
- Sign extension differs between platforms
- Overflow behavior is inconsistent
- Masks don't adapt to variable bit widths

#### 2. Multiple Rounding in Floating Point

```kotlin
// Kotlin
val result = a * b + c * d  // Rounds after *, then after +

// C
double result = a * b + c * d;  // Single rounding at end
```

**Why this breaks C ports:**
- Double-double algorithms depend on single rounding
- Error accumulation differs
- Precision guarantees break

#### 3. No Multi-Precision Support

C provides `unsigned long long` and compiler extensions (`__uint128`), while Kotlin's largest primitive is `Long` (64-bit). Multi-precision arithmetic requires exact carry/overflow tracking that Kotlin primitives don't provide.

## The KLang Solution

### 1. BitShiftEngine: Controlled Bitwise Operations

**Problem**: Kotlin's native operators have platform-specific behavior.

**Solution**: Single engine with dual modes:

```kotlin
// ARITHMETIC mode: Pure arithmetic ops (*, /, +, -) that exactly replicate C
val arithmetic = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
val result = arithmetic.leftShift(value, 8)  // Identical on all platforms

// NATIVE mode: Uses shl/shr for speed, after validation
val native = BitShiftEngine(BitShiftMode.NATIVE, 32)
val fast = native.leftShift(value, 8)  // Fast, but requires validation
```

**Key Features**:
- Bit-width awareness (8, 16, 32, 64 bits)
- Carry/overflow tracking
- Mask generation for any bit width
- Zero-copy heap operations

**Critical Rules**:
- FORBIDDEN: Raw Kotlin bitwise operators outside BitShiftEngine
- FORBIDDEN: Hard-coded masks (0xFF, 0xFFFF, etc.)
- REQUIRED: All bitwise ops through BitShiftEngine
- REQUIRED: Use getMask(bits) for bit-width-safe masking

### 2. C-Compatible Type System

**Problem**: Kotlin types don't map 1:1 to C types.

**Solution**: Every C type has a KLang equivalent with exact semantics:

```kotlin
// Standard C types
C_UInt8, C_Int8       // unsigned char, signed char
C_UInt16, C_Int16     // unsigned short, short
C_UInt32, C_Int32     // unsigned int, int
C_UInt64, C_Int64     // unsigned long long, long long

// Compiler extensions
C_UInt128, C_Int128   // __uint128, __int128 (GCC/Clang)

// Floating point
CFloat                // float
CDouble               // double
CLongDouble           // long double (with flavor selection)
CFloat128             // double-double (~106-bit mantissa)

// Experimental
SwAR128               // SIMD-within-a-register (KLang invention)
```

**Naming Convention**:
- C-compatible types: `C_` prefix (e.g., `C_UInt128`)
- Follows C convention: `__uint128` → `C_UInt128` (leading underscores preserved)
- KLang inventions: No prefix (e.g., `SwAR128`)

### 3. Single-Heap Memory Model

**Problem**: Kotlin's memory model doesn't match C's pointer-based model.

**Solution**: GlobalHeap — single ByteArray with typed I/O:

```kotlin
// C
unsigned int *ptr = malloc(sizeof(unsigned int));
*ptr = 42;

// KLang equivalent
val ptr: Int = KMalloc.malloc(4)  // Returns byte offset
GlobalHeap.sw(ptr, 42)  // Store word at offset
val value = GlobalHeap.lw(ptr)  // Load word from offset
```

**Features**:
- Single expandable ByteArray backing all memory
- Pointers are Int byte offsets (not object references)
- 16-byte aligned allocator (KMalloc)
- Little-endian load/store operations
- Complete libc surface (malloc/free/memcpy/strcmp/etc.)

### 4. Zero-Copy Operations

**Problem**: Copying data between Kotlin arrays and C-style memory kills performance.

**Solution**: All types store data directly in the heap:

```kotlin
// BAD: Copying to/from arrays
val limbs = UIntArray(4)  // Allocates Kotlin array
heap.copyTo(limbs)        // Copy from heap
compute(limbs)            // Compute on array
heap.copyFrom(limbs)      // Copy back to heap

// GOOD: Direct heap operations
val ptr: Int = ...        // Heap offset
compute(heap, ptr, 4)     // Operate directly on heap
```

**No Persistent Kotlin Arrays**:
- Arrays only as short-lived function temporaries
- All long-lived data in GlobalHeap
- All operations in-place on heap

## Design Principles

### 1. Pure Kotlin Multiplatform

**DO**:
- Implement all C semantics in pure Kotlin
- Write platform-agnostic code
- Use `expect`/`actual` only for true platform differences

**DON'T**:
- Use cinterop or FFI
- Depend on native libraries
- Use JVM-specific features

### 2. Bit-Exact C Replication

**DO**:
- Match C behavior exactly, bit for bit
- Test against C reference implementations
- Use ARITHMETIC mode for initial ports

**DON'T**:
- Assume Kotlin operators match C
- Use platform-specific optimizations before validation
- Tolerate "close enough" results

### 3. Zero-Copy, In-Place Operations

**DO**:
- Operate directly on GlobalHeap
- Use typed load/store operations
- Pass heap offsets between functions

**DON'T**:
- Copy data to Kotlin arrays for processing
- Create temporary allocations in hot paths
- Store persistent data in Kotlin collections

### 4. Enforce Through Architecture

**DO**:
- Centralize bitwise operations in BitShiftEngine
- Provide type-safe APIs that prevent misuse
- Make incorrect code impossible to write

**DON'T**:
- Trust developers to follow guidelines
- Allow raw operator access
- Permit hard-coded masks

### 5. Document Everything

**DO**:
- Write comprehensive KDoc for all public APIs
- Explain why each design decision was made
- Provide examples of correct usage

**DON'T**:
- Assume intent is obvious
- Leave edge cases undocumented
- Skip rationale for constraints

## Testing Strategy

### 1. Dual-Mode Validation

Test both NATIVE and ARITHMETIC modes produce identical results:

```kotlin
@Test
fun testOperation() {
    val arithmetic = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
    val native = BitShiftEngine(BitShiftMode.NATIVE, 32)
    
    val input = 0x12345678L
    
    val result1 = arithmetic.leftShift(input, 8).value
    val result2 = native.leftShift(input, 8).value
    
    assertEquals(result1, result2)  // Must match
}
```

### 2. C Reference Comparison

Compare KLang output with C reference implementations:

```c
// test.c
uint128_t c_add(uint128_t a, uint128_t b) {
    return a + b;
}
```

```kotlin
// Test.kt
@Test
fun testAgainstCReference() {
    val a = C_UInt128(...)
    val b = C_UInt128(...)
    val result = a + b
    
    // Compare with output from compiled C code
    assertEquals(expectedFromC, result.toString())
}
```

### 3. Cross-Platform Consistency

Run identical tests on JVM, JS, and Native:

```kotlin
// commonTest
@Test
fun testCrossPlatform() {
    val result = computeHash(data)
    
    // Must produce identical hash on all platforms
    assertEquals("expected_hash", result)
}
```

### 4. Edge Case Coverage

Test boundary conditions:

- Zero values
- Maximum values (overflow)
- Negative numbers (sign extension)
- Odd/even lengths
- Aligned/unaligned addresses

## Performance Philosophy

### Start Correct, Optimize Later

1. **Correctness First**: Use ARITHMETIC mode for initial implementation
2. **Validate Behavior**: Extensive testing against C references
3. **Optimize Selectively**: Switch to NATIVE mode only after validation
4. **Benchmark**: Measure actual impact before optimizing

### When Speed Matters

- Use NATIVE mode after validation
- Implement platform-specific fast paths with `expect`/`actual`
- Profile before optimizing
- Never sacrifice correctness for speed

### When Correctness Matters

- Always use ARITHMETIC mode for:
  - Cryptography
  - Financial calculations
  - Scientific computing
  - Initial C ports
- Accept performance trade-off for determinism

## Common Pitfalls

### Using Raw Bitwise Operators

```kotlin
// WRONG - Platform-specific behavior
val result = value shl 8
```

```kotlin
// CORRECT - Deterministic behavior
val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
val result = engine.leftShift(value, 8).value
```

### Hard-Coded Masks

```kotlin
// WRONG - Only works for 8-bit values
val masked = value and 0xFF
```

```kotlin
// CORRECT - Works for any bit width
val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, bitWidth)
val masked = engine.bitwiseAnd(value, engine.getMask(8))
```

### Copying to/from Arrays

```kotlin
// WRONG - Inefficient copying
val array = UIntArray(size)
heap.copyTo(array, offset, size)
process(array)
heap.copyFrom(array, offset, size)
```

```kotlin
// CORRECT - In-place operations
process(heap, offset, size)
```

### Assuming Kotlin == C

```kotlin
// WRONG - Kotlin rounds differently
val result = a * b + c * d
```

```kotlin
// CORRECT - Use KLang floating point
val result = CFloat128.fma(a, b, CFloat128.multiply(c, d))
```

## Success Criteria

A KLang implementation is successful when:

1. Produces bit-identical results to C reference on all platforms
2. No raw Kotlin bitwise operators outside BitShiftEngine
3. No hard-coded masks anywhere in codebase
4. All operations in-place on GlobalHeap
5. 100% test coverage with dual-mode validation
6. Comprehensive KDoc documentation
7. Performance within 2-5× of C (ARITHMETIC mode)
8. Performance within 1.2× of C (NATIVE mode, after validation)

## Future Directions

### 1. Enhanced Type System

- Complex numbers
- Fixed-point arithmetic
- SIMD types beyond SwAR128

### 2. Advanced Memory Management

- Memory pools
- Arena allocators
- Garbage collection integration

### 3. Platform-Specific Optimizations

- SIMD intrinsics (where available)
- Hardware crypto acceleration
- Platform-specific fast paths

### 4. Expanded libc Surface

- stdio (printf, scanf)
- math library (sin, cos, sqrt)
- More POSIX functions

## Conclusion

KLang exists to solve a real problem: **C code cannot be reliably ported to Kotlin without exact semantic replication**. By implementing C behavior in pure Kotlin multiplatform code and enforcing correct usage through architectural constraints, KLang makes C-to-Kotlin porting practical, reliable, and verifiable.

The library's constraints (no raw operators, no hard-coded masks, heap-only storage) may seem restrictive, but they exist to prevent the subtle bugs that plagued previous porting efforts. These rules aren't arbitrary—they're lessons learned from real-world failures.

When porting C code to Kotlin, you have two choices:
1. Fight Kotlin's platform-specific behavior and spend months debugging
2. Use KLang and get bit-exact results from day one

KLang chooses correctness over convenience, and determinism over performance. The result is a library that actually works for its intended purpose: enabling successful C-to-Kotlin migration.
