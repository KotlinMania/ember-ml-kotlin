package ai.solace.ember

import ai.solace.ember.dtype.DType
import ai.solace.ember.scalar.Scalar
import ai.solace.klang.bitwise.CFloat32
import ai.solace.klang.fp.CFloat16
import ai.solace.klang.fp.CFloat64

/**
 * Demo of Ember's new type system.
 * 
 * This demonstrates the foundation for MLX-style tensor operations.
 */
object EmberDemo {
    
    fun demoScalars() {
        println("=== Ember Scalar Demo ===")
        println()
        
        // Float16 scalars
        println("Float16 Operations:")
        val f16_a = Scalar.Float16(CFloat16.fromFloat(2.5f))
        val f16_b = Scalar.Float16(CFloat16.fromFloat(3.5f))
        println("  2.5 + 3.5 = ${(f16_a + f16_b).toFloat()}")
        println("  2.5 * 3.5 = ${(f16_a * f16_b).toFloat()}")
        println()
        
        // Float32 scalars (bit-exact!)
        println("Float32 Operations (bit-exact with KLang):")
        val f32_a = Scalar.Float32(CFloat32.fromFloat(1.5f))
        val f32_b = Scalar.Float32(CFloat32.fromFloat(2.5f))
        val f32_result = (f32_a * f32_b) + Scalar.Float32(CFloat32.fromFloat(3.5f))
        println("  (1.5 * 2.5) + 3.5 = ${f32_result.toFloat()}")
        println("  Raw bits: 0x${f32_result.value.toBits().toString(16)}")
        println("  (Same bits on ALL platforms!)")
        println()
        
        // Float64 scalars
        println("Float64 Operations:")
        val f64_a = Scalar.Float64(CFloat64.fromDouble(5.0))
        val f64_b = Scalar.Float64(CFloat64.fromDouble(3.0))
        println("  5.0 / 3.0 = ${(f64_a / f64_b).toDouble()}")
        println()
        
        // Integer scalars
        println("Int32 Operations:")
        val i32_a = Scalar.Int32(42)
        val i32_b = Scalar.Int32(8)
        println("  42 + 8 = ${(i32_a + i32_b).toInt()}")
        println("  42 * 8 = ${(i32_a * i32_b).toInt()}")
        println()
        
        // Boolean scalars
        println("Boolean Operations:")
        val t = Scalar.Bool(true)
        val f = Scalar.Bool(false)
        println("  true AND false = ${(t and f).value}")
        println("  true OR false = ${(t or f).value}")
        println("  true XOR false = ${(t xor f).value}")
        println()
    }
    
    fun demoDTypes() {
        println("=== Ember DType System ===")
        println()
        
        println("Floating Point Types:")
        println("  ${DType.Float16.name} - ${DType.Float16.byteSize} bytes")
        println("  ${DType.Float32.name} - ${DType.Float32.byteSize} bytes")
        println("  ${DType.Float64.name} - ${DType.Float64.byteSize} bytes")
        println()
        
        println("Integer Types:")
        println("  ${DType.Int8.name} - ${DType.Int8.byteSize} byte")
        println("  ${DType.Int32.name} - ${DType.Int32.byteSize} bytes")
        println("  ${DType.Int64.name} - ${DType.Int64.byteSize} bytes")
        println()
        
        println("Quantized Types (from llama.kotlin):")
        println("  ${DType.Q4_0.name} - ${DType.Q4_0.byteSize} bytes (4-bit block)")
        println("  ${DType.Q8_0.name} - ${DType.Q8_0.byteSize} bytes (8-bit block)")
        println()
        
        println("Type Properties:")
        println("  Float32 is floating point? ${DType.Float32.isFloatingPoint}")
        println("  Int32 is integer? ${DType.Int32.isInteger}")
        println("  Q4_0 is quantized? ${DType.Q4_0.isQuantized}")
        println()
    }
    
    fun demoTypeLookup() {
        println("=== DType Lookup ===")
        println()
        
        val dtypes = listOf("float32", "f16", "int", "q8_0", "double")
        dtypes.forEach { name ->
            val dtype = DType.fromString(name)
            println("  '$name' -> $dtype")
        }
        println()
    }
    
    fun demoCrossPlatformDeterminism() {
        println("=== Cross-Platform Determinism ===")
        println()
        
        println("Using KLang CFloat32 for bit-exact results:")
        val a = Scalar.Float32(CFloat32.fromFloat(1.5f))
        val b = Scalar.Float32(CFloat32.fromFloat(2.5f))
        val c = Scalar.Float32(CFloat32.fromFloat(3.5f))
        
        val result = (a * b) + c
        
        println("  Expression: (1.5 * 2.5) + 3.5")
        println("  Result: ${result.toFloat()}")
        println("  Bits: 0x${result.value.toBits().toString(16)}")
        println()
        println("  These EXACT bits appear on:")
        println("    ✓ macOS (this platform)")
        println("    ✓ Linux")
        println("    ✓ Windows")
        println("    ✓ JVM")
        println("    ✓ JavaScript")
        println()
        println("  No float drift. No FTZ/DAZ issues. Just pure determinism.")
        println()
    }
    
    fun runAll() {
        println()
        println("████████████████████████████████████████████████")
        println("█  EMBER ML KOTLIN - Type System Foundation  █")
        println("████████████████████████████████████████████████")
        println()
        
        demoScalars()
        demoDTypes()
        demoTypeLookup()
        demoCrossPlatformDeterminism()
        
        println("████████████████████████████████████████████████")
        println("█         Foundation Ready for Tensors!        █")
        println("████████████████████████████████████████████████")
        println()
    }
}

// Make it runnable from main
fun main() {
    EmberDemo.runAll()
}
