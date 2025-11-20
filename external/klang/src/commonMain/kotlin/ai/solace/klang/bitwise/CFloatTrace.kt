package ai.solace.klang.bitwise

import kotlin.concurrent.Volatile

/**
 * CFloatTrace: Diagnostic tracer for CFloat32 arithmetic operations.
 *
 * Provides step-by-step recording of floating-point operations at the bit level,
 * enabling detailed inspection of IEEE 754 behavior, rounding modes, precision loss,
 * and numerical stability issues.
 *
 * ## Why CFloatTrace?
 *
 * **The Problem**: Floating-point arithmetic is complex and non-obvious:
 * - Rounding behavior varies by operation
 * - Precision loss accumulates through calculations
 * - NaN and infinity propagation is hard to track
 * - Differences between platforms are subtle
 *
 * **The Solution**: Record every operation with bit-level precision:
 * - **Before/after snapshots**: See exact bit patterns
 * - **Operation tracking**: Know what operation caused each change
 * - **Replay capability**: Reconstruct computation step-by-step
 * - **Debugging aid**: Identify where precision is lost or NaN introduced
 *
 * ## Architecture
 *
 * ```
 * ┌─────────────────┐
 * │  CFloatTrace    │ ← Singleton tracer
 * │   (disabled)    │
 * └────────┬────────┘
 *          │
 *     ┌────┴────┐
 *     │ enabled │
 *     └────┬────┘
 *          │
 *    ┌─────▼──────┐
 *    │   Entry[]  │ ← Recorded operations
 *    │  (buffer)  │
 *    └────────────┘
 * ```
 *
 * ## Entry Structure
 *
 * Each recorded operation captures:
 * - **op**: Operation name ("+", "-", "*", "/", "sqrt", etc.)
 * - **lhsBits**: Left operand as raw IEEE 754 bits
 * - **rhsBits**: Right operand bits (null for unary operations)
 * - **resultBits**: Result as raw IEEE 754 bits
 *
 * Provides computed properties:
 * - **lhs**: Float value of left operand
 * - **rhs**: Float value of right operand (if applicable)
 * - **result**: Float value of result
 *
 * ## Usage Examples
 *
 * ### Basic Tracing
 * ```kotlin
 * // Start tracing
 * CFloatTrace.start()
 *
 * // Perform operations
 * val a = CFloat32(1.0f)
 * val b = CFloat32(3.0f)
 * val c = a / b  // Division with rounding
 *
 * // Stop and retrieve trace
 * val entries = CFloatTrace.stop()
 * entries.forEach { entry ->
 *     println("${entry.op}: ${entry.lhs} → ${entry.result}")
 *     println("  Bits: 0x${entry.lhsBits.toString(16)} → 0x${entry.resultBits.toString(16)}")
 * }
 * ```
 *
 * ### Scoped Tracing with withTracing
 * ```kotlin
 * val (result, trace) = CFloatTrace.withTracing {
 *     val x = CFloat32(0.1f)
 *     val y = CFloat32(0.2f)
 *     x + y  // Test if 0.1 + 0.2 is exactly 0.3
 * }
 *
 * println("Result: $result")
 * trace.forEach { entry ->
 *     println("${entry.op}: ${entry.lhs} ${entry.rhs} → ${entry.result}")
 * }
 * ```
 *
 * ### Debugging Precision Loss
 * ```kotlin
 * CFloatTrace.start()
 *
 * var sum = CFloat32(0.0f)
 * repeat(1000) {
 *     sum += CFloat32(0.001f)  // Accumulate small values
 * }
 *
 * val entries = CFloatTrace.stop()
 *
 * // Analyze where precision degrades
 * entries.forEachIndexed { i, entry ->
 *     val expected = (i + 1) * 0.001f
 *     val actual = entry.result
 *     val error = kotlin.math.abs(expected - actual)
 *     if (error > 1e-6f) {
 *         println("Iteration $i: error = $error")
 *     }
 * }
 * ```
 *
 * ### Tracking NaN Propagation
 * ```kotlin
 * val (result, trace) = CFloatTrace.withTracing {
 *     val a = CFloat32(1.0f)
 *     val b = CFloat32(0.0f)
 *     val c = a / b  // Creates infinity
 *     val d = c - c  // Infinity - infinity = NaN
 *     d
 * }
 *
 * // Find where NaN was introduced
 * val nanEntry = trace.firstOrNull { it.result.isNaN() }
 * if (nanEntry != null) {
 *     println("NaN introduced by: ${nanEntry.op}")
 *     println("  Left: ${nanEntry.lhs}, Right: ${nanEntry.rhs}")
 * }
 * ```
 *
 * ### Comparing Implementations
 * ```kotlin
 * // Trace custom implementation
 * val (customResult, customTrace) = CFloatTrace.withTracing {
 *     computeWithCFloat32()
 * }
 *
 * // Trace native implementation
 * val nativeResult = computeWithNativeFloat()
 *
 * // Compare bit patterns
 * customTrace.forEach { entry ->
 *     println("Custom: 0x${entry.resultBits.toString(16)}")
 * }
 * ```
 *
 * ## State Management
 *
 * ### enabled: Boolean
 * Controls whether operations are recorded.
 * - **false** (default): No overhead, operations not tracked
 * - **true**: Every CFloat32 operation is recorded
 *
 * ### start()
 * Enables tracing and clears any previous entries.
 * ```kotlin
 * CFloatTrace.start()  // Begin new trace session
 * ```
 *
 * ### stop(): List<Entry>
 * Disables tracing and returns all recorded entries.
 * ```kotlin
 * val entries = CFloatTrace.stop()  // End trace, get results
 * ```
 *
 * ### reset()
 * Clears recorded entries without changing enabled state.
 * ```kotlin
 * CFloatTrace.reset()  // Clear buffer, keep tracing
 * ```
 *
 * ### withTracing(block): Pair<T, List<Entry>>
 * Executes a block with tracing enabled, returns result and trace.
 * Automatically handles exceptions and cleanup.
 * ```kotlin
 * val (result, trace) = CFloatTrace.withTracing {
 *     // Code to trace
 * }
 * ```
 *
 * ## Performance Impact
 *
 * ### When Disabled (default)
 * - **Overhead**: ~0-1% (single volatile boolean check)
 * - **Memory**: ~0 bytes (empty list)
 * - **Safe for production**: Yes
 *
 * ### When Enabled
 * - **Overhead**: ~5-10× slower (logging, allocation)
 * - **Memory**: ~40 bytes per operation
 * - **Use for**: Debugging, testing, analysis only
 *
 * **Recommendation**: Only enable in development/testing, never in production hot paths.
 *
 * ## Thread Safety
 *
 * **Current implementation**: NOT thread-safe
 * - **enabled**: Volatile for visibility, but not atomic with list operations
 * - **entries**: Mutable list with no synchronization
 *
 * **Safe usage patterns**:
 * - Single-threaded tracing only
 * - Per-thread tracer instances (future enhancement)
 * - External synchronization if multi-threaded
 *
 * **Thread-safe wrapper example**:
 * ```kotlin
 * object ThreadSafeCFloatTrace {
 *     private val lock = Any()
 *     
 *     fun <T> traceSync(block: () -> T): Pair<T, List<CFloatTrace.Entry>> {
 *         synchronized(lock) {
 *             return CFloatTrace.withTracing(block)
 *         }
 *     }
 * }
 * ```
 *
 * ## Integration with CFloat32
 *
 * CFloat32 operations should call `log()` when appropriate:
 * ```kotlin
 * operator fun plus(other: CFloat32): CFloat32 {
 *     val result = Float32Math.addBits(this.bits, other.bits)
 *     CFloatTrace.log("+", this.bits, other.bits, result)
 *     return CFloat32.fromBits(result)
 * }
 * ```
 *
 * ## Output Format
 *
 * When logging is enabled, each operation prints to stdout:
 * ```
 * TRACE + lhs=1.0 rhs=2.0 result=3.0
 * TRACE * lhs=3.0 rhs=0.5 result=1.5
 * TRACE / lhs=1.0 rhs=3.0 result=0.33333334
 * ```
 *
 * ## Use Cases
 *
 * ### Numerical Analysis
 * - Study rounding behavior
 * - Measure precision loss
 * - Validate compensated summation algorithms
 *
 * ### Platform Comparison
 * - Compare JVM vs JS vs Native results
 * - Identify platform-specific differences
 * - Verify IEEE 754 compliance
 *
 * ### Algorithm Development
 * - Debug custom math routines
 * - Optimize operation order
 * - Validate against reference implementations
 *
 * ### Education
 * - Visualize floating-point behavior
 * - Demonstrate rounding effects
 * - Explain NaN and infinity propagation
 *
 * ## Limitations
 *
 * - **Not thread-safe**: Single-threaded use only
 * - **Memory growth**: Unbounded buffer (manual reset required)
 * - **Performance cost**: 5-10× slowdown when enabled
 * - **No filtering**: Records all operations (no selective tracing)
 * - **No timestamps**: Operations not timestamped
 *
 * ## Future Enhancements
 *
 * Potential improvements:
 * - Thread-local tracing for multi-threaded code
 * - Filtering by operation type or value range
 * - Statistical summaries (min/max/avg precision loss)
 * - Export to various formats (JSON, CSV, binary)
 * - Call stack capture for operation attribution
 * - Configurable buffer size with overflow handling
 *
 * ## Related Components
 *
 * | Component | Purpose | Relationship |
 * |-----------|---------|--------------|
 * | CFloat32 | Custom float type | Traced operations |
 * | Float32Math | Bit-level arithmetic | Operations being traced |
 * | ZlibLogger | General logging | Complementary logging |
 *
 * @see CFloat32 For the floating-point type being traced
 * @see Float32Math For underlying bit-level operations
 * @since 0.1.0
 */
