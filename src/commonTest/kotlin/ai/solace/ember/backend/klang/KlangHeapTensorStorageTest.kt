package ai.solace.ember.backend.klang

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.system.measureTimeMillis

class KlangHeapTensorStorageTest {

    @Test
    fun roundTripFloat32() {
        val data = floatArrayOf(1.0f, -2.5f, 3.75f)
        val buf = KlangHeapTensorStorage.mallocFloat32(data.size)
        try {
            KlangHeapTensorStorage.writeFloat32(buf, data)
            val out = KlangHeapTensorStorage.readFloat32(buf, data.size)
            assertContentEquals(data, out)
        } finally {
            buf.free()
        }
    }

    @Test
    fun roundTripFloat32Bulk() {
        val data = floatArrayOf(0.125f, -2.5f, 99.5f, Float.NaN, Float.POSITIVE_INFINITY)
        val buf = KlangHeapTensorStorage.mallocFloat32(data.size)
        try {
            KlangHeapTensorStorage.writeFloat32Bulk(buf, data)
            val out = KlangHeapTensorStorage.readFloat32Bulk(buf, data.size)
            // Use bits compare because NaN != NaN
            assertContentEquals(data.map { it.toRawBits() }, out.map { it.toRawBits() })
        } finally {
            buf.free()
        }
    }

    @Test
    fun packedRoundTrip() {
        val data = floatArrayOf(0.5f, -1f, Float.NaN, Float.POSITIVE_INFINITY)
        val packed = IntArray(data.size) { data[it].toRawBits() }
        val buf = KlangHeapTensorStorage.mallocFloat32(data.size)
        try {
            KlangHeapTensorStorage.writeFloat32Packed(buf, packed)
            val outPacked = KlangHeapTensorStorage.readFloat32Packed(buf, data.size)
            assertContentEquals(packed.toList(), outPacked.toList())
        } finally {
            buf.free()
        }
    }

    @Test
    fun alignmentIsApplied() {
        val buf = KlangHeapTensorStorage.mallocFloat32(1, alignment = 64)
        try {
            // Pointer should be a multiple of 64
            assertEquals(0, buf.ptr % 64)
        } finally {
            buf.free()
        }
    }

    @Test
    fun heap_vs_array_roundtrip_timing() {
        val data = FloatArray(1024) { it.toFloat() * 0.5f }
        val iterations = 100

        val heapMs = measureTimeMillis {
            val buf = KlangHeapTensorStorage.mallocFloat32(data.size)
            try {
                repeat(iterations) {
                    KlangHeapTensorStorage.writeFloat32(buf, data)
                    KlangHeapTensorStorage.readFloat32(buf, data.size)
                }
            } finally {
                buf.free()
            }
        }

        val heapBulkMs = measureTimeMillis {
            val buf = KlangHeapTensorStorage.mallocFloat32(data.size)
            try {
                repeat(iterations) {
                    KlangHeapTensorStorage.writeFloat32Bulk(buf, data)
                    KlangHeapTensorStorage.readFloat32Bulk(buf, data.size)
                }
            } finally {
                buf.free()
            }
        }

        val arrayMs = measureTimeMillis {
            repeat(iterations) {
                val arr = FloatArray(data.size)
                // copy with CFloat32 conversion to mirror per-element rounding
                for (i in data.indices) arr[i] = ai.solace.klang.fp.CFloat32.fromFloat(data[i]).toFloat()
            }
        }

        println(
            "heap roundtrip ${data.size}x$iterations -> scalar ${heapMs}ms; bulk ${heapBulkMs}ms; array copy ${arrayMs}ms"
        )
    }
}
