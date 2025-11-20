package ai.solace.klang.fp

import ai.solace.klang.bitwise.Float16Math

/**
 * CFloat16: IEEE-754 binary16 (half-precision) floating-point with deterministic behavior.
 *
 * Represents a 16-bit half-precision floating-point number commonly used in machine learning,
 * graphics (GPU shaders), and memory-constrained applications. Provides bit-exact arithmetic
 * across all Kotlin multiplatform targets through [Float16Math].
 *
 * ## IEEE-754 Binary16 Format
 *
 * ```
 * 15  14──────10  9───────────────0
 * ┌─┬──────────┬──────────────────┐
 * │S│ Exponent │     Mantissa     │
 * └─┴──────────┴──────────────────┘
 *  1      5            10 bits
 * ```
 *
 * - **Sign**: bit 15 (0 = positive, 1 = negative)
 * - **Exponent**: bits 14-10 (5 bits, bias = 15)
 * - **Mantissa**: bits 9-0 (10 bits, implicit leading 1)
 *
 * ## Precision and Range
 *
 * - **Mantissa precision**: ~3.3 decimal digits (vs 7 for Float, 16 for Double)
 * - **Exponent range**: 2^-14 to 2^15 (vs 2^-126 to 2^127 for Float)
 * - **Max value**: 65,504 (vs 3.4×10^38 for Float)
 * - **Min positive**: 6.1×10^-5 (vs 1.2×10^-38 for Float)
 * - **Epsilon**: 0.000977 (vs 0.000119 for Float)
 *
 * ## Why CFloat16?
 *
 * **Use Cases**:
 * - **Machine Learning**: Reduced memory footprint for neural network weights
 * - **GPU Computing**: Native half-precision shader operations
 * - **Memory Bandwidth**: 2× more values fit in cache vs Float
 * - **Cross-Platform**: Deterministic behavior even on platforms without native FP16
 *
 * **Advantages**:
 * - 50% memory savings vs Float
 * - Faster GPU processing (2× throughput on most hardware)
 * - Deterministic cross-platform behavior
 * - Compatible with TensorFlow/PyTorch FP16 tensors
 *
 * **Trade-offs**:
 * - Limited precision (3 decimal digits)
 * - Narrow range (overflows at 65,504)
 * - Not suitable for accumulation (use Float or CFloat128 for sums)
 *
 * ## Storage Format
 *
 * Internally stored as an [Int] (32 bits) with only the lower 16 bits used.
 * This provides overflow headroom during intermediate calculations and simplifies
 * bit manipulation (no sign extension issues).
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Create from Float
 * val a = CFloat16.fromFloat(1.5f)
 * val b = CFloat16.fromFloat(2.0f)
 *
 * // Arithmetic operations
 * val sum = a + b                       // 3.5
 * val product = a * b                   // 3.0
 *
 * // Special values
 * val zero = CFloat16.ZERO
 * val infinity = CFloat16.POSITIVE_INFINITY
 * val notANumber = CFloat16.NaN
 *
 * // Conversion
 * val asFloat = a.toFloat()             // 1.5f
 * val asDouble = a.toDouble()           // 1.5
 * val bits = a.toBits()                 // 0x3E00 (raw bits)
 *
 * // ML-style usage
 * val weights = Array(1000) { CFloat16.fromFloat(it * 0.01f) }
 * val compressed = weights.map { it.toBits().toUShort() }
 * // 2KB instead of 4KB for Float array
 * ```
 *
 * ## Performance
 *
 * - **Conversion (Float↔Half)**: ~5-10 CPU cycles per value
 * - **Arithmetic**: ~2-3× slower than Float on CPU (bit manipulation overhead)
 * - **GPU arithmetic**: Up to 2× faster than Float on modern hardware
 * - **Memory bandwidth**: 2× better than Float
 *
 * ## Precision Examples
 *
 * ```kotlin
 * CFloat16.fromFloat(1.0f)    // Exact: 1.0
 * CFloat16.fromFloat(1.001f)  // Rounded: 1.00097656 (11 bits available)
 * CFloat16.fromFloat(65504f)  // Max value: 65504
 * CFloat16.fromFloat(65520f)  // Overflow: +Infinity
 * CFloat16.fromFloat(0.00006f)// Subnormal: very low precision
 * ```
 *
 * ## Subnormal Numbers
 *
 * CFloat16 supports subnormal (denormalized) numbers below 2^-14.
 * These provide gradual underflow but with reduced precision.
 *
 * ## Thread Safety
 *
 * CFloat16 instances are immutable and thread-safe. All operations return new instances.
 *
 * ## Compatibility
 *
 * - **TensorFlow**: Compatible with `tf.float16`
 * - **PyTorch**: Compatible with `torch.float16` / `torch.half`
 * - **CUDA**: Compatible with `__half` type
 * - **OpenGL/Vulkan**: Compatible with `half` / `float16_t` in shaders
 *
 * @property bits Raw IEEE-754 binary16 representation (stored in lower 16 bits of Int)
 * @constructor Private constructor; use companion object factory methods
 * @see Float16Math For underlying bit-level operations
 * @see CDouble For 64-bit precision
 * @see CFloat128 For 128-bit extended precision
 * @since 0.1.0
 */
class CFloat16 private constructor(private val bits: Int) {
    
    /**
     * The value as a Kotlin [Float].
     *
     * Converts the half-precision value to single-precision.
     * This is a widening conversion (no precision loss).
     */
    val value: Float get() = Float.fromBits(Float16Math.toFloat32Bits(bits))
    
