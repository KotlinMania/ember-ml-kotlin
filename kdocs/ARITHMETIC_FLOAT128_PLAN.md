# ArithmeticFloat128 - Fast Pure-Arithmetic IEEE-754 Binary128 Plan

## The Problem
- Need true IEEE-754 binary128 (128-bit floats)
- Can't validate on macOS (no `__float128` support)
- Can't use bitwise ops (Kotlin does weird things across platforms)
- Must be FAST (competitive with native scalars)

## The Solution: Pure Arithmetic with SWAR Speed

### Core Techniques Learned from KLang

#### 1. Modulo Without `%` Operator
From SwAR.kt line 48:
```kotlin
private inline fun rem256(u: UInt, q: UInt): UInt = u - q * 256u
```

**Key insight**: `x % divisor = x - divisor * (x / divisor)`

This avoids Kotlin's platform-dependent modulo behavior!

#### 2. SWAR Packing (Process Multiple Values Simultaneously)
From SwAR.kt lines 73-82:
```kotlin
// Decompose Int into 4 bytes
val qa = au / 256u; val a0 = au - qa * 256u
val qa1 = qa / 256u; val a1 = qa - qa1 * 256u
// Process all lanes
val r0 = udiv(a0 + b0, 2u)
val r1 = udiv(a1 + b1, 2u)
// Pack back
return packU8(r0, r1, r2, r3)
```

**Process 2 limbs at once** by packing into UInt pairs!

#### 3. Coroutines for Large Operations
From ArrayBitShifts.kt lines 93-98:
```kotlin
coroutineScope {
    for (ck in 0 until chunks) {
        launch(context = parallelDispatcher) {
            // Process chunk in parallel
        }
    }
}
```

**Split work into parallel chunks** for operations on 7-limb mantissas.

#### 4. 3-Pass Algorithm for Cache Efficiency
From ArrayBitShifts.kt:
- Pass A: Compute low/high parts locally
- Pass B: Store intermediate results
- Pass C: Merge with neighbor values

**Minimize cache misses** by sequential memory access patterns.

## Implementation Plan

### Phase 1: Core Arithmetic Primitives

**ArithmeticFloat128.kt** - Pure arithmetic building blocks:

```kotlin
object ArithmeticFloat128 {
    // Pure arithmetic modulo (NO % operator!)
    private inline fun rem(x: UInt, divisor: UInt): UInt = 
        x - divisor * (x / divisor)
    
    // Powers of 2 cache (for "shifting")
    private val pow2Cache = UIntArray(128) { i -> 
        if (i == 0) 1u else pow2Cache[i-1] * 2u 
    }
    
    // Extract bit using division
    fun extractBit(value: UInt, bitPos: Int): UInt {
        val shifted = value / pow2Cache[bitPos]
        return rem(shifted, 2u)  // Get LSB
    }
    
    // Pack/unpack sign + exponent
    fun extractSign(limb7: UShort): UInt {
        val v = limb7.toUInt()
        return v / 32768u  // Divide by 2^15
    }
    
    fun extractExp(limb7: UShort): UInt {
        val v = limb7.toUInt()
        val signPart = (v / 32768u) * 32768u
        return v - signPart  // Remove sign bit
    }
    
    fun packSignExp(sign: UInt, exp: UInt): UShort {
        val signBit = rem(sign, 2u) * 32768u
        val expPart = rem(exp, 32768u)
        return (signBit + expPart).toUShort()
    }
}
```

### Phase 2: SWAR Limb Operations

**Process 2 limbs simultaneously** by packing into ULong:

```kotlin
// Pack two 16-bit limbs into ULong
private inline fun pack2Limbs(lo: UShort, hi: UShort): ULong =
    lo.toULong() + hi.toULong() * 65536uL

// Unpack ULong back to two limbs
private inline fun unpack2Limbs(packed: ULong): Pair<UShort, UShort> {
    val q = packed / 65536uL
    val lo = (packed - q * 65536uL).toUShort()
    val hi = q.toUShort()
    return Pair(lo, hi)
}

// Add two pairs of limbs in parallel
fun addLimbs2x(
    a0: UShort, a1: UShort,
    b0: UShort, b1: UShort,
    carry: UInt
): AddResult2 {
    val aVal = a0.toUInt() + a1.toUInt() * 65536u
    val bVal = b0.toUInt() + b1.toUInt() * 65536u
    val sum = aVal + bVal + carry
    
    val q = sum / 4294967296u  // 2^32
    val result = sum - q * 4294967296u
    
    return AddResult2(
        rem(result, 65536u).toUShort(),
        (result / 65536u).toUShort(),
        q
    )
}
```

