package ai.solace.ember.tensor.common

/**
 * Represents a data type for tensors.
 * This is a backend-agnostic representation of data types that can be mapped to backend-specific types.
 */
enum class DType {
    FLOAT32,
    FLOAT64,
    INT32,
    INT64,
    UINT8,
    BOOL;
    // TODO: Add INT8, INT16 support - requires updating all when expressions
    // INT8,
    // INT16,

    /**
     * Returns a string representation of the data type.
     *
     * @return A string representation of the data type.
     */
    override fun toString(): String = when (this) {
        FLOAT32 -> "float32"
        FLOAT64 -> "float64"
        INT32 -> "int32"
        INT64 -> "int64"
        UINT8 -> "uint8"
        BOOL -> "bool"
    }

    /**
     * Gets the size in bytes of this data type.
     */
    val sizeInBytes: Int get() = when (this) {
        FLOAT32 -> 4
        FLOAT64 -> 8
        INT32 -> 4
        INT64 -> 8
        UINT8 -> 1
        BOOL -> 1
    }

    companion object {
        /**
         * Gets a data type from its string representation.
         *
         * @param name The string representation of the data type.
         * @return The corresponding data type, or null if not found.
         */
        fun fromString(name: String): DType? = when (name.lowercase()) {
            "float32", "float" -> FLOAT32
            "float64", "double" -> FLOAT64
            "int32", "int" -> INT32
            "int64", "long" -> INT64
            "uint8", "byte" -> UINT8
            "bool", "boolean" -> BOOL
            // TODO: Add when INT8, INT16 are enabled
            // "int8", "sbyte" -> INT8
            // "int16", "short" -> INT16
            else -> null
        }
    }
}

// Singleton instances for convenience
val float32 = DType.FLOAT32
val float64 = DType.FLOAT64
val int32 = DType.INT32
val int64 = DType.INT64
val uint8 = DType.UINT8
val bool = DType.BOOL
// TODO: Add when INT8, INT16 are enabled
// val int8 = DType.INT8
// val int16 = DType.INT16
