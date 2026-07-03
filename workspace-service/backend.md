# Workspace Service — Backend Summary

## Overview

A Spring Boot microservice that is the **spatial backbone** of the Virtual Office. It owns
Workspaces, Desks (membership + per-workspace profile + live position), Teams, MapObjects,
Invitations, and the **2D floorplan** (tilesets / layers / zones / spawn points). It is the
single source of truth the SkyOffice/Colyseus client renders from, and it exposes a
server-to-server **session API** (`/api/internal/**`) consumed by the Colyseus fork,
room-service, and chat-service. Runs on port **8087** with PostgreSQL.

## Tech Stack

| Component | Tech |
|---|---|
| Framework | Spring Boot, Java 21 |
| Database | PostgreSQL (`workspace`); `jsonb` for widget/geometry config |
| Migrations | Flyway (`V1__init_schema.sql`, `R__seed_demo.sql`); Hibernate `ddl-auto: validate` |
| Messaging | RabbitMQ — **publishes** `workspace.channel.event` to chat-service |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Build | Maven (wrapper included) |
| Tests | JUnit + Testcontainers (Docker required) |

## Architecture

Runs behind the **API gateway** in a zero-trust setup. The gateway validates JWTs and forwards
`X-User-Id` / `X-User-Role`; workspace-service never touches JWTs. Server-to-server callers hit
`/api/internal/**` with a shared `X-Internal-Token` secret instead of a user identity.

```
Client  → Gateway (JWT validation) → Workspace Service (trusts X-User-* headers)
Services → (X-Internal-Token)       → Workspace Service (/api/internal/**)
```

## Project Structure

```
src/main/java/com/virtualoffice/workspace/
├── config/          # InternalAuthFilter, GlobalExceptionHandler, OpenApiConfig, RabbitConfig, LoggingAspect
├── controller/      # Workspace, Desk, Team, MapObject, Invitation, Layout, Session (internal), Health
├── dto/
│   ├── request/     # incoming payloads (records + Bean Validation)
│   ├── response/    # outgoing payloads (records)
│   ├── mapper/      # entity → DTO conversion
│   └── (Layout*)    # shared layout sub-records (Tileset/Layer/Zone/SpawnPoint)
├── model/           # JPA entities + enums/
├── repository/      # Spring Data JPA repos
├── service/         # interfaces + WorkspaceAccessGuard
│   └── impl/        # business logic
├── messaging/       # WorkspaceChannelEvent(+Type) + publisher
└── util/            # UserContext (header extraction)
```

## Data Model (see [DATABASE.md](./DATABASE.md) for the normalized BCNF schema)

- **Workspace** — `name`, unique `slug`, `ownerId`, `status` (`ACTIVE|ARCHIVED|SUSPENDED`),
  `visibility` (`INVITE_ONLY`), `inviteToken` (UUID), `defaultTimezone`, floorplan scalars
  (`tileSize`, `mapWidth`, `mapHeight`) and the optimistic **`layoutVersion`**.
- **Desk** — one per `(workspace, user)`. Membership + profile (`fullName`, `nickName`, `title`,
  `bio`, `avatarCharacter`, `timezone`, `links`, `widgets`) + `WorkspaceRole` + presence
  (`isOnline`, `positionX/Y`, `status`, `statusEmoji`, `lastSeenAt`) + `isActive` +
  `inviteStatus` + `teamId`. Removal = deactivation, not delete.
- **Team** — `name` (unique per workspace) + `description`.
- **MapObject** — `type` (`COMPUTER|WHITEBOARD`), `label`, `positionX/Y`, server-generated
  `roomId` (UUID), `capacity`, `isActive`.
- **WorkspaceInvitation** — `invitedEmail`, `invitedBy`, `token` (UUID), `role`, `status`
  (`PENDING|ACCEPTED|DECLINED|EXPIRED`), `expiresAt` (7-day TTL).
- **Layout tables** — `Tileset`, `MapLayer` (gid matrix as JSON `data`, `layerIndex`, `collides`),
  `Zone` (`type`, bounds, `voiceRoomId`, `proximityRadius`), `SpawnPoint`. Assembled into
  `LayoutResponse` on read; fully replaced on `PUT`.

## Key Features

### Workspaces
- Create → creator gets an **active `OWNER` desk** in the same transaction + publishes
  `WORKSPACE_CHANNEL_CREATE`.
- Update / rotate-invite-token require **ADMIN**; **archive is OWNER-only** (soft `ARCHIVED`).
- `slug` is globally unique (`409` on collision) and format-validated.

### Membership & Authorization
- `WorkspaceAccessGuard` centralizes it: `requireMember` (active desk) and `requireRole(min)`
  against the ranked `WorkspaceRole` (`GUEST<MEMBER<ADMIN<OWNER`).
- The `X-User-Role` header is the **account** role and does **not** gate workspace actions —
  every action resolves the caller's per-workspace desk role.
