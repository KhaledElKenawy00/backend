# workspace-service — Implementation Tasks

## Phase 1 — Foundation

> Service boots, connects to DB, handles errors.

- [ ] Update `pom.xml` — dependencies added:
  - `spring-boot-starter-data-jpa` + `postgresql` driver
  - `spring-boot-starter-validation` — `@Valid` on request DTOs
  - `spring-boot-starter-actuator` — dependency added; full setup tracked in issue #18 (Prometheus + Grafana dashboard — skipped for now)
  - `flyway-core` — versioned DB migrations (PostgreSQL uses only flyway-core, no extra module needed)
  - `mapstruct` + `mapstruct-processor` — auto-generated DTO ↔ entity mappers
  - `spring-boot-docker-compose` — auto-starts MySQL locally on dev boot
  - `springdoc-openapi-starter-webmvc-ui` — Swagger UI
- [ ] `compose.yml` — PostgreSQL service definition for local dev
- [ ] `application.yml` — datasource, JPA, Flyway, Actuator, server port config
- [ ] `WorkspaceServiceApplication.java` — main class with `@SpringBootApplication`
- [ ] `config/GlobalExceptionHandler.java` — adapt from chat-service (swap Mongo/Redis handlers for JPA `DataIntegrityViolationException`)
- [ ] `config/LoggingAspect.java` — copy from chat-service, update package pointcuts to `com.virtualoffice.workspace`
- [ ] `config/OpenApiConfig.java` — Swagger UI title, version, description; available at `/swagger-ui.html`
- [ ] `dto/mapper/WorkspaceMapper.java` — MapStruct interface, replaces hand-written DtoMapper
- [ ] `util/UserContext.java` — copy from chat-service (reads `X-User-Id` / `X-User-Role` headers)
- [ ] `controller/HealthController.java` — `GET /api/workspace/health`
- [ ] `src/main/resources/db/migration/V1__init_schema.sql` — Flyway baseline migration (all tables)

---

## Phase 2 — Data Layer (Enums → Entities → Repositories)

> Define the schema. Everything else builds on this.

**Enums**
- [ ] `model/enums/WorkspaceStatus.java` — `ACTIVE`, `ARCHIVED`, `SUSPENDED`
- [ ] `model/enums/WorkspaceRole.java` — `OWNER`, `ADMIN`, `MEMBER`, `GUEST`
- [ ] `model/enums/DeskStatus.java` — `ACTIVE`, `AWAY`, `DO_NOT_DISTURB`, `FOCUS_MODE`, `CUSTOM`
- [ ] `model/enums/InviteStatus.java` — `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`
- [ ] `model/enums/MapObjectType.java` — `COMPUTER`, `WHITEBOARD`
- [ ] `model/enums/AvatarCharacter.java` — `ADAM`, `ASH`, `LUCY`, `NANCY`

**Entities**
- [ ] `model/Workspace.java` — name, slug, ownerId, description, logo, layoutMap (JSON column), status, inviteToken, defaultTimezone
- [ ] `model/Team.java` — workspaceId, name, description
- [ ] `model/Desk.java` — userId, workspaceId, fullName, nickName, title, workEmail, phone, personalImage, avatarCharacter, timezone, status, statusEmoji, positionX, positionY, isOnline, lastSeenAt, role, deskCustomization, bio, teamId, inviteStatus, invitedBy, isActive, joinedAt
- [ ] `model/DeskLink.java` — deskId, url (child table for `Desk.links`)
- [ ] `model/MapObject.java` — workspaceId, type, label, positionX, positionY, roomId, capacity, isActive
- [ ] `model/WorkspaceInvitation.java` — workspaceId, invitedEmail, invitedBy, token, role, status, expiresAt

**MapStruct Mappers**
- [ ] `dto/mapper/WorkspaceMapper.java` — Workspace ↔ WorkspaceResponse
- [ ] `dto/mapper/DeskMapper.java` — Desk ↔ DeskResponse
- [ ] `dto/mapper/MapObjectMapper.java` — MapObject ↔ MapObjectResponse
- [ ] `dto/mapper/InvitationMapper.java` — WorkspaceInvitation ↔ InvitationResponse

