# Build System & Developer Notes

## Core Library
```bash
# Build kcoro library
make -C kcoro/core
ls kcoro/core/build/lib/libkcoro.a

# Or use top-level orchestrated build
cd /path/to/coroutines
make kcoro          # Build kcoro core + tests
make go-tools       # Build Go IPC tools
make all            # Build everything
```

### Repository build hints
- Top-level make targets: `make kcoro`, `make go-tools`, `make all` orchestrate core and IPC tools.
- To build only the core library: `make -C kcoro/user`
- To run the TUI monitor, build `kcoro_chanmon` and run it against a socket exported by `kcoro_srv`.

## Complete IPC Pipeline
```bash
# Build distributed IPC system
make -C ipc/posix   # POSIX distributed channels
make go-tools       # Go server + tools

# Test IPC system
./build/go/kcoro_srv -sock /tmp/kcoro.sock &
./build/go/bridge -sock /tmp/kcoro.sock
```

## Tests & Benchmarks
- Run unit tests and stress harnesses: `tests/run.sh [--full] [--long] [--bench]`
- Benchmarks: `BENCH=1 tests/run.sh [--debug]`

## Build-time tunables
- `include/kcoro_config.h` exposes knobs like `KCORO_CANCEL_SLICE_MS`, `KCORO_UNLIMITED_INIT_CAP`, `KCORO_IPC_BACKLOG`, and `KCORO_IPC_MAX_TLV_ELEM`.

