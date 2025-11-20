# Ember ML Kotlin Architecture Design

**Date**: 2025-01-XX  
**Status**: Design Phase  
**Goal**: Pure Kotlin ML framework with MLX-inspired API patterns

## Vision

Ember ML Kotlin will **BE** the backend, not select between backends. We're building a pure Kotlin ML framework with:

1. **KLang Foundation**: Bit-exact cross-platform numerics (CFloat16, CFloat32, CFloat64, CFloat128)
2. **MLX-Inspired API**: `Ember.Tensor()`, `Ember.Dtype()` patterns
3. **Scalar-First Design**: Like MLX, everything is an array/tensor
4. **Metal-Ready**: C++ interop hooks for future GPU support

## Core Principles

### 1. Single Backend Philosophy

```kotlin
// ❌ Old way (multiple backends)
set_backend("torch")  
val tensor = TensorFactory.create()

// ✅ New way (Ember IS the backend)
val tensor = Ember.Tensor(data)
val dtype = Ember.Dtype.float32
```

### 2. MLX-Style API Patterns

```kotlin
// Python MLX:
// import mlx.core as mx
// x = mx.array([1, 2, 3])
// y = mx.sin(x)

// Kotlin Ember:
import ai.solace.ember.Ember
val x = Ember.array(listOf(1f, 2f, 3f))
val y = Ember.sin(x)

// Or with aliasing:
typealias Tensor = Ember.Tensor
val x = Tensor(listOf(1f, 2f, 3f))
```

### 3. Scalars Are Tensors

```kotlin
// Scalars are 0-dimensional tensors (like MLX)
val scalar = Ember.array(5.0f)  // shape = []
val vector = Ember.array(listOf(1f, 2f, 3f))  // shape = [3]
val matrix = Ember.array(listOf(listOf(1f, 2f), listOf(3f, 4f)))  // shape = [2, 2]
```

## Type System with KLang

### DType Hierarchy

```kotlin
package ai.solace.ember.dtype

import ai.solace.klang.bitwise.CFloat32
import ai.solace.klang.fp.CFloat16  // To be implemented
import ai.solace.klang.fp.CFloat64  // To be implemented
import ai.solace.klang.fp.CFloat128 // To be implemented

/**
 * Ember data type system based on KLang soft-float types.
 * 
 * These provide bit-exact cross-platform arithmetic.
 */
sealed class EmberDType(val name: String, val byteSize: Int) {
    // Floating point types (backed by KLang)
    object Float16 : EmberDType("float16", 2)   // CFloat16
    object Float32 : EmberDType("float32", 4)   // CFloat32
    object Float64 : EmberDType("float64", 8)   // CFloat64
    object Float128 : EmberDType("float128", 16) // CFloat128 (future)
    
    // BFloat16 for ML (backed by KLang)
    object BFloat16 : EmberDType("bfloat16", 2) // CBF16
    
    // Integer types (native Kotlin types for now)
    object Int8 : EmberDType("int8", 1)
    object Int16 : EmberDType("int16", 2)
    object Int32 : EmberDType("int32", 4)
    object Int64 : EmberDType("int64", 8)
    
    object UInt8 : EmberDType("uint8", 1)
    object UInt16 : EmberDType("uint16", 2)
    object UInt32 : EmberDType("uint32", 4)
    object UInt64 : EmberDType("uint64", 8)
    
    // Boolean
    object Bool : EmberDType("bool", 1)
    
    // Complex types (future)
    object Complex64 : EmberDType("complex64", 8)
    object Complex128 : EmberDType("complex128", 16)
    
    // Quantized types (from llama.kotlin)
    object Q4_0 : EmberDType("q4_0", 18)  // 4-bit quantization
    object Q8_0 : EmberDType("q8_0", 34)  // 8-bit quantization
}
```

### Scalar Wrappers

