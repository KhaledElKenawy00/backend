#!/usr/bin/env bash
# Stops everything run.sh started: the host processes (services, Colyseus, client) and the infra.
set -uo pipefail

DEV="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIDFILE="$DEV/.run/pids"

if [[ -f "$PIDFILE" ]]; then
  while read -r pid; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null && echo "[stop] killed pid $pid" || true
  done <"$PIDFILE"
  : > "$PIDFILE"
fi

# Vite/ts-node-dev spawn children; sweep any stragglers on the demo ports.
for port in 8080 8084 8086 8087 2567 5173; do
  pid="$(lsof -ti tcp:"$port" 2>/dev/null || true)"
  [[ -n "$pid" ]] && kill $pid 2>/dev/null && echo "[stop] freed port $port" || true
done

echo "[stop] stopping infra…"
docker compose -f "$DEV/compose.yml" down
echo "[stop] done"
