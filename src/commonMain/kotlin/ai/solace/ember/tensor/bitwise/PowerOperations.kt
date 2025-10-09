package ai.solace.ember.tensor.bitwise

/**
 * Interface for power/exponentiation operations
 */
interface PowerOperations {
    /**
     * Raise a number to the power of an exponent
     * 
     * @param exponent The exponent to raise the number to
     * @return The result of the power operation
     */
    fun pow(exponent: MegaNumber): MegaNumber
}