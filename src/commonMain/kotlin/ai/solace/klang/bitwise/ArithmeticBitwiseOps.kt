@file:Suppress("unused", "UNUSED_PARAMETER")
package ai.solace.klang.bitwise

import ai.solace.klang.common.ZlibLogger

/**
 * ArithmeticBitwiseOps - Configurable arithmetic-only bitwise operations for cross-platform compatibility
 *
 * This class provides arithmetic-only implementations of bitwise operations that work consistently
 * across all Kotlin platforms, including Kotlin/Native. It's particularly useful for porting
 * 8-bit, 16-bit, or 32-bit programs where bitwise operations need to behave identically
 * regardless of the target platform.
 *
 * The bit length parameter allows proper handling of boundary values and overflow conditions
 * that match the original target architecture.
 *
 * @param bitLength The number of bits for operations (8, 16, or 32)
 */
class ArithmeticBitwiseOps(
    private val bitLength: Int,
) {
    init {
        require(bitLength in 1..32) { "Bit length must be between 1 and 32" }
    }

    // Precompute powers of two up to the configured bit length for reuse
    private val pow2Cache: LongArray = LongArray(bitLength + 1).also { cache ->
        cache[0] = 1L
        for (i in 1..bitLength) {
            cache[i] = cache[i - 1] * 2L
        }
    }

    // Arithmetic-only power-of-two (no bit shifts)
    private fun pow2(n: Int): Long {
        if (n <= 0) return 1L
        if (n <= bitLength) return pow2Cache[n]
        var r = pow2Cache[bitLength]
        repeat(n - bitLength) { r *= 2L }
        return r
    }

    // Computed boundary values based on bit length (arithmetic-only)
    private val maxValue: Long = pow2(bitLength) - 1L
    private val signBit: Long = if (bitLength == 0) 0L else pow2(bitLength - 1)
    private val mask: Long = maxValue

    /**
     * Normalizes a value to fit within the specified bit length
     * @param value The value to normalize
     * @return The value masked to the bit length
     */
    fun normalize(value: Long): Long {
        // Since mask is (2^bitLength - 1), and (value & mask) = value % (mask + 1) for positive values
        // We need to handle negative values specially
        if (value < 0) {
            // For negative values, we need to get the unsigned representation
            val mod = maxValue + 1L
            val remainder = ((value % mod) + mod) % mod
            return remainder
        }
        return value % (maxValue + 1L)
    }

    /**
     * Performs left shift using arithmetic operations
     * @param value The value to shift
     * @param bits Number of bits to shift left
     * @return The shifted value, normalized to bit length
     */
    fun leftShift(
        value: Long,
        bits: Int,
    ): Long {
        if (bits !in 0..<bitLength) {
            ZlibLogger.logBitwise("leftShift($value, $bits) -> 0 (out of range for $bitLength-bit)", "leftShift")
            return 0L
        }
        if (bits == 0) {
            val result = normalize(value)
            ZlibLogger.logBitwise("leftShift($value, $bits) -> $result (no shift, normalized)", "leftShift")
            return result
        }

        val original = normalize(value)
        val scale = pow2(bits)
        val mod = maxValue + 1L
        val result = (original * scale) % mod
        ZlibLogger.logBitwise("leftShift($value, $bits) -> $result [$bitLength-bit arithmetic]", "leftShift")
        return result
    }

    /**
     * Performs unsigned right shift using arithmetic operations
     * @param value The value to shift
     * @param bits Number of bits to shift right
     * @return The shifted value
     */
    fun rightShift(
        value: Long,
        bits: Int,
    ): Long {
        if (bits !in 0..<bitLength) return 0L
        if (bits == 0) return normalize(value)

        val divisor = if (bits <= 0) 1L else pow2(bits)
        return normalize(value) / divisor
    }

    /**
     * Creates a bit mask with the specified number of bits set
     * @param bits Number of bits to set (0 to bitLength)
     * @return A mask with the lowest 'bits' bits set to 1
     */
    fun createMask(bits: Int): Long {
        if (bits < 0) return 0L
        if (bits >= bitLength) return mask
        if (bits == 0) return 0L

        return pow2(bits) - 1L
    }

    /**
     * Check if a number is a power of 2 using only arithmetic operations
     * @param n The number to check
     * @return true if n is a power of 2
     */
    private fun isPowerOfTwo(n: Long): Boolean {
        if (n <= 0) return false
        if (n == 1L) return true

        // Repeatedly divide by 2 and check if we get exactly 1
        var temp = n
        while (temp > 1) {
            if (temp % 2 != 0L) return false
            temp /= 2
        }
        return temp == 1L
    }

    /**
     * Estimates how many bits are worth iterating based on the value magnitude.
     * Keeps loops tight for small inputs while capping at bitLength.
     */
    private fun estimateMaxBitsFor(value: Long): Int {
        val v = normalize(value)
        return if (v < 256) {
            8
        } else if (v < 65536) {
            16
        } else if (v < 16777216) {
            24
        } else {
            bitLength
        }
    }

    /**
     * Extracts the lowest N bits from a value
     * @param value The value to extract bits from
     * @param bits Number of bits to extract
     * @return The value of the lowest 'bits' bits
     */
    fun extractBits(
        value: Long,
        bits: Int,
    ): Long {
        if (bits <= 0) return 0L
        if (bits >= bitLength) return normalize(value)

        val mod = pow2(bits)
        return normalize(value) % mod
    }

    /**
     * Checks if a bit is set at the specified position
     * @param value The value to check
     * @param bitPosition The position of the bit to check (0-based from LSB)
     * @return true if the bit is set, false otherwise
     */
    fun isBitSet(
        value: Long,
        bitPosition: Int,
    ): Boolean {
        if (bitPosition !in 0..<bitLength) return false

        val normalizedValue = normalize(value)
        val powerOf2 = leftShift(1L, bitPosition)
        return (normalizedValue / powerOf2) % 2 == 1L
    }

    /**
     * Performs bitwise OR using arithmetic operations
     * @param value1 First value
     * @param value2 Second value
     * @return The result of value1 OR value2
     */
    fun or(
        value1: Long,
        value2: Long,
    ): Long {
        val norm1 = normalize(value1)
        val norm2 = normalize(value2)

        // Quick optimization for zero operands
        if (norm1 == 0L) return norm2
        if (norm2 == 0L) return norm1

        // Determine how many bits we actually need to check
        val maxVal = if (norm1 > norm2) norm1 else norm2
        val maxBits = estimateMaxBitsFor(maxVal)

        var result = 0L
        var powerOf2 = 1L
        var remaining1 = norm1
        var remaining2 = norm2

        for (i in 0 until maxBits) {
            if (remaining1 == 0L && remaining2 == 0L) break

            val bit1 = remaining1 % 2
            val bit2 = remaining2 % 2

            // OR the bits: 0|0=0, 0|1=1, 1|0=1, 1|1=1
            if (bit1 == 1L || bit2 == 1L) {
                result += powerOf2
            }

            remaining1 /= 2
            remaining2 /= 2
            powerOf2 *= 2
        }

        return result
    }

    /**
     * Performs bitwise AND using arithmetic operations
     * @param value1 First value
     * @param value2 Second value
     * @return The result of value1 AND value2
     */
    fun and(
        value1: Long,
        value2: Long,
    ): Long {
        // Optimize for zero operands
        if (value1 == 0L || value2 == 0L) return 0L

        val norm1 = normalize(value1)
        val norm2 = normalize(value2)

        // Check if value2 is (2^n - 1) - these become simple modulo operations
        // A number is (2^n - 1) if adding 1 gives a power of 2
        val plusOne = norm2 + 1
        if (plusOne > 0 && isPowerOfTwo(plusOne)) {
            // norm2 is 2^n - 1, so AND is equivalent to modulo 2^n
            return norm1 % plusOne
        }

        // Check if value1 is (2^n - 1) and swap for optimization
        val plusOne1 = norm1 + 1
        if (plusOne1 > 0 && isPowerOfTwo(plusOne1)) {
            return norm2 % plusOne1
        }

        // For small second operand, we can terminate early
        val maxBits = estimateMaxBitsFor(norm2)

        var result = 0L
        var powerOf2 = 1L
        var remaining1 = norm1
        var remaining2 = norm2

        ZlibLogger.logBitwise("and($value1, $value2) starting bit-by-bit analysis [max $maxBits bits]", "and")

        for (i in 0 until maxBits) {
            if (remaining1 == 0L || remaining2 == 0L) break

            val bit1 = remaining1 % 2
            val bit2 = remaining2 % 2

            // AND the bits: 0&0=0, 0&1=0, 1&0=0, 1&1=1
            if (bit1 == 1L && bit2 == 1L) {
                result += powerOf2
                ZlibLogger.logBitwise("and: bit position $i: 1&1=1, adding $powerOf2 to result", "and")
            }

            remaining1 /= 2
            remaining2 /= 2
            powerOf2 *= 2
        }

        ZlibLogger.logBitwise("and($value1, $value2) -> $result [binary: ${value1.toString(2)} & ${value2.toString(2)} = ${result.toString(2)}]", "and")
        return result
    }

    /**
     * Performs bitwise XOR using arithmetic operations
     * @param value1 First value
     * @param value2 Second value
     * @return The result of value1 XOR value2
     */
    fun xor(
        value1: Long,
        value2: Long,
    ): Long {
        var result = 0L
        var powerOf2 = 1L
        var remaining1 = normalize(value1)
        var remaining2 = normalize(value2)

        for (i in 0 until bitLength) {
            if (remaining1 == 0L && remaining2 == 0L) break

            val bit1 = remaining1 % 2
            val bit2 = remaining2 % 2

            // XOR the bits: 0^0=0, 0^1=1, 1^0=1, 1^1=0
            if ((bit1 == 1L) != (bit2 == 1L)) {
                result += powerOf2
            }

            remaining1 /= 2
            remaining2 /= 2
            powerOf2 *= 2
        }

        return result
    }

    /**
     * Performs bitwise NOT using arithmetic operations
     * @param value The value to invert
     * @return The result of NOT value (all bits flipped within bit length)
     */
    fun not(value: Long): Long {
        // NOT is equivalent to XOR with all 1s (mask)
        // Which is also equivalent to (mask - value) for normalized values
        val norm = normalize(value)
        return mask - norm
    }

    /**
     * Rotates bits to the left
     * @param value The value to rotate
     * @param positions Number of positions to rotate
     * @return The rotated value
     */
    fun rotateLeft(
        value: Long,
        positions: Int,
    ): Long {
        val normalizedValue = normalize(value)
        val normalizedPositions = positions % bitLength

        if (normalizedPositions == 0) return normalizedValue

        val mod = maxValue + 1L
        val left = (normalizedValue * pow2(normalizedPositions)) % mod
        val right = normalizedValue / pow2(bitLength - normalizedPositions)
        return (left + right) % mod
    }

    /**
     * Rotates bits to the right
     * @param value The value to rotate
     * @param positions Number of positions to rotate
     * @return The rotated value
     */
    fun rotateRight(
        value: Long,
        positions: Int,
    ): Long {
        val normalizedValue = normalize(value)
        val normalizedPositions = positions % bitLength

        if (normalizedPositions == 0) return normalizedValue

        val mod = maxValue + 1L
        val right = normalizedValue / pow2(normalizedPositions)
        val left = (normalizedValue * pow2(bitLength - normalizedPositions)) % mod
        return (left + right) % mod
    }

    /**
     * Converts a signed value to unsigned representation
     * @param value The signed value
     * @return The unsigned representation within bit length
     */
    fun toUnsigned(value: Long): Long = normalize(value)

    /**
     * Converts an unsigned value to signed representation
     * @param value The unsigned value
     * @return The signed representation within bit length
     */
    fun toSigned(value: Long): Long {
        val normalizedValue = normalize(value)
        return if (normalizedValue >= signBit) {
            normalizedValue - (mask + 1)
        } else {
            normalizedValue
        }
    }

    companion object {
        /**
         * Pre-configured instance for 8-bit operations
         */
        val BITS_8 = ArithmeticBitwiseOps(8)

        /**
         * Pre-configured instance for 16-bit operations
         */
        val BITS_16 = ArithmeticBitwiseOps(16)

        /**
         * Pre-configured instance for 32-bit operations
         */
        val BITS_32 = ArithmeticBitwiseOps(32)
    }
}
