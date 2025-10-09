package ai.solace.ember.tensor.bitwise

/**
 * Utility class for arithmetic operations on MegaNumber instances.
 */
object ArithmeticUtils {
    /**
     * Extension function to convert a MegaNumber exponent to Int value
     * 
     * @return The exponent as a signed Int
     */
    private fun MegaNumber.expAsInt(): Int {
        if (this.mantissa.isEmpty()) return 0

        // Handle single-chunk case for efficiency
        if (this.mantissa.size == 1) {
            val value = this.mantissa[0]
            return if (this.negative) -value else value
        }

        // Multi-chunk case - limit to Int.MAX_VALUE/MIN_VALUE
        return if (this.negative) -Int.MAX_VALUE else Int.MAX_VALUE
    }
    /**
     * Add two MegaNumbers. If either is float, handle float addition.
     *
     * @param a First MegaNumber
     * @param b Second MegaNumber
     * @return Sum as a MegaNumber
     */
    fun add(a: MegaNumber, b: MegaNumber): MegaNumber {
        // If either is float, handle float addition
        if (a.isFloat || b.isFloat) {
            return addFloat(a, b)
        }

        // Integer addition
        val aMantissa = a.mantissa
        val bMantissa = b.mantissa

        // If signs are the same, add magnitudes
        if (a.negative == b.negative) {
            val sum = DefaultChunkOperations.addChunks(aMantissa, bMantissa)
            return MegaNumber(
                mantissa = sum,
                exponent = MegaNumber(intArrayOf(0)),
                negative = a.negative,
                isFloat = false
            )
        } else {
            // Signs differ, subtract the smaller magnitude from the larger
            val c = DefaultChunkOperations.compareAbs(aMantissa, bMantissa)
            if (c == 0) {
                // Equal magnitudes with opposite signs => zero
                return MegaNumber(
                    mantissa = intArrayOf(0),
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = false,
                    isFloat = false
                )
            } else if (c > 0) {
                // |a| > |b|, so result has sign of a
                val diff = DefaultChunkOperations.subChunks(aMantissa, bMantissa)
                return MegaNumber(
                    mantissa = diff,
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = a.negative,
                    isFloat = false
                )
            } else {
                // |a| < |b|, so result has sign of b
                val diff = DefaultChunkOperations.subChunks(bMantissa, aMantissa)
                return MegaNumber(
                    mantissa = diff,
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = b.negative,
                    isFloat = false
                )
            }
        }
    }

    /**
     * Subtract two MegaNumbers. a - b = a + (-b)
     *
     * @param a First MegaNumber
     * @param b Second MegaNumber
     * @return Difference as a MegaNumber
     */
    fun subtract(a: MegaNumber, b: MegaNumber): MegaNumber {
        val negB = MegaNumber(
            mantissa = b.mantissa.copyOf(),
            exponent = MegaNumber(b.exponent.mantissa.copyOf(), negative = b.exponent.negative),
            negative = !b.negative,
            isFloat = b.isFloat
        )
        return add(a, negB)
    }

    /**
     * Multiply two MegaNumbers. If either is float, delegate to float multiply.
     *
     * @param a First MegaNumber
     * @param b Second MegaNumber
     * @return Product as a MegaNumber
     */
    fun multiply(a: MegaNumber, b: MegaNumber): MegaNumber {
        if (a.isFloat || b.isFloat) {
            return multiplyFloat(a, b)
        }

        // Integer multiply
        val sign = (a.negative != b.negative)
        val product = DefaultChunkOperations.mulChunks(a.mantissa, b.mantissa)
        return MegaNumber(
            mantissa = product,
            exponent = MegaNumber(intArrayOf(0)),
            negative = sign,
            isFloat = false
        )
    }

    /**
     * Divide two MegaNumbers. If either is float, delegate to float division.
     *
     * @param a First MegaNumber (dividend)
     * @param b Second MegaNumber (divisor)
     * @return Quotient as a MegaNumber
     */
    fun divide(a: MegaNumber, b: MegaNumber): MegaNumber {
        // Unified public entry‑point – dispatches to integer or float path
        return if (a.isFloat || b.isFloat) {
            divideFloat(a, b)
        } else {
            divideInteger(a, b)
        }
    }

