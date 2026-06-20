# Chat Service — Frontend Integration Guide

## Prerequisites

- **Java 21+** (JDK)
- **Docker** (for MongoDB + Redis)

## How to Run

> **Prerequisite — start `user-service` first.** It brings up the shared **RabbitMQ** broker that chat-service's room-channel consumer connects to (see arc §19). Chat-service still boots without RabbitMQ (the consumer connects lazily and retries), but you'll see connection-retry warnings in the log and the Room integration won't work until RabbitMQ is up.

```bash
# 1. clone the repo and cd into the service
cd backend/chat-service

# 2. set JAVA_HOME (point to your JDK 21+ installation)
# Linux / Mac
export JAVA_HOME=/path/to/your/jdk

# Windows (PowerShell)
$env:JAVA_HOME = "C:\Users\Two Star\.jdks\openjdk-24.0.2"

# 3. start user-service FIRST — it brings up the shared RabbitMQ broker
cd ../user-service && ./mvnw spring-boot:run   # then return here

# 4. start mongo + redis (docker-compose.yml is included)
docker compose up -d

# 5. run the service
# Linux / Mac
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

The service starts on **http://localhost:8084**.
Health check: `GET /api/chat/health` → plain text body `OK` (Content-Type: `text/plain`).

---

## Authentication

This service does NOT handle JWT itself. It expects **Nginx** to validate the JWT and forward these headers:

| Header | Description |
|---|---|
| `X-User-Id` | integer user ID (e.g. `42`) |
| `X-User-Role` | `ADMIN` or `USER` (case-insensitive) |

Every REST request must include these headers. For local testing without Nginx, just set them manually.

If `X-User-Id` is missing or blank the request fails with **401**. If `X-User-Id` is non-numeric or `X-User-Role` is missing/invalid the request fails with **400**.

---

## REST API

Base path: `/api/chat`

### Channels

#### Create a group channel
```
POST /api/chat/channels
```
**Request body:**
```json
{
  "name": "general",
  "workspaceId": 100,
  "members": [1, 2, 3]
}
```
All three fields are required. `members` must contain at least one user ID; the requesting user (creator) is auto-added on top of whatever is sent — pass `[creatorId]` if you want a single-member channel.

**Response** `201`:
```json
{
  "id": "64f1a2b3...",
  "name": "general",
  "type": "GROUP",
  "workspaceId": 100,
  "members": [1, 2, 3],
  "createdBy": 1,
  "createdAt": "2026-04-10T14:00:00Z",
  "updatedAt": "2026-04-10T14:00:00Z"
}
```

`409 Conflict` if another channel with the same `(workspaceId, name)` already exists.

#### List workspace channels
```
GET /api/chat/channels?workspaceId=100&page=1&limit=20
```
Returns only **`GROUP`** channels the requesting user is a member of. `DIRECT` (use `GET /api/chat/dm`) and `ROOM` (use `GET /api/chat/rooms`) channels are **excluded**.

**Response** `200`:
```json
{
  "content": [ /* ChannelResponse[] */ ],
  "totalPages": 1,
  "totalElements": 3,
  "currentPage": 1
}
```

#### List room channels
```
GET /api/chat/rooms?workspaceId=100&page=1&limit=20
```
Returns the **`ROOM`**-type channels (the text side of voice/video rooms) the requesting user is a member of, in the given workspace, paginated (newest activity first). This is how the frontend pulls the **message history** for the rooms a user belongs to. Each item is a `ChannelResponse` with `type: "ROOM"` and `name: null` — resolve the display name from the matching room in room-service. ROOM channels can only be **listed/read** here; they are created/deleted only by room-service (via RabbitMQ).

**Response** `200`: same `PaginatedResponse<ChannelResponse>` shape as above.

#### Get a channel
```
GET /api/chat/channels/{id}
```
Membership is required. Returns `403` for non-members and `404` if the channel does not exist.

#### Join a channel
```
POST /api/chat/channels/{id}/join
```
No body. Only works for `GROUP` channels (DMs return `400`). Idempotent. **Response:** `200 OK`, empty body.

#### Leave a channel
```
POST /api/chat/channels/{id}/leave
```
No body. Only `GROUP` channels. Returns `400` if not a member or if the channel is a DM. **Response:** `200 OK`, empty body.

When the last member leaves, the channel document is deleted server-side so it stops occupying the `(workspaceId, name)` uniqueness slot. Frontends don't need to do anything special — subsequent `GET /channels/{id}` returns `404`.

---

### Direct Messages (DM)

#### Create or get a DM
```
POST /api/chat/dm
```
**Request body:**
```json
{
  "targetUserId": 2
}
```
Idempotent — calling twice returns the same DM. Self-DMs are blocked (`400`).

**Response** `200`: same `ChannelResponse` shape, with `type: "DIRECT"`, `workspaceId: null`, and `name: null`. The `members` array contains exactly two user IDs but the order is **not guaranteed** — derive "the other user" by filtering out your own ID.

#### List DMs
```
GET /api/chat/dm?page=1&limit=20
```
Returns all DMs for the current user, sorted by most recently updated.

---

### Messages

#### Send a message
```
POST /api/chat/channels/{channelId}/messages
```
**Request body:**
```json
{
  "content": "Hello world!",
  "threadId": null,
  "replyToId": null,
  "mentions": [2, 5],
  "clientMessageId": "uuid-from-client"
}
```
Only `content` is required. Everything else is optional.

- `threadId` — if set, the message goes into that thread instead of the main channel feed.
- `replyToId` — inline reply reference.
- `mentions` — array of user IDs being mentioned.
- `clientMessageId` — client-generated UUID for idempotent sends. If you retry with the same `clientMessageId`, you get the original message back instead of a duplicate. Empty/blank strings are treated the same as omitting the field — no dedup will be performed.

**Response** `201`:
```json
{
  "id": "64f1a2b3...",
  "channelId": "64f1a000...",
  "senderId": 1,
  "content": "Hello world!",
  "type": "TEXT",
  "threadId": null,
  "replyToId": null,
  "mentions": [2, 5],
  "clientMessageId": "uuid-from-client",
  "deleted": false,
  "deletedAt": null,
  "createdAt": "2026-04-10T14:00:00Z",
  "updatedAt": "2026-04-10T14:00:00Z"
}
```

**Important:** REST `POST /messages` *also* publishes a `NEW_MESSAGE` WebSocket event to the channel topic (and to the thread topic if `threadId` is set). A frontend that uses REST to send and WebSocket to receive will see its own send arrive over the socket too — dedupe on `MessageResponse.id` if that is unwanted.

The `senderRole` field is stored server-side for admin-vs-admin delete checks but is **not** returned in `MessageResponse`.

#### Get channel messages (page-based)
```
GET /api/chat/channels/{channelId}/messages?page=1&limit=50
```
Returns top-level messages only (thread replies are excluded). Sorted newest first.

**Response** `200`: `PaginatedResponse<MessageResponse>`

#### Get channel messages (cursor-based)
```
GET /api/chat/channels/{channelId}/messages?before={messageId}&limit=50
GET /api/chat/channels/{channelId}/messages?after={messageId}&limit=50
```
- `before` — load older messages (scroll up).
- `after` — load newer messages (catch up after reconnect).

Cursor-based takes priority over page-based if both are provided.

**Response** `200`: `MessageResponse[]` (flat array, no pagination wrapper).

#### Edit a message
```
PUT /api/chat/messages/{messageId}
```
**Request body:**
```json
{
  "content": "edited text"
}
```
Edit is restricted to the original sender; **role does not matter** — admins cannot edit other users' messages. Returns `403` if a non-author tries to edit.

**Response** `200`: updated `MessageResponse`.

Also broadcasts an `EDIT_MESSAGE` event to the channel topic (and the thread topic when the message is in a thread).

#### Delete a message (soft)
```
DELETE /api/chat/messages/{messageId}
```
Sets `deleted: true` and `content: null`. The message stays in the DB.

**Rules:**
- Users can delete their own messages.
- Admins can delete non-admin users' messages.
- Admins CANNOT delete other admins' messages (`403`).
- Already-deleted messages are silently skipped — no broadcast is emitted.

**Response:** `200 OK`, empty body. On success, broadcasts `DELETE_MESSAGE` to the channel topic (and thread topic when applicable) with payload `{ "messageId": "..." }`.

---

### Threads

#### Create a thread
```
POST /api/chat/channels/{channelId}/threads
```
**Request body:**
```json
{
  "rootMessageId": "64f1a2b3...",
  "name": "discussion"
}
```
Both fields required.

**Validation rules:**
- Root message must exist.
- Root message must belong to this channel.
- Root message can't already be inside a thread.
- Only one thread per root message (`409` if duplicate).

**Response** `201`:
```json
{
  "id": "64f1b000...",
  "channelId": "64f1a000...",
  "rootMessageId": "64f1a2b3...",
  "name": "discussion",
  "createdBy": 1,
  "deleted": false,
  "createdAt": "2026-04-10T14:00:00Z",
  "updatedAt": "2026-04-10T14:00:00Z"
}
```

#### List threads in a channel
```
GET /api/chat/channels/{channelId}/threads?page=1&limit=20
```
Only returns non-deleted threads.

#### Get a thread
```
GET /api/chat/threads/{threadId}
```

#### Delete a thread (soft)
```
DELETE /api/chat/threads/{threadId}
```
Same admin rules as message deletion. Thread messages are cleaned up asynchronously.

**Response:** `200 OK`, empty body. Broadcasts `THREAD_DELETED` to **both** `/topic/channel/{channelId}` and `/topic/thread/{threadId}` with payload `{ "threadId": "...", "channelId": "..." }`.

#### Get thread messages
```
GET /api/chat/threads/{threadId}/messages?page=1&limit=50
GET /api/chat/threads/{threadId}/messages?before={messageId}&limit=50
GET /api/chat/threads/{threadId}/messages?after={messageId}&limit=50
```
Same pagination logic as channel messages.

---

### Read Receipts

Channel and thread read receipts are tracked separately.

#### Mark channel as read
```
POST /api/chat/channels/{channelId}/read
```
**Request body:**
```json
{
  "lastReadMessageId": "64f1a2b3..."
}
```
The cursor only moves forward — you can't mark older messages as "last read" if you've already read newer ones (newer marks win silently).

**Validation:** `lastReadMessageId` must exist, must belong to the same channel, and must be a top-level message (not a thread reply). Bad input returns `400`. Read cursors expire after 30 days of inactivity (refreshed on every successful mark).

#### Get channel unread count
```
GET /api/chat/channels/{channelId}/unread
```
**Response** `200`:
```json
{
  "unreadCount": 5,
  "lastReadMessageId": "64f1a2b3..."
}
```
`lastReadMessageId` is `null` if nothing has been read yet (in which case `unreadCount` is the total number of top-level messages in the channel).

#### Mark thread as read
```
POST /api/chat/threads/{threadId}/read
```
Same body as channel read. `lastReadMessageId` must belong to this thread; otherwise `400`.

#### Get thread unread count
```
GET /api/chat/threads/{threadId}/unread
```
Same response shape.

---

### WebSocket Ticket

#### Get a ticket
```
POST /api/chat/ws-ticket
```
No body. Returns:
```json
{
  "ticket": "e556486f-435d-4537-bea0-e7d1e1f0627b"
}
```
Ticket is valid for **60 seconds** and can only be used **once**.

---

## WebSocket (STOMP)

### Connecting

1. Call `POST /api/chat/ws-ticket` to get a ticket (via REST, with `X-User-Id` header).
2. Connect via STOMP to: `ws://localhost:8084/api/chat/connect?ticket={ticket}`.

