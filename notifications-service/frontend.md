# Notifications Service - Frontend Integration Guide

Everything a frontend developer needs to integrate with the notifications
service. You should be able to wire up the in-app notification UI from this
document alone, without reading the backend code.

## 1. Before you start

This service has **no infrastructure of its own**. It is a pure client of
RabbitMQ (owned by user-service), MongoDB and Redis (owned by chat-service).

**Required start order:**

1. Bring up user-service's docker containers (this starts RabbitMQ).
2. Bring up chat-service's docker containers (this starts MongoDB and Redis).
3. **Wait 20-30 seconds** for MongoDB's first-boot initialization to finish.
   The MongoDB container is configured with auth, and the root user is created
   on first launch only. If you connect during that window you get auth
   errors. After that one-time wait, restarts are instant.
4. Start the notifications service.

### Bring everything up from the command line

These commands assume you are at the repository root and have Docker and a
JDK 21 installed.

**Linux / macOS**

```bash
# 1. Start RabbitMQ (lives in user-service's compose file).
cd backend/user-service && docker compose up -d && cd ../..

# 2. Start MongoDB + Redis (lives in chat-service's compose file).
cd backend/chat-service && docker compose up -d && cd ../..

# 3. Wait for MongoDB's first-boot auth init.
sleep 25

# 4. Start the notifications service in the foreground.
cd backend/notifications-service && ./mvnw spring-boot:run
```

If you prefer to keep the service running in the background:

```bash
cd backend/notifications-service && nohup ./mvnw spring-boot:run > notif.log 2>&1 &
```

**Windows (PowerShell)**

```powershell
# 1. Start RabbitMQ.
cd backend\user-service ; docker compose up -d ; cd ..\..

# 2. Start MongoDB + Redis.
cd backend\chat-service ; docker compose up -d ; cd ..\..

# 3. Wait for MongoDB's first-boot auth init.
Start-Sleep -Seconds 25

# 4. Start the notifications service.
cd backend\notifications-service ; .\mvnw.cmd spring-boot:run
```

### Verifying everything is up

Run this once you have started all three:

```bash
# Linux / macOS
curl -s http://localhost:8082/actuator/health
# -> {"status":"UP"}
```

```powershell
# Windows (PowerShell)
Invoke-WebRequest -UseBasicParsing http://localhost:8082/actuator/health |
    Select-Object -ExpandProperty Content
# -> {"status":"UP"}
```

### Bringing it down

```bash
# Linux / macOS - stop the foreground notification service with Ctrl-C, then:
cd backend/chat-service && docker compose down && cd ../..
cd backend/user-service && docker compose down && cd ../..
```

```powershell
# Windows - stop the foreground notification service with Ctrl-C, then:
cd backend\chat-service ; docker compose down ; cd ..\..
cd backend\user-service ; docker compose down ; cd ..\..
```

If this is anything other than `UP`, do not assume the API is working. Check
that the three infra pieces are reachable (see Troubleshooting below).

## 2. Base URL and auth

- Base URL during local development: `http://localhost:8082`
- Production: behind the gateway at `/api/notifications/...`

**Auth model:** the notifications service has no JWT logic. It trusts the
`X-User-Id` header set by the gateway after JWT validation. In local dev you
will need to set this header yourself on every inbox call.

- All inbox endpoints (Section 4) **require** `X-User-Id: <userId>`.
- The publish endpoint (Section 3) does **not** require `X-User-Id` (it is an
  internal service-to-service call, not user-facing).

Error response shape (any 4xx or 5xx from this service):

```json
{ "status": "error", "message": "human-readable reason" }
```

## 3. Publish endpoint (rarely used by the frontend)

This is mainly used by backend services to trigger one-off emails. Frontend
usually does not call it. Documented here for completeness.

```
POST /api/notifications/email
Content-Type: application/json

{
  "template": "SIGNUP_SUCCESS",
  "to": "user@example.com",
  "vars": { "firstName": "Khaled", "email": "user@example.com" }
}
```

Successful response: `200 OK`
```json
{ "status": "sent", "eventId": "uuid-v4" }
```

Valid templates: `SIGNUP_SUCCESS`, `LOGIN_SUCCESS`, `OTP`, `PASSWORD_RESET_SUCCESS`.

## 4. Inbox REST endpoints

