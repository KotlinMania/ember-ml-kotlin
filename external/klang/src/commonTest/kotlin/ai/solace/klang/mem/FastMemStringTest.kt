package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastMemStringTest {
    @Test
    fun memcpyAndMemmoveOverlap() {
        KMalloc.init(1 shl 18)
        val p = KMalloc.malloc(64)
        // initialize 0..63
        val expected = ByteArray(64) { it.toByte() }
        repeat(64) { GlobalHeap.sb(p + it, expected[it]) }
        // memmove right overlap: dst=8, src=0, n=32
        GlobalHeap.memmove(p + 8, p, 32)
        // compute expected
        run {
            val dst = 8; val src = 0; val n = 32
            if (dst > src && dst < src + n) {
                for (i in n - 1 downTo 0) expected[dst + i] = expected[src + i]
            } else {
                for (i in 0 until n) expected[dst + i] = expected[src + i]
            }
        }
        repeat(64) { assertEquals(expected[it].toInt() and 0xFF, GlobalHeap.lbu(p + it)) }
        // memmove left overlap: dst=0, src=16, n=16
        GlobalHeap.memmove(p, p + 16, 16)
        run {
            val dst = 0; val src = 16; val n = 16
            if (dst > src && dst < src + n) {
                for (i in n - 1 downTo 0) expected[dst + i] = expected[src + i]
            } else {
                for (i in 0 until n) expected[dst + i] = expected[src + i]
            }
        }
        repeat(64) { assertEquals(expected[it].toInt() and 0xFF, GlobalHeap.lbu(p + it)) }
        KMalloc.free(p)
    }

    @Test
    fun strlenStrcmpMemcpy() {
        KMalloc.reset()
        val hello = CString.strdup("hello")
        val world = CString.strdup("world")
        assertEquals(5, CLib.strlen(hello))
        assertTrue(CLib.strcmp(hello, world) < 0)
        val buf = KMalloc.malloc(16)
        GlobalHeap.memcpy(buf, hello, 6) // includes NUL
        assertEquals("hello", CString.read(buf))
        KMalloc.free(hello); KMalloc.free(world); KMalloc.free(buf)
    }

    @Test
    fun strchrMemchrMemcmp() {
        KMalloc.reset()
        val s = CString.strdup("abcabc")
        val p = CLib.strchr(s, 'b'.code)
        assertEquals('b'.code, GlobalHeap.lbu(p))
        val q = CLib.memchr(s, 'c'.code, 6)
        assertEquals('c'.code, GlobalHeap.lbu(q))
        val s2 = CString.strdup("abcabd")
        val cmp = CLib.memcmp(s, s2, 6)
        assertTrue(cmp < 0)
        KMalloc.free(s); KMalloc.free(s2)
    }
}
