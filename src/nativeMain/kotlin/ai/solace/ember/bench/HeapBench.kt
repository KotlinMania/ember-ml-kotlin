package ai.solace.ember.bench

import ai.solace.ember.backend.klang.KlangHeapTensorStorage
import ai.solace.ember.backend.klang.NativeHeapTensorStorage
import ai.solace.klang.fp.CFloat32
import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KAligned
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlin.math.roundToInt
import kotlin.time.measureTime
import kotlin.time.Duration.Companion.milliseconds

private fun benchHeap(size: Int, iters: Int): Long {
    val data = FloatArray(size) { it.toFloat() * 0.5f }
    val buf = KlangHeapTensorStorage.mallocFloat32(size)
    return try {
        measureTime {
            repeat(iters) {
                KlangHeapTensorStorage.writeFloat32(buf, data)
                KlangHeapTensorStorage.readFloat32(buf, size)
            }
        }.inWholeMilliseconds
    } finally {
        buf.free()
    }
}

private fun benchHeapBulk(size: Int, iters: Int): Long {
    val data = FloatArray(size) { it.toFloat() * 0.5f }
    val buf = KlangHeapTensorStorage.mallocFloat32(size)
    return try {
        measureTime {
            repeat(iters) {
                KlangHeapTensorStorage.writeFloat32Bulk(buf, data)
                KlangHeapTensorStorage.readFloat32Bulk(buf, size)
            }
        }.inWholeMilliseconds
    } finally {
        buf.free()
    }
}

private fun benchArray(size: Int, iters: Int): Long {
    val data = FloatArray(size) { it.toFloat() * 0.5f }
    return measureTime {
        repeat(iters) {
            val arr = FloatArray(size)
            for (i in data.indices) arr[i] = CFloat32.fromFloat(data[i]).toFloat()
        }
    }.inWholeMilliseconds
}

private fun benchHeapPacked(size: Int, iters: Int): Long {
    // Pre-pack once; simulate a producer that already has IEEE754 bits.
    val packed = IntArray(size) { (it.toFloat() * 0.5f).toRawBits() }
    val buf = KlangHeapTensorStorage.mallocFloat32(size)
    return try {
        measureTime {
            repeat(iters) {
                KlangHeapTensorStorage.writeFloat32Packed(buf, packed)
                KlangHeapTensorStorage.readFloat32Packed(buf, size)
            }
        }.inWholeMilliseconds
    } finally {
        buf.free()
    }
}

/** In-place math directly on the heap pointer: no host array copies per iteration. */
private fun benchHeapInPlace(size: Int, iters: Int): Pair<Long, Float> {
    val data = FloatArray(size) { it.toFloat() * 0.5f }
    val buf = KlangHeapTensorStorage.mallocFloat32(size)
    return try {
        KlangHeapTensorStorage.writeFloat32Bulk(buf, data) // one-time ingress
        var checksum = 0f
        val time = measureTime {
            repeat(iters) {
                var addr = buf.ptr
                for (i in 0 until size) {
                    val bits = GlobalHeap.lw(addr)
                    // Reinterpret without constructing CFloat32; math in raw bits domain.
                    val v = Float.fromBits(bits)
                    val next = v * 1.0001f + 1.0f // simple math to prevent elision
                    GlobalHeap.sw(addr, next.toRawBits())
                    checksum += next
                    addr += 4
                }
            }
        }.inWholeMilliseconds
        time to checksum
    } finally {
        buf.free()
    }
}