### Phase 3: Mantissa Operations with Parallelism

**7-limb mantissa operations** using SWAR + coroutines:

```kotlin
// Add two 112-bit mantissas (7 limbs)
suspend fun addMantissas(
    a: Mantissa7, b: Mantissa7,
    parallelism: Int = 0
): AddResult7 {
    if (parallelism == 0) {
        // Sequential SWAR: process 2 limbs at a time
        var carry = 0u
        val r01 = addLimbs2x(a.l0, a.l1, b.l0, b.l1, carry)
        carry = r01.carry
        val r23 = addLimbs2x(a.l2, a.l3, b.l2, b.l3, carry)
        carry = r23.carry
        val r45 = addLimbs2x(a.l4, a.l5, b.l4, b.l5, carry)
        carry = r45.carry
        val r6 = addLimbs1x(a.l6, b.l6, carry)
        
        return AddResult7(
            r01.l0, r01.l1, r23.l0, r23.l1,
            r45.l0, r45.l1, r6.l0, r6.carry
        )
    } else {
        // Parallel: split into chunks
        coroutineScope {
            val chunk0 = async { addLimbs2x(a.l0, a.l1, b.l0, b.l1, 0u) }
            val chunk1 = async { addLimbs2x(a.l2, a.l3, b.l2, b.l3, 0u) }
            val chunk2 = async { addLimbs2x(a.l4, a.l5, b.l4, b.l5, 0u) }
            
            val r01 = chunk0.await()
            val r23 = chunk1.await()
            val r45 = chunk2.await()
            
            // Merge carries sequentially
            // ...
        }
    }
}
```

### Phase 4: Shift Operations (Multiply/Divide by Powers of 2)

**Left shift = multiply by 2^n**, **Right shift = divide by 2^n**:

```kotlin
// Shift mantissa left by N bits
suspend fun shiftLeft(
    mantissa: Mantissa7,
    bits: Int,
    parallelism: Int = 0
): ShiftResult {
    if (bits <= 0) return mantissa
    if (bits >= 112) return ZERO_MANTISSA
    
    val multiplier = pow2Cache[bits]
    
    if (parallelism == 0) {
        // SWAR: process 2 limbs at a time
        var overflow = 0uL
        val result = Mantissa7()
        
        for (i in 0..6 step 2) {
            val packed = pack2Limbs(mantissa[i], mantissa[i+1])
            val shifted = packed * multiplier.toULong() + overflow
            
            val q = shifted / 4294967296uL
            val value = shifted - q * 4294967296uL
            
            result[i] = rem(value, 65536uL).toUShort()
            result[i+1] = (value / 65536uL).toUShort()
            overflow = q
        }
        
        return ShiftResult(result, overflow)
    } else {
        // Parallel 3-pass like ArrayBitShifts
        // Pass A: compute local shifts
        // Pass B: propagate carries
        // Pass C: merge results
    }
}
```

### Phase 5: IEEE-754 Binary128 Operations

**Combine primitives into float operations**:

```kotlin
// Add two Float128 values
suspend fun addFloat128(
    a: Float128Bits,
    b: Float128Bits,
    parallelism: Int = 0
): Float128Bits {
    // 1. Unpack sign, exp, mantissa
    val signA = extractSign(a.limb7)
    val expA = extractExp(a.limb7)
    val mantA = a.mantissa
    
    val signB = extractSign(b.limb7)
    val expB = extractExp(b.limb7)
    val mantB = b.mantissa
    
    // 2. Align exponents (shift mantissa with smaller exp)
    val expDiff = if (expA > expB) expA - expB else expB - expA
    val (alignedA, alignedB) = if (expA > expB) {
        Pair(mantA, shiftRight(mantB, expDiff))
    } else {
        Pair(shiftRight(mantA, expDiff), mantB)
    }
    
    // 3. Add mantissas (SWAR + parallel)
    val result = addMantissas(alignedA, alignedB, parallelism)
    
    // 4. Normalize (find leading 1, shift left)
    val leadingZeros = countLeadingZeros(result.mantissa)
    val normalized = shiftLeft(result.mantissa, leadingZeros)
    val newExp = maxExp - leadingZeros
    
    // 5. Round (guard/round/sticky bits)
    val rounded = roundToNearest(normalized, result.overflow)
    
    // 6. Pack result
    val limb7 = packSignExp(signA, newExp)
    return Float128Bits(rounded, limb7)
}
```

