package ai.solace.ember.scalar

import ai.solace.ember.dtype.DType
import ai.solace.klang.bitwise.CFloat32
import ai.solace.klang.fp.CFloat16
import ai.solace.klang.fp.CFloat64

/**
 * Scalar value wrapper using KLang for bit-exact arithmetic.
 * 
 * This is the foundation for 0-dimensional tensors in Ember.
 */
sealed class Scalar {
    abstract val dtype: DType
    abstract fun toDouble(): Double
    abstract fun toFloat(): Float
    abstract fun toInt(): Int
    abstract fun toLong(): Long
    
    // ============================================
    // Float16 scalar (16-bit half precision)
    // ============================================
    
    data class Float16(val value: CFloat16) : Scalar() {
        override val dtype = DType.Float16
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        override fun toInt() = value.toFloat().toInt()
        override fun toLong() = value.toFloat().toLong()
        
        operator fun plus(other: Float16) = Float16(value + other.value)
        operator fun minus(other: Float16) = Float16(value - other.value)
        operator fun times(other: Float16) = Float16(value * other.value)
        operator fun div(other: Float16) = Float16(value / other.value)
        operator fun unaryMinus() = Float16(-value)
        
        override fun toString() = value.toString()
    }
    
    // ============================================
    // Float32 scalar (32-bit single precision)
    // ============================================
    
    data class Float32(val value: CFloat32) : Scalar() {
        override val dtype = DType.Float32
        override fun toDouble() = value.value.toDouble()
        override fun toFloat() = value.toFloat()
        override fun toInt() = value.toFloat().toInt()
        override fun toLong() = value.toFloat().toLong()
        
        operator fun plus(other: Float32) = Float32(value + other.value)
        operator fun minus(other: Float32) = Float32(value - other.value)
        operator fun times(other: Float32) = Float32(value * other.value)
        operator fun div(other: Float32) = Float32(value / other.value)
        operator fun unaryMinus() = Float32(-value)
        
        override fun toString() = value.toString()
    }
    
    // ============================================
    // Float64 scalar (64-bit double precision)
    // ============================================
    
    data class Float64(val value: CFloat64) : Scalar() {
        override val dtype = DType.Float64
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        override fun toInt() = value.toDouble().toInt()
        override fun toLong() = value.toDouble().toLong()
        
        operator fun plus(other: Float64) = Float64(value + other.value)
        operator fun minus(other: Float64) = Float64(value - other.value)
        operator fun times(other: Float64) = Float64(value * other.value)
        operator fun div(other: Float64) = Float64(value / other.value)
        operator fun unaryMinus() = Float64(-value)
        
        override fun toString() = value.toString()
    }
    
    // ============================================
    // Integer scalars (native Kotlin types)
    // ============================================
    
    data class Int8(val value: Byte) : Scalar() {
        override val dtype = DType.Int8
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        override fun toInt() = value.toInt()
        override fun toLong() = value.toLong()
        
        operator fun plus(other: Int8) = Int8((value + other.value).toByte())
        operator fun minus(other: Int8) = Int8((value - other.value).toByte())
        operator fun times(other: Int8) = Int8((value * other.value).toByte())
        operator fun div(other: Int8) = Int8((value / other.value).toByte())
        operator fun unaryMinus() = Int8((-value).toByte())
        
        override fun toString() = value.toString()
    }
    
    data class Int32(val value: Int) : Scalar() {
        override val dtype = DType.Int32
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        override fun toInt() = value
        override fun toLong() = value.toLong()
        
        operator fun plus(other: Int32) = Int32(value + other.value)
        operator fun minus(other: Int32) = Int32(value - other.value)
        operator fun times(other: Int32) = Int32(value * other.value)
        operator fun div(other: Int32) = Int32(value / other.value)
        operator fun unaryMinus() = Int32(-value)
        
        override fun toString() = value.toString()
    }
    
    data class Int64(val value: Long) : Scalar() {
        override val dtype = DType.Int64
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        override fun toInt() = value.toInt()
        override fun toLong() = value
        
        operator fun plus(other: Int64) = Int64(value + other.value)
        operator fun minus(other: Int64) = Int64(value - other.value)
        operator fun times(other: Int64) = Int64(value * other.value)
        operator fun div(other: Int64) = Int64(value / other.value)
        operator fun unaryMinus() = Int64(-value)
        
        override fun toString() = value.toString()
    }
    
    // ============================================
    // Boolean scalar
    // ============================================
    
    data class Bool(val value: Boolean) : Scalar() {
        override val dtype = DType.Bool
        override fun toDouble() = if (value) 1.0 else 0.0
        override fun toFloat() = if (value) 1.0f else 0.0f
        override fun toInt() = if (value) 1 else 0
        override fun toLong() = if (value) 1L else 0L
        
        operator fun not() = Bool(!value)
        infix fun and(other: Bool) = Bool(value && other.value)
        infix fun or(other: Bool) = Bool(value || other.value)
        infix fun xor(other: Bool) = Bool(value xor other.value)
        
        override fun toString() = value.toString()
    }
    
    companion object {
        /**
         * Create scalar from value and dtype.
         */
        fun fromValue(value: Number, dtype: DType): Scalar = when (dtype) {
            DType.Float16 -> Float16(CFloat16.fromFloat(value.toFloat()))
            DType.Float32 -> Float32(CFloat32.fromFloat(value.toFloat()))
            DType.Float64 -> Float64(CFloat64.fromDouble(value.toDouble()))
            DType.Int8 -> Int8(value.toByte())
            DType.Int32 -> Int32(value.toInt())
            DType.Int64 -> Int64(value.toLong())
            else -> throw IllegalArgumentException("Unsupported dtype for scalar: $dtype")
        }
        
        /**
         * Create scalar from boolean.
         */
        fun fromBoolean(value: Boolean): Scalar = Bool(value)
    }
}
