# Component Architecture and Interconnections

## System Overview

KLang is organized into five major component subsystems, each with clear responsibilities and well-defined interfaces. This document describes how these components work together to provide C-like semantics in pure Kotlin.

```
┌─────────────────────────────────────────────────────────────┐
│                        Application Layer                      │
│              (User code using KLang primitives)              │
└────────────┬─────────────────────────────────┬───────────────┘
             │                                 │
             ▼                                 ▼
┌─────────────────────┐           ┌─────────────────────────┐
│   Floating Point    │           │    Integer Types        │
│   - CDouble         │           │    - HeapUInt128        │
│   - CFloat128       │           │    - Signed types (TODO)│
│   - CLongDouble     │           │                         │
└──────────┬──────────┘           └───────────┬─────────────┘
           │                                   │
           │          ┌────────────────────────┘
           │          │
           ▼          ▼
┌──────────────────────────────────────────────────────────┐
│                   Bitwise Operations                      │
│   - BitShiftEngine (NATIVE/ARITHMETIC modes)             │
│   - Float32Math/Float64Math (soft-float)                 │
│   - SwAR128 (multi-limb arithmetic)                      │
│   - BitwiseOps (masks, rotations, extractions)           │
└───────────────────────────┬──────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────┐
│                   Memory Management                       │
│   - GlobalHeap (single heap, byte offsets)               │
│   - KMalloc (malloc/free with coalescing)                │
│   - KStack (stack frames)                                │
│   - CLib (string/memory functions)                       │
│   - CScalars (typed variables on heap)                   │
└──────────────────────────────────────────────────────────┘
```

## Component Descriptions

### 1. Memory Management (Foundation Layer)

**Purpose**: Provide C-like memory model with deterministic allocation.

**Key Components**:
- `GlobalHeap`: Single ByteArray-backed heap, little-endian I/O
- `KMalloc`: Allocator with bins, coalescing, 16-byte alignment
- `KStack`: Stack frame allocator for automatic storage
- `GlobalData`: DATA/BSS segment for globals/statics
- `CLib`: libc string/memory functions
- `CScalars`: Type-safe heap-backed variables

**Interfaces**:
```kotlin
// Heap operations
GlobalHeap.init(size: Int)
GlobalHeap.lw(addr: Int): Int          // Load word
GlobalHeap.sw(addr: Int, value: Int)   // Store word

// Allocator
KMalloc.malloc(size: Int): Int         // Returns address
KMalloc.free(addr: Int)

// Scalars
val x = CAutos.int(42)                 // Stack variable
x.addAssign(1)                         // In-place operation
```

**Dependencies**: None (foundation layer)

**Used By**: All other components

**Documentation**: 
- [02-global-heap.md](02-global-heap.md)
- [03-allocator-kmalloc.md](03-allocator-kmalloc.md)

### 2. Bitwise Operations (Engine Layer)

**Purpose**: Provide deterministic bit manipulation across platforms.

**Key Components**:
- `BitShiftEngine`: Configurable shift strategies
- `ArithmeticBitwiseOps`: Pure arithmetic implementations
- `Float32Math`: Soft-float 32-bit operations
- `Float64Math`: Soft-float 64-bit operations
- `SwAR128`: 128-bit multi-limb arithmetic
- `BitwiseOps`: Utility functions (masks, rotations)

**Interfaces**:
```kotlin
// Shift engine
BitShiftConfig.defaultMode = BitShiftMode.NATIVE
val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
engine.leftShift(value, bits)

// Soft-float
Float32Math.mul(a, b)
Float64Math.addBits(aBits, bBits)

// Multi-limb
SwAR128.addHeap(aAddr, bAddr, destAddr)  // Zero-copy
```

**Dependencies**: 
- Memory Management (for heap operations)

**Used By**:
- Floating Point (for arithmetic)
- Integer Types (for multi-limb operations)

**Documentation**:
- [05-bitshift-engine.md](05-bitshift-engine.md)
- [06-array-bitshifts.md](06-array-bitshifts.md)

### 3. Integer Types (Application Layer)