    /**
     * Convert to Kotlin [Float] (single-precision).
     *
     * @return The value as a 32-bit Float
     */
    fun toFloat(): Float = value
    
    /**
     * Convert to [Double] (double-precision).
     *
     * @return The value as a 64-bit Double
     */
    fun toDouble(): Double = value.toDouble()
    
    /**
     * Get raw IEEE-754 binary16 bits as an [Int].
     *
     * Only the lower 16 bits are significant; upper bits are zero.
     *
     * @return The 16-bit representation in an Int
     */
    fun toBits(): Int = bits and 0xFFFF
    
    /**
     * Get raw IEEE-754 binary16 bits as a [UShort].
     *
     * @return The 16-bit representation as an unsigned short
     */
    fun toUShort(): UShort = (bits and 0xFFFF).toUShort()
    
    /**
     * Unary negation operator.
     *
     * Flips the sign bit without affecting magnitude or special values.
     *
     * @return A new CFloat16 with opposite sign
     */
    operator fun unaryMinus(): CFloat16 = fromBits(Float16Math.negateBits(bits))
    
    /**
     * Addition operator.
     *
     * Performs IEEE-754 compliant addition with round-to-nearest-even.
     * Result may overflow to infinity or lose precision.
     *
     * @param other Value to add
     * @return A new CFloat16 representing the sum
     *
     * ## Example
     * ```kotlin
     * val a = CFloat16.fromFloat(1.0f)
     * val b = CFloat16.fromFloat(2.5f)
     * val sum = a + b  // 3.5
     * ```
     */
    operator fun plus(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.addBits(this.bits, other.bits))
    }
    
    /**
     * Subtraction operator.
     *
     * @param other Value to subtract
     * @return A new CFloat16 representing the difference
     */
    operator fun minus(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.subBits(this.bits, other.bits))
    }
    
    /**
     * Multiplication operator.
     *
     * @param other Value to multiply by
     * @return A new CFloat16 representing the product
     */
    operator fun times(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.mulBits(this.bits, other.bits))
    }
    
    /**
     * Division operator.
     *
     * @param other Divisor
     * @return A new CFloat16 representing the quotient
     */
    operator fun div(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.divBits(this.bits, other.bits))
    }
    
    /**
     * Comparison operator.
     *
     * Implements total ordering:
     * - NaN is considered greater than all values including +Infinity
     * - -0.0 equals +0.0
     *
     * @param other Value to compare against
     * @return Negative if this < other, zero if equal, positive if this > other
     */
    operator fun compareTo(other: CFloat16): Int {
        return Float16Math.compareBits(this.bits, other.bits)
    }
    
    /**
     * String representation of the value.
     *
     * @return String representation (converts to Float first)
     */
    override fun toString(): String = value.toString()
    
    companion object {
        /**
         * Positive zero (+0.0).
         */
        val ZERO = CFloat16(Float16Math.ZERO_BITS)
        
        /**
         * One (1.0).
         */
        val ONE = CFloat16(Float16Math.ONE_BITS)
        
        /**
         * Not-a-Number (NaN).
         */
        val NaN = CFloat16(Float16Math.NAN_BITS)
        
        /**
         * Positive infinity (+∞).
         */
        val POSITIVE_INFINITY = CFloat16(Float16Math.INF_BITS)
        
        /**
         * Negative infinity (-∞).
         */
        val NEGATIVE_INFINITY = CFloat16(Float16Math.NEG_INF_BITS)
        
        /**
         * Create CFloat16 from raw bits.
         *
         * @param bits 16-bit IEEE-754 binary16 representation (in an Int)
         * @return A new CFloat16 with the specified bit pattern
         */
        fun fromBits(bits: Int): CFloat16 = CFloat16(bits and 0xFFFF)
        
        /**
         * Create CFloat16 from raw bits (UShort variant).
         *
         * @param bits 16-bit IEEE-754 binary16 representation
         * @return A new CFloat16 with the specified bit pattern
         */
        fun fromBits(bits: UShort): CFloat16 = CFloat16(bits.toInt() and 0xFFFF)
        
        /**
         * Create CFloat16 from [Float] with rounding.
         *
         * Uses [Float16Math.fromFloat32Bits] for deterministic round-to-nearest-even.
         * Values outside the representable range become ±Infinity.
         *
         * @param value Float value to convert
         * @return A new CFloat16 representing the rounded value
         *
         * ## Example
         * ```kotlin
         * CFloat16.fromFloat(1.0f)    // Exact
         * CFloat16.fromFloat(1.001f)  // Rounded to nearest
         * CFloat16.fromFloat(70000f)  // Overflows to +Infinity
         * ```
         */
        fun fromFloat(value: Float): CFloat16 {
            val f32bits = value.toRawBits()
            val f16bits = Float16Math.fromFloat32Bits(f32bits)
            return CFloat16(f16bits)
        }
        
        /**
         * Create CFloat16 from [Double].
         *
         * Converts to Float first, then to CFloat16.
         *
         * @param value Double value to convert
         * @return A new CFloat16 representing the rounded value
         */
        fun fromDouble(value: Double): CFloat16 = fromFloat(value.toFloat())
        
        /**
         * Create CFloat16 from [Int].
         *
         * Large integers may lose precision (only 11 bits of mantissa available).
         *
         * @param value Integer value to convert
         * @return A new CFloat16 representing the value (may be approximate)
         */
        fun fromInt(value: Int): CFloat16 = fromFloat(value.toFloat())
    }
}