```kotlin
package ai.solace.ember.scalar

import ai.solace.klang.bitwise.CFloat32
import ai.solace.klang.fp.CFloat16
import ai.solace.klang.fp.CFloat64

/**
 * Scalar value wrapper that uses KLang for bit-exact arithmetic.
 */
sealed class Scalar {
    abstract val dtype: EmberDType
    abstract fun toDouble(): Double
    abstract fun toFloat(): Float
    
    // Float16 scalar (16-bit float)
    data class Float16(val value: CFloat16) : Scalar() {
        override val dtype = EmberDType.Float16
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        
        operator fun plus(other: Float16) = Float16(value + other.value)
        operator fun minus(other: Float16) = Float16(value - other.value)
        operator fun times(other: Float16) = Float16(value * other.value)
        operator fun div(other: Float16) = Float16(value / other.value)
    }
    
    // Float32 scalar (32-bit float - backed by CFloat32)
    data class Float32(val value: CFloat32) : Scalar() {
        override val dtype = EmberDType.Float32
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        
        operator fun plus(other: Float32) = Float32(value + other.value)
        operator fun minus(other: Float32) = Float32(value - other.value)
        operator fun times(other: Float32) = Float32(value * other.value)
        operator fun div(other: Float32) = Float32(value / other.value)
    }
    
    // Float64 scalar (64-bit float - backed by CFloat64)
    data class Float64(val value: CFloat64) : Scalar() {
        override val dtype = EmberDType.Float64
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        
        operator fun plus(other: Float64) = Float64(value + other.value)
        operator fun minus(other: Float64) = Float64(value - other.value)
        operator fun times(other: Float64) = Float64(value * other.value)
        operator fun div(other: Float64) = Float64(value / other.value)
    }
    
    // Integer scalars (native Kotlin for now)
    data class Int32(val value: Int) : Scalar() {
        override val dtype = EmberDType.Int32
        override fun toDouble() = value.toDouble()
        override fun toFloat() = value.toFloat()
        
        operator fun plus(other: Int32) = Int32(value + other.value)
        operator fun minus(other: Int32) = Int32(value - other.value)
        operator fun times(other: Int32) = Int32(value * other.value)
        operator fun div(other: Int32) = Int32(value / other.value)
    }
}
```

## Tensor API

### Core Tensor Class

```kotlin
package ai.solace.ember.tensor

import ai.solace.ember.dtype.EmberDType
import ai.solace.ember.scalar.Scalar

/**
 * Ember Tensor - the fundamental data structure.
 * 
 * Like MLX, everything is a tensor (including scalars).
 * Backed by KLang for cross-platform determinism.
 */
class EmberTensor internal constructor(
    private val data: Any,  // Storage: Array, CFloat32Array, etc.
    val shape: IntArray,
    val dtype: EmberDType,
    val device: String = "cpu"
) {
    /** Number of dimensions */
    val ndim: Int get() = shape.size
    
    /** Total number of elements */
    val size: Int get() = shape.fold(1) { acc, dim -> acc * dim }
    
    /** Is this a scalar (0-dimensional tensor)? */
    val isScalar: Boolean get() = ndim == 0
    
    // ============================================
    // Arithmetic operators (element-wise)
    // ============================================
    
    operator fun plus(other: EmberTensor): EmberTensor {
        return Ember.add(this, other)
    }
    
    operator fun minus(other: EmberTensor): EmberTensor {
        return Ember.subtract(this, other)
    }
    
    operator fun times(other: EmberTensor): EmberTensor {
        return Ember.multiply(this, other)
    }
    
    operator fun div(other: EmberTensor): EmberTensor {
        return Ember.divide(this, other)
    }
    
    // Scalar overloads
    operator fun plus(other: Float): EmberTensor = this + Ember.array(other)
    operator fun minus(other: Float): EmberTensor = this - Ember.array(other)
    operator fun times(other: Float): EmberTensor = this * Ember.array(other)
    operator fun div(other: Float): EmberTensor = this / Ember.array(other)
    
    // ============================================
    // Indexing
    // ============================================
    
    operator fun get(vararg indices: Int): EmberTensor {
        return Ember.getElement(this, indices)
    }
    
    operator fun set(vararg indices: Int, value: EmberTensor) {
        Ember.setElement(this, indices, value)
    }
    
    // ============================================
    // Shape operations
    // ============================================
    
    fun reshape(vararg newShape: Int): EmberTensor {
        return Ember.reshape(this, newShape)
    }
    
    fun transpose(vararg axes: Int): EmberTensor {
        return if (axes.isEmpty()) {
            Ember.transpose(this, null)
        } else {
            Ember.transpose(this, axes)
        }
    }
    
    fun squeeze(axis: Int? = null): EmberTensor {
        return Ember.squeeze(this, axis)
    }
    
    fun unsqueeze(axis: Int): EmberTensor {
        return Ember.expandDims(this, axis)
    }
    
    // ============================================
    // Type conversion
    // ============================================
    
    fun asType(dtype: EmberDType): EmberTensor {
        return Ember.cast(this, dtype)
    }
    
    fun toScalar(): Scalar {
        require(isScalar) { "Cannot convert non-scalar tensor to scalar" }
        return Ember.item(this)
    }
    
    fun toFloatArray(): FloatArray {
        return Ember.toArray(this)
    }
    
    // ============================================
    // Math operations (instance methods)
    // ============================================
    
    fun sin(): EmberTensor = Ember.sin(this)
    fun cos(): EmberTensor = Ember.cos(this)
    fun tan(): EmberTensor = Ember.tan(this)
    fun exp(): EmberTensor = Ember.exp(this)
    fun log(): EmberTensor = Ember.log(this)
    fun sqrt(): EmberTensor = Ember.sqrt(this)
    fun square(): EmberTensor = Ember.square(this)
    fun abs(): EmberTensor = Ember.abs(this)
    
    // Reduction operations
    fun sum(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return Ember.sum(this, axis, keepDims)
    }
    
    fun mean(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return Ember.mean(this, axis, keepDims)
    }
    
    fun max(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return Ember.max(this, axis, keepDims)
    }
    
    fun min(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return Ember.min(this, axis, keepDims)
    }
    
    // Matrix operations
    infix fun matmul(other: EmberTensor): EmberTensor {
        return Ember.matmul(this, other)
    }
    
    override fun toString(): String {
        return "EmberTensor(shape=${shape.contentToString()}, dtype=$dtype)"
    }
}
```

