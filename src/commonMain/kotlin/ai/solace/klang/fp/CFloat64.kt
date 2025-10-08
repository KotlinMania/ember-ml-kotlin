package ai.solace.klang.fp

import ai.solace.klang.bitwise.Float64Math
import kotlin.jvm.JvmInline

/**
 * CFloat64: 64-bit IEEE-754 binary64 (double precision) float.
 * 
 * Format: 1 sign bit, 11 exponent bits, 52 mantissa bits
 * - Sign: bit 63
 * - Exponent: bits 62-52 (bias = 1023)
 * - Mantissa: bits 51-0
 * 
 * Uses Float64Math for arithmetic operations.
 * Future: Use HPC16x8 for 128-bit intermediate precision.
 */
@JvmInline
value class CFloat64 private constructor(private val bits: Long) {
    
    val value: Double get() = Double.fromBits(bits)
    
    fun toDouble(): Double = value
    fun toFloat(): Float = Float.fromBits(Float64Math.toFloat32Bits(bits))
    fun toBits(): Long = bits
    
    // Unary operators
    operator fun unaryMinus(): CFloat64 = fromBits(Float64Math.negateBits(bits))
    
    // Arithmetic operators using Float64Math
    operator fun plus(other: CFloat64): CFloat64 {
        return fromBits(Float64Math.addBits(this.bits, other.bits))
    }
    
    operator fun minus(other: CFloat64): CFloat64 {
        return fromBits(Float64Math.subBits(this.bits, other.bits))
    }
    
    operator fun times(other: CFloat64): CFloat64 {
        return fromBits(Float64Math.mulBits(this.bits, other.bits))
    }
    
    operator fun div(other: CFloat64): CFloat64 {
        return fromBits(Float64Math.divBits(this.bits, other.bits))
    }
    
    // Comparison using Float64Math
    operator fun compareTo(other: CFloat64): Int {
        return Float64Math.compareBits(this.bits, other.bits)
    }
    
    // String representation
    override fun toString(): String = value.toString()
    
    companion object {
        // Special values
        val ZERO = CFloat64(Float64Math.ZERO_BITS)
        val ONE = CFloat64(Float64Math.ONE_BITS)
        val NaN = CFloat64(Float64Math.NAN_BITS)
        val POSITIVE_INFINITY = CFloat64(Float64Math.INF_BITS)
        val NEGATIVE_INFINITY = CFloat64(Float64Math.NEG_INF_BITS)
        
        /**
         * Create CFloat64 from raw bits.
         */
        fun fromBits(bits: Long): CFloat64 = CFloat64(bits)
        
        /**
         * Create CFloat64 from Double.
         */
        fun fromDouble(value: Double): CFloat64 = CFloat64(value.toRawBits())
        
        /**
         * Create from Float using proper widening conversion.
         */
        fun fromFloat(value: Float): CFloat64 {
            val f64bits = Float64Math.fromFloat32Bits(value.toRawBits())
            return CFloat64(f64bits)
        }
        
        /**
         * Create from Int.
         */
        fun fromInt(value: Int): CFloat64 = fromDouble(value.toDouble())
        
        /**
         * Create from Long.
         */
        fun fromLong(value: Long): CFloat64 = fromDouble(value.toDouble())
    }
}
