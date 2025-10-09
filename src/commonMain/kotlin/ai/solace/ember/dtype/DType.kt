package ai.solace.ember.dtype

/**
 * Ember data type system.
 * 
 * Based on KLang soft-float types for cross-platform determinism.
 */
sealed class DType(val name: String, val byteSize: Int) {
    
    // ============================================
    // Floating point types (KLang-backed)
    // ============================================
    
    object Float16 : DType("float16", 2)
    object Float32 : DType("float32", 4)
    object Float64 : DType("float64", 8)
    object Float128 : DType("float128", 16)  // Future
    
    // BFloat16 for ML
    object BFloat16 : DType("bfloat16", 2)
    
    // ============================================
    // Integer types
    // ============================================
    
    object Int8 : DType("int8", 1)
    object Int16 : DType("int16", 2)
    object Int32 : DType("int32", 4)
    object Int64 : DType("int64", 8)
    
    object UInt8 : DType("uint8", 1)
    object UInt16 : DType("uint16", 2)
    object UInt32 : DType("uint32", 4)
    object UInt64 : DType("uint64", 8)
    
    // ============================================
    // Boolean
    // ============================================
    
    object Bool : DType("bool", 1)
    
    // ============================================
    // Complex types (future)
    // ============================================
    
    object Complex64 : DType("complex64", 8)
    object Complex128 : DType("complex128", 16)
    
    // ============================================
    // Quantized types (from llama.kotlin)
    // ============================================
    
    object Q4_0 : DType("q4_0", 18)   // 4-bit quantization block
    object Q4_1 : DType("q4_1", 20)   // 4-bit with min
    object Q8_0 : DType("q8_0", 34)   // 8-bit quantization block
    object Q8_1 : DType("q8_1", 36)   // 8-bit with min
    
    // K-Quants (super-block based)
    object Q2_K : DType("q2_k", 84)
    object Q3_K : DType("q3_k", 110)
    object Q4_K : DType("q4_k", 144)
    object Q5_K : DType("q5_k", 176)
    object Q6_K : DType("q6_k", 210)
    
    // ============================================
    // Properties
    // ============================================
    
    val isFloatingPoint: Boolean get() = when (this) {
        Float16, Float32, Float64, Float128, BFloat16 -> true
        else -> false
    }
    
    val isInteger: Boolean get() = when (this) {
        Int8, Int16, Int32, Int64, UInt8, UInt16, UInt32, UInt64 -> true
        else -> false
    }
    
    val isQuantized: Boolean get() = when (this) {
        Q4_0, Q4_1, Q8_0, Q8_1, Q2_K, Q3_K, Q4_K, Q5_K, Q6_K -> true
        else -> false
    }
    
    val isComplex: Boolean get() = when (this) {
        Complex64, Complex128 -> true
        else -> false
    }
    
    // ============================================
    // String representation
    // ============================================
    
    override fun toString(): String = name
    
    companion object {
        /**
         * Get dtype by name.
         */
        fun fromString(name: String): DType? = when (name.lowercase()) {
            "float16", "f16" -> Float16
            "float32", "f32", "float" -> Float32
            "float64", "f64", "double" -> Float64
            "float128", "f128" -> Float128
            "bfloat16", "bf16" -> BFloat16
            
            "int8", "i8" -> Int8
            "int16", "i16" -> Int16
            "int32", "i32", "int" -> Int32
            "int64", "i64", "long" -> Int64
            
            "uint8", "u8", "byte" -> UInt8
            "uint16", "u16" -> UInt16
            "uint32", "u32" -> UInt32
            "uint64", "u64" -> UInt64
            
            "bool", "boolean" -> Bool
            
            "complex64", "c64" -> Complex64
            "complex128", "c128" -> Complex128
            
            "q4_0" -> Q4_0
            "q4_1" -> Q4_1
            "q8_0" -> Q8_0
            "q8_1" -> Q8_1
            "q2_k" -> Q2_K
            "q3_k" -> Q3_K
            "q4_k" -> Q4_K
            "q5_k" -> Q5_K
            "q6_k" -> Q6_K
            
            else -> null
        }
    }
}
