package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals

class CLibStrnTests {
    @Test
    fun strnlenAndStrncmp() {
        KMalloc.init(1 shl 18)
        val s = CString.strdup("abcdef")
        assertEquals(3, CLib.strnlen(s, 3))
        assertEquals(6, CLib.strnlen(s, 64))
        val t = CString.strdup("abczzz")
        assertEquals(0, CLib.strncmp(s, t, 3))
        // compare next char: 'd'(100) - 'z'(122) = -22
        assertEquals('d'.code - 'z'.code, CLib.strncmp(s, t, 4))
        KMalloc.free(s); KMalloc.free(t)
    }

    @Test
    fun strncpyPadsWithZeros() {
        KMalloc.reset()
        val dst = KMalloc.malloc(8)
        val src = CString.strdup("hi")
        CLib.strncpy(dst, src, 5)
        // Expect 'h','i','\0','\0','\0'
        val expect = intArrayOf('h'.code, 'i'.code, 0, 0, 0)
        repeat(5) { assertEquals(expect[it], GlobalHeap.lbu(dst + it)) }
        KMalloc.free(dst); KMalloc.free(src)
    }
}

