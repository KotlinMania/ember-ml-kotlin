/**
 * KLang Floating-Point (fp) – intent-driven C parity.
 *
 * Types
 * - CDouble: IEEE-754 binary64. Matches C `double`.
 * - CLongDouble: intent wrapper for C `long double` with explicit flavors:
 *   - AUTO: resolved via library default (host profile integration planned)
 *   - DOUBLE64: treat long double as 64-bit double
 *   - EXTENDED80: x87-style extended precision (approx via double-double core)
 *   - IEEE128: IEEE-754 binary128 (approx via double-double core with rounding hooks)
 *
 * Guidance
 * - Use CDouble for portable C `double` semantics.
 * - Use CLongDouble with a specific Flavor when numerical stability or exact parity with C is
 *   required. Prefer AUTO when a host profile is available; otherwise set default explicitly.
 *
 * Determinism
 * - Where platform shifts differ (esp. 8/16-bit), the bitwise package provides arithmetic-only
 *   operations ensuring cross-platform determinism (see ArithmeticBitwiseOps, ArrayBitShifts).
 */
package ai.solace.klang.fp
