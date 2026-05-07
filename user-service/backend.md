> **Reality check**: this service is currently a **skeleton**. The repository contains only the JPA data model and Spring Boot entry point. There are **no controllers, services, repositories, security config, DTOs, mappers, or business logic** yet. Every line of source code is summarised below — there is genuinely nothing more to find.

---

## 1. Purpose

The User Service owns user identity for the Virtual Office platform: registration, login, profile management, and (in the rest of the architecture) JWT validation that other services rely on. According to [`backend/README.md`](../README.md) it is the only service that owns user data — Chat, Desk, etc. reference users by integer ID and call User Service for lookups.

---

## 2. Tech Stack

| Concern         | Choice                                                           |
|-----------------|------------------------------------------------------------------|
| Framework       | Spring Boot **3.4.1** (inherited from parent `backend/pom.xml`)  |
| Language        | Java **21**                                                      |
| Build           | Maven (multi-module; parent at `backend/pom.xml`)                |
| Persistence     | Spring Data JPA + Hibernate                                      |
| Database        | MySQL 8 (database `virtual_office`)                              |
| HTTP            | `spring-boot-starter-web` (Servlet stack)                        |
| Security        | `spring-boot-starter-security` (dependency present, **not configured**) |
| Messaging       | `spring-boot-starter-amqp` (RabbitMQ client present, **not used**) |
| Lombok          | Yes — `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor` |
| Shared code     | `com.virtualoffice:shared-library` Maven module                  |
| Tests           | JUnit 5 via `spring-boot-starter-test`, `spring-security-test`   |

### Dependencies declared but unused so far
- `spring-boot-starter-security` — **no `SecurityFilterChain` bean exists**. Out of the box this means Spring Security 6 will lock down every endpoint with HTTP Basic auth using a generated password printed at startup. Once you add controllers, you will almost certainly need a `@Configuration` class to permit the public auth endpoints and configure JWT.
- `spring-boot-starter-amqp` — RabbitMQ pulled in but no `RabbitTemplate`/listener/queue config. Presumably for publishing `user.created` / `user.email-verified` events to Notification Service.

---

## 3. Project Layout

```
user-service/
├── pom.xml
├── README.md
├── mvnw / mvnw.cmd                      (Maven wrappers)
└── src/
    ├── main/
    │   ├── java/com/virtualoffice/service/user/
    │   │   ├── VirtualOfficeUserApplication.java        ← @SpringBootApplication entry
    │   │   └── domain/
    │   │       ├── entity/
    │   │       │   ├── User.java
    │   │       │   └── VerificationRequests.java
    │   │       └── enumuration/                          ← (note: misspelled, sic)
    │   │           ├── AccountStatus.java
    │   │           └── VerificationRequestStatus.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/virtualoffice/service/user/
            └── VirtualOfficeUserApplicationTests.java   ← context-loads only
```

> The package directory is named `enumuration` (sic). Keep it consistent until renamed everywhere.

There are **no** `controller/`, `service/`, `repository/`, `dto/`, `config/`, or `security/` packages yet. All of those need to be created.

---

## 4. Configuration ([application.properties](src/main/resources/application.properties))

```properties
server.port=8081
spring.application.name=user-service

spring.datasource.url=jdbc:mysql://localhost:3306/virtual_office
spring.datasource.username=root
spring.datasource.password=your_password

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
```

Notes:
- `ddl-auto=update` is the default for "rapid prototyping" — Hibernate creates/alters tables on boot. **Switch to `validate` and use Flyway/Liquibase before staging.**
- The DB password is committed as a placeholder — load it from an env var (`${DB_PASSWORD}`) before any deploy.
- `show-sql=true` is verbose; turn it off for non-dev profiles.
- No connection pool tuning, no `application-prod.properties`, no Spring Profiles set up yet.

---

## 5. Application Entry Point

[`VirtualOfficeUserApplication.java`](src/main/java/com/virtualoffice/service/user/VirtualOfficeUserApplication.java)
```java
@SpringBootApplication
public class VirtualOfficeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(VirtualOfficeUserApplication.class, args);
    }
}
```

That's all there is — no `@EnableJpaAuditing`, no component scan tweaks, no `@EnableFeignClients`, no `@EnableAsync`. Add as needed.

---

## 6. Data Model

### 6.1 `User` ([User.java](src/main/java/com/virtualoffice/service/user/domain/entity/User.java))

```java
@Entity
@Table(name = "users")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "first_name", nullable = false, length = 100)        private String firstName;
    @Column(name = "last_name",  nullable = false, length = 100)        private String lastName;
    @Column(                     nullable = false, length = 100, unique = true)
                                                                        private String email;
    @Column(name = "phone_number", nullable = true, length = 20)        private String phoneNumber;
    @Column(name = "password_hash", nullable = false, length = 255)     private String password;

    @Column(name = "account_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "is_email_verified",        nullable = false)        private boolean isEmailVerified;
    @Column(name = "is_phone_number_verified", nullable = false)        private boolean isPhoneVerified;
    @Column(name = "is_disabled",              nullable = false)        private boolean isDisabled;
}
```

