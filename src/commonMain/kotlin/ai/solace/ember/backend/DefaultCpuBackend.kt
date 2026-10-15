package ai.solace.ember.backend

import ai.solace.ember.backend.storage.TensorStorage
import ai.solace.ember.tensor.common.DType

/**
 * Backend implementation backed by compact per-dtype tensor storage.
 */
class DefaultCpuBackend : Backend {
    private var defaultDevice: String = "cpu"
    private val mathOps = MathematicalOperations(this)
    private val statsOps = StatisticalOperations(this)
    private val linalgOps = LinearAlgebraOperations(this)

    data class DefaultCpuTensor(
        val storage: TensorStorage,
        val shape: IntArray,
        val device: String,
    ) {
        val dtype: DType get() = storage.dtype
        val size: Int get() = storage.size

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is DefaultCpuTensor &&
                storage == other.storage &&
                shape.contentEquals(other.shape) &&
                device == other.device

        override fun hashCode(): Int {
            var result = storage.hashCode()
            result = 31 * result + shape.contentHashCode()
            result = 31 * result + device.hashCode()
            return result
        }
    }

    override fun createTensor(data: Any, shape: IntArray, dtype: DType): DefaultCpuTensor {
        val size = shape.fold(1) { acc, dim -> acc * dim }
        val values = flattenValues(data)
        require(values.size == size) {
            "Data size ${values.size} does not match tensor shape ${shape.contentToString()} ($size elements)"
        }
        val storage = TensorStorage.createOptimalStorage(dtype, size)
        for (i in values.indices) {
            setStorageValue(storage, i, values[i])
        }
        return DefaultCpuTensor(storage, shape.copyOf(), defaultDevice)
    }

    override fun getTensorShape(tensor: Any): IntArray = asTensor(tensor).shape.copyOf()

    override fun getTensorDType(tensor: Any): DType = asTensor(tensor).dtype

    override fun getTensorDevice(tensor: Any): String = asTensor(tensor).device

    override fun add(a: Any, b: Any): DefaultCpuTensor =
        binaryTensorOp(a, b) { left, right ->
            when (left) {
                is Boolean -> left || right.toBooleanValue()
                is UByte -> (left.toInt() + right.toIntValue()).coerceIn(0, 255).toUByte()
                is Int -> left + right.toIntValue()
                is Long -> left + right.toLongValue()
                is Float -> left + right.toFloatValue()
                is Double -> left + right.toDoubleValue()
                else -> unsupportedValue(left)
            }
        }

    override fun subtract(a: Any, b: Any): DefaultCpuTensor =
        binaryTensorOp(a, b) { left, right ->
            when (left) {
                is Boolean -> left && !right.toBooleanValue()
                is UByte -> (left.toInt() - right.toIntValue()).coerceIn(0, 255).toUByte()
                is Int -> left - right.toIntValue()
                is Long -> left - right.toLongValue()
                is Float -> left - right.toFloatValue()
                is Double -> left - right.toDoubleValue()
                else -> unsupportedValue(left)
            }
        }

    override fun multiply(a: Any, b: Any): DefaultCpuTensor =
        binaryTensorOp(a, b) { left, right ->
            when (left) {
                is Boolean -> left && right.toBooleanValue()
                is UByte -> (left.toInt() * right.toIntValue()).coerceIn(0, 255).toUByte()
                is Int -> left * right.toIntValue()
                is Long -> left * right.toLongValue()
                is Float -> left * right.toFloatValue()
                is Double -> left * right.toDoubleValue()
                else -> unsupportedValue(left)
            }
        }

    override fun divide(a: Any, b: Any): DefaultCpuTensor =
        binaryTensorOp(a, b) { left, right ->
            when (left) {
                is Boolean -> left
                is UByte -> (left.toInt() / right.toIntValue().requireNonZero()).coerceIn(0, 255).toUByte()
                is Int -> left / right.toIntValue().requireNonZero()
                is Long -> left / right.toLongValue().requireNonZero()
                is Float -> left / right.toFloatValue().requireNonZero()
                is Double -> left / right.toDoubleValue().requireNonZero()
                else -> unsupportedValue(left)
            }
        }

    override fun matmul(a: Any, b: Any): DefaultCpuTensor =
        linalgOps.matmul(a, b)

