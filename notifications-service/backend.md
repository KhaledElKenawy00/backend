# Notifications Service - Backend Producer Guide

This document is for backend developers working on **user-service** (and
later **tasks-service**) who need to publish events into the notifications
service. It also covers how to run the notifications service locally while
you develop your producer code.

## 1. The model in one sentence

The notifications service is a **pure consumer** on a single RabbitMQ queue.
You publish a `NotificationEvent` to one exchange with one routing key; the
notifications service does the rest (template lookup, SMTP send, MongoDB
write, WebSocket push).

You do not call the notifications service's HTTP API for events. AMQP is the
intended integration path. The HTTP `POST /api/notifications/email` endpoint
exists for ad-hoc / manual triggers only.

## 2. Running the notifications service locally

The notifications service has no infrastructure of its own. It connects to:

| Infra | Owner | Used for |
|---|---|---|
| RabbitMQ | **user-service**'s `docker-compose.yml` | event delivery |
| MongoDB  | **chat-service**'s `docker-compose.yml`  | in-app notifications storage (database `notifications`) |
| Redis    | **chat-service**'s `docker-compose.yml`  | WS tickets + email dedup (keys prefixed `notif:*`) |

### Start order

```bash
# 1. user-service brings up RabbitMQ
cd backend/user-service && ./mvnw spring-boot:run

# 2. chat-service brings up MongoDB + Redis
cd backend/chat-service && docker compose up -d

# 3. Wait 20-30s for MongoDB's first-boot root-user init.
#    Skip this wait on subsequent restarts (data volumes already initialized).

# 4. Now you can start the notifications service
cd backend/notifications-service && ./mvnw spring-boot:run
```

The notifications service listens on `:8082`. RabbitMQ management UI lives at
`http://localhost:15672` (`guest` / `guest`).

If MongoDB is configured with auth (it is) and you skip step 3, the
notifications service will fail to start with `MongoSecurityException`.

## 3. RabbitMQ topology

| Object | Value |
|---|---|
| Exchange | `notifications.exchange` (type `direct`, durable) |
| Queue | `notifications.queue` (durable) |
| Routing key | `routing.key` |

There is **one** binding from the exchange to the queue with that single
routing key. The type discriminator lives in the message body
(`event.type`), not in the routing key. Adding new notification types does
not require new routing keys or new queues.

There is no DLQ. After 5 retries with exponential backoff (1s, 2s, 4s, 8s,
16s, capped at 30s), failed messages are rejected without requeue and
dropped. We tolerate the loss for simplicity.

## 4. The `NotificationEvent` envelope

All messages on the queue share this JSON shape:

```json
{
  "eventId": "uuid-v4",
  "type": "SIGNUP_SUCCESS",
  "occurredAt": "2026-05-16T10:23:14.221Z",
  "payload": { /* type-specific, see section 6 */ }
}
```

| Field | Required | Notes |
|---|---|---|
| `eventId` | yes | UUID. Used for **idempotency** - if you publish the same eventId twice (e.g. due to your own retry logic), the notifications service deduplicates: it will send the email/insert the doc exactly once. Generate a fresh UUID per business event, not per publish attempt. |
| `type` | yes | One of `SIGNUP_SUCCESS`, `LOGIN_SUCCESS`, `OTP`, `PASSWORD_RESET_SUCCESS`, `TASK_ASSIGNED`. Producers and consumer must agree on this enum; see Section 7. |
| `occurredAt` | yes | ISO-8601 instant. Server timezone irrelevant. |
| `payload` | yes | Type-specific map. See Section 6. |

### `content_type` header

When publishing, set the AMQP `content_type` property to `application/json`
on the message. The notifications service's Jackson converter uses this to
decide how to deserialize. If you publish with the wrong content type, the
listener will reject the message and you will not see a clear error.

`RabbitTemplate.convertAndSend(...)` with a `Jackson2JsonMessageConverter`
(or its Jackson 3 equivalent) does this automatically.

## 5. Publishing from a Spring producer

Minimum dependencies in your service's pom.xml:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Configure these properties (point at the broker user-service brings up):

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Names match the notifications service's properties exactly.
notifications.exchange=notifications.exchange
notifications.routing.key=routing.key
```

Wire a `RabbitTemplate` with a JSON converter (Spring Boot 4 / Jackson 3
example):

```java
@Configuration
public class NotificationsPublisherConfig {

