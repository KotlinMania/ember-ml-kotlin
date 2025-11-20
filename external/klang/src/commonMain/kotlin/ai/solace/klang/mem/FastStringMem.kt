package ai.solace.klang.mem

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode

/**
 * FastStringMem: High-performance word-at-a-time string operations.
 *
 * Implements optimized string and memory comparison operations using word-level
 * access patterns inspired by musl libc. These implementations process 8 bytes at a
 * time when alignment permits.
 *
 * All bitwise operations use [BitShiftEngine] to ensure correct behavior across
 * all platforms and to avoid Kotlin's type promotion issues.
 */
internal object FastStringMem {
    private const val WORD_BYTES = 8
    private const val WORD_MASK: Int = WORD_BYTES - 1
    private const val BYTE_MASK = 0xFF

    private const val M1: Long = 0x0101010101010101L
    // 0x8080808080808080 as signed Long literal:
    private const val M2: Long = -0x7F7F7F7F7F7F7F80L
    
    // Use 64-bit shifter for word operations
    private val shifter = BitShiftEngine(BitShiftMode.NATIVE, 64)

    private inline fun hasZeroByte(x: Long): Boolean {
        val sub = x - M1
        val notX = shifter.bitwiseNot(x)
        val and1 = shifter.bitwiseAnd(sub, notX)
        val and2 = shifter.bitwiseAnd(and1, M2)
        return and2 != 0L
    }

    private inline fun repeatByte(b: Int): Long {
        return shifter.repeatByteToWord(b)
    }

    private inline fun loadWord(addr: Int): Long {
        val bytes = LongArray(WORD_BYTES) { i ->
            shifter.bitwiseAnd(GlobalHeap.lbu(addr + i).toLong(), 0xFFL)
        }
        return shifter.composeBytes(bytes)
    }

    fun strlen(addr: Int): Int {
        var p = addr
        // Align to word
        while (shifter.bitwiseAnd(p.toLong(), WORD_MASK.toLong()) != 0L) {
            if (GlobalHeap.lbu(p) == 0) return p - addr
            p++
        }
        // Scan by words
        while (true) {
            val w = loadWord(p)
            if (hasZeroByte(w)) break
            p += WORD_BYTES
        }
        // Finish byte-wise
        while (GlobalHeap.lbu(p) != 0) p++
        return p - addr
    }

    fun memchr(addr: Int, c: Int, n: Int): Int {
        if (n <= 0) return 0
        var p = addr
        var rem = n
        val cMasked = shifter.bitwiseAnd(c.toLong(), BYTE_MASK.toLong()).toInt()
        val cword = repeatByte(cMasked)

        // Align
        while (rem > 0 && shifter.bitwiseAnd(p.toLong(), WORD_MASK.toLong()) != 0L) {
            if (GlobalHeap.lbu(p) == cMasked) return p
            p++
            rem--
        }
        // Words
        while (rem >= WORD_BYTES) {
            val w = loadWord(p)
            val x = shifter.bitwiseXor(w, cword)
            if (hasZeroByte(x)) {
                // Locate exact byte
                var i = 0
                while (i < WORD_BYTES) {
                    if (GlobalHeap.lbu(p + i) == cMasked) return p + i
                    i++
                }
            }
            p += WORD_BYTES
            rem -= WORD_BYTES
        }
        // Tail
        while (rem > 0) {
            if (GlobalHeap.lbu(p) == cMasked) return p
            p++
            rem--
        }
        return 0
    }

    fun memcmp(a: Int, b: Int, n: Int): Int {
        if (n <= 0) return 0
        var pa = a
        var pb = b
        var rem = n
        // Align
        while (rem > 0 && shifter.bitwiseAnd(shifter.bitwiseOr(pa.toLong(), pb.toLong()), WORD_MASK.toLong()) != 0L) {
            val da = GlobalHeap.lbu(pa)
            val db = GlobalHeap.lbu(pb)
            if (da != db) return da - db
            pa++
            pb++
            rem--
        }
        // Words
        while (rem >= WORD_BYTES) {
            val wa = loadWord(pa)
            val wb = loadWord(pb)
            if (wa != wb) {
                // Find first differing byte in this word
                var i = 0
                while (i < WORD_BYTES) {
                    val da = GlobalHeap.lbu(pa + i)
                    val db = GlobalHeap.lbu(pb + i)
                    if (da != db) return da - db
                    i++
                }
            }
            pa += WORD_BYTES
            pb += WORD_BYTES
            rem -= WORD_BYTES
        }
        // Tail
        while (rem > 0) {
            val da = GlobalHeap.lbu(pa)
            val db = GlobalHeap.lbu(pb)
            if (da != db) return da - db
            pa++
            pb++
            rem--
        }
        return 0
    }

    fun strcmp(a: Int, b: Int): Int {
        var pa = a
        var pb = b
        // Align
        while (shifter.bitwiseAnd(shifter.bitwiseOr(pa.toLong(), pb.toLong()), WORD_MASK.toLong()) != 0L) {
            val da = GlobalHeap.lbu(pa)
            val db = GlobalHeap.lbu(pb)
            if (da != db || da == 0) return da - db
            pa++
            pb++
        }
        // Words
        while (true) {
            val wa = loadWord(pa)
            val wb = loadWord(pb)
            if (wa != wb) {
                // differ; locate exact byte
                var i = 0
                while (i < WORD_BYTES) {
                    val da = GlobalHeap.lbu(pa + i)
                    val db = GlobalHeap.lbu(pb + i)
                    if (da != db || da == 0) return da - db
                    i++
                }
            }
            if (hasZeroByte(wa)) return 0 // equal up to NUL in this word
            pa += WORD_BYTES
            pb += WORD_BYTES
        }
    }
}