The ticket is validated during the handshake. If invalid or expired, the connection is rejected.

### Subscribing

Subscribe to topics to receive real-time updates:

| Topic | Events |
|---|---|
| `/topic/channel/{channelId}` | `NEW_MESSAGE`, `EDIT_MESSAGE`, `DELETE_MESSAGE`, `THREAD_DELETED` for that channel (incl. thread activity that originates from this channel) |
| `/topic/thread/{threadId}` | `NEW_MESSAGE`, `EDIT_MESSAGE`, `DELETE_MESSAGE`, `THREAD_DELETED` for that thread |
| `/topic/channel/{channelId}/typing` | `TYPING` indicators for the channel |
| `/topic/thread/{threadId}/typing` | `TYPING` indicators for the thread |
| `/user/queue/errors` | per-session error envelopes (see *Error Handling* below) |

Subscription is **authorized** — you can only subscribe to channels you're a member of. Thread access is checked through the parent channel. If a `SUBSCRIBE` is rejected, the server sends a structured `ERROR` envelope to `/user/queue/errors` and silently drops the subscribe (no `RECEIPT` will arrive).

> **Duplicate delivery:** thread messages are broadcast to both the thread topic *and* the parent channel topic. Clients subscribed to both will receive the same event twice — dedupe on `MessageResponse.id` (or `payload.messageId` for `DELETE_MESSAGE`).