Things worth flagging:
- **`id` is `long`, not `Long`.** Other services in this repo (e.g. Chat) use `Integer` for `userId`. When publishing user IDs across services, agree on the wire type — `long` here vs `Integer` in chat will bite you.
- **No `createdAt` / `updatedAt`** auditing columns. Add `@CreatedDate` / `@LastModifiedDate` (and `@EnableJpaAuditing` on a config class) before going live.
- **No `role` column.** The architecture talks about `USER` / `ADMIN`, but there's nowhere to store it on the entity yet. Add an `enum Role` and column before security can differentiate.
- **`password` field, `password_hash` column.** The mismatch is fine but easy to misread; never `setPassword(plain)` anywhere — always pass a BCrypt/Argon2 hash.
- **`isDisabled` vs `accountStatus = SUSPENDED`** are two ways of saying "can't log in." Decide which is canonical (or what each means) before two reviewers diverge.
- The default `accountStatus = ACTIVE` initializer works for the no-args constructor and `@Builder` fallback, but `@AllArgsConstructor` lets a caller pass `null`. Validate at the service layer.

### 6.2 `AccountStatus` ([AccountStatus.java](src/main/java/com/virtualoffice/service/user/domain/enumuration/AccountStatus.java))

```java
public enum AccountStatus {
    ACTIVE, INACTIVE, PENDING_REPORT_REVIEW, SUSPENDED
}
```

| Value                    | Intended meaning (assumed)                                      |
|--------------------------|------------------------------------------------------------------|
| `ACTIVE`                 | Normal, can log in.                                              |
| `INACTIVE`               | Self-deactivated or never finished onboarding.                   |
| `PENDING_REPORT_REVIEW`  | Reported by another user; under moderator review.                |
| `SUSPENDED`              | Banned by an admin.                                              |

Stored as a string column (`@Enumerated(EnumType.STRING)`) — safe to reorder/add values without ordinal shifts.

### 6.3 `VerificationRequests` ([VerificationRequests.java](src/main/java/com/virtualoffice/service/user/domain/entity/VerificationRequests.java))

```java
@Entity
@Getter @Setter @Builder @NoArgsConstructor
public class VerificationRequests {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "otp", nullable = false)
    private long OTP;
}
```

Caveats — this entity is far from production-ready:
- **No `@Table` annotation** → Hibernate names the table after the class (`verification_requests` in snake-case via the default physical naming strategy, or `VerificationRequests` depending on naming config). Pin it explicitly.
- **No FK to `User`.** A verification request without a user it belongs to is meaningless.
- **No `expiresAt`** — OTPs must expire. Currently there is no way to reject old codes.
- **No `status`** — the `VerificationRequestStatus` enum exists in code but is not referenced anywhere.
- **No `type`** — there's no way to distinguish email vs phone OTPs.
- **`OTP` as `long`** — UPPERCASE field name violates Java conventions, and a numeric type loses leading zeros (a "012345" OTP becomes 12345). Prefer `String otp` with a length constraint.
- **No `@AllArgsConstructor`** (only `@NoArgsConstructor` + `@Builder`) — minor inconsistency with `User`.

Expect this entity to be expanded substantially before any verification endpoint ships.

### 6.4 `VerificationRequestStatus` ([VerificationRequestStatus.java](src/main/java/com/virtualoffice/service/user/domain/enumuration/VerificationRequestStatus.java))

```java
public enum VerificationRequestStatus { PENDING, APPROVED }
```

Currently unreferenced. Likely needs a `REJECTED` / `EXPIRED` value too.

---

## 7. Schema Hibernate will generate (effective today)

With `ddl-auto=update`, on first boot Hibernate will create roughly:

```sql
CREATE TABLE users (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    first_name               VARCHAR(100) NOT NULL,
    last_name                VARCHAR(100) NOT NULL,
    email                    VARCHAR(100) NOT NULL UNIQUE,
    phone_number             VARCHAR(20),
    password_hash            VARCHAR(255) NOT NULL,
    account_status           VARCHAR(255) NOT NULL,
    is_email_verified        BIT NOT NULL,
    is_phone_number_verified BIT NOT NULL,
    is_disabled              BIT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE verification_requests (
    id  BIGINT NOT NULL AUTO_INCREMENT,
    otp BIGINT NOT NULL,
    PRIMARY KEY (id)
);
```

(Booleans render as `BIT(1)` in MySQL via Hibernate defaults.)

---

## 8. What is NOT in the codebase yet

Be explicit so a backend dev coming in knows what they're building, not what they're maintaining:

