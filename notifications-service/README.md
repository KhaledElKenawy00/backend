# Notifications Service

Async notification dispatch for Virtual Office. Sends transactional emails
(signup, login, OTP, password reset) and persists in-app notifications
(task assigned) with live STOMP push.

See [`notification-service-arc.md`](./notification-service-arc.md) for the
full architecture and [`implementation-steps.md`](./implementation-steps.md)
for the build-up order.

## Running

This service brings up **no infra of its own**. It depends on:

| Infra | Owned by | Used for |
|---|---|---|
| RabbitMQ | `user-service/docker-compose.yml` | event consumption |
| MongoDB  | `chat-service/docker-compose.yml` | in-app notification storage (DB `notifications`) |
| Redis    | `chat-service/docker-compose.yml` | WS tickets + email dedup (keys prefixed `notif:*`) |

### Start order

```bash
# 1. Start RabbitMQ (via user-service)
cd ../user-service && ./mvnw spring-boot:run

# 2. Start MongoDB + Redis (via chat-service)
cd ../chat-service && docker compose up -d

# 3. Wait ~15s for Mongo's first-boot auth init to settle
#    (skip if data/ volumes already exist from a previous run)

# 4. Start the notification service
cd ../notifications-service && ./mvnw spring-boot:run
```

App listens on `http://localhost:8082`.

### Verifying the wiring

| What | Where |
|---|---|
| App health | `curl http://localhost:8082/actuator/health` → `{"status":"UP"}` |
| RabbitMQ UI | http://localhost:15672 (guest / guest) — check `notifications.queue` has Consumers: 1 |
| Mongo | `docker exec -it chat-mongo mongosh -u root -p rootpassword --authenticationDatabase admin` |
| Redis | `docker exec -it chat-redis redis-cli` — keys are prefixed `notif:*` |

## Sending a test email (HTTP path)

```bash
curl -i -X POST http://localhost:8082/api/notifications/email \
  -H "Content-Type: application/json" \
  -d '{
    "template": "SIGNUP_SUCCESS",
    "to": "your-test@example.com",
    "vars": { "firstName": "Khaled", "email": "your-test@example.com" }
  }'
```

Expected: `200 OK { status: "sent", eventId: "..." }`. Email lands in inbox.

Other templates: `LOGIN_SUCCESS`, `OTP`, `PASSWORD_RESET_SUCCESS`. See arc §7
for each one's expected placeholders.

## Sending a test event (RabbitMQ path)

Open the RabbitMQ management UI, navigate to **Exchanges → `notifications.exchange`**,
and publish:

- Routing key: `routing.key`
- Properties: `content_type` = `application/json`
- Payload:
  ```json
  {
    "eventId": "test-1",
    "type": "SIGNUP_SUCCESS",
    "occurredAt": "2026-05-16T14:00:00Z",
    "payload": { "firstName": "Khaled", "email": "your-test@example.com" }
  }
  ```

For an in-app notification, use `"type": "TASK_ASSIGNED"` with payload:
```json
{ "assigneeUserId": 12, "taskId": 884, "taskTitle": "Wire up SMTP retries",
  "assignedBy": 4, "assignedByName": "Mostafa" }
```

The notification will appear in Mongo and (if a WS client is connected) be
pushed live.

## Inbox API (frontend)

All routes require `X-User-Id` header (set by the gateway after JWT validation).

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/notifications?page=1&size=20` | Paginated list (page is **1-based**) |
| `GET` | `/api/notifications/unread-count` | `{ "unread": <n> }` |
| `PATCH` | `/api/notifications/{id}/read` | Mark one read |
| `PATCH` | `/api/notifications/read-all` | Mark all read |
| `DELETE` | `/api/notifications/{id}` | Dismiss |
| `POST` | `/api/notifications/ws-ticket` | Mint a one-time WS ticket |

## WebSocket

| Endpoint | Description |
|---|---|
| `POST /api/notifications/ws-ticket` | Get a single-use ticket (TTL 60s) |
| `WS /api/notifications/connect?ticket=<t>` | STOMP handshake |
| `SUBSCRIBE /user/queue/notifications` | Live push of new in-app notifications |
| `SUBSCRIBE /user/queue/errors` | Structured errors |

Push envelope:
```json
{
  "action": "NEW_NOTIFICATION",
  "payload": { /* NotificationResponse */ }
}
```

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| App starts but `notifications.queue` shows 0 consumers | `NotificationListener` missing `@Component` |
| Boot fails with `MongoSocketOpenException` | Chat-service's Mongo not running yet |
| Boot fails with `RedisConnectionFailureException` | Chat-service's Redis not running yet |
| Auth error on Mongo connect | Wait ~15s after first `docker compose up` (Mongo's root user init takes time on first boot) |
| Email arrives with literal `{{firstName}}` in body | `TemplateRenderService` not wrapping keys — should be `replace("{{" + key + "}}", value)` |
| Validation 400s have ugly Spring default body | `GlobalExceptionHandler` not on classpath / not annotated `@RestControllerAdvice` |

## Project layout

```
src/main/java/com/khalwsh/notifications/
├── NotificationsApplication.java
├── config/
│   ├── GlobalExceptionHandler.java
│   ├── RabbitMQConfig.java
│   ├── UserHandshakeHandler.java
│   ├── WebSocketConfig.java
│   └── WebSocketHandshakeInterceptor.java
├── controller/
│   ├── InboxController.java
│   ├── PublishController.java
│   └── WebSocketTicketController.java
├── dto/
│   ├── InboxPage.java
│   ├── NotificationResponse.java
│   ├── SendEmailRequest.java
│   └── WebSocketEvent.java
├── messaging/
│   ├── NotificationEvent.java
│   ├── NotificationListener.java
│   └── NotificationType.java
├── model/Notification.java
├── repository/NotificationRepository.java
├── service/
│   ├── EmailDispatchService.java
│   ├── EmailIdempotencyService.java
│   ├── InAppNotificationService.java
│   ├── NotificationPushService.java
│   ├── TemplateRenderService.java
│   └── WebSocketTicketService.java
├── template/EmailTemplate.java
└── util/UserContext.java
```
