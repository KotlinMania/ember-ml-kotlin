@file:Suppress("unused", "UNUSED_PARAMETER", "ktlint:standard:indent")

package ai.solace.klang.bitwise

/**
 * BitwiseOps - A library for efficient bitwise operations in Kotlin Multiplatform
 * Extracted from ZLib.kotlin implementation patterns
 *
 * This library provides a set of utility functions for common bitwise operations,
 * with special attention to cross-platform compatibility. Some operations, like
 * URShift (unsigned right shift), have different behaviors on different platforms,
 * and this library aims to provide consistent behavior across all platforms.
 *
 * The URShift operation is particularly important for porting code from other languages
 * like C# or Java, where the behavior of unsigned right shift for negative numbers
 * differs from Kotlin's native `ushr` operator.
 *
 * This class now integrates with BitShiftEngine for configurable operation modes.
 * 
 * @native-bitshift-allowed This is a core BitShift implementation file.
 * Native bitwise operations (shl, shr, ushr, and, or) are permitted here
 * as this file provides the foundation for the BitShift engine.
 */
object BitwiseOps {
        // Default engines use global mode
        private val defaultEngine32 get() = BitShiftEngine(BitShiftConfig.defaultMode, 32)

        @Suppress("unused")
        private val defaultEngine16 get() = BitShiftEngine(BitShiftConfig.defaultMode, 16)

        @Suppress("unused")
        private val defaultEngine8 get() = BitShiftEngine(BitShiftConfig.defaultMode, 8)

        private val defaultEngine64 get() = BitShiftEngine(BitShiftConfig.defaultMode, 64)

        /**
         * Creates a bit mask with the specified number of bits set to 1
         * @param bits Number of bits to set (0-32)
         * @return An integer with the lowest 'bits' bits set to 1
         */
        fun createMask(bits: Int): Int {
            require(bits in 0..32) { "Bits must be between 0 and 32" }
            return createMaskArithmetic(bits)
        }

        /**
         * Extracts the lowest N bits from a value
         * @param value The value to extract bits from
         * @param bits Number of bits to extract
         * @return The value of the lowest 'bits' bits
         */
        fun extractBits(
            value: Int,
            bits: Int,
        ): Int = extractBitsArithmetic(value, bits)

        /**
         * Extracts a range of bits from a value
         * @param value The value to extract bits from
         * @param startBit The starting bit position (0-based, from LSB)
         * @param bitCount Number of bits to extract
         * @return The extracted bits as an integer
         */
        fun extractBitRange(
            value: Int,
            startBit: Int,
            bitCount: Int,
        ): Int {
            if (bitCount <= 0) return 0
            if (startBit !in 0..<32) return 0
            val take = minOf(bitCount, 32 - startBit)
            val a32 = ArithmeticBitwiseOps.BITS_32
            val shifted = a32.rightShift(value.toLong(), startBit).toInt()
            return extractBitsArithmetic(shifted, take)
        }

        /**
         * Combines two 16-bit values into a 32-bit value
         * @param high The high 16 bits
         * @param low The low 16 bits
         * @return A 32-bit integer combining both values
         */
        fun combine16Bit(
            high: Int,
            low: Int,
        ): Int {
            val a32 = ArithmeticBitwiseOps.BITS_32
            val hi = a32.leftShift(high.toLong(), 16).toInt()
            val lo = ((low % 65536) + 65536) % 65536
            return a32.or(hi.toLong(), lo.toLong()).toInt()
        }

        /**
         * Combines two 16-bit values into a 32-bit long value
         * @param high The high 16 bits
         * @param low The low 16 bits
         * @return A 32-bit long combining both values
         */
        fun combine16BitToLong(
            high: Long,
            low: Long,
        ): Long {
            val a32 = ArithmeticBitwiseOps.BITS_32
            val hi = a32.leftShift(high, 16)
            val lo = ((low % 65536) + 65536) % 65536
            return a32.or(hi, lo)
        }

