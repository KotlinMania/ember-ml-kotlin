package ai.solace.ember.backend.metal

import ai.solace.ember.backend.Backend
import ai.solace.ember.tensor.common.DType

/**
 * Metal backend implementation for GPU-accelerated operations on Apple platforms.
 * 
 * This backend provides hardware acceleration through Metal kernels and is designed
 * to work with Kotlin Native on macOS and iOS platforms.
 */
class MetalBackend : Backend {
    
    private val metalContext: MetalContext? = createMetalContext()
    
    /**
     * Gets the name of this backend.
     */
    fun name(): String = "metal"
    
    /**
     * Checks if Metal is available on this platform.
     */
    fun isAvailable(): Boolean = metalContext != null
    
    /**
     * Gets the priority of this backend (higher values have higher priority).
     */
    fun priority(): Int = 200  // Higher priority than CPU backends
    
    override fun createTensor(data: Any, shape: IntArray, dtype: DType): Any {
        requireMetalAvailable()
        return MetalTensor.create(data, shape, dtype, metalContext!!)
    }
    
    override fun getTensorShape(tensor: Any): IntArray {
        return (tensor as MetalTensor).shape
    }
    
    override fun getTensorDType(tensor: Any): DType {
        return (tensor as MetalTensor).dtype
    }
    
    override fun getTensorDevice(tensor: Any): String {
        return "metal"
    }
    
    override fun add(a: Any, b: Any): Any {
        requireMetalAvailable()
        return MetalOperations.add(a as MetalTensor, b as MetalTensor, metalContext!!)
    }
    
    override fun subtract(a: Any, b: Any): Any {
        requireMetalAvailable()
        return MetalOperations.subtract(a as MetalTensor, b as MetalTensor, metalContext!!)
    }
    
    override fun multiply(a: Any, b: Any): Any {
        requireMetalAvailable()
        return MetalOperations.multiply(a as MetalTensor, b as MetalTensor, metalContext!!)
    }
    
    override fun divide(a: Any, b: Any): Any {
        requireMetalAvailable()
        return MetalOperations.divide(a as MetalTensor, b as MetalTensor, metalContext!!)
    }
    
    override fun matmul(a: Any, b: Any): Any {
        requireMetalAvailable()
        return MetalOperations.matmul(a as MetalTensor, b as MetalTensor, metalContext!!)
    }
    
    override fun cast(tensor: Any, dtype: DType): Any {
        requireMetalAvailable()
        return MetalOperations.cast(tensor as MetalTensor, dtype, metalContext!!)
    }
    
    override fun reshape(tensor: Any, newShape: IntArray): Any {
        return MetalOperations.reshape(tensor as MetalTensor, newShape)
    }
    
    override fun transpose(tensor: Any, axes: IntArray?): Any {
        requireMetalAvailable()
        return MetalOperations.transpose(tensor as MetalTensor, axes, metalContext!!)
    }
    
    override fun toDevice(tensor: Any, device: String): Any {
        if (device == "metal") {
            return tensor
        }
        throw UnsupportedOperationException("Cannot move Metal tensor to device: $device")
    }
    
    override fun getAvailableDevices(): List<String> {
        return if (isAvailable()) listOf("metal") else emptyList()
    }
    
    override fun setDefaultDevice(device: String) {
        if (device != "metal") {
            throw UnsupportedOperationException("Metal backend only supports 'metal' device")
        }
    }
    
    override fun getDefaultDevice(): String = "metal"
    
    /**
     * Performs SVD decomposition using Metal kernels.
     */
    fun svd(matrix: Any, fullMatrices: Boolean = true, computeUv: Boolean = true): Array<Any> {
        requireMetalAvailable()
        return MetalLinearAlgebra.svd(matrix as MetalTensor, fullMatrices, computeUv, metalContext!!)
    }
    
    private fun requireMetalAvailable() {
        if (metalContext == null) {
            throw IllegalStateException("Metal is not available on this platform")
        }
    }
    
    // Bitwise operations - implementations using Metal kernels
    override fun leftShift(x: Any, shifts: Any): Any {
        requireMetalAvailable()
        return MetalOperations.leftShift(x, shifts)
    }
    
    override fun rightShift(x: Any, shifts: Any): Any {
        requireMetalAvailable()
        return MetalOperations.rightShift(x, shifts)
    }
    
    override fun rotateLeft(x: Any, shifts: Any, bitWidth: Int): Any {
        requireMetalAvailable()
        return MetalOperations.rotateLeft(x, shifts, bitWidth)
    }
    
    override fun rotateRight(x: Any, shifts: Any, bitWidth: Int): Any {
        requireMetalAvailable()
        return MetalOperations.rotateRight(x, shifts, bitWidth)
    }
    
    override fun countOnes(x: Any): Any {
        requireMetalAvailable()
        return MetalOperations.countOnes(x)
    }
    
    override fun countZeros(x: Any): Any {
        requireMetalAvailable()
        return MetalOperations.countZeros(x)
    }
    
    override fun getBit(x: Any, position: Any): Any {
        requireMetalAvailable()
        return MetalOperations.getBit(x, position)
    }
    
    override fun setBit(x: Any, position: Any, value: Any): Any {
        requireMetalAvailable()
        return MetalOperations.setBit(x, position, value)
    }
    
    override fun toggleBit(x: Any, position: Any): Any {
        requireMetalAvailable()
        return MetalOperations.toggleBit(x, position)
    }
    
    override fun bitwiseAnd(x: Any, y: Any): Any {
        requireMetalAvailable()
        return MetalOperations.bitwiseAnd(x, y)
    }
    
    override fun bitwiseOr(x: Any, y: Any): Any {
        requireMetalAvailable()
        return MetalOperations.bitwiseOr(x, y)
    }
    
    override fun bitwiseXor(x: Any, y: Any): Any {
        requireMetalAvailable()
        return MetalOperations.bitwiseXor(x, y)
    }
    
    override fun bitwiseNot(x: Any): Any {
        requireMetalAvailable()
        return MetalOperations.bitwiseNot(x)
    }
    
    override fun binaryWaveInterference(waves: List<Any>, mode: String): Any {
        requireMetalAvailable()
        return MetalOperations.binaryWaveInterference(waves, mode)
    }
    
    override fun binaryWavePropagate(wave: Any, shift: Any): Any {
        requireMetalAvailable()
        return MetalOperations.binaryWavePropagate(wave, shift)
    }
    
    override fun createDutyCycle(length: Int, dutyCycle: Float, dtype: DType): Any {
        requireMetalAvailable()
        return MetalOperations.createDutyCycle(length, dutyCycle, dtype)
    }
    
    override fun generateBlockySin(length: Int, halfPeriod: Int, dtype: DType): Any {
        requireMetalAvailable()
        return MetalOperations.generateBlockySin(length, halfPeriod, dtype)
    }
    
    companion object {
        /**
         * Creates a Metal context if available on the platform.
         */
        private fun createMetalContext(): MetalContext? {
            return try {
                MetalContext.create()
            } catch (e: Exception) {
                null
            }
        }
    }
}