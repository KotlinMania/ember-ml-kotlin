package ai.solace.ember.tensor.bitwise.ops

/**
 * Basic bitwise operations for Ember ML Kotlin.
 * 
 * This module provides Kotlin implementations of basic bitwise operations
 * (AND, OR, XOR, NOT) compatible with the Ember ML tensor system.
 */

/**
 * Compute the bitwise AND of x and y element-wise.
 *
 * @param x First input value (Int, Long, or compatible type).
 * @param y Second input value (Int, Long, or compatible type).
 * @return The element-wise bitwise AND result.
 */
fun bitwiseAnd(x: Int, y: Int): Int = x and y
fun bitwiseAnd(x: Long, y: Long): Long = x and y
fun bitwiseAnd(x: UInt, y: UInt): UInt = x and y
fun bitwiseAnd(x: ULong, y: ULong): ULong = x and y

/**
 * Compute the bitwise OR of x and y element-wise.
 *
 * @param x First input value (Int, Long, or compatible type).
 * @param y Second input value (Int, Long, or compatible type).
 * @return The element-wise bitwise OR result.
 */
fun bitwiseOr(x: Int, y: Int): Int = x or y
fun bitwiseOr(x: Long, y: Long): Long = x or y
fun bitwiseOr(x: UInt, y: UInt): UInt = x or y
fun bitwiseOr(x: ULong, y: ULong): ULong = x or y

/**
 * Compute the bitwise XOR of x and y element-wise.
 *
 * @param x First input value (Int, Long, or compatible type).
 * @param y Second input value (Int, Long, or compatible type).
 * @return The element-wise bitwise XOR result.
 */
fun bitwiseXor(x: Int, y: Int): Int = x xor y
fun bitwiseXor(x: Long, y: Long): Long = x xor y
fun bitwiseXor(x: UInt, y: UInt): UInt = x xor y
fun bitwiseXor(x: ULong, y: ULong): ULong = x xor y

/**
 * Compute the bitwise NOT (complement) of x.
 *
 * @param x Input value (Int, Long, or compatible type).
 * @return The bitwise NOT result.
 */
fun bitwiseNot(x: Int): Int = x.inv()
fun bitwiseNot(x: Long): Long = x.inv()
fun bitwiseNot(x: UInt): UInt = x.inv()
fun bitwiseNot(x: ULong): ULong = x.inv()

/**
 * Array operations for bitwise AND.
 *
 * @param x First input array.
 * @param y Second input array.
 * @return Array with element-wise bitwise AND results.
 */
fun bitwiseAnd(x: IntArray, y: IntArray): IntArray {
    require(x.size == y.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> x[i] and y[i] }
}

fun bitwiseAnd(x: LongArray, y: LongArray): LongArray {
    require(x.size == y.size) { "Arrays must have the same size" }
    return LongArray(x.size) { i -> x[i] and y[i] }
}

/**
 * Array operations for bitwise OR.
 *
 * @param x First input array.
 * @param y Second input array.
 * @return Array with element-wise bitwise OR results.
 */
fun bitwiseOr(x: IntArray, y: IntArray): IntArray {
    require(x.size == y.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> x[i] or y[i] }
}

fun bitwiseOr(x: LongArray, y: LongArray): LongArray {
    require(x.size == y.size) { "Arrays must have the same size" }
    return LongArray(x.size) { i -> x[i] or y[i] }
}

/**
 * Array operations for bitwise XOR.
 *
 * @param x First input array.
 * @param y Second input array.
 * @return Array with element-wise bitwise XOR results.
 */
fun bitwiseXor(x: IntArray, y: IntArray): IntArray {
    require(x.size == y.size) { "Arrays must have the same size" }
    return IntArray(x.size) { i -> x[i] xor y[i] }
}

fun bitwiseXor(x: LongArray, y: LongArray): LongArray {
    require(x.size == y.size) { "Arrays must have the same size" }
    return LongArray(x.size) { i -> x[i] xor y[i] }
}

/**
 * Array operations for bitwise NOT.
 *
 * @param x Input array.
 * @return Array with element-wise bitwise NOT results.
 */
fun bitwiseNot(x: IntArray): IntArray = IntArray(x.size) { i -> x[i].inv() }
fun bitwiseNot(x: LongArray): LongArray = LongArray(x.size) { i -> x[i].inv() }