        /**
         * Extracts the high 16 bits from a 32-bit value
         * @param value The 32-bit value
         * @return The high 16 bits as an integer
         */
        fun getHigh16Bits(value: Int): Int {
            val a32 = ArithmeticBitwiseOps.BITS_32
            return a32.rightShift(value.toLong(), 16).toInt()
        }

        /**
         * Extracts the low 16 bits from a 32-bit value
         * @param value The 32-bit value
         * @return The low 16 bits as an integer
         */
        fun getLow16Bits(value: Int): Int {
            // Arithmetic-only extraction of lower 16 bits
            return ((value % 65536) + 65536) % 65536
        }

        /**
         * Converts a signed byte to an unsigned integer (0-255) using arithmetic-only operations
         * @param b The byte to convert
         * @return An integer in the range 0-255
         */
        fun byteToUnsignedInt(b: Byte): Int {
            var unsigned = b.toInt()
            if (unsigned < 0) unsigned += 256
            return unsigned
        }

        /**
         * Extracts the high 16 bits from a 32-bit value using arithmetic operations
         * @param value The 32-bit value
         * @return The high 16 bits as an integer (0-65535)
         */
        fun getHigh16BitsArithmetic(value: Long): Int = ((value / 65536) % 65536 + 65536).toInt() % 65536

        /**
         * Extracts the low 16 bits from a 32-bit value using arithmetic operations
         * @param value The 32-bit value
         * @return The low 16 bits as an integer (0-65535)
         */
        fun getLow16BitsArithmetic(value: Long): Int = ((value % 65536) + 65536).toInt() % 65536

        /**
         * Combines two 16-bit values into a 32-bit value using arithmetic operations
         * @param high The high 16 bits (0-65535)
         * @param low The low 16 bits (0-65535)
         * @return A 32-bit value combining both
         */
        fun combine16BitArithmetic(
            high: Int,
            low: Int,
        ): Long = (high.toLong() * 65536) + low.toLong()

        /**
         * Performs left shift using arithmetic operations (multiplication by powers of 2)
         * @param value The value to shift
         * @param bits Number of bits to shift left (0-31)
         * @return The shifted value
         */
        fun leftShiftArithmetic(
            value: Int,
            bits: Int,
        ): Int {
            // Pure arithmetic: multiplication by 2^bits within 32-bit width
            val a32 = ArithmeticBitwiseOps.BITS_32
            return a32.leftShift(value.toLong(), bits).toInt()
        }

        /**
         * Performs right shift using arithmetic operations (division by powers of 2)
         * @param value The value to shift
         * @param bits Number of bits to shift right (0-31)
         * @return The shifted value
         */
        fun rightShiftArithmetic(
            value: Int,
            bits: Int,
        ): Int {
            // Pure arithmetic: floor-division by 2^bits within 32-bit width
            val a32 = ArithmeticBitwiseOps.BITS_32
            return a32.rightShift(value.toLong(), bits).toInt()
        }

        /**
         * Creates a bit mask using arithmetic operations
         * @param bits Number of bits to set (0-32)
         * @return An integer with the lowest 'bits' bits set to 1
         */
        fun createMaskArithmetic(bits: Int): Int {
            require(bits in 0..32) { "Bits must be between 0 and 32" }
            if (bits == 0) return 0
            if (bits == 32) return -1

            // Calculate 2^bits - 1 using repeated multiplication
            var result = 1
            repeat(bits) { result *= 2 }
            return result - 1
        }

        /**
         * Extracts the lowest N bits from a value using arithmetic operations
         * @param value The value to extract bits from
         * @param bits Number of bits to extract
         * @return The value of the lowest 'bits' bits
         */
        fun extractBitsArithmetic(
            value: Int,
            bits: Int,
        ): Int {
            if (bits <= 0) return 0
            if (bits >= 32) return value
            val mask = createMaskArithmetic(bits)
            return value % (mask + 1)
        }

