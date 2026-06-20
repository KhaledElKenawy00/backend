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

> **Rooms model:** one Colyseus room **per workspace**. Use the workspace's stable id as the
> Colyseus `roomId` (or maintain a `workspaceId → roomId` lookup). Replace the LOBBY/public/custom
> room-picker flow with "join my workspace".

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

workspace-service supplies geometry only; **room-service owns voice**.

- workspace-service: `GET /api/internal/workspace/{wid}/zones` → zones (`type`, bounds,
  `voiceRoomId`, `proximityRadius`).
- room-service decides voice membership two ways:
  - **Zone voice:** avatar inside a `MEETING_ROOM` zone → joins that zone's `voiceRoomId` (private; only occupants connect).
  - **Proximity voice:** in `OPEN` areas, avatars within `proximityRadius` form an ad-hoc group; volume falls off with distance.
- Avatar positions come from Colyseus live state. **Decision needed (room-service scope):**
  whether SkyOffice forwards positions to room-service directly, or room-service subscribes to
  Colyseus — pick when room-service is built. workspace-service is **not** in this path.
- Authorization for joining a voice room reuses `GET …/members/{userId}/role`.
- **Media transport:** the current PeerJS P2P can stay for an MVP with room-service acting as the
  signaling/coordination layer; an SFU (LiveKit/mediasoup) is the scale path. That choice is
  room-service's, not workspace-service's.

---

## 5. Chat → chat-service (D5: persist everything)

- chat-service already owns channels/DMs/threads + STOMP (port 8084, behind Nginx with
  `X-User-Id`/`X-User-Role`).
- workspace-service: `GET /api/internal/workspace/{wid}/chat-context` → the canonical workspace
  channel id/key. It stores **no messages**.
- **Both** the in-game `ADD_CHAT_MESSAGE` (proximity dialog bubbles) **and** the global chat window
  forward to chat-service, keyed by `(workspaceId, userId)` resolved from the `sessionId` map (D5).
  - Simplest path: the **client** sends persistent chat to chat-service over STOMP directly, while
    Colyseus keeps broadcasting bubbles live for low latency; a fork that forwards from the server is
    also valid. Persisted history is loaded from chat-service on room enter.
- chat-service enforces the per-action role table from `Design.md §chat-service` via
  `GET …/members/{userId}/role` — no role is duplicated in chat-service.

---

## 6. Avatar / animation mapping

`AvatarCharacter` enum keys (`ADAM`, `ASH`, `LUCY`, `NANCY`) must match SkyOffice sprite keys
(lowercase: `adam`, `ash`, `lucy`, `nancy`). Anim strings follow `<char>_<state>_<dir>`
(e.g. `adam_idle_down`, `lucy_run_left`). The backend stores only the character key; the client
composes anim strings. Adding a character = enum value + client sprite atlas, nothing else.

---

## 7. Build order

1. **Backend Phases 1–9** (workspace-service boots, session API live).
2. **SkyOffice server fork** (§1, §2) against a seeded demo workspace (TASKS Phase 13).
3. **SkyOffice client map rewrite** (§3).
4. **chat-service wiring** (§5) — smallest, chat-service already exists.
5. **room-service voice** (§4) — last; depends on room-service being built (currently a stub).

---

## 8. Open items to confirm as services mature

- JWT verification in Colyseus: shared secret vs. a user-service `/verify` call.
- room-service ↔ Colyseus position feed (push from SkyOffice vs. subscribe).
- Media transport: keep PeerJS P2P vs. introduce an SFU.
- Whether the admin layout editor ships for the graduation demo or is deferred.
</content>
