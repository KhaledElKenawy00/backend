# Chat Service — Backend Summary

## Overview

A Spring Boot microservice handling real-time messaging, channels, threads, and read receipts. Runs on port **8084** with MongoDB for persistence and Redis for caching (read receipts + WS tickets).

## Tech Stack

| Component | Tech |
|---|---|
| Framework | Spring Boot 4.0.5, Java 21 |
| Database | MongoDB 7 (via Docker) |
| Cache | Redis 7 (via Docker) |
| Realtime | STOMP over WebSocket |
| Build | Maven (wrapper included) |

## Architecture

This service runs behind **Nginx** in a zero-trust setup. Nginx validates JWTs against the User Service and forwards `X-User-Id` and `X-User-Role` headers. The chat service never touches JWTs directly.

```
Client → Nginx (JWT validation) → Chat Service (trusts headers)
```

## Project Structure

```
src/main/java/com/khalwsh/chat_service/
├── config/              # mongo, redis, websocket, async, error handling
├── controller/          # REST + STOMP endpoints
├── dto/
│   ├── request/         # incoming payloads
│   ├── response/        # outgoing payloads
│   └── mapper/          # entity → DTO conversion
├── model/               # MongoDB documents (Channel, Message, ChatThread)
├── repository/          # Spring Data MongoDB repos
├── service/             # interfaces
│   └── impl/            # business logic
└── util/                # UserContext (header extraction)
```

## Data Model

### Channel
- `id` (ObjectId) — auto-generated
- `name` — channel name (null for DMs)
- `type` — `GROUP` or `DIRECT`
- `workspaceId` — integer (null for DMs)
- `members` — list of user IDs
- `dmKey` — deterministic key for DMs (e.g. `"5_42"`); null for GROUP channels
- `createdBy`, `createdAt`, `updatedAt`

### Message
- `id` (ObjectId) — auto-generated, time-sortable
- `channelId` — which channel this belongs to
- `senderId` — who sent it
- `senderRole` — stored at send time for admin-vs-admin delete checks (not exposed in `MessageResponse`)
- `content` — message text (null after soft-delete)
- `type` — `TEXT` or `SYSTEM`
- `threadId` — if set, this msg is inside a thread (excluded from main channel feed)
- `replyToId` — inline reply reference
- `mentions` — list of mentioned user IDs
- `clientMessageId` — client-generated UUID for idempotent sends (blank/empty treated as absent)
- `deleted`, `deletedAt` — soft delete fields
- `createdAt`, `updatedAt`

**Indexes (declared in `MongoConfig` for partial-filter support):**
- `(channelId, createdAt)` — channel message queries
- `(threadId, createdAt)` — thread message queries
- `(senderId, clientMessageId)` — partial unique index for idempotency (only when `clientMessageId` is present and non-null)
- `(workspaceId, name)` — partial unique index for channel name uniqueness (only when `workspaceId` is non-null, so DMs are excluded)
- `(dmKey)` — partial unique index for DM deduplication (only when `dmKey` is present and non-null, so GROUP channels are excluded). Done with a `partialFilterExpression` rather than `sparse: true` because Spring writes `dmKey: null` for GROUPs and `sparse` would still index that.

### ChatThread
- `id` (ObjectId) — auto-generated
- `channelId` — parent channel
- `rootMessageId` — the message this thread branches from (unique)
- `name` — thread name
- `createdBy` — thread creator
- `creatorRole` — stored for admin-vs-admin deletion rules
- `deleted`, `createdAt`, `updatedAt`

## Key Features

