package ai.solace.klang.bitwise

data class DoubleDouble(val hi: Double, val lo: Double) {
    operator fun plus(other: DoubleDouble): DoubleDouble {
        val (s, e) = twoSum(hi, other.hi)
        val loSum = lo + other.lo + e
        val (resHi, resLo) = quickTwoSum(s, loSum)
        return DoubleDouble(resHi, resLo)
    }

    operator fun plus(value: Double): DoubleDouble {
        val (s, e) = twoSum(hi, value)
        val loSum = lo + e
        val (resHi, resLo) = quickTwoSum(s, loSum)
        return DoubleDouble(resHi, resLo)
    }

    operator fun minus(value: DoubleDouble): DoubleDouble = this + (-value)

    operator fun unaryMinus(): DoubleDouble = DoubleDouble(-hi, -lo)

    operator fun times(value: Double): DoubleDouble {
        val (p, e) = twoProd(hi, value)
        val loTerm = lo * value + e
        val (resHi, resLo) = quickTwoSum(p, loTerm)
        return DoubleDouble(resHi, resLo)
    }

    operator fun times(other: DoubleDouble): DoubleDouble {
        val (p, e) = twoProd(hi, other.hi)
        var result = DoubleDouble(p, e)
        result = result.addProduct(hi, other.lo)
        result = result.addProduct(lo, other.hi)
        result = result.addProduct(lo, other.lo)
        return result
    }

    fun addProduct(a: Double, b: Double): DoubleDouble {
        val (p, e) = twoProd(a, b)
        val (s, err) = twoSum(hi, p)
        val t = lo + e + err
        val (resHi, resLo) = quickTwoSum(s, t)
        return DoubleDouble(resHi, resLo)
    }

    fun toDouble(): Double = hi + lo

    fun toFloat(): Float = (hi + lo).toFloat()

    companion object {
        fun fromDouble(value: Double): DoubleDouble = DoubleDouble(value, 0.0)

        fun fromFloat(value: Float): DoubleDouble = fromDouble(value.toDouble())

        // Fused multiply-subtract at double-double precision:
        // returns (a*b) - (c*d) with compensation across both products and the subtraction.
        fun fms(a: DoubleDouble, b: DoubleDouble, c: DoubleDouble, d: DoubleDouble): DoubleDouble {
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

fun Float.toDoubleDouble(): DoubleDouble = DoubleDouble.fromFloat(this)

fun Double.toDoubleDouble(): DoubleDouble = DoubleDouble.fromDouble(this)
