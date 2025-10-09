package ai.solace.ember.tensor.bitwise

import kotlin.test.*

class DebugTest {

    @Test
    fun testToDecimalString() {
        // Test MegaInteger
        val integer = MegaInteger("100")
        println("[DEBUG_LOG] MegaInteger(100).toDecimalString(): ${integer.toDecimalString()}")

        // Test MegaFloat with detailed debugging
        val float = MegaFloat("123.45")
        println("[DEBUG_LOG] MegaFloat(123.45):")
        println("  mantissa: ${float.mantissa.contentToString()}")
        println("  exponent: ${float.exponent.contentToString()}")
        println("  exponentNegative: ${float.exponentNegative}")
        println("  chunkToDecimal(mantissa): ${float.chunkToDecimal(float.mantissa)}")
        println("  toDecimalString(): ${float.toDecimalString()}")

        // Test what base MegaNumber would show
        val baseFloat = MegaNumber(
            mantissa = float.mantissa.map { it.toInt() }.toIntArray(),
            exponent = float.exponent.map { it.toInt() }.toIntArray(),
            negative = float.negative,
            isFloat = true,
            exponentNegative = float.exponentNegative
        )
        println("  base MegaNumber.toDecimalString(): ${baseFloat.toDecimalString()}")

        // Test arithmetic operations
        val a = MegaInteger("100")
        val b = MegaInteger("50")
        val sum = a.add(b) as MegaInteger
        println("[DEBUG_LOG] MegaInteger(100).add(MegaInteger(50)).toDecimalString(): ${sum.toDecimalString()}")

        val product = a.mul(b) as MegaInteger
        println("[DEBUG_LOG] MegaInteger(100).mul(MegaInteger(50)).toDecimalString(): ${product.toDecimalString()}")

        // Test MegaFloat arithmetic with debugging
        val c = MegaFloat("10.0")
        val d = MegaFloat("2.0")
        println("[DEBUG_LOG] MegaFloat(10.0) details:")
        println("  mantissa: ${c.mantissa.contentToString()}")
        println("  exponent: ${c.exponent.contentToString()}")
        println("  exponentNegative: ${c.exponentNegative}")

        val floatSum = c.add(d) as MegaFloat
        println("[DEBUG_LOG] MegaFloat(10.0).add(MegaFloat(2.0)):")
        println("  result mantissa: ${floatSum.mantissa.contentToString()}")
        println("  result exponent: ${floatSum.exponent.contentToString()}")
        println("  result exponentNegative: ${floatSum.exponentNegative}")
        println("  toDecimalString(): ${floatSum.toDecimalString()}")

        val floatProduct = c.mul(d) as MegaFloat
        println("[DEBUG_LOG] MegaFloat(10.0).mul(MegaFloat(2.0)).toDecimalString(): ${floatProduct.toDecimalString()}")
    }
}