### Sending Messages (via STOMP)

Send to `/app/chat/send`:
```json
{
  "channelId": "64f1a000...",
  "content": "Hello!",
  "threadId": null,
  "replyToId": null,
  "mentions": [2],
  "clientMessageId": "uuid-from-client"
}
```
Only `channelId` and `content` are required.

### Typing Indicators

Send to `/app/chat/typing`:
```json
{
  "channelId": "64f1a000...",
  "threadId": null,
  "typing": true
}
```
No database write — just broadcasts to subscribers.

### Event Envelope

All WebSocket messages come wrapped in this format:

```json
{
  "action": "NEW_MESSAGE",
  "payload": { /* the actual data */ }
}
```

| Action | Payload | When |
|---|---|---|
| `NEW_MESSAGE` | `MessageResponse` | someone sent a message (REST or STOMP) |
| `EDIT_MESSAGE` | `MessageResponse` | someone edited a message |
| `DELETE_MESSAGE` | `{ "messageId": "..." }` | someone deleted a message |
| `TYPING` | `TypingNotification` | someone started/stopped typing |
| `THREAD_DELETED` | `{ "threadId": "...", "channelId": "..." }` | a thread was soft-deleted |
| `ERROR` | `{ "code": "...", "message": "..." }` | per-session error (see below) |

