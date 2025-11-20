From C To KLang — In-Place, Heap-Native Rules (Authoritative)
============================================================

Purpose
- Capture the exact, C-driven rules we follow so every engineer can build and review this system without losing intent.
- All memory lives in GlobalHeap. No persistent Kotlin arrays. Variables exist in place, accessed by typed loads/stores.
- This is **pure Kotlin multiplatform**, not cinterop. We replicate C semantics exactly in idiomatic Kotlin.

Non-Negotiables
- Heap is the single universe. Long-lived state must never bounce through Kotlin arrays.
- In-place operations only: shifts, scalar updates, string/memory ops, etc. Must touch GlobalHeap directly.
- Alignment and semantics follow the platform C ABI and libc behavior where applicable.
- No raw Kotlin bitwise operators (shl/shr/ushr/and/or/xor) except within BitShiftEngine implementation.
- No hard-coded masks (0xFF, 0xFFFF, etc.) — use BitShiftEngine.getMask(bits) for bit-width-safe masking.
- C types prefixed with C_ (C_UInt128, C_Int128, etc.) map exactly to C types like __uint128 and __int128.

1) Automatic Storage — KStack
-----------------------------
- We maintain a stack region inside GlobalHeap for C‑style automatic variables.
- 16‑byte alignment: On aarch64 (macOS arm64), SP must be 16‑byte aligned at all call boundaries. KStack:
  - Aligns the stack base to 16 bytes.
  - Aligns each pushed frame to a power‑of‑two boundary.
  - alloca(size, align) returns an absolute heap address meeting the requested alignment.
- API: KStack.init(bytes), pushFrame()/popFrame(marker), alloca(size, align), withFrame { … }.
- Tests: verify 4/8/16‑byte alignment, nested frames reuse after pop, and byte‑level writeability.

2) Static/Global Storage — GlobalData (DATA / BSS)
--------------------------------------------------
- Globals/statics are placed in GlobalHeap and live for the program duration.
- BSS: zero‑initialized blocks (defineBss(name, size, align)).
- DATA: byte‑for‑byte initialized blocks (defineData(name, bytes, align)).
- Over‑allocation for >16‑byte alignments returns an aligned interior address; original base is kept for disposal. A symbol table holds name→address.
- Thin conveniences: defineI32/defineI64/defineF64.
- Tests: alignment, zeroing, init pattern, duplicate symbol rejection.

3) Scalars — No Wrappers, No Copies, Just Addresses
---------------------------------------------------
- A scalar is an address with typed loads/stores:
  - CByteVar/CShortVar/CIntVar/CLongVar/CFloatVar/CDoubleVar: plain multiplatform classes holding addr: Int.
  - All reads/writes do GlobalHeap.lb/lh/lw/ld/lwf/ldf and sb/sh/sw/sd/swf/sdf directly.
- Creation sites:
  - CAutos.* → KStack.alloca (automatic storage) with initial value written in place.
  - CGlobals.* → GlobalData (static/global) with initial value written in place.
  - CHeapVars.* → KMalloc with initial value written in place; free(addr) releases it.
- CDoubleVar exposes a CDouble view via raw bits; no payload loss.
- Tests: CScalarsTest validates read/write in place and verifies heap bytes match expected values.

4) Memory Functions (mem*/str*) — libc Semantics
-----------------------------------------------
- memmove chooses direction based on overlap; memcpy is UB on overlap; memset fills in a word‑wise loop.
- We implement word‑at‑a‑time loops in FastMem/FastStringMem; all loads/stores are little‑endian assembled.
- CLib (strlen/strnlen/strcmp/strncmp/strcpy/strncpy/memchr/strchr/memcmp) mirrors libc behavior.
- Tests: overlaps (right/left), zero‑length no‑ops, strnlen and strncmp edge cases, strncpy padding, memcmp ordering.

5) Bit Shifts — In-Place, Limb-Wise, Carry-Correct
-----------------------------------------------
- BitShiftEngine controls mode (NATIVE vs ARITHMETIC). ALWAYS use BitShiftEngine, never raw shl/shr/ushr.
- ARITHMETIC mode: Exactly replicates C bitwise behavior across all platforms using pure arithmetic operations.
- NATIVE mode: Uses Kotlin's native operators for speed, but only after validation that behavior matches C.
- ArrayBitShifts must operate in place on GlobalHeap.
- Left shift by s (1..15): per-limb: new = ((v << s) & 0xFFFF) | carry; carry = (v >> (16−s)) & ((1<<s)−1).
- Right shift by s: per-limb from the end: new = (v >> s) | (carry << (16−s)); carry = v & ((1<<s)−1); sticky ORs all dropped bits.
- Heap overloads:
  - shl16LEInPlace(base, from, len, s, carryIn): updates limbs directly; returns carryOut.
  - rsh16LEInPlace(base, from, len, s): updates limbs directly; returns carryOut + sticky.