### Main Ember API Object

```kotlin
package ai.solace.ember

import ai.solace.ember.dtype.EmberDType
import ai.solace.ember.tensor.EmberTensor
import ai.solace.ember.scalar.Scalar
import ai.solace.klang.bitwise.CFloat32

/**
 * Ember - Main API entry point.
 * 
 * MLX-inspired interface:
 *   import ai.solace.ember.Ember
 *   val x = Ember.array(listOf(1f, 2f, 3f))
 *   val y = Ember.sin(x)
 */
object Ember {
    
    // ============================================
    // Dtype namespace (like mx.Dtype)
    // ============================================
    
    object Dtype {
        val float16 = EmberDType.Float16
        val float32 = EmberDType.Float32
        val float64 = EmberDType.Float64
        val bfloat16 = EmberDType.BFloat16
        
        val int8 = EmberDType.Int8
        val int16 = EmberDType.Int16
        val int32 = EmberDType.Int32
        val int64 = EmberDType.Int64
        
        val uint8 = EmberDType.UInt8
        val uint16 = EmberDType.UInt16
        val uint32 = EmberDType.UInt32
        val uint64 = EmberDType.UInt64
        
        val bool = EmberDType.Bool
    }
    
    // ============================================
    // Tensor creation (like mx.array)
    // ============================================
    
    /**
     * Create a tensor from data.
     * 
     * Examples:
     *   Ember.array(5.0f)              // scalar, shape = []
     *   Ember.array(listOf(1f, 2f))    // vector, shape = [2]
     *   Ember.array(listOf(           // matrix, shape = [2, 2]
     *     listOf(1f, 2f),
     *     listOf(3f, 4f)
     *   ))
     */
    fun array(data: Any, dtype: EmberDType = Dtype.float32): EmberTensor {
        return when (data) {
            is Number -> {
                // Scalar: 0-dimensional tensor
                val scalar = convertToScalar(data, dtype)
                EmberTensor(scalar, intArrayOf(), dtype)
            }
            is List<*> -> {
                // Infer shape and create tensor
                val (shape, flatData) = inferShapeAndFlatten(data)
                val storage = createStorage(flatData, dtype)
                EmberTensor(storage, shape, dtype)
            }
            is FloatArray -> {
                val storage = createStorage(data.toList(), dtype)
                EmberTensor(storage, intArrayOf(data.size), dtype)
            }
            is IntArray -> {
                val storage = createStorage(data.toList(), dtype)
                EmberTensor(storage, intArrayOf(data.size), dtype)
            }
            else -> throw IllegalArgumentException("Unsupported data type: ${data::class}")
        }
    }
    
    // Convenience function
    fun Tensor(data: Any, dtype: EmberDType = Dtype.float32) = array(data, dtype)
    
    // ============================================
    // Creation functions (like mx.zeros, mx.ones)
    // ============================================
    
    fun zeros(vararg shape: Int, dtype: EmberDType = Dtype.float32): EmberTensor {
        val size = shape.fold(1) { acc, dim -> acc * dim }
        val storage = when (dtype) {
            EmberDType.Float32 -> Array(size) { CFloat32.fromFloat(0f) }
            // ... other types
            else -> TODO("Implement for dtype: $dtype")
        }
        return EmberTensor(storage, shape, dtype)
    }
    
    fun ones(vararg shape: Int, dtype: EmberDType = Dtype.float32): EmberTensor {
        val size = shape.fold(1) { acc, dim -> acc * dim }
        val storage = when (dtype) {
            EmberDType.Float32 -> Array(size) { CFloat32.fromFloat(1f) }
            // ... other types
            else -> TODO("Implement for dtype: $dtype")
        }
        return EmberTensor(storage, shape, dtype)
    }
    
    fun arange(start: Int, stop: Int, step: Int = 1, dtype: EmberDType = Dtype.int32): EmberTensor {
        val values = (start until stop step step).toList()
        return array(values, dtype)
    }
    
    fun linspace(start: Float, stop: Float, num: Int, dtype: EmberDType = Dtype.float32): EmberTensor {
        val step = (stop - start) / (num - 1)
        val values = (0 until num).map { start + it * step }
        return array(values, dtype)
    }
    
    // ============================================
    // Math operations (like mx.sin, mx.cos)
    // ============================================
    
    fun add(a: EmberTensor, b: EmberTensor): EmberTensor {
        require(a.shape.contentEquals(b.shape) || canBroadcast(a.shape, b.shape)) {
            "Shapes must match or be broadcastable"
        }
        // Implementation using KLang
        TODO("Implement element-wise addition")
    }
    
    fun subtract(a: EmberTensor, b: EmberTensor): EmberTensor {
        TODO("Implement element-wise subtraction")
    }
    
    fun multiply(a: EmberTensor, b: EmberTensor): EmberTensor {
        TODO("Implement element-wise multiplication")
    }
    
    fun divide(a: EmberTensor, b: EmberTensor): EmberTensor {
        TODO("Implement element-wise division")
    }
    
    // Trigonometric
    fun sin(x: EmberTensor): EmberTensor {
        // Use KLang Float32Math for bit-exact sin
        TODO("Implement using Float32Math")
    }
    
    fun cos(x: EmberTensor): EmberTensor {
        TODO("Implement using Float32Math")
    }
    
    fun tan(x: EmberTensor): EmberTensor {
        TODO("Implement using Float32Math")
    }
    
    // Exponential/Log
    fun exp(x: EmberTensor): EmberTensor {
        TODO("Implement using Float32Math")
    }
    
    fun log(x: EmberTensor): EmberTensor {
        TODO("Implement using Float32Math")
    }
    
    fun sqrt(x: EmberTensor): EmberTensor {
        TODO("Implement using Float32Math")
    }
    
    // Other
    fun square(x: EmberTensor): EmberTensor = multiply(x, x)
    
    fun abs(x: EmberTensor): EmberTensor {
        TODO("Implement absolute value")
    }
    
    // ============================================
    // Reduction operations
    // ============================================
    
    fun sum(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        TODO("Implement sum reduction")
    }
    
    fun mean(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        TODO("Implement mean reduction")
    }
    
    fun max(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        TODO("Implement max reduction")
    }
    
    fun min(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        TODO("Implement min reduction")
    }
    
    // ============================================
    // Matrix operations
    // ============================================
    
    fun matmul(a: EmberTensor, b: EmberTensor): EmberTensor {
        require(a.ndim >= 2 && b.ndim >= 2) {
            "Matrix multiplication requires at least 2D tensors"
        }
        TODO("Implement matrix multiplication")
    }
    
    // ============================================
    // Shape operations
    // ============================================
    
    fun reshape(x: EmberTensor, shape: IntArray): EmberTensor {
        require(x.size == shape.fold(1) { acc, dim -> acc * dim }) {
            "New shape must have same number of elements"
        }
        return EmberTensor(x.data, shape, x.dtype, x.device)
    }
    
    fun transpose(x: EmberTensor, axes: IntArray?): EmberTensor {
        TODO("Implement transpose")
    }
    
    fun squeeze(x: EmberTensor, axis: Int?): EmberTensor {
        TODO("Implement squeeze")
    }
    
    fun expandDims(x: EmberTensor, axis: Int): EmberTensor {
        TODO("Implement expand_dims")
    }
    
    // ============================================
    // Type conversion
    // ============================================
    
    fun cast(x: EmberTensor, dtype: EmberDType): EmberTensor {
        if (x.dtype == dtype) return x
        TODO("Implement type casting using KLang conversions")
    }
    
    fun item(x: EmberTensor): Scalar {
        require(x.isScalar) { "Can only extract scalar from 0-dimensional tensor" }
        TODO("Extract scalar value")
    }
    
    fun toArray(x: EmberTensor): FloatArray {
        TODO("Convert tensor to FloatArray")
    }
    
    // ============================================
    // Helper functions
    // ============================================
    
    private fun convertToScalar(value: Number, dtype: EmberDType): Scalar {
        return when (dtype) {
            EmberDType.Float32 -> Scalar.Float32(CFloat32.fromFloat(value.toFloat()))
            // ... other types
            else -> TODO("Implement for dtype: $dtype")
        }
    }
    
    private fun inferShapeAndFlatten(data: List<*>): Pair<IntArray, List<Any>> {
        // Recursively infer shape and flatten nested lists
        TODO("Implement shape inference")
    }
    
    private fun createStorage(data: List<Any>, dtype: EmberDType): Any {
        return when (dtype) {
            EmberDType.Float32 -> {
                data.map { CFloat32.fromFloat((it as Number).toFloat()) }.toTypedArray()
            }
            // ... other types
            else -> TODO("Implement for dtype: $dtype")
        }
    }
    
    private fun canBroadcast(shape1: IntArray, shape2: IntArray): Boolean {
        // NumPy-style broadcasting rules
        TODO("Implement broadcasting check")
    }
}

// ============================================
// Type aliases for convenience
// ============================================

typealias Tensor = EmberTensor
```

