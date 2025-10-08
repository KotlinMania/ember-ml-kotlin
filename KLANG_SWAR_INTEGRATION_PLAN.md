# KLang SWAR Float128 - True IEEE-754 Binary128

## The Right Approach

**NOT**: Double-double (current CFloat128 implementation)  
**YES**: True IEEE-754 binary128 using HPC16x8 + overflow room + SWAR acceleration

## IEEE-754 Binary128 Format

```
Total: 128 bits
├─ Sign:     1 bit  (bit 127)
├─ Exponent: 15 bits (bits 126-112, bias = 16383)
└─ Mantissa: 112 bits (bits 111-0, implicit leading 1)
```

## Storage Strategy with Overflow Room

### HPC16x8 (128-bit base) ✅ Already have this
```kotlin
// 8 limbs × 16 bits = 128 bits
class HPC16x8(val limbs: UShortArray) {
    // Limbs[0] = bits 0-15 (LSB)
    // Limbs[1] = bits 16-31
    // ...
    // Limbs[7] = bits 112-127 (MSB, includes sign)
}
```

### HPC16x9 (144-bit for overflow room) - NEED TO CREATE
```kotlin
// 9 limbs × 16 bits = 144 bits
// Extra 16 bits for carry/overflow during addition/subtraction
class HPC16x9(val limbs: UShortArray) {
    // Limbs[0-7] = main 128-bit value
    // Limbs[8] = overflow/carry limb (critical for rounding!)
}
```

### HPC16x14 (224-bit for multiplication) - NEED TO CREATE
```kotlin
// 14 limbs × 16 bits = 224 bits
// For 112-bit × 112-bit mantissa multiplication
class HPC16x14(val limbs: UShortArray) {
    // Result of multiplying two 112-bit mantissas
    // Then round back to 112 bits with proper GRS bits
}
```

## SWAR Acceleration Points

### 1. Parallel Limb Addition (2× speedup)
```kotlin
// Process 2 limbs at once using Int (32-bit) packing
fun swarAdd2Limbs(
    a0: UShort, a1: UShort,
    b0: UShort, b1: UShort,
    carryIn: Int
): Triple<UShort, UShort, Int> {
    // Pack into Int for parallel operation
    val aPacked = (a1.toInt() shl 16) or a0.toInt()
    val bPacked = (b1.toInt() shl 16) or b0.toInt()
    val sum = aPacked + bPacked + carryIn
    
    return Triple(
        (sum and 0xFFFF).toUShort(),
        ((sum ushr 16) and 0xFFFF).toUShort(),
        sum ushr 32
    )
}
```

### 2. Fast CLZ (Count Leading Zeros)
```kotlin
// SWAR: Check multiple limbs quickly
fun countLeadingZeros(value: HPC16x9): Int {
    // Start from MSB limb
    for (i in 8 downTo 0) {
        val limb = value.limbs[i].toInt()
        if (limb != 0) {
            // Use Kotlin's built-in CLZ
            val limbClz = limb.countLeadingZeroBits() - 16  // Adjust for UShort
            return (8 - i - 1) * 16 + limbClz
        }
    }
    return 144  // All zeros
}
```

### 3. ArrayBitShifts for Alignment
```kotlin
// Use existing ArrayBitShifts with SWAR for mantissa alignment
suspend fun alignMantissa(
    mantissa: HPC16x8,
    shiftAmount: Int
): HPC16x9 {
    // Convert to IntArray for ArrayBitShifts
    val asIntArray = packToIntArray(mantissa)
    
    // Use ArrayBitShifts.shr16LEInPlaceParallel
    ArrayBitShifts.shr16LEInPlaceParallel(
        asIntArray, 0, asIntArray.size, shiftAmount
    )
    
    // Convert back to HPC16x9 with overflow room
    return unpackToHPC16x9(asIntArray)
}
```

## Implementation Plan

### Step 1: Create Extended HPC Types (Today!)
```kotlin
// File: src/commonMain/kotlin/ai/solace/klang/int/hpc/HPC16x9.kt
class HPC16x9(val limbs: UShortArray) {
    init { require(limbs.size == 9) }
    
    fun add(other: HPC16x9): HPC16x9
    fun shiftRight(bits: Int): HPC16x9
    fun shiftLeft(bits: Int): HPC16x9
    fun toHPC16x8(): HPC16x8  // Truncate to 128 bits
}

// File: src/commonMain/kotlin/ai/solace/klang/int/hpc/HPC16x14.kt
class HPC16x14(val limbs: UShortArray) {
    init { require(limbs.size == 14) }
    
    fun extractBits(start: Int, count: Int): HPC16x8
    fun shiftRight(bits: Int): HPC16x14
}
```

