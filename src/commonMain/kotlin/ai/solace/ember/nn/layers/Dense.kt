package ai.solace.ember.nn.layers

import ai.solace.ember.nn.Layer
import ai.solace.ember.tensor.common.EmberTensor
import ai.solace.ember.tensor.common.EmberShape
import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.BackendRegistry

/**
 * Dense (fully connected) layer.
 *
 * @param inputSize The number of input features.
 * @param outputSize The number of output features.
 * @param useBias Whether to use bias parameters.
 */
class Dense(
    private val inputSize: Int,
    private val outputSize: Int,
    private val useBias: Boolean = true
) : Layer() {
    
    private val weights: EmberTensor
    private val bias: EmberTensor?
    
    init {
        // Initialize weights with Xavier initialization
        val backend = BackendRegistry.getCurrentBackend()
        val limit = kotlin.math.sqrt(6.0 / (inputSize + outputSize)).toFloat()
        
        // Create weight matrix
        val weightData = FloatArray(inputSize * outputSize) { 
            (kotlin.random.Random.nextFloat() - 0.5f) * 2 * limit 
        }
        val weightBackendTensor = backend.createTensor(
            weightData, 
            intArrayOf(inputSize, outputSize), 
            DType.FLOAT32
        )
        weights = EmberTensor(
            shape = EmberShape(intArrayOf(inputSize, outputSize)),
            dtype = DType.FLOAT32,
            device = "cpu",
            requiresGrad = true,
            backendTensor = weightBackendTensor
        )
        
        // Create bias vector if needed
        bias = if (useBias) {
            val biasData = FloatArray(outputSize) { 0.0f }
            val biasBackendTensor = backend.createTensor(
                biasData,
                intArrayOf(outputSize),
                DType.FLOAT32
            )
            EmberTensor(
                shape = EmberShape(intArrayOf(outputSize)),
                dtype = DType.FLOAT32,
                device = "cpu",
                requiresGrad = true,
                backendTensor = biasBackendTensor
            )
        } else {
            null
        }
    }
    
    override suspend fun forward(input: EmberTensor): EmberTensor {
        // Perform matrix multiplication: input @ weights
        val output = input.matmul(weights)
        
        // Add bias if present
        return if (bias != null) {
            output + bias
        } else {
            output
        }
    }
    
    override suspend fun backward(gradOutput: EmberTensor): EmberTensor {
        // This is a simplified backward pass
        // In a full implementation, we would need to compute and store gradients
        // For now, just return the input gradient (identity)
        return gradOutput
    }
    
    override fun parameters(): Map<String, EmberTensor> {
        val params = mutableMapOf<String, EmberTensor>()
        params["weight"] = weights
        if (bias != null) {
            params["bias"] = bias
        }
        return params
    }
    
    override suspend fun updateParameters(gradients: Map<String, EmberTensor>, learningRate: Float) {
        // This is a simplified parameter update
        // In a full implementation, we would apply the gradients to update parameters
        // For now, this is a placeholder
    }
}