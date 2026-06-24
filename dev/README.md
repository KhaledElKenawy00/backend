# Demo stack runner

One command brings up the whole Virtual Office demo — infra in Docker, the Spring services +
the Colyseus fork + the SkyOffice client on the host — seeds consistent demo data across every
service, mints a demo JWT, and prints the URL to walk around the office.

```bash
cd backend/dev
./run.sh        # start everything, prints the office URL
./stop.sh       # stop everything (host processes + infra)
```

Open the printed URL (e.g. `http://localhost:5173/?token=…&workspaceId=1`).

## What it starts
- **Infra** (`compose.yml`): PostgreSQL, MongoDB, Redis, RabbitMQ.
- **Services**: workspace-service (8087), chat-service (8084), room-service (8086), gateway (8080).
- **Colyseus fork** (2567) and the **SkyOffice client** (5173).

user-service / MySQL are **not** required for the office demo — the JWT is minted directly
(`mint-jwt.mjs`) and validated by the gateway with the shared secret. Add them later for real login.

## Demo data (workspace 1, users 1–3) — synced across services
- **workspace-service** (Flyway `R__seed_demo.sql`): "Demo Office", a 25×18 tile map, a meeting-room
  zone + open floor, 3 active desks — user 1 *Demo User/ADAM* (OWNER, the login), user 2 *Ash/ASH*,
  user 3 *Lucy/LUCY* — and 2 computers + 1 whiteboard.
- **chat-service** (`DemoSeeder`, `demo.seed=true`): the canonical `general` channel for workspace 1
  with members 1–3 and a few seeded messages.
- **room-service** (`DemoSeeder`, `demo.seed=true`): a `Lounge` voice room in workspace 1.

All seeders are idempotent. workspace-service seeds on every boot (Flyway); chat/room seed only when
`demo.seed=true`, which `run.sh` sets.

## Requirements
- Docker (compose v2), Node + Yarn, and a **JDK 21** (the default JDK 26 breaks Lombok). `run.sh`
  uses `~/.jdks/ms-21.0.11` if present; otherwise set `JAVA_HOME` to a JDK 21.

## Configuration (env overrides)
`JWT_SECRET`, `INTERNAL_API_TOKEN` (must match across services), `JAVA_HOME`. The SkyOffice client
reads the gateway URL from `VITE_API_URL` and voice from `VITE_ROOM_WS_URL` / `VITE_AGORA_APP_ID`.

Logs are under `.run/logs/`. Real voice audio needs an Agora app-id that matches room-service.
