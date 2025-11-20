HeapUInt128 — 128‑bit Unsigned Integer
=====================================

Representation
- 8 little‑endian 16‑bit limbs stored in GlobalHeap; constructors read/write limbs.

Ops
- plus/minus: use SwAR128 helpers in bitwise package to operate on transient limb arrays, then write back.
- shiftLeft/shiftRight: same approach; return new heap object with results.
- Comparable: compare via limb arrays.

Why heap?
- No persistent array storage — values live in place in the heap, mirroring how C would hold data in memory.