- Desk edits are **owner-only** (even admins can't edit another user's desk). Removing a member
  deactivates the desk and emits `WORKSPACE_CHANNEL_REMOVE_MEMBER`; the `OWNER` desk can't be
  removed (`409`).

### Invitations
- ADMIN invites by email + role (cannot invite as `OWNER`); one `PENDING` invite per email
  (`409` on duplicate). 7-day TTL.
- Accept (invitee's own identity) creates **or reactivates** the desk, flips to `ACCEPTED`,
  emits `WORKSPACE_CHANNEL_ADD_MEMBER`. Expired → `410` (status flipped to `EXPIRED`);
  non-pending / already-member → `409`.
- Decline requires only the token.

### Map Objects
- ADMIN CRUD + `toggle` (flip `isActive`). Each object gets a server-generated `roomId` (UUID)
  the whiteboard / room-service key off — never client-supplied.

### Layout (optimistic locking)
- `GET /layout` (MEMBER) assembles tilesets + ordered layers + zones + spawn points.
- `PUT /layout` (ADMIN) **fully replaces** the floorplan in one transaction guarded by
  `expectedVersion`; stale version → `409`. Validates unique `layerIndex`, unique tileset
  `firstGid`, unique zone `voiceRoomId`. Uses `OPTIMISTIC_FORCE_INCREMENT` to bump
  `layoutVersion`.

### Session API (internal, `/api/internal/**`)
- `session-config` (full room boot: workspace meta + layout + active desks + active map objects),
  `join-validation/{userId}` (Colyseus `onAuth`), `members/{userId}/role` (chat/room-service
  auth), `presence` + `presence/batch` (Colyseus presence flush, batch skips missing desks),
  `zones` (room-service proximity/voice), `chat-context` (canonical `workspace:{id}` channel key).
- Guarded by `InternalAuthFilter` (`X-Internal-Token`); wrong/missing token → `403`.

### Messaging (RabbitMQ publisher)
- `WorkspaceChannelEventPublisher` emits `WorkspaceChannelEvent` to `workspace.exchange` /
  routing key `workspace.channel.event` on membership changes: `WORKSPACE_CHANNEL_CREATE`
  (workspace created), `_ADD_MEMBER` (invite accepted), `_REMOVE_MEMBER` (member removed).
  chat-service consumes these to keep the canonical workspace chat channel in sync
  (see [INTEGRATION.md](./INTEGRATION.md) §5.1). workspace-service stores **no messages**.

### Error Handling
- `GlobalExceptionHandler` maps domain exceptions to a uniform `{status,error,message,timestamp}`
  body: `ResourceNotFound`→404, `Forbidden`→403, `Conflict`/`DataIntegrityViolation`/
  `OptimisticLockingFailure`→409, `Gone`→410, validation/`IllegalArgument`→400,
  `DataAccessResourceFailure`→503, `ResponseStatusException` forwarded (401/400 from header
  parsing), catch-all→500.

### Observability (AOP)
- `LoggingAspect` logs service (DEBUG) and controller (INFO) calls with timing; business code
  needs no manual logging.

## Configuration

| Setting | Value |
|---|---|
| Port | `8087` |
| DB URL | `WORKSPACE_DB_URL` (default `jdbc:postgresql://localhost:5432/workspace`) |
| Internal token | `INTERNAL_API_TOKEN` (default `dev-internal-token`) |
| RabbitMQ | `RABBITMQ_HOST/PORT/USER/PASSWORD` (default `localhost:5672` guest/guest) |
| Exchange / routing key | `WORKSPACE_EXCHANGE` / `WORKSPACE_CHANNEL_ROUTING_KEY` |
| Swagger UI | `/swagger-ui.html` · OpenAPI JSON `/v3/api-docs` |

## How to Run

> Requires **JDK 21** (Lombok limitation). PostgreSQL is auto-started in dev via
> `compose.yml` + spring-boot-docker-compose.

```bash
# set JAVA_HOME first
export JAVA_HOME=/path/to/your/jdk                         # Linux / Mac
$env:JAVA_HOME = "C:\Users\Two Star\.jdks\openjdk-24.0.2"  # Windows (PowerShell)

# run the service (starts Postgres for you)
./mvnw spring-boot:run     # Linux / Mac
mvnw.cmd spring-boot:run   # Windows
```

Service starts on `http://localhost:8087`. Health: `GET /api/workspace/health` → `OK`.

## Build & Test

```bash
# from the repo root — Testcontainers needs Docker running
./mvnw -pl workspace-service -am verify
```

## Related Docs

- [README.md](./README.md) — quickstart · [frontend.md](./frontend.md) — REST/API integration guide
- [Design.md](./Design.md) — entities · [DATABASE.md](./DATABASE.md) — normalized schema
- [INTEGRATION.md](./INTEGRATION.md) — SkyOffice/Colyseus + room/chat wiring · [TASKS.md](./TASKS.md) — milestones
</content>