    override fun cast(tensor: Any, dtype: DType): DefaultCpuTensor {
        val source = asTensor(tensor)
        val storage = TensorStorage.createOptimalStorage(dtype, source.size)
        for (i in 0 until source.size) {
            setStorageValue(storage, i, getStorageValue(source.storage, i))
        }
        return DefaultCpuTensor(storage, source.shape.copyOf(), source.device)
    }

    override fun reshape(tensor: Any, newShape: IntArray): DefaultCpuTensor {
        val source = asTensor(tensor)
        val newSize = newShape.fold(1) { acc, dim -> acc * dim }
        require(newSize == source.size) {
            "Cannot reshape ${source.shape.contentToString()} to ${newShape.contentToString()}"
        }
        return DefaultCpuTensor(copyStorage(source.storage), newShape.copyOf(), source.device)
    }

    override fun transpose(tensor: Any, axes: IntArray?): DefaultCpuTensor =
        linalgOps.transpose(tensor, axes)

    override fun toDevice(tensor: Any, device: String): DefaultCpuTensor {
        val source = asTensor(tensor)
        return DefaultCpuTensor(copyStorage(source.storage), source.shape.copyOf(), device)
    }

    override fun getAvailableDevices(): List<String> = listOf(defaultDevice)

    override fun setDefaultDevice(device: String) {
        defaultDevice = device
    }

    override fun getDefaultDevice(): String = defaultDevice

    fun sum(tensor: Any): DefaultCpuTensor = statsOps.sum(tensor)

    fun mean(tensor: Any): DefaultCpuTensor = statsOps.mean(tensor)

    fun min(tensor: Any): DefaultCpuTensor = statsOps.min(tensor)

    fun max(tensor: Any): DefaultCpuTensor = statsOps.max(tensor)

    fun getElement(tensor: Any, index: Int): Any {
        val source = asTensor(tensor)
        require(index in 0 until source.size) { "Index $index out of bounds for size ${source.size}" }
        return getStorageValue(source.storage, index)
    }

    fun setElement(tensor: Any, index: Int, value: Any): DefaultCpuTensor {
        val source = asTensor(tensor)
        require(index in 0 until source.size) { "Index $index out of bounds for size ${source.size}" }
        val storage = copyStorage(source.storage)
        setStorageValue(storage, index, value)
        return DefaultCpuTensor(storage, source.shape.copyOf(), source.device)
    }

    fun sin(tensor: Any): DefaultCpuTensor = mathOps.sin(tensor)

    fun cos(tensor: Any): DefaultCpuTensor = mathOps.cos(tensor)

    fun exp(tensor: Any): DefaultCpuTensor = mathOps.exp(tensor)

    fun log(tensor: Any): DefaultCpuTensor = mathOps.log(tensor)

    fun sqrt(tensor: Any): DefaultCpuTensor = mathOps.sqrt(tensor)

    fun pow(tensor: Any, exponent: Any): DefaultCpuTensor = mathOps.pow(tensor, exponent.toDoubleValue())

    fun abs(tensor: Any): DefaultCpuTensor = mathOps.abs(tensor)

    fun greaterThan(a: Any, b: Any): DefaultCpuTensor = mathOps.greaterThan(a, b)

    fun lessThan(a: Any, b: Any): DefaultCpuTensor = mathOps.lessThan(a, b)

    fun equal(a: Any, b: Any): DefaultCpuTensor = mathOps.equal(a, b)

    override fun leftShift(x: Any, shifts: Any): Any = integerTensorOp(x) { it shl shiftValue(shifts) }

    override fun rightShift(x: Any, shifts: Any): Any = integerTensorOp(x) { it shr shiftValue(shifts) }

    override fun rotateLeft(x: Any, shifts: Any, bitWidth: Int): Any =
        integerTensorOp(x) { value ->
            val shift = shiftValue(shifts).floorMod(bitWidth)
            val mask = if (bitWidth >= Long.SIZE_BITS) -1L else (1L shl bitWidth) - 1L
            val clipped = value and mask
            ((clipped shl shift) or (clipped ushr (bitWidth - shift))) and mask
        }

    override fun rotateRight(x: Any, shifts: Any, bitWidth: Int): Any =
        integerTensorOp(x) { value ->
            val shift = shiftValue(shifts).floorMod(bitWidth)
            val mask = if (bitWidth >= Long.SIZE_BITS) -1L else (1L shl bitWidth) - 1L
            val clipped = value and mask
            ((clipped ushr shift) or (clipped shl (bitWidth - shift))) and mask
        }

