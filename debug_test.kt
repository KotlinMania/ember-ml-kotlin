// Simple debug test to understand the current behavior
package ai.solace.ember.tensor.bitwise

fun debugMain() {
    println("=== Debug MegaFloat ===")
    
    try {
        // Test decimal string constructor
        val float = MegaFloat("123.45")
        println("Created MegaFloat from string: ${float.toString()}")
        println("toDecimalString(): ${float.toDecimalString()}")
        println("isFloat: ${float.isFloat}")
        println("negative: ${float.negative}")
        println("mantissa: ${float.mantissa.contentToString()}")
        println("exponent: ${float.exponent.contentToString()}")
        println("exponentNegative: ${float.exponentNegative}")
    } catch (e: Exception) {
        println("Error creating MegaFloat from string: ${e.message}")
        e.printStackTrace()
    }
    
    try {
        // Test simple constructor
        val simpleFloat = MegaFloat()
        println("\nCreated simple MegaFloat: ${simpleFloat.toString()}")
        println("toDecimalString(): ${simpleFloat.toDecimalString()}")
    } catch (e: Exception) {
        println("Error creating simple MegaFloat: ${e.message}")
        e.printStackTrace()
    }
}