**TypingNotification:**
```json
{
  "userId": 2,
  "channelId": "64f1a000...",
  "threadId": null,
  "typing": true
}
```

### Error Handling (STOMP)

Errors are sent to `/user/queue/errors` (subscribe to this path to receive error notifications). They use the same `WebSocketEvent` envelope:
```json
{
  "action": "ERROR",
  "payload": {
    "code": "NOT_A_MEMBER",
    "message": "you are not a member of this channel"
  }
}
```

| Error Code | Meaning |
|---|---|
| `NOT_A_MEMBER` | user isn't in the channel |
| `CHANNEL_NOT_FOUND` | channel/thread doesn't exist |
| `INVALID_PAYLOAD` | missing required fields, malformed ObjectId, or empty path segment |
| `INTERNAL_ERROR` | something unexpected broke (or session identity is missing) |

Both `SUBSCRIBE` rejections (membership/auth failures) and `SEND` failures (`/app/chat/send`) flow through the same channel.

---

## Error Responses (REST)

All REST errors follow this shape:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "workspaceId is required for group channels",
  "timestamp": "2026-04-10T14:00:00Z"
}
```

For validation errors with multiple field violations, `message` joins them with `; ` — e.g. `"name: must not be blank; workspaceId: must not be null"`.

| Status | When |
|---|---|
| `400` | validation failures, bad input, malformed ObjectId, or `X-User-Id`/`X-User-Role` malformed |
| `401` | missing `X-User-Id` header |
| `403` | not a member, can't edit/delete someone else's message |
| `404` | channel/message/thread not found |
| `409` | duplicate thread for the same root message, or duplicate channel name in the same workspace |
| `503` | MongoDB or Redis is down |

---

## Data Types Quick Reference

- All IDs are **MongoDB ObjectId strings** (24-char hex, e.g. `"64f1a2b3c4d5e6f7a8b9c0d1"`).
- Timestamps are **ISO 8601 UTC** (e.g. `"2026-04-10T14:00:00Z"`).
- User IDs are **integers** (e.g. `42`).
- Channel types: `"GROUP"`, `"DIRECT"`, or `"ROOM"` (see below).
- Message types: `"TEXT"` or `"SYSTEM"` (the API only ever creates `TEXT` from client requests).
- Pages are **1-based** (`page=1` is the first page).

---

## Room text channels (`ROOM`)

Voice/video **rooms** (managed by **room-service**, `:8086`) each have a bound text channel of type `"ROOM"`. chat-service lets you **list and read** these channels but **not create or delete** them — they are provisioned by room-service (via RabbitMQ) behind the scenes.

How the frontend uses them:

1. List the room channels a user is in: `GET /api/chat/rooms?workspaceId=…` → `ChannelResponse[]` with `type: "ROOM"` and their `channelId`s. (Room **metadata** — display name, participants, presence — comes from room-service: `GET /api/rooms?workspaceId=…`; match by `channelId`.)
2. Use a `channelId` with the **normal** chat endpoints — messages, threads, read receipts, and the `/topic/channel/{channelId}` WebSocket topic all work exactly as for a `GROUP` channel (membership is enforced the same way; room-service keeps the channel's members in sync with room membership).

A unified "all my conversations" view is composed client-side from `GET /api/chat/channels?workspaceId` (groups) + `GET /api/chat/dm` (DMs) + `GET /api/chat/rooms?workspaceId` (room text channels). chat-service does **not** return a single merged list across the three types.
