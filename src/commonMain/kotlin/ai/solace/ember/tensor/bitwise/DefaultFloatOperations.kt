package ai.solace.ember.tensor.bitwise

/**
 * Default implementation of FloatSpecificOperations interface.
 * This class provides implementations for the methods defined in the FloatSpecificOperations interface.
 */
class DefaultFloatOperations(private val megaNumber: MegaNumber) : FloatSpecificOperations {
   /**
    * Add two floating-point numbers
    *
    * @param other The number to add
    * @return The sum as a floating-point number
    */
   override fun addFloat(other: MegaNumber): MegaNumber {
      return addFloat(megaNumber, other)
   }

   /**
    * Multiply two floating-point numbers
    *
    * @param other The number to multiply by
    * @return The product as a floating-point number
    */
   override fun mulFloat(other: MegaNumber): MegaNumber {
      return multiplyFloat(megaNumber, other)
   }

   companion object {
      /**
       * Float addition using chunk-based arithmetic.
       *
       * @param a First MegaNumber
       * @param b Second MegaNumber
       * @return Sum as a MegaNumber
       */
      fun addFloat(a: MegaNumber, b: MegaNumber): MegaNumber {
         // Signed exponents as Int
         val expA = expAsInt(a.exponent)
         val expB = expAsInt(b.exponent)

         // Align mantissas
         var mantA = a.mantissa.copyOf()
         var mantB = b.mantissa.copyOf()
         val finalExp: MegaNumber

         if (expA > expB) {
            mantB = DefaultBitManipulationOperations.shiftRight(mantB, expA - expB)
            finalExp = a.exponent
         } else if (expB > expA) {
            mantA = DefaultBitManipulationOperations.shiftRight(mantA, expB - expA)
            finalExp = b.exponent
         } else {
            finalExp = a.exponent          // equal exponents
         }

         // Combine mantissas
         val sameSign = (a.negative == b.negative)
         val resultMant: IntArray
         val resultNeg: Boolean

         if (sameSign) {
            resultMant = MegaNumber.addChunks(mantA, mantB)
            resultNeg  = a.negative
         } else {
            val cmp = MegaNumber.compareAbs(mantA, mantB)
            when {
               cmp == 0 -> return MegaNumber(intArrayOf(0))   // exact zero
               cmp > 0  -> {
                  resultMant = MegaNumber.subChunks(mantA, mantB)
                  resultNeg  = a.negative
               }
               else     -> {
                  resultMant = MegaNumber.subChunks(mantB, mantA)
                  resultNeg  = b.negative
               }
            }
         }

         val out = MegaNumber(
            mantissa = resultMant,
            exponent = finalExp,
            negative = resultNeg,
            isFloat  = true
         )
         out.normalize()
         return out
      }

      /**
       * Float multiplication using chunk-based arithmetic.
       *
       * @param a First MegaNumber
       * @param b Second MegaNumber
       * @return Product as a MegaNumber
       */
      fun multiplyFloat(a: MegaNumber, b: MegaNumber): MegaNumber {
         // Multiply mantissas
         val productMant = MegaNumber.mulChunks(a.mantissa, b.mantissa)

         // Add exponents (signed)
         val sumExp = expAsInt(a.exponent) + expAsInt(b.exponent)
         val newExponent = MegaNumber(intArrayOf(kotlin.math.abs(sumExp)), negative = sumExp < 0)

         // Determine sign
         val newNegative = (a.negative != b.negative)

         // Create result
         val out = MegaNumber(
            mantissa = productMant,
            exponent = newExponent,
            negative = newNegative,
            isFloat = true
         )
         out.normalize()
         return out
      }

      /**
       * Float division branch used when either operand is float.
       *
       * @param a First MegaNumber (dividend)
       * @param b Second MegaNumber (divisor)
       * @return Quotient as a MegaNumber
       */
      fun divideFloat(a: MegaNumber, b: MegaNumber): MegaNumber {
         // Divide mantissas
         val (quotientMant, _) = a.chunkDivide(a.mantissa, b.mantissa)
         // Subtract exponents
         val diffExp = expAsInt(a.exponent) - expAsInt(b.exponent)
         val newExponent = MegaNumber(intArrayOf(kotlin.math.abs(diffExp)), negative = diffExp < 0)
         // Determine sign
         val newNegative = (a.negative != b.negative)
         val out = MegaNumber(
            mantissa = quotientMant,
            exponent = newExponent,
            negative = newNegative,
            isFloat = true
         )
         out.normalize()
         return out
      }

      /**
       * Treat a MegaNumber exponent as a signed Int
       */
      private fun expAsInt(exponent: MegaNumber): Int {
         val absVal = chunksToInt(exponent.mantissa)
         return if (exponent.negative) -absVal else absVal
      }

      /**
       * Convert a chunk array to an Int value.
       * Only uses the first chunk for simplicity.
       */
      private fun chunksToInt(chunks: IntArray): Int {
         if (chunks.isEmpty()) {
            return 0
         }

         // Mask to 31 bits to avoid sign‑extension if bit 31 is set.
         return chunks[0] and 0x7FFFFFFF
      }
   }
}
