package ai.solace.klang.mem

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode

/**
 * C primitive helpers used by the GlobalHeap model.
 * Pure Kotlin, multiplatform, no interop.
 */

object CPlatform {
    /** Signedness of plain `char`. Default true (common on GCC/Clang for x86_64). */
    var plainCharIsSigned: Boolean = true
}

@Suppress("NOTHING_TO_INLINE")
value class CChar(val raw: Byte) {
    inline fun toIntSigned(): Int = raw.toInt()
    inline fun toIntUnsigned(): Int {
        val shifter = BitShiftEngine(BitShiftMode.NATIVE, 8)
        return shifter.bitwiseAnd(raw.toLong(), 0xFF).toInt()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun ccharOf(value: Int): CChar = CChar(value.toByte())

@Suppress("NOTHING_TO_INLINE")
inline fun asPlainChar(value: Int): CChar = CChar(value.toByte())

@Suppress("NOTHING_TO_INLINE")
inline fun plainCharToInt(c: CChar): Int = if (CPlatform.plainCharIsSigned) c.toIntSigned() else c.toIntUnsigned()

