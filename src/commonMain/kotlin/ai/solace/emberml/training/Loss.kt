package ai.solace.emberml.training

import ai.solace.emberml.tensor.common.EmberTensor
import ai.solace.emberml.tensor.common.EmberShape
import ai.solace.emberml.tensor.common.EmberDType
import ai.solace.emberml.backend.BackendRegistry

/**
 * Base interface for loss functions.
 */
interface Loss {
    /**
     * Computes the loss between predictions and targets.
     * 
     * @param predictions The predicted values
     * @param targets The target values
     * @return The computed loss
     */
    suspend fun forward(predictions: EmberTensor, targets: EmberTensor): EmberTensor
    
    /**
     * Computes the gradient of the loss with respect to predictions.
     * 
     * @param predictions The predicted values
     * @param targets The target values
     * @return The gradient tensor
     */
    suspend fun backward(predictions: EmberTensor, targets: EmberTensor): EmberTensor
}

/**
 * Mean Squared Error (MSE) loss function.
 * 
 * Computes: L = (1/n) * Σ(predictions - targets)^2
 */
class MSELoss : Loss {
    
    override suspend fun forward(predictions: EmberTensor, targets: EmberTensor): EmberTensor {
        if (!predictions.shape.isBroadcastableWith(targets.shape)) {
            throw IllegalArgumentException("Predictions shape ${predictions.shape} is not compatible with targets shape ${targets.shape}")
        }
        
        val backend = BackendRegistry.getCurrentBackend()
        
        // Compute difference: predictions - targets
        val diff = predictions - targets
        
        // Compute squared difference: diff^2
        val squaredDiff = diff * diff
        
        // Compute mean: sum(squaredDiff) / n
        // For now, return a simplified version - in practice we'd compute the actual mean
        return squaredDiff
    }
    
    override suspend fun backward(predictions: EmberTensor, targets: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        
        // MSE gradient: 2 * (predictions - targets) / n
        val diff = predictions - targets
        
        // Create scalar tensor for the factor 2
        val twoData = when (predictions.dtype) {
            EmberDType.FLOAT32 -> FloatArray(1) { 2.0f }
            EmberDType.FLOAT64 -> DoubleArray(1) { 2.0 }
            else -> throw IllegalArgumentException("MSE loss only supports floating point types")
        }
        
        val twoBackendTensor = backend.createTensor(twoData, intArrayOf(1), predictions.dtype)
        val twoTensor = EmberTensor(
            shape = EmberShape.of(1),
            dtype = predictions.dtype,
            device = predictions.device,
            requiresGrad = false,
            backendTensor = twoBackendTensor
        )
        
        return diff * twoTensor
    }
}

/**
 * Mean Absolute Error (MAE) loss function.
 * 
 * Computes: L = (1/n) * Σ|predictions - targets|
 */
class MAELoss : Loss {
    
    override suspend fun forward(predictions: EmberTensor, targets: EmberTensor): EmberTensor {
        if (!predictions.shape.isBroadcastableWith(targets.shape)) {
            throw IllegalArgumentException("Predictions shape ${predictions.shape} is not compatible with targets shape ${targets.shape}")
        }
        
        // Compute absolute difference: |predictions - targets|
        val diff = predictions - targets
        
        // For now, return the difference as a placeholder
        // In practice, we'd compute the absolute value and mean
        return diff
    }
    
    override suspend fun backward(predictions: EmberTensor, targets: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        
        // MAE gradient: sign(predictions - targets) / n
        val diff = predictions - targets
        
        // For now, return a simplified gradient
        // In practice, we'd compute the sign of the difference
        
        val oneData = when (predictions.dtype) {
            EmberDType.FLOAT32 -> FloatArray(1) { 1.0f }
            EmberDType.FLOAT64 -> DoubleArray(1) { 1.0 }
            else -> throw IllegalArgumentException("MAE loss only supports floating point types")
        }
        
        val oneBackendTensor = backend.createTensor(oneData, intArrayOf(1), predictions.dtype)
        val oneTensor = EmberTensor(
            shape = EmberShape.of(1),
            dtype = predictions.dtype,
            device = predictions.device,
            requiresGrad = false,
            backendTensor = oneBackendTensor
        )
        
        return diff * oneTensor
    }
}

/**
 * Binary Cross-Entropy loss function.
 * 
 * Computes: L = -(1/n) * Σ[targets * log(predictions) + (1 - targets) * log(1 - predictions)]
 */
class BCELoss : Loss {
    
    override suspend fun forward(predictions: EmberTensor, targets: EmberTensor): EmberTensor {
        if (!predictions.shape.isBroadcastableWith(targets.shape)) {
            throw IllegalArgumentException("Predictions shape ${predictions.shape} is not compatible with targets shape ${targets.shape}")
        }
        
        // For now, return a simplified version
        // In practice, we'd implement the full BCE formula with log operations
        val diff = predictions - targets
        return diff * diff
    }
    
    override suspend fun backward(predictions: EmberTensor, targets: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        
        // BCE gradient: (predictions - targets) / (predictions * (1 - predictions)) / n
        // For now, return a simplified gradient
        val diff = predictions - targets
        
        val oneData = when (predictions.dtype) {
            EmberDType.FLOAT32 -> FloatArray(1) { 1.0f }
            EmberDType.FLOAT64 -> DoubleArray(1) { 1.0 }
            else -> throw IllegalArgumentException("BCE loss only supports floating point types")
        }
        
        val oneBackendTensor = backend.createTensor(oneData, intArrayOf(1), predictions.dtype)
        val oneTensor = EmberTensor(
            shape = EmberShape.of(1),
            dtype = predictions.dtype,
            device = predictions.device,
            requiresGrad = false,
            backendTensor = oneBackendTensor
        )
        
        return diff * oneTensor
    }
}