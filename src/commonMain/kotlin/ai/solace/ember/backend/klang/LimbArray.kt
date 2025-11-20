package ai.solace.ember.backend.klang

/**
 * Batch wrapper over multiple [LimbEngine] instances that keeps a lazy queue of operations.
 *
 * Each operation is recorded and fanned out once [flush] is invoked (explicitly or when the
 * auto-flush threshold is reached). This mirrors the scalar lazy behaviour and makes it easy
 * to compose shift/mask pipelines that execute in one pass across all limbs in the array.
 */
class LimbArray private constructor(
    private val items: MutableList<LimbEngine>,
    private val autoFlushThreshold: Int,
    private var dagEnabled: Boolean,
    private val autoGate: Boolean,
    private val trace: Boolean,
) {
    private sealed interface Operation {
        data class Shift(val bits: Int) : Operation
        data class And(val mask: IntArray) : Operation
        data class Or(val mask: IntArray) : Operation
        data class Xor(val mask: IntArray) : Operation
        data class Add(val rhs: LimbEngine) : Operation
        data class Sub(val rhs: LimbEngine) : Operation
        data class Mul(val rhs: LimbEngine) : Operation
        data class Div(val rhs: LimbEngine) : Operation
    }

    private val queue = mutableListOf<Operation>()

    val size: Int
        get() = items.size

    operator fun get(index: Int): LimbEngine {
        if (dagEnabled) {
            // Read barrier: materialize queued ops before exposing an element
            flush()
        }
        return items[index]
    }

    fun copy(): LimbArray = LimbArray(
        items = items.map { it.copy().apply { this.dagEnabled = this@LimbArray.dagEnabled } }.toMutableList(),
        autoFlushThreshold = autoFlushThreshold,
        dagEnabled = this.dagEnabled,
        autoGate = this.autoGate,
        trace = this.trace,
    )

    fun shift(bits: Int): LimbArray = applyOperation(Operation.Shift(bits))

    fun shiftLeft(bits: Int): LimbArray = shift(bits)

    fun shiftRight(bits: Int): LimbArray = shift(-bits)

    fun bitAnd(mask: IntArray): LimbArray = applyOperation(Operation.And(mask.copyOf()))

    fun bitOr(mask: IntArray): LimbArray = applyOperation(Operation.Or(mask.copyOf()))

    fun bitXor(mask: IntArray): LimbArray = applyOperation(Operation.Xor(mask.copyOf()))

    fun add(rhs: LimbEngine): LimbArray = applyOperation(Operation.Add(rhs.copy()))
    fun subtract(rhs: LimbEngine): LimbArray = applyOperation(Operation.Sub(rhs.copy()))
    fun multiply(rhs: LimbEngine): LimbArray = applyOperation(Operation.Mul(rhs.copy()))
    fun divide(rhs: LimbEngine): LimbArray = applyOperation(Operation.Div(rhs.copy()))

    fun flush(): LimbArray = apply {
        if (queue.isEmpty()) return@apply
        val useDag = if (autoGate) decideDagBySampling() else dagEnabled
        if (autoGate && trace) {
            val mode = if (useDag) "DAG" else "EAGER"
            println("[LimbArray] autoGate decided: " + mode)
        }
        if (useDag) {
            for (idx in items.indices) {
                var eng = items[idx]
                queue.forEach { op ->
                    when (op) {
                        is Operation.Shift -> when {
                            op.bits > 0 -> eng.shiftLeft(op.bits)
                            op.bits < 0 -> eng.shiftRight(-op.bits)
                        }

                        is Operation.And -> eng.bitAnd(op.mask)
                        is Operation.Or -> eng.bitOr(op.mask)
                        is Operation.Xor -> eng.bitXor(op.mask)
                        is Operation.Add -> eng = eng.add(op.rhs)
                        is Operation.Sub -> eng = eng.sub(op.rhs)
                        is Operation.Mul -> eng = eng.mul(op.rhs)
                        is Operation.Div -> eng = eng.div(op.rhs)
                        else -> {}
                    }
                }
                eng.flush()
                items[idx] = eng
            }
        } else {
            // Eager path: apply ops sequentially without queuing/fusion
            for (idx in items.indices) {
                var eng = items[idx]
                val prev = eng.dagEnabled
                eng.dagEnabled = false
                queue.forEach { op ->
                    when (op) {
                        is Operation.Shift -> if (op.bits >= 0) eng.shiftLeft(op.bits) else eng.shiftRight(-op.bits)
                        is Operation.And -> eng.bitAnd(op.mask)
                        is Operation.Or -> eng.bitOr(op.mask)
                        is Operation.Xor -> eng.bitXor(op.mask)
                        is Operation.Add -> eng = eng.add(op.rhs)
                        is Operation.Sub -> eng = eng.sub(op.rhs)
                        is Operation.Mul -> eng = eng.mul(op.rhs)
                        is Operation.Div -> eng = eng.div(op.rhs)
                        else -> {}
                    }
                }
                eng.flush() // no-op when dag disabled
                eng.dagEnabled = prev
                items[idx] = eng
            }
        }
        queue.clear()
    }

    private fun decideDagBySampling(): Boolean {
        if (queue.isEmpty()) return dagEnabled
        val sampleCount = if (items.size < 64) items.size else 64
        if (sampleCount == 0) return dagEnabled

        fun runDag(): Long {
            val mark = kotlin.time.TimeSource.Monotonic.markNow()
            repeat(sampleCount) { idx ->
                var copy = items[idx].copy().apply { this.dagEnabled = true }
                queue.forEach { op ->
                    when (op) {
                        is Operation.Shift -> when {
                            op.bits > 0 -> copy.shiftLeft(op.bits)
                            op.bits < 0 -> copy.shiftRight(-op.bits)
                        }
                        is Operation.And -> copy.bitAnd(op.mask)
                        is Operation.Or -> copy.bitOr(op.mask)
                        is Operation.Xor -> copy.bitXor(op.mask)
                        is Operation.Add -> copy = copy.add(op.rhs)
                        is Operation.Sub -> copy = copy.sub(op.rhs)
                        is Operation.Mul -> copy = copy.mul(op.rhs)
                        is Operation.Div -> copy = copy.div(op.rhs)
                        else -> {}
                    }
                }
                copy.flush()
            }
            return mark.elapsedNow().inWholeMilliseconds
        }

        fun runEager(): Long {
            val mark = kotlin.time.TimeSource.Monotonic.markNow()
            repeat(sampleCount) { idx ->
                var copy = items[idx].copy().apply { this.dagEnabled = false }
                queue.forEach { op ->
                    when (op) {
                        is Operation.Shift -> if (op.bits >= 0) copy.shiftLeft(op.bits) else copy.shiftRight(-op.bits)
                        is Operation.And -> copy.bitAnd(op.mask)
                        is Operation.Or -> copy.bitOr(op.mask)
                        is Operation.Xor -> copy.bitXor(op.mask)
                        is Operation.Add -> copy = copy.add(op.rhs)
                        is Operation.Sub -> copy = copy.sub(op.rhs)
                        is Operation.Mul -> copy = copy.mul(op.rhs)
                        is Operation.Div -> copy = copy.div(op.rhs)
                        else -> {}
                    }
                }
                copy.flush() // no-op
            }
            return mark.elapsedNow().inWholeMilliseconds
        }

        val dagMs = runDag()
        val eagerMs = runEager()
        // Prefer the faster path; tie -> keep current mode
        return if (dagMs == eagerMs) dagEnabled else dagMs < eagerMs
    }

    fun toList(): List<LimbEngine> = run {
        // Materialize before snapshotting
        flush()
        items.map { it.copy() }
    }

    override fun toString(): String {
        // Ensure printed representation reflects materialized state
        flush()
        return "LimbArray(size=" + items.size + ")"
    }

    private fun applyOperation(op: Operation): LimbArray = apply {
        if (dagEnabled) {
            queue.add(op)
            if (queue.size >= autoFlushThreshold) {
                flush()
            }
        } else {
            // Eager path: apply op to every element immediately
            when (op) {
                is Operation.Shift -> items.forEach { if (op.bits >= 0) it.shiftLeft(op.bits) else it.shiftRight(-op.bits) }
                is Operation.And -> items.forEach { it.bitAnd(op.mask) }
                is Operation.Or -> items.forEach { it.bitOr(op.mask) }
                is Operation.Xor -> items.forEach { it.bitXor(op.mask) }
                is Operation.Add -> for (i in items.indices) items[i] = items[i].add(op.rhs)
                is Operation.Sub -> for (i in items.indices) items[i] = items[i].sub(op.rhs)
                is Operation.Mul -> for (i in items.indices) items[i] = items[i].mul(op.rhs)
                is Operation.Div -> for (i in items.indices) items[i] = items[i].div(op.rhs)
                else -> {}
            }
        }
    }

    companion object {
        private const val DEFAULT_THRESHOLD = 16

        fun of(
            vararg elements: LimbEngine,
            autoFlushThreshold: Int = DEFAULT_THRESHOLD,
            dagEnabled: Boolean = LimbEngine.DEFAULT_DAG_ENABLED,
            autoGate: Boolean = false,
            trace: Boolean = false,
        ): LimbArray = LimbArray(
            items = elements.map { it.copy().apply { this.dagEnabled = dagEnabled } }.toMutableList(),
            autoFlushThreshold = autoFlushThreshold,
            dagEnabled = dagEnabled,
            autoGate = autoGate,
            trace = trace,
        )

        fun fromList(
            elements: List<LimbEngine>,
            autoFlushThreshold: Int = DEFAULT_THRESHOLD,
            dagEnabled: Boolean = LimbEngine.DEFAULT_DAG_ENABLED,
            autoGate: Boolean = false,
            trace: Boolean = false,
        ): LimbArray = LimbArray(
            items = elements.map { it.copy().apply { this.dagEnabled = dagEnabled } }.toMutableList(),
            autoFlushThreshold = autoFlushThreshold,
            dagEnabled = dagEnabled,
            autoGate = autoGate,
            trace = trace,
        )
    }
}
