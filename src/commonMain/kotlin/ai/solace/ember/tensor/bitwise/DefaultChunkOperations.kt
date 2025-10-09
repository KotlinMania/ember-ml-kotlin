package ai.solace.ember.tensor.bitwise

/**
 * Default implementation of ChunkOperations interface.
 * This class provides implementations for the methods defined in the ChunkOperations interface.
 */
object DefaultChunkOperations : ChunkOperations {
    /**
     * Add two chunk arrays
     * 
     * @param a First chunk array
     * @param b Second chunk array
     * @return The sum as a chunk array
     */
    fun addChunks(a: IntArray, b: IntArray): IntArray {
        val maxLen = maxOf(a.size, b.size)
        val out = IntArray(maxLen + 1)
        var carry = 0L

        for (i in 0 until maxLen) {
            val av = if (i < a.size) a[i].toLong() and MegaNumberConstants.MASK else 0L
            val bv = if (i < b.size) b[i].toLong() and MegaNumberConstants.MASK else 0L
            val s = av + bv + carry
            out[i] = (s and MegaNumberConstants.MASK).toInt()
            carry = s ushr MegaNumberConstants.GLOBAL_CHUNK_SIZE
        }

        if (carry != 0L) {
            out[maxLen] = carry.toInt()
            return out
        }

        // Trim trailing zeros
        var lastNonZero = out.size - 1
        while (lastNonZero > 0 && out[lastNonZero] == 0) {
            lastNonZero--
        }

        return if (lastNonZero == out.size - 1) out else out.copyOf(lastNonZero + 1)
    }

    /**
     * Subtract one chunk array from another
     * 
     * @param a First chunk array
     * @param b Second chunk array
     * @param preserveSize Whether to preserve the size of the result
     * @return The difference as a chunk array
     */
    fun subChunks(a: IntArray, b: IntArray, preserveSize: Boolean = true): IntArray {
        val maxLen = maxOf(a.size, b.size)
        val result = IntArray(maxLen)
        var borrow = 0L

        for (i in 0 until maxLen) {
            val av = if (i < a.size) a[i].toLong() and MegaNumberConstants.MASK else 0L
            val bv = if (i < b.size) b[i].toLong() and MegaNumberConstants.MASK else 0L
            val diff = av - bv - borrow
            result[i] = (diff and MegaNumberConstants.MASK).toInt()
            borrow = if (diff < 0) 1 else 0
        }

        if (borrow != 0L) {
            throw ArithmeticException("Negative result in subtraction")
        }

        if (!preserveSize) {
            // Trim trailing zeros
            var lastNonZero = result.size - 1
            while (lastNonZero > 0 && result[lastNonZero] == 0) {
                lastNonZero--
            }
            return if (lastNonZero < result.size - 1) result.copyOf(lastNonZero + 1) else result
        }

        return result
    }

    /**
     * Multiply two chunk arrays
     * 
     * @param a First chunk array
     * @param b Second chunk array
     * @return The product as a chunk array
     */
    fun mulChunks(a: IntArray, b: IntArray): IntArray {
        // Simple implementation for now - can be optimized later
        val result = IntArray(a.size + b.size)
        
        for (i in a.indices) {
            var carry = 0L
            for (j in b.indices) {
                val product = (a[i].toLong() and MegaNumberConstants.MASK) * (b[j].toLong() and MegaNumberConstants.MASK) + 
                              (result[i + j].toLong() and MegaNumberConstants.MASK) + carry
                result[i + j] = (product and MegaNumberConstants.MASK).toInt()
                carry = product ushr MegaNumberConstants.GLOBAL_CHUNK_SIZE
            }
            if (carry != 0L) {
                result[i + b.size] = (result[i + b.size] + carry).toInt()
            }
        }
        
        // Trim trailing zeros
        var lastNonZero = result.size - 1
        while (lastNonZero > 0 && result[lastNonZero] == 0) {
            lastNonZero--
        }
        
        return if (lastNonZero < result.size - 1) result.copyOf(lastNonZero + 1) else result
    }

    /**
     * Compare the absolute values of two chunk arrays
     * 
     * @param a First chunk array
     * @param b Second chunk array
     * @return Negative if a < b, positive if a > b, zero if a == b
     */
    fun compareAbs(a: IntArray, b: IntArray): Int {
        // Compare lengths first
        if (a.size != b.size) {
            return a.size - b.size
        }
        
        // Compare chunks from most significant to least
        for (i in a.indices.reversed()) {
            val aUnsigned = a[i].toLong() and MegaNumberConstants.MASK
            val bUnsigned = b[i].toLong() and MegaNumberConstants.MASK
            if (aUnsigned != bUnsigned) {
                return if (aUnsigned < bUnsigned) -1 else 1
            }
        }
        
        // Equal
        return 0
    }
}