package ai.solace.klang.stringshift

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftConfig
import kotlin.math.min

/**
 * HexShift: Bit-level manipulation of arbitrary-precision integers via hexadecimal strings.
 *
 * Provides efficient nibble-based (4-bit) shifting operations on hexadecimal representations
 * of large integers. This approach avoids the overhead of full BigInteger implementations
 * when only basic shift operations are needed, making it particularly useful for
 * cryptographic operations, bit manipulation in wide integers, and format conversions.
 *
 * ## Key Features
 *
 * - **String-based representation**: Works directly on hex strings, avoiding memory allocation churn
 * - **Limb conversion**: Converts between hex strings and 16-bit limb arrays (little-endian)
 * - **Arbitrary precision**: No fixed width limits, handles integers of any size
 * - **Table-driven shifts**: Uses precomputed lookup tables for efficient nibble-level operations
 *
 * ## Use Cases
 *
 * - Implementing wide integer types (128-bit, 256-bit, etc.)
 * - Cryptographic computations requiring precise bit manipulation
 * - Format conversions between different integer representations
 * - Testing and verification of multi-precision arithmetic
 *
 * @see leftShiftHexString
 * @see rightShiftHexString
 * @see limbsToHex
 * @see hexToLimbs
 */

/**
 * Represents a single hex nibble shift result with carry propagation.
 *
 * @property out The output hex character after shifting.
 * @property carry The carry bits to propagate to the next nibble.
 */
private data class NibbleShift(val out: Char, val carry: Int)

/**
 * Builds a lookup table for left-shifting hex nibbles by [r] bits (1-3).
 *
 * The table is indexed by `carry * 16 + nibble` where:
 * - `nibble` is the input hex digit (0-15)
 * - `carry` is the incoming carry from the previous (less significant) nibble
 *
 * @param r The number of bits to shift left (must be 1, 2, or 3).
 * @return A lookup table mapping (carry, nibble) → (output_char, new_carry).
 */
private fun buildLeftNibbleTable(r: Int): Array<NibbleShift> {
    require(r in 1..3)
    val engine = BitShiftEngine(BitShiftConfig.defaultMode, 32)
    val table = Array(engine.leftShift(1L, r).value.toInt() * 16) { NibbleShift('0', 0) }
    val maskOut = 0xF
    val carryMask = engine.leftShift(1L, r).value.toInt() - 1
    for (carry in 0..carryMask) {
        for (n in 0..15) {
            val combined = (engine.leftShift(n.toLong(), r).value.toInt() or carry)
            val outNibble = combined and maskOut
            val newCarry = (engine.unsignedRightShift(combined.toLong(), 4).value.toInt() and carryMask)
            table[carry * 16 + n] = NibbleShift(outNibble.toString(16)[0], newCarry)
        }
    }
    return table
}

/**
 * Builds a lookup table for right-shifting hex nibbles by [r] bits (1-3).
 *
 * The table is indexed by `carry * 16 + nibble` where:
 * - `nibble` is the input hex digit (0-15)
 * - `carry` is the incoming carry from the previous (more significant) nibble
 *
 * @param r The number of bits to shift right (must be 1, 2, or 3).
 * @return A lookup table mapping (carry, nibble) → (output_char, new_carry).
 */
private fun buildRightNibbleTable(r: Int): Array<NibbleShift> {
    require(r in 1..3)
    val engine = BitShiftEngine(BitShiftConfig.defaultMode, 32)
    val table = Array(engine.leftShift(1L, r).value.toInt() * 16) { NibbleShift('0', 0) }
    val carryMask = engine.leftShift(1L, r).value.toInt() - 1
    val leftBits = 4 - r
    for (carry in 0..carryMask) {
        for (n in 0..15) {
            val outNibble = ((engine.unsignedRightShift(n.toLong(), r).value.toInt() or 
                            ((engine.leftShift(carry.toLong(), leftBits).value.toInt() and 0xF))) and 0xF)
            val newCarry = n and carryMask // pass low r bits to next nibble (to the right)
            table[carry * 16 + n] = NibbleShift(outNibble.toString(16)[0], newCarry)
        }
    }
    return table
}

/**
 * Removes leading zeros from a hex string, preserving "0" for zero values.
 *
 * @param hex The hex string to trim.
 * @return The hex string without leading zeros, or "0" if all zeros.
 */
private fun trimLeadingZeros(hex: String): String {
    val h = hex.trimStart('0')
    return if (h.isEmpty()) "0" else h
}

/**
 * Converts an array of 16-bit limbs (little-endian) to a hexadecimal string.
 *
 * Limbs are stored in little-endian order (least significant limb at index 0),
 * but the output hex string is MSB-first (most significant digits first).
 *
 * @param limbs The limb array to convert (16-bit values, little-endian).
 * @return A lowercase hex string with no leading zeros (except "0" for zero).
 */