        /**
         * Checks if a bit is set using arithmetic operations
         * @param value The value to check
         * @param bitPosition The position of the bit to check (0-based from LSB)
         * @return true if the bit is set, false otherwise
         */
        fun isBitSetArithmetic(
            value: Int,
            bitPosition: Int,
        ): Boolean {
            if (bitPosition !in 0..<32) return false
            val powerOf2 = leftShiftArithmetic(1, bitPosition)
            return (value / powerOf2) % 2 == 1
        }

        /**
         * Performs bitwise OR using arithmetic operations for combining non-overlapping bit fields
         * This only works correctly when the two values don't have overlapping bits set
         * @param value1 First value
         * @param value2 Second value (must be non-overlapping with value1)
         * @return The combined value
         */
        fun orArithmetic(
            value1: Int,
            value2: Int,
        ): Int = value1 + value2

        /**
         * Performs bitwise OR using arithmetic operations that handles overlapping bits correctly
         * @param value1 First value
         * @param value2 Second value
         * @return The combined value (value1 OR value2)
         */
        fun orArithmeticGeneral(
            value1: Int,
            value2: Int,
        ): Int {
            var result = 0
            var powerOf2 = 1
            var remaining1 = value1
            var remaining2 = value2

            // Process each bit position
            var count = 0
            while (count < 32) {
                if (remaining1 == 0 && remaining2 == 0) break

                val bit1 = remaining1 % 2
                val bit2 = remaining2 % 2

                // OR the bits: 0|0=0, 0|1=1, 1|0=1, 1|1=1
                if (bit1 == 1 || bit2 == 1) {
                    result += powerOf2
                }

                remaining1 /= 2
                remaining2 /= 2
                powerOf2 *= 2
                count++
            }

            return result
        }

        /**
         * Performs a bitwise rotation to the left
         * @param value The value to rotate
         * @param bits Number of bits to rotate by
         * @return The rotated value
         */
        fun rotateLeft(
            value: Int,
            bits: Int,
        ): Int {
            val a32 = ArithmeticBitwiseOps.BITS_32
            val n = bits and 31
            if (n == 0) return a32.normalize(value.toLong()).toInt()
            val left = a32.leftShift(value.toLong(), n).toInt()
            val right = a32.rightShift(value.toLong(), 32 - n).toInt()
            return a32.or(left.toLong(), right.toLong()).toInt()
        }

        /**
         * Performs a bitwise rotation to the right
         * @param value The value to rotate
         * @param bits Number of bits to rotate by
         * @return The rotated value
         */
        fun rotateRight(
            value: Int,
            bits: Int,
        ): Int {
            val a32 = ArithmeticBitwiseOps.BITS_32
            val n = bits and 31
            if (n == 0) return a32.normalize(value.toLong()).toInt()
            val right = a32.rightShift(value.toLong(), n).toInt()
            val left = a32.leftShift(value.toLong(), 32 - n).toInt()
            return a32.or(left.toLong(), right.toLong()).toInt()
        }

        /**
         * Factory function to get a configured BitwiseOps instance that uses arithmetic operations
         */
        fun withArithmeticEngine(): ArithmeticBitwiseOps = ArithmeticBitwiseOps.BITS_32

        /**
         * Improved unsigned right shift using BitShiftEngine for consistency
         * @param number The number to shift
         * @param bits The number of bits to shift
         * @param engine The engine to use (defaults to native 32-bit)
         * @return The result of the unsigned right shift operation
         */
        fun urShiftImproved(
            number: Int,
            bits: Int,
            engine: BitShiftEngine = defaultEngine32,
        ): Int = engine.unsignedRightShift(number.toLong(), bits).value.toInt()

        /**
         * Improved unsigned right shift using BitShiftEngine for consistency
         * @param number The number to shift
         * @param bits The number of bits to shift
         * @param engine The engine to use (defaults to native 32-bit)
         * @return The result of the unsigned right shift operation
         */
        fun urShiftImproved(
            number: Long,
            bits: Int,
            engine: BitShiftEngine = defaultEngine64,
        ): Long = engine.unsignedRightShift(number, bits).value
}
