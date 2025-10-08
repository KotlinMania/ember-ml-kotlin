package ai.solace.klang.bitwise

/**
 * Float64Math - IEEE-754 binary64 (double precision) arithmetic operations.
 * 
 * Based on compiler-rt patterns from Float32Math.
 * Uses Long (64-bit) storage - could use HPC16x8 (128-bit) for extended precision
 * in the future.
 * 
 * Format: 1 sign + 11 exponent + 52 mantissa
 * - Sign: bit 63
 * - Exponent: bits 62-52 (bias = 1023)
 * - Mantissa: bits 51-0 (implicit leading 1 for normals)
 */
object Float64Math {
    // Constants for IEEE-754 binary64
    private const val SIGN_MASK = 0x7FFFFFFFFFFFFFFFL.inv()  // 0x8000_0000_0000_0000
    private const val EXP_MASK = 0x7FF0_0000_0000_0000L
    private const val FRAC_MASK = 0x000F_FFFF_FFFF_FFFFL
    private const val IMPLICIT_BIT = 0x0010_0000_0000_0000L  // Implicit leading 1
    private const val EXP_BIAS = 1023
    private const val EXP_MAX = 2047  // 2^11 - 1
    
    // Special values
    const val ZERO_BITS = 0x0000_0000_0000_0000L
    const val ONE_BITS = 0x3FF0_0000_0000_0000L  // 1.0 in float64
    const val NAN_BITS = 0x7FF8_0000_0000_0000L  // Canonical NaN
    const val INF_BITS = 0x7FF0_0000_0000_0000L  // +Infinity
    const val NEG_INF_BITS = 0x000FFFFFFFFFFFFFL.inv()  // 0xFFF0_0000_0000_0000
    
    /**
     * Add two float64 values.
     * Currently uses native Double operations.
     * TODO: Implement bit-exact addition using HPC16x8 (128-bit intermediates).
     */
    fun addBits(aBits: Long, bBits: Long): Long {
        val a = Double.fromBits(aBits)
        val b = Double.fromBits(bBits)
        return (a + b).toRawBits()
    }
    
    /**
     * Subtract two float64 values.
     */
    fun subBits(aBits: Long, bBits: Long): Long {
        // Negate b and add
        val bNeg = bBits xor SIGN_MASK
        return addBits(aBits, bNeg)
    }
    
    /**
     * Multiply two float64 values.
     * Currently uses native Double operations.
     * TODO: Implement bit-exact multiplication using HPC16x8.
     */
    fun mulBits(aBits: Long, bBits: Long): Long {
        val a = Double.fromBits(aBits)
        val b = Double.fromBits(bBits)
        return (a * b).toRawBits()
    }
    
    /**
     * Divide two float64 values.
     * Currently uses native Double operations.
     * TODO: Implement bit-exact division.
     */
    fun divBits(aBits: Long, bBits: Long): Long {
        val a = Double.fromBits(aBits)
        val b = Double.fromBits(bBits)
        return (a / b).toRawBits()
    }
    
    /**
     * Check if float64 value is NaN.
     */
    fun isNaN(bits: Long): Boolean {
        val exp = (bits and EXP_MASK) ushr 52
        val frac = bits and FRAC_MASK
        return exp == EXP_MAX.toLong() && frac != 0L
    }
    
    /**
     * Check if float64 value is infinity.
     */
    fun isInf(bits: Long): Boolean {
        val exp = (bits and EXP_MASK) ushr 52
        val frac = bits and FRAC_MASK
        return exp == EXP_MAX.toLong() && frac == 0L
    }
    
    /**
     * Check if float64 value is zero.
     */
    fun isZero(bits: Long): Boolean {
        return (bits and 0x7FFF_FFFF_FFFF_FFFFL) == 0L
    }
    
    /**
     * Get sign of float64 value.
     * @return 1 for negative, 0 for positive
     */
    fun getSign(bits: Long): Int {
        return ((bits and SIGN_MASK) ushr 63).toInt()
    }
    
    /**
     * Negate float64 value (flip sign bit).
     */
    fun negateBits(bits: Long): Long {
        return bits xor SIGN_MASK
    }
    