**Repositories**
- [ ] `repository/WorkspaceRepository.java` — find by slug, find by ownerId
- [ ] `repository/TeamRepository.java` — find by workspaceId
- [ ] `repository/DeskRepository.java` — find by workspaceId, find by userId+workspaceId, find active members
- [ ] `repository/DeskLinkRepository.java` — find by deskId
- [ ] `repository/MapObjectRepository.java` — find by workspaceId, find active by workspaceId
- [ ] `repository/InvitationRepository.java` — find by token, find by workspaceId+email

---

## Phase 3 — Workspace CRUD

- [ ] `dto/request/CreateWorkspaceRequest.java` — name, slug, description, logo, defaultTimezone
- [ ] `dto/request/UpdateWorkspaceRequest.java` — name, description, logo, defaultTimezone, layoutMap
- [ ] `dto/response/WorkspaceResponse.java`
- [ ] `service/WorkspaceService.java` — interface
- [ ] `service/impl/WorkspaceServiceImpl.java`
  - `createWorkspace(request, ownerId)` — creates workspace + owner Desk in one transaction
  - `getWorkspace(workspaceId, requesterId)` — member-only access
  - `updateWorkspace(workspaceId, request, requesterId)` — admin only
  - `archiveWorkspace(workspaceId, requesterId)` — owner only
  - `rotateInviteToken(workspaceId, requesterId)` — admin only
- [ ] `controller/WorkspaceController.java`
  - `POST   /api/workspace`
  - `GET    /api/workspace/{id}`
  - `PUT    /api/workspace/{id}`
  - `DELETE /api/workspace/{id}`
  - `POST   /api/workspace/{id}/rotate-invite-token`

---

## Phase 4 — Team CRUD

- [ ] `dto/request/CreateTeamRequest.java` — name, description
- [ ] `dto/response/TeamResponse.java`
- [ ] `service/TeamService.java` — interface
- [ ] `service/impl/TeamServiceImpl.java`
  - `createTeam(workspaceId, request, requesterId)` — admin only
  - `getTeams(workspaceId)` — all members
  - `updateTeam(teamId, request, requesterId)` — admin only
  - `deleteTeam(teamId, requesterId)` — admin only
- [ ] `controller/TeamController.java`
  - `POST   /api/workspace/{workspaceId}/teams`
  - `GET    /api/workspace/{workspaceId}/teams`
  - `PUT    /api/workspace/{workspaceId}/teams/{teamId}`
  - `DELETE /api/workspace/{workspaceId}/teams/{teamId}`

---

## Phase 5 — Desk & Member Directory

- [ ] `dto/request/UpdateDeskRequest.java` — fullName, nickName, title, bio, avatarCharacter, timezone, links, teamId, deskCustomization
- [ ] `dto/response/DeskResponse.java`
- [ ] `service/DeskService.java` — interface
- [ ] `service/impl/DeskServiceImpl.java`
  - `getMyDesk(workspaceId, userId)`
  - `getDeskById(deskId, requesterId)`
  - `updateDesk(deskId, request, requesterId)` — own desk only
  - `updateStatus(deskId, status, emoji, requesterId)` — own desk only
  - `getMembers(workspaceId, requesterId)` — member directory (active only)
  - `removeMember(deskId, requesterId)` — admin only, soft-delete
- [ ] `controller/DeskController.java`
  - `GET    /api/workspace/{workspaceId}/desks` — member directory
  - `GET    /api/workspace/{workspaceId}/desks/me`
  - `GET    /api/workspace/{workspaceId}/desks/{deskId}`
  - `PUT    /api/workspace/{workspaceId}/desks/{deskId}`
  - `PATCH  /api/workspace/{workspaceId}/desks/{deskId}/status`
  - `DELETE /api/workspace/{workspaceId}/desks/{deskId}` — remove member

---

## Phase 6 — Invitation Flow

