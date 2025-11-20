Pointer Model
=============

CPointer<T>
- A thin value class holding an Int heap address. Arithmetic done in bytes unless a typed helper is used.

Extensions (PointerExtensions.kt)
- index(i) for Byte/Short/Int/Long/Float/Double pointers to advance by element size.
- load()/store() delegates to GlobalHeap typed routines.

Usage
- Treat addresses as lvalues. Avoid copying into Kotlin arrays for persistent state; use GlobalHeap directly.

Interop
- Strings: see CString helpers and CLib wrappers (CLibSymbols.kt) for C‑like signatures.

