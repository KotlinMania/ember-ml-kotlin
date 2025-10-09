package ai.solace.ember.tensor.bitwise.ops

/**
 * Shift bitwise operations for Ember ML Kotlin.
 * 
 * This module provides Kotlin implementations of bitwise shift operations
 * (left_shift, right_shift, rotate_left, rotate_right).
 */

/**
 * Shift the bits of x to the left by shifts positions.
 *
 * @param x Input value.
 * @param shifts Number of bits to shift.
 * @return x shifted left by shifts bits.
 */
fun leftShift(x: Int, shifts: Int): Int = x shl shifts
fun leftShift(x: Long, shifts: Int): Long = x shl shifts
fun leftShift(x: UInt, shifts: Int): UInt = x shl shifts
fun leftShift(x: ULong, shifts: Int): ULong = x shl shifts

/**
 * Shift the bits of x to the right by shifts positions.
 *
 * @param x Input value.
 * @param shifts Number of bits to shift.
 * @return x shifted right by shifts bits.
 */
fun rightShift(x: Int, shifts: Int): Int = x shr shifts
fun rightShift(x: Long, shifts: Int): Long = x shr shifts
fun rightShift(x: UInt, shifts: Int): UInt = x shr shifts
fun rightShift(x: ULong, shifts: Int): ULong = x shr shifts

/**
 * Rotate the bits of x to the left by shifts positions.
 *
 * @param x Input value (must be unsigned for proper rotation logic).
 * @param shifts Number of bits to rotate.
 * @param bitWidth The bit width of the integer type (8, 16, 32, 64).
 * @return x rotated left by shifts bits.
 */
fun rotateLeft(x: UInt, shifts: Int, bitWidth: Int = 32): UInt {
    val normalizedShifts = shifts % bitWidth
    return (x shl normalizedShifts) or (x shr (bitWidth - normalizedShifts))
}

fun rotateLeft(x: ULong, shifts: Int, bitWidth: Int = 64): ULong {
    val normalizedShifts = shifts % bitWidth
    return (x shl normalizedShifts) or (x shr (bitWidth - normalizedShifts))
}

fun rotateLeft(x: UByte, shifts: Int, bitWidth: Int = 8): UByte {
    val normalizedShifts = shifts % bitWidth
    return ((x.toUInt() shl normalizedShifts) or (x.toUInt() shr (bitWidth - normalizedShifts))).toUByte()
}

fun rotateLeft(x: UShort, shifts: Int, bitWidth: Int = 16): UShort {
    val normalizedShifts = shifts % bitWidth
    return ((x.toUInt() shl normalizedShifts) or (x.toUInt() shr (bitWidth - normalizedShifts))).toUShort()
}

/**
 * Rotate the bits of x to the right by shifts positions.
 *
 * @param x Input value (must be unsigned for proper rotation logic).
 * @param shifts Number of bits to rotate.
 * @param bitWidth The bit width of the integer type (8, 16, 32, 64).
 * @return x rotated right by shifts bits.
 */
fun rotateRight(x: UInt, shifts: Int, bitWidth: Int = 32): UInt {
    val normalizedShifts = shifts % bitWidth
    return (x shr normalizedShifts) or (x shl (bitWidth - normalizedShifts))
}

fun rotateRight(x: ULong, shifts: Int, bitWidth: Int = 64): ULong {
    val normalizedShifts = shifts % bitWidth
    return (x shr normalizedShifts) or (x shl (bitWidth - normalizedShifts))
}

fun rotateRight(x: UByte, shifts: Int, bitWidth: Int = 8): UByte {
    val normalizedShifts = shifts % bitWidth
    return ((x.toUInt() shr normalizedShifts) or (x.toUInt() shl (bitWidth - normalizedShifts))).toUByte()
}

fun rotateRight(x: UShort, shifts: Int, bitWidth: Int = 16): UShort {
    val normalizedShifts = shifts % bitWidth
    return ((x.toUInt() shr normalizedShifts) or (x.toUInt() shl (bitWidth - normalizedShifts))).toUShort()
}

/**
 * Array operations for left shift.
 *
 * @param x Input array.
 * @param shifts Array of shift amounts or single shift amount.
 * @return Array with element-wise left shift results.
 */
fun leftShift(x: IntArray, shifts: Int): IntArray = IntArray(x.size) { i -> x[i] shl shifts }
fun leftShift(x: IntArray, shifts: IntArray): IntArray {
    require(x.size == shifts.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> x[i] shl shifts[i] }
}

fun leftShift(x: LongArray, shifts: Int): LongArray = LongArray(x.size) { i -> x[i] shl shifts }
fun leftShift(x: LongArray, shifts: IntArray): LongArray {
    require(x.size == shifts.size) { "Arrays must have the same size" }
    return LongArray(x.size) { i -> x[i] shl shifts[i] }
}

/**
 * Array operations for right shift.
 *
 * @param x Input array.
 * @param shifts Array of shift amounts or single shift amount.
 * @return Array with element-wise right shift results.
 */
fun rightShift(x: IntArray, shifts: Int): IntArray = IntArray(x.size) { i -> x[i] shr shifts }
fun rightShift(x: IntArray, shifts: IntArray): IntArray {
    require(x.size == shifts.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> x[i] shr shifts[i] }
}

fun rightShift(x: LongArray, shifts: Int): LongArray = LongArray(x.size) { i -> x[i] shr shifts }
fun rightShift(x: LongArray, shifts: IntArray): LongArray {
    require(x.size == shifts.size) { "Arrays must have the same size" }
    return LongArray(x.size) { i -> x[i] shr shifts[i] }
}