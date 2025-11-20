package ai.solace.ember.backend.klang

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.value
import kotlin.math.max

/**
 * Native-only fast path that uses Kotlin/Native's nativeHeap for zero-copy, word-sized access.
 * This avoids ByteArray bounds checks and the KLang virtual heap when running on K/N targets.
 *
 * API mirrors KlangHeapTensorStorage for easy swapping in benchmarks.
 */
@OptIn(ExperimentalForeignApi::class)
object NativeHeapTensorStorage {

    data class Buffer(val ptr: CPointer<ByteVar>, val sizeBytes: Int) {
        fun free() = nativeHeap.free(ptr.rawValue)
    }

    fun mallocFloat32(count: Int, alignment: Int = 32): Buffer {
        require(count >= 0)
        val bytes = max(1, count * 4)
        // nativeHeap.allocArray is already word-aligned; alignment parameter kept for symmetry.
        val ptr = nativeHeap.allocArray<ByteVar>(bytes)
        return Buffer(ptr, bytes)
    }

    fun writeFloat32(buffer: Buffer, data: FloatArray) {
        require(data.size * 4 <= buffer.sizeBytes)
        val ints = buffer.ptr.reinterpret<IntVar>()
        for (i in data.indices) ints[i] = data[i].toRawBits()
    }

    fun writeFloat32Packed(buffer: Buffer, packedBits: IntArray) {
        require(packedBits.size * 4 <= buffer.sizeBytes)
        val ints = buffer.ptr.reinterpret<IntVar>()
        for (i in packedBits.indices) ints[i] = packedBits[i]
    }

    fun readFloat32(buffer: Buffer, count: Int): FloatArray {
        require(count * 4 <= buffer.sizeBytes)
        val out = FloatArray(count)
        val ints = buffer.ptr.reinterpret<IntVar>()
        for (i in 0 until count) out[i] = Float.fromBits(ints[i])
        return out
    }

    fun readFloat32Packed(buffer: Buffer, count: Int): IntArray {
        require(count * 4 <= buffer.sizeBytes)
        val ints = buffer.ptr.reinterpret<IntVar>()
        return IntArray(count) { i -> ints[i] }
    }
}
