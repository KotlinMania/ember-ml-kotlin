package ai.solace.klang.bitwise

import ai.solace.klang.int.SwAR128
import ai.solace.klang.int.hpc.HeapUInt128
import kotlin.math.abs

/**
 * Float128Math - placeholder quad-precision support built on top of the limb-based SWAR engine.
 *
 * NOTE: arithmetic operators are currently implemented via double conversions while the
 * limb-based mantissa math is being rebuilt. The bit-level conversions mirror the previous
 * implementation so that constants round-trip through the limb representation.
 */
object Float128Math {
    private const val EXP_BIAS = 16383
    private const val EXP_MAX = 0x7FFF
    private const val EXP_MIN = 0
    private const val DOUBLE_BIAS = 1023

    // Canonical constants -----------------------------------------------------------------------

    val ZERO_BITS: HeapUInt128 = HeapUInt128.zero()
    val ONE_BITS: HeapUInt128 = pack(0, EXP_BIAS, IntArray(7) { 0 })
    val NAN_BITS: HeapUInt128 = pack(0, EXP_MAX, mantissaWithBit(0x8000_0000_0000_0000uL))
    val INF_BITS: HeapUInt128 = pack(0, EXP_MAX, IntArray(7) { 0 })
    val NEG_INF_BITS: HeapUInt128 = pack(1, EXP_MAX, IntArray(7) { 0 })

    // Conversion helpers -----------------------------------------------------------------------

    fun fromFloat64Bits(bits: Long): HeapUInt128 {
        val sign = ((bits ushr 63) and 1L).toInt()
        val exp64 = ((bits ushr 52) and 0x7FFL).toInt()
        val frac64 = bits and 0x000F_FFFF_FFFF_FFFFL

        return when (exp64) {
            0 -> {
                if (frac64 == 0L) {
                    ZERO_BITS
                } else {
                    // Subnormal double -> map to subnormal quad (rare). Shift fraction accordingly.
                    val mantissa = buildMantissa(frac64, 112 - 52)
                    pack(sign, EXP_MIN, mantissa)
                }
            }
            0x7FF -> {
                if (frac64 == 0L) {
                    if (sign == 0) INF_BITS else NEG_INF_BITS
                } else {
                    NAN_BITS
                }
            }
            else -> {
                val exponent = exp64 - DOUBLE_BIAS + EXP_BIAS
                val combined = (1L shl 52) or frac64
                val mantissa = buildMantissa(combined, 112 - 53)
                pack(sign, exponent, mantissa)
            }
        }
    }

    fun toFloat64Bits(bits: HeapUInt128): Long {
        val (sign, exp, mantissa) = unpack(bits)
        return when {
            exp == EXP_MAX && !mantissaAllZero(mantissa) -> {
                // NaN
                (sign.toLong() shl 63) or (0x7FFL shl 52) or 1L
            }
            exp == EXP_MAX -> {
                // Infinity
                (sign.toLong() shl 63) or (0x7FFL shl 52)
            }
            exp == 0 && mantissaAllZero(mantissa) -> {
                // Zero
                sign.toLong() shl 63
            }
            else -> {
                val exponent = exp - EXP_BIAS + DOUBLE_BIAS
                val mantShift = 112 - 53
                val mantValue = extractMantissa(mantissa, mantShift)
                val frac = mantValue and 0x000F_FFFF_FFFF_FFFFL
                (sign.toLong() shl 63) or (exponent.toLong() shl 52) or frac
            }
        }
    }

    // Arithmetic (temporary double conversions) ------------------------------------------------

    fun addBits(a: HeapUInt128, b: HeapUInt128): HeapUInt128 {
        if (isNaN(a) || isNaN(b)) return NAN_BITS
        if (isInf(a)) return if (isInf(b) && compareBits(a, b) != 0) NAN_BITS else a
        if (isInf(b)) return b
        val sum = Double.fromBits(toFloat64Bits(a)) + Double.fromBits(toFloat64Bits(b))
        return fromFloat64Bits(sum.toRawBits())
    }

    fun subBits(a: HeapUInt128, b: HeapUInt128): HeapUInt128 = addBits(a, negateBits(b))

    fun mulBits(a: HeapUInt128, b: HeapUInt128): HeapUInt128 {
        if (isNaN(a) || isNaN(b)) return NAN_BITS
        if (isZero(a) || isZero(b)) {
            val sign = (signOf(a) xor signOf(b))
            return if (sign == 1) negateBits(ZERO_BITS) else ZERO_BITS
        }
        if (isInf(a) || isInf(b)) {
            val sign = signOf(a) xor signOf(b)
            return if (sign == 1) NEG_INF_BITS else INF_BITS
        }
        val product = Double.fromBits(toFloat64Bits(a)) * Double.fromBits(toFloat64Bits(b))
        return fromFloat64Bits(product.toRawBits())
    }