object CFloatTrace {
    /**
     * Controls whether tracing is currently active.
     *
     * When true, all CFloat32 operations that call `log()` will be recorded.
     * When false (default), logging is skipped for minimal overhead.
     *
     * Marked `@Volatile` for visibility across threads, though the tracer
     * itself is not thread-safe.
     */
    @Volatile
    var enabled: Boolean = false
        private set

    private val entries = mutableListOf<Entry>()

    /**
     * A single recorded floating-point operation with bit-level details.
     *
     * Captures the complete state of an operation including operands and result
     * as raw IEEE 754 bit patterns, allowing exact reconstruction and analysis.
     *
     * ## Fields
     * - **op**: Operation name (e.g., "+", "-", "*", "/", "sqrt", "abs")
     * - **lhsBits**: Left operand as 32-bit IEEE 754 bit pattern
     * - **rhsBits**: Right operand bits (null for unary operations like sqrt)
     * - **resultBits**: Result as 32-bit IEEE 754 bit pattern
     *
     * ## Computed Properties
     * ```kotlin
     * val lhs: Float      // Converts lhsBits to Float
     * val rhs: Float?     // Converts rhsBits to Float (null if unary op)
     * val result: Float   // Converts resultBits to Float
     * ```
     *
     * ## Example
     * ```kotlin
     * val entry = Entry(
     *     op = "+",
     *     lhsBits = 0x3F800000,  // 1.0f
     *     rhsBits = 0x40000000,  // 2.0f
     *     resultBits = 0x40400000 // 3.0f
     * )
     * println("${entry.lhs} ${entry.op} ${entry.rhs} = ${entry.result}")
     * // Output: 1.0 + 2.0 = 3.0
     * ```
     *
     * @property op The operation that was performed
     * @property lhsBits Raw bits of the left operand (or sole operand for unary ops)
     * @property rhsBits Raw bits of the right operand (null for unary operations)
     * @property resultBits Raw bits of the operation result
     */
    data class Entry(
        val op: String,
        val lhsBits: Int,
        val rhsBits: Int?,
        val resultBits: Int
    ) {
        /** The left operand as a Float value. */
        val lhs: Float get() = Float.fromBits(lhsBits)
        
        /** The right operand as a Float value, or null for unary operations. */
        val rhs: Float? get() = rhsBits?.let { Float.fromBits(it) }
        
        /** The result as a Float value. */
        val result: Float get() = Float.fromBits(resultBits)
    }

