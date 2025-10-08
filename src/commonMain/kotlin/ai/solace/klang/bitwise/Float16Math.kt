package ai.solace.klang.bitwise

/**
 * Float16Math - IEEE-754 binary16 (half precision) arithmetic operations.
 * 
 * Based on compiler-rt patterns from Float32Math.
 * Uses Int (32-bit) for intermediate calculations to handle overflow/carry.
 * 
 * Format: 1 sign + 5 exponent + 10 mantissa
 * - Sign: bit 15
 * - Exponent: bits 14-10 (bias = 15)
 * - Mantissa: bits 9-0 (implicit leading 1 for normals)
 */
object Float16Math {
    // Constants for IEEE-754 binary16
    private const val SIGN_MASK = 0x8000
    private const val EXP_MASK = 0x7C00
    private const val FRAC_MASK = 0x03FF
    private const val IMPLICIT_BIT = 0x0400  // Implicit leading 1
    private const val EXP_BIAS = 15
    private const val EXP_MAX = 31  // 2^5 - 1
    
    // Special values
    const val ZERO_BITS = 0x0000
    const val ONE_BITS = 0x3C00  // 1.0 in float16
    const val NAN_BITS = 0x7E00  // Canonical NaN
    const val INF_BITS = 0x7C00  // +Infinity
    const val NEG_INF_BITS = 0xFC00  // -Infinity
    
    private val arith16 = ArithmeticBitwiseOps(16)
    private val arith32 = ArithmeticBitwiseOps(32)
    
    /**
     * Convert float16 bits to Float32 for processing.
     * Uses Int (32-bit) for intermediate calculations.
     */
    fun toFloat32Bits(f16bits: Int): Int {
        val bits = f16bits and 0xFFFF
        
        // Extract components
        val sign = (bits and SIGN_MASK) shr 15
        val exp = (bits and EXP_MASK) shr 10
        val frac = bits and FRAC_MASK
        
        // Handle special cases
        if (exp == EXP_MAX) {
            // Infinity or NaN
            val f32exp = 0xFF
            val f32frac = if (frac != 0) (1 shl 22) else 0  // Canonical NaN or zero mantissa
            return (sign shl 31) or (f32exp shl 23) or f32frac
        }
        
        if (exp == 0) {
            if (frac == 0) {
                // Zero
                return sign shl 31
            }
            // Subnormal - normalize it
            var m = frac
            var e = -14  // Unbias and adjust
            // Find leading 1
            while ((m and IMPLICIT_BIT) == 0) {
                m = m shl 1
                e--
            }
            m = m and FRAC_MASK  // Remove implicit bit
            val f32exp = e + 127
            if (f32exp <= 0) {
                // Underflow to zero
                return sign shl 31
            }
            val f32frac = m shl 13  // 10 bits -> 23 bits
            return (sign shl 31) or (f32exp shl 23) or f32frac
        }
        
        // Normal number
        val f32exp = exp - EXP_BIAS + 127
        val f32frac = frac shl 13  // 10 bits -> 23 bits
        return (sign shl 31) or (f32exp shl 23) or f32frac
    }
    
    /**
     * Convert Float32 bits to float16 bits.
     * Uses Int (32-bit) to handle intermediate values with overflow room.
     */
    fun fromFloat32Bits(f32bits: Int): Int {
        // Extract components
        val sign = (f32bits ushr 31) and 0x1
        val exp = (f32bits ushr 23) and 0xFF
        val frac = f32bits and 0x7FFFFF
        
        // Handle special cases
        if (exp == 0xFF) {
            // Infinity or NaN
            return if (frac != 0) {
                (sign shl 15) or NAN_BITS
            } else {
                (sign shl 15) or INF_BITS
            }
        }
        
        if (exp == 0 && frac == 0) {
            // Zero
            return sign shl 15
        }
        
        // Convert exponent (float32 bias=127, float16 bias=15)
        var newExp = exp - 127 + 15
        
        // Handle overflow
        if (newExp >= EXP_MAX) {
            // Overflow to infinity
            return (sign shl 15) or INF_BITS
        }
        
        // Handle underflow
        if (newExp <= 0) {
            // Could implement subnormals here
            // For now, flush to zero
            return sign shl 15
        }
        
        // Convert mantissa (23 bits -> 10 bits)
        // Round to nearest even
        val fracShift = 13
        val roundBit = (frac ushr (fracShift - 1)) and 1
        val stickyBits = frac and ((1 shl (fracShift - 1)) - 1)
        var newFrac = frac ushr fracShift
        
        // Round to nearest, ties to even
        if (roundBit != 0 && (stickyBits != 0 || (newFrac and 1) != 0)) {
            newFrac++
            if (newFrac > FRAC_MASK) {
                // Mantissa overflow
                newFrac = 0
                newExp++
                if (newExp >= EXP_MAX) {
                    // Exponent overflow
                    return (sign shl 15) or INF_BITS
                }
            }
        }
        
        return (sign shl 15) or (newExp shl 10) or newFrac
    }
    