    /**
     * Float addition using chunk-based arithmetic.
     *
     * @param a First MegaNumber
     * @param b Second MegaNumber
     * @return Sum as a MegaNumber
     */
    private fun addFloat(a: MegaNumber, b: MegaNumber): MegaNumber {
        // Signed exponents as Int
        val expA = a.exponent.expAsInt()
        val expB = b.exponent.expAsInt()

        // Align mantissas
        val expDiff = expA - expB
        val (alignedA, alignedB, resultExp) = if (expDiff > 0) {
            // A has larger exponent, shift B
            val shiftedB = DefaultBitManipulationOperations.shiftRight(b.mantissa, expDiff)
            Triple<IntArray, IntArray, Int>(a.mantissa, shiftedB, expA)
        } else {
            if (expDiff < 0) {
                // B has larger exponent, shift A
                val shiftedA = DefaultBitManipulationOperations.shiftRight(a.mantissa, -expDiff)
                Triple<IntArray, IntArray, Int>(shiftedA, b.mantissa, expB)
            } else {
                // Same exponent, no shift needed
                Triple<IntArray, IntArray, Int>(a.mantissa, b.mantissa, expA)
            }
        }

        // Now add or subtract aligned mantissas
        val resultMant: IntArray
        val resultNeg: Boolean

        if (a.negative == b.negative) {
            // Same sign, add magnitudes
            resultMant = DefaultChunkOperations.addChunks(alignedA, alignedB)
            resultNeg = a.negative
        } else {
            // Different signs, subtract smaller from larger
            val cmp = DefaultChunkOperations.compareAbs(alignedA, alignedB)
            if (cmp == 0) {
                // Equal magnitudes with opposite signs => zero
                return MegaNumber(
                    mantissa = intArrayOf(0),
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = false,
                    isFloat = true
                )
            } else if (cmp > 0) {
                // |A| > |B|, result has sign of A
                resultMant = DefaultChunkOperations.subChunks(alignedA, alignedB)
                resultNeg = a.negative
            } else {
                // |A| < |B|, result has sign of B
                resultMant = DefaultChunkOperations.subChunks(alignedB, alignedA)
                resultNeg = b.negative
            }
        }

        // Create result with proper exponent
        val resultExpObj = MegaNumber(
            mantissa = intArrayOf(kotlin.math.abs(resultExp)),
            negative = resultExp < 0
        )

        val result = MegaNumber(
            mantissa = resultMant,
            exponent = resultExpObj,
            negative = resultNeg,
            isFloat = true
        )
        normalize(result)
        return result
    }

    /**
     * Float multiplication using chunk-based arithmetic.
     *
     * @param a First MegaNumber
     * @param b Second MegaNumber
     * @return Product as a MegaNumber
     */
    private fun multiplyFloat(a: MegaNumber, b: MegaNumber): MegaNumber {
        // Multiply mantissas
        val productMant = DefaultChunkOperations.mulChunks(a.mantissa, b.mantissa)

        // Add exponents (signed)
        val sumExp = a.exponent.expAsInt() + b.exponent.expAsInt()
        val newExponent = MegaNumber(
            mantissa = intArrayOf(kotlin.math.abs(sumExp)),
            negative = sumExp < 0
        )

        // Determine sign
        val newNegative = (a.negative != b.negative)

        val result = MegaNumber(
            mantissa = productMant,
            exponent = newExponent,
            negative = newNegative,
            isFloat = true
        )
        normalize(result)
        return result
    }

