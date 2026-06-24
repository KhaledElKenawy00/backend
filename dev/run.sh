#!/usr/bin/env bash
#
# One-command Virtual Office demo: infra (Docker) + the Spring services + the Colyseus fork + the
# SkyOffice client, then mints a demo JWT and prints the URL to walk around the seeded demo office.
#
#   ./run.sh         start everything and print the URL
#   ./stop.sh        stop everything
#
# Requires: Docker (compose v2), a JDK 21, Node/Yarn. The office demo does not need user-service or
# MySQL — the JWT is minted directly and validated by the gateway with the shared secret.
set -euo pipefail

DEV="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$(cd "$DEV/.." && pwd)"
SKYOFFICE="$(cd "$BACKEND/../SkyOffice" && pwd)"
RUN="$DEV/.run"
LOGS="$RUN/logs"
PIDFILE="$RUN/pids"
mkdir -p "$LOGS"
: > "$PIDFILE"

# ── Shared config (defaults match every service's application config) ──────────────────────────
export JWT_SECRET="${JWT_SECRET:-your-very-strong-secret-key-must-be-at-least-32-characters-long}"
export INTERNAL_API_TOKEN="${INTERNAL_API_TOKEN:-dev-internal-token}"
CLIENT_PORT=5173

# Build/run on JDK 21 — the default JDK 26 breaks Lombok. Prefer the known JDK 21 if present;
# otherwise rely on JAVA_HOME / PATH (point JAVA_HOME at a JDK 21 if your default differs).
if [[ -d "$HOME/.jdks/ms-21.0.11" ]]; then
  export JAVA_HOME="$HOME/.jdks/ms-21.0.11"
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

log()  { printf '\033[1;36m[run]\033[0m %s\n' "$*"; }
note() { printf '\033[1;33m[run]\033[0m %s\n' "$*"; }

wait_http() { # name url
  local name="$1" url="$2" i
  for i in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then log "$name is up"; return 0; fi
    sleep 2
  done
  note "$name did not become healthy — see $LOGS/$name.log"; return 1
}

start_jar() { # name extra-env...
  local name="$1"; shift
  local jar
  jar="$(ls "$BACKEND/$name/target/$name-"*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
  [[ -n "$jar" ]] || { note "no jar for $name (build failed?)"; exit 1; }
  ( env "$@" "$JAVA" -jar "$jar" >"$LOGS/$name.log" 2>&1 & echo $! >>"$PIDFILE" )
  log "started $name (pid $(tail -1 "$PIDFILE"))"
}

# ── 1. Infra ───────────────────────────────────────────────────────────────────────────────────
log "starting infra (postgres, mongo, redis, rabbitmq)…"
docker compose -f "$DEV/compose.yml" up -d --wait
log "infra healthy"

# ── 2. Build the services (skip tests; JDK 21) ──────────────────────────────────────────────────
log "building services (first run downloads deps, be patient)…"
( cd "$BACKEND" && ./mvnw -q -DskipTests -pl gateway-api,workspace-service,chat-service,room-service -am package )

# ── 3. Start the services ───────────────────────────────────────────────────────────────────────
# workspace-service owns its own compose.yml; disable it so it uses the shared infra above.
# DEMO_SEED=true makes chat/room seed their demo data (workspace-service seeds via Flyway).
start_jar workspace-service SPRING_DOCKER_COMPOSE_ENABLED=false
start_jar chat-service DEMO_SEED=true
start_jar room-service DEMO_SEED=true
start_jar gateway-api

wait_http workspace-service http://localhost:8087/api/workspace/health
wait_http chat-service      http://localhost:8084/api/chat/health
wait_http room-service      http://localhost:8086/api/rooms/health
wait_http gateway-api       http://localhost:8080/actuator/health

# ── 4. Mint the demo JWT + resolve the seeded workspace id ──────────────────────────────────────
TOKEN="$(node "$DEV/mint-jwt.mjs")"
WID="$(docker compose -f "$DEV/compose.yml" exec -T postgres \
        psql -U workspace -d workspace -tAc "select id from workspace where slug='demo'" \
        2>/dev/null | tr -d '[:space:]')"
WID="${WID:-1}"

# ── 5. Colyseus fork + SkyOffice client ─────────────────────────────────────────────────────────
log "starting Colyseus server (SkyOffice fork)…"
( cd "$SKYOFFICE" && \
    WORKSPACE_SERVICE_URL=http://localhost:8087 ROOM_SERVICE_URL=http://localhost:8086 \
    INTERNAL_API_TOKEN="$INTERNAL_API_TOKEN" JWT_SECRET="$JWT_SECRET" \
    yarn --silent start >"$LOGS/colyseus.log" 2>&1 & echo $! >>"$PIDFILE" )

log "starting SkyOffice client (vite)…"
( cd "$SKYOFFICE/client" && \
    VITE_API_URL=http://localhost:8080 \
    yarn --silent dev --port "$CLIENT_PORT" --strictPort >"$LOGS/client.log" 2>&1 & echo $! >>"$PIDFILE" )
wait_http client "http://localhost:$CLIENT_PORT" || true

URL="http://localhost:$CLIENT_PORT/?token=$TOKEN&workspaceId=$WID"
printf '\n\033[1;32m========================================================================\033[0m\n'
printf '  Virtual Office demo is up. Open the office (workspace %s):\n\n  %s\n' "$WID" "$URL"
printf '\n  Logs: %s   ·   Stop: %s/stop.sh\n' "$LOGS" "$DEV"
printf '\033[1;32m========================================================================\033[0m\n'