    /**
     * Add two float16 values using bit manipulation.
     * Uses Int (32-bit) for intermediate calculations to handle overflow.
     */
    fun addBits(aBits: Int, bBits: Int): Int {
        val a16 = aBits and 0xFFFF
        val b16 = bBits and 0xFFFF
        
        // Convert to float32, add, convert back
        // This is safe because we have overflow room in Int
        val a32 = toFloat32Bits(a16)
        val b32 = toFloat32Bits(b16)
        
        // Use Float32Math for the actual addition
        val result32 = Float32Math.addBits(a32, b32)
        
        return fromFloat32Bits(result32)
    }
    
    /**
     * Subtract two float16 values.
     */
    fun subBits(aBits: Int, bBits: Int): Int {
        // Negate b and add
        val bNeg = (bBits and 0xFFFF) xor SIGN_MASK
        return addBits(aBits, bNeg)
    }
    
    /**
     * Multiply two float16 values.
     * Uses Int (32-bit) for intermediate calculations.
     */
    fun mulBits(aBits: Int, bBits: Int): Int {
        val a16 = aBits and 0xFFFF
        val b16 = bBits and 0xFFFF
        
        // Convert to float32, multiply, convert back
        val a32 = toFloat32Bits(a16)
        val b32 = toFloat32Bits(b16)
        
        val result32 = Float32Math.mulBits(a32, b32)
        
        return fromFloat32Bits(result32)
    }
    
    /**
     * Divide two float16 values.
     * Uses Int (32-bit) for intermediate calculations.
     */
    fun divBits(aBits: Int, bBits: Int): Int {
        val a16 = aBits and 0xFFFF
        val b16 = bBits and 0xFFFF
        
        // Convert to float32, divide, convert back
        val a32 = toFloat32Bits(a16)
        val b32 = toFloat32Bits(b16)
        
        val result32 = Float32Math.divBits(a32, b32)
        
        return fromFloat32Bits(result32)
    }
    
    /**
     * Check if float16 value is NaN.
     */
    fun isNaN(bits: Int): Boolean {
        val exp = (bits and EXP_MASK) shr 10
        val frac = bits and FRAC_MASK
        return exp == EXP_MAX && frac != 0
    }
    
    /**
     * Check if float16 value is infinity.
     */
    fun isInf(bits: Int): Boolean {
        val exp = (bits and EXP_MASK) shr 10
        val frac = bits and FRAC_MASK
        return exp == EXP_MAX && frac == 0
    }
    
    /**
     * Check if float16 value is zero.
     */
    fun isZero(bits: Int): Boolean {
        return (bits and 0x7FFF) == 0
    }
    
    /**
     * Get sign of float16 value.
     * @return 1 for negative, 0 for positive
     */
    fun getSign(bits: Int): Int {
        return (bits and SIGN_MASK) shr 15
    }
    
    /**
     * Negate float16 value (flip sign bit).
     */
    fun negateBits(bits: Int): Int {
        return (bits and 0xFFFF) xor SIGN_MASK
    }
    
    /**
     * Absolute value (clear sign bit).
     */
    fun absBits(bits: Int): Int {
        return bits and 0x7FFF
    }
    
    /**
     * Compare two float16 values.
     * @return -1 if a < b, 0 if a == b, 1 if a > b
     * NaN handling: NaN compares unordered (returns based on bit pattern)
     */
    fun compareBits(aBits: Int, bBits: Int): Int {
        val a16 = aBits and 0xFFFF
        val b16 = bBits and 0xFFFF
        
        // Handle NaN
        if (isNaN(a16) || isNaN(b16)) {
            return if (a16 < b16) -1 else if (a16 > b16) 1 else 0
        }
        
        // Handle zeros (positive and negative zero are equal)
        if (isZero(a16) && isZero(b16)) {
            return 0
        }
        
        // Get signs
        val signA = getSign(a16)
        val signB = getSign(b16)
        
        // Different signs
        if (signA != signB) {
            return if (signA > signB) -1 else 1
        }
        
        // Same sign - compare magnitude
        val magCmp = if (a16 < b16) -1 else if (a16 > b16) 1 else 0
        
        // If both negative, reverse comparison
        return if (signA != 0) -magCmp else magCmp
    }
}
