# Demo stack runner

One command brings up the whole Virtual Office demo — infra in Docker, the Spring services +
the Colyseus fork + the SkyOffice client on the host — seeds consistent demo data across every
service, mints a demo JWT, and prints the URL to walk around the office.

```bash
cd backend/dev
./run.sh        # start everything, prints the office URL
./stop.sh       # stop everything (host processes + infra)
```

It prints **two** URLs — one for user 1 and one for user 2, both in workspace 1. Open each in a
separate browser window (two profiles, or one normal + one incognito) to drive two avatars: walk
them together to test proximity voice, and use the chat panel to message between them.

## What it starts
- **Infra** (`compose.yml`): PostgreSQL, MongoDB, Redis, RabbitMQ.
- **Services**: workspace-service (8087), chat-service (8084), room-service (8086), gateway (8080).
- **Colyseus fork** (2567) and the **SkyOffice client** (5173).

user-service / MySQL are **not** required for the office demo — the JWT is minted directly
(`mint-jwt.mjs`) and validated by the gateway with the shared secret. Add them later for real login.

## Demo data (two workspaces, five users each) — synced across services
Two workspaces are seeded with matching members across every service:
- **workspace 1** `demo` (id 1): users **1..5** — *Demo User/ADAM* (OWNER, login), Ash/ASH, Lucy/LUCY,
  Nancy/NANCY, Sam/ADAM.
- **workspace 2** `demo2` (id 2): users **6..10** — a second office, for workspace-isolation testing.

Each workspace gets, per service:
- **workspace-service** (Flyway `R__seed_demo.sql`): a 25×18 tile map, a meeting-room zone + open
  floor, 5 active desks spread near the spawn (so two users land in one proximity voice group), and
  2 computers + 1 whiteboard.
- **chat-service** (`DemoSeeder`, `demo.seed=true`): the canonical `general` channel with all five
  members and a few seeded messages.
- **room-service** (`DemoSeeder`, `demo.seed=true`): a `Lounge` voice room with all five members.

All seeders are idempotent. workspace-service seeds on every boot (Flyway); chat/room seed only when
`demo.seed=true`, which `run.sh` sets.

## Requirements
- Docker (compose v2), Node + Yarn, and a **JDK 21** (the default JDK 26 breaks Lombok). `run.sh`
  uses `~/.jdks/ms-21.0.11` if present; otherwise set `JAVA_HOME` to a JDK 21.

## Configuration (env overrides)
`JWT_SECRET`, `INTERNAL_API_TOKEN` (must match across services), `JAVA_HOME`. The SkyOffice client
reads the gateway URL from `VITE_API_URL` and voice from `VITE_ROOM_WS_URL` / `VITE_AGORA_APP_ID`.

Logs are under `.run/logs/`. Real voice audio needs an Agora app-id that matches room-service.