    /**
     * Absolute value (clear sign bit).
     */
    fun absBits(bits: Long): Long {
        return bits and 0x7FFF_FFFF_FFFF_FFFFL
    }
    
    /**
     * Compare two float64 values.
     * @return -1 if a < b, 0 if a == b, 1 if a > b
     * NaN handling: NaN compares unordered
     */
    fun compareBits(aBits: Long, bBits: Long): Int {
        // Handle NaN
        if (isNaN(aBits) || isNaN(bBits)) {
            return if (aBits < bBits) -1 else if (aBits > bBits) 1 else 0
        }
        
        // Handle zeros
        if (isZero(aBits) && isZero(bBits)) {
            return 0
        }
        
        val a = Double.fromBits(aBits)
        val b = Double.fromBits(bBits)
        return a.compareTo(b)
    }
    
    /**
     * Convert Float32 to Float64 (widening conversion).
     * Based on compiler-rt extendsfdf2.
     */
    fun fromFloat32Bits(f32bits: Int): Long {
        val sign = (f32bits.toLong() and 0x80000000L) shl 32
        val exp = (f32bits ushr 23) and 0xFF
        val frac = f32bits and 0x007FFFFF
        
        if (exp == 0xFF) {
            // Inf/NaN
            val dExp = 0x7FFL shl 52
            val dFrac = if (frac != 0) 0x0008_0000_0000_0000L else 0L  // canonical qNaN
            return sign or dExp or dFrac
        }
        
        if (exp == 0) {
            if (frac == 0) return sign  // signed zero
            // subnormal: normalize mantissa
            var m = frac
            var shift = 0
            while ((m and 0x00800000) == 0) {
                m = m shl 1
                shift++
            }
            m = m and 0x007FFFFF
            val eUnb = -126 - shift
            val dExp = ((eUnb + 1023).toLong() and 0x7FF) shl 52
            val dFrac = (m.toLong() shl (52 - 23))
            return sign or dExp or dFrac
        }
        
        // normal
        val eUnb = exp - 127
        val dExp = ((eUnb + 1023).toLong() and 0x7FF) shl 52
        val dFrac = (frac.toLong() shl (52 - 23))
        return sign or dExp or dFrac
    }
    
    /**
     * Convert Float64 to Float32 (narrowing conversion with rounding).
     * Based on compiler-rt truncdfsf2.
     */
    fun toFloat32Bits(f64bits: Long): Int {
        val sign = ((f64bits ushr 32) and 0x80000000L).toInt()
        val exp = ((f64bits ushr 52) and 0x7FF).toInt()
        val frac = f64bits and 0x000F_FFFF_FFFF_FFFFL
        
        if (exp == 0x7FF) {
            // Inf/NaN
            val f32exp = 0xFF shl 23
            val f32frac = if (frac != 0L) 0x0040_0000 else 0
            return sign or f32exp or f32frac
        }
        
        if (exp == 0 && frac == 0L) {
            return sign  // signed zero
        }
        
        // Convert exponent (float64 bias=1023, float32 bias=127)
        var newExp = exp - 1023 + 127
        
        // Handle overflow
        if (newExp >= 0xFF) {
            return sign or (0xFF shl 23)  // overflow to infinity
        }
        
        // Handle underflow
        if (newExp <= 0) {
            // Flush to zero (could implement subnormals)
            return sign
        }
        
        // Convert mantissa (52 bits -> 23 bits) with rounding
        val fracShift = 29
        val roundBit = ((frac ushr (fracShift - 1)) and 1L).toInt()
        val stickyBits = frac and ((1L shl (fracShift - 1)) - 1L)
        var newFrac = (frac ushr fracShift).toInt()
        
        // Round to nearest, ties to even
        if (roundBit != 0 && (stickyBits != 0L || (newFrac and 1) != 0)) {
            newFrac++
            if (newFrac > 0x007FFFFF) {
                // Mantissa overflow
                newFrac = 0
                newExp++
                if (newExp >= 0xFF) {
                    return sign or (0xFF shl 23)
                }
            }
        }
        
        return sign or (newExp shl 23) or newFrac
    }
}
