# BitShiftEngine Usage Guide

## Overview

BitShiftEngine is Klang's unified interface for all bitwise operations. It provides cross-platform deterministic bit shifting and manipulation that works consistently across JVM, Native, and JS platforms.

**IMPORTANT**: Klang is **pure Kotlin multiplatform code**, not a cinterop wrapper. It provides idiomatic Kotlin implementations that precisely replicate C bitwise behavior, enabling accurate porting of C code to Kotlin while maintaining exact bit-level compatibility.

## Why BitShiftEngine Exists

### The Core Problem: C-to-Kotlin Porting

Multiple large-scale C-to-Kotlin porting projects failed or required months of debugging due to subtle behavioral differences:

1. **16-bit C code** had different sign extension and overflow behavior
2. **Double-double floating point** algorithms suffered from Kotlin's multiple rounding steps during operations
3. **Cryptographic implementations** produced incorrect results due to platform-specific bitwise behavior
4. **High-precision computation (HPC)** code couldn't be reliably ported

### The Underlying Issues

Kotlin's native bitwise operators (`shl`, `shr`, `ushr`, `and`, `or`, `xor`) have subtle platform-specific behaviors that break C algorithm ports:

- **Arithmetic shifts** differ between platforms (sign extension behavior varies)
- **JavaScript** has 32-bit limitations and different overflow handling
- **Mask operations** don't account for variable bit widths properly, breaking on types that don't align with mask bit length
- **Rounding behavior** in floating-point operations differs from C
- **Multi-precision arithmetic** requires carry/overflow tracking absent in Kotlin primitives

### The Solution

BitShiftEngine and Klang provide:

1. **Mode Selection**: Choose between NATIVE (fast) and ARITHMETIC (bit-exact C replication)
2. **Bit Width Awareness**: Operations respect 8, 16, 32, or 64-bit boundaries exactly as C does
3. **Carry/Overflow Tracking**: Essential for multi-limb arithmetic and C algorithm compatibility
4. **Zero-Copy Heap Operations**: Work directly with memory without allocations, matching C's pointer semantics
5. **Deterministic Cross-Platform Behavior**: Same results on JVM, Native, and JS as the original C code

## Quick Start

### Basic Usage

```kotlin
// Create an engine for 32-bit operations in NATIVE mode
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)

// Shift left by 8 bits
val result = engine.leftShift(0x12345678, 8)
println("0x${result.value.toString(16)}") // 0x34567800

// Extract a specific byte
val byte2 = engine.extractByte(0x12345678, 2)
println("0x${byte2.toString(16)}") // 0x34

// Check if bit is set
val isSet = engine.isBitSet(0b10101010, 5)
println(isSet) // true
```

### Arithmetic vs Native Mode

```kotlin
// Native mode - uses Kotlin's built-in operators (faster)
val native = BitShiftEngine(BitShiftMode.NATIVE, 32)

// Arithmetic mode - uses pure arithmetic (deterministic across platforms)
val arithmetic = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)

// Both produce same results, but arithmetic guarantees cross-platform consistency
val v1 = native.leftShift(0xFF, 4).value      // Fast
val v2 = arithmetic.leftShift(0xFF, 4).value  // Deterministic

assert(v1 == v2)
```

## Core Operations

### Bit Shifting

```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)

// Left shift
val left = engine.leftShift(0x1, 8)
// left.value: 0x100
// left.carry: bits shifted out
// left.overflow: true if bits were lost

// Right shift (unsigned)
val right = engine.unsignedRightShift(0x100, 8)
// right.value: 0x1

// Byte-aligned shifting (optimized for multi-byte operations)
val byteShifted = engine.byteShiftLeft(0x12345678, 1)
// byteShifted.value: 0x34567800 (shifted left by 8 bits)
```

### Bitwise Logic

```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)

val and = engine.bitwiseAnd(0b11110000, 0b10101010)
// and: 0b10100000

val or = engine.bitwiseOr(0b11110000, 0b10101010)
// or: 0b11111010

val xor = engine.bitwiseXor(0b11110000, 0b10101010)
// xor: 0b01011010

val not = engine.bitwiseNot(0b11110000)
// not: 0b00001111
```

### Byte Operations

```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
val value = 0x12345678L

// Extract individual bytes
val byte0 = engine.extractByte(value, 0) // 0x78
val byte1 = engine.extractByte(value, 1) // 0x56
val byte2 = engine.extractByte(value, 2) // 0x34
val byte3 = engine.extractByte(value, 3) // 0x12

// Replace a byte
val modified = engine.replaceByte(value, 1, 0xAA)
// modified: 0x1234AA78

// Compose bytes into a value
val composed = engine.composeBytes(longArrayOf(0x78, 0x56, 0x34, 0x12))
// composed: 0x12345678

// Decompose value into bytes
val bytes = engine.decomposeBytes(value, 4)
// bytes: [0x78, 0x56, 0x34, 0x12]
```

