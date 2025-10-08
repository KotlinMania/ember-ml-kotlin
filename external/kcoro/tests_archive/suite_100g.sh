#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

export KCORO_DEBUG=0          # keep console quiet for heavy stress
export KCORO_BENCH_SPIN=${KCORO_BENCH_SPIN:-4096}
export KCORO_BENCH_PIN=${KCORO_BENCH_PIN:-1}

echo "[suite] build lib/tests"
make -C ../user >/dev/null
make -C . >/dev/null

ts=$(date +%Y%m%d-%H%M%S)
logdir="logs/suite_$ts"
mkdir -p "$logdir"
echo "[suite] logs -> $logdir"

run() {
  local cmd="$1"; echo "[suite] RUN $cmd";
  bash -lc "$cmd" 2>&1 | tee "$logdir/$(echo "$cmd" | tr ' ' '_').log"
}

# Standard 100Gbps test
run "build/test_stress_pc 6 4 200000 8192 64"

# Burst capacity test
run "build/test_stress_pc 8 6 500000 16384 64"

# Low-latency test (small buffers)
run "build/test_stress_pc 4 2 100000 1024 64"

# Memory efficiency test (large packets)
run "build/test_stress_pc 4 4 50000 4096 1500"

echo "[suite] done"