## Implementation Phases

### Phase 1: KLang Float Types (Week 1) ✅

Already done!
- ✅ CFloat32 with full operators
- ✅ Float32Math (29KB of compiler-rt ports)
- ✅ HPC16x4 / HPC16x8 limb engine

**TODO:**
- [ ] Implement CFloat16 (port from llama.kotlin or compiler-rt)
- [ ] Implement CFloat64 (use HPC16x8 for 128-bit intermediates)
- [ ] Implement CFloat128 (optional, future)

### Phase 2: Scalar System (Week 1-2)

**Goal**: Get scalars working with KLang

```kotlin
// Create scalars
val x = Scalar.Float32(CFloat32.fromFloat(5.0f))
val y = Scalar.Float32(CFloat32.fromFloat(3.0f))

// Arithmetic works
val sum = x + y  // Uses CFloat32 operators
val product = x * y

// Conversion
x.toFloat()  // Native Kotlin Float
x.toDouble() // Native Kotlin Double
```

**Files to create:**
- `src/commonMain/kotlin/ai/solace/ember/scalar/Scalar.kt`
- `src/commonMain/kotlin/ai/solace/ember/scalar/Float16Scalar.kt`
- `src/commonMain/kotlin/ai/solace/ember/scalar/Float32Scalar.kt`
- `src/commonMain/kotlin/ai/solace/ember/scalar/Float64Scalar.kt`
- `src/commonTest/kotlin/ai/solace/ember/scalar/ScalarTest.kt`