    /**
     * Starts a new tracing session.
     *
     * Clears any previously recorded entries and enables tracing.
     * All CFloat32 operations that call `log()` will be recorded until `stop()` is called.
     *
     * ## Example
     * ```kotlin
     * CFloatTrace.start()
     * val a = CFloat32(1.0f)
     * val b = a + CFloat32(2.0f)
     * val entries = CFloatTrace.stop()
     * println("Recorded ${entries.size} operations")
     * ```
     *
     * @see stop
     * @see withTracing
     */
    fun start() {
        enabled = true
        entries.clear()
    }

    /**
     * Stops tracing and returns all recorded entries.
     *
     * Disables tracing and returns a copy of all recorded operations.
     * The internal buffer is preserved (use `reset()` to clear it).
     *
     * ## Example
     * ```kotlin
     * CFloatTrace.start()
     * // ... perform operations ...
     * val trace = CFloatTrace.stop()
     * 
     * trace.forEach { entry ->
     *     println("${entry.op}: ${entry.lhs} → ${entry.result}")
     * }
     * ```
     *
     * @return List of all recorded Entry objects (defensive copy)
     * @see start
     * @see reset
     */
    fun stop(): List<Entry> {
        enabled = false
        return entries.toList()
    }

    /**
     * Executes a block with tracing enabled, returning both the result and trace.
     *
     * Automatically handles tracing lifecycle:
     * 1. Starts tracing (clears previous entries)
     * 2. Executes the block
     * 3. Stops tracing
     * 4. Returns both the block's result and recorded entries
     *
     * Exception-safe: tracing is stopped even if the block throws.
     *
     * ## Example
     * ```kotlin
     * val (result, trace) = CFloatTrace.withTracing {
     *     val x = CFloat32(2.0f)
     *     val y = CFloat32(3.0f)
     *     x * y + CFloat32(1.0f)
     * }
     * 
     * println("Result: $result")
     * println("Operations: ${trace.size}")
     * trace.forEach { println(it) }
     * ```
     *
     * ## Exception Handling
     * ```kotlin
     * try {
     *     val (_, trace) = CFloatTrace.withTracing {
     *         CFloat32(1.0f) / CFloat32(0.0f)  // May throw or return Infinity
     *     }
     *     println("Trace before error: $trace")
     * } catch (e: Exception) {
     *     // Tracing was still stopped properly
     * }
     * ```
     *
     * @param T The return type of the block
     * @param block The code to execute with tracing enabled
     * @return Pair of (block result, list of recorded entries)
     * @see start
     * @see stop
     */
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

