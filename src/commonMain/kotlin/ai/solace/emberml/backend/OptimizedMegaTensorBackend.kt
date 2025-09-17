package ai.solace.emberml.backend

import ai.solace.emberml.tensor.common.EmberDType
import ai.solace.emberml.tensor.common.EmberShape
import ai.solace.emberml.tensor.bitwise.MegaNumber
import ai.solace.emberml.tensor.bitwise.MegaFloat
import ai.solace.emberml.tensor.bitwise.MegaInteger
import ai.solace.emberml.backend.storage.TensorStorage

/**
 * An optimized backend implementation that uses hybrid storage for tensor operations.
 * 
 * This backend addresses the critical 32-bit limb inefficiency by:
 * - Using efficient native storage for common data types (Boolean, UINT8, INT32, etc.)
 * - Falling back to MegaNumber storage only when arbitrary precision is needed
 * - Providing significant memory reductions: 256x for booleans, 32x for UINT8, etc.
 * 
 * This replaces the previous MegaTensorBackend which forced all data types into
 * expensive MegaNumber storage regardless of their natural size.
 */
class OptimizedMegaTensorBackend : Backend {
    // The default device for tensor operations
    private var defaultDevice: String = "cpu"
    
    // Mathematical operations helper
    private val mathOps = MathematicalOperations(this)

    /**
     * An optimized tensor implementation using hybrid storage.
     * This class wraps a TensorStorage object that uses the most efficient
     * storage strategy for the given data type.
     *
     * @property storage The hybrid storage implementation
     * @property shape The shape of the tensor
     * @property device The device where the tensor is stored
     */
    data class OptimizedMegaTensor(
        val storage: TensorStorage,
        val shape: IntArray,
        val device: String
    ) {
        val dtype: EmberDType get() = storage.dtype
        val size: Int get() = storage.size
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OptimizedMegaTensor) return false

            if (storage != other.storage) return false
            if (!shape.contentEquals(other.shape)) return false
            if (device != other.device) return false

