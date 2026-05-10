# User Service — Frontend Integration Guide

A complete reference for integrating a frontend with the User Service. Everything below describes what is currently in the code.

---

## 1. Running the service locally

### Prerequisites
- **JDK 21** with `JAVA_HOME` pointing at it
- **Docker** installed and running (Docker Desktop on Windows / macOS)

Nothing else is needed locally — MySQL and RabbitMQ are launched automatically by Spring Boot's `spring-boot-docker-compose` integration using `user-service/docker-compose.yml`.

### Start
```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk21
cd backend
./mvnw -pl user-service spring-boot:run

# Windows (PowerShell)
$env:JAVA_HOME = "C:\path\to\jdk21"
cd backend
.\mvnw.cmd -pl user-service spring-boot:run
```

What happens automatically:
1. Spring Boot reads [`docker-compose.yml`](docker-compose.yml), runs `docker compose up`, and waits for MySQL + RabbitMQ to be healthy.
2. Hibernate creates/updates the `users` and `verification_requests` tables on first boot (`ddl-auto=update`).
3. Tomcat listens on **`http://localhost:8081`**.
4. **Ctrl+C** → Spring Boot runs `docker compose down` automatically (`spring.docker.compose.lifecycle-management=start-and-stop`).

### Caveats
- **Port 3306 must be free.** If a local MySQL Windows service is running, the Docker container can't bind to it. Stop it once from an elevated PowerShell:
  ```powershell
  Stop-Service MySQL80 -Force
  Set-Service MySQL80 -StartupType Manual
  ```
  Same applies to RabbitMQ on `:5672` and `:15672` if a local install owns those ports.
- **Docker Desktop must already be running** before you run `mvnw spring-boot:run`.
- The compose file persists data under `user-service/data/mysql` and `user-service/data/rabbitmq`. Delete those folders to reset to a clean DB.

---

## 2. Service URL and ports

| Item | Value |
|---|---|
| Base URL (local) | `http://localhost:8081` |
| Auth base path | `/api/auth` |
| MySQL (host) | `localhost:3306`, user `root` / password `rootpassword`, database `virtual_office` |
| RabbitMQ AMQP | `localhost:5672` (`guest` / `guest`) |
| RabbitMQ management UI | `http://localhost:15672` |

---

## 3. Authentication model

The User Service is the source of truth for authentication. It accepts email + password, hashes passwords with **BCrypt**, and issues a **JWT** signed with **HS256** containing the user's email as the subject. Token TTL is **24 hours** (`jwt.expiration=86400000` ms).

### How requests are authorized
- Endpoints under `/api/auth/**` are **public** (`permitAll`).
- Every other endpoint requires a valid JWT in the `Authorization: Bearer <token>` header.
- The service is **stateless** — no session cookies. Every request must carry the token.
- CSRF protection is disabled.

### How the JWT filter works ([`JwtAuthFilter`](src/main/java/com/virtualoffice/service/user/security/JwtAuthFilter.java))
1. Skips entirely for any path starting with `/api/auth/`.
2. For every other path: reads the `Authorization` header.
3. If the header is missing or doesn't start with `Bearer `, the request continues unauthenticated (and Spring Security will then return `403`).
4. If the header is present, the filter:
   - Strips the `Bearer ` prefix.
   - Extracts the email (`sub` claim) from the token.
   - Loads the user from MySQL by that email.
   - Verifies the token (signature, expiration, email matches the user).
   - Sets a `UsernamePasswordAuthenticationToken` on the `SecurityContext` so downstream code sees the request as authenticated.

### Account status checks at login ([`CustomUserDetailsService`](src/main/java/com/virtualoffice/service/user/security/CustomUserDetailsService.java))
- `isDisabled = true` → login is rejected (translated to a `UsernameNotFoundException`).
- `accountStatus = SUSPENDED` → login is rejected as **account locked**.
- All other statuses (`ACTIVE`, `INACTIVE`, `PENDING_REPORT_REVIEW`) currently allow login as far as Spring Security is concerned.

---

## 4. REST API

All endpoints below are implemented in [`AuthController`](src/main/java/com/virtualoffice/service/user/controller/AuthController.java).

### 4.1 Register
```
POST /api/auth/register
Content-Type: application/json
```

**Request body** (`RegisterRequest`):
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "password": "hello12345",
  "phoneNumber": "+1-555-0100"
}
```

All five fields are accepted by the DTO. `phoneNumber` may be omitted (the column is nullable in the DB). There is **no bean-validation (`@Valid`)** wired up on the controller — the values you send are passed straight through to the entity, with these column constraints (a violation throws a server-side error):

| Field         | Constraints                                      |
|---------------|--------------------------------------------------|
| `firstName`   | required, max 100 chars                          |
| `lastName`    | required, max 100 chars                          |
| `email`       | required, max 100 chars, **unique**              |
| `password`    | required, hashed before storage (BCrypt)         |
| `phoneNumber` | optional, max 20 chars                           |

**On success — `200 OK`** (`AuthResponse`):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "email": "jane@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "errorMessage": "None"
}
```

