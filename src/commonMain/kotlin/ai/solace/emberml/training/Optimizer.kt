package ai.solace.emberml.training

import ai.solace.emberml.tensor.common.EmberTensor
import ai.solace.emberml.tensor.common.EmberShape
import ai.solace.emberml.tensor.common.EmberDType
import ai.solace.emberml.backend.BackendRegistry

/**
 * Base interface for optimizers.
 */
interface Optimizer {
    /**
     * Updates parameters based on gradients.
     * 
     * @param parameters Map of parameter names to tensors
     * @param gradients Map of parameter names to gradient tensors
     */
    suspend fun step(parameters: Map<String, EmberTensor>, gradients: Map<String, EmberTensor>)
    
    /**
     * Zeros out gradients.
     */
    suspend fun zeroGrad()
}

/**
 * Stochastic Gradient Descent (SGD) optimizer.
 * 
 * @param learningRate The learning rate for parameter updates
 * @param momentum The momentum factor (default: 0.0)
 * @param weightDecay Weight decay factor for L2 regularization (default: 0.0)
 */
class SGD(
    private val learningRate: Float,
    private val momentum: Float = 0.0f,
    private val weightDecay: Float = 0.0f
) : Optimizer {
    
    private val velocities = mutableMapOf<String, EmberTensor>()
    
    override suspend fun step(parameters: Map<String, EmberTensor>, gradients: Map<String, EmberTensor>) {
        val backend = BackendRegistry.getCurrentBackend()
        
        for ((name, param) in parameters) {
            val grad = gradients[name] ?: continue
            
            // Apply weight decay if specified
            val effectiveGrad = if (weightDecay > 0.0f) {
                val decayData = when (param.dtype) {
                    EmberDType.FLOAT32 -> FloatArray(1) { weightDecay }
                    EmberDType.FLOAT64 -> DoubleArray(1) { weightDecay.toDouble() }
                    else -> throw IllegalArgumentException("Weight decay only supports floating point types")
                }
                
                val decayBackendTensor = backend.createTensor(decayData, intArrayOf(1), param.dtype)
                val decayTensor = EmberTensor(
                    shape = EmberShape.of(1),
                    dtype = param.dtype,
                    device = param.device,
                    requiresGrad = false,
                    backendTensor = decayBackendTensor
                )
                
                grad + param * decayTensor
            } else {
                grad
            }
            
            // Apply momentum if specified
            val update = if (momentum > 0.0f) {
                val velocity = velocities.getOrPut(name) {
                    // Initialize velocity to zero
                    val zeroData = when (param.dtype) {
                        EmberDType.FLOAT32 -> FloatArray(param.shape.totalSize()) { 0.0f }
                        EmberDType.FLOAT64 -> DoubleArray(param.shape.totalSize()) { 0.0 }
                        else -> throw IllegalArgumentException("Momentum only supports floating point types")
                    }
                    
                    val zeroBackendTensor = backend.createTensor(zeroData, param.shape.dimensions, param.dtype)
                    EmberTensor(
                        shape = param.shape,
                        dtype = param.dtype,
                        device = param.device,
                        requiresGrad = false,
                        backendTensor = zeroBackendTensor
                    )
                }
                
                // Update velocity: v = momentum * v + grad
                val momentumData = when (param.dtype) {
                    EmberDType.FLOAT32 -> FloatArray(1) { momentum }
                    EmberDType.FLOAT64 -> DoubleArray(1) { momentum.toDouble() }
                    else -> throw IllegalArgumentException("Momentum only supports floating point types")
                }
                
                val momentumBackendTensor = backend.createTensor(momentumData, intArrayOf(1), param.dtype)
                val momentumTensor = EmberTensor(
                    shape = EmberShape.of(1),
                    dtype = param.dtype,
                    device = param.device,
                    requiresGrad = false,
                    backendTensor = momentumBackendTensor
                )
                
                val newVelocity = velocity * momentumTensor + effectiveGrad
                velocities[name] = newVelocity
                newVelocity
            } else {
                effectiveGrad
            }
            
            // Apply learning rate and update parameters
            val lrData = when (param.dtype) {
                EmberDType.FLOAT32 -> FloatArray(1) { learningRate }
                EmberDType.FLOAT64 -> DoubleArray(1) { learningRate.toDouble() }
                else -> throw IllegalArgumentException("Learning rate only supports floating point types")
            }
            
            val lrBackendTensor = backend.createTensor(lrData, intArrayOf(1), param.dtype)
            val lrTensor = EmberTensor(
                shape = EmberShape.of(1),
                dtype = param.dtype,
                device = param.device,
                requiresGrad = false,
                backendTensor = lrBackendTensor
            )
            
            // Note: In a full implementation, we would modify parameters in-place
            // For now, this is a simplified version that would need integration
            // with the actual parameter storage mechanism
            val paramUpdate = update * lrTensor
            // param = param - paramUpdate (this would need actual parameter mutation)
        }
    }
    
    override suspend fun zeroGrad() {
        // In a full implementation, this would zero out accumulated gradients
        // For now, this is a placeholder
    }
}

