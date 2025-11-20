# KDoc Documentation Strategy

## Overview

KLang will maintain comprehensive KDoc documentation for all public APIs, enabling IDE support, documentation generation, and better code discoverability.

## KDoc Standards

### Class/Object Documentation

```kotlin
/**
 * Brief one-line description of the class.
 *
 * More detailed explanation spanning multiple lines if needed.
 * Explain the purpose, use cases, and key design decisions.
 *
 * ## Usage Example
 * ```kotlin
 * val example = MyClass(param)
 * val result = example.doSomething()
 * ```
 *
 * ## Thread Safety
 * Specify if thread-safe or not.
 *
 * ## Performance Characteristics
 * Mention complexity (O notation) for operations.
 *
 * @property propertyName Description of the property
 * @constructor Description if constructor is non-trivial
 * @see RelatedClass
 * @since 0.1.0
 */
class MyClass
```

### Function Documentation

```kotlin
/**
 * Brief one-line description of what the function does.
 *
 * More detailed explanation of behavior, edge cases, and algorithm if complex.
 *
 * @param paramName Description of parameter, including valid ranges/constraints
 * @param otherParam Another parameter description
 * @return Description of return value, including special cases
 * @throws ExceptionType When this exception is thrown
 * @see relatedFunction
 * @since 0.1.0
 *
 * ## Example
 * ```kotlin
 * val result = myFunction(42, "test")
 * ```
 *
 * ## Complexity
 * O(n) time, O(1) space
 */
fun myFunction(paramName: Int, otherParam: String): Result
```

### Property Documentation

```kotlin
/**
 * Brief description of what this property represents.
 *
 * Additional details about constraints, valid ranges, or special behaviors.
 *
 * @see relatedProperty
 */
val myProperty: Type
```

## Documentation Priorities

### Priority 1: Public API (CRITICAL)
Core classes that users will interact with directly:

**Memory Management**:
- `GlobalHeap` - The foundation
- `KMalloc` - Memory allocator
- `KStack` - Stack frames
- `CScalars` - Variable system
- `CLib` - C library functions

**Floating Point**:
- `CDouble` - IEEE-754 binary64
- `CFloat128` - Double-double precision
- `CLongDouble` - Intent-based precision
- `CFloat16` - Half precision
- `VectorOps` - Vector operations

**Integer Types**:
- `HeapUInt128` - 128-bit integers

**Bitwise Operations**:
- `BitShiftEngine` - Shift operations
- `Float32Math` - Soft-float 32-bit
- `Float64Math` - Soft-float 64-bit

### Priority 2: Supporting APIs
Important but less frequently used directly:

**Memory Details**:
- `Views` - Type-safe views
- `GlobalData` - DATA/BSS segments
- `FastMem` - Fast operations
- `CString` - String utilities

**Bitwise Details**:
- `ArithmeticBitwiseOps` - Arithmetic operations
- `ArrayBitShifts` - Multi-limb shifts
- `SwAR128` - 128-bit arithmetic
- `BitwiseOps` - Utility functions

### Priority 3: Internal Implementation
Less critical but still valuable:

- `BitShiftConfig` - Configuration
- `ShiftTables` - Lookup tables
- `PackOps` - Packing operations

## Documentation Sections

### Required for All Public APIs

1. **Brief Description** (1 line)
2. **Detailed Description** (1+ paragraphs)
3. **Parameters** (with constraints)
4. **Return Values** (with special cases)
5. **Exceptions/Errors** (if any)

### Recommended Sections

6. **Usage Example** (code snippet)
7. **Performance** (complexity analysis)
8. **Thread Safety** (if applicable)
9. **See Also** (related APIs)
10. **Since** (version introduced)

### Optional Sections

11. **Algorithm** (for complex operations)
12. **Implementation Notes** (design decisions)
13. **Platform Differences** (JS vs Native)
14. **Known Limitations**

## Code Comment Standards

### Inline Comments

Use inline comments for:
- Complex algorithm steps
- Non-obvious optimizations
- Workarounds for platform issues
- Important invariants

```kotlin
// Align to 16 bytes for optimal SIMD access
val aligned = (addr + 15) and 0xFFFFFFF0.inv()

// Newton-Raphson iteration: x_n+1 = x_n * (2 - b*x_n)
val correction = two - (b * x)
```

### Block Comments

Use block comments for:
- Complex algorithm explanations
- Mathematical proofs
- Citation of papers/references

```kotlin
/*
 * Double-double division using Newton-Raphson refinement.
 * 
 * Algorithm from:
 * "Library for Double-Double and Quad-Double Arithmetic"
 * by Yozo Hida, Xiaoye S. Li, and David H. Bailey
 * 
 * Steps:
 * 1. Initial approximation: q0 = a.hi / b.hi
 * 2. Compute remainder: r = a - q0*b (high precision)
 * 3. Refinement: q1 = r / b.hi
 * 4. Combine: (q0, q1) with error-free summation
 */
```

## KDoc Generation

### Build Configuration

Add to `build.gradle.kts`:
```kotlin
tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("commonMain") {
            includes.from("Module.md")
            samples.from("src/commonTest/kotlin/examples")
            sourceLink {
                localDirectory.set(file("src/commonMain/kotlin"))
                remoteUrl.set(URL("https://github.com/SolaceHarmony/klang/tree/main/src/commonMain/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}
```

### Generated Documentation

Run: `./gradlew dokkaHtml`

Output: `build/dokka/html/index.html`

## Documentation Review Checklist

Before committing KDoc changes:

- [ ] All public classes have KDoc
- [ ] All public functions have KDoc
- [ ] All parameters documented
- [ ] Return values documented
- [ ] Examples provided for complex APIs
- [ ] No TODOs in KDoc (move to issues)
- [ ] Links to related APIs work
- [ ] Code examples compile
- [ ] Performance characteristics noted
- [ ] Platform differences highlighted

## Maintenance

### When Adding New APIs

1. Write KDoc before/alongside implementation
2. Include usage example
3. Document edge cases
4. Note performance characteristics

### When Modifying APIs

1. Update KDoc to match changes
2. Update examples if behavior changed
3. Add `@since` for new parameters
4. Mark deprecated APIs with `@Deprecated`

### Deprecation Process

```kotlin
/**
 * Old description.
 *
 * @deprecated Use [newFunction] instead. This will be removed in v1.0.
 * @see newFunction
 */
@Deprecated(
    message = "Use newFunction instead",
    replaceWith = ReplaceWith("newFunction(param)"),
    level = DeprecationLevel.WARNING
)
fun oldFunction(param: Int)
```

## Documentation Metrics

### Current Status
- KDoc blocks: 46
- Public APIs: ~100+
- Coverage: ~40%

### Goals
- Short-term: 70% coverage (Priority 1 complete)
- Medium-term: 85% coverage (Priority 2 complete)
- Long-term: 95%+ coverage (all public APIs)

## Examples Repository

Create `examples/` directory with:
- `QuickStart.kt` - Basic usage
- `MemoryManagement.kt` - Heap, malloc, scalars
- `FloatingPoint.kt` - Precision examples
- `ZeroCopy.kt` - Zero-copy operations
- `CrossPlatform.kt` - Platform handling

## Conclusion

Comprehensive KDoc documentation is essential for:
- API discoverability in IDEs
- Generated HTML/Markdown docs
- Onboarding new contributors
- Professional library presentation

This strategy ensures KLang maintains high-quality documentation as it grows.
