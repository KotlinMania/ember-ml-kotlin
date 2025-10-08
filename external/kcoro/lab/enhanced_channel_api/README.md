Enhanced Channel API (Archived)

Summary
- This header was an exploratory surface for a richer channel API beyond the
  currently shipped kcoro channel interface. It is not referenced by the
  production build or tests and is archived here for future forensics.

What it likely aimed to cover (hypothesis)
- Additional channel semantics and variants not finalized in the main API:
  - SPSC/MPMC fast paths and/or specialization hooks
  - Zero‑copy or buffer‑borrowing patterns for larger payloads
  - Conflated/unlimited configuration knobs at the API boundary
  - Async/await style entry points tied to a scheduler (park/wake integration)
  - Try/timeout overloads with consistent error semantics
- Extensibility hooks for per‑channel instrumentation or backpressure policies.

Rationale for archiving
- The current production direction consolidates on the stable kcoro channel API
  plus the ARM64 coroutine core and scheduler. Anything experimental or not
  referenced by production is moved under lab/ to keep the main include/
  canonical and free of unused surfaces.

Next steps (if needed)
- Perform a detailed forensic review of the archived header to identify any
  missing features worth promoting to the production channel API.
- If we decide to bring any ideas forward (e.g., park/wake, specialization),
  implement them directly in the production code path with tests and docs,
  rather than resurrecting this header wholesale.
