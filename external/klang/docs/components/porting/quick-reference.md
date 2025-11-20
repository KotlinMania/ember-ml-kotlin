Porting Guide — From C to KLang
===============================

Pointers and Memory
- Every C pointer maps to an Int address in GlobalHeap.
- Allocate with KMalloc (malloc/calloc/realloc/free); keep addresses stable; avoid copies.

Scalars and Aggregates
- Use StructLayout for offsets/padding; store/load fields via GlobalHeap typed IO.
- Align allocations to C rules (1/2/4/8/16). KMalloc gives 16‑byte alignment by default.

Arrays and Strings
- Arrays live in heap; take base address + length; index via pointer arithmetic.
- NUL‑terminated strings: use CString helpers or CLib functions.

Bit Operations
- Prefer ArrayBitShifts for multi‑limb shifts; set BitShiftConfig to ARITHMETIC for strict 8/16‑bit parity when required.

Floating‑Point
- Map `double` to CDouble (IEEE‑754 binary64). Map `long double` to CLongDouble with the desired flavor.

Do/Don’t
- Do: operate in place on heap addresses; treat them as lvalues. Don’t: materialize long‑lived Kotlin arrays.
- Do: use CLib/FastMem for bulk moves/sets. Don’t: write ad‑hoc loops unless necessary.

