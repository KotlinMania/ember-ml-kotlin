Legacy pthread-based benchmarks (Archived)

Summary
- This folder contains the original pthread-based benchmarks and drivers
  that exercised kcoro channels without using the assembly-based coroutine core.
- These benchmarks are not part of the default build.

Contents
- kc_bench.c           — pthread producer/consumer benchmark over kcoro channels
- kc_actor_bench.c     — actor benchmark variant using pthreads

Notes
- The production direction favors the assembly-backed coroutine core
  and scheduler integration. These files are kept for historical
  reference and can be used for ad-hoc comparisons if required.
