package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType

/**
 * Interface for all backend implementations.
 * This is the core interface that all backends must implement.
 */
interface Backend {
    /**
     * Creates a tensor from the given data.
     *
     * @param data The data to create the tensor from.
     * @param shape The shape of the tensor.
     * @param dtype The data type of the tensor.
     * @return The backend-specific tensor.
     */
    fun createTensor(data: Any, shape: IntArray, dtype: DType): Any

    /**
     * Gets the shape of a tensor.
     *
     * @param tensor The backend-specific tensor.
     * @return The shape of the tensor as an IntArray.
     */
    fun getTensorShape(tensor: Any): IntArray

    /**
     * Gets the data type of a tensor.
     *
     * @param tensor The backend-specific tensor.
     * @return The data type of the tensor.
     */
    fun getTensorDType(tensor: Any): DType

    /**
     * Gets the device where a tensor is stored.
     *
     * @param tensor The backend-specific tensor.
     * @return The device where the tensor is stored.
     */
    fun getTensorDevice(tensor: Any): String

    /**
     * Adds two tensors.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the addition.
     */
    fun add(a: Any, b: Any): Any

    /**
     * Subtracts one tensor from another.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the subtraction.
     */
    fun subtract(a: Any, b: Any): Any

    /**
     * Multiplies two tensors.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the multiplication.
     */
    fun multiply(a: Any, b: Any): Any

    /**
     * Divides one tensor by another.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the division.
     */
    fun divide(a: Any, b: Any): Any

    /**
     * Performs matrix multiplication of two tensors.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the matrix multiplication.
     */
    fun matmul(a: Any, b: Any): Any

    /**
     * Casts a tensor to a different data type.
     *
     * @param tensor The tensor to cast.
     * @param dtype The target data type.
     * @return The tensor with the new data type.
     */
    fun cast(tensor: Any, dtype: DType): Any

    /**
     * Reshapes a tensor to a new shape.
     *
     * @param tensor The tensor to reshape.
     * @param newShape The new shape.
     * @return The reshaped tensor.
     */
    fun reshape(tensor: Any, newShape: IntArray): Any

    /**
     * Transposes a tensor.
     *
     * @param tensor The tensor to transpose.
     * @param axes The permutation of the dimensions. If null, reverses the dimensions.
     * @return The transposed tensor.
     */
    fun transpose(tensor: Any, axes: IntArray? = null): Any

    /**
     * Moves a tensor to a different device.
     *
     * @param tensor The tensor to move.
     * @param device The target device.
     * @return The tensor on the new device.
     */
    fun toDevice(tensor: Any, device: String): Any

    /**
     * Gets a list of available devices.
     *
     * @return A list of available devices.
     */
    fun getAvailableDevices(): List<String>

    /**
     * Sets the default device for tensor operations.
     *
     * @param device The device to use as the default.
     */
    fun setDefaultDevice(device: String)

    /**
     * Gets the default device for tensor operations.
     *
     * @return The default device.
     */
    fun getDefaultDevice(): String

    // ===== BITWISE OPERATIONS =====

    /**
     * Shift the bits of x to the left by shifts positions.
     *
     * @param x Input tensor (must be integer type).
     * @param shifts Number of bits to shift (integer or tensor).
     * @return Tensor with x shifted left by shifts bits.
     */
    fun leftShift(x: Any, shifts: Any): Any

    /**
     * Shift the bits of x to the right by shifts positions.
     *
     * @param x Input tensor (must be integer type).
     * @param shifts Number of bits to shift (integer or tensor).
     * @return Tensor with x shifted right by shifts bits.
     */
    fun rightShift(x: Any, shifts: Any): Any

    /**
     * Rotate the bits of x to the left by shifts positions.
     *
     * @param x Input tensor (must be unsigned integer type).
     * @param shifts Number of bits to rotate (integer or tensor).
     * @param bitWidth The bit width of the integer type (e.g., 8, 16, 32, 64).
     * @return Tensor with x rotated left by shifts bits.
     */
    fun rotateLeft(x: Any, shifts: Any, bitWidth: Int = 32): Any