### Phase 3: DType System (Week 2)

**Goal**: Clean dtype hierarchy

**Files to create:**
- `src/commonMain/kotlin/ai/solace/ember/dtype/EmberDType.kt`
- `src/commonMain/kotlin/ai/solace/ember/dtype/TypeConversions.kt`
- `src/commonTest/kotlin/ai/solace/ember/dtype/DTypeTest.kt`

### Phase 4: Storage System (Week 2-3)

**Goal**: Efficient tensor data storage

**Options:**
1. **Typed Arrays**: `Array<CFloat32>`, `Array<CFloat16>`, etc.
2. **Columnar Storage**: Like Apache Arrow
3. **Hybrid**: Different storage for different dtypes

**Decision**: Start with typed arrays for simplicity.

**Files to create:**
- `src/commonMain/kotlin/ai/solace/ember/storage/TensorStorage.kt`
- `src/commonMain/kotlin/ai/solace/ember/storage/Float32Storage.kt`
- `src/commonMain/kotlin/ai/solace/ember/storage/Float16Storage.kt`

### Phase 5: Basic Tensor Operations (Week 3-4)

**Goal**: Get `Ember.array()` working

**Priorities:**
1. Tensor creation (array, zeros, ones)
2. Element-wise ops (add, multiply) using KLang
3. Shape operations (reshape, transpose)
4. Indexing (get/set elements)

