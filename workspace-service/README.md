# Workspace Service

## Description
The Workspace Service is the **spatial backbone** of the Virtual Office: it owns Workspaces,
Desks (membership + workspace-specific profile + position), Teams, MapObjects (computers /
whiteboards), Invitations, and the **2D floorplan** (the DB is the source of truth — the
SkyOffice client renders from it). It also exposes the server-to-server **session API** the
Colyseus/SkyOffice layer, room-service, and chat-service consume.

See [`Design.md`](./Design.md) (entities), [`DATABASE.md`](./DATABASE.md) (normalized BCNF
schema), [`TASKS.md`](./TASKS.md) (implementation plan / milestones), and
[`INTEGRATION.md`](./INTEGRATION.md) (SkyOffice fork + room/chat wiring).

## Key Features
- Workspace, Team, Desk, MapObject, Invitation management
- 2D layout (tilesets / layers / zones / spawn points) with optimistic locking
- Member directory, presence, status
- Internal session API for SkyOffice (`/api/internal/**`)

## Configuration
- **Port**: `8087`
- **Database**: **PostgreSQL** (`workspace`) — `layoutMap`/widget config use `jsonb`; schema via Flyway
- **Auth**: trusts gateway-injected `X-User-Id` / `X-User-Role` headers (no JWT here)
- **Internal API**: `/api/internal/**` requires the `X-Internal-Token` shared secret
  (env `INTERNAL_API_TOKEN`) and is blocked at the gateway for external clients

## Setup
1. Start PostgreSQL (auto-started in dev via `compose.yml` + spring-boot-docker-compose).
2. Run the service: `./mvnw spring-boot:run`

Swagger UI: `http://localhost:8087/swagger-ui.html` · Health: `GET /api/workspace/health`

## Build & Test
> Requires **JDK 21** (Lombok does not yet support newer JDKs). Tests use Testcontainers
> (Docker required).

```bash
./mvnw -pl workspace-service -am verify
```
</content>
