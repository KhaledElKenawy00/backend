# Workspace Service — Frontend Integration Guide

The **spatial backbone** of the Virtual Office. It owns Workspaces, Desks (membership +
per-workspace profile + position), Teams, MapObjects (computers / whiteboards), Invitations,
and the **2D floorplan** (tilesets / layers / zones / spawn points). It is the single source
of truth the SkyOffice/Colyseus client renders from, and it exposes a server-to-server
**session API** that room-service, chat-service, and the Colyseus fork consume.

## Prerequisites

- **Java 21+** (JDK — Lombok does not support newer JDKs yet)
- **Docker** (for PostgreSQL; auto-started in dev via `compose.yml`)

## How to Run

```bash
# 1. cd into the service
cd backend/workspace-service

# 2. set JAVA_HOME (point to your JDK 21 installation)
# Linux / Mac
export JAVA_HOME=/path/to/your/jdk
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Users\Two Star\.jdks\openjdk-24.0.2"

# 3. run the service — Spring Boot's docker-compose integration starts PostgreSQL for you
# Linux / Mac
./mvnw spring-boot:run
# Windows
mvnw.cmd spring-boot:run
```

The service starts on **http://localhost:8087**.

- Health check: `GET /api/workspace/health` → plain text body `OK`
- **Swagger UI: http://localhost:8087/swagger-ui.html** (OpenAPI JSON at `/v3/api-docs`)

---

## Authentication

This service does **not** handle JWT itself. It expects the **API gateway** to validate the
JWT and forward these headers on every request:

| Header | Description |
|---|---|
| `X-User-Id` | integer user ID (e.g. `42`) |
| `X-User-Role` | `ADMIN` or `USER` (case-insensitive) — this is the **account-level** role |

For local testing without the gateway, just set them manually.

- Missing/blank `X-User-Id` → **401**.
- Non-numeric `X-User-Id`, or missing/invalid `X-User-Role` → **400**.

