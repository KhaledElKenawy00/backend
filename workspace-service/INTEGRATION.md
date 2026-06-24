# SkyOffice ↔ Backend Integration

> Cross-repo contract for wiring **SkyOffice** (`/home/sersawy/Work/project/SkyOffice`,
> Colyseus + Phaser) to the Virtual Office backend. Covers the Colyseus-server fork,
> the client map rewrite, the room-service voice boundary, and chat-service persistence.
> Backend endpoints referenced here are defined in [`TASKS.md`](./TASKS.md) Phases 9–11.

---

## 0. The gap (why this is real work)

SkyOffice as-shipped is **standalone and stateless**. Verified in its source:

| Concern | SkyOffice today | Required target |
|---|---|---|
| Identity | ephemeral Colyseus `sessionId` only; player name typed in `LoginDialog` | persistent `userId` from a JWT, mapped to `sessionId` |
| Auth | optional bcrypt **room password** (`onAuth`) | JWT validated + active Desk checked against workspace-service |
| Map | `Tilemap` bundled in client assets; zones/items from Tiled object layers | built at runtime from `GET /api/workspace/{wid}/layout` (D2) |
| Computers/whiteboards | **hard-coded** `for` loops in `onCreate`; whiteboard `roomId` is `Math.random()` | loaded from `session-config`; `roomId` = persisted `MapObject.roomId` |
| Player spawn/avatar | hard-coded `x=705,y=500,anim='adam_idle_down'` | from the user's Desk (`positionX/Y`, `avatarCharacter`) |
| Presence | none | synced to workspace-service on join/leave/status |
| Chat | in-memory `ArraySchema`, never persisted | forwarded to chat-service (D5) |
| Voice/video | pure **PeerJS P2P**, client-side proximity, no server | coordinated by **room-service** (D4) |

> Conclusion: integration is **two-sided**. The backend exposes the contract; SkyOffice
> must be forked to consume it. Keep the fork in a branch of the SkyOffice repo.

---

## 0.1 One workspace ⇿ one SkyOffice (single configuration) — INVARIANT

**Each workspace has exactly one SkyOffice instance, and that instance is configured
entirely from the workspace.** This is a 1:1 relationship, not a room-picker:

| | |
|---|---|
| **Room** | One Colyseus room **per workspace**. The workspace's stable id *is* the room identity (use it as the Colyseus `roomId`, or keep a `workspaceId → roomId` lookup). There is no LOBBY/public/custom multi-room flow — a user joins **their workspace**, full stop. |
| **Configuration** | The room's entire configuration — floorplan (tiles, walls, zones, spawn points), interactive objects (computers/whiteboards), and member desks — comes from that one workspace's `GET …/session-config` (D3, Phase 9). workspace-service is the **single source of truth** for that configuration; the running room is the single source of truth for live session state. |
| **Uniqueness** | At most one live room per `workspaceId`. A second join for the same workspace **joins the existing room**, never spawns a parallel one. Reuse `joinById(workspaceId)` / a `workspaceId`-keyed `define`, not `create`. |
| **Lifecycle** | The room is created on first join and disposed when empty (or kept warm) — but its identity and configuration are always re-derivable from the workspace, so a disposed room rebuilds identically from `session-config`. Nothing about the room is authored in SkyOffice; everything is authored in workspace-service. |

> Practical consequence: there is no "create a room" UX and no per-room settings stored in
> SkyOffice. Editing the office = editing the workspace (layout/map-objects APIs, Phases 7–8);
> the next room boot reflects it. See §2 *Rooms model* for the exact server-fork wiring and §3
> for how the client builds the map from this single configuration.

---

## 1. Identity bridge (D6)

The single most important change. Colyseus keys on `sessionId`; the backend keys on `userId`.

**Join handshake (client → Colyseus):** the client passes its JWT and target `workspaceId`
in the join options:

```ts
// client/src/services/Network.ts
this.room = await this.client.joinById(workspaceRoomId, { token: jwt, workspaceId })
```

