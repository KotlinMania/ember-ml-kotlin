package ai.solace.ember.tensor.bitwise

/**
 * Default implementation of PowerOperations interface.
 * This class provides implementations for the methods defined in the PowerOperations interface.
 */
class DefaultPowerOperations(private val megaNumber: MegaNumber) : PowerOperations {
    /**
     * Raise a number to the power of an exponent
     * 
     * @param exponent The exponent to raise the number to
     * @return The result of the power operation
     */
    override fun pow(exponent: MegaNumber): MegaNumber {
        return Companion.pow(megaNumber, exponent)
    }

    companion object {
        /**
         * Raise a MegaNumber to the power of an exponent
         * 
         * @param base The base number
         * @param exponent The exponent to raise the base to
         * @return The result of the power operation
         */
        fun pow(base: MegaNumber, exponent: MegaNumber): MegaNumber {
            // Handle negative exponents
            if (exponent.negative) {
                if (!base.isFloat) {
                    throw IllegalArgumentException("Negative exponents not supported for integers")
                }
                throw IllegalArgumentException("Negative exponents not supported yet")
            }

            // Convert exponent to integer for simplicity
            val expInt = chunksToInt(exponent.mantissa)

            // Base cases
            if (expInt == 0) {
                return MegaNumber(
                    mantissa = intArrayOf(1),
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = false,
                    isFloat = base.isFloat
                )
            }

            if (expInt == 1) {
                return MegaNumber(
                    mantissa = base.mantissa.copyOf(),
                    exponent = base.exponent,
                    negative = base.negative,
                    isFloat = base.isFloat
                )
            }

            // Use repeated squaring algorithm
            var result = MegaNumber(
                mantissa = intArrayOf(1),
                exponent = MegaNumber(intArrayOf(0)),
                negative = false,
                isFloat = base.isFloat
            )

            var currentBase = MegaNumber(
                mantissa = base.mantissa.copyOf(),
                exponent = base.exponent,
                negative = base.negative,
                isFloat = base.isFloat
            )

            var exp = expInt

            while (exp > 0) {
                if (exp and 1 == 1) {
                    // If the current bit is set, multiply result by the current power of base
                    result = ArithmeticUtils.multiply(result, currentBase)
                }

                // Square the base
                currentBase = ArithmeticUtils.multiply(currentBase, currentBase)

                // Move to the next bit
                exp = exp shr 1
            }

            return result
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