- No heap→array→heap copies anywhere in shift paths.
- Tests: heap-address left/right parity vs IntArray references, plus word-shift+bit-shift composition tests.
- **FORBIDDEN**: Raw Kotlin shift operators (shl/shr/ushr) and hard-coded masks (0xFF, 0xFFFF, etc.) break C compatibility.

6) Allocator — KMalloc Bins, Coalescing, Alignment
--------------------------------------------------
- 16‑byte minimum alignment. Size‑class bins ≤1024 bytes; large free list; coalescing on free; splitting on allocation.
- realloc grow: alloc new, memcpy old bytes, free old if needed; shrink may keep the same address and split tail into free chunk.
- Coalescing tests assert contiguity/writeability, not strict address equality.
- Next to implement: aligned_alloc/posix_memalign semantics — alignment a power of two and multiple of pointer size; return aligned block or fail.

7) JVM‑Only Annotations — Removed
---------------------------------
- All @JvmInline/@kotlin.jvm.JvmInline annotations were removed. All wrappers are plain multiplatform classes.
- CPointer/CFunction/FloatPointer/IntPointer are simple classes; no JVM‑specific behavior.

8) No Persistent Kotlin Arrays
------------------------------
- Kotlin arrays are permitted only as short‑lived temporaries inside a function for math; persistent data structures must live in GlobalHeap.
- The POC benchmark will be refactored to pass heap addresses/ranges only (actors exchange base+offset+len), not IntArray slices.

9) Test Coverage (Current)
--------------------------
- KStack: alignment, nested frames, in‑place byte writes.
- GlobalData: BSS zeroing, DATA init/alignment, duplicate symbols.
- KMalloc: calloc zeroing, realloc grow/shrink, reuse same size class, coalescing writeability.
- FastMem/CLib: memmove overlaps, zero‑length ops, full str*/mem* coverage.
- ArrayBitShifts: heap left/right parity, composition with word‑shifts.
- CScalars: autos/globals in place; heap bytes match scalar values.

10) Next Work (From C Sources)
------------------------------
- Implement aligned_alloc/posix_memalign shims with C11/POSIX constraints; extend tests to validate alignment/errno behavior.
- Keep SP 16‑aligned in all withFrame scopes; add tests that check alignment across nested frames and allocations.
- Refactor POC: operate solely on heap addresses (U16View or direct lb/sb pairs), remove IntArray working buffers.
- Extend randomized tests for limb shifts (1..15, various lengths) and allocator stress (random alloc/free patterns) to catch fragmentation regressions.

11) Quick Examples
------------------
- Auto scalar on stack:
  ```kotlin
  KMalloc.init(1 shl 18); KStack.init(1 shl 16)
  KStack.withFrame {
      val x = CAutos.int(42)
      x.addAssign(1)
      check(GlobalHeap.lw(x.addr) == 43)
  }
  ```
- Global double:
  ```kotlin
  GlobalData.init()
  val pi = CGlobals.double("g_pi", 3.5)
  pi.value += 0.25
  check(GlobalHeap.ldf(pi.addr) == 3.75)
  ```
- In‑place limb shift:
  ```kotlin
  val base = KMalloc.calloc(32 * 2, 1) // 32 limbs
  ArrayBitShifts.shl16LEInPlace(base, 0, 32, s = 7)
  ```

12) Ground Rules for Contributors
---------------------------------
- Never introduce heap→array→heap round‑trips for persistent data; operate in place.
- Enforce alignment rules (16‑byte for SP; power‑of‑two for allocations); validate in tests.
- Use GlobalHeap typed loads/stores; avoid ad‑hoc byte math sprinkled in call sites.
- Prefer existing CLib/FastMem for string/memory ops; don’t re‑implement copies.
- Keep tests comprehensive and reflective of real C behavior (memmove overlaps, strnlen bounds, allocator coalescing, etc.).

