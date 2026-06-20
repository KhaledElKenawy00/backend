# workspace-service — Implementation Tasks

> **Role:** the spatial backbone. Owns Workspace, Desk (membership), Team, MapObject,
> Invitation, and the **full 2D floorplan** (source of truth — see decision below).
> It is the configuration source of truth; the SkyOffice/Colyseus layer is the live-session
> source of truth. See [`Design.md`](./Design.md) for entities and [`INTEGRATION.md`](./INTEGRATION.md)
> for the cross-repo contract (SkyOffice fork, room-service voice, chat-service wiring).

---

## Locked decisions (read first)

These shape every phase below. Changing them ripples widely.

| # | Decision | Consequence |
|---|----------|-------------|
| D1 | **PostgreSQL** is the database (not MySQL). | Fix the README (says MySQL). Tests use **Testcontainers PostgreSQL**, not H2. |
| D8 | **Schema is fully normalized to BCNF.** No entity arrays in `jsonb`; `jsonb` survives only in 3 justified exceptions. See [`DATABASE.md`](./DATABASE.md). | The old `layoutMap`/`deskCustomization` blobs are decomposed into `zone`, `spawn_point`, `tileset`, `map_layer`, `desk_widget` tables. D2 still holds — the map is DB-owned, just normalized across tables and reassembled at the API edge. |
| D2 | **workspace-service is the source of truth for the entire floorplan** (tiles, walls, zones, spawn points), served as JSON. The SkyOffice client builds its Phaser tilemap at runtime from this — it does **not** bundle a static Tiled map. | Adds Phase 7 (Layout) + Phase 8 map-serving. Requires the SkyOffice client rewrite in [`INTEGRATION.md`](./INTEGRATION.md). |
| D3 | **SkyOffice's Colyseus server is forked** to call workspace-service: validate identity on join, load objects/desks from session-config, sync presence back. | Phase 9 here is the *backend* contract; the *Colyseus-side* code lives in [`INTEGRATION.md`](./INTEGRATION.md). |
| D4 | **Voice/video is owned by room-service**, not workspace-service. workspace-service only supplies spatial data (zones, proximity radius, object `roomId`s). | No WebRTC/SFU/PeerJS code here. Phase 10 defines only the spatial-context endpoints room-service consumes. |
| D5 | **All chat is persisted via chat-service.** workspace-service only supplies the workspace→channel mapping. | No message storage here. Phase 10 defines the channel-resolution contract chat-service / SkyOffice use. |
| D6 | **Identity bridge:** SkyOffice players are ephemeral Colyseus `sessionId`s; the backend keys on persistent `userId`. The fork maps `sessionId ↔ userId` at join time using a validated JWT. workspace-service exposes the desk-validation endpoint. | Phase 9. |
| D7 | **`/api/internal/*` is server-to-server only.** Blocked at the gateway for browsers; callers (Colyseus, chat-service, room-service) authenticate with a shared internal token (`X-Internal-Token`). | Phase 1 (filter) + Phase 11 (gateway). |

---

## Phase 1 — Foundation

> Service boots, connects to Postgres, handles errors, enforces auth.

