# Bitwise Tensor Operations Implementation

## Overview

This implementation adds comprehensive bitwise tensor operations to the Ember ML Kotlin library, completing the tensor implementation that was identified as missing in issue #9.

### Hybrid Shift Engine (Native + SWAR)

The limb shifter now auto-selects between Kotlin’s native bitwise operators and the arithmetic SWAR engine at runtime:

- On startup a lightweight self-test compares native `shl/ushr` results against the arithmetic reference for 8-, 16-, 32-, and 64-bit lanes. Only if every combination matches do we enable native mode.
- EmberScalar and the shift workspaces route through this hybrid front end. Scalar-only moves get the native fast path; multi-limb math keeps the deterministic SWAR implementation for SIMD-friendly carry handling.
- If a platform diverges, the detector falls back to the arithmetic engine automatically, so behaviour stays portable without manual toggles.

This “AUTO” mode gives us the best of both worlds: native speed when the host matches our C helpers, and guaranteed correctness elsewhere.

## Architecture

The implementation leverages the existing MegaNumber/MegaBinary system to provide bitwise operations at the tensor level:

1. **Backend Interface Extension**: Added 17 bitwise operations to the `Backend` interface
2. **MegaTensorBackend Implementation**: Implemented all operations using existing MegaBinary functionality  
3. **EmberTensor API**: Added user-facing methods to the EmberTensor class
4. **Float64 Compatibility**: Ensured operations work with Float64 through MegaNumber conversion

## Implemented Operations

### Shift Operations
- `leftShift(shifts)` - Shift bits to the left
- `rightShift(shifts)` - Shift bits to the right  
- `rotateLeft(shifts, bitWidth)` - Rotate bits to the left
- `rotateRight(shifts, bitWidth)` - Rotate bits to the right

### Basic Bitwise Operations  
- `bitwiseAnd(other)` - Element-wise bitwise AND
- `bitwiseOr(other)` - Element-wise bitwise OR
- `bitwiseXor(other)` - Element-wise bitwise XOR
- `bitwiseNot()` - Element-wise bitwise NOT

### Bit Manipulation
- `getBit(position)` - Get bit at specific position
- `setBit(position, value)` - Set bit at specific position
- `toggleBit(position)` - Toggle bit at specific position
- `countOnes()` - Count number of set bits (1s)
- `countZeros()` - Count number of unset bits (0s)

### Wave Operations
- `propagate(shift)` - Propagate binary wave by shifting
- `binaryWaveInterference(waves, mode)` - Apply wave interference (XOR, AND, OR)

### Pattern Generation
- `createDutyCycle(length, dutyCycle)` - Create binary pattern with duty cycle
- `generateBlockySin(length, halfPeriod)` - Generate square wave pattern

## Usage Examples

```kotlin
// Basic operations
val a = EmberTensor(intArrayOf(10), int32)  // Binary: 1010  
val b = EmberTensor(intArrayOf(12), int32)  // Binary: 1100

val andResult = a bitwiseAnd b              // 1000 = 8
val orResult = a bitwiseOr b                // 1110 = 14
val xorResult = a bitwiseXor b              // 0110 = 6

// Shift operations
val leftShifted = a.leftShift(1)           // 10100 = 20
val rightShifted = a.rightShift(1)         // 101 = 5

// Bit manipulation
val bitCount = a.countOnes()               // 2 (number of 1s in 1010)
val bit1 = a.getBit(1)                     // true (bit 1 is set)
val modified = a.setBit(0, true)           // 1011 = 11

// Pattern generation
val dutyCycle = EmberTensor.createDutyCycle(8, 0.5f)        // 50% duty cycle
val squareWave = EmberTensor.generateBlockySin(8, 2)        // Square wave

// Wave interference
val interference = EmberTensor.binaryWaveInterference(listOf(a, b), "xor")
```

## Float64 Compatibility

The implementation ensures compatibility with Float64 values and provides workarounds for Apple MLX/Metal platforms that may not natively support 64-bit floating-point bitwise operations:

- **Automatic Conversion**: Float64 values are automatically converted to MegaNumber representation
- **High Precision**: Operations maintain precision through the MegaNumber system
- **Cross-Platform**: Works consistently across all supported platforms

## Testing

Created comprehensive tests covering:
- Basic bitwise operations functionality
- Shift and rotation operations
- Bit manipulation operations
- Pattern generation
- Wave operations
- Float64 compatibility scenarios
- Mixed data type operations

## Implementation Status

✅ **Complete**: All tensor-level bitwise operations are implemented and functional
✅ **Tested**: Basic functionality tests created
✅ **Compatible**: Float64 operations work through MegaNumber conversion
✅ **Documented**: API methods include comprehensive documentation

## Benefits

1. **Feature Completeness**: Closes the gap identified in issue #9
2. **Platform Independence**: Works on all platforms including Apple MLX/Metal
3. **High Precision**: Leverages MegaNumber for arbitrary precision arithmetic
4. **Consistent API**: Follows established patterns in the EmberTensor interface
5. **Performance**: Delegates to optimized MegaBinary operations

This implementation completes the bitwise tensor functionality and addresses the Float64 compatibility requirements for Apple MLX/Metal platforms.