    @Bean
    public JacksonJsonMessageConverter notificationsMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate notificationsRabbitTemplate(
            ConnectionFactory cf,
            JacksonJsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
```

Local copy of the wire-contract DTOs (the notifications service does not
publish them as a shared library - keep an identical copy in your service):

```java
public enum NotificationType {
    SIGNUP_SUCCESS, LOGIN_SUCCESS, OTP, PASSWORD_RESET_SUCCESS, TASK_ASSIGNED
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationEvent {
    private String eventId;
    private NotificationType type;
    private Instant occurredAt;
    private Map<String, Object> payload;
}
```

Publish:

```java
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate notificationsRabbitTemplate;

    @Value("${notifications.exchange}")
    private String exchange;

    @Value("${notifications.routing.key}")
    private String routingKey;

    public void publish(NotificationType type, Map<String, Object> payload) {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                payload);
        notificationsRabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
```

Then from your own services:

```java
// After a successful register()
notificationPublisher.publish(NotificationType.SIGNUP_SUCCESS, Map.of(
        "userId",    user.getId(),
        "email",     user.getEmail(),
        "firstName", user.getFirstName()
));

// After a successful login
notificationPublisher.publish(NotificationType.LOGIN_SUCCESS, Map.of(
        "userId",    user.getId(),
        "email",     user.getEmail(),
        "firstName", user.getFirstName(),
        "loginAt",   Instant.now().toString(),
        "ip",        request.getRemoteAddr(),
        "userAgent", request.getHeader("User-Agent")
));

// When generating an OTP
notificationPublisher.publish(NotificationType.OTP, Map.of(
        "userId",            user.getId(),
        "email",             user.getEmail(),
        "firstName",         user.getFirstName(),
        "otp",               otpCode,
        "expiresInMinutes",  10
));

// After a password change
notificationPublisher.publish(NotificationType.PASSWORD_RESET_SUCCESS, Map.of(
        "userId",    user.getId(),
        "email",     user.getEmail(),
        "firstName", user.getFirstName(),
        "resetAt",   Instant.now().toString()
));
```

For `tasks-service` once it exists:

```java
notificationPublisher.publish(NotificationType.TASK_ASSIGNED, Map.of(
        "assigneeUserId",  assignee.getId(),
        "taskId",          task.getId(),
        "taskTitle",       task.getTitle(),
        "assignedBy",      currentUser.getId(),
        "assignedByName",  currentUser.getDisplayName(),
        "workspaceId",     task.getWorkspaceId(),
        "dueAt",           task.getDueAt() != null ? task.getDueAt().toString() : null
));
```

## 6. Per-type payload contracts

The notifications service is forgiving about extra payload keys (ignored)
but strict about required ones. Sending a missing `email` for an email
type means the message is dropped (no retry). Sending a missing
`assigneeUserId` for `TASK_ASSIGNED` is a silent no-op (logged at ERROR).

| Type | Required payload keys | Used for |
|---|---|---|
| `SIGNUP_SUCCESS`         | `email`, `firstName` | Welcome email |
| `LOGIN_SUCCESS`          | `email`, `firstName`, `loginAt`, `ip`, `userAgent` | Sign-in notification email |
| `OTP`                    | `email`, `firstName`, `otp`, `expiresInMinutes` | Verification code email |
| `PASSWORD_RESET_SUCCESS` | `email`, `firstName`, `resetAt` | Password-changed confirmation email |
| `TASK_ASSIGNED`          | `assigneeUserId`, `taskId`, `taskTitle`, `assignedBy`, `assignedByName`, `workspaceId`, `dueAt` | In-app notification (Mongo + WebSocket push) |

All values can be primitives or strings. Numeric values are accepted as both
JSON numbers and JSON strings (the consumer parses both).

For email types: `userId` is conventional but not required by the consumer.
Add it for your own logging/correlation if you want.

## 7. The `NotificationType` enum is a shared wire contract

The notifications service declares this enum in its own codebase. Your
producer must declare a **matching** copy. There is no shared library
(intentional - we don't want a cross-cutting Maven dependency).

If you add a new value to your local copy and publish it, the notifications
service's Jackson will fail to deserialize the message (`InvalidFormatException`),
the listener will retry 5 times, and the message will be dropped. The
notifications service team must add the new value first.

Rule of thumb: when adding a new notification type, do it on the consumer
side first, ship that, then ship the producer change.

## 8. Idempotency, retries, and your responsibilities

The notifications service:

- Deduplicates by `eventId` for emails (Redis SETNX, 24h window).
- Deduplicates by `eventId` for in-app notifications (unique Mongo index).
- Retries on transient failures (SMTP timeout, Mongo unavailable, etc.) up
  to 5 times with exponential backoff.
- Drops messages that exhaust retries (no DLQ).

Producer side:

- **Use a fresh UUID per business event.** Not per publish attempt. If you
  publish "user 12 signed up" twice with the same eventId, the second call
  is dropped intentionally.
- **Do not retry on AMQP publish failures by re-generating eventId.** If
  your publish fails (broker unreachable), retry with the **same** eventId
  - that's what idempotency is for.
- **Do not block your business logic on the publish.** If user-service is
  registering a user, the registration must succeed even if RabbitMQ is
  down. Publish as a side effect after the DB commit, and log+swallow any
  AMQP exceptions.
- **You do not need to await any response.** AMQP publish is fire-and-forget.

## 9. Testing your producer locally

The fastest end-to-end smoke test:

1. Start user-service, chat-service, and notifications-service per Section 2.
2. From your producer code, publish a `SIGNUP_SUCCESS` event.
3. Watch the notifications service log for `Created new connection` and the
   listener entry. The email should arrive at the address in the payload
   within a few seconds.

To test without writing producer code, use the **RabbitMQ management UI**:

1. Open http://localhost:15672 (guest / guest).
2. Exchanges -> `notifications.exchange` -> Publish message.
3. Routing key: `routing.key`.
4. Properties: set `content_type` to `application/json`.
5. Payload:
   ```json
   {
     "eventId": "manual-test-1",
     "type": "SIGNUP_SUCCESS",
     "occurredAt": "2026-05-16T10:00:00Z",
     "payload": { "email": "you@example.com", "firstName": "Test" }
   }
   ```
6. Click Publish.

Within ~1 second the queue should drain (Ready count goes 1 -> 0). The
notifications service log will show the listener firing. The email will
arrive at the address you specified.

Republish with the same eventId to verify dedup: no second email arrives.

## 10. Observability

| What | How |
|---|---|
| Did the message land in the queue? | RabbitMQ UI -> Queues -> `notifications.queue` -> Get messages |
| Is the listener running? | RabbitMQ UI -> Queues -> `notifications.queue` -> "Consumers" column should be 1 |
| Is the email being sent? | Notifications service log lines from `EmailDispatchService` |
| Is the in-app doc being saved? | `db.notifications.find({ eventId: "your-eventId" })` against MongoDB |
| Are retries happening? | Notifications service log shows the retry attempts at WARN/ERROR |

## 11. Common pitfalls

| Symptom | Likely cause |
|---|---|
| Message lands in queue but listener never consumes it | Notifications service is not running, or `NotificationListener` is not registered as a Spring bean |
| Listener fails with `InvalidFormatException` for `type` | Your producer is sending an unknown enum value (typo, or producer enum out of date with consumer) |
| Listener fails with Jackson `Cannot construct instance of Instant` | You forgot to register `JavaTimeModule` on your producer's ObjectMapper. Use Jackson 3 or register the module manually. |
| `content_type` header missing -> message rejected | RabbitTemplate's message converter isn't wired up. Verify your config sets `Jackson2JsonMessageConverter` (or its Jackson 3 equivalent) on the template. |
| Sending OTP works in dev but never arrives in inbox | Gmail blocked the SMTP login. App Password expired or 2FA disabled on the dev mailbox. |
| Same email arrives twice | You're either using a different `eventId` per retry, or 24 hours has passed (dedup TTL expired). |

## 12. Production posture (notes for later)

The local setup uses Gmail SMTP with credentials inline in
`application.properties`. Before deploying:

- Move `spring.mail.username` and `spring.mail.password` to environment
  variables.
- Use a transactional mail provider (SES, SendGrid, Postmark) rather than
  Gmail SMTP.
- Move RabbitMQ from "shared with user-service" to a managed broker
  (AmazonMQ, CloudAMQP, etc.) or a dedicated cluster.
- Configure TLS on both the broker connection and the WebSocket endpoint.

None of these change the wire contract. Your producer code from Section 5
keeps working unchanged.
