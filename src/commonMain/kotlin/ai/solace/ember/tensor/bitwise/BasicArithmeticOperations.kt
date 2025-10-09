package ai.solace.ember.tensor.bitwise

/**
 * Interface for basic arithmetic operations
 */
interface BasicArithmeticOperations {
    /**
     * Add two numbers
     * 
     * @param other The number to add
     * @return The sum
     */
    fun add(other: MegaNumber): MegaNumber
    
    /**
     * Subtract another number from this one
     * 
     * @param other The number to subtract
     * @return The difference
     */
    fun sub(other: MegaNumber): MegaNumber
    
    /**
     * Multiply two numbers
     * 
     * @param other The number to multiply by
     * @return The product
     */
    fun mul(other: MegaNumber): MegaNumber
    
    /**
     * Divide this number by another
     * 
     * @param other The divisor
     * @return The quotient
     */
    fun divide(other: MegaNumber): MegaNumber
}