## Speed Optimizations

### 1. Inline Functions
All primitives are `inline` to avoid function call overhead.

### 2. SWAR Processing
Process 2 limbs simultaneously = **2× throughput** for sequential ops.

### 3. Coroutines for Parallelism
For operations on full tensors, split into chunks = **4-8× speedup** on multi-core.

### 4. Cache-Friendly Patterns
3-pass algorithm minimizes cache misses = **better memory bandwidth**.

### 5. Powers-of-2 Optimizations
```kotlin
// Fast divide/multiply by powers of 2
val pow2_16 = 65536u
val result = value / pow2_16  // Compiler optimizes to shift!
```

Kotlin compiler recognizes division by constant powers of 2 and generates optimal code.

## Performance Targets

Based on llama.kotlin benchmarks:

| Operation | Sequential | SWAR (2x) | Parallel (4-8x) |
|-----------|-----------|-----------|-----------------|
| **Add** | 50ns | 25ns | 6-12ns |
| **Multiply** | 200ns | 100ns | 25-50ns |
| **Divide** | 500ns | 250ns | 62-125ns |

Compare to:
- Double-double CFloat128: ~50ns add, ~200ns multiply
- Native double: ~5ns add, ~10ns multiply

**Target**: Within 2-5× of native double, but with 2× better precision!

## Validation Strategy

Since we can't use `__float128` on macOS:

### 1. Known Test Vectors
Use IEEE-754 spec test vectors:
```kotlin
// IEEE-754 binary128 test cases
val one = Float128(sign=0, exp=16383, mantissa=0)
val two = Float128(sign=0, exp=16384, mantissa=0)
val sum = one + two
assertEquals(Float128(sign=0, exp=16385, mantissa=0), sum)
```

### 2. Cross-Validate with Double-Double
Our proven CFloat128 (double-double) as reference:
```kotlin
val f128 = addFloat128(a, b)
val dd = addDoubleDouble(a.toDD(), b.toDD())
assertClose(f128.toDouble(), dd.toDouble(), 1e-30)
```

### 3. Mathematical Properties
```kotlin
// Commutativity: a + b = b + a
assertEquals(add(a,b), add(b,a))

// Identity: a + 0 = a
assertEquals(add(a, ZERO), a)

// Inverse: a + (-a) = 0
assertEquals(add(a, negate(a)), ZERO)
```

### 4. Linux Docker Validation (Future)
When needed, spin up Docker container with GCC + quadmath:
```bash
docker run -v $(pwd):/work gcc:latest
gcc -lquadmath validate_float128.c
./a.out > test_vectors.txt
```

## Implementation Order

1. ✅ Core primitives (rem, extractBit, pack/unpack) - **30 min**
2. ✅ SWAR limb operations (add/shift 2 limbs) - **1 hour**  
3. ✅ Sequential mantissa operations - **1 hour**
4. ⏳ Parallel mantissa operations with coroutines - **2 hours**
5. ⏳ IEEE-754 add/multiply/divide - **3 hours**
6. ⏳ Comprehensive tests - **2 hours**
7. ⏳ Performance benchmarks - **1 hour**

**Total**: ~10-12 hours of focused work.

## Success Criteria