**Purpose**: Provide arbitrary-precision integers with C-like semantics.

**Key Components**:
- `HeapUInt128`: Zero-copy 128-bit unsigned integer
- Future: Signed types, BigInt

**Interfaces**:
```kotlin
// 128-bit operations
val a = HeapUInt128.fromULong(100u)
val b = HeapUInt128.fromULong(200u)
val c = a + b                          // Zero-copy heap operation
val shifted = c.shiftLeft(16)
```

**Dependencies**:
- Memory Management (GlobalHeap, KMalloc)
- Bitwise Operations (SwAR128)

**Used By**: Application code

**Documentation**:
- [07-heap-uint128.md](07-heap-uint128.md)

### 4. Floating Point (Application Layer)

**Purpose**: Provide IEEE-754 compliant floating-point with intent-based precision.

**Key Components**:
- `CDouble`: IEEE-754 binary64, platform-independent
- `CFloat128`: Double-double (~106-bit mantissa)
- `CLongDouble`: Intent-driven (DOUBLE64/EXTENDED80/IEEE128)
- `CFloat16`: Half-precision
- `CBF16`: BFloat16 format
- `VectorOps`: Deterministic vector operations

**Interfaces**:
```kotlin
// CDouble
val a = CDouble.fromDouble(10.0)
val b = CDouble.fromDouble(20.0)
val sum = a + b

// CFloat128 (high precision)
val x = CFloat128.fromDouble(1.0 / 3.0)
val tripled = x * 3.0              // ~31 decimal digits accuracy

// CLongDouble (intent-based)
CLongDouble.DefaultFlavorProvider.default = CLongDouble.Flavor.IEEE128
val ld = CLongDouble.ofDouble(42.0, CLongDouble.Flavor.AUTO)
```

**Dependencies**:
- Bitwise Operations (Float32Math, Float64Math for arithmetic)

**Used By**: Application code

**Documentation**:
- [08-floating.md](08-floating.md)

### 5. String Operations (Utility Layer)

**Purpose**: Provide C-like string manipulation.

**Key Components**:
- `CLib`: Complete libc string functions
- `FastStringMem`: Word-at-a-time operations
- `HexShift`: Hex conversion utilities

**Interfaces**:
```kotlin
// String operations (zero-copy on heap)
val str = "Hello".toCString()          // Returns heap address
val len = CLib.strlen(str)
val cmp = CLib.strcmp(str1, str2)
```

**Dependencies**:
- Memory Management (GlobalHeap for string storage)

**Used By**: Application code

**Documentation**:
- [09-clib-strings.md](09-clib-strings.md)

## Data Flow Examples

### Example 1: High-Precision Calculation

```kotlin
// Application uses CFloat128
val a = CFloat128.fromDouble(1.0)
val b = CFloat128.fromDouble(1e-16)
val sum = a + b                        // ← Triggers floating-point ops

// CFloat128 uses internal operations
// - twoSum() for error-free addition
// - quickTwoSum() for normalization
// Result: 106-bit mantissa precision maintained
```

**Data Flow**:
```
Application
    ↓
CFloat128 (error-free transforms)
    ↓
Native Double operations (for hi/lo components)
    ↓
Result with extended precision
```

### Example 2: Zero-Copy Integer Arithmetic

```kotlin
// Application uses HeapUInt128
val x = HeapUInt128.fromULong(100u)   // ← Allocates on heap
val y = HeapUInt128.fromULong(200u)
val z = x + y                          // ← Zero-copy operation

// HeapUInt128 delegates to SwAR128
SwAR128.addHeap(x.addr, y.addr, z.addr)

// SwAR128 operates directly on heap
// - Reads limbs from x.addr and y.addr
// - Performs add with carry
// - Writes result to z.addr
// No IntArray allocations!
```

**Data Flow**:
```
Application
    ↓
HeapUInt128 (operator overloading)
    ↓
SwAR128.addHeap() (heap-native)
    ↓
GlobalHeap (read/write limbs)
    ↓
Result in heap memory (zero-copy)
```

### Example 3: String Operation

