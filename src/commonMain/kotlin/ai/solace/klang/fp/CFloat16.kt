package ai.solace.klang.fp

import ai.solace.klang.bitwise.Float16Math
import kotlin.jvm.JvmInline

/**
 * CFloat16: 16-bit IEEE-754 binary16 (half precision) float.
 * 
 * Format: 1 sign bit, 5 exponent bits, 10 mantissa bits
 * - Sign: bit 15
 * - Exponent: bits 14-10 (bias = 15)
 * - Mantissa: bits 9-0
 * 
 * Uses Int storage (32-bit) for overflow room during operations,
 * only using lower 16 bits for the actual value.
 */
@JvmInline
value class CFloat16 private constructor(private val bits: Int) {
    
    val value: Float get() = Float.fromBits(Float16Math.toFloat32Bits(bits))
    
    fun toFloat(): Float = value
    fun toDouble(): Double = value.toDouble()
    fun toBits(): Int = bits and 0xFFFF
    fun toUShort(): UShort = (bits and 0xFFFF).toUShort()
    
    // Unary operators
    operator fun unaryMinus(): CFloat16 = fromBits(Float16Math.negateBits(bits))
    
    // Arithmetic operators using Float16Math for bit-exact operations
    operator fun plus(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.addBits(this.bits, other.bits))
    }
    
    operator fun minus(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.subBits(this.bits, other.bits))
    }
    
    operator fun times(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.mulBits(this.bits, other.bits))
    }
    
    operator fun div(other: CFloat16): CFloat16 {
        return fromBits(Float16Math.divBits(this.bits, other.bits))
    }
    
    // Comparison using Float16Math
    operator fun compareTo(other: CFloat16): Int {
        return Float16Math.compareBits(this.bits, other.bits)
    }
    
    // String representation
    override fun toString(): String = value.toString()
    
    companion object {
        // Special values
        val ZERO = CFloat16(Float16Math.ZERO_BITS)
        val ONE = CFloat16(Float16Math.ONE_BITS)
        val NaN = CFloat16(Float16Math.NAN_BITS)
        val POSITIVE_INFINITY = CFloat16(Float16Math.INF_BITS)
        val NEGATIVE_INFINITY = CFloat16(Float16Math.NEG_INF_BITS)
        
        /**
         * Create CFloat16 from raw bits (16-bit value in Int).
         */
        fun fromBits(bits: Int): CFloat16 = CFloat16(bits and 0xFFFF)
        
        /**
         * Create CFloat16 from UShort bits.
         */
        fun fromBits(bits: UShort): CFloat16 = CFloat16(bits.toInt() and 0xFFFF)
        
        /**
         * Create CFloat16 from Float32 using Float16Math.
         */
        fun fromFloat(value: Float): CFloat16 {
            val f32bits = value.toRawBits()
            val f16bits = Float16Math.fromFloat32Bits(f32bits)
            return CFloat16(f16bits)
        }
        
        /**
         * Create from Double.
         */
        fun fromDouble(value: Double): CFloat16 = fromFloat(value.toFloat())
        
        /**
         * Create from Int.
         */
        fun fromInt(value: Int): CFloat16 = fromFloat(value.toFloat())
    }
}
