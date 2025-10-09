package ai.solace.ember.tensor.bitwise

/**
 * Default implementation of AdvancedMathOperations interface.
 * This class provides implementations for the methods defined in the AdvancedMathOperations interface.
 */
class DefaultAdvancedMathOperations(private val megaNumber: MegaNumber) : AdvancedMathOperations {
    /**
     * Calculate the square root
     * 
     * @return The square root
     */
    override fun sqrt(): MegaNumber {
        return Companion.sqrt(megaNumber)
    }
    
    companion object {
        /**
         * Compute the square root of a MegaNumber.
         * For integer values, returns the integer square root.
         * For float values, returns a float approximation.
         *
         * @param megaNumber The MegaNumber to compute the square root of
         * @return The square root as a MegaNumber
         * @throws IllegalArgumentException if the MegaNumber is negative
         */
        fun sqrt(megaNumber: MegaNumber): MegaNumber {
            if (megaNumber.negative) {
                throw IllegalArgumentException("Cannot compute square root of a negative number")
            }

            // If zero, return zero
            if (megaNumber.mantissa.size == 1 && megaNumber.mantissa[0] == 0) {
                return MegaNumber(
                    mantissa = intArrayOf(0),
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = false,
                    isFloat = megaNumber.isFloat
                )
            }

            // For integer values
            if (!megaNumber.isFloat) {
                // Use binary search to find the integer square root
                val a = megaNumber.mantissa.copyOf()
                var low = intArrayOf(0)
                var high = a.copyOf()

                while (true) {
                    // mid = (low + high) / 2
                    val sumLH = DefaultChunkOperations.addChunks(low, high)
                    val mid = div2(sumLH)

                    // Check if we've converged
                    val cLo = DefaultChunkOperations.compareAbs(mid, low)
                    val cHi = DefaultChunkOperations.compareAbs(mid, high)
                    if (cLo == 0 || cHi == 0) {
                        return MegaNumber(mid, MegaNumber(intArrayOf(0)), false)
                    }

                    // mid^2
                    val midSqr = DefaultChunkOperations.mulChunks(mid, mid)

                    // Compare mid^2 with a
                    val cCmp = DefaultChunkOperations.compareAbs(midSqr, a)
                    if (cCmp == 0) {
                        return MegaNumber(mid, MegaNumber(intArrayOf(0)), false)
                    } else if (cCmp < 0) {
                        low = mid
                    } else {
                        high = mid
                    }
                }
            } else {
                // For float values, use floatSqrt
                return floatSqrt(megaNumber)
            }
        }

        /**
         * Compute the square root for float values.
         *
         * @param megaNumber The MegaNumber to compute the square root of
         * @return The square root as a MegaNumber with float representation
         */
        private fun floatSqrt(megaNumber: MegaNumber): MegaNumber {
            // Get the exponent as an integer
            val totalExp = expAsInt(megaNumber.exponent)

            // Check if exponent is odd
            val remainder = totalExp and 1          // 1 = odd, 0 = even

            // Make a working copy of mantissa
            var workMantissa = megaNumber.mantissa.copyOf()
            var adjustedExp = totalExp

            // If exponent is odd, adjust mantissa and exponent
            if (remainder != 0) {
                if (totalExp > 0) {
                    // Double the mantissa (shift left by 1 bit)
                    var carry = 0
                    val result = IntArray(workMantissa.size + 1)
                    for (i in workMantissa.indices) {
                        val doubled = (workMantissa[i] shl 1) + carry
                        result[i] = doubled and MegaNumberConstants.MASK.toInt()
                        carry = doubled shr MegaNumberConstants.GLOBAL_CHUNK_SIZE
                    }
                    if (carry != 0) {
                        result[workMantissa.size] = carry
                    }
                    workMantissa = result
                    adjustedExp = adjustedExp - 1
                } else {
                    // Halve the mantissa (shift right by 1 bit)
                    val result = IntArray(workMantissa.size)
                    var carry = 0
                    for (i in workMantissa.indices.reversed()) {
                        val value = workMantissa[i]
                        result[i] = (value shr 1) or (carry shl (MegaNumberConstants.GLOBAL_CHUNK_SIZE - 1))
                        carry = value and 1
                    }
                    workMantissa = result
                    adjustedExp = adjustedExp + 1
                }
            }

            // Half of exponent
            val halfExp = adjustedExp / 2

            // Do integer sqrt on workMantissa
            var low = intArrayOf(0)
            var high = workMantissa.copyOf()
            var sqrtMantissa: IntArray

            while (true) {
                // mid = (low + high) / 2
                val sumLH = DefaultChunkOperations.addChunks(low, high)
                val mid = div2(sumLH)

                // Check if we've converged
                val cLo = DefaultChunkOperations.compareAbs(mid, low)
                val cHi = DefaultChunkOperations.compareAbs(mid, high)
                if (cLo == 0 || cHi == 0) {
                    sqrtMantissa = mid
                    break
                }

                // mid^2
                val midSqr = DefaultChunkOperations.mulChunks(mid, mid)

                // Compare mid^2 with workMantissa
                val cCmp = DefaultChunkOperations.compareAbs(midSqr, workMantissa)
                if (cCmp == 0) {
                    sqrtMantissa = mid
                    break
                } else if (cCmp < 0) {
                    low = mid
                } else {
                    high = mid
                }
            }

            // Create the result with the adjusted exponent
            val newExponent = MegaNumber(
                mantissa = intArrayOf(kotlin.math.abs(halfExp)),
                negative = halfExp < 0
            )

            return MegaNumber(
                mantissa = sqrtMantissa,
                exponent = newExponent,
                negative = false,
                isFloat = true
            )
        }

        /**
         * Right shift chunk-limbs by 1 bit => integer //2.
         */
        private fun div2(limbs: IntArray): IntArray {
            if (limbs.size == 1 && limbs[0] == 0) return intArrayOf(0)

            val result = IntArray(limbs.size)
            var carry = 0          // LSB carried from higher limb

            for (i in limbs.indices.reversed()) {
                val cur = limbs[i].toLong() and 0xFFFFFFFFL
                result[i] = ((cur ushr 1) or ((carry.toLong() and 1) shl (MegaNumberConstants.GLOBAL_CHUNK_SIZE - 1))).toInt()
                carry = (cur and 1L).toInt()   // next iteration uses this bit
            }

            // Trim trailing zero limbs
            var last = result.size - 1
            while (last > 0 && result[last] == 0) last--
            return result.copyOf(last + 1)
        }

        /**
         * Treat a MegaNumber exponent as a signed Int
         */
        private fun expAsInt(exponent: MegaNumber): Int {
            val absVal = chunksToInt(exponent.mantissa)
            return if (exponent.negative) -absVal else absVal
        }

        /**
         * Convert a chunk array to an Int value.
         * Only uses the first chunk for simplicity.
         */
        private fun chunksToInt(chunks: IntArray): Int {
            if (chunks.isEmpty()) {
                return 0
            }

            // Mask to 31 bits to avoid sign‑extension if bit 31 is set.
            return chunks[0] and 0x7FFFFFFF
        }
    }
}