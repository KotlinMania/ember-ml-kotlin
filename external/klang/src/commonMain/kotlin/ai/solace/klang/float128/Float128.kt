package ai.solace.klang.float128

import ai.solace.klang.bitwise.Float128Math
import ai.solace.klang.int.SwAR128
import ai.solace.klang.int.hpc.HeapUInt128

/**
 * Float128 – IEEE-754 binary128 placeholder backed by limb arithmetic.
 * 
 * Current arithmetic delegates to Double while the limb math is being implemented.
 */
class Float128 private constructor(
    private val sign: Int,
    private val exponent: Int,
    private val mantissa: HeapUInt128,
    private val specialZero: Boolean,
    private val specialInf: Boolean,
    private val specialNaN: Boolean
) : Comparable<Float128> {

    override fun compareTo(other: Float128): Int {
        if (this.specialNaN || other.specialNaN) return 0
        if (this.specialInf && other.specialInf) return sign.compareTo(other.sign)
        if (this.specialInf) return if (sign == 0) 1 else -1
        if (other.specialInf) return if (other.sign == 0) -1 else 1
        if (this.specialZero && other.specialZero) return 0
        val dblA = toDouble()
        val dblB = other.toDouble()
        return dblA.compareTo(dblB)
    }

    fun toDouble(): Double {
        if (specialNaN) return Double.NaN
        if (specialInf) return if (sign == 1) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        if (specialZero) return if (sign == 1) -0.0 else 0.0
        val packed = Float128Math.pack(sign, exponent, mantissa.toIntArray())
        return Double.fromBits(Float128Math.toFloat64Bits(packed))
    }

    override fun toString(): String {
        return when {
            specialNaN -> "NaN"
            specialInf -> if (sign == 1) "-Inf" else "Inf"
            specialZero -> if (sign == 1) "-0" else "0"
            else -> buildString {
                append(if (sign == 1) "-0x" else "0x")
                append(mantissa.toHexString())
                append("p")
                append(exponent)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Float128) return false
        if (specialNaN && other.specialNaN) return true
        return sign == other.sign && exponent == other.exponent &&
            mantissa == other.mantissa && specialZero == other.specialZero &&
            specialInf == other.specialInf && specialNaN == other.specialNaN
    }

    override fun hashCode(): Int {
        var result = sign
        result = 31 * result + exponent
        result = 31 * result + mantissa.hashCode()
        result = 31 * result + specialZero.hashCode()
        result = 31 * result + specialInf.hashCode()
        result = 31 * result + specialNaN.hashCode()
        return result
    }

    companion object {
        private const val EXP_BIAS = 16383
        private const val EXP_MAX = 0x7FFF

        fun zero(sign: Int = 0): Float128 = Float128(sign, 0, HeapUInt128.zero(), true, false, false)

        fun fromDouble(value: Double): Float128 {
            val packed = Float128Math.fromFloat64Bits(value.toRawBits())
            val (sign, exp, mant) = Float128Math.unpack(packed)
            val mantissa = HeapUInt128.fromLimbsUnsafe(mant)
            val isZero = exp == 0 && Float128Math.mantissaAllZero(mant)
            val isInf = exp == EXP_MAX && Float128Math.mantissaAllZero(mant)
            val isNaN = exp == EXP_MAX && !Float128Math.mantissaAllZero(mant)
            return Float128(sign, exp, mantissa, isZero, isInf, isNaN)
        }

        fun fromDecimalString(decimal: String): Float128 {
            val trimmed = decimal.trim()
            if (trimmed.equals("nan", ignoreCase = true)) return Float128(0, 0, HeapUInt128.zero(), false, false, true)
            if (trimmed.equals("inf", ignoreCase = true)) return Float128(0, EXP_MAX, HeapUInt128.zero(), false, true, false)
            if (trimmed.equals("-inf", ignoreCase = true)) return Float128(1, EXP_MAX, HeapUInt128.zero(), false, true, false)

            val sign = if (trimmed.startsWith("-")) 1 else 0
            val numeric = trimmed.removePrefix("-").removePrefix("+")
            if (numeric.isEmpty() || numeric == "0" || numeric == "0.0") return zero(sign)

            val parsed = decimalToBinary128(numeric)
            return Float128(sign, parsed.exponent, parsed.mantissa, parsed.isZero, false, false)
        }

        private data class Parsed(val mantissa: HeapUInt128, val exponent: Int, val isZero: Boolean)

        private fun decimalToBinary128(value: String): Parsed {
            val parts = value.split('.')
            val integerPart = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "0"
            val fractionalPart = parts.getOrNull(1) ?: ""
            var mantissa = HeapUInt128.zero()

            val temp = IntArray(SwAR128.LIMB_COUNT)
            val buffer = IntArray(SwAR128.LIMB_COUNT)
            for (ch in integerPart) {
                val digit = ch - '0'
                SwAR128.multiplyBySmall(mantissa.toIntArray(), 10, temp)
                SwAR128.addSmall(temp, digit, buffer)
                mantissa = HeapUInt128.fromLimbsUnsafe(buffer)
            }

            var exponent = 0
            if (fractionalPart.isNotEmpty()) {
                val fractionalValue = ("0.$fractionalPart").toDouble()
                val fractional128 = fromDouble(fractionalValue)
                mantissa = mantissa + fractional128.mantissa
                exponent = fractional128.exponent
            }
            return Parsed(mantissa, exponent + EXP_BIAS, false)
        }
    }
}
