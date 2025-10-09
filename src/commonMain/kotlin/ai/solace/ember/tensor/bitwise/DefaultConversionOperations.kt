package ai.solace.ember.tensor.bitwise

/**
 * Default implementation of ConversionOperations interface.
 * This class provides implementations for the methods defined in the ConversionOperations interface.
 */
class DefaultConversionOperations(private val megaNumber: MegaNumber) : ConversionOperations {
    /**
     * Convert to a decimal string representation
     * 
     * @return The decimal string representation
     */
    override fun toDecimalString(): String {
        return Companion.toDecimalString(megaNumber)
    }

    companion object {
        /**
         * Convert to a decimal string representation
         * 
         * @param megaNumber The MegaNumber to convert
         * @return The decimal string representation
         */
        fun toDecimalString(megaNumber: MegaNumber): String {
            // If zero
            if (megaNumber.mantissa.size == 1 && megaNumber.mantissa[0] == 0) {
                return "0"
            }

            // If exponent is zero or we are integer => treat as integer
            val expNonZero = !(megaNumber.exponent.mantissa.size == 1 && megaNumber.exponent.mantissa[0] == 0)
            if (!expNonZero) {
                // purely integer
                val s = chunkToDecimal(megaNumber.mantissa)
                return (if (megaNumber.negative) "-" else "") + s
            } else {
                // float => represent as "mantissa * 2^(exponent * chunkBits)" for simplicity
                val eVal = if (megaNumber.exponent.negative) {
                    -chunksToInt(megaNumber.exponent.mantissa)
                } else {
                    chunksToInt(megaNumber.exponent.mantissa)
                }
                val mantString = chunkToDecimal(megaNumber.mantissa)
                val signStr = if (megaNumber.negative) "-" else ""
                // This is a simplistic representation.
                return "$signStr$mantString * 2^($eVal * ${MegaNumberConstants.GLOBAL_CHUNK_SIZE})"
            }
        }

        /**
         * Convert chunk-limbs to decimal string
         */
        fun chunkToDecimal(limbs: IntArray): String {
            // quick check for zero
            if (limbs.size == 1 && limbs[0] == 0) {
                return "0"
            }
            var temp = limbs.copyOf()
            val digits = mutableListOf<Char>()
            while (!(temp.size == 1 && temp[0] == 0)) {
                val (q, r) = divMod10(temp)
                temp = q
                digits.add('0' + r)
            }
            return digits.reversed().joinToString("")
        }

        /**
         * Divmod by 10 (decimal conversion helper).
         */
        private fun divMod10(a: IntArray): Pair<IntArray, Int> {
            var remainder = 0
            val out = IntArray(a.size)

            for (i in a.indices.reversed()) {
                // Shift the remainder left by GLOBAL_CHUNK_SIZE bits and add the current limb
                val cur = (remainder shl MegaNumberConstants.GLOBAL_CHUNK_SIZE) + a[i]

                // Compute the quotient digit and the new remainder (dividing by 10)
                val qd = cur / 10
                remainder = cur % 10

                // Assign the quotient digit to the output array, ensuring it fits within the chunk mask
                out[i] = qd and MegaNumberConstants.MASK.toInt()
            }

            // Trim any unnecessary trailing zeros from the output array
            var lastNonZero = out.size - 1
            while (lastNonZero > 0 && out[lastNonZero] == 0) {
                lastNonZero--
            }

            return Pair(out.copyOf(lastNonZero + 1), remainder)
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

        /**
         * Convert from a decimal string
         * 
         * @param s The decimal string
         * @return The resulting number
         */
        fun fromDecimalString(s: String): MegaNumber {
            // Basic parse
            var negative = false
            var raw = s.trim()
            if (raw.startsWith("-")) {
                negative = true
                raw = raw.substring(1).trim()
            }
            if (raw.isEmpty()) return MegaNumber()

            // Check float or int
            val parts = raw.split(".")
            if (parts.size == 1) {
                // Integer
                val mant = decimalStringToChunks(parts[0])
                return MegaNumber(
                    mantissa = mant,
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = negative,
                    isFloat = false
                )
            } else {
                // Float
                val intPart = parts[0]
                val fracPart = parts[1]

                // Combine them as integer => do repeated multiply/add
                val fullNumStr = intPart + fracPart
                val mant = decimalStringToChunks(fullNumStr)
                // Approximate exponent using length of fraction => treat fraction as 2^some shift
                // E.g., log2(10) * fracLen
                val fracLen = fracPart.length
                val shiftBits = kotlin.math.ceil(fracLen * kotlin.math.log2(10.0)).toInt()
                val expChunks = intArrayOf(shiftBits)

                return MegaNumber(
                    mantissa = mant,
                    exponent = MegaNumber(expChunks, negative = true),
                    negative = negative,
                    isFloat = true
                )
            }
        }

        /**
         * Convert decimal string => chunk-limb array
         */
        private fun decimalStringToChunks(dec: String): IntArray {
            if (dec.isEmpty()) return intArrayOf(0)
            if (dec == "0") return intArrayOf(0)

            var limbs = intArrayOf(0)
            for (ch in dec) {
                if (ch < '0' || ch > '9') {
                    throw IllegalArgumentException("Invalid decimal digit in $dec")
                }
                val digit : Int = (ch - '0')
                // Multiply limbs by 10, then add digit
                limbs = DefaultChunkOperations.addChunks(DefaultChunkOperations.mulChunks(limbs, intArrayOf(10)), intArrayOf(digit))
            }
            return limbs
        }
    }
}