```kotlin
// Application uses CLib
val str1 = "Hello".toCString()        // ← Allocates on heap
val str2 = "World".toCString()
val result = CLib.strcmp(str1, str2)  // ← Zero-copy comparison

// CLib uses FastStringMem
// - Word-at-a-time comparison (4-8 bytes per iteration)
// - Direct heap access via GlobalHeap
// - Returns comparison result
```

**Data Flow**:
```
Application
    ↓
CLib.strcmp()
    ↓
FastStringMem (word-at-a-time)
    ↓
GlobalHeap (load words)
    ↓
Comparison result
```

## Component Interactions

### Memory Allocation Flow

```
Application requests memory
    ↓
KMalloc.malloc(size)
    ↓
Search free lists (bins for ≤1024, large list for >1024)
    ↓
If found: split chunk if needed
    ↓
If not found: bump brk pointer
    ↓
Return heap address (Int)
```

### Scalar Variable Operations

```
Application creates scalar
    ↓
CAutos.int(42) or CHeapVars.int(42)
    ↓
Allocate space (stack or heap)
    ↓
Initialize with value
    ↓
Return CIntVar wrapper
    ↓
Operations (addAssign, etc.) work directly on heap
```

### Soft-Float Arithmetic

```
Application: Float32Math.mul(a, b)
    ↓
Extract sign, exponent, mantissa bits
    ↓
Multiply mantissas (with implicit bit)
    ↓
Add exponents, adjust for bias
    ↓
Normalize result (shift mantissa if needed)
    ↓
Round to nearest-even
    ↓
Pack bits back into Float format
```

## Cross-Component Communication

### Type System Integration

All components use consistent types:
- **Addresses**: `Int` (byte offsets into GlobalHeap)
- **Pointers**: `CPointer<T>` (type-safe wrappers around Int)
- **Sizes**: `Int` (byte counts)
- **Bits**: Platform-appropriate (Int for 32-bit, Long for 64-bit)

### Error Handling

Components use Kotlin's type system and require() checks:
```kotlin
require(size > 0) { "Size must be positive" }
require(addr % 16 == 0) { "Address must be 16-byte aligned" }
```

No exceptions for normal operation - errors are detected early via preconditions.

### Configuration

Global configuration via objects:
```kotlin
BitShiftConfig.defaultMode = BitShiftMode.NATIVE
CLongDouble.DefaultFlavorProvider.default = CLongDouble.Flavor.IEEE128
```

## Performance Characteristics

### Memory Operations
- **Allocation**: O(1) for common sizes (bins), O(n) worst case (large list)
- **Free**: O(1) marking, O(1) coalescing with neighbors
- **Load/Store**: O(1) heap access

### Arithmetic Operations
- **Scalar**: O(1) - direct heap access
- **Float**: O(1) - fixed number of bit operations
- **128-bit Integer**: O(1) - fixed 8 limbs, no dynamic allocation

### String Operations
- **Length**: O(n) but word-at-a-time (4-8x faster than byte-at-a-time)
- **Comparison**: O(n) worst case, early termination on mismatch
- **Copy**: O(n) with word-at-a-time optimization

## Design Principles

1. **Zero-Copy**: Operations work in-place on heap whenever possible
2. **Determinism**: Same inputs produce same outputs across platforms
3. **Type Safety**: Kotlin's type system prevents common C mistakes
4. **Modularity**: Clean interfaces between components
5. **Performance**: Match or exceed native C implementations

## Future Extensions

### Planned Component Additions

1. **Advanced Math Library**
   - Transcendental functions (sin, cos, exp, log)
   - Special functions (erf, gamma)
   - Matrix operations

2. **BigInt Support**
   - Arbitrary-precision integers
   - Modular arithmetic
   - GCD/LCM operations

3. **SIMD Operations**
   - Vector instructions where available
   - Fallback to scalar operations

## Conclusion

KLang's component architecture provides a clean separation of concerns while maintaining tight integration where needed. The foundation (Memory Management) supports all higher-level operations, while the Engine Layer (Bitwise Operations) provides platform-independent primitives that Application Layer components build upon.

For detailed information on specific components, see the individual documentation files in this directory.