**On duplicate email — `200 OK`** (note: still 200, not 409):
```json
{
  "token": null,
  "email": null,
  "firstName": null,
  "lastName": null,
  "errorMessage": "Such E-mail Already Exist"
}
```

> The frontend **must** check `errorMessage` to detect duplicate-email failures — the HTTP status alone does not distinguish success from failure here.

User defaults applied at registration:
- `accountStatus = ACTIVE`
- `isEmailVerified = false`
- `isPhoneVerified = false`
- `isDisabled = false`

### 4.2 Login
```
POST /api/auth/login
Content-Type: application/json
```

**Request body** (`LoginRequest`):
```json
{
  "email": "jane@example.com",
  "password": "hello12345"
}
```

**On success — `200 OK`** (`AuthResponse`, same shape as register):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "email": "jane@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "errorMessage": "None"
}
```

**Failure modes:**
- **Wrong password / unknown email / disabled account / suspended account** → Spring Security throws an authentication exception. There is no global exception handler in the codebase, so the response is the framework default — typically **`403 Forbidden`** with an empty body.
- **Sending an empty body `{}` or missing fields** → also surfaces as `403`.

> So login distinguishes "OK" (`200` with token) from "everything else" (`403` empty body). There is no machine-readable error message for failed logins today.

### 4.3 No other endpoints

The codebase contains no other controllers. There are no:
- `GET /api/users/me` or other profile endpoints
- Email/phone verification endpoints (the `VerificationRequest` entity exists but isn't exposed via HTTP)
- Logout / token refresh endpoints
- Admin endpoints
- `/actuator/**` is present (Actuator is on the classpath through Spring Boot defaults) but **all actuator paths return `403`** because they are not in the `permitAll` list.

---

## 5. Data Shapes

### `RegisterRequest`
```ts
{
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phoneNumber?: string;
}
```

### `LoginRequest`
```ts
{
  email: string;
  password: string;
}
```

### `AuthResponse`
```ts
{
  token: string | null;        // JWT (HS256), null on error
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  errorMessage: string;        // "None" on success, otherwise an error label
}
```

`errorMessage` values currently produced by the backend:
- `"None"` — success
- `"Such E-mail Already Exist"` — duplicate email on register
- `"User Not Found"` — login passed authentication but user lookup returned null (defensive branch; in practice you will hit `403` first)

---

## 6. JWT details (what's inside the token)

A token returned by register/login decodes to:
- Header: `{ "alg": "HS256" }`
- Payload:
  - `sub` — the user's email
  - `iat` — issued-at (seconds since epoch)
  - `exp` — expiration (seconds since epoch, 24 h after `iat`)
- Signature: HMAC-SHA256 over the secret in `application.properties` (`jwt.secret`).

The token does **not** carry the user's id, role, name, or any claim other than the email.

---

## 7. Frontend integration recipe

```ts
// 1. Register
const reg = await fetch("http://localhost:8081/api/auth/register", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    firstName: "Jane",
    lastName: "Doe",
    email: "jane@example.com",
    password: "hello12345"
  })
}).then(r => r.json());

if (reg.errorMessage !== "None") throw new Error(reg.errorMessage);
const token = reg.token;

// 2. Login (same shape)
const lg = await fetch("http://localhost:8081/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: "jane@example.com", password: "hello12345" })
});
if (lg.status !== 200) throw new Error("login failed");   // empty 403 on bad creds
const { token: jwt } = await lg.json();

// 3. Call any future protected endpoint
await fetch("http://localhost:8081/some/protected/path", {
  headers: { "Authorization": `Bearer ${jwt}` }
});
```

---

## 8. Quick reference

```
Base URL              http://localhost:8081
Public endpoints      POST /api/auth/register          -> 200 AuthResponse (check errorMessage)
                      POST /api/auth/login             -> 200 AuthResponse on success, 403 otherwise
Authenticated paths   anything else (none implemented yet)
                      requires header: Authorization: Bearer <jwt>
JWT                   HS256, sub=email, 24 h TTL
Password storage      BCrypt
DB                    MySQL 8, db=virtual_office, table=users  (auto-created via JPA)
                      MySQL 8, table=verification_requests     (auto-created via JPA, unused at HTTP layer)
Message broker        RabbitMQ 3 (started via docker-compose, no producers/consumers wired)
```
