package ai.solace.ember.tensor.bitwise

/**
 * Interface for operations on chunks (the internal representation of numbers)
 */
interface ChunkOperations {
    companion object {
        /**
         * Add two chunk arrays
         * 
         * @param a First chunk array
         * @param b Second chunk array
         * @return The sum as a chunk array
         */
        fun addChunks(a: IntArray, b: IntArray): IntArray = throw NotImplementedError("Implemented by concrete classes")

        /**
         * Subtract one chunk array from another
         * 
         * @param a First chunk array
         * @param b Second chunk array
         * @param preserveSize Whether to preserve the size of the result
         * @return The difference as a chunk array
         */
        fun subChunks(a: IntArray, b: IntArray, preserveSize: Boolean = true): IntArray = throw NotImplementedError("Implemented by concrete classes")

        /**
         * Multiply two chunk arrays
         * 
         * @param a First chunk array
         * @param b Second chunk array
         * @return The product as a chunk array
         */
        fun mulChunks(a: IntArray, b: IntArray): IntArray = throw NotImplementedError("Implemented by concrete classes")

        /**
         * Compare the absolute values of two chunk arrays
         * 
         * @param a First chunk array
         * @param b Second chunk array
         * @return Negative if a < b, positive if a > b, zero if a == b
         */
        fun compareAbs(a: IntArray, b: IntArray): Int = throw NotImplementedError("Implemented by concrete classes")
    }
}