    override fun countOnes(x: Any): Any = integerTensorOp(x, DType.INT32) { it.countOneBits().toLong() }

    override fun countZeros(x: Any): Any =
        integerTensorOp(x, DType.INT32) {
            (Long.SIZE_BITS - it.countOneBits()).toLong()
        }

    override fun getBit(x: Any, position: Any): Any =
        integerTensorOp(x, DType.INT32) {
            ((it ushr shiftValue(position)) and 1L)
        }

    override fun setBit(x: Any, position: Any, value: Any): Any =
        integerTensorOp(x) {
            val mask = 1L shl shiftValue(position)
            if (value.toBooleanValue()) it or mask else it and mask.inv()
        }

    override fun toggleBit(x: Any, position: Any): Any = integerTensorOp(x) { it xor (1L shl shiftValue(position)) }

    override fun bitwiseAnd(x: Any, y: Any): Any = integerBinaryTensorOp(x, y) { a, b -> a and b }

    override fun bitwiseOr(x: Any, y: Any): Any = integerBinaryTensorOp(x, y) { a, b -> a or b }

    override fun bitwiseXor(x: Any, y: Any): Any = integerBinaryTensorOp(x, y) { a, b -> a xor b }

    override fun bitwiseNot(x: Any): Any = integerTensorOp(x) { it.inv() }

    override fun binaryWaveInterference(waves: List<Any>, mode: String): Any {
        require(waves.isNotEmpty()) { "At least one wave is required" }
        return waves.drop(1).fold(waves.first()) { acc, wave ->
            when (mode.lowercase()) {
                "or" -> bitwiseOr(acc, wave)
                "xor" -> bitwiseXor(acc, wave)
                "and" -> bitwiseAnd(acc, wave)
                else -> throw IllegalArgumentException("Unsupported interference mode: $mode")
            }
        }
    }

    override fun binaryWavePropagate(wave: Any, shift: Any): Any = leftShift(wave, shift)

    override fun createDutyCycle(length: Int, dutyCycle: Float, dtype: DType): Any {
        require(length >= 0) { "Length must be non-negative" }
        require(dutyCycle in 0.0f..1.0f) { "Duty cycle must be between 0 and 1" }
        val active = (length * dutyCycle).toInt()
        return createTensor(IntArray(length) { if (it < active) 1 else 0 }, intArrayOf(length), dtype)
    }

    override fun generateBlockySin(length: Int, halfPeriod: Int, dtype: DType): Any {
        require(length >= 0) { "Length must be non-negative" }
        require(halfPeriod > 0) { "Half period must be positive" }
        val period = halfPeriod * 2
        return createTensor(IntArray(length) { if (it % period < halfPeriod) 1 else 0 }, intArrayOf(length), dtype)
    }

    private fun binaryTensorOp(a: Any, b: Any, operation: (Any, Any) -> Any): DefaultCpuTensor {
        val left = asTensor(a)
        val right = asTensor(b)
        require(left.shape.contentEquals(right.shape)) {
            "Tensor shapes must match: ${left.shape.contentToString()} vs ${right.shape.contentToString()}"
        }
        require(left.dtype == right.dtype) { "Tensor dtypes must match: ${left.dtype} vs ${right.dtype}" }
        val storage = TensorStorage.createOptimalStorage(left.dtype, left.size)
        for (i in 0 until left.size) {
            setStorageValue(storage, i, operation(getStorageValue(left.storage, i), getStorageValue(right.storage, i)))
        }
        return DefaultCpuTensor(storage, left.shape.copyOf(), left.device)
    }

    private fun integerBinaryTensorOp(a: Any, b: Any, operation: (Long, Long) -> Long): DefaultCpuTensor =
        binaryTensorOp(a, b) { left, right -> operation(left.toLongValue(), right.toLongValue()) }

    private fun integerTensorOp(tensor: Any, resultDType: DType = asTensor(tensor).dtype, operation: (Long) -> Long): DefaultCpuTensor {
        val source = asTensor(tensor)
        val storage = TensorStorage.createOptimalStorage(resultDType, source.size)
        for (i in 0 until source.size) {
            setStorageValue(storage, i, operation(getStorageValue(source.storage, i).toLongValue()))
        }
        return DefaultCpuTensor(storage, source.shape.copyOf(), source.device)
    }

