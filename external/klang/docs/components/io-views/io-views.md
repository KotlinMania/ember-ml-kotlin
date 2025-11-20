Typed IO and Views
==================

Typed IO
- Use GlobalHeap.{lb/sb, lh/sh, lw/sw, ld/sd} for 8/16/32/64‑bit loads/stores (little‑endian).
- Use GlobalHeap.{lwf/swf, ldf/sdf} for Float/Double via raw bits.

Views (src/commonMain/kotlin/ai/solace/klang/mem/Views.kt)
- U8View/U16View/U32View expose small helpers to read/write packed arrays in place on the heap.
- Each view carries base address + element count and enforces bounds.

Usage pattern
- Prefer views for bulk ops; avoid copying out to arrays unless truly transient.
- Keep addresses stable; treat them as lvalues like in C.