- [ ] `pom.xml` — dependencies:
  - `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, **`postgresql`** driver
  - `spring-boot-starter-validation` — `@Valid` on request DTOs
  - `spring-boot-starter-actuator` — health/metrics (full Prometheus+Grafana tracked in #18)
  - `flyway-core` — versioned migrations
  - `mapstruct` + `mapstruct-processor`
  - `spring-boot-docker-compose` — auto-starts **Postgres** on dev boot (not MySQL)
  - `springdoc-openapi-starter-webmvc-ui`
  - `shared-library` (group `com.virtualoffice`) — reuse common DTOs/exceptions
- [ ] `compose.yml` — **PostgreSQL** service for local dev
- [ ] `application.yml` — datasource, JPA (`ddl-auto: validate` — Flyway owns schema), Flyway, Actuator, `server.port: 8087`
- [ ] `WorkspaceServiceApplication.java` — `@SpringBootApplication`
- [ ] `config/GlobalExceptionHandler.java` — adapt from chat-service; map `DataIntegrityViolationException` → 409, `OptimisticLockException` → 409, custom `ForbiddenException`/`NotFoundException`/`GoneException`
- [ ] `config/LoggingAspect.java` — copy from chat-service, repoint pointcuts to `com.virtualoffice.workspace`
- [ ] `config/OpenApiConfig.java` — Swagger UI at `/swagger-ui.html`
- [ ] `util/UserContext.java` — reads `X-User-Id` / `X-User-Role` (copy from chat-service)
- [ ] `config/InternalAuthFilter.java` — **NEW**: rejects `/api/internal/**` unless `X-Internal-Token` matches a configured secret (env `INTERNAL_API_TOKEN`). Backbone of D7.
- [ ] `controller/HealthController.java` — `GET /api/workspace/health` → `OK` (no auth)
- [ ] `db/migration/V1__init_schema.sql` — Flyway baseline (all **12 normalized tables** per [`DATABASE.md`](./DATABASE.md): workspace, tileset, map_layer, zone, spawn_point, team, desk, desk_link, desk_widget, map_object, workspace_invitation + indexes/constraints)

---

## Phase 2 — Data Layer (Enums → Entities → Repositories)

**Enums** (`model/enums/`)
- [ ] `WorkspaceStatus` — `ACTIVE`, `ARCHIVED`, `SUSPENDED`
- [ ] `WorkspaceRole` — `OWNER`, `ADMIN`, `MEMBER`, `GUEST` (define an explicit rank for `>=` comparisons)
- [ ] `DeskStatus` — `ACTIVE`, `AWAY`, `DO_NOT_DISTURB`, `FOCUS_MODE`, `CUSTOM`
- [ ] `InviteStatus` — `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`
- [ ] `MapObjectType` — `COMPUTER`, `WHITEBOARD`
- [ ] `AvatarCharacter` — `ADAM`, `ASH`, `LUCY`, `NANCY` (must match SkyOffice sprite keys; anim strings are `<char>_idle_down` etc. — see INTEGRATION §Avatar)
- [ ] `ZoneType` — `MEETING_ROOM`, `FOCUS_AREA`, `SOCIAL`, `TEAM`, `OPEN` (used by layout + room-service proximity)

**Entities** (`model/`) — fully normalized; see [`DATABASE.md`](./DATABASE.md) for columns/keys
- [ ] `Workspace` — name, slug (unique), ownerId, description, logoUrl, status, visibility, inviteToken, defaultTimezone, tileSize, mapWidth, mapHeight, `mapGeometry` (jsonb, §3.1), **`layoutVersion` (`@Version`)**, timestamps
- [ ] `Tileset` — workspaceId, name, imageUrl, firstGid, tileWidth, tileHeight, columns, tileCount · **UNIQUE(workspaceId, firstGid)**
- [ ] `MapLayer` — workspaceId, name, layerIndex, collides, `data` (jsonb tile matrix, §3.2) · **UNIQUE(workspaceId, layerIndex)**
- [ ] `Zone` — workspaceId, type (`ZoneType`), name, x, y, width, height, voiceRoomId (unique, null), proximityRadius (null), timestamps
- [ ] `SpawnPoint` — workspaceId, x, y, label, isDefault
- [ ] `Team` — workspaceId, name, description, timestamps · **UNIQUE(workspaceId, name)**
- [ ] `Desk` — userId, workspaceId, fullName, nickName, title, workEmail, phone, personalImageUrl, avatarCharacter, timezone, status, statusEmoji, statusCustomText, positionX, positionY, isOnline (cache), lastSeenAt, role, bio, teamId (FK, ON DELETE SET NULL), inviteStatus, invitedBy, isActive, joinedAt · **UNIQUE(userId, workspaceId)**
- [ ] `DeskLink` — deskId (FK CASCADE), url · **UNIQUE(deskId, url)**
- [ ] `DeskWidget` — deskId (FK CASCADE), type, label, position, `config` (jsonb, §3.3) · **UNIQUE(deskId, position)**
- [ ] `MapObject` — workspaceId, type, label, positionX, positionY, **roomId (unique, stable — drives whiteboard/computer rejoin)**, capacity, isActive, timestamps
- [ ] `WorkspaceInvitation` — workspaceId, invitedEmail, invitedBy, token (unique), role, status, expiresAt, createdAt · **partial UNIQUE(workspaceId, invitedEmail) WHERE status=PENDING**

**MapStruct Mappers** (`dto/mapper/`)
- [ ] `WorkspaceMapper`, `LayoutMapper` (assembles tilesets+layers+zones+spawns), `DeskMapper`, `MapObjectMapper`, `InvitationMapper`, `TeamMapper`

**Repositories** (`repository/`)
- [ ] `WorkspaceRepository` — `findBySlug`, `findByOwnerId`, `findBySlugAndStatusNot`
- [ ] `TilesetRepository` / `MapLayerRepository` / `ZoneRepository` / `SpawnPointRepository` — `findByWorkspaceId(OrderBy…)`
- [ ] `TeamRepository` — `findByWorkspaceId`
- [ ] `DeskRepository` — `findByWorkspaceIdAndUserId`, `findByWorkspaceIdAndIsActiveTrue` (directory), `existsByWorkspaceIdAndUserIdAndIsActiveTrue` (membership guard)
- [ ] `DeskLinkRepository` / `DeskWidgetRepository` — `findByDeskId`
- [ ] `MapObjectRepository` — `findByWorkspaceId`, `findByWorkspaceIdAndIsActiveTrue`
- [ ] `InvitationRepository` — `findByToken`, `findByWorkspaceIdAndInvitedEmail`

> **Authorization helper:** add a shared `WorkspaceAccessGuard` (service-layer) with
> `requireMember(workspaceId, userId)` and `requireRole(workspaceId, userId, minRole)` so
> every service reuses one consistent check instead of ad-hoc role logic.

---

## Phase 3 — Workspace CRUD

- [ ] `dto/request/CreateWorkspaceRequest.java` — name, slug, description, logo, defaultTimezone
- [ ] `dto/request/UpdateWorkspaceRequest.java` — name, description, logo, defaultTimezone
- [ ] `dto/response/WorkspaceResponse.java`
- [ ] `service/WorkspaceService.java` + `impl/WorkspaceServiceImpl.java`
  - `createWorkspace(request, ownerId)` — creates workspace **+ owner Desk (role OWNER, accepted)** + seeds a default `layoutMap` (empty floor) in one `@Transactional`
  - `getWorkspace(id, requesterId)` — member-only (`requireMember`)
  - `updateWorkspace(id, request, requesterId)` — `requireRole(ADMIN)`
  - `archiveWorkspace(id, requesterId)` — owner only; sets status `ARCHIVED` (soft)
  - `rotateInviteToken(id, requesterId)` — `requireRole(ADMIN)`; new UUID, old invalidated
- [ ] `controller/WorkspaceController.java`
  - `POST /api/workspace` · `GET /api/workspace/{id}` · `PUT /api/workspace/{id}` · `DELETE /api/workspace/{id}` · `POST /api/workspace/{id}/rotate-invite-token`
  - **`GET /api/workspace/mine`** — workspaces where the requester has an active desk (frontend needs a list)

---

## Phase 4 — Team CRUD

- [ ] `dto/request/CreateTeamRequest.java` (name, description) · `dto/response/TeamResponse.java`
- [ ] `service/TeamService.java` + `impl`
  - `createTeam` / `updateTeam` / `deleteTeam` — `requireRole(ADMIN)`; `getTeams` — any member
  - On delete, null out `teamId` on member desks (don't orphan FK)
- [ ] `controller/TeamController.java` — `POST|GET /api/workspace/{wid}/teams`, `PUT|DELETE /api/workspace/{wid}/teams/{teamId}`

---

## Phase 5 — Desk & Member Directory

- [ ] `dto/request/UpdateDeskRequest.java` — fullName, nickName, title, bio, avatarCharacter, timezone, links, teamId, deskCustomization
- [ ] `dto/request/UpdateStatusRequest.java` — status, statusEmoji, customText
- [ ] `dto/response/DeskResponse.java` — includes `role` (frontend stores it per Design §Frontend Role Storage)
- [ ] `service/DeskService.java` + `impl`
  - `getMyDesk(wid, userId)` · `getDeskById` · `updateDesk` (own desk only) · `updateStatus` (own desk only)
  - `getMembers(wid, requesterId)` — active only, **paginated**; optional `teamId` filter
  - `removeMember(deskId, requesterId)` — `requireRole(ADMIN)`, soft-delete (`isActive=false`)
- [ ] `controller/DeskController.java`
  - `GET /api/workspace/{wid}/desks` (directory, paginated) · `GET …/desks/me` · `GET …/desks/{deskId}`
  - `PUT …/desks/{deskId}` · `PATCH …/desks/{deskId}/status` · `DELETE …/desks/{deskId}`

---

## Phase 6 — Invitation Flow

- [ ] `dto/request/InviteMemberRequest.java` (email, role) · `dto/response/InvitationResponse.java`
- [ ] `service/InvitationService.java` + `impl`
  - `invite(wid, request, invitedBy)` — `requireRole(ADMIN)`; creates `WorkspaceInvitation` + **PENDING Desk**; publishes an event to **notifications-service** (RabbitMQ) for the invite email
  - `acceptInvite(token)` — validate token + expiry; activate Desk (`ACCEPTED`, `joinedAt`, `isActive=true`); mark invitation `ACCEPTED`
  - `declineInvite(token)` — mark `DECLINED`
  - `getInvitations(wid, requesterId)` — `requireRole(ADMIN)`
  - `revokeInvitation(id, requesterId)` — `requireRole(ADMIN)`
  - Background job (or lazy check) to mark past-`expiresAt` invites `EXPIRED`
- [ ] `controller/InvitationController.java`
  - `POST|GET /api/workspace/{wid}/invitations` · `DELETE …/invitations/{id}`
  - `POST /api/invitations/accept?token=` · `POST /api/invitations/decline?token=`

---

## Phase 7 — Layout / Floorplan (D2: DB is source of truth)

> The full map is **DB-owned but normalized** (D8): static geometry is rows in
> `tileset` / `map_layer` / `zone` / `spawn_point`; **interactive** objects (computers/
> whiteboards) are `MapObject` rows (Phase 8). The layout endpoint **assembles** these into
> one client document and parses one back on write — JSON exists only at the API edge, not in storage.

- [ ] **Define & document the layout client schema** (`docs/layout-schema.md`): the assembled shape (tileSize, width/height, tilesets, tilelayers with `collides`, `spawnPoints[]`, `zones[]`). Convertible to a Phaser tilemap. This is the API contract, **not** the storage model (see [`DATABASE.md`](./DATABASE.md)).
- [ ] `dto/request/UpdateLayoutRequest.java` — full assembled layout + expected `layoutVersion`
- [ ] `dto/response/LayoutResponse.java` — assembled layout + `layoutVersion`
- [ ] `service/LayoutService.java` + `impl`
  - `getLayout(wid, requesterId)` — any member; joins the normalized tables → client document
  - `updateLayout(wid, request, requesterId)` — `requireRole(ADMIN)`; **one `@Transactional`** that diffs/replaces `tileset`/`map_layer`/`zone`/`spawn_point` rows; validates the payload; **bumps `layoutVersion`**; stale version → 409
  - Validate `zone.voiceRoomId` uniqueness and that `MapObject` positions fall within map bounds
- [ ] `controller/LayoutController.java`
  - `GET /api/workspace/{wid}/layout` · `PUT /api/workspace/{wid}/layout`

---

## Phase 8 — MapObject Management

- [ ] `dto/request/CreateMapObjectRequest.java` (type, label, positionX, positionY, capacity) · `UpdateMapObjectRequest.java` · `dto/response/MapObjectResponse.java`
- [ ] `service/MapObjectService.java` + `impl`
  - `createMapObject` — `requireRole(ADMIN)`; **auto-generates stable `roomId` (UUID)**; validate position within layout bounds
  - `getMapObjects(wid)` — active objects · `updateMapObject` · `toggleActive` · `deleteMapObject` (all admin)
- [ ] `controller/MapObjectController.java`
  - `POST|GET /api/workspace/{wid}/map-objects` · `PUT|PATCH(/toggle)|DELETE …/map-objects/{id}`

---

## Phase 9 — SkyOffice Session API (backend side of D3/D6)

> Endpoints the **forked Colyseus server** calls. All under `/api/internal/**` (D7).
> The Colyseus-side code that consumes these is specified in [`INTEGRATION.md`](./INTEGRATION.md).

- [ ] `dto/response/SessionConfigResponse.java` — workspace metadata + **layoutMap** + `List<DeskResponse>` + `List<MapObjectResponse>` (everything Colyseus needs on room boot)
- [ ] `dto/response/MemberRoleResponse.java` — userId, workspaceId, role, isActive (404 if no active desk)
- [ ] `dto/response/JoinValidationResponse.java` — userId, workspaceId, deskId, fullName, avatarCharacter, spawn (positionX/Y), role, allowed (boolean)
- [ ] `dto/request/PresenceSyncRequest.java` — userId, isOnline, status?, statusEmoji?, positionX?, positionY?
- [ ] `dto/request/PresenceBatchRequest.java` — `List<PresenceSyncRequest>` (Colyseus flushes many at once; cheaper than per-event calls)
- [ ] `service/SessionService.java` + `impl`
  - `getSessionConfig(wid)` — assembled boot payload
  - `validateJoin(wid, userId)` — confirms an active Desk; returns desk identity for `sessionId→userId` mapping; **403/404 if not a member**
  - `syncPresence(wid, request)` / `syncPresenceBatch(wid, batch)` — update `isOnline`, `lastSeenAt`, optionally status + position. **Position is persisted only on disconnect or throttled (≤1/30s per desk), never per movement frame.**
  - `getMemberRole(wid, userId)` — for chat-service/room-service authorization
- [ ] `controller/SessionController.java`
  - `GET  /api/internal/workspace/{wid}/session-config`
  - `GET  /api/internal/workspace/{wid}/join-validation/{userId}`
  - `POST /api/internal/workspace/{wid}/presence`  ·  `POST …/presence/batch`
  - `GET  /api/internal/workspace/{wid}/members/{userId}/role`

---

## Phase 10 — Cross-service spatial contracts (D4 voice, D5 chat)

> workspace-service does **not** implement voice or chat. It exposes the spatial/identity
> context the owning services need. Full flows in [`INTEGRATION.md`](./INTEGRATION.md).

- [ ] **room-service (voice):** `GET /api/internal/workspace/{wid}/zones` — zones with `type`, bounds, `voiceRoomId`, `proximityRadius`. room-service decides who shares a voice room from avatar positions; workspace-service only supplies geometry. (Reuses `getMemberRole` for join authz.)
- [ ] **chat-service:** `GET /api/internal/workspace/{wid}/chat-context` — returns the canonical workspace chat channel id/key so chat-service/SkyOffice persist room chat to the right channel. workspace-service stores **no messages** (D5). Confirm chat-service already enforces role via `getMemberRole` (it does, per Design §chat-service).
- [ ] Document in `INTEGRATION.md` that **dialog bubbles + global chat both forward to chat-service** (D5), keyed by `(workspaceId, userId)` resolved from the Colyseus `sessionId` map.

---

## Phase 11 — Gateway & wiring

- [ ] gateway-api route: `/api/workspace/**` → workspace-service:8087
- [ ] gateway-api: **block `/api/internal/**` from external clients** (D7) — return 404/403 at the edge
- [ ] Confirm gateway injects `X-User-Id` / `X-User-Role` after JWT validation (workspace-service trusts them, like chat-service)
- [ ] Set `INTERNAL_API_TOKEN` as a shared secret across Colyseus / chat-service / room-service / workspace-service

---

## Phase 12 — Tests

### Service/unit (Mockito)
- [ ] `WorkspaceServiceImplTest`, `DeskServiceImplTest`, `TeamServiceImplTest`, `MapObjectServiceImplTest`, `InvitationServiceImplTest`, `LayoutServiceImplTest`, `SessionServiceImplTest`, `WorkspaceAccessGuardTest`, `AllTestsSuite`

### End-to-end (`@SpringBootTest` + `TestRestTemplate` + **Testcontainers PostgreSQL**, D1)

**Workspace** — create→201 (+owner desk); duplicate slug→409; get as member→200 / non-member→403; update admin→200 / non-admin→403; archive owner→200 (ARCHIVED); rotate-invite-token (old invalid); `GET /mine`→200 list

**Team** — create admin→201 / non-admin→403; list→200; update→200; delete→200 (members' teamId nulled)

**Desk** — `desks/me`→200; update own→200 / another's→403; status patch→200; directory excludes inactive + paginates; remove member admin→200 (isActive=false)

**Invitation** — invite→201 (PENDING desk) + email event published; duplicate email→409; accept valid→200 (desk active); accept expired→410; accept invalid→404; decline→200; revoke admin→200

**Layout** — get as member→200; update admin→200 (version bumped); update non-admin→403; **stale version→409**; invalid JSON→400

**MapObject** — create admin→201 (roomId set) / non-admin→403; list excludes inactive; toggle flips; delete→200

**Session (internal)** — session-config→200 (workspace + layout + desks + objects); join-validation member→200 / non-member→403; presence isOnline→updated; presence batch→all updated; **position not persisted on high-frequency call** (throttle assert); member role→200 / no-desk→404; **`/api/internal/**` without `X-Internal-Token`→403**

**Health** — `GET /api/workspace/health`→200 `OK` (no auth)

---

## Phase 13 — Seed & docs

- [ ] Flyway `R__seed_demo.sql` (or a dev profile) — one demo workspace with layout + computers/whiteboards/zones so SkyOffice has something to render on day one
- [ ] Update `workspace-service/README.md` — **fix MySQL→PostgreSQL**, document `INTERNAL_API_TOKEN`, link `INTEGRATION.md`
- [ ] Keep `docs/specs/workspace-service.json` (OpenAPI) in sync — add the new layout + internal endpoints

---

## Cross-repo work (tracked in INTEGRATION.md, not buildable here)

- [ ] SkyOffice Colyseus fork — `onAuth`/`onJoin`/`onLeave`/`onCreate`, sessionId↔userId map, load from session-config, presence sync, forward chat to chat-service
- [ ] SkyOffice client — build Phaser tilemap from `GET /api/workspace/{wid}/layout` (D2), pass JWT+workspaceId on join, drive spawn/avatar from desk
- [ ] room-service — consume `/zones` + positions for proximity voice (D4)
- [ ] chat-service — already exposes channels; confirm workspace channel resolution (D5)
</content>
</invoke>