- ❌ No REST controllers (no `/api/auth/**`, no `/api/users/**`, no JWT validation endpoint).
- ❌ No `JpaRepository<User, Long>` / `JpaRepository<VerificationRequests, Long>`.
- ❌ No `@Service` classes — no registration, login, profile, or OTP logic.
- ❌ No DTOs (`RegisterRequest`, `LoginRequest`, `UserResponse`, `JwtResponse`, …).
- ❌ No mappers (entity ↔ DTO).
- ❌ No password hasher bean (`BCryptPasswordEncoder`) — without one, security starter-app exposes default basic auth.
- ❌ No `SecurityFilterChain` configuration — every URL is currently behind the default-generated auth.
- ❌ No JWT issuance / validation (no `jjwt` / `nimbus-jose-jwt` on the classpath; you'll need to add one).
- ❌ No global exception handler (`@RestControllerAdvice`).
- ❌ No request validation (`@Valid` / Bean Validation annotations).
- ❌ No RabbitMQ config — no `Queue`/`Exchange`/`Binding` beans, no `@RabbitListener`.
- ❌ No Eureka / service-discovery client configured (the parent pom imports Spring Cloud, but this module doesn't depend on it).
- ❌ No tests beyond the auto-generated `contextLoads()`.
- ❌ No CI/build-verify other than what the repo-wide tooling provides.

---

## 9. Suggested implementation order

If you're picking this up, this is roughly the order things should be built so each step is testable on its own:

1. **Repositories** — `UserRepository extends JpaRepository<User, Long>` with a `findByEmail` method. `VerificationRequestsRepository`.
2. **Password encoding** — register a `BCryptPasswordEncoder` bean in a `SecurityConfig`.
3. **DTOs** — `RegisterRequest`, `LoginRequest`, `UserResponse`, `JwtResponse`, `ValidateResponse` (the one returned to the gateway).
4. **`SecurityConfig`** — disable CSRF for stateless API, permit `/api/auth/**`, require auth elsewhere, set `SessionCreationPolicy.STATELESS`.
5. **JWT utility** — token issue/parse using a library of choice; pull the secret from env, not properties.
6. **`AuthService` + `AuthController`** — register / login / validate.
7. **`VerificationToken` redesign** — add FK to `User`, `expiresAt`, `status`, `type`. Wire OTP creation into register flow.
8. **Notification publisher** — RabbitMQ `RabbitTemplate.convertAndSend(...)` for `user.created` / `user.email-verified` events.
9. **`UserController`** — `GET /api/users/me`, `PUT /api/users/me`.
10. **Tests** — controller slice tests with `@WebMvcTest` + `MockMvc`, `@DataJpaTest` for repositories, integration tests for the auth flow with `Testcontainers` + MySQL.
11. **Add a `Role` column** to `User` and propagate `X-User-Role` in the validate response so the rest of the platform stops hardcoding `USER`/`ADMIN` strings.
12. **Schema migration** — switch from `ddl-auto=update` to Flyway or Liquibase before any deploy.

---

## 10. Tests

Only one test file exists:

[`VirtualOfficeUserApplicationTests.java`](src/test/java/com/virtualoffice/service/user/VirtualOfficeUserApplicationTests.java)
```java
@SpringBootTest
class VirtualOfficeUserApplicationTests {
    @Test void contextLoads() {}
}
```

This requires a reachable MySQL on `localhost:3306` to actually pass (because `@SpringBootTest` boots the full context, which tries to open a JPA `EntityManagerFactory`). For CI you'll either need Testcontainers, an H2 profile, or a `@DataJpaTest` slice instead.

Run tests:
```bash
./mvnw -pl user-service test
```

---

## 11. How this service fits the rest of the platform

From [`backend/README.md`](../README.md) and `chat-service/chat-service-arc.md`:

- The **Gateway API** (`:8080`) is meant to send every authenticated request through this service for JWT validation. That endpoint (`/api/auth/validate`) is **not built yet** — once it exists, every other backend service stops dealing with JWTs and just trusts forwarded headers (`X-User-Id`, `X-User-Role`).
- **Chat Service** depends on User Service for user profile lookups (name/avatar) — caches results in Redis.
- **Desk Service** validates user existence against User Service when assigning a desk.
- **Notification Service** will be a downstream consumer of `user.*` events from this service via RabbitMQ.

Until the validation endpoint exists, the rest of the platform cannot run authenticated traffic end-to-end. **This is the highest-leverage thing to build first.**

---

## 12. Quick reference

```
Port            : 8081
DB              : MySQL  jdbc:mysql://localhost:3306/virtual_office
Spring Boot     : 3.4.1
Java            : 21
Entry point     : com.virtualoffice.service.user.VirtualOfficeUserApplication
Entities        : User, VerificationRequests
Enums           : AccountStatus { ACTIVE, INACTIVE, PENDING_REPORT_REVIEW, SUSPENDED }
                  VerificationRequestStatus { PENDING, APPROVED }
Endpoints       : (none yet)
Tests           : 1 (context-loads only)
```