fun limbsToHex(limbs: IntArray): String {
    if (limbs.isEmpty()) return "0"
    val sb = StringBuilder(limbs.size * 4)
    for (i in limbs.indices.reversed()) {
        val v = limbs[i] and 0xFFFF
        val chunk = v.toString(16).padStart(4, '0')
        sb.append(chunk)
    }
    return trimLeadingZeros(sb.toString().lowercase())
}

/**
 * Parses a hexadecimal string into an array of 16-bit limbs (little-endian).
 *
 * The input hex string is MSB-first, but the output limbs are stored in
 * little-endian order (least significant limb at index 0).
 *
 * @param hexIn The hex string to parse (may have "0x" prefix, case-insensitive).
 * @return An array of 16-bit limbs representing the value.
 */
fun hexToLimbs(hexIn: String): IntArray {
    var hex = hexIn.lowercase().trim()
    if (hex.startsWith("0x")) hex = hex.substring(2)
    hex = trimLeadingZeros(hex)
    if (hex == "0") return intArrayOf(0)
    val limbCount = (hex.length + 3) / 4
    val limbs = IntArray(limbCount)
    var idx = hex.length
    var li = 0
    while (idx > 0) {
        val start = (idx - 4).coerceAtLeast(0)
        val slice = hex.substring(start, idx)
        limbs[li++] = slice.toInt(16)
        idx = start
    }
    return limbs
}

/**
 * Left-shifts a hexadecimal string by [s] bits.
 *
 * Performs a logical left shift on the integer represented by the hex string.
 * This is equivalent to multiplying by 2^s. The operation handles arbitrary
 * precision and preserves leading zeros only when the result is zero.
 *
 * ## Algorithm
 *
 * 1. Extract full nibble shifts (s / 4) by appending zeros
 * 2. Handle remaining bit shifts (s % 4) using nibble lookup tables
 * 3. Propagate carries from LSB to MSB
 *
 * @param hexIn The input hex string (case-insensitive, may have "0x" prefix).
 * @param s The number of bits to shift left (must be non-negative).
 * @return The shifted hex string (lowercase, no leading zeros except "0").
 */
fun leftShiftHexString(hexIn: String, s: Int): String {
    require(s >= 0)
    val hex0 = trimLeadingZeros(hexIn.lowercase())
    if (hex0 == "0" || s == 0) return hex0
    val q = s / 4
    val r = s % 4
    val base = if (q > 0) hex0 + "0".repeat(q) else hex0
    if (r == 0) return base

    val table = buildLeftNibbleTable(r)
    val sb = StringBuilder(base.length + 1)
    var carry = 0
    // process from LSB nibble (rightmost) to MSB
    for (i in base.lastIndex downTo 0) {
        val nibble = base[i].digitToInt(16)
        val entry = table[carry * 16 + nibble]
        sb.append(entry.out)
        carry = entry.carry
    }
    if (carry != 0) sb.append(carry.toString(16))
    return trimLeadingZeros(sb.reverse().toString())
}

/**
 * Right-shifts a hexadecimal string by [s] bits.
 *
 * Performs a logical right shift on the integer represented by the hex string.
 * This is equivalent to integer division by 2^s (floor division for positive numbers).
 * The operation handles arbitrary precision.
 *
 * ## Algorithm
 *
 * 1. Remove full nibbles (s / 4) by truncating from the right
 * 2. Handle remaining bit shifts (s % 4) using nibble lookup tables
 * 3. Propagate carries from MSB to LSB
 *
 * @param hexIn The input hex string (case-insensitive, may have "0x" prefix).
 * @param s The number of bits to shift right (must be non-negative).
 * @return The shifted hex string (lowercase, no leading zeros except "0").
 */
fun rightShiftHexString(hexIn: String, s: Int): String {
    require(s >= 0)
    val hex0 = trimLeadingZeros(hexIn.lowercase())
    if (hex0 == "0" || s == 0) return hex0
    val q = s / 4
    val r = s % 4
    if (hex0.length <= q) return "0"
    val base = if (q > 0) hex0.substring(0, hex0.length - q) else hex0
    if (r == 0) return trimLeadingZeros(base)

    val table = buildRightNibbleTable(r)
    val sb = StringBuilder(base.length)
    var carry = 0
    // process from MSB nibble (leftmost) to LSB
    for (i in 0 until base.length) {
        val nibble = base[i].digitToInt(16)
        val entry = table[carry * 16 + nibble]
        sb.append(entry.out)
        carry = entry.carry
    }
    return trimLeadingZeros(sb.toString())
}