All require `X-User-Id` header. All scope by that userId; cross-user access
returns `404` (we never leak existence of another user's notification).

### 4.1 List inbox (paginated, newest first)

```
GET /api/notifications?page=1&size=20
X-User-Id: 12
```

**`page` is 1-based.** `page=1` is the first page. `size` defaults to 20,
maximum 100 (silently capped). `page=0` or `size=0` returns `400`.

Response: `200 OK`
```json
{
  "items": [
    {
      "id": "655f9c8b1e2d3a4b5c6d7e8f",
      "type": "TASK_ASSIGNED",
      "title": "New task assigned",
      "body": "Mostafa assigned you \"Wire up SMTP retries\"",
      "data": {
        "taskId": 884,
        "assignedBy": 4,
        "workspaceId": 7,
        "dueAt": "2026-05-22T17:00:00Z"
      },
      "read": false,
      "createdAt": "2026-05-16T14:00:00.123Z"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 41,
  "unreadCount": 7
}
```

### 4.2 Unread count

```
GET /api/notifications/unread-count
X-User-Id: 12
```

Response: `200 OK`
```json
{ "unread": 7 }
```

Use this for the inbox badge in the navbar. Cheap and indexed.

### 4.3 Mark one notification as read

```
PATCH /api/notifications/{id}/read
X-User-Id: 12
```

- `200 OK` (empty body) if the notification existed and is now read. Idempotent:
  marking an already-read notification still returns `200`.
- `404 Not Found` if no notification with that id belongs to this user.

### 4.4 Mark all as read

```
PATCH /api/notifications/read-all
X-User-Id: 12
```

Response: `200 OK`
```json
{ "updated": 5 }
```

`updated` is the number that flipped from unread to read. Already-read
notifications are not counted.

### 4.5 Delete a notification

```
DELETE /api/notifications/{id}
X-User-Id: 12
```

- `204 No Content` on success.
- `404 Not Found` if no notification with that id belongs to this user.

## 5. WebSocket (live push)

When a new notification is created for the current user, the server pushes it
live over STOMP/WebSocket. The REST inbox is still the source of truth (use
it on app load and reconnect); the WebSocket only delivers events that happen
*while you are connected*.

### 5.1 Get a one-time ticket

Browsers cannot set headers on WebSocket handshakes, so we mint a short-lived
ticket over REST first.

```
POST /api/notifications/ws-ticket
X-User-Id: 12
```

Response: `200 OK`
```json
{ "ticket": "8c1d4a3e-..." }
```

The ticket:

- Is bound to the userId from `X-User-Id`.
- Lives for **60 seconds**.
- Is **single-use** - the server deletes it on the first successful WS
  handshake. A replay returns `401`.
- Get a fresh one for every connection attempt.

### 5.2 Connect

```
ws://localhost:8082/api/notifications/connect?ticket=<ticket>
```

(Production: `wss://...` through the gateway.)

If the ticket is missing, expired, replayed, or otherwise invalid, the
handshake returns `401` and the connection is dropped.

### 5.3 Subscribe

After the STOMP handshake, subscribe to:

```
/user/queue/notifications
```

That is the destination Spring routes your user's push messages to. You do
not put the userId in the destination - Spring resolves `/user/...` for you
based on the session principal that the ticket established.

(Optional) Subscribe to `/user/queue/errors` for structured error frames.

### 5.4 Receive

Each frame is JSON with this envelope:

```json
{
  "action": "NEW_NOTIFICATION",
  "payload": {
    "id": "655f9c8b1e2d3a4b5c6d7e8f",
    "type": "TASK_ASSIGNED",
    "title": "New task assigned",
    "body": "Mostafa assigned you \"Wire up SMTP retries\"",
    "data": { "taskId": 884, "assignedBy": 4, "workspaceId": 7, "dueAt": "2026-05-22T17:00:00Z" },
    "read": false,
    "createdAt": "2026-05-16T14:00:00.123Z"
  }
}
```

`payload` matches the `items[]` shape from the REST list endpoint exactly, so
you can use the same TypeScript/JS interface for both.

Future event types may add new `action` values - design your handler to
ignore unknown actions rather than crash.

### 5.5 Worked example with stomp.js

```javascript
// 1. Get a ticket
const ticketRes = await fetch('/api/notifications/ws-ticket', {
  method: 'POST',
  headers: { 'X-User-Id': String(currentUserId) }
});
const { ticket } = await ticketRes.json();

// 2. Open the WS using the ticket
const sock = new WebSocket(`ws://localhost:8082/api/notifications/connect?ticket=${ticket}`);
const stomp = Stomp.over(sock);

stomp.connect({}, () => {

  // 3. Subscribe to your user destination
  stomp.subscribe('/user/queue/notifications', (frame) => {
    const event = JSON.parse(frame.body);
    if (event.action === 'NEW_NOTIFICATION') {
      addToInbox(event.payload);
      bumpUnreadBadge();
    }
  });
});

// 4. On reconnect, refetch the inbox via REST to catch what you missed
sock.onclose = () => {
  refetchInboxFromRest();
  scheduleReconnect();
};
```

## 6. DTOs (TypeScript)

```typescript
type NotificationType =
  | "SIGNUP_SUCCESS"
  | "LOGIN_SUCCESS"
  | "OTP"
  | "PASSWORD_RESET_SUCCESS"
  | "TASK_ASSIGNED";

interface Notification {
  id: string;                   // Mongo ObjectId string
  type: NotificationType;       // currently only TASK_ASSIGNED is ever returned to the frontend
  title: string;
  body: string;
  data: Record<string, unknown>;
  read: boolean;
  createdAt: string;            // ISO-8601 instant
}

interface InboxPage {
  items: Notification[];
  page: number;                 // 1-based
  size: number;
  total: number;
  unreadCount: number;
}

interface WebSocketEvent<T = unknown> {
  action: string;               // "NEW_NOTIFICATION" today
  payload: T;
}

interface ErrorResponse {
  status: "error";
  message: string;
}
```

Note: only `TASK_ASSIGNED` notifications surface in the inbox today. The
email types (`SIGNUP_SUCCESS`, etc.) exist on the wire but are never stored
in the inbox - they go straight to email.

## 7. Suggested frontend flow

1. **On app load**, after the user logs in:
   - `GET /api/notifications/unread-count` for the navbar badge.
   - `GET /api/notifications?page=1` for the inbox dropdown / page.
   - `POST /api/notifications/ws-ticket`, then open the WebSocket.
2. **On `NEW_NOTIFICATION` frame**: prepend to the in-memory list, increment
   the unread badge, optionally show a toast.
3. **On user clicking a notification**: `PATCH /{id}/read`.
4. **On user clicking "Mark all read"**: `PATCH /read-all`, then reset the
   badge to 0 optimistically.
5. **On user clicking dismiss**: `DELETE /{id}`.
6. **On WebSocket close**: schedule a reconnect with backoff (1s, 2s, 4s,
   max 30s), and call `GET /api/notifications/unread-count` after a successful
   reconnect to catch up on anything missed while disconnected.

## 8. Troubleshooting

| What you see | What is likely wrong |
|---|---|
| All requests return `401 Missing X-User-Id` | The header is not being sent. In local dev you must set it manually; in production check the gateway is forwarding it. |
| `400 Invalid X-User-Id` | The header value is not a number. Userids are integers. |
| `404` on a notification you just received over WS | The id was malformed or the notification was deleted between push and click. The error response body has details. |
| WS handshake immediately closes | Ticket expired (>60s old), already used, or never minted. Get a fresh one. |
| WS opens but no frames arrive when you expect them | (a) The `assigneeUserId` in the published event does not match your current `X-User-Id`. (b) You are subscribed to the wrong destination - it must be exactly `/user/queue/notifications`, not `/topic/...`. |
| Inbox API returns 500 on the very first call after starting the stack | MongoDB is not ready yet. Wait the full 20-30 seconds after starting chat-service. |
| `unreadCount` keeps decreasing then jumping back up | You probably have multiple clients open and one is calling `PATCH /read-all`. Expected behavior. |

## 9. Quick reference

| Method | Path | Auth (`X-User-Id`) | Purpose |
|---|---|---|---|
| `POST`   | `/api/notifications/email`        | no  | (backend) trigger email synchronously |
| `GET`    | `/api/notifications`              | yes | paginated inbox (1-based) |
| `GET`    | `/api/notifications/unread-count` | yes | navbar badge count |
| `PATCH`  | `/api/notifications/{id}/read`    | yes | mark one read |
| `PATCH`  | `/api/notifications/read-all`     | yes | mark all read |
| `DELETE` | `/api/notifications/{id}`         | yes | dismiss |
| `POST`   | `/api/notifications/ws-ticket`    | yes | mint single-use WS ticket |
| `WS`     | `/api/notifications/connect?ticket=<t>` | (ticket) | STOMP upgrade |

Server-to-client STOMP destinations:

| Destination | Frames |
|---|---|
| `/user/queue/notifications` | `{ action: "NEW_NOTIFICATION", payload: Notification }` |
| `/user/queue/errors`        | structured errors (rare) |
