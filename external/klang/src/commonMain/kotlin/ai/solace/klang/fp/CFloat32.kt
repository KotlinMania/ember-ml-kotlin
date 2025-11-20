package ai.solace.klang.fp

import ai.solace.klang.bitwise.CFloatTrace
import kotlin.math.abs

/**
 * Inline value representing a C-style 32-bit floating point number.
 *
 * Kotlin/Native and the JVM both follow IEEE-754 semantics, but certain C code
 * (including ggml) relies on the fact that every intermediate operation is
 * rounded back to single precision immediately. This wrapper preserves that
 * behaviour explicitly, providing convenience operators that always truncate to
 * 32-bit precision after each arithmetic step.
 */
class CFloat32 private constructor(private val bits: Int) {
    val value: Float get() = Float.fromBits(bits)

    fun toFloat(): Float = value

    fun toBits(): Int = bits

    operator fun unaryMinus(): CFloat32 = fromFloat(-value)

    operator fun plus(other: CFloat32): CFloat32 {
        val resBits = Float32Math.addBits(this.bits, other.bits)
        val wrapped = fromBits(resBits)
        CFloatTrace.log("plus", bits, other.bits, wrapped.bits)
        return wrapped
    }

    operator fun plus(other: Float): CFloat32 {
        val resBits = Float32Math.addBits(this.bits, other.toRawBits())
        val wrapped = fromBits(resBits)
        CFloatTrace.log("plusF", bits, other.toRawBits(), wrapped.bits)
        return wrapped
    }

    operator fun minus(other: CFloat32): CFloat32 {
        val resBits = Float32Math.subBits(this.bits, other.bits)
        val wrapped = fromBits(resBits)
        CFloatTrace.log("minus", bits, other.bits, wrapped.bits)
        return wrapped
    }

    operator fun minus(other: Float): CFloat32 {
        val resBits = Float32Math.subBits(this.bits, other.toRawBits())
        val wrapped = fromBits(resBits)
        CFloatTrace.log("minusF", bits, other.toRawBits(), wrapped.bits)
        return wrapped
    }

    operator fun times(other: CFloat32): CFloat32 {
        val resBits = Float32Math.mulBits(this.bits, other.bits)
        val wrapped = fromBits(resBits)
        CFloatTrace.log("times", bits, other.bits, wrapped.bits)
        return wrapped
    }

    operator fun times(other: Float): CFloat32 {
        val resBits = Float32Math.mulBits(this.bits, other.toRawBits())
        val wrapped = fromBits(resBits)
        CFloatTrace.log("timesF", bits, other.toRawBits(), wrapped.bits)
        return wrapped
    }

    // Bit-true float32 multiply using software IEEE-754 rounding.
    fun timesExact(other: CFloat32): CFloat32 {
        val resBits = Float32Math.mulBits(this.bits, other.bits)
        val wrapped = fromBits(resBits)
        CFloatTrace.log("timesExact", bits, other.bits, wrapped.bits)
        return wrapped
    }

    fun timesExact(other: Float): CFloat32 {
        val resBits = Float32Math.mulBits(this.bits, other.toRawBits())
        val wrapped = fromBits(resBits)
        CFloatTrace.log("timesExactF", bits, other.toRawBits(), wrapped.bits)
        return wrapped
    }

    fun plusExact(other: CFloat32): CFloat32 {
        val res = Float32Math.addPos(this.value, other.value)
        val wrapped = fromFloat(res)
        CFloatTrace.log("plusExact", bits, other.bits, wrapped.bits)
        return wrapped
    }

    fun plusExact(other: Float): CFloat32 {
        val res = Float32Math.addPos(this.value, other)
        val wrapped = fromFloat(res)
        CFloatTrace.log("plusExactF", bits, other.toRawBits(), wrapped.bits)
        return wrapped
    }

    operator fun div(other: CFloat32): CFloat32 {
        val resBits = Float32Math.divBits(this.bits, other.bits)
        val wrapped = fromBits(resBits)
        CFloatTrace.log("div", bits, other.bits, wrapped.bits)
        return wrapped
    }

    operator fun div(other: Float): CFloat32 {
        val resBits = Float32Math.divBits(this.bits, other.toRawBits())
        val wrapped = fromBits(resBits)
        CFloatTrace.log("divF", bits, other.toRawBits(), wrapped.bits)
        return wrapped
    }

    fun abs(): CFloat32 = unary("abs", null) { a, _ -> abs(a) }

    fun fma(multiplier: CFloat32, addend: CFloat32): CFloat32 {
        val result = (value.toDouble() + multiplier.value.toDouble() * addend.value.toDouble()).toFloat()
        val wrapped = fromFloat(result)
        CFloatTrace.log("fma", bits, multiplier.bits, wrapped.bits)
        return wrapped
    }

    fun fma(multiplier: Float, addend: Float): CFloat32 {
        val result = (value.toDouble() + multiplier.toDouble() * addend.toDouble()).toFloat()
        val wrapped = fromFloat(result)
        CFloatTrace.log("fmaF", bits, multiplier.toRawBits(), wrapped.bits)
        return wrapped
    }

    private inline fun binary(opName: String, rhsBits: Int, op: (Float, Float) -> Float): CFloat32 {
        val result: Float = op(value, Float.fromBits(rhsBits))
        val wrapped = fromFloat(result)
        CFloatTrace.log(opName, bits, rhsBits, wrapped.bits)
        return wrapped
    }

    private inline fun unary(opName: String, other: Float?, op: (Float, Float) -> Float): CFloat32 {
        val rhsVal = other ?: 0f
        val result: Float = op(value, rhsVal)
        val wrapped = fromFloat(result)
        CFloatTrace.log(opName, bits, other?.toRawBits(), wrapped.bits)
        return wrapped
    }

    override fun toString(): String = value.toString()

    companion object {
        val ZERO: CFloat32 = CFloat32(0)

        fun fromFloat(value: Float): CFloat32 = CFloat32(value.toRawBits())

        fun fromBits(bits: Int): CFloat32 = CFloat32(bits)
    }
}

fun Float.toCFloat32(): CFloat32 = CFloat32.fromFloat(this)
