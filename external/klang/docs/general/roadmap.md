Roadmap
=======

Near‑term
- Add stack frame allocator (automatic storage) on the heap; wire call entry/exit.
- DATA/BSS loader for globals/statics; symbol table for addressing.
- Rounding hooks for CLongDouble flavors; optional host ABI probe for AUTO.
- Expand CLib (strchrnul, memrchr, etc.) and add allocator/string stress tests.

Defaults
- BitShiftEngine default: NATIVE (fast path). Switch to ARITHMETIC for legacy 8/16‑bit code sections.
- CDouble fixed to IEEE‑754 binary64; CLongDouble flavor selected by intent.

