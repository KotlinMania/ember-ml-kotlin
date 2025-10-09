package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.tensor.bitwise.MegaNumber
import ai.solace.ember.tensor.bitwise.MegaFloat
import ai.solace.ember.tensor.bitwise.MegaInteger
import ai.solace.ember.tensor.bitwise.MegaBinary
import ai.solace.ember.tensor.bitwise.DefaultConversionOperations

/**
 * A backend implementation that uses the MegaNumber system for tensor operations.
 * This backend provides high-precision arithmetic operations using the MegaNumber system,
 * which is particularly useful for platforms that don't natively support Float64 operations.
 */
class MegaTensorBackend : Backend {
    // The default device for tensor operations
    private var defaultDevice: String = "cpu"

    /**
     * A tensor implementation using the MegaNumber system.
     * This class wraps a multi-dimensional array of MegaNumber objects.
     *
     * @property data The multi-dimensional array of MegaNumber objects.
     * @property shape The shape of the tensor.
     * @property dtype The data type of the tensor.
     * @property device The device where the tensor is stored.
     */
    data class MegaTensor(
        val data: Array<*>,
        val shape: IntArray,
        val dtype: DType,
        val device: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MegaTensor) return false

            if (!data.contentDeepEquals(other.data)) return false
            if (!shape.contentEquals(other.shape)) return false
            if (dtype != other.dtype) return false
            if (device != other.device) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentDeepHashCode()
            result = 31 * result + shape.contentHashCode()
            result = 31 * result + dtype.hashCode()
            result = 31 * result + device.hashCode()
            return result
        }
    }

    /**
     * Creates a tensor from the given data.
     *
     * @param data The data to create the tensor from.
     * @param shape The shape of the tensor.
     * @param dtype The data type of the tensor.
     * @return The backend-specific tensor.
     */
    override fun createTensor(data: Any, shape: IntArray, dtype: DType): Any {
        // Convert the data to a multi-dimensional array of MegaNumber objects
        val megaData = when (data) {
            is List<*> -> convertListToMegaNumbers(data, dtype)
            is Array<*> -> convertArrayToMegaNumbers(data, dtype)
            is IntArray -> convertIntArrayToMegaNumbers(data, dtype)
            is FloatArray -> convertFloatArrayToMegaNumbers(data, dtype)
            is DoubleArray -> convertDoubleArrayToMegaNumbers(data, dtype)
            is BooleanArray -> convertBooleanArrayToMegaNumbers(data, dtype)
            else -> throw IllegalArgumentException("Unsupported data type: ${data::class.simpleName}")
        }

        return MegaTensor(megaData, shape, dtype, defaultDevice)
    }

    /**
     * Gets the shape of a tensor.
     *
     * @param tensor The backend-specific tensor.
     * @return The shape of the tensor as an IntArray.
     */
    override fun getTensorShape(tensor: Any): IntArray {
        if (tensor !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${tensor::class.simpleName}")
        }
        return tensor.shape
    }

    /**
     * Gets the data type of a tensor.
     *
     * @param tensor The backend-specific tensor.
     * @return The data type of the tensor.
     */
    override fun getTensorDType(tensor: Any): DType {
        if (tensor !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${tensor::class.simpleName}")
        }
        return tensor.dtype
    }

    /**
     * Gets the device where a tensor is stored.
     *
     * @param tensor The backend-specific tensor.
     * @return The device where the tensor is stored.
     */
    override fun getTensorDevice(tensor: Any): String {
        if (tensor !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${tensor::class.simpleName}")
        }
        return tensor.device
    }

    /**
     * Adds two tensors.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the addition.
     */
    override fun add(a: Any, b: Any): Any {
        if (a !is MegaTensor || b !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${a::class.simpleName} and ${b::class.simpleName}")
        }

        // For now, we'll just implement element-wise addition for tensors of the same shape
        if (!a.shape.contentEquals(b.shape)) {
            throw IllegalArgumentException("Tensors must have the same shape for addition")
        }

        // Create a new tensor to hold the result
        val resultData = addArrays(a.data, b.data)
        
        // Determine the result dtype (promote to higher precision if needed)
        val resultDType = promoteTypes(a.dtype, b.dtype)
        
        return MegaTensor(resultData, a.shape, resultDType, a.device)
    }

    /**
     * Subtracts one tensor from another.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the subtraction.
     */
    override fun subtract(a: Any, b: Any): Any {
        if (a !is MegaTensor || b !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${a::class.simpleName} and ${b::class.simpleName}")
        }

        // For now, we'll just implement element-wise subtraction for tensors of the same shape
        if (!a.shape.contentEquals(b.shape)) {
            throw IllegalArgumentException("Tensors must have the same shape for subtraction")
        }

        // Create a new tensor to hold the result
        val resultData = subtractArrays(a.data, b.data)
        
        // Determine the result dtype (promote to higher precision if needed)
        val resultDType = promoteTypes(a.dtype, b.dtype)
        
        return MegaTensor(resultData, a.shape, resultDType, a.device)
    }

    /**
     * Multiplies two tensors.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the multiplication.
     */
    override fun multiply(a: Any, b: Any): Any {
        if (a !is MegaTensor || b !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${a::class.simpleName} and ${b::class.simpleName}")
        }

        // For now, we'll just implement element-wise multiplication for tensors of the same shape
        if (!a.shape.contentEquals(b.shape)) {
            throw IllegalArgumentException("Tensors must have the same shape for multiplication")
        }

        // Create a new tensor to hold the result
        val resultData = multiplyArrays(a.data, b.data)
        
        // Determine the result dtype (promote to higher precision if needed)
        val resultDType = promoteTypes(a.dtype, b.dtype)
        
        return MegaTensor(resultData, a.shape, resultDType, a.device)
    }

    /**
     * Divides one tensor by another.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the division.
     */
    override fun divide(a: Any, b: Any): Any {
        if (a !is MegaTensor || b !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${a::class.simpleName} and ${b::class.simpleName}")
        }

        // For now, we'll just implement element-wise division for tensors of the same shape
        if (!a.shape.contentEquals(b.shape)) {
            throw IllegalArgumentException("Tensors must have the same shape for division")
        }

        // Create a new tensor to hold the result
        val resultData = divideArrays(a.data, b.data)
        
        // Division always results in a float type
        val resultDType = DType.FLOAT64
        
        return MegaTensor(resultData, a.shape, resultDType, a.device)
    }

    /**
     * Performs matrix multiplication of two tensors.
     *
     * @param a The first tensor.
     * @param b The second tensor.
     * @return The result of the matrix multiplication.
     */
    override fun matmul(a: Any, b: Any): Any {
        if (a !is MegaTensor || b !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${a::class.simpleName} and ${b::class.simpleName}")
        }

        // Matrix multiplication requires specific shape constraints
        if (a.shape.size < 2 || b.shape.size < 2) {
            throw IllegalArgumentException("Tensors must have at least 2 dimensions for matrix multiplication")
        }
        
        if (a.shape.last() != b.shape[b.shape.size - 2]) {
            throw IllegalArgumentException("Incompatible shapes for matrix multiplication: ${a.shape.contentToString()} and ${b.shape.contentToString()}")
        }

        // For now, we'll just implement a placeholder for matrix multiplication
        // In a real implementation, this would perform the actual matrix multiplication
        
        // Determine the result shape
        val resultShape = IntArray(a.shape.size + b.shape.size - 2)
        for (i in 0 until a.shape.size - 1) {
            resultShape[i] = a.shape[i]
        }
        for (i in 0 until b.shape.size - 2) {
            resultShape[a.shape.size - 1 + i] = b.shape[i]
        }
        resultShape[resultShape.size - 1] = b.shape.last()
        
        // Determine the result dtype (promote to higher precision if needed)
        val resultDType = promoteTypes(a.dtype, b.dtype)
        
        // Create a placeholder result tensor
        // In a real implementation, this would contain the actual matrix multiplication result
        val resultData = Array<MegaNumber>(resultShape.fold(1) { acc, dim -> acc * dim }) { MegaNumber() }
        
        return MegaTensor(resultData, resultShape, resultDType, a.device)
    }

    /**
     * Casts a tensor to a different data type.
     *
     * @param tensor The tensor to cast.
     * @param dtype The target data type.
     * @return The tensor with the new data type.
     */
    override fun cast(tensor: Any, dtype: DType): Any {
        if (tensor !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${tensor::class.simpleName}")
        }

        // If the dtype is already the target dtype, return the tensor as is
        if (tensor.dtype == dtype) {
            return tensor
        }

        // Cast the data to the target dtype
        val castedData = castArray(tensor.data, dtype)
        
        return MegaTensor(castedData, tensor.shape, dtype, tensor.device)
    }

    /**
     * Reshapes a tensor to a new shape.
     *
     * @param tensor The tensor to reshape.
     * @param newShape The new shape.
     * @return The reshaped tensor.
     */
    override fun reshape(tensor: Any, newShape: IntArray): Any {
        if (tensor !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${tensor::class.simpleName}")
        }

        // Check if the new shape is compatible with the tensor's size
        val tensorSize = tensor.shape.fold(1) { acc, dim -> acc * dim }
        val newSize = newShape.fold(1) { acc, dim -> acc * dim }
        
        if (tensorSize != newSize) {
            throw IllegalArgumentException("Cannot reshape tensor of size $tensorSize to shape ${newShape.contentToString()} with size $newSize")
        }

        // For now, we'll just return a new tensor with the same data but different shape
        return MegaTensor(tensor.data, newShape, tensor.dtype, tensor.device)
    }

    /**
     * Transposes a tensor.
     *
     * @param tensor The tensor to transpose.
     * @param axes The permutation of the dimensions. If null, reverses the dimensions.
     * @return The transposed tensor.
     */
    override fun transpose(tensor: Any, axes: IntArray?): Any {
        if (tensor !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${tensor::class.simpleName}")
        }

        // Determine the permutation of dimensions
        val permutation = axes ?: IntArray(tensor.shape.size) { tensor.shape.size - 1 - it }
        
        // Check if the permutation is valid
        if (permutation.size != tensor.shape.size) {
            throw IllegalArgumentException("Permutation size ${permutation.size} does not match tensor dimension ${tensor.shape.size}")
        }
        
        // Determine the new shape after transposition
        val newShape = IntArray(tensor.shape.size) { tensor.shape[permutation[it]] }
        
        // For now, we'll just return a new tensor with the same data but transposed shape
        // In a real implementation, this would rearrange the data according to the permutation
        return MegaTensor(tensor.data, newShape, tensor.dtype, tensor.device)
    }

    /**
     * Moves a tensor to a different device.
     *
     * @param tensor The tensor to move.
     * @param device The target device.
     * @return The tensor on the new device.
     */
    override fun toDevice(tensor: Any, device: String): Any {
        if (tensor !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${tensor::class.simpleName}")
        }

        // If the tensor is already on the target device, return it as is
        if (tensor.device == device) {
            return tensor
        }

        // For now, we'll just return a new tensor with the same data but different device
        return MegaTensor(tensor.data, tensor.shape, tensor.dtype, device)
    }

    /**
     * Gets a list of available devices.
     *
     * @return A list of available devices.
     */
    override fun getAvailableDevices(): List<String> {
        // For now, we'll just return a list with the CPU device
        return listOf("cpu")
    }

    /**
     * Sets the default device for tensor operations.
     *
     * @param device The device to use as the default.
     */
    override fun setDefaultDevice(device: String) {
        // Check if the device is available
        if (device !in getAvailableDevices()) {
            throw IllegalArgumentException("Device $device is not available")
        }
        
        defaultDevice = device
    }

    /**
     * Gets the default device for tensor operations.
     *
     * @return The default device.
     */
    override fun getDefaultDevice(): String {
        return defaultDevice
    }

    // Helper methods for converting data to MegaNumber arrays

    private fun convertListToMegaNumbers(list: List<*>, dtype: DType): Array<*> {
        // Recursively convert nested lists to MegaNumber arrays
        if (list.isEmpty()) {
            return emptyArray<MegaNumber>()
        }
        
        return if (list[0] is List<*>) {
            Array(list.size) { i ->
                convertListToMegaNumbers(list[i] as List<*>, dtype)
            }
        } else {
            Array(list.size) { i ->
                convertValueToMegaNumber(list[i], dtype)
            }
        }
    }

    private fun convertArrayToMegaNumbers(array: Array<*>, dtype: DType): Array<*> {
        // Recursively convert nested arrays to MegaNumber arrays
        if (array.isEmpty()) {
            return emptyArray<MegaNumber>()
        }
        
        return if (array[0] is Array<*>) {
            Array(array.size) { i ->
                convertArrayToMegaNumbers(array[i] as Array<*>, dtype)
            }
        } else {
            Array(array.size) { i ->
                convertValueToMegaNumber(array[i], dtype)
            }
        }
    }

    private fun convertIntArrayToMegaNumbers(array: IntArray, dtype: DType): Array<MegaNumber> {
        return Array(array.size) { i ->
            convertValueToMegaNumber(array[i], dtype)
        }
    }

    private fun convertFloatArrayToMegaNumbers(array: FloatArray, dtype: DType): Array<MegaNumber> {
        return Array(array.size) { i ->
            convertValueToMegaNumber(array[i], dtype)
        }
    }

    private fun convertDoubleArrayToMegaNumbers(array: DoubleArray, dtype: DType): Array<MegaNumber> {
        return Array(array.size) { i ->
            convertValueToMegaNumber(array[i], dtype)
        }
    }

    private fun convertBooleanArrayToMegaNumbers(array: BooleanArray, dtype: DType): Array<MegaNumber> {
        return Array(array.size) { i ->
            convertValueToMegaNumber(if (array[i]) 1 else 0, dtype)
        }
    }

    private fun convertValueToMegaNumber(value: Any?, dtype: DType): MegaNumber {
        return when (value) {
            is Int -> when (dtype) {
                DType.FLOAT32, DType.FLOAT64 -> MegaFloat.fromValue(value.toDouble())
                else -> MegaInteger.fromValue(value)
            }
            is Float -> when (dtype) {
                DType.FLOAT32, DType.FLOAT64 -> MegaFloat.fromValue(value.toDouble())
                else -> MegaInteger.fromValue(value.toInt())
            }
            is Double -> when (dtype) {
                DType.FLOAT32, DType.FLOAT64 -> MegaFloat.fromValue(value)
                else -> MegaInteger.fromValue(value.toInt())
            }
            is Boolean -> when (dtype) {
                DType.BOOL -> MegaInteger.fromValue(if (value) 1 else 0)
                DType.FLOAT32, DType.FLOAT64 -> MegaFloat.fromValue(if (value) 1.0 else 0.0)
                else -> MegaInteger.fromValue(if (value) 1 else 0)
            }
            is String -> when (dtype) {
                DType.FLOAT32, DType.FLOAT64 -> MegaFloat.fromValue(value.toDouble())
                else -> MegaInteger.fromValue(value.toInt())
            }
            null -> MegaNumber() // Default to zero
            else -> throw IllegalArgumentException("Unsupported value type: ${value::class.simpleName}")
        }
    }

    // Helper methods for array operations

    private fun addArrays(a: Array<*>, b: Array<*>): Array<*> {
        // Recursively add nested arrays
        if (a.isEmpty() || b.isEmpty()) {
            return emptyArray<MegaNumber>()
        }
        
        return if (a[0] is Array<*> && b[0] is Array<*>) {
            Array(a.size) { i ->
                addArrays(a[i] as Array<*>, b[i] as Array<*>)
            }
        } else {
            Array(a.size) { i ->
                (a[i] as MegaNumber).add(b[i] as MegaNumber)
            }
        }
    }

    private fun subtractArrays(a: Array<*>, b: Array<*>): Array<*> {
        // Recursively subtract nested arrays
        if (a.isEmpty() || b.isEmpty()) {
            return emptyArray<MegaNumber>()
        }
        
        return if (a[0] is Array<*> && b[0] is Array<*>) {
            Array(a.size) { i ->
                subtractArrays(a[i] as Array<*>, b[i] as Array<*>)
            }
        } else {
            Array(a.size) { i ->
                (a[i] as MegaNumber).sub(b[i] as MegaNumber)
            }
        }
    }

    private fun multiplyArrays(a: Array<*>, b: Array<*>): Array<*> {
        // Recursively multiply nested arrays
        if (a.isEmpty() || b.isEmpty()) {
            return emptyArray<MegaNumber>()
        }
        
        return if (a[0] is Array<*> && b[0] is Array<*>) {
            Array(a.size) { i ->
                multiplyArrays(a[i] as Array<*>, b[i] as Array<*>)
            }
        } else {
            Array(a.size) { i ->
                (a[i] as MegaNumber).mul(b[i] as MegaNumber)
            }
        }
    }

    private fun divideArrays(a: Array<*>, b: Array<*>): Array<*> {
        // Recursively divide nested arrays
        if (a.isEmpty() || b.isEmpty()) {
            return emptyArray<MegaNumber>()
        }
        
        return if (a[0] is Array<*> && b[0] is Array<*>) {
            Array(a.size) { i ->
                divideArrays(a[i] as Array<*>, b[i] as Array<*>)
            }
        } else {
            Array(a.size) { i ->
                (a[i] as MegaNumber).divide(b[i] as MegaNumber)
            }
        }
    }

    private fun castArray(array: Array<*>, dtype: DType): Array<*> {
        // Recursively cast nested arrays
        if (array.isEmpty()) {
            return emptyArray<MegaNumber>()
        }
        
        return if (array[0] is Array<*>) {
            Array(array.size) { i ->
                castArray(array[i] as Array<*>, dtype)
            }
        } else {
            Array(array.size) { i ->
                val value = array[i] as MegaNumber
                when (dtype) {
                    DType.FLOAT32, DType.FLOAT64 -> {
                        if (value is MegaFloat) value else MegaFloat(value)
                    }
                    DType.INT32, DType.INT64, DType.UINT8 -> {
                        if (value is MegaInteger) value else MegaInteger(value)
                    }
                    DType.BOOL -> {
                        // Convert to boolean (0 = false, non-zero = true)
                        val isZero = value.mantissa.size == 1 && value.mantissa[0] == 0
                        MegaInteger.fromValue(if (isZero) 0 else 1)
                    }
                }
            }
        }
    }

    // ===== BITWISE OPERATIONS IMPLEMENTATION =====

    /**
     * Shift the bits of x to the left by shifts positions.
     */
    override fun leftShift(x: Any, shifts: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val shiftsValue = convertToShiftValue(shifts)
        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) {
                megaNumber.shiftLeft(createMegaBinaryFromInt(shiftsValue))
            } else {
                // Convert to MegaBinary, perform operation, convert back
                val binaryNum = MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
                val result = binaryNum.shiftLeft(createMegaBinaryFromInt(shiftsValue))
                convertBinaryToMegaNumber(result, x.dtype)
            }
            binary
        }

        return MegaTensor(resultData, x.shape, x.dtype, x.device)
    }

    /**
     * Shift the bits of x to the right by shifts positions.
     */
    override fun rightShift(x: Any, shifts: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val shiftsValue = convertToShiftValue(shifts)
        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) {
                megaNumber.shiftRight(createMegaBinaryFromInt(shiftsValue))
            } else {
                val binaryNum = MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
                val result = binaryNum.shiftRight(createMegaBinaryFromInt(shiftsValue))
                convertBinaryToMegaNumber(result, x.dtype)
            }
            binary
        }

        return MegaTensor(resultData, x.shape, x.dtype, x.device)
    }

    /**
     * Rotate the bits of x to the left by shifts positions.
     */
    override fun rotateLeft(x: Any, shifts: Any, bitWidth: Int): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val shiftsValue = convertToShiftValue(shifts)
        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) {
                // Implement rotation using shift and OR
                val normalizedShift = shiftsValue % bitWidth
                val leftPart = megaNumber.shiftLeft(createMegaBinaryFromInt(normalizedShift))
                val rightPart = megaNumber.shiftRight(createMegaBinaryFromInt(bitWidth - normalizedShift))
                leftPart.bitwiseOr(rightPart)
            } else {
                val binaryNum = MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
                val normalizedShift = shiftsValue % bitWidth
                val leftPart = binaryNum.shiftLeft(createMegaBinaryFromInt(normalizedShift))
                val rightPart = binaryNum.shiftRight(createMegaBinaryFromInt(bitWidth - normalizedShift))
                val result = leftPart.bitwiseOr(rightPart)
                convertBinaryToMegaNumber(result, x.dtype)
            }
            binary
        }

        return MegaTensor(resultData, x.shape, x.dtype, x.device)
    }

    /**
     * Rotate the bits of x to the right by shifts positions.
     */
    override fun rotateRight(x: Any, shifts: Any, bitWidth: Int): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val shiftsValue = convertToShiftValue(shifts)
        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) {
                val normalizedShift = shiftsValue % bitWidth
                val rightPart = megaNumber.shiftRight(createMegaBinaryFromInt(normalizedShift))
                val leftPart = megaNumber.shiftLeft(createMegaBinaryFromInt(bitWidth - normalizedShift))
                rightPart.bitwiseOr(leftPart)
            } else {
                val binaryNum = MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
                val normalizedShift = shiftsValue % bitWidth
                val rightPart = binaryNum.shiftRight(createMegaBinaryFromInt(normalizedShift))
                val leftPart = binaryNum.shiftLeft(createMegaBinaryFromInt(bitWidth - normalizedShift))
                val result = rightPart.bitwiseOr(leftPart)
                convertBinaryToMegaNumber(result, x.dtype)
            }
            binary
        }

        return MegaTensor(resultData, x.shape, x.dtype, x.device)
    }

    /**
     * Count the number of set bits (1s) in each element of x.
     */
    override fun countOnes(x: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) megaNumber else MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
            val count = binary.toBits().count { it == 1 }
            MegaInteger.fromValue(count)
        }

        return MegaTensor(resultData, x.shape, DType.INT32, x.device)
    }

    /**
     * Count the number of unset bits (0s) in each element of x.
     */
    override fun countZeros(x: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) megaNumber else MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
            val bits = binary.toBits()
            val bitWidth = when (x.dtype) {
                DType.UINT8 -> 8
                DType.INT32 -> 32
                DType.INT64 -> 64
                else -> 32
            }
            val count = bitWidth - bits.count { it == 1 }
            MegaInteger.fromValue(count)
        }

        return MegaTensor(resultData, x.shape, DType.INT32, x.device)
    }

    /**
     * Get the bit at the specified position in each element of x.
     */
    override fun getBit(x: Any, position: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val positionValue = convertToShiftValue(position)
        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) megaNumber else MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
            val bit = binary.getBit(createMegaBinaryFromInt(positionValue))
            MegaInteger.fromValue(if (bit) 1 else 0)
        }

        return MegaTensor(resultData, x.shape, DType.INT32, x.device)
    }

    /**
     * Set the bit at the specified position in each element of x to value (0 or 1).
     */
    override fun setBit(x: Any, position: Any, value: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val positionValue = convertToShiftValue(position)
        val valueValue = convertToShiftValue(value) != 0
        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) megaNumber.copy() else MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
            binary.setBit(createMegaBinaryFromInt(positionValue), valueValue)
            if (megaNumber is MegaBinary) binary else convertBinaryToMegaNumber(binary, x.dtype)
        }

        return MegaTensor(resultData, x.shape, x.dtype, x.device)
    }

    /**
     * Toggle the bit at the specified position in each element of x.
     */
    override fun toggleBit(x: Any, position: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val positionValue = convertToShiftValue(position)
        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) megaNumber.copy() else MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
            val currentBit = binary.getBit(createMegaBinaryFromInt(positionValue))
            binary.setBit(createMegaBinaryFromInt(positionValue), !currentBit)
            if (megaNumber is MegaBinary) binary else convertBinaryToMegaNumber(binary, x.dtype)
        }

        return MegaTensor(resultData, x.shape, x.dtype, x.device)
    }

    /**
     * Compute the bitwise AND of x and y element-wise.
     */
    override fun bitwiseAnd(x: Any, y: Any): Any {
        if (x !is MegaTensor || y !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor")
        }

        if (!x.shape.contentEquals(y.shape)) {
            throw IllegalArgumentException("Tensors must have the same shape for bitwise AND")
        }

        val resultData = applyBinaryBitwiseOperation(x.data, y.data) { a, b ->
            val binaryA = if (a is MegaBinary) a else MegaBinary(DefaultConversionOperations.toDecimalString(a))
            val binaryB = if (b is MegaBinary) b else MegaBinary(DefaultConversionOperations.toDecimalString(b))
            val result = binaryA.bitwiseAnd(binaryB)
            if (a is MegaBinary) result else convertBinaryToMegaNumber(result, x.dtype)
        }

        val resultDType = promoteTypes(x.dtype, y.dtype)
        return MegaTensor(resultData, x.shape, resultDType, x.device)
    }

    /**
     * Compute the bitwise OR of x and y element-wise.
     */
    override fun bitwiseOr(x: Any, y: Any): Any {
        if (x !is MegaTensor || y !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor")
        }

        if (!x.shape.contentEquals(y.shape)) {
            throw IllegalArgumentException("Tensors must have the same shape for bitwise OR")
        }

        val resultData = applyBinaryBitwiseOperation(x.data, y.data) { a, b ->
            val binaryA = if (a is MegaBinary) a else MegaBinary(DefaultConversionOperations.toDecimalString(a))
            val binaryB = if (b is MegaBinary) b else MegaBinary(DefaultConversionOperations.toDecimalString(b))
            val result = binaryA.bitwiseOr(binaryB)
            if (a is MegaBinary) result else convertBinaryToMegaNumber(result, x.dtype)
        }

        val resultDType = promoteTypes(x.dtype, y.dtype)
        return MegaTensor(resultData, x.shape, resultDType, x.device)
    }

    /**
     * Compute the bitwise XOR of x and y element-wise.
     */
    override fun bitwiseXor(x: Any, y: Any): Any {
        if (x !is MegaTensor || y !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor")
        }

        if (!x.shape.contentEquals(y.shape)) {
            throw IllegalArgumentException("Tensors must have the same shape for bitwise XOR")
        }

        val resultData = applyBinaryBitwiseOperation(x.data, y.data) { a, b ->
            val binaryA = if (a is MegaBinary) a else MegaBinary(DefaultConversionOperations.toDecimalString(a))
            val binaryB = if (b is MegaBinary) b else MegaBinary(DefaultConversionOperations.toDecimalString(b))
            val result = binaryA.bitwiseXor(binaryB)
            if (a is MegaBinary) result else convertBinaryToMegaNumber(result, x.dtype)
        }

        val resultDType = promoteTypes(x.dtype, y.dtype)
        return MegaTensor(resultData, x.shape, resultDType, x.device)
    }

    /**
     * Compute the bitwise NOT (inversion) of x element-wise.
     */
    override fun bitwiseNot(x: Any): Any {
        if (x !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${x::class.simpleName}")
        }

        val resultData = applyBitwiseOperation(x.data) { megaNumber ->
            val binary = if (megaNumber is MegaBinary) megaNumber else MegaBinary(DefaultConversionOperations.toDecimalString(megaNumber))
            val result = binary.bitwiseNot()
            if (megaNumber is MegaBinary) result else convertBinaryToMegaNumber(result, x.dtype)
        }

        return MegaTensor(resultData, x.shape, x.dtype, x.device)
    }

    /**
     * Apply wave interference between multiple binary patterns element-wise.
     */
    override fun binaryWaveInterference(waves: List<Any>, mode: String): Any {
        if (waves.isEmpty()) {
            throw IllegalArgumentException("Waves list cannot be empty")
        }

        val megaTensors = waves.map { wave ->
            if (wave !is MegaTensor) {
                throw IllegalArgumentException("All waves must be MegaTensor")
            }
            wave
        }

        // Use the first tensor as the base
        val baseTensor = megaTensors[0]
        var result = baseTensor

        // Apply interference iteratively
        for (i in 1 until megaTensors.size) {
            result = when (mode.lowercase()) {
                "xor" -> bitwiseXor(result, megaTensors[i]) as MegaTensor
                "and" -> bitwiseAnd(result, megaTensors[i]) as MegaTensor
                "or" -> bitwiseOr(result, megaTensors[i]) as MegaTensor
                else -> throw IllegalArgumentException("Unsupported interference mode: '$mode'. Choose 'xor', 'and', or 'or'.")
            }
        }

        return result
    }

    /**
     * Propagate a binary wave by shifting its bits.
     */
    override fun binaryWavePropagate(wave: Any, shift: Any): Any {
        if (wave !is MegaTensor) {
            throw IllegalArgumentException("Expected MegaTensor, got ${wave::class.simpleName}")
        }

        val shiftValue = convertToShiftValue(shift)
        return if (shiftValue >= 0) {
            leftShift(wave, shiftValue)
        } else {
            rightShift(wave, -shiftValue)
        }
    }

    /**
     * Create a binary pattern tensor with a specified duty cycle.
     */
    override fun createDutyCycle(length: Int, dutyCycle: Float, dtype: DType): Any {
        if (length <= 0) {
            throw IllegalArgumentException("Length must be positive")
        }
        if (dutyCycle < 0.0f || dutyCycle > 1.0f) {
            throw IllegalArgumentException("Duty cycle must be between 0.0 and 1.0")
        }

        val numOnes = (length * dutyCycle).toInt()
        val pattern = Array(length) { i ->
            val value = if (i < numOnes) 1 else 0
            convertValueToMegaNumber(value, dtype)
        }

        return MegaTensor(pattern, intArrayOf(length), dtype, defaultDevice)
    }

    /**
     * Generate a blocky sine wave pattern (square wave).
     */
    override fun generateBlockySin(length: Int, halfPeriod: Int, dtype: DType): Any {
        if (length <= 0) {
            throw IllegalArgumentException("Length must be positive")
        }
        if (halfPeriod <= 0) {
            throw IllegalArgumentException("Half period must be positive")
        }

        val fullPeriod = 2 * halfPeriod
        val pattern = Array(length) { i ->
            val cyclePosition = i % fullPeriod
            val value = if (cyclePosition < halfPeriod) 1 else 0
            convertValueToMegaNumber(value, dtype)
        }

        return MegaTensor(pattern, intArrayOf(length), dtype, defaultDevice)
    }

    // ===== HELPER METHODS FOR BITWISE OPERATIONS =====

    /**
     * Convert shift parameter to integer value.
     */
    private fun convertToShiftValue(shifts: Any): Int {
        return when (shifts) {
            is Int -> shifts
            is MegaTensor -> {
                if (shifts.shape.contentEquals(intArrayOf())) {
                    // Scalar tensor
                    val megaNumber = shifts.data as MegaNumber
                    when (megaNumber) {
                        is MegaInteger -> DefaultConversionOperations.toDecimalString(megaNumber).toInt()
                        is MegaFloat -> DefaultConversionOperations.toDecimalString(megaNumber).split("*")[0].trim().toDouble().toInt()
                        else -> throw IllegalArgumentException("Cannot convert ${megaNumber::class.simpleName} to shift value")
                    }
                } else {
                    throw IllegalArgumentException("Shift value must be scalar")
                }
            }
            else -> throw IllegalArgumentException("Unsupported shift type: ${shifts::class.simpleName}")
        }
    }

    /**
     * Create a MegaBinary from an integer value.
     */
    private fun createMegaBinaryFromInt(value: Int): MegaBinary {
        return MegaBinary(value.toString())
    }

    /**
     * Apply a bitwise operation to each element in the data array.
     */
    private fun applyBitwiseOperation(data: Array<*>, operation: (MegaNumber) -> MegaNumber): Array<*> {
        return if (data.isEmpty()) {
            emptyArray<MegaNumber>()
        } else if (data[0] is Array<*>) {
            Array(data.size) { i ->
                applyBitwiseOperation(data[i] as Array<*>, operation)
            }
        } else {
            Array(data.size) { i ->
                operation(data[i] as MegaNumber)
            }
        }
    }

    /**
     * Apply a binary bitwise operation to each pair of elements.
     */
    private fun applyBinaryBitwiseOperation(dataA: Array<*>, dataB: Array<*>, operation: (MegaNumber, MegaNumber) -> MegaNumber): Array<*> {
        return if (dataA.isEmpty() || dataB.isEmpty()) {
            emptyArray<MegaNumber>()
        } else if (dataA[0] is Array<*> && dataB[0] is Array<*>) {
            Array(dataA.size) { i ->
                applyBinaryBitwiseOperation(dataA[i] as Array<*>, dataB[i] as Array<*>, operation)
            }
        } else {
            Array(dataA.size) { i ->
                operation(dataA[i] as MegaNumber, dataB[i] as MegaNumber)
            }
        }
    }

    /**
     * Convert a MegaBinary result back to the appropriate MegaNumber type.
     */
    private fun convertBinaryToMegaNumber(binary: MegaBinary, dtype: DType): MegaNumber {
        return when (dtype) {
            DType.FLOAT32, DType.FLOAT64 -> MegaFloat(binary)
            else -> MegaInteger(binary)
        }
    }

    // Helper method for type promotion

    private fun promoteTypes(a: DType, b: DType): DType {
        // Type promotion rules:
        // 1. If either type is float, the result is float
        // 2. If both types are integer, the result is the larger integer type
        // 3. If one type is bool, the result is the other type
        
        return when {
            a == DType.FLOAT64 || b == DType.FLOAT64 -> DType.FLOAT64
            a == DType.FLOAT32 || b == DType.FLOAT32 -> DType.FLOAT32
            a == DType.INT64 || b == DType.INT64 -> DType.INT64
            a == DType.INT32 || b == DType.INT32 -> DType.INT32
            a == DType.UINT8 || b == DType.UINT8 -> DType.UINT8
            else -> DType.BOOL
        }
    }
}