    private fun copyStorage(storage: TensorStorage): TensorStorage {
        val copy = TensorStorage.createOptimalStorage(storage.dtype, storage.size)
        for (i in 0 until storage.size) {
            setStorageValue(copy, i, getStorageValue(storage, i))
        }
        return copy
    }

    private fun asTensor(tensor: Any): DefaultCpuTensor =
        tensor as? DefaultCpuTensor
            ?: throw IllegalArgumentException("Expected DefaultCpuTensor, got ${tensor::class.simpleName}")

    private fun flattenValues(data: Any): List<Any> =
        when (data) {
            is List<*> -> data.flatMap { if (it is List<*>) flattenValues(it) else listOf(it ?: 0) }
            is Array<*> -> data.flatMap { if (it is Array<*>) flattenValues(it) else listOf(it ?: 0) }
            is IntArray -> data.toList()
            is LongArray -> data.toList()
            is FloatArray -> data.toList()
            is DoubleArray -> data.toList()
            is BooleanArray -> data.toList()
            is ByteArray -> data.map { it.toInt() }
            is UByteArray -> data.toList()
            else -> throw IllegalArgumentException("Unsupported data type: ${data::class.simpleName}")
        }

    private fun getStorageValue(storage: TensorStorage, index: Int): Any =
        when (storage) {
            is TensorStorage.PackedBooleanStorage -> storage.get(index)
            is TensorStorage.NativeUByteStorage -> storage.get(index)
            is TensorStorage.NativeIntStorage -> storage.get(index)
            is TensorStorage.NativeLongStorage -> storage.get(index)
            is TensorStorage.NativeFloatStorage -> storage.get(index)
            is TensorStorage.NativeDoubleStorage -> storage.get(index)
        }

    private fun setStorageValue(storage: TensorStorage, index: Int, value: Any) {
        when (storage) {
            is TensorStorage.PackedBooleanStorage -> storage.set(index, value.toBooleanValue())
            is TensorStorage.NativeUByteStorage -> storage.set(index, value.toIntValue().coerceIn(0, 255).toUByte())
            is TensorStorage.NativeIntStorage -> storage.set(index, value.toIntValue())
            is TensorStorage.NativeLongStorage -> storage.set(index, value.toLongValue())
            is TensorStorage.NativeFloatStorage -> storage.set(index, value.toFloatValue())
            is TensorStorage.NativeDoubleStorage -> storage.set(index, value.toDoubleValue())
        }
    }

    private fun Any.toBooleanValue(): Boolean =
        when (this) {
            is Boolean -> this
            is Number -> toDouble() != 0.0
            is UByte -> toInt() != 0
            else -> false
        }

    private fun Any.toIntValue(): Int =
        when (this) {
            is Number -> toInt()
            is UByte -> toInt()
            is Boolean -> if (this) 1 else 0
            else -> 0
        }

    private fun Any.toLongValue(): Long =
        when (this) {
            is Number -> toLong()
            is UByte -> toLong()
            is Boolean -> if (this) 1L else 0L
            else -> 0L
        }

    private fun Any.toFloatValue(): Float =
        when (this) {
            is Number -> toFloat()
            is UByte -> toFloat()
            is Boolean -> if (this) 1f else 0f
            else -> 0f
        }

    private fun Any.toDoubleValue(): Double =
        when (this) {
            is Number -> toDouble()
            is UByte -> toDouble()
            is Boolean -> if (this) 1.0 else 0.0
            else -> 0.0
        }

    private fun Int.requireNonZero(): Int {
        if (this == 0) throw ArithmeticException("Division by zero")
        return this
    }

    private fun Long.requireNonZero(): Long {
        if (this == 0L) throw ArithmeticException("Division by zero")
        return this
    }

    private fun Float.requireNonZero(): Float {
        if (this == 0f) throw ArithmeticException("Division by zero")
        return this
    }

    private fun Double.requireNonZero(): Double {
        if (this == 0.0) throw ArithmeticException("Division by zero")
        return this
    }

    private fun shiftValue(value: Any): Int =
        when (value) {
            is DefaultCpuTensor -> getStorageValue(value.storage, 0).toIntValue()
            else -> value.toIntValue()
        }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun unsupportedValue(value: Any): Nothing =
        throw IllegalArgumentException("Unsupported tensor value type: ${value::class.simpleName}")
}
