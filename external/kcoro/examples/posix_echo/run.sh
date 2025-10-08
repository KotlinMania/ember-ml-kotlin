#!/usr/bin/env bash
set -euo pipefail

sock=${1:-/tmp/kcoro.sock}

cd "$(dirname "$0")"
echo "[build] IPC lib + demo"
make -C ../../ipc/posix >/dev/null
make -C . >/dev/null

echo "[run] server -> $sock"
rm -f "$sock"
./build/server "$sock" &
srv=$!
trap 'kill $srv 2>/dev/null || true; rm -f "$sock"' EXIT
sleep 0.2

echo "[run] client"
./build/client "$sock"