### Bit Manipulation

```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)

// Check if bit is set
val isSet = engine.isBitSet(0b10101010, 5) // true

// Set a bit
val withBitSet = engine.setBit(0b00000000, 3)
// withBitSet: 0b00001000

// Clear a bit
val withBitCleared = engine.clearBit(0b11111111, 3)
// withBitCleared: 0b11110111

// Toggle a bit
val toggled = engine.toggleBit(0b10101010, 0)
// toggled: 0b10101011

// Count set bits (population count)
val count = engine.popCount(0b10101010)
// count: 4
```

### Masks

```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)

// Generate masks for different bit counts
val mask8 = engine.getMask(8)   // 0xFF
val mask16 = engine.getMask(16) // 0xFFFF
val mask32 = engine.getMask(32) // 0xFFFFFFFF

// Use masks for safe operations
val masked = engine.bitwiseAnd(0x12345678, mask16)
// masked: 0x5678
```

### Sign/Zero Extension

```kotlin
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)

// Sign-extend an 8-bit value to 32 bits
val signExtended = engine.signExtend(0xFF, 8)
// signExtended: 0xFFFFFFFF (negative)

val signExtended2 = engine.signExtend(0x7F, 8)
// signExtended2: 0x0000007F (positive)

// Zero-extend (always positive)
val zeroExtended = engine.zeroExtend(0xFF, 8)
// zeroExtended: 0x000000FF
```

## Advanced Usage

### Multi-Precision Arithmetic

```kotlin
val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)

// Simulating 32-bit addition with two 16-bit limbs
fun add32(a: Long, b: Long): Long {
    val aLow = engine.bitwiseAnd(a, 0xFFFF)
    val aHigh = engine.unsignedRightShift(a, 16).value
    val bLow = engine.bitwiseAnd(b, 0xFFFF)
    val bHigh = engine.unsignedRightShift(b, 16).value
    
    // Add low limbs
    val sumLow = aLow + bLow
    val carry = if (sumLow > 0xFFFF) 1L else 0L
    val low = engine.bitwiseAnd(sumLow, 0xFFFF)
    
    // Add high limbs with carry
    val sumHigh = aHigh + bHigh + carry
    val high = engine.bitwiseAnd(sumHigh, 0xFFFF)
    
    // Compose result
    return engine.bitwiseOr(low, engine.leftShift(high, 16).value)
}
```

### Builder Pattern

```kotlin
// Start with a base configuration
val base = BitShiftEngine(BitShiftMode.NATIVE, 32)

// Create variants
val arithmetic = base.withMode(BitShiftMode.ARITHMETIC)
val wider = base.withBitWidth(64)
val narrower = base.withBitWidth(16)

// All are independent instances
```

### Working with Different Bit Widths

```kotlin
// 8-bit operations (byte)
val engine8 = BitShiftEngine(BitShiftMode.NATIVE, 8)
val byte = engine8.bitwiseAnd(0xFF, 0xF0) // 0xF0

// 16-bit operations (short/word)
val engine16 = BitShiftEngine(BitShiftMode.NATIVE, 16)
val word = engine16.bitwiseAnd(0xFFFF, 0xF0F0) // 0xF0F0

// 32-bit operations (int/dword)
val engine32 = BitShiftEngine(BitShiftMode.NATIVE, 32)
val dword = engine32.bitwiseAnd(0xFFFFFFFF, 0xF0F0F0F0) // 0xF0F0F0F0

// 64-bit operations (long/qword)
val engine64 = BitShiftEngine(BitShiftMode.NATIVE, 64)
val qword = engine64.leftShift(1, 32).value // 0x100000000
```

## Rules and Guidelines

### The Klang Philosophy

Klang exists to enable **exact C code porting to pure Kotlin multiplatform**. This means:

- Every C type has a corresponding Klang type (prefixed with `C_`, e.g., `C_UInt128`, `C_Int128`)
- All bitwise operations must go through BitShiftEngine to ensure C-compatible behavior
- Hard-coded masks break variable bit-width compatibility and are forbidden
- The library uses its own implementations, **not** Kotlin/Native cinterop

### CRITICAL: Raw Bitwise Operations Are Forbidden

**DO NOT** use raw Kotlin bitwise operators outside of BitShiftEngine's implementation:

**Why?** Kotlin's operators have platform-specific behavior that breaks C algorithm ports. Even a single `shl` or `and 0xFF` can produce different results than C on different platforms, breaking cryptography, compression, and HPC code.