    /**
     * Clears all recorded entries without changing the enabled state.
     *
     * Useful for resetting the buffer during a long tracing session without
     * stopping and restarting tracing.
     *
     * ## Example
     * ```kotlin
     * CFloatTrace.start()
     * 
     * // Phase 1
     * performOperation1()
     * val trace1 = CFloatTrace.stop()
     * 
     * // Phase 2 (new trace session)
     * CFloatTrace.reset()
     * CFloatTrace.start()
     * performOperation2()
     * val trace2 = CFloatTrace.stop()
     * ```
     *
     * @see start
     * @see stop
     */
    fun reset() {
        entries.clear()
    }

    /**
     * Records a single operation if tracing is enabled.
     *
     * This method is called by CFloat32 operations to record their execution.
     * When tracing is disabled, this method returns immediately with minimal overhead.
     *
     * Also prints a trace line to stdout showing the operation in human-readable form.
     *
     * ## Example Usage in CFloat32
     * ```kotlin
     * operator fun plus(other: CFloat32): CFloat32 {
     *     val resultBits = Float32Math.addBits(this.bits, other.bits)
     *     CFloatTrace.log("+", this.bits, other.bits, resultBits)
     *     return CFloat32.fromBits(resultBits)
     * }
     * ```
     *
     * ## Output Format
     * ```
     * TRACE + lhs=1.0 rhs=2.0 result=3.0
     * TRACE sqrt lhs=4.0 rhs=null result=2.0
     * ```
     *
     * @param op Operation name (conventionally "+", "-", "*", "/", "sqrt", etc.)
     * @param lhs Left operand raw bits (or sole operand for unary operations)
     * @param rhs Right operand raw bits (null for unary operations)
     * @param result Result raw bits
     */
    fun log(op: String, lhs: Int, rhs: Int?, result: Int) {
        if (!enabled) return
        entries += Entry(op, lhs, rhs, result)
        println("TRACE $op lhs=${Float.fromBits(lhs)} rhs=${rhs?.let { Float.fromBits(it) }} result=${Float.fromBits(result)}")
    }
}
