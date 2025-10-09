/**
 * Performance benchmarking utilities for comparing Kotlin implementation 
 * to Python reference implementation.
 * 
 * This provides the infrastructure for performance testing as specified
 * in the testing strategy.
 */
package ai.solace.ember.testing

import ai.solace.ember.tensor.bitwise.MegaBinary
import ai.solace.ember.tensor.bitwise.InterferenceMode
import kotlin.test.*

/**
 * Cross-platform time measurement function
 */
expect fun getCurrentTimeMs(): Long

/**
 * Cross-platform inline time measurement
 */
inline fun <T> measureTimeMs(block: () -> T): Pair<T, Long> {
    val start = getCurrentTimeMs()
    val result = block()
    val end = getCurrentTimeMs()
    return result to (end - start)
}

/**
 * Performance benchmarking data class to store results
 */
data class BenchmarkResult(
    val operationName: String,
    val executionTimeMs: Long,
    val operationsPerSecond: Double,
    val inputSize: Int,
    val iterations: Int
)

/**
 * Performance benchmark suite for bitwise and tensor operations.
 * 
 * Benchmarks cover:
 * - Basic bitwise operations
 * - Complex pattern generation
 * - Large data operations
 * - Comparison baselines
 */
class PerformanceBenchmarkSuite {

    companion object {
        const val DEFAULT_ITERATIONS = 1000
        const val LARGE_DATA_SIZE = 1000
    }

    /**
     * Benchmark basic bitwise AND operation
     */
    @Test
    fun benchmarkBitwiseAnd() {
        val a = MegaBinary("1010101010101010")
        val b = MegaBinary("1100110011001100")
        
        val result = benchmark("BitwiseAND", DEFAULT_ITERATIONS) {
            a.bitwiseAnd(b)
        }
        
        println("Bitwise AND benchmark: ${result.operationsPerSecond} ops/sec")
        assertTrue(result.executionTimeMs > 0, "Benchmark should measure execution time")
    }

    /**
     * Benchmark basic bitwise OR operation
     */
    @Test
    fun benchmarkBitwiseOr() {
        val a = MegaBinary("1010101010101010")
        val b = MegaBinary("1100110011001100")
        
        val result = benchmark("BitwiseOR", DEFAULT_ITERATIONS) {
            a.bitwiseOr(b)
        }
        
        println("Bitwise OR benchmark: ${result.operationsPerSecond} ops/sec")
        assertTrue(result.executionTimeMs > 0, "Benchmark should measure execution time")
    }

    /**
     * Benchmark basic bitwise XOR operation
     */
    @Test
    fun benchmarkBitwiseXor() {
        val a = MegaBinary("1010101010101010")
        val b = MegaBinary("1100110011001100")
        
        val result = benchmark("BitwiseXOR", DEFAULT_ITERATIONS) {
            a.bitwiseXor(b)
        }
        
        println("Bitwise XOR benchmark: ${result.operationsPerSecond} ops/sec")
        assertTrue(result.executionTimeMs > 0, "Benchmark should measure execution time")
    }

    /**
     * Benchmark shift operations
     */
    @Test
    fun benchmarkShiftOperations() {
        val data = MegaBinary("1010101010101010")
        val shift = MegaBinary("10") // Shift by 2
        
        val leftShiftResult = benchmark("LeftShift", DEFAULT_ITERATIONS) {
            data.shiftLeft(shift)
        }
        
        val rightShiftResult = benchmark("RightShift", DEFAULT_ITERATIONS) {
            data.shiftRight(shift)
        }
        
        println("Left shift benchmark: ${leftShiftResult.operationsPerSecond} ops/sec")
        println("Right shift benchmark: ${rightShiftResult.operationsPerSecond} ops/sec")
        
        assertTrue(leftShiftResult.executionTimeMs > 0, "Left shift benchmark should measure time")
        assertTrue(rightShiftResult.executionTimeMs > 0, "Right shift benchmark should measure time")
    }

    /**
     * Benchmark wave interference operations
     */
    @Test
    fun benchmarkWaveInterference() {
        val waves = listOf(
            MegaBinary("1010101010101010"),
            MegaBinary("1100110011001100"),
            MegaBinary("1111000011110000")
        )
        
        val xorResult = benchmark("WaveInterferenceXOR", 100) {
            MegaBinary.interfere(waves, InterferenceMode.XOR)
        }
        
        val andResult = benchmark("WaveInterferenceAND", 100) {
            MegaBinary.interfere(waves, InterferenceMode.AND)
        }
        
        val orResult = benchmark("WaveInterferenceOR", 100) {
            MegaBinary.interfere(waves, InterferenceMode.OR)
        }
        
        println("Wave XOR interference benchmark: ${xorResult.operationsPerSecond} ops/sec")
        println("Wave AND interference benchmark: ${andResult.operationsPerSecond} ops/sec")
        println("Wave OR interference benchmark: ${orResult.operationsPerSecond} ops/sec")
        
        assertTrue(xorResult.executionTimeMs > 0, "XOR interference should measure time")
        assertTrue(andResult.executionTimeMs > 0, "AND interference should measure time")
        assertTrue(orResult.executionTimeMs > 0, "OR interference should measure time")
    }

