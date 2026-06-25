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

wait_responding() { # name url — up as soon as it returns ANY HTTP status (the gateway has no
  local name="$1" url="$2" i code   # actuator; a protected route answers 401, which still means up)
  for i in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)"
    if [[ "$code" != "000" && -n "$code" ]]; then log "$name is up (HTTP $code)"; return 0; fi
    sleep 2
  done
  note "$name did not start — see $LOGS/$name.log"; return 1
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
# Postgres is published on host 5433 (see compose.yml), so point workspace-service there.
# DEMO_SEED=true makes chat/room seed their demo data (workspace-service seeds via Flyway).
start_jar workspace-service SPRING_DOCKER_COMPOSE_ENABLED=false \
  WORKSPACE_DB_URL=jdbc:postgresql://localhost:5433/workspace
start_jar chat-service DEMO_SEED=true
start_jar room-service DEMO_SEED=true
start_jar gateway-api

wait_http workspace-service http://localhost:8087/api/workspace/health
wait_http chat-service      http://localhost:8084/api/chat/health
wait_http room-service      http://localhost:8086/api/rooms/health
wait_responding gateway-api http://localhost:8080/api/workspace/1/layout

# ── 4. Mint two demo JWTs + resolve the seeded workspace id ──────────────────────────────────────
# Two users so you can open the same office in two browsers and try chat + proximity voice between
# them. Both are seeded members of workspace 'demo' (users 1..5); we use user 1 and user 2.
TOKEN1="$(DEMO_USER_ID=1 DEMO_EMAIL=demo@office.dev   node "$DEV/mint-jwt.mjs")"
TOKEN2="$(DEMO_USER_ID=2 DEMO_EMAIL=ash@office.dev     node "$DEV/mint-jwt.mjs")"
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

URL1="http://localhost:$CLIENT_PORT/?token=$TOKEN1&workspaceId=$WID"
URL2="http://localhost:$CLIENT_PORT/?token=$TOKEN2&workspaceId=$WID"
printf '\n\033[1;32m========================================================================\033[0m\n'
printf '  Virtual Office demo is up. Open BOTH urls (workspace %s) in two browser windows\n' "$WID"
printf '  (use two profiles or one normal + one incognito), then walk the avatars together to\n'
printf '  test proximity voice, and use the chat panel to message each other:\n\n'
printf '  User 1 (Demo User): %s\n\n' "$URL1"
printf '  User 2 (Ash Rivera): %s\n' "$URL2"
printf '\n  A second office (workspace 2, users 6..10) is also seeded for isolation testing.\n'
printf '  Logs: %s   ·   Stop: %s/stop.sh\n' "$LOGS" "$DEV"
printf '\033[1;32m========================================================================\033[0m\n'
