# User Service — Frontend Integration Guide

> **Status**: this service is currently a **skeleton**. Only the JPA data model and Spring Boot scaffolding exist in code. **No REST endpoints have been implemented yet.** Every section below describes what is *currently in the codebase*; anything labelled _planned_ is implied by the README ("Key Features") but not yet wired up. Treat this document as the integration contract a frontend dev can rely on **today**, plus the data shapes you will be reading/writing once endpoints land.

---

## 1. What the service is for

The User Service is responsible for user identities, profiles, and authentication in the Virtual Office platform. The README lists three "Key Features":

- User Registration and Login
- Profile Management
- Authentication & Authorization (Security)

In the broader microservice topology (see [`backend/README.md`](../README.md)), this is the only service that owns user identity. Other services (Chat, Desk, etc.) treat user IDs as foreign keys and call User Service for lookups.

---

## 2. How to run it locally

### Prerequisites
- **JDK 21**
- **Maven** (or use `mvnw` from the repo root)
- **MySQL 8** running locally with a database called `virtual_office`

### Configuration ([src/main/resources/application.properties](src/main/resources/application.properties))
```properties
server.port=8081
spring.application.name=user-service

spring.datasource.url=jdbc:mysql://localhost:3306/virtual_office
spring.datasource.username=root
spring.datasource.password=your_password   # ← change before running

spring.jpa.hibernate.ddl-auto=update       # Hibernate auto-creates/updates tables
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
```

> ⚠️ Replace `your_password` with the actual MySQL root password before launching.

### Start the service
```bash
# from the user-service directory
./mvnw spring-boot:run        # Linux / macOS
mvnw.cmd spring-boot:run      # Windows
```

Service comes up on **`http://localhost:8081`**.

In the deployed topology, all client traffic goes through the **Gateway API** on port `8080`, which forwards to the User Service. For local frontend dev you can call `:8081` directly until the Gateway is configured.

---

## 3. Authentication model (where this service sits)

According to the cross-service architecture documented in `chat-service/chat-service-arc.md`, the **User Service is the source of truth for JWT validation**. The expected flow is:

1. Client sends `Authorization: Bearer <JWT>` to any service (via Nginx/Gateway).
2. Gateway calls `User Service` with the token.
3. User Service responds 200 with headers `X-User-Id`, `X-User-Email`, `X-User-Role`, OR 401 if invalid.
4. Other services trust those forwarded headers and never parse JWTs themselves.

> ⚠️ The endpoint that performs that validation (`GET /api/auth/validate` in the design docs) **does not exist in code yet** — it is the next thing to build. Once it exists, the frontend will only ever talk to login/register/profile endpoints directly; everything else will be authenticated transparently by the gateway.

---

## 4. REST API

**There are currently no REST endpoints implemented.** The repository contains:
- `VirtualOfficeUserApplication.java` — `@SpringBootApplication` entry point, nothing else
- 2 JPA entities (`User`, `VerificationRequests`)
- 2 enums (`AccountStatus`, `VerificationRequestStatus`)

When endpoints do land, the conventional base path will be `/api/auth/**` and `/api/users/**` (matching the gateway pattern other services use). Until then, every HTTP call to this service returns `404`.

### Planned endpoints (inferred from data model + README)

These are not yet implemented — listing them here so the frontend can stub against them:

| Method | Path                            | Purpose                                                |
|--------|---------------------------------|--------------------------------------------------------|
| POST   | `/api/auth/register`            | Create a new user                                      |
| POST   | `/api/auth/login`               | Issue a JWT                                            |
| GET    | `/api/auth/validate`            | Validate a JWT, return user identity (used by Gateway) |
| POST   | `/api/auth/verify-email`        | Submit email OTP                                       |
| POST   | `/api/auth/verify-phone`        | Submit phone OTP                                       |
| GET    | `/api/users/me`                 | Get my profile                                         |
| PUT    | `/api/users/me`                 | Update my profile                                      |

**Do not assume these shapes are final.** Coordinate with the backend dev once they're being implemented.

---

## 5. Data Model (what you will be reading / writing)

These are the shapes that exist in code today as JPA entities. When DTOs are added, they will most likely mirror these but redact `password` and add metadata.

### 5.1 `User` ([User.java](src/main/java/com/virtualoffice/service/user/domain/entity/User.java))

| Field              | Type             | Column                     | Constraints                        |
|--------------------|------------------|----------------------------|------------------------------------|
| `id`               | `long`           | `id` (auto-increment PK)   | identity-generated                 |
| `firstName`        | `String`         | `first_name`               | not null, max 100 chars            |
| `lastName`         | `String`         | `last_name`                | not null, max 100 chars            |
| `email`            | `String`         | `email`                    | not null, max 100, **unique**      |
| `phoneNumber`      | `String`         | `phone_number`             | nullable, max 20 chars             |
| `password`         | `String`         | `password_hash`            | not null, max 255 (stored hashed)  |
| `accountStatus`    | `AccountStatus`  | `account_status`           | not null, enum stored as STRING; default `ACTIVE` |
| `isEmailVerified`  | `boolean`        | `is_email_verified`        | not null                           |
| `isPhoneVerified`  | `boolean`        | `is_phone_number_verified` | not null                           |
| `isDisabled`       | `boolean`        | `is_disabled`              | not null                           |