    /**
     * Benchmark pattern generation operations
     */
    @Test
    fun benchmarkPatternGeneration() {
        val length = MegaBinary("10000000") // 128 bits
        val dutyCycle = MegaBinary("1000") // 8 bits duty
        val halfPeriod = MegaBinary("1000") // 8 bits half period
        
        val dutyCycleResult = benchmark("DutyCycleGeneration", 100) {
            MegaBinary.createDutyCycle(length, dutyCycle)
        }
        
        val blockySinResult = benchmark("BlockySinGeneration", 100) {
            MegaBinary.generateBlockySin(length, halfPeriod)
        }
        
        println("Duty cycle generation benchmark: ${dutyCycleResult.operationsPerSecond} ops/sec")
        println("Blocky sin generation benchmark: ${blockySinResult.operationsPerSecond} ops/sec")
        
        assertTrue(dutyCycleResult.executionTimeMs > 0, "Duty cycle benchmark should measure time")
        assertTrue(blockySinResult.executionTimeMs > 0, "Blocky sin benchmark should measure time")
    }

    /**
     * Benchmark large data operations for scalability testing
     */
    @Test
    fun benchmarkLargeDataOperations() {
        // Create large binary numbers (1000 bits each)
        val large1 = MegaBinary("1".repeat(LARGE_DATA_SIZE))
        val large2 = MegaBinary("10".repeat(LARGE_DATA_SIZE / 2))
        
        val result = benchmark("LargeDataXOR", 10) {
            large1.bitwiseXor(large2)
        }
        
        println("Large data XOR benchmark (${LARGE_DATA_SIZE} bits): ${result.operationsPerSecond} ops/sec")
        
        // For large data, even small performance is acceptable
        assertTrue(result.executionTimeMs > 0, "Large data benchmark should measure time")
        assertTrue(result.operationsPerSecond > 0, "Should complete at least some operations per second")
    }

    /**
     * Memory allocation benchmark to test efficiency
     */
    @Test
    fun benchmarkMemoryAllocation() {
        val result = benchmark("MemoryAllocation", DEFAULT_ITERATIONS) {
            // Create and immediately use binary objects to test allocation
            val temp = MegaBinary("10101010")
            temp.bitwiseNot()
        }
        
        println("Memory allocation benchmark: ${result.operationsPerSecond} ops/sec")
        assertTrue(result.operationsPerSecond > 1000, "Memory allocation should be efficient")
    }

    /**
     * Benchmark operation chains for complex workflows
     */
    @Test
    fun benchmarkOperationChains() {
        val a = MegaBinary("1010101010101010")
        val b = MegaBinary("1100110011001100")
        val c = MegaBinary("1111000011110000")
        
        val result = benchmark("ComplexOperationChain", 100) {
            // Chain: ((a XOR b) AND c) OR a
            val step1 = a.bitwiseXor(b)
            val step2 = step1.bitwiseAnd(c)
            step2.bitwiseOr(a)
        }
        
        println("Complex operation chain benchmark: ${result.operationsPerSecond} ops/sec")
        assertTrue(result.executionTimeMs > 0, "Complex chain should measure execution time")
    }

    /**
     * Generic benchmark function
     */
    private fun benchmark(
        operationName: String,
        iterations: Int,
        operation: () -> Any
    ): BenchmarkResult {
        // Warm up
        repeat(10) { operation() }
        
        // Actual benchmark
        val (_, executionTime) = measureTimeMs {
            repeat(iterations) {
                operation()
            }
        }
        
        val operationsPerSecond = if (executionTime > 0) {
            (iterations * 1000.0) / executionTime
        } else {
            Double.MAX_VALUE
        }
        
        return BenchmarkResult(
            operationName = operationName,
            executionTimeMs = executionTime,
            operationsPerSecond = operationsPerSecond,
            inputSize = 16, // Default for most test cases
            iterations = iterations
        )
    }

    /**
     * Comparative benchmark against reference implementation
     * (This would typically compare to Python implementation results)
     */
    @Test
    fun benchmarkComparativePerformance() {
        // For now, this establishes baseline measurements
        // In a real scenario, this would compare against Python implementation timings
        
        val operations = mapOf(
            "AND" to { a: MegaBinary, b: MegaBinary -> a.bitwiseAnd(b) },
            "OR" to { a: MegaBinary, b: MegaBinary -> a.bitwiseOr(b) },
            "XOR" to { a: MegaBinary, b: MegaBinary -> a.bitwiseXor(b) }
        )
        
        val a = MegaBinary("1010101010101010")
        val b = MegaBinary("1100110011001100")
        
        val results = operations.map { (name, op) ->
            name to benchmark("Comparative_$name", 1000) {
                op(a, b)
            }
        }
        
        println("Comparative Performance Results:")
        results.forEach { (name, result) ->
            println("  $name: ${result.operationsPerSecond} ops/sec")
        }
        
        // All operations should complete successfully
        results.forEach { (name, result) ->
            assertTrue(result.operationsPerSecond > 0, "$name should have positive performance")
        }
    }
}