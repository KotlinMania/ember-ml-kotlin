package ai.solace.ember.tensor.bitwise

/**
 * Interface for floating-point specific operations
 */
interface FloatSpecificOperations {
    /**
     * Add two floating-point numbers
     * 
     * @param other The number to add
     * @return The sum as a floating-point number
     */
    fun addFloat(other: MegaNumber): MegaNumber

    /**
     * Multiply two floating-point numbers
     * 
     * @param other The number to multiply by
     * @return The product as a floating-point number
     */
    fun mulFloat(other: MegaNumber): MegaNumber
}
