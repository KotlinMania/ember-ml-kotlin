POC Benchmark — Limb Shifts at Scale
====================================

Location
- src/nativeMain/kotlin/ai/solace/klang/poc/ActorArrayBitShiftPOC.kt

What it does
- Shifts large arrays of 16‑bit limbs by s bits, with several strategies:
  - ActorArrayBitShiftPOC: coroutine actors (IntArray slices)
  - ZeroCopyActorArithmeticShiftPOC: actor style, arithmetic engine
  - ZeroCopyActorShiftPOC: actor style, native engine
  - NativeShiftSequentialPOC: sequential with ArrayBitShifts in native mode
  - Reference/scalar: simple correctness references

Notes
- The implementations now call ArrayBitShifts.shl16LEInPlace(...) so actual bit movement goes through BitShiftEngine.
- The program prints average per‑iteration nanoseconds for several sizes/bits.

