package ai.solace.klang

/**
 * KLang: Kotlin Language Numeric Core
 * 
 * A clang-aligned workaround for Kotlin's float and bitwise support.
 * Provides bit-exact IEEE-754 arithmetic across all platforms.
 * 
 * Future: This will grow into its own standalone project.
 */

// Re-export commonly used types for easier access
import ai.solace.klang.bitwise.CFloat32
import ai.solace.klang.bitwise.SwAR
import ai.solace.klang.bitwise.Float32Math
import ai.solace.klang.bitwise.BitPrimitives
import ai.solace.klang.int.hpc.LimbUInt128

// Type aliases for convenience
typealias KFloat32 = CFloat32
typealias KUInt128 = LimbUInt128