**`onAuth` (Colyseus, forked `SkyOffice.ts`):**
1. Verify the JWT (shared signing key with user-service, or call a user-service verify endpoint).
2. Call `GET /api/internal/workspace/{workspaceId}/join-validation/{userId}` with `X-Internal-Token`.
3. If `allowed=false` / 403 / 404 → `throw new ServerError(403, 'Not a member')`.
4. Return the validated identity (`{ userId, deskId, fullName, avatarCharacter, spawn, role }`)
   from `onAuth` so `onJoin` receives it as `auth`.

**Keep a `Map<sessionId, userId>`** on the room instance; every backend call (presence, chat,
voice) resolves `sessionId → userId` through it. Clear the entry in `onLeave`.

---

## 2. Colyseus server fork — exact changes (`server/rooms/SkyOffice.ts`)

| Hook | Change |
|---|---|
| `onCreate(options)` | `options` now carries `workspaceId`. Call `GET …/session-config`. **Delete the two hard-coded `for` loops.** Instead, for each `MapObject` from the payload, create a `Computer`/`Whiteboard` keyed by `MapObject.id`, setting `Whiteboard.roomId = mapObject.roomId` (persisted, not random). Store `workspaceId` + the layout for reference. |
| `onAuth(client, opts)` | Replace password check with the JWT + `join-validation` flow in §1. |
| `onJoin(client, auth)` | Create `Player` initialized from `auth`: `name=fullName`, `x=spawn.x`, `y=spawn.y`, `anim='<avatar>_idle_down'`. Record `sessionId→userId`. Fire `POST …/presence` `{userId, isOnline:true, positionX, positionY}`. |
| `onLeave(client)` | Look up `userId`; fire `POST …/presence` `{userId, isOnline:false, positionX, positionY}` (persist last position here). Remove the map entry. Existing computer/whiteboard cleanup stays. |
| `UPDATE_PLAYER` handler | Unchanged for live state, **but** do **not** call the backend per frame. Accumulate and flush positions via `POST …/presence/batch` on a timer (e.g. every 30s) and on leave (throttle per TASKS Phase 9). |
| status-change handler (new) | When a user changes status in-session, `POST …/presence` `{userId, status, statusEmoji}`. |
| `schema/OfficeState.ts` | Add `@type('string') id` to `Computer`/`Whiteboard` so the client can match a tile-object to its backend `MapObject`. Drop the random `getRoomId()` for whiteboards — `roomId` now comes from the backend. |

> **Rooms model:** one Colyseus room **per workspace**, configured from `session-config` — the
> single-configuration invariant of §0.1. In `server/index.ts`, drop the `LOBBY`/`PUBLIC`/`CUSTOM`
> `gameServer.define` set and the room-picker flow; define a single workspace room keyed by
> `workspaceId` so a repeat join lands in the existing room instead of creating a parallel one.

---

## 3. Client map rewrite (D2 — DB is source of truth)

SkyOffice's `Game.ts` currently does `this.make.tilemap({ key: 'tilemap' })` from a bundled asset.
Rewrite to build the map from the API:

1. On entering a workspace, `GET /api/workspace/{wid}/layout` → `{ layoutMap, layoutVersion }`.
2. Construct a Phaser tilemap from `layoutMap` (tileSize, tilesets, tilelayers). Set wall-layer
   collisions from `collides:true` layers.
3. Place chairs/computers/whiteboards from the `session-config` `MapObject` list (positions +
   ids), not from Tiled object layers.
4. Spawn `MyPlayer` at the desk spawn point with the desk's `avatarCharacter`.
5. Build voice/meeting zones from `layoutMap.zones` (used by room-service, §4).

**Admin layout editor** (enabled by D2): an authenticated ADMIN can edit the floorplan and
`PUT /api/workspace/{wid}/layout` with the expected `layoutVersion`. Stale version → 409 →
client re-fetches and retries. (Editor UI itself is frontend scope; the API is Phase 7.)

> See `docs/layout-schema.md` (Phase 7) for the exact JSON shape the client must parse.

---

## 4. Voice/video → room-service (D4)

