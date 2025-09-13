package ai.solace.emberml.tensor.common

import ai.solace.emberml.tensor.interfaces.TensorInterface
import ai.solace.emberml.backend.BackendRegistry

/**
 * The main tensor class that users interact with.
 * This is a backend-agnostic tensor implementation that delegates operations to the current backend.
 *
 * @property shape The shape of the tensor.
 * @property dtype The data type of the tensor.
 * @property device The device where the tensor is stored.
 * @property requiresGrad Whether the tensor requires gradients.
 * @property backendTensor The backend-specific tensor implementation.
 */
class EmberTensor(
    override val shape: EmberShape,
    override val dtype: EmberDType,
    override val device: String = "cpu",
    override val requiresGrad: Boolean = false,
    internal val backendTensor: Any
) : TensorInterface {

    /**
     * Creates a tensor from a list of values.
     *
     * @param data The data to create the tensor from.
     * @param dtype The data type of the tensor.
     * @param device The device where the tensor is stored.
     * @param requiresGrad Whether the tensor requires gradients.
     */
    constructor(
        data: List<*>,
        dtype: EmberDType = float32,
        device: String = "cpu",
        requiresGrad: Boolean = false
    ) : this(
        shape = inferShape(data),
        dtype = dtype,
        device = device,
        requiresGrad = requiresGrad,
        backendTensor = createBackendTensor(data, dtype, device, requiresGrad)
    )

    /**
     * Creates a tensor from an array of values.
     *
     * @param data The data to create the tensor from.
     * @param dtype The data type of the tensor.
     * @param device The device where the tensor is stored.
     * @param requiresGrad Whether the tensor requires gradients.
     */
    constructor(
        data: Array<*>,
        dtype: EmberDType = float32,
        device: String = "cpu",
        requiresGrad: Boolean = false
    ) : this(
        data.toList(),
        dtype,
        device,
        requiresGrad
    )

    /**
     * Creates a tensor from a primitive array of values.
     *
     * @param data The data to create the tensor from.
     * @param dtype The data type of the tensor.
     * @param device The device where the tensor is stored.
     * @param requiresGrad Whether the tensor requires gradients.
     */
    constructor(
        data: IntArray,
        dtype: EmberDType = int32,
        device: String = "cpu",
        requiresGrad: Boolean = false
    ) : this(
        data.toList(),
        dtype,
        device,
        requiresGrad
    )

    /**
     * Creates a tensor from a primitive array of values.
     *
     * @param data The data to create the tensor from.
     * @param dtype The data type of the tensor.
     * @param device The device where the tensor is stored.
     * @param requiresGrad Whether the tensor requires gradients.
     */
    constructor(
        data: FloatArray,
        dtype: EmberDType = float32,
        device: String = "cpu",
        requiresGrad: Boolean = false
    ) : this(
        data.toList(),
        dtype,
        device,
        requiresGrad
    )

    /**
     * Creates a tensor from a primitive array of values.
     *
     * @param data The data to create the tensor from.
     * @param dtype The data type of the tensor.
     * @param device The device where the tensor is stored.
     * @param requiresGrad Whether the tensor requires gradients.
     */
    constructor(
        data: DoubleArray,
        dtype: EmberDType = float64,
        device: String = "cpu",
        requiresGrad: Boolean = false
    ) : this(
        data.toList(),
        dtype,
        device,
        requiresGrad
    )

    /**
     * Creates a tensor from a primitive array of values.
     *
     * @param data The data to create the tensor from.
     * @param dtype The data type of the tensor.
     * @param device The device where the tensor is stored.
     * @param requiresGrad Whether the tensor requires gradients.
     */
    constructor(
        data: BooleanArray,
        dtype: EmberDType = bool,
        device: String = "cpu",
        requiresGrad: Boolean = false
    ) : this(
        data.toList(),
        dtype,
        device,
        requiresGrad
    )

    /**
     * Casts the tensor to a different data type.
     *
     * @param dtype The target data type.
     * @return A new tensor with the same data but different data type.
     */
    override fun cast(dtype: EmberDType): TensorInterface {
        // Delegate to the backend implementation
        val backend = BackendRegistry.getCurrentBackend()
        val newBackendTensor = backend.cast(this.backendTensor, dtype)

        return EmberTensor(
            shape = this.shape,
            dtype = dtype,
            device = this.device,
            requiresGrad = this.requiresGrad,
            backendTensor = newBackendTensor
        )
    }

    /**
     * Reshapes the tensor to a new shape.
     *
     * @param newShape The new shape.
     * @return A new tensor with the same data but different shape.
     */
    override fun reshape(newShape: EmberShape): TensorInterface {
        // Delegate to the backend implementation
        val backend = BackendRegistry.getCurrentBackend()
        val newBackendTensor = backend.reshape(this.backendTensor, newShape.dimensions)

        return EmberTensor(
            shape = newShape,
            dtype = this.dtype,
            device = this.device,
            requiresGrad = this.requiresGrad,
            backendTensor = newBackendTensor
        )
    }

    /**
     * Transposes the tensor.
     *
     * @param axes The permutation of the dimensions. If null, reverses the dimensions.
     * @return A new tensor with the dimensions permuted.
     */
    override fun transpose(axes: IntArray?): TensorInterface {
        // Delegate to the backend implementation
        val backend = BackendRegistry.getCurrentBackend()
        val newBackendTensor = backend.transpose(this.backendTensor, axes)

        // Get the new shape from the backend
        val newShapeDimensions = backend.getTensorShape(newBackendTensor)
        val newShape = EmberShape(newShapeDimensions)

        return EmberTensor(
            shape = newShape,
            dtype = this.dtype,
            device = this.device,
            requiresGrad = this.requiresGrad,
            backendTensor = newBackendTensor
        )
    }

    /**
     * Converts the tensor to a string representation.
     *
     * @return A string representation of the tensor.
     */
    override fun toString(): String {
        return "EmberTensor(shape=$shape, dtype=$dtype, device=$device, requiresGrad=$requiresGrad)"
    }

    /**
     * Adds another tensor to this tensor.
     *
     * @param other The tensor to add.
     * @return The result of the addition.
     */
    operator fun plus(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.add(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Subtracts another tensor from this tensor.
     *
     * @param other The tensor to subtract.
     * @return The result of the subtraction.
     */
    operator fun minus(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.subtract(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Multiplies this tensor by another tensor.
     *
     * @param other The tensor to multiply by.
     * @return The result of the multiplication.
     */
    operator fun times(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.multiply(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Divides this tensor by another tensor.
     *
     * @param other The tensor to divide by.
     * @return The result of the division.
     */
    operator fun div(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.divide(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Performs matrix multiplication of this tensor with another tensor.
     *
     * @param other The tensor to multiply with.
     * @return The result of the matrix multiplication.
     */
    fun matmul(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.matmul(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    // ===== BITWISE OPERATIONS =====

    /**
     * Shift the bits of this tensor to the left by shifts positions.
     *
     * @param shifts Number of bits to shift (integer or tensor).
     * @return New tensor with bits shifted left.
     */
    fun leftShift(shifts: Int): EmberTensor = leftShift(EmberTensor(intArrayOf(shifts)))

    fun leftShift(shifts: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.leftShift(this.backendTensor, shifts.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Shift the bits of this tensor to the right by shifts positions.
     *
     * @param shifts Number of bits to shift (integer or tensor).
     * @return New tensor with bits shifted right.
     */
    fun rightShift(shifts: Int): EmberTensor = rightShift(EmberTensor(intArrayOf(shifts)))

    fun rightShift(shifts: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.rightShift(this.backendTensor, shifts.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Rotate the bits of this tensor to the left by shifts positions.
     *
     * @param shifts Number of bits to rotate.
     * @param bitWidth The bit width of the integer type.
     * @return New tensor with bits rotated left.
     */
    fun rotateLeft(shifts: Int, bitWidth: Int = 32): EmberTensor = rotateLeft(EmberTensor(intArrayOf(shifts)), bitWidth)

    fun rotateLeft(shifts: EmberTensor, bitWidth: Int = 32): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.rotateLeft(this.backendTensor, shifts.backendTensor, bitWidth)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Rotate the bits of this tensor to the right by shifts positions.
     *
     * @param shifts Number of bits to rotate.
     * @param bitWidth The bit width of the integer type.
     * @return New tensor with bits rotated right.
     */
    fun rotateRight(shifts: Int, bitWidth: Int = 32): EmberTensor = rotateRight(EmberTensor(intArrayOf(shifts)), bitWidth)

    fun rotateRight(shifts: EmberTensor, bitWidth: Int = 32): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.rotateRight(this.backendTensor, shifts.backendTensor, bitWidth)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Count the number of set bits (1s) in each element.
     *
     * @return New tensor with bit counts.
     */
    fun countOnes(): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.countOnes(this.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = false, // Bit count operations don't require gradients
            backendTensor = resultTensor
        )
    }

    /**
     * Count the number of unset bits (0s) in each element.
     *
     * @return New tensor with zero bit counts.
     */
    fun countZeros(): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.countZeros(this.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = false,
            backendTensor = resultTensor
        )
    }

    /**
     * Get the bit at the specified position in each element.
     *
     * @param position Bit position (0-based, LSB).
     * @return New tensor with bit values (0 or 1).
     */
    fun getBit(position: Int): EmberTensor = getBit(EmberTensor(intArrayOf(position)))

    fun getBit(position: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.getBit(this.backendTensor, position.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = false,
            backendTensor = resultTensor
        )
    }

    /**
     * Set the bit at the specified position to the given value.
     *
     * @param position Bit position (0-based, LSB).
     * @param value Bit value (0 or 1).
     * @return New tensor with the bit set.
     */
    fun setBit(position: Int, value: Int): EmberTensor = setBit(EmberTensor(intArrayOf(position)), EmberTensor(intArrayOf(value)))

    fun setBit(position: EmberTensor, value: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.setBit(this.backendTensor, position.backendTensor, value.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Toggle the bit at the specified position.
     *
     * @param position Bit position (0-based, LSB).
     * @return New tensor with the bit toggled.
     */
    fun toggleBit(position: Int): EmberTensor = toggleBit(EmberTensor(intArrayOf(position)))

    fun toggleBit(position: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.toggleBit(this.backendTensor, position.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Compute the bitwise AND with another tensor.
     *
     * @param other The other tensor.
     * @return New tensor with element-wise bitwise AND.
     */
    infix fun bitwiseAnd(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.bitwiseAnd(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Compute the bitwise OR with another tensor.
     *
     * @param other The other tensor.
     * @return New tensor with element-wise bitwise OR.
     */
    infix fun bitwiseOr(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.bitwiseOr(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Compute the bitwise XOR with another tensor.
     *
     * @param other The other tensor.
     * @return New tensor with element-wise bitwise XOR.
     */
    infix fun bitwiseXor(other: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.bitwiseXor(this.backendTensor, other.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad || other.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Compute the bitwise NOT (inversion).
     *
     * @return New tensor with element-wise bitwise NOT.
     */
    fun bitwiseNot(): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.bitwiseNot(this.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    /**
     * Propagate this binary wave by shifting its bits.
     *
     * @param shift Number of positions to shift (positive = left, negative = right).
     * @return New tensor representing the propagated wave pattern.
     */
    fun propagate(shift: Int): EmberTensor = propagate(EmberTensor(intArrayOf(shift)))

    fun propagate(shift: EmberTensor): EmberTensor {
        val backend = BackendRegistry.getCurrentBackend()
        val resultTensor = backend.binaryWavePropagate(this.backendTensor, shift.backendTensor)
        val resultShape = EmberShape(backend.getTensorShape(resultTensor))
        val resultDType = backend.getTensorDType(resultTensor)
        val resultDevice = backend.getTensorDevice(resultTensor)

        return EmberTensor(
            shape = resultShape,
            dtype = resultDType,
            device = resultDevice,
            requiresGrad = this.requiresGrad,
            backendTensor = resultTensor
        )
    }

    companion object {
        /**
         * Infers the shape of a tensor from a list of values.
         *
         * @param data The data to infer the shape from.
         * @return The inferred shape.
         */
        private fun inferShape(data: List<*>): EmberShape {
            val dimensions = mutableListOf<Int>()
            var current: Any? = data

            while (current is List<*> && current.isNotEmpty()) {
                dimensions.add(current.size)
                current = current.firstOrNull()
            }

            return EmberShape(dimensions.toIntArray())
        }

        /**
         * Creates a backend-specific tensor from a list of values.
         *
         * @param data The data to create the tensor from.
         * @param dtype The data type of the tensor.
         * @param device The device where the tensor is stored.
         * @param requiresGrad Whether the tensor requires gradients.
         * @return The backend-specific tensor.
         */
        private fun createBackendTensor(
            data: List<*>,
            dtype: EmberDType,
            device: String,
            requiresGrad: Boolean
        ): Any {
            // Delegate to the current backend's tensor creation function
            val backend = BackendRegistry.getCurrentBackend()
            val shape = inferShape(data).dimensions
            return backend.createTensor(data, shape, dtype)
        }

        // ===== BITWISE OPERATIONS FACTORY METHODS =====

        /**
         * Apply wave interference between multiple binary patterns element-wise.
         *
         * @param waves List of input tensors (must be integer type).
         * @param mode Interference type ('xor', 'and', or 'or'). Defaults to 'xor'.
         * @return Tensor representing the interference pattern.
         */
        fun binaryWaveInterference(waves: List<EmberTensor>, mode: String = "xor"): EmberTensor {
            if (waves.isEmpty()) {
                throw IllegalArgumentException("Waves list cannot be empty")
            }

            val backend = BackendRegistry.getCurrentBackend()
            val backendWaves = waves.map { it.backendTensor }
            val resultTensor = backend.binaryWaveInterference(backendWaves, mode)
            val resultShape = EmberShape(backend.getTensorShape(resultTensor))
            val resultDType = backend.getTensorDType(resultTensor)
            val resultDevice = backend.getTensorDevice(resultTensor)

            return EmberTensor(
                shape = resultShape,
                dtype = resultDType,
                device = resultDevice,
                requiresGrad = waves.any { it.requiresGrad },
                backendTensor = resultTensor
            )
        }

        /**
         * Create a binary pattern tensor with a specified duty cycle.
         *
         * @param length The length of the binary pattern (number of bits).
         * @param dutyCycle The fraction of bits that should be 1 (between 0.0 and 1.0).
         * @param dtype The data type of the tensor (must be integer type).
         * @param device The device where the tensor is stored.
         * @return Tensor representing the binary pattern.
         */
        fun createDutyCycle(
            length: Int,
            dutyCycle: Float,
            dtype: EmberDType = int32,
            device: String = "cpu"
        ): EmberTensor {
            val backend = BackendRegistry.getCurrentBackend()
            val resultTensor = backend.createDutyCycle(length, dutyCycle, dtype)
            val resultShape = EmberShape(backend.getTensorShape(resultTensor))
            val resultDType = backend.getTensorDType(resultTensor)
            val resultDevice = backend.getTensorDevice(resultTensor)

            return EmberTensor(
                shape = resultShape,
                dtype = resultDType,
                device = resultDevice,
                requiresGrad = false,
                backendTensor = resultTensor
            )
        }

        /**
         * Generate a blocky sine wave pattern (square wave).
         *
         * @param length The length of the binary pattern (number of bits).
         * @param halfPeriod Half the period of the wave in bits.
         * @param dtype The data type of the tensor (must be integer type).
         * @param device The device where the tensor is stored.
         * @return Tensor representing the blocky sine wave pattern.
         */
        fun generateBlockySin(
            length: Int,
            halfPeriod: Int,
            dtype: EmberDType = int32,
            device: String = "cpu"
        ): EmberTensor {
            val backend = BackendRegistry.getCurrentBackend()
            val resultTensor = backend.generateBlockySin(length, halfPeriod, dtype)
            val resultShape = EmberShape(backend.getTensorShape(resultTensor))
            val resultDType = backend.getTensorDType(resultTensor)
            val resultDevice = backend.getTensorDevice(resultTensor)

            return EmberTensor(
                shape = resultShape,
                dtype = resultDType,
                device = resultDevice,
                requiresGrad = false,
                backendTensor = resultTensor
            )
        }
    }
}
