package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals

class ArrayBitShiftsWordShiftTest {
    private fun manualWordPlusBitShift(src: IntArray, words: Int, s: Int): IntArray {
        val n = src.size
        val out = IntArray(n + words + 1)
        // word shift
        for (i in n - 1 downTo 0) out[i + words] = src[i] and 0xFFFF
        // bit shift with carry limited to s bits
        if (s == 0) return trim(out)
        val mask = 0xFFFF
        val carryMask = (1 shl s) - 1
        var carry = 0
        for (i in 0 until out.size - 1) {
            val cur = out[i] and mask
            val combined = (cur shl s) + carry
            out[i] = combined and mask
            carry = (combined ushr 16) and carryMask
        }
        out[out.lastIndex] = carry
        return trim(out)
    }

    private fun trim(a: IntArray): IntArray {
        var last = a.size - 1
        while (last > 0 && a[last] == 0) last--
        return a.copyOf(last + 1)
    }

    @Test
    fun shlWordsThenBitsMatchesManual() {
        val a = IntArray(8) { it * 3 and 0xFFFF }
        val b = a.copyOf()
        val words = 2
        val s = 7
        ArrayBitShifts.shl16LEWordsInPlace(a, 0, a.size, words)
        ArrayBitShifts.shl16LEInPlace(a, 0, a.size, s, 0)
        val exp = manualWordPlusBitShift(b, words, s)
        val expTrunc = if (exp.size > a.size) exp.copyOf(a.size) else exp
        assertEquals(expTrunc.toList(), a.toList())
    }

    private fun trimWithCarry(a: IntArray, carry: Int): IntArray {
        val out = if (carry != 0) a.copyOf(a.size + 1) else a.copyOf()
        if (carry != 0) out[out.lastIndex] = carry and 0xFFFF
        var last = out.size - 1
        while (last > 0 && out[last] == 0) last--
        return out.copyOf(last + 1)
    }
}
