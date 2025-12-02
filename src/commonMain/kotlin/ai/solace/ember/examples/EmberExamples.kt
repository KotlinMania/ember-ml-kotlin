package ai.solace.ember.examples

import ai.solace.ember.Ember
import ai.solace.ember.Tensor

/**
 * Examples demonstrating the Ember ML Kotlin API.
 * 
 * This file showcases the MLX-inspired tensor operations available in Ember.
 */
object EmberExamples {
    
    /**
     * Basic tensor creation and arithmetic.
     */
    fun basicOperations() {
        println("=== Basic Tensor Operations ===\n")
        
        // Create tensors from lists
        val x = Ember.array(listOf(1.0f, 2.0f, 3.0f, 4.0f))
        println("x = [${x.toFloatArray().joinToString(", ")}]")
        
        val y = Ember.array(listOf(10.0f, 20.0f, 30.0f, 40.0f))
        println("y = [${y.toFloatArray().joinToString(", ")}]")
        
        // Element-wise operations
        val sum = x + y
        println("x + y = [${sum.toFloatArray().joinToString(", ")}]")
        
        val product = x * y
        println("x * y = [${product.toFloatArray().joinToString(", ")}]")
        
        // Scalar operations
        val scaled = x * 2.0f
        println("x * 2 = [${scaled.toFloatArray().joinToString(", ")}]")
        
        println()
    }
    
    /**
     * Factory functions for creating tensors.
     */
    fun factoryFunctions() {
        println("=== Tensor Factory Functions ===\n")
        
        // Zeros
        val zeros = Ember.zeros(intArrayOf(2, 3))
        println("zeros(2, 3): shape = [${zeros.shape.joinToString(", ")}]")
        
        // Ones
        val ones = Ember.ones(intArrayOf(3))
        println("ones(3) = [${ones.toFloatArray().joinToString(", ")}]")
        
        // Arange
        val range = Ember.arange(0, 10, 2)
        println("arange(0, 10, 2) = [${range.toFloatArray().joinToString(", ")}]")
        
        // Linspace
        val linspace = Ember.linspace(0.0, 1.0, 5)
        println("linspace(0, 1, 5) = [${linspace.toFloatArray().joinToString(", ")}]")
        
        println()
    }
    
    /**
     * Mathematical operations on tensors.
     */
    fun mathOperations() {
        println("=== Mathematical Operations ===\n")
        
        val x = Ember.array(listOf(0.0f, 1.0f, 2.0f))
        println("x = [${x.toFloatArray().joinToString(", ")}]")
        
        // Trigonometric functions
        val sinX = Ember.sin(x)
        println("sin(x) = [${sinX.toFloatArray().joinToString(", ") { "%.4f".format(it) }}]")
        
        // Exponential and logarithm
        val expX = Ember.exp(x)
        println("exp(x) = [${expX.toFloatArray().joinToString(", ") { "%.4f".format(it) }}]")
        
        // Power functions
        val sqrtX = Ember.sqrt(Ember.array(listOf(1.0f, 4.0f, 9.0f)))
        println("sqrt([1, 4, 9]) = [${sqrtX.toFloatArray().joinToString(", ")}]")
        
        println()
    }
    
    /**
     * Reduction operations.
     */
    fun reductions() {
        println("=== Reduction Operations ===\n")
        
        val x = Ember.array(listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))
        println("x = [${x.toFloatArray().joinToString(", ")}]")
        
        println("sum(x) = ${Ember.sum(x).toScalar().toFloat()}")
        println("mean(x) = ${Ember.mean(x).toScalar().toFloat()}")
        println("max(x) = ${Ember.max(x).toScalar().toFloat()}")
        println("min(x) = ${Ember.min(x).toScalar().toFloat()}")
        
        println()
    }
    
    /**
     * Shape operations: reshape, transpose, matmul.
     */
    fun shapeOperations() {
        println("=== Shape Operations ===\n")
        
        // Reshape
        val x = Ember.arange(0, 6)
        println("x = [${x.toFloatArray().joinToString(", ")}]")
        
        val reshaped = Ember.reshape(x, intArrayOf(2, 3))
        println("reshape(x, [2, 3]): shape = [${reshaped.shape.joinToString(", ")}]")
        
        // Matrix multiplication
        val a = Ember.array(listOf(
            listOf(1.0f, 2.0f),
            listOf(3.0f, 4.0f)
        ))
        val b = Ember.array(listOf(
            listOf(5.0f, 6.0f),
            listOf(7.0f, 8.0f)
        ))
        val c = Ember.matmul(a, b)
        println("matmul result shape: [${c.shape.joinToString(", ")}]")
        println("Result values: [${c.toFloatArray().joinToString(", ")}]")
        
        println()
    }
    
    /**
     * Run all examples.
     */
    fun runAll() {
        println()
        println("════════════════════════════════════════════════")
        println("       Ember ML Kotlin - API Examples          ")
        println("════════════════════════════════════════════════")
        println()
        
        basicOperations()
        factoryFunctions()
        mathOperations()
        reductions()
        shapeOperations()
        
        println("════════════════════════════════════════════════")
        println("        All Examples Completed! ✓               ")
        println("════════════════════════════════════════════════")
        println()
    }
}

/**
 * Main entry point for running examples.
 */
fun main() {
    EmberExamples.runAll()
}
