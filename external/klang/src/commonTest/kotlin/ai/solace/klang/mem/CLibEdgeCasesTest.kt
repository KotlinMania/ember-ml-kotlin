package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals

class CLibEdgeCasesTest {
    @Test
    fun zeroLengthOpsAndOverlap() {
        KMalloc.init(1 shl 18)
        val p = KMalloc.malloc(16)
        repeat(16) { GlobalHeap.sb(p + it, it.toByte()) }
        // zero-length memmove/memcpy should not change
        GlobalHeap.memmove(p + 4, p, 0)
        GlobalHeap.memcpy(p + 4, p, 0)
        repeat(16) { assertEquals(it, GlobalHeap.lbu(p + it)) }
        // strncpy with n smaller than src length copies n and no NUL
        val dst = KMalloc.malloc(4)
        val src = CString.strdup("abcd")
        CLib.strncpy(dst, src, 3)
        assertEquals('a'.code, GlobalHeap.lbu(dst + 0))
        assertEquals('b'.code, GlobalHeap.lbu(dst + 1))
        assertEquals('c'.code, GlobalHeap.lbu(dst + 2))
        KMalloc.free(p); KMalloc.free(dst); KMalloc.free(src)
    }
}