**Files to create:**
- `src/commonMain/kotlin/ai/solace/ember/tensor/EmberTensor.kt`
- `src/commonMain/kotlin/ai/solace/ember/tensor/TensorCreation.kt`
- `src/commonMain/kotlin/ai/solace/ember/tensor/TensorOps.kt`
- `src/commonMain/kotlin/ai/solace/ember/tensor/ShapeOps.kt`

### Phase 6: Math Operations (Week 4-5)

**Goal**: Port MLX math_ops using KLang

**Use Float32Math for:**
- sin, cos, tan
- exp, log, sqrt
- Type conversions

**Files to create:**
- `src/commonMain/kotlin/ai/solace/ember/ops/MathOps.kt`
- `src/commonMain/kotlin/ai/solace/ember/ops/TrigOps.kt`
- `src/commonMain/kotlin/ai/solace/ember/ops/ExpLogOps.kt`

### Phase 7: Main Ember API (Week 5)

**Goal**: MLX-style top-level API

**Files to create:**
- `src/commonMain/kotlin/ai/solace/ember/Ember.kt` (main entry point)
- `src/commonMain/kotlin/ai/solace/ember/EmberDsl.kt` (DSL helpers)

### Phase 8: Broadcasting & Advanced Ops (Week 6-7)

**Goal**: NumPy-style broadcasting

**Files to create:**
- `src/commonMain/kotlin/ai/solace/ember/broadcast/Broadcasting.kt`
- `src/commonMain/kotlin/ai/solace/ember/ops/ReductionOps.kt`
- `src/commonMain/kotlin/ai/solace/ember/ops/MatrixOps.kt`

### Phase 9: SWAR Integration (Week 7-8)

**Goal**: Use SwAR for 2-4x speedups

**For operations on packed data:**
- Quantized tensor operations
- Batch processing
- SIMD-like performance without SIMD

### Phase 10: Metal Hooks (Week 9+)

**Goal**: Prepare for C++ Metal interop

**Design C++ boundary:**
```kotlin
expect class MetalTensor {
    fun matmul(other: MetalTensor): MetalTensor
}

// On Apple platforms:
actual class MetalTensor {
    // Calls into Metal via C interop
}

// On other platforms:
actual class MetalTensor {
    // Falls back to Ember CPU implementation
}
```

## File Structure

```
src/commonMain/kotlin/ai/solace/
├── ember/                          # Main Ember API
│   ├── Ember.kt                    # Entry point (like mlx.core)
│   ├── dtype/
│   │   ├── EmberDType.kt          # DType hierarchy
│   │   └── TypeConversions.kt     # KLang conversions
│   ├── scalar/
│   │   └── Scalar.kt              # Scalar wrappers
│   ├── tensor/
│   │   ├── EmberTensor.kt         # Main tensor class
│   │   ├── TensorCreation.kt      # array, zeros, ones
│   │   └── TensorOps.kt           # Core operations
│   ├── storage/
│   │   └── TensorStorage.kt       # Storage strategies
│   ├── ops/
│   │   ├── MathOps.kt             # sin, cos, exp, log
│   │   ├── ReductionOps.kt        # sum, mean, max, min
│   │   ├── MatrixOps.kt           # matmul, dot
│   │   └── ShapeOps.kt            # reshape, transpose
│   ├── broadcast/
│   │   └── Broadcasting.kt        # NumPy-style broadcasting
│   └── nn/                         # Neural network ops (future)
│       ├── Linear.kt
│       ├── Conv.kt
│       └── Activations.kt
└── klang/                          # Already ported! ✅
    ├── bitwise/                    # CFloat32, SwAR, etc.
    ├── int/hpc/                    # HPC16x4, HPC16x8
    └── fp/                         # CFloat16, CFloat64 (TODO)
```

## Testing Strategy

### Unit Tests

