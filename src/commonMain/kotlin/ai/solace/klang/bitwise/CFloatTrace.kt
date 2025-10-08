package ai.solace.klang.bitwise

import kotlin.concurrent.Volatile

/**
 * Diagnostic tracer for CFloat32 operations. When enabled, every arithmetic
 * operation records the before/after bit patterns so we can inspect rounding
 * behaviour step-by-step.
 */
object CFloatTrace {
    @Volatile
    var enabled: Boolean = false
        private set

    private val entries = mutableListOf<Entry>()

    data class Entry(
        val op: String,
        val lhsBits: Int,
        val rhsBits: Int?,
        val resultBits: Int
    ) {
        val lhs: Float get() = Float.fromBits(lhsBits)
        val rhs: Float? get() = rhsBits?.let { Float.fromBits(it) }
        val result: Float get() = Float.fromBits(resultBits)
    }

    fun start() {
        enabled = true
        entries.clear()
    }

    fun stop(): List<Entry> {
        enabled = false
        return entries.toList()
    }

    inline fun <T> withTracing(block: () -> T): Pair<T, List<Entry>> {
        start()
        return try {
            val result = block()
            result to stop()
        } catch (t: Throwable) {
            stop()
            throw t
        }
    }

    fun reset() {
        entries.clear()
    }

    fun log(op: String, lhs: Int, rhs: Int?, result: Int) {
        if (!enabled) return
        entries += Entry(op, lhs, rhs, result)
        println("TRACE $op lhs=${Float.fromBits(lhs)} rhs=${rhs?.let { Float.fromBits(it) }} result=${Float.fromBits(result)}")
    }
}
