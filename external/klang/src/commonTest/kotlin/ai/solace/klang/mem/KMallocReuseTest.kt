package ai.solace.klang.mem

import kotlin.test.Test
import kotlin.test.assertEquals

class KMallocReuseTest {
    @Test
    fun freeThenAllocReusesChunk() {
        KMalloc.init(1 shl 18)
        val a = KMalloc.malloc(128)
        val b = KMalloc.malloc(128)
        KMalloc.free(b)
        val c = KMalloc.malloc(128)
        assertEquals(b, c, "allocator should reuse recently freed chunk of same size class")
        KMalloc.free(a); KMalloc.free(c)
    }
}