    /**
     * Rotate the bits of x to the right by shifts positions.
     *
     * @param x Input tensor (must be unsigned integer type).
     * @param shifts Number of bits to rotate (integer or tensor).
     * @param bitWidth The bit width of the integer type (e.g., 8, 16, 32, 64).
     * @return Tensor with x rotated right by shifts bits.
     */
    fun rotateRight(x: Any, shifts: Any, bitWidth: Int = 32): Any

    /**
     * Count the number of set bits (1s) in each element of x.
     *
     * @param x Input tensor (must be integer type).
     * @return Tensor with the count of set bits for each element.
     */
    fun countOnes(x: Any): Any

    /**
     * Count the number of unset bits (0s) in each element of x.
     *
     * @param x Input tensor (must be integer type).
     * @return Tensor with the count of unset bits for each element.
     */
    fun countZeros(x: Any): Any

    /**
     * Get the bit at the specified position in each element of x.
     *
     * @param x Input tensor (must be integer type).
     * @param position Bit position(s) (0-based, LSB). Integer or tensor.
     * @return Tensor with the bit value (0 or 1) at the specified position(s).
     */
    fun getBit(x: Any, position: Any): Any

    /**
     * Set the bit at the specified position in each element of x to value (0 or 1).
     *
     * @param x Input tensor (must be integer type).
     * @param position Bit position(s) (0-based, LSB). Integer or tensor.
     * @param value Bit value(s) (0 or 1). Integer or tensor.
     * @return Tensor with the bit at the specified position(s) set.
     */
    fun setBit(x: Any, position: Any, value: Any): Any

    /**
     * Toggle the bit at the specified position in each element of x.
     *
     * @param x Input tensor (must be integer type).
     * @param position Bit position(s) (0-based, LSB). Integer or tensor.
     * @return Tensor with the bit at the specified position(s) toggled.
     */
    fun toggleBit(x: Any, position: Any): Any

    /**
     * Compute the bitwise AND of x and y element-wise.
     *
     * @param x First input tensor.
     * @param y Second input tensor.
     * @return Tensor with the element-wise bitwise AND.
     */
    fun bitwiseAnd(x: Any, y: Any): Any

    /**
     * Compute the bitwise OR of x and y element-wise.
     *
     * @param x First input tensor.
     * @param y Second input tensor.
     * @return Tensor with the element-wise bitwise OR.
     */
    fun bitwiseOr(x: Any, y: Any): Any

    /**
     * Compute the bitwise XOR of x and y element-wise.
     *
     * @param x First input tensor.
     * @param y Second input tensor.
     * @return Tensor with the element-wise bitwise XOR.
     */
    fun bitwiseXor(x: Any, y: Any): Any

    /**
     * Compute the bitwise NOT (inversion) of x element-wise.
     *
     * @param x Input tensor.
     * @return Tensor with the element-wise bitwise NOT.
     */
    fun bitwiseNot(x: Any): Any

    /**
     * Apply wave interference between multiple binary patterns element-wise.
     *
     * @param waves List of input tensors (must be integer type).
     * @param mode Interference type ('xor', 'and', or 'or'). Defaults to 'xor'.
     * @return Tensor representing the interference pattern.
     */
    fun binaryWaveInterference(waves: List<Any>, mode: String = "xor"): Any

    /**
     * Propagate a binary wave by shifting its bits.
     *
     * @param wave Input tensor (must be integer type).
     * @param shift Number of positions to shift (integer or tensor).
     * @return Tensor representing the propagated wave pattern.
     */
    fun binaryWavePropagate(wave: Any, shift: Any): Any

    /**
     * Create a binary pattern tensor with a specified duty cycle.
     *
     * @param length The length of the binary pattern (number of bits).
     * @param dutyCycle The fraction of bits that should be 1 (between 0.0 and 1.0).
     * @param dtype The data type of the tensor (must be integer type).
     * @return Tensor representing the binary pattern.
     */
    fun createDutyCycle(length: Int, dutyCycle: Float, dtype: DType): Any

    /**
     * Generate a blocky sine wave pattern (square wave).
     *
     * @param length The length of the binary pattern (number of bits).
     * @param halfPeriod Half the period of the wave in bits.
     * @param dtype The data type of the tensor (must be integer type).
     * @return Tensor representing the blocky sine wave pattern.
     */
    fun generateBlockySin(length: Int, halfPeriod: Int, dtype: DType): Any
}