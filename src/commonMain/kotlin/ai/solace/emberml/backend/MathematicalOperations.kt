package ai.solace.emberml.backend

import ai.solace.emberml.tensor.common.EmberDType
import ai.solace.emberml.backend.storage.TensorStorage
import kotlin.math.*

/**
 * Mathematical functions for the OptimizedMegaTensorBackend.
 * 
 * This class provides element-wise mathematical operations that were missing
 * in the original implementation, including trigonometric, exponential, 
 * and other mathematical functions.
 */
class MathematicalOperations(private val backend: OptimizedMegaTensorBackend) {
    
    /**
     * Applies sine function element-wise.
     */
    fun sin(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> sin(value)
                is Double -> sin(value)
                is Int -> sin(value.toDouble())
                is Long -> sin(value.toDouble())
                is UByte -> sin(value.toDouble())
                else -> throw IllegalArgumentException("Sin operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies cosine function element-wise.
     */
    fun cos(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> cos(value)
                is Double -> cos(value)
                is Int -> cos(value.toDouble())
                is Long -> cos(value.toDouble())
                is UByte -> cos(value.toDouble())
                else -> throw IllegalArgumentException("Cos operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies tangent function element-wise.
     */
    fun tan(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> tan(value)
                is Double -> tan(value)
                is Int -> tan(value.toDouble())
                is Long -> tan(value.toDouble())
                is UByte -> tan(value.toDouble())
                else -> throw IllegalArgumentException("Tan operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies exponential function element-wise.
     */
    fun exp(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> exp(value)
                is Double -> exp(value)
                is Int -> exp(value.toDouble())
                is Long -> exp(value.toDouble())
                is UByte -> exp(value.toDouble())
                else -> throw IllegalArgumentException("Exp operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies natural logarithm function element-wise.
     */
    fun log(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> {
                    if (value <= 0f) throw ArithmeticException("Log of non-positive number: $value")
                    ln(value)
                }
                is Double -> {
                    if (value <= 0.0) throw ArithmeticException("Log of non-positive number: $value")
                    ln(value)
                }
                is Int -> {
                    if (value <= 0) throw ArithmeticException("Log of non-positive number: $value")
                    ln(value.toDouble())
                }
                is Long -> {
                    if (value <= 0L) throw ArithmeticException("Log of non-positive number: $value")
                    ln(value.toDouble())
                }
                is UByte -> {
                    if (value.toInt() == 0) throw ArithmeticException("Log of zero")
                    ln(value.toDouble())
                }
                else -> throw IllegalArgumentException("Log operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies square root function element-wise.
     */
    fun sqrt(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> {
                    if (value < 0f) throw ArithmeticException("Square root of negative number: $value")
                    sqrt(value)
                }
                is Double -> {
                    if (value < 0.0) throw ArithmeticException("Square root of negative number: $value")
                    sqrt(value)
                }
                is Int -> {
                    if (value < 0) throw ArithmeticException("Square root of negative number: $value")
                    sqrt(value.toDouble())
                }
                is Long -> {
                    if (value < 0L) throw ArithmeticException("Square root of negative number: $value")
                    sqrt(value.toDouble())
                }
                is UByte -> sqrt(value.toDouble())
                else -> throw IllegalArgumentException("Sqrt operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies power function element-wise.
     */
    fun pow(tensor: Any, exponent: Double): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> value.pow(exponent.toFloat())
                is Double -> value.pow(exponent)
                is Int -> value.toDouble().pow(exponent)
                is Long -> value.toDouble().pow(exponent)
                is UByte -> value.toDouble().pow(exponent)
                else -> throw IllegalArgumentException("Pow operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies absolute value function element-wise.
     */
    fun abs(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> abs(value)
                is Double -> abs(value)
                is Int -> abs(value)
                is Long -> abs(value)
                is UByte -> value // UByte is always positive
                is Boolean -> if (value) 1 else 0
                else -> throw IllegalArgumentException("Abs operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies base-10 logarithm function element-wise.
     */
    fun log10(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> {
                    if (value <= 0f) throw ArithmeticException("Log10 of non-positive number: $value")
                    log10(value)
                }
                is Double -> {
                    if (value <= 0.0) throw ArithmeticException("Log10 of non-positive number: $value")
                    log10(value)
                }
                is Int -> {
                    if (value <= 0) throw ArithmeticException("Log10 of non-positive number: $value")
                    log10(value.toDouble())
                }
                is Long -> {
                    if (value <= 0L) throw ArithmeticException("Log10 of non-positive number: $value")
                    log10(value.toDouble())
                }
                is UByte -> {
                    if (value.toInt() == 0) throw ArithmeticException("Log10 of zero")
                    log10(value.toDouble())
                }
                else -> throw IllegalArgumentException("Log10 operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies base-2 logarithm function element-wise.
     */
    fun log2(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> {
                    if (value <= 0f) throw ArithmeticException("Log2 of non-positive number: $value")
                    log2(value)
                }
                is Double -> {
                    if (value <= 0.0) throw ArithmeticException("Log2 of non-positive number: $value")
                    log2(value)
                }
                is Int -> {
                    if (value <= 0) throw ArithmeticException("Log2 of non-positive number: $value")
                    log2(value.toDouble())
                }
                is Long -> {
                    if (value <= 0L) throw ArithmeticException("Log2 of non-positive number: $value")
                    log2(value.toDouble())
                }
                is UByte -> {
                    if (value.toInt() == 0) throw ArithmeticException("Log2 of zero")
                    log2(value.toDouble())
                }
                else -> throw IllegalArgumentException("Log2 operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies hyperbolic sine function element-wise.
     */
    fun sinh(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> sinh(value)
                is Double -> sinh(value)
                is Int -> sinh(value.toDouble())
                is Long -> sinh(value.toDouble())
                is UByte -> sinh(value.toDouble())
                else -> throw IllegalArgumentException("Sinh operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies hyperbolic cosine function element-wise.
     */
    fun cosh(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> cosh(value)
                is Double -> cosh(value)
                is Int -> cosh(value.toDouble())
                is Long -> cosh(value.toDouble())
                is UByte -> cosh(value.toDouble())
                else -> throw IllegalArgumentException("Cosh operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies floor function element-wise.
     */
    fun floor(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> floor(value)
                is Double -> floor(value)
                is Int -> value.toDouble() // Already integer
                is Long -> value.toDouble() // Already integer
                is UByte -> value.toDouble() // Already integer
                else -> throw IllegalArgumentException("Floor operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies ceiling function element-wise.
     */
    fun ceil(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> ceil(value)
                is Double -> ceil(value)
                is Int -> value.toDouble() // Already integer
                is Long -> value.toDouble() // Already integer
                is UByte -> value.toDouble() // Already integer
                else -> throw IllegalArgumentException("Ceil operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies modulo operation element-wise.
     */
    fun mod(tensor1: Any, tensor2: Any): Any {
        return applyBinaryMathFunction(tensor1, tensor2) { a, b ->
            when {
                a is Float && b is Float -> a % b
                a is Double && b is Double -> a % b
                a is Int && b is Int -> a % b
                a is Long && b is Long -> a % b
                a is UByte && b is UByte -> (a.toInt() % b.toInt()).toUByte()
                else -> {
                    val aVal = convertToDouble(a)
                    val bVal = convertToDouble(b)
                    if (bVal == 0.0) throw ArithmeticException("Division by zero in mod operation")
                    aVal % bVal
                }
            }
        }
    }
    
    /**
     * Applies clipping function element-wise.
     */
    fun clip(tensor: Any, minVal: Double, maxVal: Double): Any {
        if (minVal > maxVal) {
            throw IllegalArgumentException("minVal ($minVal) must be <= maxVal ($maxVal)")
        }
        
        return applyUnaryMathFunction(tensor) { value ->
            val doubleValue = convertToDouble(value)
            when {
                doubleValue < minVal -> minVal
                doubleValue > maxVal -> maxVal
                else -> doubleValue
            }
        }
    }
    
    /**
     * Applies negation function element-wise.
     */
    fun negative(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> -value
                is Double -> -value
                is Int -> -value
                is Long -> -value
                is UByte -> -(value.toInt())
                is Boolean -> if (value) 0 else 1 // Boolean negation as integer
                else -> throw IllegalArgumentException("Negative operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Applies sign function element-wise.
     */
    fun sign(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> sign(value)
                is Double -> sign(value)
                is Int -> sign(value.toDouble())
                is Long -> sign(value.toDouble())
                is UByte -> if (value.toInt() == 0) 0.0 else 1.0
                is Boolean -> if (value) 1.0 else 0.0
                else -> throw IllegalArgumentException("Sign operation not supported for type: ${value::class.simpleName}")
            }
        }
    }
    
    /**
     * Computes gradient along specified axis.
     */
    fun gradient(tensor: Any, axis: Int? = null): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // For now, implement 1D gradient (discrete difference)
        if (axis != null && (axis < 0 || axis >= t.shape.size)) {
            throw IllegalArgumentException("Axis $axis is out of bounds for tensor with ${t.shape.size} dimensions")
        }
        
        val gradAxis = axis ?: 0
        if (t.shape[gradAxis] < 2) {
            throw IllegalArgumentException("Cannot compute gradient along axis with size < 2")
        }
        
        val outputDType = when (t.dtype) {
            EmberDType.BOOL, EmberDType.UINT8, EmberDType.INT32, EmberDType.INT64 -> EmberDType.FLOAT64
            EmberDType.FLOAT32 -> EmberDType.FLOAT32
            EmberDType.FLOAT64 -> EmberDType.FLOAT64
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(outputDType, t.size)
        
        // Simple 1D gradient implementation (forward/backward differences)
        for (i in 0 until t.size) {
            val gradient = when {
                i == 0 -> {
                    // Forward difference
                    val curr = convertToDouble(getStorageValue(t.storage, i))
                    val next = convertToDouble(getStorageValue(t.storage, i + 1))
                    next - curr
                }
                i == t.size - 1 -> {
                    // Backward difference
                    val curr = convertToDouble(getStorageValue(t.storage, i))
                    val prev = convertToDouble(getStorageValue(t.storage, i - 1))
                    curr - prev
                }
                else -> {
                    // Central difference
                    val prev = convertToDouble(getStorageValue(t.storage, i - 1))
                    val next = convertToDouble(getStorageValue(t.storage, i + 1))
                    (next - prev) / 2.0
                }
            }
            setStorageValue(resultStorage, i, gradient, outputDType)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, t.shape, t.device)
    }
    
    /**
     * Element-wise greater than comparison.
     */
    fun greaterThan(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            when {
                a is Float && b is Float -> a > b
                a is Double && b is Double -> a > b
                a is Int && b is Int -> a > b
                a is Long && b is Long -> a > b
                a is UByte && b is UByte -> a > b
                a is Boolean && b is Boolean -> a && !b
                else -> {
                    // Convert to comparable values
                    val aVal = convertToDouble(a)
                    val bVal = convertToDouble(b)
                    aVal > bVal
                }
            }
        }
    }
    
    /**
     * Element-wise less than comparison.
     */
    fun lessThan(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            when {
                a is Float && b is Float -> a < b
                a is Double && b is Double -> a < b
                a is Int && b is Int -> a < b
                a is Long && b is Long -> a < b
                a is UByte && b is UByte -> a < b
                a is Boolean && b is Boolean -> !a && b
                else -> {
                    // Convert to comparable values
                    val aVal = convertToDouble(a)
                    val bVal = convertToDouble(b)
                    aVal < bVal
                }
            }
        }
    }
    
    /**
     * Element-wise equality comparison.
     */
    fun equal(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            when {
                a is Float && b is Float -> a == b
                a is Double && b is Double -> a == b
                a is Int && b is Int -> a == b
                a is Long && b is Long -> a == b
                a is UByte && b is UByte -> a == b
                a is Boolean && b is Boolean -> a == b
                else -> {
                    // Convert to comparable values
                    val aVal = convertToDouble(a)
                    val bVal = convertToDouble(b)
                    aVal == bVal
                }
            }
        }
    }
    
    /**
     * Element-wise not equal comparison.
     */
    fun notEqual(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            when {
                a is Float && b is Float -> a != b
                a is Double && b is Double -> a != b
                a is Int && b is Int -> a != b
                a is Long && b is Long -> a != b
                a is UByte && b is UByte -> a != b
                a is Boolean && b is Boolean -> a != b
                else -> {
                    val aVal = convertToDouble(a)
                    val bVal = convertToDouble(b)
                    aVal != bVal
                }
            }
        }
    }
    
    /**
     * Element-wise less than or equal comparison.
     */
    fun lessEqual(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            when {
                a is Float && b is Float -> a <= b
                a is Double && b is Double -> a <= b
                a is Int && b is Int -> a <= b
                a is Long && b is Long -> a <= b
                a is UByte && b is UByte -> a <= b
                a is Boolean && b is Boolean -> !a || b
                else -> {
                    val aVal = convertToDouble(a)
                    val bVal = convertToDouble(b)
                    aVal <= bVal
                }
            }
        }
    }
    
    /**
     * Element-wise greater than or equal comparison.
     */
    fun greaterEqual(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            when {
                a is Float && b is Float -> a >= b
                a is Double && b is Double -> a >= b
                a is Int && b is Int -> a >= b
                a is Long && b is Long -> a >= b
                a is UByte && b is UByte -> a >= b
                a is Boolean && b is Boolean -> a || !b
                else -> {
                    val aVal = convertToDouble(a)
                    val bVal = convertToDouble(b)
                    aVal >= bVal
                }
            }
        }
    }
    
    /**
     * Element-wise logical AND operation.
     */
    fun logicalAnd(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            convertToBoolean(a) && convertToBoolean(b)
        }
    }
    
    /**
     * Element-wise logical OR operation.
     */
    fun logicalOr(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            convertToBoolean(a) || convertToBoolean(b)
        }
    }
    
    /**
     * Element-wise logical NOT operation.
     */
    fun logicalNot(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            !convertToBoolean(value)
        }
    }
    
    /**
     * Element-wise logical XOR operation.
     */
    fun logicalXor(tensor1: Any, tensor2: Any): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            val aBool = convertToBoolean(a)
            val bBool = convertToBoolean(b)
            (aBool && !bBool) || (!aBool && bBool)
        }
    }
    
    /**
     * Check if values are NaN (Not a Number).
     */
    fun isNaN(tensor: Any): Any {
        return applyUnaryMathFunction(tensor) { value ->
            when (value) {
                is Float -> value.isNaN()
                is Double -> value.isNaN()
                else -> false // Non-floating point values cannot be NaN
            }
        }
    }
    
    /**
     * Element-wise close comparison within tolerance.
     */
    fun isClose(tensor1: Any, tensor2: Any, rtol: Double = 1e-05, atol: Double = 1e-08): Any {
        return applyBinaryComparisonFunction(tensor1, tensor2) { a, b ->
            val aVal = convertToDouble(a)
            val bVal = convertToDouble(b)
            val diff = abs(aVal - bVal)
            diff <= (atol + rtol * abs(bVal))
        }
    }
    
    /**
     * Test whether all array elements along given axes evaluate to True.
     */
    fun allClose(tensor1: Any, tensor2: Any, rtol: Double = 1e-05, atol: Double = 1e-08): Boolean {
        val isCloseResult = isClose(tensor1, tensor2, rtol, atol)
        val closeResultTensor = isCloseResult as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Check if all elements are true
        for (i in 0 until closeResultTensor.size) {
            val value = getStorageValue(closeResultTensor.storage, i)
            if (!convertToBoolean(value)) {
                return false
            }
        }
        return true
    }
    
    /**
     * Select elements from tensors based on condition.
     */
    fun where(condition: Any, x: Any, y: Any): Any {
        val condTensor = condition as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val xTensor = x as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val yTensor = y as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (!condTensor.shape.contentEquals(xTensor.shape) || !condTensor.shape.contentEquals(yTensor.shape)) {
            throw IllegalArgumentException("All tensors must have the same shape")
        }
        
        // Determine output dtype based on x and y tensors
        val outputDType = when {
            xTensor.dtype == yTensor.dtype -> xTensor.dtype
            (xTensor.dtype == EmberDType.FLOAT64 || yTensor.dtype == EmberDType.FLOAT64) -> EmberDType.FLOAT64
            (xTensor.dtype == EmberDType.FLOAT32 || yTensor.dtype == EmberDType.FLOAT32) -> EmberDType.FLOAT32
            (xTensor.dtype == EmberDType.INT64 || yTensor.dtype == EmberDType.INT64) -> EmberDType.INT64
            else -> EmberDType.INT32
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(outputDType, condTensor.size)
        
        for (i in 0 until condTensor.size) {
            val condValue = getStorageValue(condTensor.storage, i)
            val selectedValue = if (convertToBoolean(condValue)) {
                getStorageValue(xTensor.storage, i)
            } else {
                getStorageValue(yTensor.storage, i)
            }
            setStorageValue(resultStorage, i, selectedValue, outputDType)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, condTensor.shape, condTensor.device)
    }
    
    // Helper functions
    
    private fun applyUnaryMathFunction(tensor: Any, operation: (Any) -> Any): Any {
        val t = tensor as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Determine output data type (usually promote to float/double for math operations)
        val outputDType = when (t.dtype) {
            EmberDType.BOOL, EmberDType.UINT8, EmberDType.INT32 -> EmberDType.FLOAT64
            EmberDType.INT64 -> EmberDType.FLOAT64
            EmberDType.FLOAT32 -> EmberDType.FLOAT32
            EmberDType.FLOAT64 -> EmberDType.FLOAT64
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(outputDType, t.size)
        
        for (i in 0 until t.size) {
            val inputValue = getStorageValue(t.storage, i)
            val outputValue = operation(inputValue)
            setStorageValue(resultStorage, i, outputValue, outputDType)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, t.shape, t.device)
    }
    
    private fun applyBinaryMathFunction(tensor1: Any, tensor2: Any, operation: (Any, Any) -> Any): Any {
        val t1 = tensor1 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (!t1.shape.contentEquals(t2.shape)) {
            throw IllegalArgumentException("Shape mismatch: ${t1.shape.contentToString()} vs ${t2.shape.contentToString()}")
        }
        
        // Determine output dtype based on input dtypes
        val outputDType = when {
            t1.dtype == EmberDType.FLOAT64 || t2.dtype == EmberDType.FLOAT64 -> EmberDType.FLOAT64
            t1.dtype == EmberDType.FLOAT32 || t2.dtype == EmberDType.FLOAT32 -> EmberDType.FLOAT32
            t1.dtype == EmberDType.INT64 || t2.dtype == EmberDType.INT64 -> EmberDType.INT64
            else -> EmberDType.INT32
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(outputDType, t1.size)
        
        for (i in 0 until t1.size) {
            val value1 = getStorageValue(t1.storage, i)
            val value2 = getStorageValue(t2.storage, i)
            val result = operation(value1, value2)
            setStorageValue(resultStorage, i, result, outputDType)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, t1.shape, t1.device)
    }
    
    private fun applyBinaryComparisonFunction(tensor1: Any, tensor2: Any, operation: (Any, Any) -> Boolean): Any {
        val t1 = tensor1 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val t2 = tensor2 as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        if (!t1.shape.contentEquals(t2.shape)) {
            throw IllegalArgumentException("Shape mismatch: ${t1.shape.contentToString()} vs ${t2.shape.contentToString()}")
        }
        
        val resultStorage = TensorStorage.createOptimalStorage(EmberDType.BOOL, t1.size)
        
        for (i in 0 until t1.size) {
            val value1 = getStorageValue(t1.storage, i)
            val value2 = getStorageValue(t2.storage, i)
            val result = operation(value1, value2)
            setStorageValue(resultStorage, i, result, EmberDType.BOOL)
        }
        
        return OptimizedMegaTensorBackend.OptimizedMegaTensor(resultStorage, t1.shape, t1.device)
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
    
    private fun setStorageValue(storage: TensorStorage, index: Int, value: Any, dtype: EmberDType) {
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
                // For MegaNumber storage, we would need to convert appropriately
                throw UnsupportedOperationException("MegaNumber storage not yet implemented for math operations")
            }
        }
    }
    
    private fun convertToBoolean(value: Any): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            else -> false
        }
    }
    
    private fun convertToUByte(value: Any): UByte {
        return when (value) {
            is Number -> value.toInt().coerceIn(0, 255).toUByte()
            is Boolean -> if (value) 1u else 0u
            else -> 0u
        }
    }
    
    private fun convertToInt(value: Any): Int {
        return when (value) {
            is Number -> value.toInt()
            is Boolean -> if (value) 1 else 0
            else -> 0
        }
    }
    
    private fun convertToLong(value: Any): Long {
        return when (value) {
            is Number -> value.toLong()
            is Boolean -> if (value) 1L else 0L
            else -> 0L
        }
    }
    
    private fun convertToFloat(value: Any): Float {
        return when (value) {
            is Number -> value.toFloat()
            is Boolean -> if (value) 1f else 0f
            else -> 0f
        }
    }
    
    private fun convertToDouble(value: Any): Double {
        return when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            else -> 0.0
        }
    }
}