/** In-place math using 16-bit limbs (Short) to probe halfword path cost. */
private fun benchHeapInPlace16(size: Int, iters: Int): Pair<Long, Int> {
    val bytes = size * 2
    val ptr = KAligned.alignedCalloc(32, bytes)
    return try {
        // Seed data
        var addrSeed = ptr
        for (i in 0 until size) {
            GlobalHeap.sh(addrSeed, (i and 0x7FFF).toShort())
            addrSeed += 2
        }
        var checksum = 0
        val time = measureTime {
            repeat(iters) {
                var addr = ptr
                for (i in 0 until size) {
                    val v = GlobalHeap.lh(addr).toInt() and 0xFFFF
                    val next = ((v + 3) and 0xFFFF).toShort()
                    GlobalHeap.sh(addr, next)
                    checksum += next.toInt()
                    addr += 2
                }
            }
        }.inWholeMilliseconds
        time to checksum
    } finally {
        KAligned.alignedFree(ptr)
    }
}

private fun benchArray16(size: Int, iters: Int): Long {
    val data = ShortArray(size) { (it and 0x7FFF).toShort() }
    return measureTime {
        repeat(iters) {
            for (i in data.indices) {
                val next = ((data[i].toInt() and 0xFFFF) + 3).toShort()
                data[i] = next
            }
        }
    }.inWholeMilliseconds
}

@OptIn(ExperimentalForeignApi::class)
private fun benchNativeHeap(size: Int, iters: Int): Long {
    val packed = IntArray(size) { (it.toFloat() * 0.5f).toRawBits() }
    val buf = NativeHeapTensorStorage.mallocFloat32(size)
    return try {
        NativeHeapTensorStorage.writeFloat32Packed(buf, packed)
        measureTime {
            repeat(iters) {
                val ints = buf.ptr.reinterpret<IntVar>()
                for (i in 0 until size) {
                    val v = Float.fromBits(ints[i])
                    val next = v * 1.0001f + 1.0f
                    ints[i] = next.toRawBits()
                }
            }
        }.inWholeMilliseconds
    } finally {
        buf.free()
    }
}

fun heapBenchMain() {
    // Keep runs short to avoid CI timeouts; scale iters with size.
    val cases = listOf(1_024 to 200, 16_384 to 80)
    println("heap vs array roundtrip (Float32 + 16-bit limbs)")
    println("size\titers\tscalar_ms\tbulk_ms\tpacked_ms\tinplace_ms\tnative_ms\tarray_ms\tscalar/arr\tbulk/arr\tpacked/arr\tinplace/arr\tnative/arr\tchecksum_f32\tshort_ms\tshort_array_ms\tshort/arr\tchecksum_s16")
    for ((s, iters) in cases) {
        val heap = benchHeap(s, iters)
        val heapBulk = benchHeapBulk(s, iters)
        val heapPacked = benchHeapPacked(s, iters)
        val (heapInPlace, checksum) = benchHeapInPlace(s, iters)
        val native = benchNativeHeap(s, iters)
        val arr = benchArray(s, iters)
        val ratioScalar = if (arr > 0) (heap.toDouble() / arr * 100).roundToInt() / 100.0 else Double.NaN
        val ratioBulk = if (arr > 0) (heapBulk.toDouble() / arr * 100).roundToInt() / 100.0 else Double.NaN
        val ratioPacked = if (arr > 0) (heapPacked.toDouble() / arr * 100).roundToInt() / 100.0 else Double.NaN
        val ratioInPlace = if (arr > 0) (heapInPlace.toDouble() / arr * 100).roundToInt() / 100.0 else Double.NaN
        val ratioNative = if (arr > 0) (native.toDouble() / arr * 100).roundToInt() / 100.0 else Double.NaN
        val (heapShort, checksumShort) = benchHeapInPlace16(s, iters)
        val arrShort = benchArray16(s, iters)
        val ratioShort = if (arrShort > 0) (heapShort.toDouble() / arrShort * 100).roundToInt() / 100.0 else Double.NaN
        println(
            "$s\t$iters\t$heap\t$heapBulk\t$heapPacked\t$heapInPlace\t$native\t$arr\t$ratioScalar\t$ratioBulk\t$ratioPacked\t$ratioInPlace\t$ratioNative\t$checksum\t$heapShort\t$arrShort\t$ratioShort\t$checksumShort"
        )
    }
}