    /**
     * Divide chunk-limb arrays => (quotient, remainder), integer division
     */
    private fun chunkDivide(a: IntArray, b: IntArray): Pair<IntArray, IntArray> {
        // B must not be zero
        if (b.size == 1 && b[0] == 0) {
            throw ArithmeticException("Division by zero")
        }
        val c = DefaultChunkOperations.compareAbs(a, b)
        if (c < 0) return Pair(intArrayOf(0), a.copyOf()) // A<B => Q=0, R=A
        if (c == 0) return Pair(intArrayOf(1), intArrayOf(0)) // A=B => Q=1, R=0

        val q = IntArray(a.size)
        var r = intArrayOf(0)

        // We do a standard chunk-based short division
        for (i in a.indices.reversed()) {
            // shift R left by one chunk
            r = IntArray(r.size + 1).also {
                r.copyInto(it, 1)
                it[0] = a[i]
            }

            // binary search in [0..BASE-1] for the best q
            var low = 0
            var high = Int.MAX_VALUE // Approximation for BASE-1
            var guess = 0

            while (low <= high) {
                val mid = (low + high) ushr 1 // Unsigned shift to avoid sign issues
                val mm = DefaultChunkOperations.mulChunks(b, intArrayOf(mid))
                val cmpv = DefaultChunkOperations.compareAbs(mm, r)
                if (cmpv <= 0) {
                    guess = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            if (guess != 0) {
                val mm = DefaultChunkOperations.mulChunks(b, intArrayOf(guess))
                r = DefaultChunkOperations.subChunks(r, mm)
            }
            q[i] = guess
        }

        // Trim q
        var lastNonZero = q.size - 1
        while (lastNonZero > 0 && q[lastNonZero] == 0) {
            lastNonZero--
        }
        return Pair(q.copyOf(lastNonZero + 1), r)
    }

    /**
     * Integer division branch used when both operands are integers.
     *
     * @param a First MegaNumber (dividend)
     * @param b Second MegaNumber (divisor)
     * @return Quotient as a MegaNumber
     */
    private fun divideInteger(a: MegaNumber, b: MegaNumber): MegaNumber {
        // Check for division by zero
        if (b.mantissa.size == 1 && b.mantissa[0] == 0) {
            throw ArithmeticException("Division by zero")
        }

        // Compute quotient and remainder
        val (quotient, _) = chunkDivide(a.mantissa, b.mantissa)

        // Determine sign
        val newNegative = (a.negative != b.negative)

        return MegaNumber(
            mantissa = quotient,
            exponent = MegaNumber(intArrayOf(0)),
            negative = newNegative,
            isFloat = false
        )
    }

    /**
     * Float division branch used when either operand is float.
     *
     * @param a First MegaNumber (dividend)
     * @param b Second MegaNumber (divisor)
     * @return Quotient as a MegaNumber
     */
    private fun divideFloat(a: MegaNumber, b: MegaNumber): MegaNumber {
        // Divide mantissas
        val (quotientMant, _) = chunkDivide(a.mantissa, b.mantissa)
        // Subtract exponents
        val diffExp = a.exponent.expAsInt() - b.exponent.expAsInt()
        val newExponent = MegaNumber(intArrayOf(kotlin.math.abs(diffExp)), negative = diffExp < 0)
        // Determine sign
        val newNegative = (a.negative != b.negative)
        val result = MegaNumber(
            mantissa = quotientMant,
            exponent = newExponent,
            negative = newNegative,
            isFloat = true
        )
        normalize(result)
        return result
    }

    /**
     * Normalize a MegaNumber by removing leading/trailing zeros and handling zero sign.
     * 
     * @param num The MegaNumber to normalize
     */
    private fun normalize(num: MegaNumber) {
        // If mantissa is empty or all zeros, set to canonical zero
        if (num.mantissa.isEmpty() || num.mantissa.all { it == 0 }) {
            num.mantissa = intArrayOf(0)
            num.negative = false
            num.exponent = MegaNumber(intArrayOf(0))
            return
        }

        // Remove leading zeros unless keepLeadingZeros is true
        if (!num.keepLeadingZeros) {
            var firstNonZero = 0
            while (firstNonZero < num.mantissa.size - 1 && num.mantissa[firstNonZero] == 0) {
                firstNonZero++
            }

            if (firstNonZero > 0) {
                val newMantissa = IntArray(num.mantissa.size - firstNonZero)
                num.mantissa.copyInto(newMantissa, 0, firstNonZero, num.mantissa.size)
                num.mantissa = newMantissa
            }
        }
    }
}