/**
 * Adam optimizer.
 * 
 * @param learningRate The learning rate (default: 0.001)
 * @param beta1 The exponential decay rate for the first moment estimates (default: 0.9)
 * @param beta2 The exponential decay rate for the second moment estimates (default: 0.999)
 * @param epsilon A small constant for numerical stability (default: 1e-8)
 * @param weightDecay Weight decay factor for L2 regularization (default: 0.0)
 */
class Adam(
    private val learningRate: Float = 0.001f,
    private val beta1: Float = 0.9f,
    private val beta2: Float = 0.999f,
    private val epsilon: Float = 1e-8f,
    private val weightDecay: Float = 0.0f
) : Optimizer {
    
    private val momentEstimates = mutableMapOf<String, EmberTensor>()
    private val velocityEstimates = mutableMapOf<String, EmberTensor>()
    private var timeStep = 0
    
    override suspend fun step(parameters: Map<String, EmberTensor>, gradients: Map<String, EmberTensor>) {
        val backend = BackendRegistry.getCurrentBackend()
        timeStep++
        
        for ((name, param) in parameters) {
            val grad = gradients[name] ?: continue
            
            // Apply weight decay if specified
            val effectiveGrad = if (weightDecay > 0.0f) {
                val decayData = when (param.dtype) {
                    EmberDType.FLOAT32 -> FloatArray(1) { weightDecay }
                    EmberDType.FLOAT64 -> DoubleArray(1) { weightDecay.toDouble() }
                    else -> throw IllegalArgumentException("Weight decay only supports floating point types")
                }
                
                val decayBackendTensor = backend.createTensor(decayData, intArrayOf(1), param.dtype)
                val decayTensor = EmberTensor(
                    shape = EmberShape.of(1),
                    dtype = param.dtype,
                    device = param.device,
                    requiresGrad = false,
                    backendTensor = decayBackendTensor
                )
                
                grad + param * decayTensor
            } else {
                grad
            }
            
            // Initialize moment and velocity estimates if not present
            val moment = momentEstimates.getOrPut(name) {
                val zeroData = when (param.dtype) {
                    EmberDType.FLOAT32 -> FloatArray(param.shape.totalSize()) { 0.0f }
                    EmberDType.FLOAT64 -> DoubleArray(param.shape.totalSize()) { 0.0 }
                    else -> throw IllegalArgumentException("Adam only supports floating point types")
                }
                
                val zeroBackendTensor = backend.createTensor(zeroData, param.shape.dimensions, param.dtype)
                EmberTensor(
                    shape = param.shape,
                    dtype = param.dtype,
                    device = param.device,
                    requiresGrad = false,
                    backendTensor = zeroBackendTensor
                )
            }
            
            val velocity = velocityEstimates.getOrPut(name) {
                val zeroData = when (param.dtype) {
                    EmberDType.FLOAT32 -> FloatArray(param.shape.totalSize()) { 0.0f }
                    EmberDType.FLOAT64 -> DoubleArray(param.shape.totalSize()) { 0.0 }
                    else -> throw IllegalArgumentException("Adam only supports floating point types")
                }
                
                val zeroBackendTensor = backend.createTensor(zeroData, param.shape.dimensions, param.dtype)
                EmberTensor(
                    shape = param.shape,
                    dtype = param.dtype,
                    device = param.device,
                    requiresGrad = false,
                    backendTensor = zeroBackendTensor
                )
            }
            
            // Create scalar tensors for Adam parameters
            val beta1Data = when (param.dtype) {
                EmberDType.FLOAT32 -> FloatArray(1) { beta1 }
                EmberDType.FLOAT64 -> DoubleArray(1) { beta1.toDouble() }
                else -> throw IllegalArgumentException("Adam only supports floating point types")
            }
            
            val beta1BackendTensor = backend.createTensor(beta1Data, intArrayOf(1), param.dtype)
            val beta1Tensor = EmberTensor(
                shape = EmberShape.of(1),
                dtype = param.dtype,
                device = param.device,
                requiresGrad = false,
                backendTensor = beta1BackendTensor
            )
            
            val beta2Data = when (param.dtype) {
                EmberDType.FLOAT32 -> FloatArray(1) { beta2 }
                EmberDType.FLOAT64 -> DoubleArray(1) { beta2.toDouble() }
                else -> throw IllegalArgumentException("Adam only supports floating point types")
            }
            
            val beta2BackendTensor = backend.createTensor(beta2Data, intArrayOf(1), param.dtype)
            val beta2Tensor = EmberTensor(
                shape = EmberShape.of(1),
                dtype = param.dtype,
                device = param.device,
                requiresGrad = false,
                backendTensor = beta2BackendTensor
            )
            
            // Update biased moment and velocity estimates
            // moment = beta1 * moment + (1 - beta1) * grad
            val oneMinusBeta1Data = when (param.dtype) {
                EmberDType.FLOAT32 -> FloatArray(1) { 1.0f - beta1 }
                EmberDType.FLOAT64 -> DoubleArray(1) { 1.0 - beta1.toDouble() }
                else -> throw IllegalArgumentException("Adam only supports floating point types")
            }
            
            val oneMinusBeta1BackendTensor = backend.createTensor(oneMinusBeta1Data, intArrayOf(1), param.dtype)
            val oneMinusBeta1Tensor = EmberTensor(
                shape = EmberShape.of(1),
                dtype = param.dtype,
                device = param.device,
                requiresGrad = false,
                backendTensor = oneMinusBeta1BackendTensor
            )
            
            val newMoment = moment * beta1Tensor + effectiveGrad * oneMinusBeta1Tensor
            momentEstimates[name] = newMoment
            
            // velocity = beta2 * velocity + (1 - beta2) * grad^2
            val oneMinusBeta2Data = when (param.dtype) {
                EmberDType.FLOAT32 -> FloatArray(1) { 1.0f - beta2 }
                EmberDType.FLOAT64 -> DoubleArray(1) { 1.0 - beta2.toDouble() }
                else -> throw IllegalArgumentException("Adam only supports floating point types")
            }
            
            val oneMinusBeta2BackendTensor = backend.createTensor(oneMinusBeta2Data, intArrayOf(1), param.dtype)
            val oneMinusBeta2Tensor = EmberTensor(
                shape = EmberShape.of(1),
                dtype = param.dtype,
                device = param.device,
                requiresGrad = false,
                backendTensor = oneMinusBeta2BackendTensor
            )
            
            val gradSquared = effectiveGrad * effectiveGrad
            val newVelocity = velocity * beta2Tensor + gradSquared * oneMinusBeta2Tensor
            velocityEstimates[name] = newVelocity
            
            // Note: In a full implementation, we would:
            // 1. Compute bias-corrected estimates
            // 2. Apply the Adam update formula
            // 3. Update parameters in-place
            // This is a simplified version showing the structure
        }
    }
    
    override suspend fun zeroGrad() {
        // In a full implementation, this would zero out accumulated gradients
        // For now, this is a placeholder
    }
}