    fun divBits(a: HeapUInt128, b: HeapUInt128): HeapUInt128 {
        if (isNaN(a) || isNaN(b)) return NAN_BITS
        if (isZero(b)) {
            return when {
                isZero(a) -> NAN_BITS
                signOf(a) xor signOf(b) == 1 -> NEG_INF_BITS
                else -> INF_BITS
            }
        }
        if (isInf(a)) {
            return when {
                isInf(b) -> NAN_BITS
                signOf(a) xor signOf(b) == 1 -> NEG_INF_BITS
                else -> INF_BITS
            }
        }
        if (isInf(b)) {
            return if (signOf(a) xor signOf(b) == 1) negateBits(ZERO_BITS) else ZERO_BITS
        }
        val quotient = Double.fromBits(toFloat64Bits(a)) / Double.fromBits(toFloat64Bits(b))
        return fromFloat64Bits(quotient.toRawBits())
    }

    fun compareBits(a: HeapUInt128, b: HeapUInt128): Int {
        val diff = Double.fromBits(toFloat64Bits(a)).compareTo(Double.fromBits(toFloat64Bits(b)))
        return diff
    }

    fun negateBits(bits: HeapUInt128): HeapUInt128 {
        val limbs = bits.toIntArray()
        limbs[SwAR128.LIMB_COUNT - 1] = limbs.last() xor (1 shl 15)
        return HeapUInt128.fromLimbsUnsafe(limbs)
    }

    fun absBits(bits: HeapUInt128): HeapUInt128 {
        val limbs = bits.toIntArray()
        limbs[SwAR128.LIMB_COUNT - 1] = limbs.last() and 0x7FFF
        return HeapUInt128.fromLimbsUnsafe(limbs)
    }

    fun isNaN(bits: HeapUInt128): Boolean {
        val (sign, exp, mant) = unpack(bits)
        return exp == EXP_MAX && !mantissaAllZero(mant)
    }

    fun isInf(bits: HeapUInt128): Boolean {
        val (_, exp, mant) = unpack(bits)
        return exp == EXP_MAX && mantissaAllZero(mant)
    }

    fun isZero(bits: HeapUInt128): Boolean {
        val (_, exp, mant) = unpack(bits)
        return exp == 0 && mantissaAllZero(mant)
    }

    fun signOf(bits: HeapUInt128): Int {
        val top = bits.toIntArray().last()
        return (top ushr 15) and 1
    }

    // -----------------------------------------------------------------------------------------
    // Internal helpers

    internal fun pack(sign: Int, exp: Int, mantissa: IntArray): HeapUInt128 {
        val limbs = IntArray(SwAR128.LIMB_COUNT) { index ->
            if (index < mantissa.size) mantissa[index] else 0
        }
        limbs[SwAR128.LIMB_COUNT - 1] = ((sign and 1) shl 15) or (exp and 0x7FFF)
        return HeapUInt128.fromLimbsUnsafe(limbs)
    }

    internal fun unpack(bits: HeapUInt128): Triple<Int, Int, IntArray> {
        val limbs = bits.toIntArray()
        val top = limbs.last() and 0xFFFF
        val sign = (top ushr 15) and 1
        val exp = top and 0x7FFF
        val mant = IntArray(7) { idx -> limbs[idx] and 0xFFFF }
        return Triple(sign, exp, mant)
    }

    internal fun mantissaAllZero(mantissa: IntArray): Boolean = mantissa.all { it and 0xFFFF == 0 }

    private fun buildMantissa(source: Long, lsbShift: Int): IntArray {
        require(lsbShift >= 0)
        var base = HeapUInt128.one().shiftLeft(lsbShift)
        var result = HeapUInt128.zero()
        var value = source
        var index = 0
        while (value != 0L) {
            if ((value and 1L) != 0L) {
                result = result + base
            }
            value = value ushr 1
            base = base.shiftLeft(1)
            index++
        }
        val limbs = result.toIntArray()
        limbs[SwAR128.LIMB_COUNT - 1] = 0
        return IntArray(7) { idx -> limbs[idx] }
    }

    private fun extractMantissa(mantissa: IntArray, shift: Int): Long {
        var value = HeapUInt128.fromLimbsUnsafe(
            IntArray(SwAR128.LIMB_COUNT) { idx -> if (idx < mantissa.size) mantissa[idx] else 0 }
        )
        if (shift > 0) {
            value = value.shiftRight(shift)
        }
        val limbs = value.toIntArray()
        var result = 0L
        for (i in SwAR128.LIMB_COUNT - 1 downTo 0) {
            result = (result shl SwAR128.LIMB_BITS) or (limbs[i].toLong() and 0xFFFF)
        }
        return result
    }

    private fun mantissaWithBit(bit: ULong): IntArray {
        var value = HeapUInt128.zero()
        var base = HeapUInt128.one()
        var remaining = bit
        var position = 0
        while (remaining != 0uL) {
            if ((remaining and 1uL) == 1uL) {
                value = value + base
            }
            remaining = remaining shr 1
            base = base.shiftLeft(1)
            position++
        }
        val limbs = value.toIntArray()
        limbs[SwAR128.LIMB_COUNT - 1] = 0
        return IntArray(7) { idx -> limbs[idx] }
    }
}