```kotlin
// FORBIDDEN - Raw operators
val bad1 = value shl 8
val bad2 = value shr 4
val bad3 = value and 0xFF
val bad4 = value or 0xF0
val bad5 = value xor 0xAA

// CORRECT - Use BitShiftEngine
val engine = BitShiftEngine(BitShiftMode.NATIVE, bitWidth)
val good1 = engine.leftShift(value, 8).value
val good2 = engine.rightShift(value, 4).value
val good3 = engine.bitwiseAnd(value, 0xFF)
val good4 = engine.bitwiseOr(value, 0xF0)
val good5 = engine.bitwiseXor(value, 0xAA)
```

### CRITICAL: Hard-Coded Masks Are Forbidden

**DO NOT** use hard-coded masks:

**Why?** A mask like `0xFF` only works for 8-bit values. If applied to values of different bit widths, it produces incorrect results. C code often uses bit-width-aware masking, and hard-coded masks break that. Use `getMask(bits)` which generates the correct mask for any bit width.

```kotlin
// FORBIDDEN - Hard-coded masks
val bad1 = value and 0xFF
val bad2 = value and 0xFFFF
val bad3 = if ((value and 0x80) != 0L) ...

// CORRECT - Use getMask
val engine = BitShiftEngine(BitShiftMode.NATIVE, bitWidth)
val good1 = engine.bitwiseAnd(value, engine.getMask(8))
val good2 = engine.bitwiseAnd(value, engine.getMask(16))
val good3 = if (engine.isBitSet(value, 7)) ...
```

### When to Use Each Mode

**Use NATIVE mode when:**
- Performance is critical AND you've validated bit-exact behavior on all target platforms
- Operating on a single known platform with validated C compatibility
- You're confident the operation doesn't expose platform-specific edge cases

**Use ARITHMETIC mode when:**
- Porting C code (always start here to ensure correctness)
- Cross-platform consistency is required
- Implementing cryptographic algorithms
- Building reference implementations
- Testing/validating against C implementations
- Working with multi-precision or HPC algorithms
- Any code where bit-exact correctness matters more than raw speed

## Performance Considerations

### Mode Performance

| Operation | NATIVE | ARITHMETIC | Difference |
|-----------|--------|------------|------------|
| Left shift by 1 | ~2ns | ~10ns | 5× slower |
| Left shift by 8 | ~2ns | ~40ns | 20× slower |
| Bitwise AND | ~1ns | ~15ns | 15× slower |
| Byte extract | ~3ns | ~20ns | 7× slower |

**Trade-off**: Speed vs determinism

### Optimization Tips

1. **Reuse engine instances** - They're immutable and thread-safe
2. **Use byte-aligned shifts** - byteShiftLeft/Right are optimized
3. **Batch operations** - Process multiple values with same engine
4. **Choose appropriate bit width** - Don't use 64-bit for 8-bit data

```kotlin
// Good - Reuse engine
val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
for (value in values) {
    process(engine.leftShift(value, 8).value)
}

// Bad - Create engine in loop
for (value in values) {
    val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
    process(engine.leftShift(value, 8).value)
}
```

## Common Patterns

### Reading Multi-Byte Integers from Memory

```kotlin
fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
    val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
    val b = LongArray(4) { bytes[offset + it].toLong() and 0xFF }
    return engine.composeBytes(b)
}
```

### Writing Multi-Byte Integers to Memory

```kotlin
fun writeUInt32LE(value: Long, bytes: ByteArray, offset: Int) {
    val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
    val decomposed = engine.decomposeBytes(value, 4)
    for (i in 0..3) {
        bytes[offset + i] = decomposed[i].toByte()
    }
}
```

### Packing Multiple Fields

```kotlin
data class PackedFlags(
    val flag1: Boolean,
    val flag2: Boolean,
    val value: Int,  // 6 bits
)

fun packFlags(flags: PackedFlags): Int {
    val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
    var packed = 0L
    
    if (flags.flag1) packed = engine.setBit(packed, 0)
    if (flags.flag2) packed = engine.setBit(packed, 1)
    
    val valueMasked = engine.bitwiseAnd(flags.value.toLong(), engine.getMask(6))
    packed = engine.bitwiseOr(packed, engine.leftShift(valueMasked, 2).value)
    
    return packed.toInt()
}
```

## Testing

Always test both modes:

```kotlin
@Test
fun testOperation() {
    val native = BitShiftEngine(BitShiftMode.NATIVE, 32)
    val arithmetic = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
    
    val input = 0x12345678L
    
    // Both modes should produce identical results
    val result1 = native.leftShift(input, 8).value
    val result2 = arithmetic.leftShift(input, 8).value
    
    assertEquals(result1, result2)
}
```

## See Also

- [BitShiftEngine API Documentation](../src/commonMain/kotlin/ai/solace/klang/bitwise/BitShiftEngine.kt)
- [ArithmeticBitwiseOps](../src/commonMain/kotlin/ai/solace/klang/bitwise/ArithmeticBitwiseOps.kt)
- [Bitwise Refactoring Analysis](../summaries/bitwise-refactoring-analysis.md)