> **Reality check (2026-06):** room-service is **built**, not a stub. It is a Discord-style
> **explicit voice-room** coordinator on port 8086: MongoDB `rooms`, Redis presence, STOMP
> signaling (`/ws/rooms`), Agora media (App-ID-only, channel name = `room-{id}`), and it
> already publishes `room.channel.event` to provision a bound chat channel. It does **not** yet
> validate `workspaceId`, read `/zones`, or do any proximity logic. The work below adds the
> spatial/identity integration and the proximity layer on top of that existing service.

workspace-service supplies **geometry + identity** only; **room-service owns voice**. Two voice
modes coexist: explicit rooms (already shipped) and proximity/zone voice (added here).

**4.1 Membership & role authorization (room-service → workspace-service).**
room-service gains an internal `WorkspaceClient` (shared `X-Internal-Token`) and calls it before
mutating voice state:
- `GET …/members/{userId}/role` — on room create/join, confirm the caller has an **active desk**
  in `room.workspaceId`. No active desk (404) → `403`. Reuses the same endpoint chat-service uses.
- `GET …/zones` — fetched once per workspace and cached (short TTL); drives the proximity/zone
  grouping below. workspace-service is the source of truth for zone bounds, `voiceRoomId`,
  and `proximityRadius`.

**4.2 Position feed (DECISION — push from SkyOffice).** Avatar positions live in Colyseus.
room-service does **not** subscribe to Colyseus schema (that would couple it to SkyOffice
internals). Instead the **forked Colyseus server pushes** position batches for the workspace to
room-service over a server-to-server channel (`X-Internal-Token`), reusing the same throttle as
the workspace `presence/batch` flush. room-service keeps an in-memory `workspaceId → {userId → (x,y)}`
index; positions are ephemeral and never persisted. workspace-service is **not** in this path.

**4.3 Proximity & zone grouping (room-service).** On each position batch, per workspace:
- **Zone voice:** an avatar inside a zone that has a `voiceRoomId` → assigned to that zone's voice
  channel (`agoraChannelName = voiceRoomId`); private, only occupants connect.
- **Proximity voice:** in `OPEN` areas, avatars within `proximityRadius` (from the zone, falling
  back to a workspace default) are clustered into connected-component groups; each stable group
  gets a deterministic Agora channel name. Volume falloff with distance is **client-side** (the
  client receives peer distances; room-service only decides channel membership).
- On any group change, room-service broadcasts a `VOICE_GROUP_CHANGED` signal to the affected
  clients over its existing `/topic/room/...` STOMP layer telling them which Agora channel to
  join/leave. This reuses the presence/signaling machinery already in room-service.

**4.4 Media transport.** Agora (App-ID-only in v1) stays — SkyOffice's PeerJS P2P is replaced by
Agora channels that room-service assigns. Server-side Agora token minting is room-service's v2
concern, not workspace-service's.

---

## 5. Chat → chat-service (D5: persist everything)

> **Reality check (2026-06):** chat-service is **built** on port 8084 (Mongo channels/DMs/threads,
> STOMP, ticket auth, read receipts) and **already consumes** `room.channel.event` from
> `room.exchange` to auto-create `ROOM`-type channels. Two gaps closed here: (a) nothing
> provisions a **canonical workspace channel**, and (b) chat-service does **not** yet enforce
> *workspace-scoped* roles (it trusts the account-level `X-User-Role` header).

**5.1 Canonical workspace channel — event-driven (DECISION).** workspace-service gains a RabbitMQ
publisher and emits a `workspace.channel.event` (mirroring the existing `room.channel.event`
contract) on membership changes:

| Trigger (workspace-service) | Event | Effect in chat-service |
|---|---|---|
| Workspace created (owner desk) | `WORKSPACE_CHANNEL_CREATE` | create the canonical `GROUP` workspace channel, owner as first member |
| Invitation accepted (desk activated) | `WORKSPACE_CHANNEL_ADD_MEMBER` | add userId to the workspace channel |
| Member removed (desk deactivated) | `WORKSPACE_CHANNEL_REMOVE_MEMBER` | remove userId from the workspace channel |

chat-service consumes these in a `WorkspaceChannelListener` (sibling of `RoomChannelListener`),
idempotently. `GET /api/internal/workspace/{wid}/chat-context` returns the canonical channel
key so the client/SkyOffice can resolve "the workspace channel" without guessing. workspace-service
still stores **no messages**.

