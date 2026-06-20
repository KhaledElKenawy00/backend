# Room Service — Frontend Integration Guide

## How to Run

```bash
# Prerequisites: JDK 21+, Docker. Room Service brings up NO infra of its own.
# Start order (Room Service depends on all three being up):
#   1. user-service  -> brings up the shared RabbitMQ broker
#   2. chat-service  -> brings up the shared MongoDB + Redis (and consumes room events)
cd backend/chat-service && ./mvnw spring-boot:run

#   3. room-service
cd backend/room-service && ./mvnw spring-boot:run     # Windows: mvnw.cmd spring-boot:run
```

The service starts on **http://localhost:8086**.
Health check: `GET /api/rooms/health` → plain text `OK`.

---

## Authentication

Room Service does NOT handle JWT. **Nginx/gateway** validates the JWT and forwards:

| Header | Description |
|---|---|
| `X-User-Id` | integer user ID (e.g. `42`) |
| `X-User-Role` | `ADMIN` or `USER` (case-insensitive) |

Every REST call (except `/api/rooms/health`) requires these. Missing `X-User-Id` → **401**; non-numeric `X-User-Id` or missing/invalid `X-User-Role` → **400**.

**Rooms are private:** every room-scoped call requires the caller to be in `room.members`, otherwise **403** (or the room is omitted from list results).

---

## REST API

Base path: `/api/rooms`. All IDs are MongoDB ObjectId hex strings (24 chars). Pages are **1-based**.

### Create a room
```
POST /api/rooms
{ "name": "Standup", "workspaceId": 100, "members": [1, 2], "maxParticipants": 25 }
```
`name` and `workspaceId` are required; `members` (creator auto-added, deduped) and `maxParticipants` (default 25) optional.

**Response** `201`:
```json
{
  "id": "64f1a2b3...",
  "workspaceId": 100,
  "name": "Standup",
  "channelId": "64f1a2c0...",
  "agoraChannelName": "room-64f1a2b3...",
  "members": [1, 2],
  "maxParticipants": 25,
  "createdBy": 1,
  "createdAt": "2026-06-20T14:00:00Z",
  "updatedAt": "2026-06-20T14:00:00Z"
}
```
- `channelId` — the bound **Chat Service** text channel (type `ROOM`); use it with the chat endpoints for the room's message history.
- `agoraChannelName` — the Agora channel every client joins (see *Agora* below).
- `409` if a room with the same `(workspaceId, name)` already exists. `503` if the message broker is down (the room is **not** created).

### List my rooms in a workspace
```
GET /api/rooms?workspaceId=100&page=1&limit=20
```
Returns only rooms the caller is a member of (paginated, most recently updated first): `{ content: Room[], totalPages, totalElements, currentPage }`.

### Get / update / delete
```
GET    /api/rooms/{id}                 -> Room (member-gated; 403/404)
PATCH  /api/rooms/{id}  { name?, maxParticipants? }   -> Room  (broadcasts ROOM_UPDATED)
DELETE /api/rooms/{id}                 -> 200  (deletes bound chat channel, clears presence, broadcasts ROOM_CLOSED)
```

### Membership
```
POST   /api/rooms/{id}/members  { "userId": 3 }   -> 200   (also added to the bound chat channel)
DELETE /api/rooms/{id}/members/{userId}           -> 200   (also removed from the bound chat channel)
```

### Join / leave / participants (presence)
```
POST /api/rooms/{id}/join          -> 200  JoinRoomResponse
POST /api/rooms/{id}/leave         -> 200
GET  /api/rooms/{id}/participants  -> ParticipantResponse[]
```
`join` response:
```json
{
  "room": { /* Room */ },
  "agoraChannelName": "room-64f1a2b3...",
  "participants": [ { "userId": 1, "muted": false, "cameraOn": false, "screenSharing": false, "joinedAt": "..." } ]
}
```
- Join is **idempotent** (re-joining doesn't duplicate you or re-broadcast).
- `409` if the room is at `maxParticipants`.
- After joining, connect to Agora with `agoraChannelName` and open the WebSocket for presence.

### WebSocket ticket
```
POST /api/rooms/ws-ticket  -> { "ticket": "..." }
```
Single-use, 60s TTL.

---

## WebSocket (STOMP) — presence & signalling

1. `POST /api/rooms/ws-ticket` → `{ ticket }`
2. Connect: `ws://localhost:8086/api/rooms/connect?ticket={ticket}` (invalid/expired ticket → handshake rejected)

### Subscribe
| Topic | Events |
|---|---|
| `/topic/room/{roomId}` | `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT`, `STATE_CHANGED`, `ROOM_UPDATED`, `ROOM_CLOSED` (requires room membership) |
| `/user/queue/errors` | per-session error envelopes |

Subscription is authorized — non-members get an `ERROR` envelope and the subscribe is dropped. Clients may only `SEND` to `/app/**`.

### Send
```
/app/room/state      { "roomId": "...", "muted": true, "cameraOn": false, "screenSharing": false }
/app/room/heartbeat  { "roomId": "..." }    # every ~15s to keep your presence alive
```

### Event envelope
```json
{ "action": "PARTICIPANT_JOINED", "payload": { ... } }
```

| Action | Payload |
|---|---|
| `PARTICIPANT_JOINED` | `ParticipantResponse` |
| `PARTICIPANT_LEFT` | `{ "userId": 2 }` |
| `STATE_CHANGED` | `ParticipantResponse` |
| `ROOM_UPDATED` | `Room` |
| `ROOM_CLOSED` | `{ "roomId": "..." }` |
| `ERROR` | `{ "code": "...", "message": "..." }` |

Error codes: `NOT_A_MEMBER`, `INVALID_PAYLOAD`, `INTERNAL_ERROR`.

---

## Agora (voice / video)

The backend does **not** mint Agora tokens in v1. It assigns each room an `agoraChannelName` (`room-{roomId}`) and returns it on `join` (and on the room object). The frontend initialises the Agora SDK with its **own App ID** and joins that channel name (App-ID-only mode).

> ⚠️ App-ID-only is insecure for production (anyone with the App ID + channel name can join). Server-side token minting is the planned v2 hardening.

There is no Agora "link" — joining is by the shared **channel name** string.

---

## Room text chat (via Chat Service)

Each room's text chat is a Chat Service channel (type `ROOM`), provisioned automatically. Use the room's `channelId` with the normal Chat Service endpoints:
- `GET /api/chat/rooms?workspaceId={id}` → the ROOM channels you're in (their message history)
- messages / threads / read-receipts and the `/topic/channel/{channelId}` WebSocket topic work exactly as for a group channel.

Room metadata (name, participants) comes from Room Service; match by `channelId`.

---

## Error Responses (REST)

```json
{ "status": 403, "error": "Forbidden", "message": "not a member of this room", "timestamp": "..." }
```

| Status | When |
|---|---|
| `400` | validation failure, malformed id, or bad `X-User-Id`/`X-User-Role` |
| `401` | missing `X-User-Id` |
| `403` | not a member of the room |
| `404` | room not found |
| `409` | duplicate room name in workspace, or room is full (join) |
| `503` | MongoDB / Redis / RabbitMQ unavailable |

---

## Data Types Quick Reference

- **Room**: `id, workspaceId, name, channelId, agoraChannelName, members[], maxParticipants, createdBy, createdAt, updatedAt`
- **ParticipantResponse**: `userId, muted, cameraOn, screenSharing, joinedAt`
- IDs are ObjectId hex strings; user IDs are integers; timestamps are ISO-8601 UTC; pages are 1-based.