### Step 2: Implement Float128Math Core (1-2 days)
```kotlin
// File: src/commonMain/kotlin/ai/solace/klang/bitwise/Float128Math.kt
object Float128Math {
    // Constants
    const val SIGN_BIT = 127
    const val EXP_BITS = 15
    const val MANT_BITS = 112
    const val EXP_BIAS = 16383
    
    // Pack IEEE-754 binary128
    fun pack(sign: Int, exp: Int, mantissa: HPC16x8): HPC16x8 {
        // Combine: sign(1) + exp(15) + mantissa(112) = 128 bits
    }
    
    // Unpack into components
    fun unpack(value: HPC16x8): Triple<Int, Int, HPC16x8> {
        // Extract sign, exp, mantissa using bit operations
    }
    
    // Addition algorithm:
    // 1. Unpack both operands
    // 2. Align mantissas (shift smaller by exp difference)
    // 3. Add aligned mantissas in HPC16x9 (overflow room!)
    // 4. Normalize result (shift left, adjust exp)
    // 5. Round using guard/round/sticky bits
    // 6. Pack result
    fun addBits(a: HPC16x8, b: HPC16x8): HPC16x8
    
    // Multiplication algorithm:
    // 1. Unpack both operands
    // 2. Add exponents
    // 3. Multiply mantissas: 112×112 → 224 bits (HPC16x14)
    // 4. Normalize
    // 5. Round to 112 bits
    // 6. Pack result
    fun mulBits(a: HPC16x8, b: HPC16x8): HPC16x8
}
```

### Step 3: Refactor CFloat128 (Quick!)
```kotlin
// Replace double-double implementation with true binary128
@JvmInline
value class CFloat128 private constructor(private val bits: HPC16x8) {
    
    operator fun plus(other: CFloat128): CFloat128 = 
        CFloat128(Float128Math.addBits(this.bits, other.bits))
    
    operator fun minus(other: CFloat128): CFloat128 = 
        CFloat128(Float128Math.subBits(this.bits, other.bits))
    
    operator fun times(other: CFloat128): CFloat128 = 
        CFloat128(Float128Math.mulBits(this.bits, other.bits))
    
    operator fun div(other: CFloat128): CFloat128 = 
        CFloat128(Float128Math.divBits(this.bits, other.bits))
    
    // Convert to/from Double
    fun toDouble(): Double = Float128Math.toDouble(bits)
    
    companion object {
        fun fromDouble(value: Double): CFloat128 = 
            CFloat128(Float128Math.fromDouble(value))
        
        // Can also convert from bit string for testing!
        fun fromBits(bitString: String): CFloat128 {
            require(bitString.length == 128)
            // Parse bit string into HPC16x8
        }
    }
    
    // For testing: export as bit string
    fun toBitString(): String {
        // Convert HPC16x8 to 128-character binary string
    }
}
```

### Step 4: C Validation with __float128
```c
// tools/float128_ieee_validation.c
#include <stdio.h>
#include <stdint.h>
#include <quadmath.h>

// Print __float128 as 128-bit binary string
void print_float128_bits(__float128 value) {
    unsigned __int128 bits;
    memcpy(&bits, &value, 16);
    
    for (int i = 127; i >= 0; i--) {
        printf("%d", (int)((bits >> i) & 1));
        if (i == 127 || i == 112) printf(" ");  // Separate sign, exp, mantissa
    }
    printf("\n");
}

int main() {
    // Test cases
    __float128 a = 1.0Q / 3.0Q;
    __float128 b = 2.5Q;
    __float128 sum = a + b;
    __float128 product = a * b;
    
    printf("1/3 bits: ");
    print_float128_bits(a);
    
    printf("2.5 bits: ");
    print_float128_bits(b);
    
    printf("sum bits: ");
    print_float128_bits(sum);
    
    printf("product bits: ");
    print_float128_bits(product);
    
    return 0;
}
```

Compile with: `gcc -lquadmath float128_ieee_validation.c`

### Step 5: Kotlin Test - Bit String Comparison
```kotlin
@Test
fun testFloat128BitwiseCorrectness() {
    // Use C program output as reference
    val oneThird_C = "0 011111111111101 0101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101"
    
    // Compute in Kotlin
    val oneThird_Kotlin = CFloat128.fromDouble(1.0 / 3.0)
    val bits_Kotlin = oneThird_Kotlin.toBitString()
    
    // Compare bit-for-bit
    assertEquals(oneThird_C, bits_Kotlin, "1/3 should match C __float128 exactly")
}
```

## Performance Comparison

### Current Double-Double
```
Addition:       ~15-20 ns
Multiplication: ~50-70 ns
Precision:      ~106 bits mantissa (non-standard)
```

### Target SWAR Float128
```
Addition:       ~5-10 ns  (2-3× faster via SWAR)
Multiplication: ~20-30 ns (2-3× faster)
Precision:      112 bits mantissa (IEEE-754 standard)
```

## Why This Wins

1. **True IEEE-754**: 128-bit standard format, not a hack
2. **Overflow Room**: HPC16x9 prevents rounding bugs
3. **SWAR Speed**: 2-3× faster than double-double
4. **Bit-Exact**: Compare with C `__float128` bit-for-bit
5. **Cross-Platform**: Same 128 bits everywhere (NumPy can't claim this!)
6. **Future-Proof**: Ready for native SIMD when available

---

**Next Action**: Create HPC16x9.kt and HPC16x14.kt, then implement Float128Math.pack/unpack