**5.2 Role enforcement (chat-service → workspace-service).** chat-service gains the same internal
`WorkspaceClient` and enforces the per-action role table from `Design.md §chat-service` via
`GET …/members/{userId}/role` for workspace-scoped actions (create/archive channel, manage
members). DMs and account-level checks keep using `X-User-Role`. No role is duplicated in
chat-service.

**5.3 In-game + global chat.** Both the in-game `ADD_CHAT_MESSAGE` (proximity dialog bubbles) and
the global chat window forward to chat-service, keyed by `(workspaceId, userId)` resolved from the
Colyseus `sessionId` map. Simplest path: the **client** sends persistent chat to chat-service over
STOMP directly while Colyseus keeps broadcasting bubbles live for low latency. History loads from
chat-service on room enter.

> **Shared internal token:** chat-service and room-service must be configured with the same
> `INTERNAL_API_TOKEN` workspace-service validates (D7), sent as `X-Internal-Token` on every
> `WorkspaceClient` call.

---

## 6. Avatar / animation mapping

`AvatarCharacter` enum keys (`ADAM`, `ASH`, `LUCY`, `NANCY`) must match SkyOffice sprite keys
(lowercase: `adam`, `ash`, `lucy`, `nancy`). Anim strings follow `<char>_<state>_<dir>`
(e.g. `adam_idle_down`, `lucy_run_left`). The backend stores only the character key; the client
composes anim strings. Adding a character = enum value + client sprite atlas, nothing else.

---

## 7. Build order

1. **Backend Phases 1–9** (workspace-service boots, session API live). **Done.**
2. **Backend cross-service wiring** (§4–§5) — independent of SkyOffice, shippable now:
   1. workspace-service: publish `workspace.channel.event` on membership change (§5.1).
   2. chat-service: consume it → provision the canonical workspace channel (§5.1).
   3. chat-service: enforce workspace roles via `WorkspaceClient` (§5.2).
   4. room-service: validate membership + cache `/zones` via `WorkspaceClient` (§4.1).
   5. room-service: proximity/zone grouping + Agora channel assignment (§4.3).
3. **SkyOffice server fork** (§1, §2) against a seeded demo workspace (TASKS Phase 13). **Done**
   (SkyOffice branch `feature/backend-integration`): onAuth JWT+join-validation, onJoin/onLeave
   presence, session-config boot, batched position push (§4.2).
4. **API gateway** (Phase 11 / D7) — JWT edge validation, `X-User-Id`/`X-User-Role` injection,
   `/api/internal/**` blocked, routes to all services. **Done.** user-service now carries `userId`
   as a JWT claim.
5. **SkyOffice client map rewrite** (§3) — build the Phaser tilemap from `GET …/layout`, place
   objects from `/map-objects`, spawn from `/desks/me`, auto-join from the URL token. **Done**
   (SkyOffice branch `feature/backend-integration`); seeded via `R__seed_demo.sql` (Phase 13).
   Remaining client polish: consuming `VOICE_GROUP_CHANGED` → Agora (§4), forwarding chat to
   chat-service over STOMP (§5).

> Each backend step above is **one focused commit, one service** — a cross-service contract change
> (e.g. a new event field) lands in its own commit per side.

---

## 8. Open items to confirm as services mature

- ~~JWT verification in Colyseus: shared secret vs. a user-service `/verify` call.~~ **Resolved:**
  shared secret. user-service now mints a `userId` JWT claim; the Colyseus fork verifies HS256
  locally (Node `crypto`) and reads `userId`, and the gateway injects `X-User-Id` from the same
  claim for HTTP. No `/verify` endpoint added.
- ~~room-service ↔ Colyseus position feed~~ **Resolved (§4.2):** SkyOffice pushes batches to
  room-service over `X-Internal-Token`; room-service does not subscribe to Colyseus.
- Server-side Agora token minting (room-service v2) vs. App-ID-only (v1).
- Whether the admin layout editor ships for the graduation demo or is deferred.
</content>