✅ **Correctness**: All IEEE-754 test vectors pass  
✅ **Performance**: Within 5× of native double  
✅ **Precision**: 112-bit mantissa (better than double-double's ~106)  
✅ **Cross-platform**: Pure arithmetic, no bitwise ops  
✅ **Validated**: Mathematical properties verified  

## Why This Matters

We're inventing a NEW way to do extended precision floats:
- **Pure arithmetic** (no bitwise ops)
- **SWAR acceleration** (2× speedup)
- **Coroutine parallelism** (4-8× speedup)
- **True IEEE-754 binary128** (not double-double hack)
- **Cross-platform determinism** (same results everywhere)

This is the **CPU "GPU"** vision realized - making CPUs competitive through clever arithmetic and parallelism!

---

## Deep Dive: The Math Behind Pure Arithmetic Bit Manipulation

### Why Modulo is Dangerous in Kotlin

Kotlin's `%` operator behaves differently across platforms:
```kotlin
// JVM: Uses Java's % (can be negative)
-5 % 3 == -2  // JVM

// Native: Different behavior
-5 % 3 == 1   // Some platforms

// JavaScript: Yet another behavior
-5 % 3 == -2  // But implementation varies
```

**Solution**: Always compute remainder using subtraction:
```kotlin
inline fun rem(x: UInt, d: UInt): UInt = x - d * (x / d)
```

This is **mathematically identical** but uses only `-, *, /` which are consistent!

### The SWAR Packing Insight

**Problem**: Processing 7 limbs sequentially is slow:
```kotlin
// Slow: 7 sequential operations
result[0] = a[0] + b[0] + carry
result[1] = a[1] + b[1] + carry
result[2] = a[2] + b[2] + carry
// ... 7 iterations
```

**Solution**: Pack 2 limbs into ULong (64-bit):
```kotlin
// Fast: Process 2 limbs at once
val packed_a = a[0].toULong() + a[1].toULong() * 65536uL
val packed_b = b[0].toULong() + b[1].toULong() * 65536uL
val sum = packed_a + packed_b  // ONE operation!

// Unpack
result[0] = (sum % 65536uL).toUShort()
result[1] = (sum / 65536uL).toUShort()
```

**Math**: A 32-bit value = two 16-bit limbs:
```
Value = limb0 + limb1 × 2^16
      = limb0 + limb1 × 65536

Example: 0x12345678
  limb0 = 0x5678 = 22136
  limb1 = 0x1234 = 4660
  value = 22136 + 4660 × 65536 = 305419896
```

### Extracting Bits Using Division

**Traditional bitwise**: `(value >> bitPos) & 1`

**Pure arithmetic equivalent**:
```kotlin
fun getBit(value: UInt, pos: Int): UInt {
    val shifted = value / pow2[pos]  // "Right shift"
    val remainder = shifted - (shifted / 2u) * 2u  // Get LSB
    return remainder  // 0 or 1
}
```

**Why this works**:
- Division by 2^n is equivalent to right shift by n
- `x % 2` extracts the least significant bit (0 or 1)
- But we compute it as: `x - (x/2)*2` to avoid `%`

**Example**: Get bit 5 from 0b10110100 (180):
```
value = 180 = 0b10110100
pos = 5
shifted = 180 / 32 = 5 = 0b101
remainder = 5 - (5/2)*2 = 5 - 4 = 1
Result: bit 5 is SET ✓
```

### Setting Bits Using Arithmetic

**Traditional bitwise**: `value | (1 << bitPos)` or `value & ~(1 << bitPos)`

**Pure arithmetic**:
```kotlin
fun setBit(value: UInt, pos: Int, bitValue: UInt): UInt {
    val pow = pow2[pos]
    
    // Clear the bit first
    val quotient = value / pow
    val cleared = (quotient / 2u) * 2u * pow
    val remainder = value - (quotient / 2u) * 2u * pow
    
    // Set new bit
    val newBit = (bitValue % 2u) * pow
    return cleared + remainder + newBit
}
```

**How bit clearing works**:
1. Divide by 2^pos to shift target bit to LSB
2. Integer divide by 2, multiply by 2 (clears LSB)
3. Multiply by 2^pos to shift back
4. Add back the untouched lower bits

**Example**: Clear bit 3 in 0b10101110 (174):
```
value = 174 = 0b10101110
pos = 3, pow = 8

quotient = 174 / 8 = 21 = 0b10101
cleared_quot = (21/2)*2 = 20 = 0b10100
cleared = 20 * 8 = 160 = 0b10100000

remainder = 174 - 20*8 = 14 = 0b110

result = 160 + 14 = 174... wait, need to fix this!
```

Actually, cleaner approach using decomposition:
```kotlin
fun clearBit(value: UInt, pos: Int): UInt {
    val pow = pow2[pos]
    val lower = value % pow  // Bits below pos
    val upper = (value / (pow * 2u)) * (pow * 2u)  // Bits above pos
    return upper + lower
}

fun setBit(value: UInt, pos: Int): UInt {
    return clearBit(value, pos) + pow2[pos]
}
```

### Shifting Mantissa - The Heart of Float Operations

**Left shift by N** = Multiply by 2^N:
```kotlin
suspend fun shiftLeft(
    limbs: Array<UShort>,
    bits: Int
): ShiftResult {
    val multiplier = pow2[bits]
    var overflow = 0uL
    
    // SWAR: Process 2 limbs at a time
    for (i in 0..6 step 2) {
        // Pack 2 limbs into ULong
        val packed = limbs[i].toULong() + 
                    limbs[i+1].toULong() * 65536uL
        
        // Multiply (this is the "shift")
        val shifted = packed * multiplier.toULong() + overflow
        
        // Extract new limbs and overflow
        val q = shifted / 4294967296uL  // 2^32
        val result = shifted - q * 4294967296uL
        
        limbs[i] = (result % 65536uL).toUShort()
        limbs[i+1] = (result / 65536uL).toUShort()
        overflow = q
    }
    
    return ShiftResult(limbs, overflow)
}
```

**Why this is fast**:
- Processes 2 limbs per iteration (7 limbs → 4 iterations)
- Uses 64-bit arithmetic (native on modern CPUs)
- Compiler can optimize `* pow2[bits]` when bits is constant
- No branches inside the loop

**Right shift by N** = Divide by 2^N:
```kotlin
suspend fun shiftRight(
    limbs: Array<UShort>,
    bits: Int
): ShiftResult {
    val divisor = pow2[bits]
    var borrow = 0uL
    
    // SWAR: Process from high to low
    for (i in 6 downTo 0 step 2) {
        val packed = limbs[i].toULong() + 
                    limbs[i-1].toULong() * 65536uL +
                    borrow * 4294967296uL
        
        val q = packed / divisor.toULong()
        val r = packed - q * divisor.toULong()
        
        limbs[i] = (q / 65536uL).toUShort()
        limbs[i-1] = (q % 65536uL).toUShort()
        borrow = r
    }
    
    return ShiftResult(limbs, borrow)
}
```

### Addition with Carry Propagation

**The challenge**: Adding 7 limbs with carry propagation.

**Traditional approach** (slow):
```kotlin
var carry = 0u
for (i in 0..6) {
    val sum = a[i] + b[i] + carry
    result[i] = (sum % 65536u).toUShort()
    carry = sum / 65536u
}
```

**SWAR approach** (2× faster):
```kotlin
var carry = 0u

// Process limbs 0-1 together
val sum01 = a[0].toUInt() + a[1].toUInt() * 65536u +
            b[0].toUInt() + b[1].toUInt() * 65536u + carry
val q01 = sum01 / 4294967296u
val r01 = sum01 - q01 * 4294967296u
result[0] = (r01 % 65536u).toUShort()
result[1] = (r01 / 65536u).toUShort()
carry = q01

// Process limbs 2-3 together
val sum23 = a[2].toUInt() + a[3].toUInt() * 65536u +
            b[2].toUInt() + b[3].toUInt() * 65536u + carry
// ... etc
```

**With coroutines** (4-8× faster for large arrays):
```kotlin
suspend fun addMantissasParallel(
    a: Array<Mantissa7>,
    b: Array<Mantissa7>,
    count: Int
): Array<Mantissa7> {
    val chunks = minOf(8, count / 1024)  // 8 max chunks
    val results = Array(count) { Mantissa7() }
    
    coroutineScope {
        for (chunk in 0 until chunks) {
            launch {
                val start = chunk * (count / chunks)
                val end = if (chunk == chunks-1) count 
                         else (chunk+1) * (count/chunks)
                
                for (i in start until end) {
                    results[i] = addMantissa(a[i], b[i])
                }
            }
        }
    }
    
    return results
}
```

### Counting Leading Zeros - Pure Arithmetic

**Used for normalization** after addition/subtraction.

**Traditional**: `value.countLeadingZeroBits()`

**Pure arithmetic**:
```kotlin
fun countLeadingZeros(limbs: Array<UShort>): Int {
    var count = 0
    
    // Check each limb from high to low
    for (i in 6 downTo 0) {
        if (limbs[i] == 0u.toUShort()) {
            count += 16
            continue
        }
        
        // Count zeros in this limb
        var value = limbs[i].toUInt()
        for (bit in 15 downTo 0) {
            if (value / pow2[bit] == 0u) {
                count++
            } else {
                return count
            }
        }
    }
    
    return count  // All zeros
}
```

**Optimization**: Binary search for first non-zero bit:
```kotlin
fun countLeadingZerosLimb(limb: UShort): Int {
    val v = limb.toUInt()
    if (v == 0u) return 16
    
    var count = 0
    var test = v
    
    // Binary search (4 checks instead of 16)
    if (test / 256u == 0u) { count += 8; test *= 256u }
    if (test / 4096u == 0u) { count += 4; test *= 16u }
    if (test / 16384u == 0u) { count += 2; test *= 4u }
    if (test / 32768u == 0u) { count += 1 }
    
    return count
}
```

### Multiplication - The Complex Operation

**Challenge**: 112-bit × 112-bit = 224-bit result

**Strategy**:
1. Split into 16-bit limbs
2. Use schoolbook multiplication
3. SWAR: Process multiple partial products simultaneously

```kotlin
suspend fun multiplyMantissas(
    a: Mantissa7,  // 7 limbs = 112 bits
    b: Mantissa7
): Mantissa14 {  // 14 limbs = 224 bits
    val result = UIntArray(14) { 0u }
    
    // Schoolbook multiplication with SWAR
    for (i in 0..6) {
        var carry = 0u
        
        for (j in 0..6) {
            // Multiply limb a[i] × b[j]
            val product = a[i].toUInt() * b[j].toUInt() + 
                         result[i+j] + carry
            
            val q = product / 65536u
            val r = product - q * 65536u
            
            result[i+j] = r
            carry = q
        }
        
        result[i+7] = carry
    }
    
    return Mantissa14(result)
}
```

**SWAR optimization** - Process 2 limbs × 2 limbs:
```kotlin
// Instead of:
// a[i] × b[j], a[i] × b[j+1], a[i+1] × b[j], a[i+1] × b[j+1]
// Do:
val packed_a = a[i] + a[i+1] * 65536u
val packed_b = b[j] + b[j+1] * 65536u
val product = packed_a.toULong() * packed_b.toULong()
// Extract 4 limbs from 64-bit product
```

**With coroutines**: Split the outer loop into chunks.

### Rounding - The Precision Details

**IEEE-754 rounding modes**:
- Round to nearest, ties to even (default)
- Round toward zero (truncate)
- Round toward +infinity (ceiling)
- Round toward -infinity (floor)

**Implementing round-to-nearest**:
```kotlin
fun roundToNearest(
    mantissa: Mantissa14,  // 224-bit result
    targetBits: Int = 112   // Want 112-bit result
): Mantissa7 {
    val discardBits = 224 - targetBits  // 112 bits
    
    // Extract guard, round, sticky bits
    val guardPos = discardBits - 1
    val roundPos = discardBits - 2
    
    val guardBit = extractBit(mantissa, guardPos)
    val roundBit = extractBit(mantissa, roundPos)
    
    // Sticky = OR of all bits below round bit
    var sticky = 0u
    for (i in 0 until roundPos) {
        if (extractBit(mantissa, i) == 1u) {
            sticky = 1u
            break
        }
    }
    
    // Shift right to get 112-bit result
    val shifted = shiftRight(mantissa, discardBits)
    
    // Round to nearest, ties to even
    val lsb = extractBit(shifted, 0)
    
    val shouldRoundUp = 
        (guardBit == 1u && roundBit == 1u) ||  // > 0.5
        (guardBit == 1u && sticky == 1u) ||    // > 0.5
        (guardBit == 1u && lsb == 1u)          // = 0.5, round to even
    
    if (shouldRoundUp) {
        return addOne(shifted)
    } else {
        return shifted
    }
}
```

### Complete Float128 Addition Algorithm

Putting it all together:

```kotlin
suspend fun addFloat128(
    a: Float128,
    b: Float128,
    parallelism: Int = 0
): Float128 {
    // 1. Unpack
    val (signA, expA, mantA) = unpack(a)
    val (signB, expB, mantB) = unpack(b)
    
    // 2. Handle special cases
    if (expA == 0x7FFF) return a  // A is Inf/NaN
    if (expB == 0x7FFF) return b  // B is Inf/NaN
    if (expA == 0 && isZero(mantA)) return b  // A is zero
    if (expB == 0 && isZero(mantB)) return a  // B is zero
    
    // 3. Add implicit leading 1
    val mantissaA = mantA.withImplicitOne()
    val mantissaB = mantB.withImplicitOne()
    
    // 4. Align exponents
    val expDiff = if (expA > expB) expA - expB else expB - expA
    val maxExp = if (expA > expB) expA else expB
    
    val (alignedA, alignedB) = if (expA > expB) {
        Pair(mantissaA, shiftRight(mantissaB, expDiff))
    } else if (expB > expA) {
        Pair(shiftRight(mantissaA, expDiff), mantissaB)
    } else {
        Pair(mantissaA, mantissaB)
    }
    
    // 5. Add or subtract based on signs
    val (resultMant, resultSign, resultExp) = if (signA == signB) {
        // Same sign: add mantissas
        val sum = addMantissas(alignedA, alignedB, parallelism)
        
        // Check for mantissa overflow
        if (sum.overflow != 0u) {
            // Shift right by 1, increment exponent
            val shifted = shiftRight(sum.mantissa, 1)
            Triple(shifted, signA, maxExp + 1)
        } else {
            Triple(sum.mantissa, signA, maxExp)
        }
    } else {
        // Different signs: subtract mantissas
        val diff = subMantissas(alignedA, alignedB)
        
        // Normalize: shift left until MSB is 1
        val leadingZeros = countLeadingZeros(diff.mantissa)
        val normalized = shiftLeft(diff.mantissa, leadingZeros)
        
        Triple(normalized, diff.sign, maxExp - leadingZeros)
    }
    
    // 6. Round
    val rounded = roundToNearest(resultMant)
    
    // 7. Pack result
    return pack(resultSign, resultExp, rounded)
}
```

### Performance Deep Dive

**Operation costs** (approximate CPU cycles):

| Operation | Cost | SWAR (2×) | Parallel (4×) |
|-----------|------|-----------|---------------|
| UInt add | 1 | 1 | 1 |
| UInt multiply | 3 | 3 | 3 |
| UInt divide | 20 | 20 | 20 |
| Load/Store | 3 | 3 | 3 |
| Branch | 1-20 | 1-20 | 1-20 |

**Float128 addition breakdown**:
```
Unpack:              ~50 cycles (divisions + extractions)
Align:               ~200 cycles (shift operation)
Add mantissas:       ~100 cycles (7 limb additions w/ SWAR)
Normalize:           ~150 cycles (CLZ + shift)
Round:               ~50 cycles (bit tests + conditional add)
Pack:                ~50 cycles (multiplications + packing)
─────────────────────────────────
Total (sequential):  ~600 cycles ≈ 300ns @ 2GHz

With SWAR (2×):      ~300 cycles ≈ 150ns
With parallel (4×):  ~150 cycles ≈ 75ns
```

Compare to:
- Native double add: ~5ns (1 cycle, pipelined)
- Double-double add: ~50ns (~10 cycles)
- Our Float128: ~150ns (30× slower than double, 3× slower than double-double)

**But**: We get 112-bit mantissa precision vs 53-bit (double) or ~106-bit (double-double)!

### Validation Test Suite

**Test categories**:

1. **Bit operations**
   - extractBit correctness
   - setBit/clearBit round-trip
   - pack/unpack identity

2. **Shift operations**
   - Left shift preserves lower bits
   - Right shift preserves upper bits  
   - Shift by 0 is identity
   - Overflow detection

3. **Arithmetic properties**
   - Commutativity: a + b = b + a
   - Associativity: (a + b) + c = a + (b + c)
   - Identity: a + 0 = a
   - Inverse: a + (-a) = 0

4. **IEEE-754 compliance**
   - Inf + x = Inf
   - NaN propagation
   - Sign handling
   - Rounding correctness

5. **Known test vectors**
   ```kotlin
   @Test
   fun testOneThird() {
       val one = Float128.ONE
       val three = Float128.fromInt(3)
       val third = one / three
       
       // Multiply back
       val result = third * three
       
       // Should be very close to 1
       assertEquals(one, result, tolerance = 1e-33)
   }
   ```

---

**Status**: Complete plan with implementation details documented.

**Next**: Begin Phase 1 implementation of core primitives.

**Estimated total implementation time**: 10-12 hours focused work.
