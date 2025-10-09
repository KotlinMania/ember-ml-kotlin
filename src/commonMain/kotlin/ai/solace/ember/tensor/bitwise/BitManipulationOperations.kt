package ai.solace.ember.tensor.bitwise

/**
 * Interface for bit manipulation operations
 */
interface BitManipulationOperations {
    companion object {
        /**
         * Shift bits left
         * 
         * @param limbs The array of limbs to shift
         * @param shift The number of bits to shift
         * @return The shifted array
         */
        fun shiftLeft(limbs: IntArray, shift: Int): IntArray = throw NotImplementedError("Implemented by concrete classes")

        /**
         * Shift bits right
         * 
         * @param limbs The array of limbs to shift
         * @param shiftBits The number of bits to shift
         * @return The shifted array
         */
        fun shiftRight(limbs: IntArray, shiftBits: Int): IntArray = throw NotImplementedError("Implemented by concrete classes")

        /**
         * Multiply by a power of 2
         * 
         * @param chunks The array of chunks to multiply
         * @param bits The power of 2 to multiply by
         * @return The result array
         */
        fun multiplyBy2ToThePower(chunks: IntArray, bits: Int): IntArray = throw NotImplementedError("Implemented by concrete classes")

        /**
         * Divide by a power of 2
         * 
         * @param chunks The array of chunks to divide
         * @param bits The power of 2 to divide by
         * @return A pair containing the quotient and remainder
         */
        fun divideBy2ToThePower(chunks: IntArray, bits: Int): Pair<IntArray, IntArray> = throw NotImplementedError("Implemented by concrete classes")
    }
}
