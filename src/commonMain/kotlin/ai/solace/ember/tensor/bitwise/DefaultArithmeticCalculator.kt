package ai.solace.ember.tensor.bitwise

/**
 * Default implementation of BasicArithmeticOperations interface.
 * This class provides implementations for the methods defined in the BasicArithmeticOperations interface.
 */
class DefaultArithmeticCalculator(private val megaNumber: MegaNumber) : BasicArithmeticOperations {
   /**
    * Add two numbers
    *
    * @param other The number to add
    * @return The sum
    */
   override fun add(other: MegaNumber): MegaNumber {
      return add(megaNumber, other)
   }

   /**
    * Subtract another number from this one
    *
    * @param other The number to subtract
    * @return The difference
    */
   override fun sub(other: MegaNumber): MegaNumber {
      return subtract(megaNumber, other)
   }

   /**
    * Multiply two numbers
    *
    * @param other The number to multiply by
    * @return The product
    */
   override fun mul(other: MegaNumber): MegaNumber {
      return multiply(megaNumber, other)
   }

   /**
    * Divide this number by another
    *
    * @param other The divisor
    * @return The quotient
    */
   override fun divide(other: MegaNumber): MegaNumber {
      return divide(megaNumber, other)
   }

   companion object {
      /**
       * Add two MegaNumbers. If either is float, handle float addition.
       *
       * @param a First MegaNumber
       * @param b Second MegaNumber
       * @return Sum as a MegaNumber
       */
      fun add(a: MegaNumber, b: MegaNumber): MegaNumber {
         // If either is float, handle float addition
         if (a.isFloat || b.isFloat) {
            return DefaultFloatOperations.Companion.addFloat(a, b)
         }

         // Integer addition
         if (a.negative == b.negative) {
            // Same sign => add magnitudes
            val sumMant = MegaNumber.addChunks(a.mantissa, b.mantissa)
            val sign = a.negative
            return MegaNumber(
               mantissa = sumMant,
               exponent = MegaNumber(intArrayOf(0)),
               negative = sign,
               isFloat = false
            )
         } else {
            // Different signs => subtract magnitudes
            val cmp = MegaNumber.compareAbs(a.mantissa, b.mantissa)
            if (cmp == 0) {
               // Result is zero
               return MegaNumber(
                  mantissa = intArrayOf(0),
                  exponent = MegaNumber(intArrayOf(0)),
                  negative = false,
                  isFloat = false
               )
            } else if (cmp > 0) {
               // a > b in magnitude
               val diff = MegaNumber.subChunks(a.mantissa, b.mantissa)
               val sign = a.negative
               return MegaNumber(
                  mantissa = diff,
                  exponent = MegaNumber(intArrayOf(0)),
                  negative = sign,
                  isFloat = false
               )
            } else {
               // b > a in magnitude
               val diff = MegaNumber.subChunks(b.mantissa, a.mantissa)
               val sign = b.negative
               return MegaNumber(
                  mantissa = diff,
                  exponent = MegaNumber(intArrayOf(0)),
                  negative = sign,
                  isFloat = false
               )
            }
         }
      }

      /**
       * Subtract two MegaNumbers. a - b = a + (-b)
       *
       * @param a First MegaNumber
       * @param b Second MegaNumber
       * @return Difference as a MegaNumber
       */
      fun subtract(a: MegaNumber, b: MegaNumber): MegaNumber {
         val negOther = MegaNumber(
            mantissa = b.mantissa.copyOf(),
            exponent = MegaNumber(b.exponent.mantissa.copyOf(), negative = b.exponent.negative),
            negative = !b.negative,
            isFloat = b.isFloat
         )
         return add(a, negOther)
      }

      /**
       * Multiply two MegaNumbers. If either is float, delegate to float multiply.
       *
       * @param a First MegaNumber
       * @param b Second MegaNumber
       * @return Product as a MegaNumber
       */
      fun multiply(a: MegaNumber, b: MegaNumber): MegaNumber {
         if (a.isFloat || b.isFloat) {
            return DefaultFloatOperations.Companion.multiplyFloat(a, b)
         }

         // Integer multiply
         val sign = (a.negative != b.negative)
         val product = MegaNumber.mulChunks(a.mantissa, b.mantissa)
         return MegaNumber(
            mantissa = product,
            exponent = MegaNumber(intArrayOf(0)),
            negative = sign,
            isFloat = false
         )
      }

      /**
       * Divide two MegaNumbers. If either is float, delegate to float division.
       *
       * @param a First MegaNumber (dividend)
       * @param b Second MegaNumber (divisor)
       * @return Quotient as a MegaNumber
       */
      fun divide(a: MegaNumber, b: MegaNumber): MegaNumber {
         // Unified public entry‑point – dispatches to integer or float path
         return if (a.isFloat || b.isFloat) {
            DefaultFloatOperations.Companion.divideFloat(a, b)
         } else {
            divideInteger(a, b)
         }
      }

      /**
       * If the supplied limb‑array encodes an exact power‑of‑two, returns the
       * number of **bit** positions to shift (i.e. log2(value)).
       * Otherwise returns −1.
       *
       * Runs in O(n) over the limb count and uses bit‑twiddling to verify
       * that exactly one bit is set across the entire array.
       */
      private fun powerOfTwoShift(chunks: IntArray): Int {
          var shift = 0
          var seenNonZero = false
          for (i in 0 until chunks.size) {
              val limb = chunks[i]
              if (limb == 0) {
                  shift += MegaNumberConstants.GLOBAL_CHUNK_SIZE
                  continue
              }
              /* limb must itself be a power‑of‑two **and** this must be the
                 only non‑zero limb encountered so far                       */
              if ((limb and (limb - 1)) != 0 || seenNonZero) return -1
              shift += limb.countTrailingZeroBits()
              seenNonZero = true
          }
          return if (seenNonZero) shift else -1
      }

      /**
       * Integer division branch used when both operands are integers.
       *
       * @param a First MegaNumber (dividend)
       * @param b Second MegaNumber (divisor)
       * @return Quotient as a MegaNumber
       */
      private fun divideInteger(a: MegaNumber, b: MegaNumber): MegaNumber {
         /* --- fast path for single‑chunk integers --------------------------- */
         if (!a.isFloat && !b.isFloat &&
             a.mantissa.size == 1 && b.mantissa.size == 1 &&
             a.exponent.mantissa.size == 1 && a.exponent.mantissa[0] == 0 &&
             b.exponent.mantissa.size == 1 && b.exponent.mantissa[0] == 0
         ) {
             val lhs = a.mantissa[0].toUInt()
             val rhs = b.mantissa[0].toUInt()
             require(rhs != 0u) { "Division by zero" }
             val neg = a.negative xor b.negative
             val q = (lhs / rhs).toInt()
             return MegaNumber(intArrayOf(q), MegaNumber(intArrayOf(0)), neg, false)
         }
         /* --- long‑form integer division ------------------------------------ */
         if (b.mantissa.size == 1 && b.mantissa[0] == 0) {
             throw ArithmeticException("Division by zero")
         }
         // ---- fast path when divisor is an exact power‑of‑two --------------
         val sign = (a.negative != b.negative)
         val powShift = powerOfTwoShift(b.mantissa)
         if (powShift >= 0) {
             val (qChunks, _) = a.divideBy2ToThePower(a.mantissa, powShift)
             return MegaNumber(qChunks, MegaNumber(intArrayOf(0)), sign, false)
         }
         val cmp = MegaNumber.compareAbs(a.mantissa, b.mantissa)
         return when {
             cmp < 0 -> MegaNumber(intArrayOf(0))
             cmp == 0 -> MegaNumber(intArrayOf(1), MegaNumber(intArrayOf(0)), sign, false)
             else -> {
                 val (q, _) = a.chunkDivide(a.mantissa, b.mantissa)
                 MegaNumber(q, MegaNumber(intArrayOf(0)), sign, false)
             }
         }
      }
   }
}