            return true
        }

        override fun hashCode(): Int {
            var result = storage.hashCode()
            result = 31 * result + shape.contentHashCode()
            result = 31 * result + device.hashCode()
            return result
        }
    }

    /**
     * Creates a tensor from the given data using optimal storage.
     *
     * @param data The data to create the tensor from.
     * @param shape The shape of the tensor.
     * @param dtype The data type of the tensor.
     * @return The backend-specific tensor with optimized storage.
     */
    override fun createTensor(data: Any, shape: IntArray, dtype: EmberDType): Any {
        val totalSize = shape.fold(1) { acc, dim -> acc * dim }
        val storage = TensorStorage.createOptimalStorage(dtype, totalSize)
        
        // Fill the storage with data
        when (data) {
            is List<*> -> fillStorageFromList(storage, data, dtype)
            is Array<*> -> fillStorageFromArray(storage, data, dtype)
            is IntArray -> fillStorageFromIntArray(storage, data, dtype)
            is FloatArray -> fillStorageFromFloatArray(storage, data, dtype)
            is DoubleArray -> fillStorageFromDoubleArray(storage, data, dtype)
            is BooleanArray -> fillStorageFromBooleanArray(storage, data, dtype)
            else -> throw IllegalArgumentException("Unsupported data type: ${data::class.simpleName}")
        }

        return OptimizedMegaTensor(storage, shape, defaultDevice)
    }

    /**
     * Gets the shape of a tensor.
     */
    override fun getTensorShape(tensor: Any): IntArray {
        if (tensor !is OptimizedMegaTensor) {
            throw IllegalArgumentException("Expected OptimizedMegaTensor, got ${tensor::class.simpleName}")
        }
        return tensor.shape
    }

    /**
     * Gets the data type of a tensor.
     */
    override fun getTensorDType(tensor: Any): EmberDType {
        if (tensor !is OptimizedMegaTensor) {
            throw IllegalArgumentException("Expected OptimizedMegaTensor, got ${tensor::class.simpleName}")
        }
        return tensor.dtype
    }

    /**
     * Gets the device of a tensor.
     */
    override fun getTensorDevice(tensor: Any): String {
        if (tensor !is OptimizedMegaTensor) {
            throw IllegalArgumentException("Expected OptimizedMegaTensor, got ${tensor::class.simpleName}")
        }
        return tensor.device
    }

    /**
     * Adds two tensors element-wise with broadcasting support.
     */
    override fun add(tensor1: Any, tensor2: Any): Any {
        val t1 = tensor1 as OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensor
        
        if (t1.dtype != t2.dtype) {
            throw IllegalArgumentException("Data type mismatch: ${t1.dtype} vs ${t2.dtype}")
        }
        
        return performElementWiseWithBroadcasting(t1, t2) { a, b ->
            when (a) {
                is Boolean -> (a || b as Boolean) // For boolean, OR operation
                is UByte -> ((a.toInt() + (b as UByte).toInt()).coerceAtMost(255).toUByte())
                is Int -> (a + b as Int)
                is Long -> (a + b as Long)
                is Float -> (a + b as Float)
                is Double -> (a + b as Double)
                else -> throw IllegalArgumentException("Unsupported type for addition: ${a::class.simpleName}")
            }
        }
    }

    /**
     * Subtracts tensor2 from tensor1 element-wise with broadcasting support.
     */
    override fun subtract(tensor1: Any, tensor2: Any): Any {
        val t1 = tensor1 as OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensor
        
        if (t1.dtype != t2.dtype) {
            throw IllegalArgumentException("Data type mismatch: ${t1.dtype} vs ${t2.dtype}")
        }
        
        return performElementWiseWithBroadcasting(t1, t2) { a, b ->
            when (a) {
                is Boolean -> (a && !(b as Boolean)) // For boolean, AND NOT operation
                is UByte -> ((a.toInt() - (b as UByte).toInt()).coerceAtLeast(0).toUByte())
                is Int -> (a - b as Int)
                is Long -> (a - b as Long)
                is Float -> (a - b as Float)
                is Double -> (a - b as Double)
                else -> throw IllegalArgumentException("Unsupported type for subtraction: ${a::class.simpleName}")
            }
        }
    }

    /**
     * Multiplies two tensors element-wise with broadcasting support.
     */
    override fun multiply(tensor1: Any, tensor2: Any): Any {
        val t1 = tensor1 as OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensor
        
        if (t1.dtype != t2.dtype) {
            throw IllegalArgumentException("Data type mismatch: ${t1.dtype} vs ${t2.dtype}")
        }
        
        return performElementWiseWithBroadcasting(t1, t2) { a, b ->
            when (a) {
                is Boolean -> (a && b as Boolean) // For boolean, AND operation
                is UByte -> ((a.toInt() * (b as UByte).toInt()).coerceAtMost(255).toUByte())
                is Int -> (a * b as Int)
                is Long -> (a * b as Long)
                is Float -> (a * b as Float)
                is Double -> (a * b as Double)
                else -> throw IllegalArgumentException("Unsupported type for multiplication: ${a::class.simpleName}")
            }
        }
    }

    /**
     * Divides tensor1 by tensor2 element-wise with broadcasting support.
     */
    override fun divide(tensor1: Any, tensor2: Any): Any {
        val t1 = tensor1 as OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensor
        
        if (t1.dtype != t2.dtype) {
            throw IllegalArgumentException("Data type mismatch: ${t1.dtype} vs ${t2.dtype}")
        }
        
        return performElementWiseWithBroadcasting(t1, t2) { a, b ->
            when (a) {
                is Boolean -> a // For boolean, just return first value
                is UByte -> {
                    val bVal = (b as UByte).toInt()
                    if (bVal == 0) throw ArithmeticException("Division by zero")
                    (a.toInt() / bVal).toUByte()
                }
                is Int -> {
                    val bVal = b as Int
                    if (bVal == 0) throw ArithmeticException("Division by zero")
                    a / bVal
                }
                is Long -> {
                    val bVal = b as Long
                    if (bVal == 0L) throw ArithmeticException("Division by zero")
                    a / bVal
                }
                is Float -> {
                    val bVal = b as Float
                    if (bVal == 0f) throw ArithmeticException("Division by zero")
                    a / bVal
                }
                is Double -> {
                    val bVal = b as Double
                    if (bVal == 0.0) throw ArithmeticException("Division by zero")
                    a / bVal
                }
                else -> throw IllegalArgumentException("Unsupported type for division: ${a::class.simpleName}")
            }
        }
    }

    // ===== ADDITIONAL TENSOR OPERATIONS =====
    
    /**
     * Sums all elements in the tensor, returning a scalar tensor.
     */
    fun sum(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensor
        
        when (t.storage) {
            is TensorStorage.PackedBooleanStorage -> {
                var count = 0
                for (i in 0 until t.size) {
                    if (t.storage.get(i)) count++
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT32, 1)
                (resultStorage as TensorStorage.NativeIntStorage).set(0, count)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeUByteStorage -> {
                var sum = 0
                for (i in 0 until t.size) {
                    sum += t.storage.get(i).toInt()
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT32, 1)
                (resultStorage as TensorStorage.NativeIntStorage).set(0, sum)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeIntStorage -> {
                var sum = 0L
                for (i in 0 until t.size) {
                    sum += t.storage.get(i)
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT64, 1)
                (resultStorage as TensorStorage.NativeLongStorage).set(0, sum)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeLongStorage -> {
                var sum = 0L
                for (i in 0 until t.size) {
                    sum += t.storage.get(i)
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT64, 1)
                (resultStorage as TensorStorage.NativeLongStorage).set(0, sum)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeFloatStorage -> {
                var sum = 0.0
                for (i in 0 until t.size) {
                    sum += t.storage.get(i)
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.FLOAT64, 1)
                (resultStorage as TensorStorage.NativeDoubleStorage).set(0, sum)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeDoubleStorage -> {
                var sum = 0.0
                for (i in 0 until t.size) {
                    sum += t.storage.get(i)
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.FLOAT64, 1)
                (resultStorage as TensorStorage.NativeDoubleStorage).set(0, sum)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            else -> throw IllegalArgumentException("Unsupported storage type for sum operation")
        }
    }
    
    /**
     * Computes the mean of all elements in the tensor.
     */
    fun mean(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensor
        val sumTensor = sum(tensor) as OptimizedMegaTensor
        
        // Create a tensor with the size value to divide by
        val sizeStorage = TensorStorage.createOptimalStorage(sumTensor.dtype, 1)
        when (sizeStorage) {
            is TensorStorage.NativeIntStorage -> sizeStorage.set(0, t.size)
            is TensorStorage.NativeLongStorage -> sizeStorage.set(0, t.size.toLong())
            is TensorStorage.NativeDoubleStorage -> sizeStorage.set(0, t.size.toDouble())
            else -> throw IllegalArgumentException("Unsupported storage type for mean operation")
        }
        val sizeTensor = OptimizedMegaTensor(sizeStorage, intArrayOf(1), t.device)
        
        return divide(sumTensor, sizeTensor)
    }
    
    /**
     * Finds the minimum value in the tensor.
     */
    fun min(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensor
        
        when (t.storage) {
            is TensorStorage.PackedBooleanStorage -> {
                var min = true
                for (i in 0 until t.size) {
                    val value = t.storage.get(i)
                    if (!value) {
                        min = false
                        break
                    }
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.BOOL, 1)
                (resultStorage as TensorStorage.PackedBooleanStorage).set(0, min)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeUByteStorage -> {
                var min = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value < min) min = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.UINT8, 1)
                (resultStorage as TensorStorage.NativeUByteStorage).set(0, min)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeIntStorage -> {
                var min = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value < min) min = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT32, 1)
                (resultStorage as TensorStorage.NativeIntStorage).set(0, min)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeLongStorage -> {
                var min = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value < min) min = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT64, 1)
                (resultStorage as TensorStorage.NativeLongStorage).set(0, min)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeFloatStorage -> {
                var min = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value < min) min = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.FLOAT32, 1)
                (resultStorage as TensorStorage.NativeFloatStorage).set(0, min)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeDoubleStorage -> {
                var min = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value < min) min = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.FLOAT64, 1)
                (resultStorage as TensorStorage.NativeDoubleStorage).set(0, min)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            else -> throw IllegalArgumentException("Unsupported storage type for min operation")
        }
    }
    
    /**
     * Finds the maximum value in the tensor.
     */
    fun max(tensor: Any): Any {
        val t = tensor as OptimizedMegaTensor
        
        when (t.storage) {
            is TensorStorage.PackedBooleanStorage -> {
                var max = false
                for (i in 0 until t.size) {
                    val value = t.storage.get(i)
                    if (value) {
                        max = true
                        break
                    }
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.BOOL, 1)
                (resultStorage as TensorStorage.PackedBooleanStorage).set(0, max)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeUByteStorage -> {
                var max = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value > max) max = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.UINT8, 1)
                (resultStorage as TensorStorage.NativeUByteStorage).set(0, max)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeIntStorage -> {
                var max = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value > max) max = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT32, 1)
                (resultStorage as TensorStorage.NativeIntStorage).set(0, max)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeLongStorage -> {
                var max = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value > max) max = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT64, 1)
                (resultStorage as TensorStorage.NativeLongStorage).set(0, max)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeFloatStorage -> {
                var max = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value > max) max = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.FLOAT32, 1)
                (resultStorage as TensorStorage.NativeFloatStorage).set(0, max)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            is TensorStorage.NativeDoubleStorage -> {
                var max = t.storage.get(0)
                for (i in 1 until t.size) {
                    val value = t.storage.get(i)
                    if (value > max) max = value
                }
                val resultStorage = TensorStorage.createOptimalStorage(EmberDType.FLOAT64, 1)
                (resultStorage as TensorStorage.NativeDoubleStorage).set(0, max)
                return OptimizedMegaTensor(resultStorage, intArrayOf(1), t.device)
            }
            else -> throw IllegalArgumentException("Unsupported storage type for max operation")
        }
    }
    
    /**
     * Gets a specific element from the tensor at the given flat index.
     */
    fun getElement(tensor: Any, index: Int): Any {
        val t = tensor as OptimizedMegaTensor
        
        if (index < 0 || index >= t.size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for tensor of size ${t.size}")
        }
        
        return getStorageValue(t.storage, index)
    }
    
    /**
     * Sets a specific element in the tensor at the given flat index.
     */
    fun setElement(tensor: Any, index: Int, value: Any): Any {
        val t = tensor as OptimizedMegaTensor
        
        if (index < 0 || index >= t.size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for tensor of size ${t.size}")
        }
        
        // Create a new tensor with the updated value (immutable approach)
        val newStorage = when (t.storage) {
            is TensorStorage.PackedBooleanStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.PackedBooleanStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeUByteStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeUByteStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeIntStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeIntStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeLongStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeLongStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeFloatStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeFloatStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeDoubleStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeDoubleStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.MegaNumberStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.MegaNumberStorage(newData, t.size, t.dtype)
            }
        }
        
        setStorageValue(newStorage, index, value, t.dtype)
        return OptimizedMegaTensor(newStorage, t.shape, t.device)
    }

    // ===== MATRIX AND TENSOR OPERATIONS =====
    
    /**
     * Performs matrix multiplication of two tensors.
     */
    override fun matmul(a: Any, b: Any): Any {
        val t1 = a as OptimizedMegaTensor
        val t2 = b as OptimizedMegaTensor
        
        // Basic matrix multiplication for 2D tensors
        if (t1.shape.size != 2 || t2.shape.size != 2) {
            throw IllegalArgumentException("Matrix multiplication currently only supports 2D tensors")
        }
        
        val (m, k) = t1.shape
        val (k2, n) = t2.shape
        
        if (k != k2) {
            throw IllegalArgumentException("Matrix dimensions incompatible: ${t1.shape.contentToString()} x ${t2.shape.contentToString()}")
        }
        
        val resultShape = intArrayOf(m, n)
        val resultStorage = TensorStorage.createOptimalStorage(t1.dtype, m * n)
        
        // Perform matrix multiplication
        for (i in 0 until m) {
            for (j in 0 until n) {
                var sum = when (t1.dtype) {
                    EmberDType.INT32 -> 0
                    EmberDType.INT64 -> 0L
                    EmberDType.FLOAT32 -> 0.0f
                    EmberDType.FLOAT64 -> 0.0
                    else -> throw IllegalArgumentException("Unsupported dtype for matmul: ${t1.dtype}")
                }
                
                for (k_idx in 0 until k) {
                    val val1 = getStorageValue(t1.storage, i * k + k_idx)
                    val val2 = getStorageValue(t2.storage, k_idx * n + j)
                    
                    sum = when (sum) {
                        is Int -> sum + (val1 as Int) * (val2 as Int)
                        is Long -> sum + (val1 as Long) * (val2 as Long)
                        is Float -> sum + (val1 as Float) * (val2 as Float)
                        is Double -> sum + (val1 as Double) * (val2 as Double)
                        else -> throw IllegalArgumentException("Unsupported type for matmul")
                    }
                }
                
                setStorageValue(resultStorage, i * n + j, sum, t1.dtype)
            }
        }
        
        return OptimizedMegaTensor(resultStorage, resultShape, t1.device)
    }

    /**
     * Casts a tensor to a different data type.
     */
    override fun cast(tensor: Any, dtype: EmberDType): Any {
        val t = tensor as OptimizedMegaTensor
        
        if (t.dtype == dtype) {
            return t // No conversion needed
        }
        
        val newStorage = TensorStorage.createOptimalStorage(dtype, t.size)
        
        for (i in 0 until t.size) {
            val value = getStorageValue(t.storage, i)
            val convertedValue = convertValueToType(value, dtype)
            setStorageValue(newStorage, i, convertedValue, dtype)
        }
        
        return OptimizedMegaTensor(newStorage, t.shape, t.device)
    }

    /**
     * Reshapes a tensor to a new shape.
     */
    override fun reshape(tensor: Any, newShape: IntArray): Any {
        val t = tensor as OptimizedMegaTensor
        
        val newSize = newShape.fold(1) { acc, dim -> acc * dim }
        if (newSize != t.size) {
            throw IllegalArgumentException("Cannot reshape tensor of size ${t.size} to shape ${newShape.contentToString()} (size $newSize)")
        }
        
        return OptimizedMegaTensor(t.storage, newShape, t.device)
    }

    /**
     * Transposes a tensor.
     */
    override fun transpose(tensor: Any, axes: IntArray?): Any {
        val t = tensor as OptimizedMegaTensor
        
        if (t.shape.size != 2) {
            throw IllegalArgumentException("Transpose currently only supports 2D tensors")
        }
        
        val (rows, cols) = t.shape
        val newShape = intArrayOf(cols, rows)
        val newStorage = TensorStorage.createOptimalStorage(t.dtype, t.size)
        
        // Transpose the data
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                val value = getStorageValue(t.storage, i * cols + j)
                setStorageValue(newStorage, j * rows + i, value, t.dtype)
            }
        }
        
        return OptimizedMegaTensor(newStorage, newShape, t.device)
    }

    // ===== DEVICE MANAGEMENT =====
    
    /**
     * Moves a tensor to a different device.
     */
    override fun toDevice(tensor: Any, device: String): Any {
        val t = tensor as OptimizedMegaTensor
        // For now, just return a copy with the new device name
        // In a real implementation, this would involve actual device transfer
        return OptimizedMegaTensor(t.storage, t.shape, device)
    }

    /**
     * Gets a list of available devices.
     */
    override fun getAvailableDevices(): List<String> {
        return listOf("cpu") // For now, only CPU is supported
    }

    /**
     * Sets the default device for tensor operations.
     */
    override fun setDefaultDevice(device: String) {
        if (device !in getAvailableDevices()) {
            throw IllegalArgumentException("Device '$device' is not available")
        }
        defaultDevice = device
    }

    /**
     * Gets the default device for tensor operations.
     */
    override fun getDefaultDevice(): String {
        return defaultDevice
    }

    // ===== BITWISE OPERATIONS =====
    
    /**
     * Shift the bits of x to the left by shifts positions.
     */
    override fun leftShift(x: Any, shifts: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val shiftValue = when (shifts) {
            is Int -> shifts
            is OptimizedMegaTensor -> {
                if (shifts.size != 1) throw IllegalArgumentException("Shift tensor must be scalar")
                getStorageValue(shifts.storage, 0) as Int
            }
            else -> throw IllegalArgumentException("Shifts must be Int or scalar tensor")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(tensor.dtype, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val shifted = when (value) {
                is Int -> value shl shiftValue
                is Long -> value shl shiftValue
                is UByte -> (value.toInt() shl shiftValue).toUByte()
                else -> throw IllegalArgumentException("Left shift only supported for integer types")
            }
            setStorageValue(resultStorage, i, shifted, tensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Shift the bits of x to the right by shifts positions.
     */
    override fun rightShift(x: Any, shifts: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val shiftValue = when (shifts) {
            is Int -> shifts
            is OptimizedMegaTensor -> {
                if (shifts.size != 1) throw IllegalArgumentException("Shift tensor must be scalar")
                getStorageValue(shifts.storage, 0) as Int
            }
            else -> throw IllegalArgumentException("Shifts must be Int or scalar tensor")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(tensor.dtype, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val shifted = when (value) {
                is Int -> value shr shiftValue
                is Long -> value shr shiftValue
                is UByte -> (value.toInt() shr shiftValue).toUByte()
                else -> throw IllegalArgumentException("Right shift only supported for integer types")
            }
            setStorageValue(resultStorage, i, shifted, tensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Rotate the bits of x to the left by shifts positions.
     */
    override fun rotateLeft(x: Any, shifts: Any, bitWidth: Int): Any {
        val tensor = x as OptimizedMegaTensor
        val shiftValue = when (shifts) {
            is Int -> shifts % bitWidth
            is OptimizedMegaTensor -> {
                if (shifts.size != 1) throw IllegalArgumentException("Shift tensor must be scalar")
                (getStorageValue(shifts.storage, 0) as Int) % bitWidth
            }
            else -> throw IllegalArgumentException("Shifts must be Int or scalar tensor")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(tensor.dtype, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val rotated = when (value) {
                is Int -> {
                    val mask = (1 shl bitWidth) - 1
                    val maskedValue = value and mask
                    ((maskedValue shl shiftValue) or (maskedValue shr (bitWidth - shiftValue))) and mask
                }
                is UByte -> {
                    val intValue = value.toInt()
                    val mask = (1 shl bitWidth) - 1
                    val maskedValue = intValue and mask
                    (((maskedValue shl shiftValue) or (maskedValue shr (bitWidth - shiftValue))) and mask).toUByte()
                }
                else -> throw IllegalArgumentException("Rotate left only supported for integer types")
            }
            setStorageValue(resultStorage, i, rotated, tensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Rotate the bits of x to the right by shifts positions.
     */
    override fun rotateRight(x: Any, shifts: Any, bitWidth: Int): Any {
        val tensor = x as OptimizedMegaTensor
        val shiftValue = when (shifts) {
            is Int -> shifts % bitWidth
            is OptimizedMegaTensor -> {
                if (shifts.size != 1) throw IllegalArgumentException("Shift tensor must be scalar")
                (getStorageValue(shifts.storage, 0) as Int) % bitWidth
            }
            else -> throw IllegalArgumentException("Shifts must be Int or scalar tensor")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(tensor.dtype, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val rotated = when (value) {
                is Int -> {
                    val mask = (1 shl bitWidth) - 1
                    val maskedValue = value and mask
                    ((maskedValue shr shiftValue) or (maskedValue shl (bitWidth - shiftValue))) and mask
                }
                is UByte -> {
                    val intValue = value.toInt()
                    val mask = (1 shl bitWidth) - 1
                    val maskedValue = intValue and mask
                    (((maskedValue shr shiftValue) or (maskedValue shl (bitWidth - shiftValue))) and mask).toUByte()
                }
                else -> throw IllegalArgumentException("Rotate right only supported for integer types")
            }
            setStorageValue(resultStorage, i, rotated, tensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Count the number of set bits (1s) in each element of x.
     */
    override fun countOnes(x: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT32, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val count = when (value) {
                is Int -> value.countOneBits()
                is Long -> value.countOneBits()
                is UByte -> value.toInt().countOneBits()
                else -> throw IllegalArgumentException("Count ones only supported for integer types")
            }
            setStorageValue(resultStorage, i, count, EmberDType.INT32)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Count the number of unset bits (0s) in each element of x.
     */
    override fun countZeros(x: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT32, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val count = when (value) {
                is Int -> 32 - value.countOneBits()
                is Long -> 64 - value.countOneBits()
                is UByte -> 8 - value.toInt().countOneBits()
                else -> throw IllegalArgumentException("Count zeros only supported for integer types")
            }
            setStorageValue(resultStorage, i, count, EmberDType.INT32)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Get the bit at the specified position in each element of x.
     */
    override fun getBit(x: Any, position: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val pos = when (position) {
            is Int -> position
            is OptimizedMegaTensor -> {
                if (position.size != 1) throw IllegalArgumentException("Position tensor must be scalar")
                getStorageValue(position.storage, 0) as Int
            }
            else -> throw IllegalArgumentException("Position must be Int or scalar tensor")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(EmberDType.INT32, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val bit = when (value) {
                is Int -> (value shr pos) and 1
                is Long -> ((value shr pos) and 1L).toInt()
                is UByte -> (value.toInt() shr pos) and 1
                else -> throw IllegalArgumentException("Get bit only supported for integer types")
            }
            setStorageValue(resultStorage, i, bit, EmberDType.INT32)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Set the bit at the specified position in each element of x to value (0 or 1).
     */
    override fun setBit(x: Any, position: Any, value: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val pos = when (position) {
            is Int -> position
            is OptimizedMegaTensor -> {
                if (position.size != 1) throw IllegalArgumentException("Position tensor must be scalar")
                getStorageValue(position.storage, 0) as Int
            }
            else -> throw IllegalArgumentException("Position must be Int or scalar tensor")
        }
        val bitValue = when (value) {
            is Int -> value and 1
            is OptimizedMegaTensor -> {
                if (value.size != 1) throw IllegalArgumentException("Value tensor must be scalar")
                (getStorageValue(value.storage, 0) as Int) and 1
            }
            else -> throw IllegalArgumentException("Value must be Int or scalar tensor")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(tensor.dtype, tensor.size)
        
        for (i in 0 until tensor.size) {
            val originalValue = getStorageValue(tensor.storage, i)
            val newValue = when (originalValue) {
                is Int -> {
                    if (bitValue == 1) {
                        originalValue or (1 shl pos)
                    } else {
                        originalValue and (1 shl pos).inv()
                    }
                }
                is Long -> {
                    if (bitValue == 1) {
                        originalValue or (1L shl pos)
                    } else {
                        originalValue and (1L shl pos).inv()
                    }
                }
                is UByte -> {
                    val intValue = originalValue.toInt()
                    if (bitValue == 1) {
                        (intValue or (1 shl pos)).toUByte()
                    } else {
                        (intValue and (1 shl pos).inv()).toUByte()
                    }
                }
                else -> throw IllegalArgumentException("Set bit only supported for integer types")
            }
            setStorageValue(resultStorage, i, newValue, tensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Toggle the bit at the specified position in each element of x.
     */
    override fun toggleBit(x: Any, position: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val pos = when (position) {
            is Int -> position
            is OptimizedMegaTensor -> {
                if (position.size != 1) throw IllegalArgumentException("Position tensor must be scalar")
                getStorageValue(position.storage, 0) as Int
            }
            else -> throw IllegalArgumentException("Position must be Int or scalar tensor")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(tensor.dtype, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val toggled = when (value) {
                is Int -> value xor (1 shl pos)
                is Long -> value xor (1L shl pos)
                is UByte -> (value.toInt() xor (1 shl pos)).toUByte()
                else -> throw IllegalArgumentException("Toggle bit only supported for integer types")
            }
            setStorageValue(resultStorage, i, toggled, tensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    /**
     * Compute the bitwise AND of x and y element-wise.
     */
    override fun bitwiseAnd(x: Any, y: Any): Any {
        val t1 = x as OptimizedMegaTensor
        val t2 = y as OptimizedMegaTensor
        
        if (!t1.shape.contentEquals(t2.shape)) {
            throw IllegalArgumentException("Shape mismatch: ${t1.shape.contentToString()} vs ${t2.shape.contentToString()}")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(t1.dtype, t1.size)
        
        for (i in 0 until t1.size) {
            val val1 = getStorageValue(t1.storage, i)
            val val2 = getStorageValue(t2.storage, i)
            val result = when (val1) {
                is Int -> (val1 and val2 as Int)
                is Long -> (val1 and val2 as Long)
                is UByte -> (val1.toInt() and (val2 as UByte).toInt()).toUByte()
                is Boolean -> (val1 && val2 as Boolean)
                else -> throw IllegalArgumentException("Bitwise AND only supported for integer and boolean types")
            }
            setStorageValue(resultStorage, i, result, t1.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, t1.shape, t1.device)
    }

    /**
     * Compute the bitwise OR of x and y element-wise.
     */
    override fun bitwiseOr(x: Any, y: Any): Any {
        val t1 = x as OptimizedMegaTensor
        val t2 = y as OptimizedMegaTensor
        
        if (!t1.shape.contentEquals(t2.shape)) {
            throw IllegalArgumentException("Shape mismatch: ${t1.shape.contentToString()} vs ${t2.shape.contentToString()}")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(t1.dtype, t1.size)
        
        for (i in 0 until t1.size) {
            val val1 = getStorageValue(t1.storage, i)
            val val2 = getStorageValue(t2.storage, i)
            val result = when (val1) {
                is Int -> (val1 or val2 as Int)
                is Long -> (val1 or val2 as Long)
                is UByte -> (val1.toInt() or (val2 as UByte).toInt()).toUByte()
                is Boolean -> (val1 || val2 as Boolean)
                else -> throw IllegalArgumentException("Bitwise OR only supported for integer and boolean types")
            }
            setStorageValue(resultStorage, i, result, t1.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, t1.shape, t1.device)
    }

    /**
     * Compute the bitwise XOR of x and y element-wise.
     */
    override fun bitwiseXor(x: Any, y: Any): Any {
        val t1 = x as OptimizedMegaTensor
        val t2 = y as OptimizedMegaTensor
        
        if (!t1.shape.contentEquals(t2.shape)) {
            throw IllegalArgumentException("Shape mismatch: ${t1.shape.contentToString()} vs ${t2.shape.contentToString()}")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(t1.dtype, t1.size)
        
        for (i in 0 until t1.size) {
            val val1 = getStorageValue(t1.storage, i)
            val val2 = getStorageValue(t2.storage, i)
            val result = when (val1) {
                is Int -> (val1 xor val2 as Int)
                is Long -> (val1 xor val2 as Long)
                is UByte -> (val1.toInt() xor (val2 as UByte).toInt()).toUByte()
                is Boolean -> (val1 xor val2 as Boolean)
                else -> throw IllegalArgumentException("Bitwise XOR only supported for integer and boolean types")
            }
            setStorageValue(resultStorage, i, result, t1.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, t1.shape, t1.device)
    }

    /**
     * Compute the bitwise NOT (inversion) of x element-wise.
     */
    override fun bitwiseNot(x: Any): Any {
        val tensor = x as OptimizedMegaTensor
        val resultStorage = TensorStorage.createOptimalStorage(tensor.dtype, tensor.size)
        
        for (i in 0 until tensor.size) {
            val value = getStorageValue(tensor.storage, i)
            val result = when (value) {
                is Int -> value.inv()
                is Long -> value.inv()
                is UByte -> value.toInt().inv().toUByte()
                is Boolean -> !value
                else -> throw IllegalArgumentException("Bitwise NOT only supported for integer and boolean types")
            }
            setStorageValue(resultStorage, i, result, tensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, tensor.shape, tensor.device)
    }

    // ===== WAVE OPERATIONS =====
    
    /**
     * Apply wave interference between multiple binary patterns element-wise.
     */
    override fun binaryWaveInterference(waves: List<Any>, mode: String): Any {
        if (waves.isEmpty()) {
            throw IllegalArgumentException("At least one wave is required")
        }
        
        val tensors = waves.map { it as OptimizedMegaTensor }
        val firstTensor = tensors[0]
        
        // Verify all tensors have the same shape
        for (tensor in tensors) {
            if (!tensor.shape.contentEquals(firstTensor.shape)) {
                throw IllegalArgumentException("All waves must have the same shape")
            }
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(firstTensor.dtype, firstTensor.size)
        
        for (i in 0 until firstTensor.size) {
            var result = getStorageValue(firstTensor.storage, i)
            
            for (j in 1 until tensors.size) {
                val value = getStorageValue(tensors[j].storage, i)
                result = when (mode.lowercase()) {
                    "xor" -> when (result) {
                        is Int -> result xor (value as Int)
                        is Long -> result xor (value as Long)
                        is UByte -> (result.toInt() xor (value as UByte).toInt()).toUByte()
                        else -> throw IllegalArgumentException("XOR interference only supported for integer types")
                    }
                    "and" -> when (result) {
                        is Int -> result and (value as Int)
                        is Long -> result and (value as Long)
                        is UByte -> (result.toInt() and (value as UByte).toInt()).toUByte()
                        else -> throw IllegalArgumentException("AND interference only supported for integer types")
                    }
                    "or" -> when (result) {
                        is Int -> result or (value as Int)
                        is Long -> result or (value as Long)
                        is UByte -> (result.toInt() or (value as UByte).toInt()).toUByte()
                        else -> throw IllegalArgumentException("OR interference only supported for integer types")
                    }
                    else -> throw IllegalArgumentException("Unknown interference mode: $mode")
                }
            }
            
            setStorageValue(resultStorage, i, result, firstTensor.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, firstTensor.shape, firstTensor.device)
    }

    /**
     * Propagate a binary wave by shifting its bits.
     */
    override fun binaryWavePropagate(wave: Any, shift: Any): Any {
        return leftShift(wave, shift) // Wave propagation is essentially a left shift
    }

    /**
     * Create a binary pattern tensor with a specified duty cycle.
     */
    override fun createDutyCycle(length: Int, dutyCycle: Float, dtype: EmberDType): Any {
        if (dutyCycle < 0.0f || dutyCycle > 1.0f) {
            throw IllegalArgumentException("Duty cycle must be between 0.0 and 1.0")
        }
        
        val storage = TensorStorage.createOptimalStorage(dtype, length)
        val onBits = (length * dutyCycle).toInt()
        
        for (i in 0 until length) {
            val value = if (i < onBits) {
                when (dtype) {
                    EmberDType.BOOL -> true
                    EmberDType.UINT8 -> 1u.toUByte()
                    EmberDType.INT32 -> 1
                    EmberDType.INT64 -> 1L
                    else -> throw IllegalArgumentException("Duty cycle only supported for integer and boolean types")
                }
            } else {
                when (dtype) {
                    EmberDType.BOOL -> false
                    EmberDType.UINT8 -> 0u.toUByte()
                    EmberDType.INT32 -> 0
                    EmberDType.INT64 -> 0L
                    else -> throw IllegalArgumentException("Duty cycle only supported for integer and boolean types")
                }
            }
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensor(storage, intArrayOf(length), defaultDevice)
    }

    /**
     * Generate a blocky sine wave pattern (square wave).
     */
    override fun generateBlockySin(length: Int, halfPeriod: Int, dtype: EmberDType): Any {
        val storage = TensorStorage.createOptimalStorage(dtype, length)
        
        for (i in 0 until length) {
            val phase = (i / halfPeriod) % 2
            val value = if (phase == 0) {
                when (dtype) {
                    EmberDType.BOOL -> true
                    EmberDType.UINT8 -> 1u.toUByte()
                    EmberDType.INT32 -> 1
                    EmberDType.INT64 -> 1L
                    else -> throw IllegalArgumentException("Blocky sine only supported for integer and boolean types")
                }
            } else {
                when (dtype) {
                    EmberDType.BOOL -> false
                    EmberDType.UINT8 -> 0u.toUByte()
                    EmberDType.INT32 -> 0
                    EmberDType.INT64 -> 0L
                    else -> throw IllegalArgumentException("Blocky sine only supported for integer and boolean types")
                }
            }
            setStorageValue(storage, i, value, dtype)
        }
        
        return OptimizedMegaTensor(storage, intArrayOf(length), defaultDevice)
    }

    // ===== SLICING OPERATIONS =====
    
    /**
     * Slice a tensor using multi-dimensional indices.
     * 
     * @param tensor The tensor to slice
     * @param sliceRanges Array of ranges for each dimension (start:end pairs)
     * @return A new tensor containing the sliced data
     */
    fun slice(tensor: Any, sliceRanges: Array<IntRange>): Any {
        val t = tensor as OptimizedMegaTensor
        
        if (sliceRanges.size != t.shape.size) {
            throw IllegalArgumentException("Number of slice ranges (${sliceRanges.size}) must match tensor rank (${t.shape.size})")
        }
        
        // Calculate new shape and validate ranges
        val newShape = IntArray(t.shape.size)
        for (i in sliceRanges.indices) {
            val range = sliceRanges[i]
            val dimSize = t.shape[i]
            
            // Validate range bounds
            if (range.first < 0 || range.last >= dimSize) {
                throw IndexOutOfBoundsException("Slice range $range is out of bounds for dimension $i (size $dimSize)")
            }
            
            newShape[i] = range.last - range.first + 1
        }
        
        val newSize = newShape.fold(1) { acc, dim -> acc * dim }
        val newStorage = TensorStorage.createOptimalStorage(t.dtype, newSize)
        
        // Extract sliced data
        var newIndex = 0
        extractSlicedData(t, sliceRanges, IntArray(t.shape.size), 0, newStorage, newIndex)
        
        return OptimizedMegaTensor(newStorage, newShape, t.device)
    }
    
    /**
     * Get a single element from a tensor using multi-dimensional indices.
     */
    fun getElementAtIndex(tensor: Any, indices: IntArray): Any {
        val t = tensor as OptimizedMegaTensor
        
        if (indices.size != t.shape.size) {
            throw IllegalArgumentException("Number of indices (${indices.size}) must match tensor rank (${t.shape.size})")
        }
        
        // Validate indices
        for (i in indices.indices) {
            if (indices[i] < 0 || indices[i] >= t.shape[i]) {
                throw IndexOutOfBoundsException("Index ${indices[i]} is out of bounds for dimension $i (size ${t.shape[i]})")
            }
        }
        
        val flatIndex = multiIndexToFlatIndex(indices, t.shape)
        return getStorageValue(t.storage, flatIndex)
    }
    
    /**
     * Set a single element in a tensor using multi-dimensional indices.
     */
    fun setElementAtIndex(tensor: Any, indices: IntArray, value: Any): Any {
        val t = tensor as OptimizedMegaTensor
        
        if (indices.size != t.shape.size) {
            throw IllegalArgumentException("Number of indices (${indices.size}) must match tensor rank (${t.shape.size})")
        }
        
        // Validate indices
        for (i in indices.indices) {
            if (indices[i] < 0 || indices[i] >= t.shape[i]) {
                throw IndexOutOfBoundsException("Index ${indices[i]} is out of bounds for dimension $i (size ${t.shape[i]})")
            }
        }
        
        // Create a new tensor with the updated value (immutable approach)
        val newStorage = when (t.storage) {
            is TensorStorage.PackedBooleanStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.PackedBooleanStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeUByteStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeUByteStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeIntStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeIntStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeLongStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeLongStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeFloatStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeFloatStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.NativeDoubleStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.NativeDoubleStorage(newData, t.size, t.dtype)
            }
            is TensorStorage.MegaNumberStorage -> {
                val newData = t.storage.data.copyOf()
                TensorStorage.MegaNumberStorage(newData, t.size, t.dtype)
            }
        }
        
        val flatIndex = multiIndexToFlatIndex(indices, t.shape)
        setStorageValue(newStorage, flatIndex, value, t.dtype)
        return OptimizedMegaTensor(newStorage, t.shape, t.device)
    }

    // Helper functions

    /**
     * Recursively extract sliced data from a tensor.
     */
    private fun extractSlicedData(
        tensor: OptimizedMegaTensor,
        sliceRanges: Array<IntRange>,
        currentIndices: IntArray,
        dimension: Int,
        targetStorage: TensorStorage,
        targetIndex: Int
    ): Int {
        var currentTargetIndex = targetIndex
        
        if (dimension == tensor.shape.size) {
            // Base case: we have a complete multi-index, copy the element
            val sourceIndex = multiIndexToFlatIndex(currentIndices, tensor.shape)
            val value = getStorageValue(tensor.storage, sourceIndex)
            setStorageValue(targetStorage, currentTargetIndex, value, tensor.dtype)
            return currentTargetIndex + 1
        }
        
        // Recursive case: iterate through the slice range for this dimension
        val range = sliceRanges[dimension]
        for (i in range) {
            currentIndices[dimension] = i
            currentTargetIndex = extractSlicedData(tensor, sliceRanges, currentIndices, dimension + 1, targetStorage, currentTargetIndex)
        }
        
        return currentTargetIndex
    }
    
    /**
     * Convert multi-dimensional indices to a flat index.
     */
    private fun multiIndexToFlatIndex(multiIndex: IntArray, shape: IntArray): Int {
        var flatIndex = 0
        for (i in multiIndex.indices) {
            flatIndex = flatIndex * shape[i] + multiIndex[i]
        }
        return flatIndex
    }

    /**
     * Performs element-wise operation with broadcasting support.
     */
    private fun performElementWiseWithBroadcasting(
        t1: OptimizedMegaTensor, 
        t2: OptimizedMegaTensor, 
        operation: (Any, Any) -> Any
    ): OptimizedMegaTensor {
        val shape1 = EmberShape(t1.shape)
        val shape2 = EmberShape(t2.shape)
        
        // Check if broadcasting is possible
        if (!shape1.isBroadcastableWith(shape2)) {
            throw IllegalArgumentException("Shapes ${t1.shape.contentToString()} and ${t2.shape.contentToString()} are not compatible for broadcasting")
        }
        
        // Calculate the broadcasted shape
        val broadcastShape = shape1.broadcastWith(shape2)
        val resultSize = broadcastShape.totalSize()
        val resultStorage = TensorStorage.createOptimalStorage(t1.dtype, resultSize)
        
        // Perform the operation with broadcasting
        for (i in 0 until resultSize) {
            val multiIndex = flatIndexToMultiIndex(i, broadcastShape.dimensions)
            
            val index1 = multiIndexToBroadcastedFlatIndex(multiIndex, broadcastShape.dimensions, t1.shape)
            val index2 = multiIndexToBroadcastedFlatIndex(multiIndex, broadcastShape.dimensions, t2.shape)
            
            val value1 = getStorageValue(t1.storage, index1)
            val value2 = getStorageValue(t2.storage, index2)
            val result = operation(value1, value2)
            
            setStorageValue(resultStorage, i, result, t1.dtype)
        }
        
        return OptimizedMegaTensor(resultStorage, broadcastShape.dimensions, t1.device)
    }

    /**
     * Converts a flat index to multi-dimensional indices.
     */
    private fun flatIndexToMultiIndex(flatIndex: Int, shape: IntArray): IntArray {
        val result = IntArray(shape.size)
        var remaining = flatIndex
        
        for (i in shape.size - 1 downTo 0) {
            result[i] = remaining % shape[i]
            remaining /= shape[i]
        }
        
        return result
    }

    /**
     * Converts multi-dimensional indices to a flat index with broadcasting.
     */
    private fun multiIndexToBroadcastedFlatIndex(multiIndex: IntArray, broadcastShape: IntArray, tensorShape: IntArray): Int {
        var flatIndex = 0
        val tensorRank = tensorShape.size
        val broadcastRank = broadcastShape.size
        
        for (i in 0 until tensorRank) {
            val broadcastDim = broadcastRank - tensorRank + i
            val tensorDim = multiIndex[broadcastDim]
            
            // Handle broadcasting: if tensor dimension is 1, use index 0
            val actualIndex = if (tensorShape[i] == 1) 0 else tensorDim
            
            flatIndex = flatIndex * tensorShape[i] + actualIndex
        }
        
        return flatIndex
    }

    private fun convertValueToType(value: Any, targetType: EmberDType): Any {
        return when (targetType) {
            EmberDType.BOOL -> convertToBoolean(value)
            EmberDType.UINT8 -> convertToUByte(value)
            EmberDType.INT32 -> convertToInt(value)
            EmberDType.INT64 -> convertToLong(value)
            EmberDType.FLOAT32 -> convertToFloat(value)
            EmberDType.FLOAT64 -> convertToDouble(value)
            else -> throw IllegalArgumentException("Unsupported target type: $targetType")
        }
    }

    private fun fillStorageFromList(storage: TensorStorage, data: List<*>, dtype: EmberDType) {
        for (i in data.indices) {
            val value = data[i]
            setStorageValue(storage, i, value, dtype)
        }
    }

    private fun fillStorageFromArray(storage: TensorStorage, data: Array<*>, dtype: EmberDType) {
        for (i in data.indices) {
            val value = data[i]
            setStorageValue(storage, i, value, dtype)
        }
    }

    private fun fillStorageFromIntArray(storage: TensorStorage, data: IntArray, dtype: EmberDType) {
        for (i in data.indices) {
            val value = data[i]
            setStorageValue(storage, i, value, dtype)
        }
    }

    private fun fillStorageFromFloatArray(storage: TensorStorage, data: FloatArray, dtype: EmberDType) {
        for (i in data.indices) {
            val value = data[i]
            setStorageValue(storage, i, value, dtype)
        }
    }

    private fun fillStorageFromDoubleArray(storage: TensorStorage, data: DoubleArray, dtype: EmberDType) {
        for (i in data.indices) {
            val value = data[i]
            setStorageValue(storage, i, value, dtype)
        }
    }

    private fun fillStorageFromBooleanArray(storage: TensorStorage, data: BooleanArray, dtype: EmberDType) {
        for (i in data.indices) {
            val value = data[i]
            setStorageValue(storage, i, value, dtype)
        }
    }

    private fun setStorageValue(storage: TensorStorage, index: Int, value: Any?, dtype: EmberDType) {
        when (storage) {
            is TensorStorage.PackedBooleanStorage -> {
                storage.set(index, convertToBoolean(value))
            }
            is TensorStorage.NativeUByteStorage -> {
                storage.set(index, convertToUByte(value))
            }
            is TensorStorage.NativeIntStorage -> {
                storage.set(index, convertToInt(value))
            }
            is TensorStorage.NativeLongStorage -> {
                storage.set(index, convertToLong(value))
            }
            is TensorStorage.NativeFloatStorage -> {
                storage.set(index, convertToFloat(value))
            }
            is TensorStorage.NativeDoubleStorage -> {
                storage.set(index, convertToDouble(value))
            }
            is TensorStorage.MegaNumberStorage -> {
                storage.set(index, convertToMegaNumber(value, dtype))
            }
        }
    }

    private fun getStorageValue(storage: TensorStorage, index: Int): Any {
        return when (storage) {
            is TensorStorage.PackedBooleanStorage -> storage.get(index)
            is TensorStorage.NativeUByteStorage -> storage.get(index)
            is TensorStorage.NativeIntStorage -> storage.get(index)
            is TensorStorage.NativeLongStorage -> storage.get(index)
            is TensorStorage.NativeFloatStorage -> storage.get(index)
            is TensorStorage.NativeDoubleStorage -> storage.get(index)
            is TensorStorage.MegaNumberStorage -> storage.get(index)
        }
    }

    private fun performElementWiseOperation(
        storage1: TensorStorage,
        storage2: TensorStorage,
        resultStorage: TensorStorage,
        operation: (Any, Any) -> Any
    ) {
        for (i in 0 until storage1.size) {
            val value1 = getStorageValue(storage1, i)
            val value2 = getStorageValue(storage2, i)
            val result = operation(value1, value2)
            setStorageValue(resultStorage, i, result, resultStorage.dtype)
        }
    }

    // Conversion helpers

    private fun convertToBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.toBoolean()
            else -> false
        }
    }

    private fun convertToUByte(value: Any?): UByte {
        return when (value) {
            is Number -> value.toInt().coerceIn(0, 255).toUByte()
            is Boolean -> if (value) 1u else 0u
            is String -> value.toInt().coerceIn(0, 255).toUByte()
            else -> 0u
        }
    }

    private fun convertToInt(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt()
            is Boolean -> if (value) 1 else 0
            is String -> value.toInt()
            else -> 0
        }
    }

    private fun convertToLong(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is Boolean -> if (value) 1L else 0L
            is String -> value.toLong()
            else -> 0L
        }
    }

    private fun convertToFloat(value: Any?): Float {
        return when (value) {
            is Number -> value.toFloat()
            is Boolean -> if (value) 1f else 0f
            is String -> value.toFloat()
            else -> 0f
        }
    }

    private fun convertToDouble(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            is String -> value.toDouble()
            else -> 0.0
        }
    }

    private fun convertToMegaNumber(value: Any?, dtype: EmberDType): MegaNumber {
        return when (value) {
            is Int -> when (dtype) {
                EmberDType.FLOAT32, EmberDType.FLOAT64 -> MegaFloat.fromValue(value.toDouble())
                else -> MegaInteger.fromValue(value)
            }
            is Long -> when (dtype) {
                EmberDType.FLOAT32, EmberDType.FLOAT64 -> MegaFloat.fromValue(value.toDouble())
                else -> MegaInteger.fromValue(value.toInt())
            }
            is Float -> MegaFloat.fromValue(value.toDouble())
            is Double -> MegaFloat.fromValue(value)
            is Boolean -> MegaInteger.fromValue(if (value) 1 else 0)
            is String -> when (dtype) {
                EmberDType.FLOAT32, EmberDType.FLOAT64 -> MegaFloat.fromValue(value.toDouble())
                else -> MegaInteger.fromValue(value.toInt())
            }
            else -> MegaInteger.fromValue(0)
        }
    }
}