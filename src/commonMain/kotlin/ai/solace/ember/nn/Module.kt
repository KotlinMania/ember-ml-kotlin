package ai.solace.ember.nn

import ai.solace.ember.tensor.common.EmberTensor

/**
 * Base interface for all neural network modules.
 */
interface Module {
    /**
     * Performs a forward pass through the module.
     *
     * @param input The input tensor.
     * @return The output tensor.
     */
    suspend fun forward(input: EmberTensor): EmberTensor
    
    /**
     * Performs a backward pass through the module.
     *
     * @param gradOutput The gradient of the output.
     * @return The gradient of the input.
     */
    suspend fun backward(gradOutput: EmberTensor): EmberTensor
    
    /**
     * Gets the parameters of this module.
     *
     * @return A map of parameter names to tensors.
     */
    fun parameters(): Map<String, EmberTensor>
    
    /**
     * Updates the parameters of this module.
     *
     * @param gradients The gradients for each parameter.
     * @param learningRate The learning rate to use for the update.
     */
    suspend fun updateParameters(gradients: Map<String, EmberTensor>, learningRate: Float)
}

/**
 * Base class for neural network layers.
 */
abstract class Layer : Module {
    /**
     * Whether this layer is in training mode.
     */
    var training: Boolean = true
    
    /**
     * Sets the training mode of this layer.
     *
     * @param training Whether to enable training mode.
     */
    fun setTraining(training: Boolean) {
        this.training = training
    }
    
    /**
     * Sets the layer to training mode.
     */
    fun train() {
        setTraining(true)
    }
    
    /**
     * Sets the layer to evaluation mode.
     */
    fun eval() {
        setTraining(false)
    }
}