- [ ] `dto/request/InviteMemberRequest.java` — email, role
- [ ] `dto/response/InvitationResponse.java`
- [ ] `service/InvitationService.java` — interface
- [ ] `service/impl/InvitationServiceImpl.java`
  - `invite(workspaceId, request, invitedBy)` — creates WorkspaceInvitation + PENDING Desk; triggers notification-service
  - `acceptInvite(token)` — validates token/expiry, activates Desk, marks invitation ACCEPTED
  - `declineInvite(token)` — marks invitation DECLINED
  - `getInvitations(workspaceId, requesterId)` — admin only
  - `revokeInvitation(invitationId, requesterId)` — admin only
- [ ] `controller/InvitationController.java`
  - `POST  /api/workspace/{workspaceId}/invitations`
  - `GET   /api/workspace/{workspaceId}/invitations`
  - `POST  /api/invitations/accept?token=`
  - `POST  /api/invitations/decline?token=`
  - `DELETE /api/workspace/{workspaceId}/invitations/{id}`

---

## Phase 7 — MapObject Management

- [ ] `dto/request/CreateMapObjectRequest.java` — type, label, positionX, positionY, capacity
- [ ] `dto/request/UpdateMapObjectRequest.java`
- [ ] `dto/response/MapObjectResponse.java`
- [ ] `service/MapObjectService.java` — interface
- [ ] `service/impl/MapObjectServiceImpl.java`
  - `createMapObject(workspaceId, request, requesterId)` — admin only; auto-generates roomId (UUID)
  - `getMapObjects(workspaceId)` — returns active objects
  - `updateMapObject(id, request, requesterId)` — admin only
  - `toggleActive(id, requesterId)` — admin only, soft-disable
  - `deleteMapObject(id, requesterId)` — admin only
- [ ] `controller/MapObjectController.java`
  - `POST   /api/workspace/{workspaceId}/map-objects`
  - `GET    /api/workspace/{workspaceId}/map-objects`
  - `PUT    /api/workspace/{workspaceId}/map-objects/{id}`
  - `PATCH  /api/workspace/{workspaceId}/map-objects/{id}/toggle`
  - `DELETE /api/workspace/{workspaceId}/map-objects/{id}`

---

## Phase 8 — SkyOffice Integration API

> These endpoints are called by the Colyseus server, not the browser client.

- [ ] `dto/response/SessionConfigResponse.java` — workspace metadata + list of DeskResponse + list of MapObjectResponse
- [ ] `dto/request/PresenceSyncRequest.java` — userId, isOnline, status, statusEmoji, positionX, positionY
- [ ] `service/SessionService.java` — interface
- [ ] `service/impl/SessionServiceImpl.java`
  - `getSessionConfig(workspaceId)` — returns everything Colyseus needs on room boot
  - `syncPresence(workspaceId, request)` — updates Desk.isOnline, lastSeenAt, status, position
- [ ] `dto/response/MemberRoleResponse.java` — userId, workspaceId, role, isActive
- [ ] `controller/SessionController.java`
  - `GET  /api/internal/workspace/{workspaceId}/session-config`
  - `POST /api/internal/workspace/{workspaceId}/presence`
  - `GET  /api/internal/workspace/{workspaceId}/members/{userId}/role`

> **Note:** `/api/internal/` routes must be blocked at the gateway for external clients — only server-to-server callers (Colyseus, chat-service, etc.) should reach them.

---

## Phase 9 — Tests

### Unit Tests (MockMvc — isolated, mocked dependencies)
- [ ] `WorkspaceServiceImplTest.java`
- [ ] `DeskServiceImplTest.java`
- [ ] `TeamServiceImplTest.java`
- [ ] `MapObjectServiceImplTest.java`
- [ ] `InvitationServiceImplTest.java`
- [ ] `SessionServiceImplTest.java`
- [ ] `AllTestsSuite.java`

---

### End-to-End REST API Tests

> `@SpringBootTest` + `TestRestTemplate` against a real embedded DB (H2 or Testcontainers MySQL).
> Each test boots the full Spring context, hits the actual HTTP endpoint, and asserts the response.