> **Account role vs. workspace role.** `X-User-Role` (`USER`/`ADMIN`) is the *platform*
> account role and is **not** what gates workspace actions. Every workspace endpoint resolves
> a per-workspace **`WorkspaceRole`** (`GUEST < MEMBER < ADMIN < OWNER`) from the caller's
> **Desk** in that workspace and authorizes against it. You can be a platform `USER` but the
> `OWNER` of your workspace. See [Authorization](#authorization-per-workspace-role).

### Internal endpoints

Everything under `/api/internal/**` is **server-to-server only**: it is blocked at the gateway
for browsers and guarded by a shared-secret header `X-Internal-Token` (env `INTERNAL_API_TOKEN`).
There is no end-user identity on these — a wrong/missing token returns **403**. Frontends never
call these directly; they are documented in [Internal / Session API](#internal--session-api-server-to-server).

---

## Core concepts

- **Workspace** — a virtual office. Created by a user, who automatically gets an **active
  `OWNER` Desk**. Visibility is always `INVITE_ONLY`. Archiving is a soft state change.
- **Desk** — a user's membership *and* profile *and* live position **inside one workspace**.
  One desk per `(workspace, user)`. Carries the profile (fullName, avatar, title, bio, links,
  widgets), the workspace role, live presence (`isOnline`, `positionX/Y`, `status`), and an
  `isActive` flag. Removing a member **deactivates** the desk (`isActive:false`) rather than
  deleting it, so re-accepting an invite reactivates the same desk.
- **Team** — a named grouping inside a workspace; a desk can reference a `teamId`.
- **MapObject** — an interactive object placed on the floor: `COMPUTER` or `WHITEBOARD`. Each
  gets a server-generated `roomId` (used by room-service / the whiteboard).
- **Invitation** — an email + target role + one-time `token` (UUID), 7-day TTL. Accepting it
  activates the invitee's desk.
- **Layout** — the floorplan: tile geometry + `tilesets` + `layers` + `zones` + `spawnPoints`.
  Guarded by an **optimistic `layoutVersion`** to prevent lost updates from concurrent editors.

---

## Authorization (per-workspace role)

`WorkspaceRole` ranks `GUEST(0) < MEMBER(1) < ADMIN(2) < OWNER(3)`. "Requires ADMIN" means
ADMIN **or** OWNER.

| Action | Minimum role |
|---|---|
| Create a workspace | any authenticated user (becomes `OWNER`) |
| Read workspace / layout / desks / teams / map-objects | **MEMBER** (active desk) |
| Update workspace, rotate invite token | **ADMIN** |
| **Archive** workspace | **OWNER** only (stricter than ADMIN) |
| Create / update / delete Team | **ADMIN** |
| Create / update / toggle / delete MapObject | **ADMIN** |
| Update layout (`PUT /layout`) | **ADMIN** |
| Invite / list invitations / revoke invitation | **ADMIN** |
| Update **own** desk / **own** status | the desk owner only (any role) |
| Update **another** user's desk | ❌ never (`403`) |
| Remove a member | **ADMIN** (the `OWNER` desk cannot be removed → `409`) |

A caller with no **active** desk in the workspace gets **403** ("not an active member"). A
caller whose desk exists but has too low a role gets **403** ("requires role … or higher").

---

## REST API

Base path: `/api/workspace`. All IDs are **integers (`Long`)**. Timestamps are **ISO-8601 UTC**
(e.g. `"2026-07-03T14:00:00Z"`).

### Workspaces

#### Create a workspace
```
POST /api/workspace
```
```json
{
  "name": "Acme HQ",
  "slug": "acme-hq",
  "description": "Our virtual office",
  "logoUrl": "https://…/logo.png",
  "defaultTimezone": "Africa/Cairo"
}
```
- `name` required (≤120). `slug` required — **3–40 lowercase letters, digits, or hyphens**,
  must start/end alphanumeric (`^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$`). `defaultTimezone`
  required. `description`/`logoUrl` optional.
- The creator is given an **active `OWNER` desk** in the same transaction, and a
  `WORKSPACE_CHANNEL_CREATE` event is published so chat-service provisions the canonical
  workspace chat channel.

**Response** `201` — `WorkspaceResponse`:
```json
{
  "id": 100,
  "name": "Acme HQ",
  "slug": "acme-hq",
  "ownerId": 1,
  "description": "Our virtual office",
  "logoUrl": "https://…/logo.png",
  "status": "ACTIVE",
  "visibility": "INVITE_ONLY",
  "inviteToken": "e556486f-435d-4537-bea0-e7d1e1f0627b",
  "defaultTimezone": "Africa/Cairo",
  "tileSize": 32,
  "mapWidth": 25,
  "mapHeight": 25,
  "layoutVersion": 0,
  "createdAt": "2026-07-03T14:00:00Z",
  "updatedAt": "2026-07-03T14:00:00Z"
}
```
`409 Conflict` if the `slug` is already taken.

#### List my workspaces
```
GET /api/workspace/mine
```
Returns `WorkspaceResponse[]` — every workspace the caller has a desk in.

#### Get a workspace
```
GET /api/workspace/{id}
```
Requires MEMBER. `403` if not a member, `404` if it doesn't exist.

#### Update a workspace (ADMIN)
```
PUT /api/workspace/{id}
```
Partial update — `null` fields are left unchanged.
```json
{ "name": "New name", "description": "…", "logoUrl": "…", "defaultTimezone": "…" }
```

#### Archive a workspace (OWNER only)
```
DELETE /api/workspace/{id}
```
Soft archive — sets `status: "ARCHIVED"` and returns the updated `WorkspaceResponse`. Only the
owner may do this (`403` otherwise).

#### Rotate the invite token (ADMIN)
```
POST /api/workspace/{id}/rotate-invite-token
```
Generates a new `inviteToken` (invalidating the old shareable link) and returns the workspace.

---

### Desks (members)

Base path: `/api/workspace/{workspaceId}/desks`

#### List members (paginated)
```
GET /api/workspace/{workspaceId}/desks?page=0&size=20
```
Requires MEMBER. **Pages are 0-based** (Spring `Pageable`). Returns a `PageResponse<DeskResponse>`:
```json
{
  "content": [ /* DeskResponse[] */ ],
  "page": 0,
  "size": 20,
  "totalElements": 12,
  "totalPages": 1
}
```

#### Get my desk
```
GET /api/workspace/{workspaceId}/desks/me
```
The caller's own desk (with links + widgets). `403` if the caller has no active desk.

#### Get a desk by id
```
GET /api/workspace/{workspaceId}/desks/{deskId}
```
Requires MEMBER.

#### Update my desk
```
PUT /api/workspace/{workspaceId}/desks/{deskId}
```
Only the **desk owner** can update it (`403` otherwise) — even admins cannot edit someone
else's desk. Partial — `null` fields unchanged; `links` and `widgets`, when present, **replace**
the existing lists wholesale.
```json
{
  "fullName": "Ada Lovelace",
  "nickName": "Ada",
  "title": "Engineer",
  "bio": "…",
  "avatarCharacter": "LUCY",
  "timezone": "Africa/Cairo",
  "teamId": 7,
  "links": ["https://github.com/ada"],
  "widgets": [
    { "type": "clock", "label": "Local time", "position": 0, "config": "{\"tz\":\"…\"}" }
  ]
}
```
`avatarCharacter` ∈ `ADAM | ASH | LUCY | NANCY`. `widgets[].position` is required; `config` is
a free-form JSON string.

**Response** `200` — `DeskResponse`:
```json
{
  "id": 5, "userId": 42, "workspaceId": 100,
  "fullName": "Ada Lovelace", "nickName": "Ada", "title": "Engineer",
  "workEmail": "ada@acme.io", "phone": null, "personalImageUrl": null,
  "avatarCharacter": "LUCY", "timezone": "Africa/Cairo",
  "status": "ACTIVE", "statusEmoji": null, "statusCustomText": null,
  "positionX": 705, "positionY": 500, "isOnline": true,
  "lastSeenAt": "2026-07-03T14:00:00Z",
  "role": "MEMBER", "bio": "…", "teamId": 7,
  "inviteStatus": "ACCEPTED", "isActive": true,
  "joinedAt": "2026-07-01T09:00:00Z",
  "links": ["https://github.com/ada"],
  "widgets": [ { "id": 3, "type": "clock", "label": "Local time", "position": 0, "config": "…" } ]
}
```

#### Update my status
```
PATCH /api/workspace/{workspaceId}/desks/{deskId}/status
```
Owner-only. Sets the presence status shown to others.
```json
{ "status": "FOCUS_MODE", "statusEmoji": "🎧", "statusCustomText": "Heads-down until 3pm" }
```
`status` ∈ `ACTIVE | AWAY | DO_NOT_DISTURB | FOCUS_MODE | CUSTOM`.

#### Remove a member (ADMIN)
```
DELETE /api/workspace/{workspaceId}/desks/{deskId}
```
Deactivates the desk (`isActive:false`) and emits `WORKSPACE_CHANNEL_REMOVE_MEMBER` to
chat-service. **Response `204`, empty body.** `409` if you try to remove the `OWNER` desk.

---

### Teams

Base path: `/api/workspace/{workspaceId}/teams`

| Method | Path | Role | Notes |
|---|---|---|---|
| `POST` | `/teams` | ADMIN | `201` → `TeamResponse`; `409` on duplicate name |
| `GET` | `/teams` | MEMBER | `TeamResponse[]` |
| `PUT` | `/teams/{teamId}` | ADMIN | partial update; `409` on duplicate name |
| `DELETE` | `/teams/{teamId}` | ADMIN | `204`, empty body |

Create body: `{ "name": "Backend", "description": "…" }` (`name` required ≤80, `description` ≤500).

`TeamResponse`: `{ "id", "workspaceId", "name", "description", "createdAt", "updatedAt" }`.

---

### Map Objects (computers / whiteboards)

Base path: `/api/workspace/{workspaceId}/map-objects`

#### Create (ADMIN)
```
POST /api/workspace/{workspaceId}/map-objects
```
```json
{ "type": "WHITEBOARD", "label": "Design board", "positionX": 12, "positionY": 8, "capacity": 4 }
```
`type` ∈ `COMPUTER | WHITEBOARD` (required). `positionX/Y` required. `capacity` required, ≥1.
`label` optional (≤120). The server generates a `roomId` (UUID) used by the whiteboard /
room-service — clients must not invent their own.

**Response** `201` — `MapObjectResponse`:
```json
{
  "id": 9, "workspaceId": 100, "type": "WHITEBOARD", "label": "Design board",
  "positionX": 12, "positionY": 8, "roomId": "b3e1…", "capacity": 4,
  "isActive": true, "createdAt": "…", "updatedAt": "…"
}
```

| Method | Path | Role | Notes |
|---|---|---|---|
| `GET` | `/map-objects` | MEMBER | `MapObjectResponse[]` |
| `PUT` | `/map-objects/{id}` | ADMIN | partial update (`label`,`positionX`,`positionY`,`capacity`) |
| `PATCH` | `/map-objects/{id}/toggle` | ADMIN | flips `isActive`, returns updated object |
| `DELETE` | `/map-objects/{id}` | ADMIN | `204`, empty body |

---

### Invitations

Members are added by **invitation → accept**. There is no "join by workspace id" flow —
visibility is always `INVITE_ONLY`.

#### Invite a member (ADMIN)
```
POST /api/workspace/{workspaceId}/invitations
```
```json
{ "email": "new@acme.io", "role": "MEMBER" }
```
`email` required (valid email). `role` required, one of `GUEST | MEMBER | ADMIN` — **inviting
as `OWNER` is rejected (`400`)**. `409` if a `PENDING` invite already exists for that email.

**Response** `201` — `InvitationResponse`:
```json
{
  "id": 3, "workspaceId": 100, "invitedEmail": "new@acme.io", "invitedBy": 1,
  "token": "9f8b…", "role": "MEMBER", "status": "PENDING",
  "expiresAt": "2026-07-10T14:00:00Z", "createdAt": "2026-07-03T14:00:00Z"
}
```
Share the `token` (or the deep link carrying it) with the invitee. TTL is **7 days**.

#### List invitations (ADMIN)
```
GET /api/workspace/{workspaceId}/invitations
```
`InvitationResponse[]` — all invitations for the workspace (any status).

#### Revoke an invitation (ADMIN)
```
DELETE /api/workspace/{workspaceId}/invitations/{invitationId}
```
Deletes the invitation. **Response `204`, empty body.** `404` if not found.

#### Accept an invitation (invitee)
```
POST /api/invitations/accept?token={token}
```
Note this is **not** under `/api/workspace/{id}` — the invitee accepts with **their own**
`X-User-Id`. The desk is created (or reactivated) here, the invite flips to `ACCEPTED`, and a
`WORKSPACE_CHANNEL_ADD_MEMBER` event is emitted so chat-service adds them to the workspace channel.

**Response** `200` — the updated `InvitationResponse`.

Errors:
- `404` — unknown/malformed token.
- `410 Gone` — the invite expired (its status is flipped to `EXPIRED`).
- `409` — the invite is no longer `PENDING`, or the user is already an active member.

#### Decline an invitation
```
POST /api/invitations/decline?token={token}
```
Flips the invite to `DECLINED`. `409` if it is no longer `PENDING`. Note: decline does **not**
require `X-User-Id` (anyone holding the token can decline).

---

### Layout (2D floorplan)

Base path: `/api/workspace/{workspaceId}/layout`

#### Get the layout
```
GET /api/workspace/{workspaceId}/layout
```
Requires MEMBER. Returns the full assembled floorplan the client renders from.

**Response** `200` — `LayoutResponse`:
```json
{
  "workspaceId": 100,
  "tileSize": 32,
  "mapWidth": 25,
  "mapHeight": 25,
  "mapGeometry": "…optional free-form string…",
  "layoutVersion": 4,
  "tilesets": [
    { "name": "office", "imageUrl": "https://…/office.png",
      "firstGid": 1, "tileWidth": 32, "tileHeight": 32, "columns": 8, "tileCount": 64 }
  ],
  "layers": [
    { "name": "Ground", "layerIndex": 0, "collides": false, "data": "[[1,1,2,…]]" },
    { "name": "Walls",  "layerIndex": 1, "collides": true,  "data": "[[0,0,5,…]]" }
  ],
  "zones": [
    { "type": "MEETING_ROOM", "name": "Room A", "x": 5, "y": 5, "width": 6, "height": 4,
      "voiceRoomId": "room-a", "proximityRadius": null }
  ],
  "spawnPoints": [
    { "x": 705, "y": 500, "label": "lobby", "isDefault": true }
  ]
}
```
- `layers[].data` is the **gid matrix as a JSON string** — the client parses it into a tile grid.
  Layers arrive ordered by `layerIndex`. A `collides:true` layer is the wall/collision layer.
- `zones[].type` ∈ `MEETING_ROOM | FOCUS_AREA | SOCIAL | TEAM | OPEN`. `voiceRoomId` /
  `proximityRadius` drive room-service's private/proximity voice (see [INTEGRATION.md](./INTEGRATION.md) §4).

#### Update the layout (ADMIN — admin floorplan editor)
```
PUT /api/workspace/{workspaceId}/layout
```
The whole layout is **replaced** transactionally. You must send the `expectedVersion` you last
read; if it no longer matches the stored `layoutVersion` the request is rejected with **409**
— re-`GET` the layout, reapply your edit, and retry.
```json
{
  "expectedVersion": 4,
  "tileSize": 32,
  "mapWidth": 25,
  "mapHeight": 25,
  "mapGeometry": null,
  "tilesets": [ /* LayoutTileset[] */ ],
  "layers": [ /* LayoutLayer[] */ ],
  "zones": [ /* LayoutZone[] */ ],
  "spawnPoints": [ /* LayoutSpawnPoint[] */ ]
}
```
Required scalars: `expectedVersion`, `tileSize`≥1, `mapWidth`≥1, `mapHeight`≥1. The four lists
are optional (omit/`null` → treated as empty, i.e. cleared).

**Validation (`400`):**
- `layers[].layerIndex` must be unique.
- `tilesets[].firstGid` must be unique.
- non-null `zones[].voiceRoomId` must be unique.

On success `layoutVersion` is force-incremented and the reassembled `LayoutResponse` is returned.

---

## Internal / Session API (server-to-server)

Base path: `/api/internal/workspace/{workspaceId}`. **Not for browsers** — requires the
`X-Internal-Token` shared secret and is blocked at the gateway. Documented here so frontend/game
devs understand what the Colyseus fork and sibling services rely on.

| Method | Path | Consumer | Purpose |
|---|---|---|---|
| `GET` | `/session-config` | Colyseus fork | full room boot config: workspace meta + `layout` + **active** `desks` + **active** `mapObjects` (`SessionConfigResponse`) |
| `GET` | `/join-validation/{userId}` | Colyseus `onAuth` | validates the user has an active desk; returns identity + spawn + avatar + role (`JoinValidationResponse`, `allowed:true`). `404` if no active desk |
| `GET` | `/members/{userId}/role` | chat-service, room-service | workspace-scoped role check (`MemberRoleResponse`). `404` if no active desk |
| `POST` | `/presence` | Colyseus join/leave/status | one presence update (`PresenceSyncRequest`) → writes `isOnline`, `lastSeenAt`, and any non-null `status`/`statusEmoji`/`positionX`/`positionY` |
| `POST` | `/presence/batch` | Colyseus timer flush | many `PresenceSyncRequest` at once; missing desks are skipped, not failed |
| `GET` | `/zones` | room-service | zone bounds + `voiceRoomId` + `proximityRadius` (`ZoneResponse[]`) |
| `GET` | `/chat-context` | chat-service / SkyOffice | canonical workspace channel key: `{ "workspaceId": 100, "channelKey": "workspace:100" }` |

`PresenceSyncRequest`: `{ "userId", "isOnline", "status?", "statusEmoji?", "positionX?", "positionY?" }`
(`userId` + `isOnline` required). `JoinValidationResponse`: `{ userId, workspaceId, deskId,
fullName, avatarCharacter, spawnX, spawnY, role, allowed }`.

See [INTEGRATION.md](./INTEGRATION.md) for the full SkyOffice/Colyseus wiring, and
[DATABASE.md](./DATABASE.md) for the schema.

---

## Error Responses

All errors follow this shape:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "slug already in use: acme-hq",
  "timestamp": "2026-07-03T14:00:00Z"
}
```
Validation errors join multiple field violations with `; ` — e.g.
`"name: must not be blank; slug: slug must be 3-40 lowercase letters, digits, or hyphens"`.

| Status | When |
|---|---|
| `400` | validation failures, bad body, malformed `X-User-Id`/`X-User-Role`, inviting as `OWNER`, layout uniqueness violations |
| `401` | missing `X-User-Id` header |
| `403` | not an active member, insufficient workspace role, editing someone else's desk, wrong/missing `X-Internal-Token` |
| `404` | workspace / desk / team / map-object / invitation not found |
| `409` | duplicate slug, duplicate team name, pending invite exists, already a member, removing the OWNER, **stale layout version**, concurrent modification |
| `410` | invitation expired |
| `503` | database temporarily unavailable |

---

## Data Types Quick Reference

- All IDs are **integers** (`Long`). Timestamps are **ISO-8601 UTC**.
- Tokens (`inviteToken`, invitation `token`) are **UUID strings**.
- Pagination: **desk listing is 0-based** Spring `Pageable` (`page`, `size`) and returns the
  `PageResponse` envelope; all other list endpoints return plain arrays.
- Enums: `WorkspaceRole` = `GUEST|MEMBER|ADMIN|OWNER`; `WorkspaceStatus` = `ACTIVE|ARCHIVED|SUSPENDED`;
  `WorkspaceVisibility` = `INVITE_ONLY`; `DeskStatus` = `ACTIVE|AWAY|DO_NOT_DISTURB|FOCUS_MODE|CUSTOM`;
  `InviteStatus` = `PENDING|ACCEPTED|DECLINED|EXPIRED`; `AvatarCharacter` = `ADAM|ASH|LUCY|NANCY`;
  `MapObjectType` = `COMPUTER|WHITEBOARD`; `ZoneType` = `MEETING_ROOM|FOCUS_AREA|SOCIAL|TEAM|OPEN`.
- `avatarCharacter` maps to SkyOffice sprite keys lowercased (`adam`, `ash`, `lucy`, `nancy`);
  the client composes anim strings `<char>_<state>_<dir>` (e.g. `lucy_idle_down`).
</content>
