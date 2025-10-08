package ai.solace.klang.stringshift

import kotlin.math.min

private data class NibbleShift(val out: Char, val carry: Int)

// Build left-shift-by-r (1..3) table for a single hex nibble with incoming r-bit carry
private fun buildLeftNibbleTable(r: Int): Array<NibbleShift> {
    require(r in 1..3)
    val table = Array((1 shl r) * 16) { NibbleShift('0', 0) }
    val maskOut = 0xF
    val carryMask = (1 shl r) - 1
    for (carry in 0..carryMask) {
        for (n in 0..15) {
            val combined = (n shl r) or carry
            val outNibble = combined and maskOut
            val newCarry = (combined ushr 4) and carryMask
            table[carry * 16 + n] = NibbleShift(outNibble.toString(16)[0], newCarry)
        }
    }
    return table
}

// Build right-shift-by-r (1..3) table for a single hex nibble with incoming r-bit carry (from MSB side)
private fun buildRightNibbleTable(r: Int): Array<NibbleShift> {
    require(r in 1..3)
    val table = Array((1 shl r) * 16) { NibbleShift('0', 0) }
    val carryMask = (1 shl r) - 1
    val leftBits = 4 - r
    for (carry in 0..carryMask) {
        for (n in 0..15) {
            val outNibble = ((n ushr r) or ((carry shl leftBits) and 0xF)) and 0xF
            val newCarry = n and carryMask // pass low r bits to next nibble (to the right)
            table[carry * 16 + n] = NibbleShift(outNibble.toString(16)[0], newCarry)
        }
    }
    return table
}

private fun trimLeadingZeros(hex: String): String {
    val h = hex.trimStart('0')
    return if (h.isEmpty()) "0" else h
}

// MSB-first hex string, lowercase, no leading zeros unless zero
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

// Parse MSB-first hex string into little-endian 16-bit limbs
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

// Left shift hex string by s bits (s >= 0)
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

// Right shift hex string by s bits (s >= 0)
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

