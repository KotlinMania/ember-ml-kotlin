package ai.solace.ember.backend.klang

import ai.solace.klang.fp.CFloat32
import ai.solace.klang.mem.CIntVar
import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KAligned
import kotlin.math.max

/**
 * Minimal storage helper that allocates a contiguous, C‑aligned buffer for Float32
 * tensors using KLang's heap utilities. This is meant to prove the path for C‑layout
 * interop; it does not cover other dtypes yet.
 */
object KlangHeapTensorStorage {

    data class Buffer(val ptr: Int, val sizeBytes: Int) {
        fun free() = KAligned.alignedFree(ptr)
    }

    /**
     * Allocate an aligned buffer for `count` float32 elements (4 bytes each).
     * Alignment defaults to 32 bytes to be safe for GPU/CPU SIMD loads.
     */
    fun mallocFloat32(count: Int, alignment: Int = 32, arenaPtr: Int? = null): Buffer {
        require(count >= 0) { "count must be non-negative" }
        val bytes = max(count * 4, 1)
        val ptr = arenaPtr ?: KAligned.alignedCalloc(alignment, bytes)
        return Buffer(ptr, bytes)
    }

    /**
     * Store Kotlin floats into the buffer as CFloat32 bit patterns.
     */
    fun writeFloat32(buffer: Buffer, data: FloatArray) {
        require(data.size * 4 <= buffer.sizeBytes) { "buffer too small" }
        var offset = buffer.ptr
        data.forEach { f ->
            val bits = CFloat32.fromFloat(f).toBits()
            CIntVar(offset).value = bits
            offset += 4
        }
    }

    /**
     * Read back into a Kotlin FloatArray.
     */
    fun readFloat32(buffer: Buffer, count: Int): FloatArray {
        require(count * 4 <= buffer.sizeBytes) { "buffer too small" }
        val out = FloatArray(count)
        var offset = buffer.ptr
        for (i in 0 until count) {
            val bits = CIntVar(offset).value
            out[i] = Float.fromBits(bits)
            offset += 4
        }
        return out
    }

    /**
     * Bulk copy variant that avoids per-element heap writes by packing to an IntArray
     * and using KLang's `memcpy`‑style helpers. This is much faster for large tensors.
     */
    fun writeFloat32Bulk(buffer: Buffer, data: FloatArray) {
        require(data.size * 4 <= buffer.sizeBytes) { "buffer too small" }
        val packed = IntArray(data.size) { idx -> CFloat32.fromFloat(data[idx]).toBits() }
        GlobalHeap.copyFromIntArray(buffer.ptr, packed, packed.size)
    }

    /**
     * Bulk read variant matching [writeFloat32Bulk].
     */
    fun readFloat32Bulk(buffer: Buffer, count: Int): FloatArray {
        require(count * 4 <= buffer.sizeBytes) { "buffer too small" }
        val ints = IntArray(count)
        GlobalHeap.copyToIntArray(buffer.ptr, ints, count)
        return FloatArray(count) { i -> Float.fromBits(ints[i]) }
    }

    /**
     * Fast path when callers already have packed IEEE754 bits (zero-copy of math domain).
     * Uses 32-bit word copy with no Float -> CFloat32 conversion inside the loop.
     */
    fun writeFloat32Packed(buffer: Buffer, packedBits: IntArray) {
        require(packedBits.size * 4 <= buffer.sizeBytes) { "buffer too small" }
        GlobalHeap.copyFromIntArray(buffer.ptr, packedBits, packedBits.size)
    }

    /**
     * Read raw bits into an IntArray without decoding to Float.
     */
    fun readFloat32Packed(buffer: Buffer, count: Int): IntArray {
        require(count * 4 <= buffer.sizeBytes) { "buffer too small" }
        val ints = IntArray(count)
        GlobalHeap.copyToIntArray(buffer.ptr, ints, count)
        return ints
    }
}
