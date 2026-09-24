#!/usr/bin/env bash
set -euo pipefail

SERVER="$(pwd)/null-market-server"
TEST_DATA="$(mktemp -d)"
PORT=18899
"$SERVER" --port "$PORT" --data "$TEST_DATA" >/dev/null 2>&1 &
SERVER_PID=$!
cleanup() {
  kill "$SERVER_PID" 2>/dev/null || true
  rm -rf "$TEST_DATA"
}
trap cleanup EXIT

for _ in $(seq 1 30); do
  if curl --max-time 1 --silent --fail "http://127.0.0.1:$PORT/v1/health" | grep -q '"tradingEnabled":false'; then
    break
  fi
  sleep 0.1
done

curl --max-time 2 --silent --fail "http://127.0.0.1:$PORT/v1/market/snapshot" | grep -q 'BTC/USDT'
curl --max-time 2 --silent --fail -X POST --data '{"ciphertext":"opaque"}' "http://127.0.0.1:$PORT/v1/packages" | grep -q '"stored":true'
test -s "$TEST_DATA/opaque-packages.ndjson"
