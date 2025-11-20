KLang (Kotlin C-Alignment Library)
==================================

Overview
--------
- Goal: make Kotlin codegen and runtime utilities behave like C across platforms, with stable,
  deterministic semantics for low-level bit operations and floating-point where required.
- Scope in this repo: bitwise primitives, array limb shifts, packed-limb buffers, and a small
  family of C-aligned floating types (CDouble, CLongDouble).

Key Ideas
---------
- Arithmetic-based bit operations: For 8/16-bit worlds (e.g., Mark Adler’s zlib internals), we
  avoid platform-dependent shifts by using purely arithmetic implementations. See:
  - `ai.solace.klang.bitwise.ArithmeticBitwiseOps`
  - `ai.solace.klang.bitwise.ArrayBitShifts`
  - `ai.solace.klang.bitwise.BitwiseOps`

- Intent-driven floating semantics: C’s `long double` differs by platform (double64, x87 80-bit,
  IEEE-754 binary128). We expose an API that clearly expresses intent and selects the appropriate
  behavior.
  - `CDouble` — IEEE-754 binary64, exact on all targets. This is our canonical
    C `double` regardless of host C library quirks.
  - `CLongDouble` — wrapper that selects a flavor: `DOUBLE64`, `EXTENDED80`, or `IEEE128`.
    - Flavor `AUTO` resolves via a default provider (host profile integration planned).
    - EXTENDED80/IEEE128 use `CFloat128` double-double as a fast core with rounding hooks (IEEE-128
      rounding to 113-bit mantissa planned).

What We Do Not Do
-----------------
- We do not import glibc/GMP sources. We study the structure (e.g., extracting mantissa/exponent
  for `long double`) to reproduce semantics, but all code here is original Kotlin.

Mapping C Types (Codegen Intent)
--------------------------------
- `float`       → `CDouble` (64-bit IEEE-754 double; Kotlin `Double`)
- `double`      → `CDouble`
- `long double` → `CLongDouble` (flavor controls precision model)

Choosing a Long Double Flavor
-----------------------------
By default, `CLongDouble` uses `AUTO` which maps to the library default. You can override globally:

```kotlin
import ai.solace.klang.fp.CLongDouble

CLongDouble.DefaultFlavorProvider.default = CLongDouble.Flavor.IEEE128
```

Or per value:

```kotlin
val a = CLongDouble.ofDouble(1.0, CLongDouble.Flavor.EXTENDED80)
val b = CLongDouble.ofDouble(2.0)
val c = a + b // uses EXTENDED80 for this instance
```

Bit-Exact vs Fast Modes
-----------------------
- Fast by default: `CDouble` and `CLongDouble` (DOUBLE64) run at native `Double` speeds.
- Exact when needed: pick `CLongDouble` with the appropriate flavor or future Strict-C mode
  (planned: Kotlin/Native shim that performs the op using host `long double` and returns exact bits).

Validation Tools
----------------
- See `tools/` for small C programs that generate reference vectors for float16/64 and
  quad-precision validation (requires GCC/libquadmath for the IEEE-128 validator). These are used to
  verify Kotlin results match C where required.

Notable Files
-------------
- Floating types: `ai/solace/klang/fp/CDouble.kt`, `ai/solace/klang/fp/CLongDouble.kt`, `ai/solace/klang/fp/CFloat128.kt`.
- Bitwise/limb: `ai/solace/klang/bitwise/*`, `ai/solace/klang/buffer/*`.

Heap Memory Model
-----------------
- All C-style memory lives in a single pure-Kotlin heap (GlobalHeap) backed by a ByteArray.
- Pointers are byte offsets (Int). Use `CPointer<T>.index(i)` and `.load()/.store()` for typed access.
- `KMalloc` provides `malloc/calloc/realloc/free`-style APIs; `Runtime` exposes `kmalloc/kfree` shims.
- libc-like helpers (`CLib`): `strlen/strnlen/strcmp/strncmp/strcpy/strncpy/memchr/strchr/memcmp`, implemented over GlobalHeap.
- `memcpy` is non-overlap-safe by design (as in C); use `memmove` for overlapping regions.

Roadmap
-------
- Host ABI profile probe to set `AUTO` flavor automatically.
- IEEE-128 and x87-extended rounding hooks to ensure we never exceed host-C precision.
- Optional Strict-C backend for Kotlin/Native to guarantee bit-for-bit identity with the toolchain.
