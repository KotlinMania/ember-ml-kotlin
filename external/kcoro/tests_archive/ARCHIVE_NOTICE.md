ARCHIVE: tests_archive/

This directory contains archived test harnesses and benchmarks kept for performance comparisons and historical playback.

DO NOT: include these tests in CI or the default build. If you need to run archived tests, set `ENABLE_ARCHIVE=1` and run them explicitly.

Consider migrating needed benchmarks to `bench/` and pruning the rest to keep the codebase focused.