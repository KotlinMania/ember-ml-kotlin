package ai.solace.klang.fp

/**
 * CFloat128: 128-bit extended precision using double-double arithmetic.
 * 
 * This implements quad-precision (128-bit) floating point using a pair of
 * Double values (hi + lo) where lo captures the error/residual from hi.
 * 
 * This provides approximately 106 bits of mantissa precision (vs 53 for Double).
 * 
 * Based on algorithms from:
 * - QD library (quad-double)
 * - Dekker's double-double arithmetic
 * - Accurate floating-point summation and product algorithms
 * 
 * Also known as DoubleDouble in academic literature.
 */
data class CFloat128(val hi: Double, val lo: Double) {
    operator fun plus(other: CFloat128): CFloat128 {
        val (s, e) = twoSum(hi, other.hi)
        val loSum = lo + other.lo + e
        val (resHi, resLo) = quickTwoSum(s, loSum)
        return CFloat128(resHi, resLo)
    }

    operator fun plus(value: Double): CFloat128 {
        val (s, e) = twoSum(hi, value)
        val loSum = lo + e
        val (resHi, resLo) = quickTwoSum(s, loSum)
        return CFloat128(resHi, resLo)
    }

    operator fun minus(value: CFloat128): CFloat128 = this + (-value)

    operator fun unaryMinus(): CFloat128 = CFloat128(-hi, -lo)

    operator fun times(value: Double): CFloat128 {
        val (p, e) = twoProd(hi, value)
        val loTerm = lo * value + e
        val (resHi, resLo) = quickTwoSum(p, loTerm)
        return CFloat128(resHi, resLo)
    }

    operator fun times(other: CFloat128): CFloat128 {
        val (p, e) = twoProd(hi, other.hi)
        var result = CFloat128(p, e)
        result = result.addProduct(hi, other.lo)
        result = result.addProduct(lo, other.hi)
        result = result.addProduct(lo, other.lo)
        return result
    }

    fun addProduct(a: Double, b: Double): CFloat128 {
        val (p, e) = twoProd(a, b)
        val (s, err) = twoSum(hi, p)
        val t = lo + e + err
        val (resHi, resLo) = quickTwoSum(s, t)
        return CFloat128(resHi, resLo)
    }

    fun toDouble(): Double = hi + lo

    fun toFloat(): Float = (hi + lo).toFloat()

    companion object {
        val ZERO = CFloat128(0.0, 0.0)
        val ONE = CFloat128(1.0, 0.0)
        
        fun fromDouble(value: Double): CFloat128 = CFloat128(value, 0.0)

        fun fromFloat(value: Float): CFloat128 = fromDouble(value.toDouble())
        
        fun fromCFloat64(value: CFloat64): CFloat128 = fromDouble(value.toDouble())
        
        fun fromCFloat16(value: CFloat16): CFloat128 = fromDouble(value.toFloat().toDouble())

        // Fused multiply-subtract at double-double precision:
        // returns (a*b) - (c*d) with compensation across both products and the subtraction.
        fun fms(a: CFloat128, b: CFloat128, c: CFloat128, d: CFloat128): CFloat128 {
            // Compute both products in extended precision
            val ab = a * b
            val cd = c * d
            // Subtract with compensated summation
            return ab - cd
        }

        private fun twoSum(a: Double, b: Double): Pair<Double, Double> {
            val s = a + b
            val bb = s - a
            val err = (a - (s - bb)) + (b - bb)
            return s to err
        }

        private fun quickTwoSum(a: Double, b: Double): Pair<Double, Double> {
            val s = a + b
            val err = b - (s - a)
            return s to err
        }

        private fun twoProd(a: Double, b: Double): Pair<Double, Double> {
            val p = a * b
            val aHigh = splitHigh(a)
            val aLow = a - aHigh
            val bHigh = splitHigh(b)
            val bLow = b - bHigh
            val err = ((aHigh * bHigh - p) + aHigh * bLow + aLow * bHigh) + aLow * bLow
            return p to err
        }

        private const val SPLIT_CONSTANT = 134217729.0 // 2^27 + 1

        private fun splitHigh(x: Double): Double {
            val c = SPLIT_CONSTANT * x
            val high = c - (c - x)
            return high
        }
    }
}

// Extension functions for easy conversion
fun Float.toCFloat128(): CFloat128 = CFloat128.fromFloat(this)
fun Double.toCFloat128(): CFloat128 = CFloat128.fromDouble(this)
fun CFloat64.toCFloat128(): CFloat128 = CFloat128.fromCFloat64(this)
fun CFloat16.toCFloat128(): CFloat128 = CFloat128.fromCFloat16(this)