**Workspace**
- [ ] `POST /api/workspace` → 201, workspace + owner desk created
- [ ] `POST /api/workspace` duplicate slug → 409 Conflict
- [ ] `GET  /api/workspace/{id}` as member → 200
- [ ] `GET  /api/workspace/{id}` as non-member → 403 Forbidden
- [ ] `PUT  /api/workspace/{id}` as admin → 200 updated
- [ ] `PUT  /api/workspace/{id}` as non-admin → 403 Forbidden
- [ ] `DELETE /api/workspace/{id}` as owner → 200, status = ARCHIVED
- [ ] `POST /api/workspace/{id}/rotate-invite-token` → new token returned, old token invalid

**Team**
- [ ] `POST /api/workspace/{id}/teams` as admin → 201
- [ ] `POST /api/workspace/{id}/teams` as non-admin → 403
- [ ] `GET  /api/workspace/{id}/teams` → 200 list
- [ ] `PUT  /api/workspace/{id}/teams/{teamId}` → 200 updated
- [ ] `DELETE /api/workspace/{id}/teams/{teamId}` → 200

**Desk**
- [ ] `GET  /api/workspace/{id}/desks/me` → 200 own desk
- [ ] `PUT  /api/workspace/{id}/desks/{deskId}` own desk → 200 updated
- [ ] `PUT  /api/workspace/{id}/desks/{deskId}` another's desk → 403 Forbidden
- [ ] `PATCH /api/workspace/{id}/desks/{deskId}/status` → 200, status updated
- [ ] `GET  /api/workspace/{id}/desks` → 200, inactive members excluded
- [ ] `DELETE /api/workspace/{id}/desks/{deskId}` as admin → 200, isActive = false

**Invitation Flow**
- [ ] `POST /api/workspace/{id}/invitations` → 201, PENDING desk created
- [ ] `POST /api/workspace/{id}/invitations` duplicate email → 409 Conflict
- [ ] `POST /api/invitations/accept?token=<valid>` → 200, desk activated
- [ ] `POST /api/invitations/accept?token=<expired>` → 410 Gone
- [ ] `POST /api/invitations/accept?token=<invalid>` → 404 Not Found
- [ ] `POST /api/invitations/decline?token=<valid>` → 200, invitation DECLINED
- [ ] `DELETE /api/workspace/{id}/invitations/{invId}` as admin → 200 revoked

**MapObject**
- [ ] `POST /api/workspace/{id}/map-objects` as admin → 201, roomId auto-generated
- [ ] `POST /api/workspace/{id}/map-objects` as non-admin → 403
- [ ] `GET  /api/workspace/{id}/map-objects` → 200, inactive objects excluded
- [ ] `PATCH /api/workspace/{id}/map-objects/{objId}/toggle` → isActive flipped
- [ ] `DELETE /api/workspace/{id}/map-objects/{objId}` → 200

**SkyOffice Internal API**
- [ ] `GET  /api/internal/workspace/{id}/session-config` → 200 full payload (workspace + desks + map objects)
- [ ] `POST /api/internal/workspace/{id}/presence` isOnline=true → desk.isOnline updated
- [ ] `POST /api/internal/workspace/{id}/presence` status change → desk.status updated

**Health**
- [ ] `GET /api/workspace/health` → 200 "OK" (no auth required)

---

## Integration Checklist (SkyOffice ↔ workspace-service)

- [ ] On Colyseus room `onCreate` → call `GET /api/internal/workspace/{id}/session-config` and populate room state
- [ ] On player `onJoin` → call `POST /api/internal/workspace/{id}/presence` with `isOnline=true`
- [ ] On player `onLeave` → call `POST /api/internal/workspace/{id}/presence` with `isOnline=false`
- [ ] On status change in-session → call `POST /api/internal/workspace/{id}/presence` with updated status
- [ ] Validate workspace access on join: check Desk exists and `isActive=true` for this userId
- [ ] Room chat messages → forward to `chat-service` (not workspace-service)
