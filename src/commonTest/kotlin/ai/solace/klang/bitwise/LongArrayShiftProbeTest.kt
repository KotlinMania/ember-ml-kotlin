package ai.solace.klang.bitwise

import kotlin.test.Test

/**
 * LongArrayShiftProbeTest: explores native Kotlin/Native LongArray bit shifts across limbs.
 * Prints exact 64-bit patterns before/after multi-limb shifts to compare with a C probe.
 */
class LongArrayShiftProbeTest {

    private fun Long.toHex64(): String = this.toULong().toString(16).padStart(16, '0')
    private fun Long.toBin64(): String = buildString(64) {
        val u = this@toBin64.toULong()
        for (i in 63 downTo 0) {
            val bit = ((u shr i) and 1uL) == 1uL
            append(if (bit) '1' else '0')
            if (i % 8 == 0 && i != 0) append('_')
        }
    }

    private fun dump(label: String, a: LongArray) {
        println("$label HEX: [" + a.joinToString(" ") { it.toHex64() } + "]")
        println("$label BIN:")
        a.forEachIndexed { idx, v -> println(" [$idx] ${v.toBin64()}") }
    }

    // Multi-limb left shift with carry from lower -> higher limb
    private fun shlCarry(a: LongArray, s: Int): LongArray {
        require(s in 1..63)
        val out = LongArray(a.size)
        for (i in a.indices) {
            val lower = if (i > 0) a[i - 1] else 0L
            val carryIn = (lower.toULong() shr (64 - s)).toLong()
            out[i] = (a[i] shl s) or carryIn
        }
        return out
    }

    // Multi-limb unsigned right shift with carry from higher -> lower limb
    private fun ushrCarry(a: LongArray, s: Int): LongArray {
        require(s in 1..63)
        val out = LongArray(a.size)
        for (i in a.indices.reversed()) {
            val higher = if (i + 1 < a.size) a[i + 1] else 0L
            val carryIn = (higher.toULong() shl (64 - s)).toLong()
            out[i] = (a[i].toULong() shr s).toLong() or carryIn
        }
        return out
    }

    // Multi-limb arithmetic right shift (sign-propagating) with carry from higher -> lower limb
    private fun shrCarry(a: LongArray, s: Int): LongArray {
        require(s in 1..63)
        val out = LongArray(a.size)
        for (i in a.indices.reversed()) {
            val higher = if (i + 1 < a.size) a[i + 1] else if (a.last() < 0) -1L else 0L
            val carryIn = (higher shl (64 - s))
            out[i] = (a[i] shr s) or carryIn
        }
        return out
    }

    @Test
    fun longArrayShiftProbe() {
        val a = longArrayOf(
            0x0123_4567_89AB_CDEFL,
            0x0000_0000_0000_0000L,
            Long.MIN_VALUE,
        )

        println("=== LongArray Shift Probe ===")
        dump("orig", a)

        for (s in listOf(1, 13, 31, 32, 47, 63)) {
            // Left shift with carry across limbs
            val l = shlCarry(a, s)
            dump("shl+$s", l)

            // Unsigned right shift with carry across limbs
            val ur = ushrCarry(a, s)
            dump("ushr+$s", ur)

            // Arithmetic right shift with carry across limbs
            val ar = shrCarry(a, s)
            dump("shr+$s", ar)
        }

        // Negative patterns to test sign behavior
        val b = longArrayOf(
            -1L,
            -2L,
            Long.MAX_VALUE,
        )
        dump("orig_neg", b)
        val bshr = shrCarry(b, 17)
        dump("shr+17_neg", bshr)
        val bushr = ushrCarry(b, 17)
        dump("ushr+17_neg", bushr)
    }
}