```kotlin
class ScalarTest {
    @Test
    fun testFloat32Arithmetic() {
        val a = Scalar.Float32(CFloat32.fromFloat(5.0f))
        val b = Scalar.Float32(CFloat32.fromFloat(3.0f))
        
        val sum = a + b
        assertEquals(8.0f, sum.toFloat(), 0.0001f)
        
        val product = a * b
        assertEquals(15.0f, product.toFloat(), 0.0001f)
    }
    
    @Test
    fun testCrossPlatformDeterminism() {
        // Same operations produce same bits on all platforms
        val a = Scalar.Float32(CFloat32.fromFloat(1.5f))
        val b = Scalar.Float32(CFloat32.fromFloat(2.5f))
        
        val result = (a * b) + Scalar.Float32(CFloat32.fromFloat(3.5f))
        
        // Exact bit pattern
        assertEquals(0x41080000, result.value.toBits())
    }
}

class TensorTest {
    @Test
    fun testArrayCreation() {
        val t = Ember.array(listOf(1f, 2f, 3f))
        
        assertEquals(1, t.ndim)
        assertContentEquals(intArrayOf(3), t.shape)
        assertEquals(EmberDType.Float32, t.dtype)
    }
    
    @Test
    fun testScalarTensor() {
        val t = Ember.array(5.0f)
        
        assertTrue(t.isScalar)
        assertEquals(0, t.ndim)
        assertContentEquals(intArrayOf(), t.shape)
    }
    
    @Test
    fun testArithmetic() {
        val a = Ember.array(listOf(1f, 2f, 3f))
        val b = Ember.array(listOf(4f, 5f, 6f))
        
        val sum = a + b
        
        // Verify using KLang bit-exact arithmetic
        val expected = Ember.array(listOf(5f, 7f, 9f))
        assertTrue(sum.shape.contentEquals(expected.shape))
    }
}
```

### Integration Tests

```kotlin
class EmberAPITest {
    @Test
    fun testMLXStyleAPI() {
        // MLX-style workflow
        val x = Ember.array(listOf(0f, PI/4, PI/2))
        val y = Ember.sin(x)
        
        // Verify bit-exact results
        val expected = listOf(0f, 0.707106781f, 1f)
        // Compare using KLang
    }
    
    @Test
    fun testAliasing() {
        // Type alias works
        val t: Tensor = Ember.array(listOf(1f, 2f, 3f))
        assertTrue(t is EmberTensor)
    }
}
```

## Success Criteria

### Phase 1-2 (Scalars) ✅
- [ ] CFloat16 implemented
- [ ] CFloat64 implemented
- [ ] Scalar.Float16/32/64 wrappers work
- [ ] All arithmetic operators functional
- [ ] Cross-platform bit-exact results

### Phase 3-5 (Basic Tensors)
- [ ] `Ember.array()` works for scalars, vectors, matrices
- [ ] Element-wise add/multiply using KLang
- [ ] Shape operations (reshape, transpose)
- [ ] Indexing works

### Phase 6-7 (Math & API)
- [ ] sin, cos, exp, log using Float32Math
- [ ] `Ember.Dtype` namespace works
- [ ] MLX-style API feels natural
- [ ] Type aliases work

### Phase 8-9 (Advanced)
- [ ] Broadcasting implemented
- [ ] Reduction operations
- [ ] Matrix multiplication
- [ ] SWAR integration for speedups

## Why This Architecture?

### 1. **Simplicity**
No backend selection, no runtime switching. Ember IS the ML framework.

### 2. **Determinism**
KLang provides bit-exact results everywhere. Critical for ML reproducibility.

### 3. **Performance**
- SWAR for 2-4x speedups without SIMD
- Quantization support from llama.kotlin
- Future Metal/GPU via C++ interop

### 4. **Familiarity**
MLX-inspired API means Python ML engineers feel at home.

### 5. **Pure Kotlin**
No JNI, no native dependencies for correctness. Runs on all Kotlin targets.

## Next Immediate Actions

1. **Implement CFloat16** (1-2 days)
   - Port from llama.kotlin or compiler-rt
   - Add tests from llama.kotlin

2. **Implement CFloat64** (2-3 days)
   - Use HPC16x8 for 128-bit intermediates
   - Port Float64Math from compiler-rt

3. **Create Scalar wrappers** (1 day)
   - `Scalar.Float32` using CFloat32
   - Basic arithmetic
   - Tests

4. **Create EmberDType** (1 day)
   - Sealed class hierarchy
   - Type properties

5. **Prototype `Ember.array()`** (2-3 days)
   - Scalar tensors work
   - Simple vectors work
   - Shape inference

**Then we can build the full API on this solid foundation!** 🚀

---

**Remember**: KLang + Ember = Pure Kotlin ML dominance.
