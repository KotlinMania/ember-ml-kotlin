package ai.solace.ember.tensor.bitwise.ops

/**
 * Bit manipulation operations for Ember ML Kotlin.
 * 
 * This module provides Kotlin implementations of bit manipulation operations
 * (count_ones, count_zeros, get_bit, set_bit, toggle_bit).
 */

/**
 * Count the number of set bits (1s) in the value (population count).
 *
 * @param x Input value (must be integer type).
 * @return The count of set bits.
 */
fun countOnes(x: Int): Int = x.countOneBits()
fun countOnes(x: Long): Int = x.countOneBits()
fun countOnes(x: UInt): Int = x.countOneBits()
fun countOnes(x: ULong): Int = x.countOneBits()
fun countOnes(x: Byte): Int = x.toInt().countOneBits()
fun countOnes(x: Short): Int = x.toInt().countOneBits()
fun countOnes(x: UByte): Int = x.toUInt().countOneBits()
fun countOnes(x: UShort): Int = x.toUInt().countOneBits()

/**
 * Count the number of unset bits (0s) in the value.
 *
 * @param x Input value (must be integer type).
 * @return The count of unset bits.
 */
fun countZeros(x: Int): Int = 32 - x.countOneBits()
fun countZeros(x: Long): Int = 64 - x.countOneBits()
fun countZeros(x: UInt): Int = 32 - x.countOneBits()
fun countZeros(x: ULong): Int = 64 - x.countOneBits()
fun countZeros(x: Byte): Int = 8 - x.toInt().countOneBits()
fun countZeros(x: Short): Int = 16 - x.toInt().countOneBits()
fun countZeros(x: UByte): Int = 8 - x.toUInt().countOneBits()
fun countZeros(x: UShort): Int = 16 - x.toUInt().countOneBits()

/**
 * Get the bit at the specified position in the value.
 *
 * @param x Input value (must be integer type).
 * @param position Bit position (0-based, LSB).
 * @return The bit value (0 or 1) at the specified position.
 */
fun getBit(x: Int, position: Int): Int = (x shr position) and 1
fun getBit(x: Long, position: Int): Int = ((x shr position) and 1L).toInt()
fun getBit(x: UInt, position: Int): Int = ((x shr position) and 1u).toInt()
fun getBit(x: ULong, position: Int): Int = ((x shr position) and 1uL).toInt()
fun getBit(x: Byte, position: Int): Int = getBit(x.toInt(), position)
fun getBit(x: Short, position: Int): Int = getBit(x.toInt(), position)
fun getBit(x: UByte, position: Int): Int = getBit(x.toUInt(), position)
fun getBit(x: UShort, position: Int): Int = getBit(x.toUInt(), position)

/**
 * Set the bit at the specified position in the value to the given value (0 or 1).
 *
 * @param x Input value (must be integer type).
 * @param position Bit position (0-based, LSB).
 * @param value Bit value (0 or 1).
 * @return The value with the bit at the specified position set.
 */
fun setBit(x: Int, position: Int, value: Int): Int {
    require(value == 0 || value == 1) { "Value must be 0 or 1" }
    val mask = 1 shl position
    return if (value == 1) {
        x or mask
    } else {
        x and mask.inv()
    }
}

fun setBit(x: Long, position: Int, value: Int): Long {
    require(value == 0 || value == 1) { "Value must be 0 or 1" }
    val mask = 1L shl position
    return if (value == 1) {
        x or mask
    } else {
        x and mask.inv()
    }
}

fun setBit(x: UInt, position: Int, value: Int): UInt {
    require(value == 0 || value == 1) { "Value must be 0 or 1" }
    val mask = 1u shl position
    return if (value == 1) {
        x or mask
    } else {
        x and mask.inv()
    }
}

fun setBit(x: ULong, position: Int, value: Int): ULong {
    require(value == 0 || value == 1) { "Value must be 0 or 1" }
    val mask = 1uL shl position
    return if (value == 1) {
        x or mask
    } else {
        x and mask.inv()
    }
}

/**
 * Toggle the bit at the specified position in the value.
 *
 * @param x Input value (must be integer type).
 * @param position Bit position (0-based, LSB).
 * @return The value with the bit at the specified position toggled.
 */
fun toggleBit(x: Int, position: Int): Int = x xor (1 shl position)
fun toggleBit(x: Long, position: Int): Long = x xor (1L shl position)
fun toggleBit(x: UInt, position: Int): UInt = x xor (1u shl position)
fun toggleBit(x: ULong, position: Int): ULong = x xor (1uL shl position)

/**
 * Array operations for counting ones.
 *
 * @param x Input array.
 * @return Array with element-wise count of set bits.
 */
fun countOnes(x: IntArray): IntArray = IntArray(x.size) { i -> countOnes(x[i]) }
fun countOnes(x: LongArray): IntArray = IntArray(x.size) { i -> countOnes(x[i]) }

/**
 * Array operations for counting zeros.
 *
 * @param x Input array.
 * @return Array with element-wise count of unset bits.
 */
fun countZeros(x: IntArray): IntArray = IntArray(x.size) { i -> countZeros(x[i]) }
fun countZeros(x: LongArray): IntArray = IntArray(x.size) { i -> countZeros(x[i]) }

/**
 * Array operations for getting bits.
 *
 * @param x Input array.
 * @param position Bit position or array of positions.
 * @return Array with bit values at specified positions.
 */
fun getBit(x: IntArray, position: Int): IntArray = IntArray(x.size) { i -> getBit(x[i], position) }
fun getBit(x: IntArray, position: IntArray): IntArray {
    require(x.size == position.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> getBit(x[i], position[i]) }
}

fun getBit(x: LongArray, position: Int): IntArray = IntArray(x.size) { i -> getBit(x[i], position) }
fun getBit(x: LongArray, position: IntArray): IntArray {
    require(x.size == position.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> getBit(x[i], position[i]) }
}

/**
 * Array operations for setting bits.
 *
 * @param x Input array.
 * @param position Bit position or array of positions.
 * @param value Bit value or array of values.
 * @return Array with bits set at specified positions.
 */
fun setBit(x: IntArray, position: Int, value: Int): IntArray = IntArray(x.size) { i -> setBit(x[i], position, value) }
fun setBit(x: IntArray, position: IntArray, value: IntArray): IntArray {
    require(x.size == position.size && x.size == value.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> setBit(x[i], position[i], value[i]) }
}

/**
 * Array operations for toggling bits.
 *
 * @param x Input array.
 * @param position Bit position or array of positions.
 * @return Array with bits toggled at specified positions.
 */
fun toggleBit(x: IntArray, position: Int): IntArray = IntArray(x.size) { i -> toggleBit(x[i], position) }
fun toggleBit(x: IntArray, position: IntArray): IntArray {
    require(x.size == position.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> toggleBit(x[i], position[i]) }
}