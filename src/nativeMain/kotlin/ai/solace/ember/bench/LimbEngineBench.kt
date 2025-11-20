package ai.solace.ember.bench

import ai.solace.ember.backend.klang.LimbArray
import ai.solace.ember.backend.klang.LimbEngine
import ai.solace.klang.bitwise.BitShiftConfig
import ai.solace.klang.bitwise.BitShiftMode
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.measureTime

private const val LIMB_BITS = 16
private const val MASK = (1 shl LIMB_BITS) - 1

private fun randomEngines(count: Int, seed: Int): List<LimbEngine> {
    val rnd = Random(seed)
    return List(count) {
        val limbs = IntArray(8) { rnd.nextInt(0x10000) }
        LimbEngine(mantissa = limbs)
    }
}

private inline fun measureMillis(times: Int, block: () -> Unit): Double {
    val total = measureTime {
        repeat(times) { block() }
    }
    return total.inWholeMilliseconds.toDouble() / times
}

private fun runSuite(size: Int, bits: Int, iters: Int, seed: Int) {
    fun bench(mode: BitShiftMode, dag: Boolean): Double {
        val engines = randomEngines(size, seed xor (if (dag) 0xA5A5 else 0x5A5A))
        var ms = 0.0
        BitShiftConfig.withMode(mode) {
            ms = measureMillis(iters) {
                val arr = LimbArray.fromList(engines, autoFlushThreshold = Int.MAX_VALUE, dagEnabled = dag)
                arr.shiftLeft(bits).bitAnd(IntArray(8) { MASK }).shiftRight(bits / 2).flush()
            }
        }
        return ms
    }

    val arDag = bench(BitShiftMode.ARITHMETIC, dag = true)
    val arEager = bench(BitShiftMode.ARITHMETIC, dag = false)
    val natDag = bench(BitShiftMode.NATIVE, dag = true)
    val natEager = bench(BitShiftMode.NATIVE, dag = false)

    fun fmt(x: Double): String {
        if (x.isNaN()) return "NaN"
        val scaled = kotlin.math.round(x * 100.0) / 100.0
        val s = scaled.toString()
        return if ("." in s) {
            val idx = s.indexOf('.')
            val pad = 3 - (s.length - idx)
            if (pad > 0) s + "0".repeat(pad) else s
        } else s + ".00"
    }

    val arRatio = if (arEager > 0.0) arDag / arEager else Double.NaN
    val natRatio = if (natEager > 0.0) natDag / natEager else Double.NaN
    println(
        buildString {
            append("size=")
            append(size.toString().padStart(6))
            append(" bits=")
            append(bits.toString().padStart(3))
            append("  AR(dag)=")
            append(fmt(arDag))
            append(" ms  AR(eager)=")
            append(fmt(arEager))
            append(" ms  ratio=")
            append(fmt(arRatio))
            append("  NAT(dag)=")
            append(fmt(natDag))
            append(" ms  NAT(eager)=")
            append(fmt(natEager))
            append(" ms  ratio=")
            append(fmt(natRatio))
        },
    )
}

fun main() {
    val sizes = listOf(512, 2048, 8192)
    val bitsList = listOf(5, 31, 63, 127)
    val iters = 3
    val seed = 0xC001D00D.toInt()

    println("LimbEngine/LimbArray shift+mask microbench (DAG vs Eager; AR and Native)")
    println("columns: size | bits | AR(dag) | AR(eager) | ratio | NAT(dag) | NAT(eager) | ratio")
    println("------------------------------------------------------------")
    for (size in sizes) {
        for (bits in bitsList) {
            runSuite(size, bits, iters, seed xor bits)
        }
        println()
    }
}