> The DB column is `password_hash` (the field is just named `password` in Java) — passwords are intended to be stored as hashes, never plaintext. The frontend never receives this field.

#### `AccountStatus` enum
```
ACTIVE                  // normal, can log in
INACTIVE                // self-deactivated or expired
PENDING_REPORT_REVIEW   // user is under moderation review
SUSPENDED               // banned by an admin
```

#### Likely `UserResponse` shape (when DTOs land)
```json
{
  "id": 42,
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "phoneNumber": "+1-555-0100",
  "accountStatus": "ACTIVE",
  "isEmailVerified": true,
  "isPhoneVerified": false,
  "isDisabled": false
}
```

### 5.2 `VerificationRequests` ([VerificationRequests.java](src/main/java/com/virtualoffice/service/user/domain/entity/VerificationRequests.java))

A barebones OTP record — currently only contains:

| Field | Type   | Notes                              |
|-------|--------|------------------------------------|
| `id`  | `long` | identity-generated PK              |
| `OTP` | `long` | the one-time code (column `otp`)   |

> Heads up: this entity is missing a foreign key to `User`, an expiry timestamp, a status (the `VerificationRequestStatus` enum exists but isn't referenced), and a "purpose" field (email vs phone). It will almost certainly grow before any verification endpoint ships. Don't model your frontend strictly around this shape today.

#### `VerificationRequestStatus` enum
```
PENDING
APPROVED
```

---

## 6. Frontend integration contract — what to do *today*

Because no endpoints exist yet, here's what a frontend developer can do right now:

1. **Stub the auth flow.** Mock POST `/api/auth/login` returning a fake JWT and POST `/api/auth/register` returning a `UserResponse` (use the shape in §5.1). When the real endpoints land, swap out the mock URLs.
2. **Build forms against the entity validation rules.** firstName/lastName ≤ 100, email ≤ 100 unique, phoneNumber ≤ 20 optional, password (whatever client-side rule you choose; the backend stores a hash up to 255).
3. **Treat email as the login identifier.** It's the only naturally unique non-PK column.
4. **Plan for two verification flows** — email and phone — even though only OTP storage exists today.
5. **Don't store the JWT in `localStorage` long-term** — once login is wired, prefer `httpOnly` cookies set by the backend. The architecture documents elsewhere assume the gateway forwards `Authorization: Bearer ...`.
6. **Account status handling.** Even before endpoints exist, design UI states for: account locked (`SUSPENDED`), under review (`PENDING_REPORT_REVIEW`), self-disabled (`isDisabled`). The Login screen should distinguish "wrong password" from "account suspended" once the backend differentiates.

---

## 7. Sequence the frontend can assume (once implemented)

```
[Register]
  POST /api/auth/register   { firstName, lastName, email, phoneNumber?, password }
    → 201 Created
    → server creates User (accountStatus=ACTIVE, isEmailVerified=false, ...)
    → server creates VerificationRequests row, sends OTP via email

[Verify email]
  POST /api/auth/verify-email   { email, otp }
    → 200 OK
    → user.isEmailVerified = true

[Login]
  POST /api/auth/login   { email, password }
    → 200 OK   { token, user }   (or sets httpOnly cookie)
    → 401 if password mismatch
    → 423 / 403 if accountStatus != ACTIVE  (suggested mapping)

[Profile]
  GET /api/users/me                 (auth required)
    → 200 OK   UserResponse
  PUT /api/users/me   { firstName?, lastName?, phoneNumber? }
    → 200 OK   updated UserResponse
```

This is the *intended* flow per the README; the frontend dev should agree on exact request/response shapes with the backend dev before merging real integrations.

---

## 8. Known gaps / questions to ask backend before wiring

- Where does the JWT signing key live? Env var or config?
- Will registration auto-issue a JWT or require email verification first?
- What's the OTP delivery channel for phone verification — SMS gateway, RabbitMQ event to Notification Service?
- The pom includes `spring-boot-starter-amqp` (RabbitMQ) — what events will User Service publish? (`user.created`, `user.email-verified`, …?)
- How will roles (`USER` vs `ADMIN`) be modeled? There is **no role field on the User entity** today.
- What's the password complexity policy?
- Is `phoneNumber` required at registration or only when the user opts into phone verification?

Treat the answers as gating questions before the frontend ships any production user flow.