### Channel Management
- Create GROUP channels (require workspaceId + members)
- Join/leave group channels (idempotent join, can't join DMs)
- Create DMs (idempotent via `dmKey` partial-unique index, race-condition safe with `DuplicateKeyException` catch)
- Self-DMs blocked
- **Last-member leave deletes the channel** so the `(workspaceId, name)` uniqueness slot is freed and the channel is no longer reachable

### Messaging
- Send messages to channels or threads via REST or STOMP
- Page-based and cursor-based pagination
- Thread messages excluded from main channel feed
- Idempotent sends via `clientMessageId` (blank/empty normalized to absent — no spurious dedup collisions)
- Inline replies via `replyToId`
- User mentions
- **Both REST and STOMP send paths broadcast `NEW_MESSAGE`** to the channel topic, plus the thread topic when `threadId` is set. REST `PUT`/`DELETE` mirror the same topology.

### Edit / Delete Authorization
- **Edit**: only the original sender can edit. Role does not matter — admins cannot edit other users' messages
- **Delete (soft)**: sender can delete their own. Admins can delete non-admin messages. Admins cannot delete other admins' messages
- Same rules apply to thread deletion

### Threading
- One thread per root message (enforced by unique index)
- Root message must exist, belong to the channel, and not already be inside a thread
- Thread deletion is soft + triggers async cleanup of all thread messages
- `THREAD_DELETED` is broadcast to both the parent channel topic and the thread topic with payload `{ threadId, channelId }`

### Read Receipts
- Channel and thread tracked separately in Redis
- Keys: `read:{channelId}:{userId}` and `read:thread:{threadId}:{userId}`
- Forward-only cursor — older marks can't overwrite newer ones (atomic Lua script)
- **30-day TTL** on every successful mark (refreshed on each move-forward) so cursor keys can't grow unbounded
- `lastReadMessageId` is **validated server-side** before being applied: must exist, must belong to the channel/thread, and (for channel reads) must not be a thread reply. Bad input → `400`
- When the cursor is unset, unread count is the total number of (top-level) messages in the channel/thread (uses a dedicated count query rather than a synthetic `_id > epochZero` predicate)

### WebSocket
- STOMP over WebSocket at `/api/chat/connect`
- Broker prefixes: **`/topic`** (broadcast) and **`/queue`** (point-to-point — used for `/user/queue/errors`)
- Ticket-based auth: client gets a one-time ticket via REST (stores userId + role), passes it as `?ticket=` query param during WS handshake. Robust to malformed tickets.
- Subscription authorization: user must be a channel member to subscribe. Thread access checked via parent channel. **Rejections route a structured `ERROR` envelope to `/user/queue/errors` and drop the SUBSCRIBE silently** instead of throwing — frontends always get feedback
- Standardized event envelope: `{ action, payload }`
- Typing indicators (broadcast only, no DB write)
- Thread messages are intentionally broadcast to both `/topic/channel/{id}` and `/topic/thread/{id}`; clients de-dupe on `payload.id`

### Error Handling
- `GlobalExceptionHandler` catches MongoDB/Redis failures (503), validation errors (400), bad IDs (400), and generic exceptions (500)
- STOMP errors sent to `/user/queue/errors` with structured error codes (`NOT_A_MEMBER`, `CHANNEL_NOT_FOUND`, `INVALID_PAYLOAD`, `INTERNAL_ERROR`)
- STOMP send falls back to `INVALID_PAYLOAD` for `IllegalArgumentException` (malformed ObjectId) and to `INTERNAL_ERROR` for missing session identity (instead of the previous `IllegalStateException` that bypassed the error envelope)
- REST header validation: missing `X-User-Id` → 401, malformed `X-User-Id` or invalid `X-User-Role` → 400

### Observability (AOP)
- `LoggingAspect` intercepts service and controller method calls (skipping `HealthController` to avoid log spam from health probes)
- Service methods log at DEBUG level, controllers at INFO level
- `ResponseStatusException` (4xx-style) is logged at INFO/DEBUG instead of WARN/ERROR — these are normal client conditions, not faults
- Genuine errors still surface at WARN/ERROR with timing metrics
- No manual logging needed in business logic — AOP handles it transparently

## Tests

**253 unit tests** covering all layers (services, controllers, config, util, DTOs):

### Service Layer (≈111 tests)

| Test Class | Tests | What it covers |
|---|---|---|
| `ChannelServiceImplTest` | 27 | create, get, join, leave (incl. **last-member channel deletion**), DM idempotency + race, self-DM block, isMember, duplicate channel name |
| `MessageServiceImplTest` | 31 | send (membership, idempotency, mentions, threads, **blank-id ordering before dedup**), pagination, edit auth, delete auth (admin rules) |
| `ThreadServiceImplTest` | 19 | create (all validations), delete (creator, admin-vs-admin, async cleanup) |
| `ReadReceiptServiceImplTest` | 13 | channel + thread marks (lua script + TTL arg), unread counts, **validation: not-found / wrong channel / thread reply / malformed ID** |
| `WebSocketTicketServiceImplTest` | 11 | create (uniqueness, TTL, role storage), validate (one-time use, null/blank safety, role extraction, malformed payload defense) |
| `ThreadCleanupServiceImplTest` | 4 | batch soft-delete, skip already-deleted, empty thread, error resilience |

### Controller Layer (≈80 tests)

| Test Class | Tests | What it covers |
|---|---|---|
| `ChannelControllerTest` | 9 | create (success, dup-key 409), getChannels, getChannel (member, non-member 403), join, leave, DM, getDMs |
| `MessageControllerTest` | 14 | send (**now broadcasts NEW_MESSAGE to channel + thread when applicable**), getMessages (membership 403, page/before/after cursors, priority), edit (channel-only and channel+thread topology), delete (channel-only and channel+thread topology, payload contents, null-skip) |
| `ThreadControllerTest` | 12 | create, getChannelThreads, getThread, **delete broadcasts THREAD_DELETED to channel and thread topics with `{threadId, channelId}` payload**, getThreadMessages (membership, cursors) |
| `ReadReceiptControllerTest` | 8 | channel mark/unread (member, 403), thread mark/unread (parent channel member, 403) |
| `ChatStompControllerTest` | 25 | send (happy + thread topology + channel-only), validation, error mapping for 403/404/400/unknown/generic, **IllegalArgumentException → INVALID_PAYLOAD**, typing (channel/thread/null/no-userId silent drop), exception handler, **session-identity errors routed via /user/queue/errors instead of throwing** |
| `WebSocketTicketControllerTest` | 3 | create ticket for user/admin, role passing |
| `HealthControllerTest` | 1 | health check returns OK |

### Config Layer (≈33 tests)

| Test Class | Tests | What it covers |
|---|---|---|
| `GlobalExceptionHandlerTest` | 10 | mongo 503, redis 503, validation 400 (with/without fields), illegal-arg 400, response-status forwarding (401/403/404), catch-all 500 |
| `WebSocketHandshakeInterceptorTest` | 7 | valid ticket, admin role, invalid ticket, null ticket, non-servlet request, afterHandshake (null/exception) |
| `WebSocketSubscriptionInterceptorTest` | 15 | pass-through (non-subscribe, null dest, unrelated dest), channel sub (member, typing, **NOT_A_MEMBER + CHANNEL_NOT_FOUND + INVALID_PAYLOAD envelopes routed to /user/queue/errors, malformed ID, empty segment, no-session silent drop**), thread sub (member, typing, missing thread, non-member of parent channel, malformed thread ID) |
| `WebSocketConfigTest` | 4 | **broker registers /topic AND /queue, /app prefix, handshake interceptor wired, subscription interceptor wired** |

### AOP Layer (7 tests)

| Test Class | Tests | What it covers |
|---|---|---|
| `LoggingAspectTest` | 7 | service call (result, exception, null/empty/null-element args), controller call (result, exception) |

### Util & DTO Layer (25 tests)

| Test Class | Tests | What it covers |
|---|---|---|
| `UserContextTest` | 18 | parse valid (USER/ADMIN/lowercase/mixed), missing userId (null, blank), bad userId (NaN, float), missing role (null, empty, invalid), UserInfo helpers |
| `DtoMapperTest` | 10 | channel mapping (GROUP, DM/null fields), message mapping (normal, thread+reply, deleted, system, null mentions), thread mapping (normal, deleted, null name) |
| `WebSocketEventTest` | 7 | factory method, null payload, constants, builder, constructors |

Run tests:
```bash
# Linux / Mac
./mvnw test

# Windows
mvnw.cmd test
```

## How to Run

```bash
# set JAVA_HOME first
export JAVA_HOME=/path/to/your/jdk          # Linux / Mac
$env:JAVA_HOME = "C:\Users\Two Star\.jdks\openjdk-24.0.2"       # Windows (PowerShell)

# start mongo + redis
docker compose up -d

# run the service
# Linux / Mac
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

Service starts on `http://localhost:8084`. Docker containers are auto-managed by Spring Boot's Docker Compose integration.
