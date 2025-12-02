# Ember ML Kotlin - Quick Start Guide

## Introduction

Ember ML Kotlin is a cross-platform machine learning framework with an MLX-inspired API. It provides:

- ✨ **Simple, intuitive API** - MLX-style tensor operations
- 🎯 **Cross-platform** - Works on macOS, Linux, Windows, JVM
- 🔢 **Bit-exact numerics** - KLang integration for deterministic results
- 🚀 **Modern Kotlin** - Leverages Kotlin's expressive syntax

## Quick Start

### Creating Tensors

```kotlin
import ai.solace.ember.Ember

// From a list
val x = Ember.array(listOf(1.0f, 2.0f, 3.0f))

// Factory functions
val zeros = Ember.zeros(intArrayOf(3, 3))
val ones = Ember.ones(intArrayOf(5))
val eye = Ember.eye(4)  // Identity matrix
val range = Ember.arange(0, 10, 2)
val linspace = Ember.linspace(0.0, 1.0, 50)
```

### Element-wise Operations

```kotlin
val a = Ember.array(listOf(1.0f, 2.0f, 3.0f))
val b = Ember.array(listOf(4.0f, 5.0f, 6.0f))

val sum = a + b
val product = a * b
val scaled = a * 2.0f
```

### Mathematical Functions

```kotlin
val x = Ember.array(listOf(0.0f, 1.0f, 2.0f))

// Trigonometric
val sinX = Ember.sin(x)
val cosX = Ember.cos(x)

// Exponential
val expX = Ember.exp(x)
val logX = Ember.log(x)

// Power
val sqrtX = Ember.sqrt(x)
val squared = Ember.square(x)
val powered = Ember.power(x, 3.0)
```

### Reductions

```kotlin
val data = Ember.array(listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))

val sum = Ember.sum(data)      // 15.0
val mean = Ember.mean(data)    // 3.0
val max = Ember.max(data)      // 5.0
val min = Ember.min(data)      // 1.0
```

### Shape Operations

```kotlin
// Reshape
val x = Ember.arange(0, 12)
val matrix = Ember.reshape(x, intArrayOf(3, 4))

// Transpose
val a = Ember.array(listOf(
    listOf(1.0f, 2.0f),
    listOf(3.0f, 4.0f)
))
val aT = Ember.transpose(a)

// Matrix multiplication
val b = Ember.array(listOf(
    listOf(5.0f, 6.0f),
    listOf(7.0f, 8.0f)
))
val c = Ember.matmul(a, b)
```

### Chained Operations

```kotlin
val x = Ember.array(listOf(1.0f, 2.0f, 3.0f))
val result = ((x + 1.0f) * 2.0f).square()
```

## Type System

### DTypes

Ember supports multiple data types:

```kotlin
// Floating point
Ember.float16
Ember.float32  // Default
Ember.float64
Ember.float128
Ember.bfloat16

// Integer
Ember.int8, Ember.int16, Ember.int32, Ember.int64
Ember.uint8, Ember.uint16, Ember.uint32, Ember.uint64

// Boolean
Ember.bool

// Complex
Ember.complex64, Ember.complex128
```

### Scalars

Scalars are 0-dimensional tensors:

```kotlin
val scalar = Ember.array(5.0f)
val value = scalar.toScalar().toFloat()  // 5.0
```

## Examples

See `src/commonMain/kotlin/ai/solace/ember/examples/EmberExamples.kt` for comprehensive examples.

## Current Status

✅ **Complete** (as of Dec 2025):
- Core Ember API with MLX-style interface
- Tensor creation and factory functions
- Element-wise operations (+, -, *, /)
- Mathematical functions (sin, cos, tan, exp, log, sqrt, etc.)
- Reduction operations (sum, mean, max, min)
- Shape operations (reshape, transpose, matmul)
- Comprehensive test suite (65+ tests)

🔄 **In Progress**:
- Full broadcasting system
- KLang-backed storage for bit-exact operations
- Advanced indexing

🔜 **Coming Soon**:
- Neural network layers
- Training utilities
- SWAR acceleration
- Metal backend for Apple platforms

## Testing

The project includes comprehensive tests:

- `EmberAPITest.kt` - 25+ tests for the main API
- `EmberTensorTest.kt` - 40+ tests for tensor operations
- `ScalarTest.kt` - Tests for scalar operations

## Architecture

Ember uses a modular architecture:

- **Ember.kt** - Main API entry point
- **EmberTensor.kt** - Core tensor implementation
- **Scalar.kt** - Scalar value wrappers
- **DType.kt** - Type system
- **KLang** - Cross-platform numerics (external)

## Contributing

See CONTRIBUTING.md for guidelines.

## License

MIT License - see LICENSE file for details.
