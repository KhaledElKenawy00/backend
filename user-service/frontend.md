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
1. Spring Boot reads [`docker-compose.yml`](docker-compose.yml), runs `docker compose up`, waits until MySQL and RabbitMQ are healthy.
2. Hibernate creates/updates the `users` and `verification_requests` tables on first boot (`ddl-auto=update`).
3. Tomcat listens on **`http://localhost:8081`**.
4. **Ctrl+C** → Spring Boot runs `docker compose down` automatically (`spring.docker.compose.lifecycle-management=start-and-stop`).

### Caveats
- **Port 3306 must be free.** A locally installed MySQL Windows service will block the container from binding. From an elevated PowerShell:
  ```powershell
  Stop-Service MySQL80 -Force
  Set-Service MySQL80 -StartupType Manual
  ```
- **Docker Desktop must already be running** before `mvnw spring-boot:run`.
- Persistent data lives under `user-service/data/mysql` and `user-service/data/rabbitmq`. Delete those folders to reset.

---

## 2. URLs and ports

| Item | Value |
|---|---|
| Base URL (local) | `http://localhost:8081` |
| Auth endpoints | `/api/auth/**` (public) |
| User endpoints | `/api/users/**` (require JWT) |
| MySQL | `localhost:3306`, `root` / `rootpassword`, db `virtual_office` |
| RabbitMQ AMQP | `localhost:5672` (`guest` / `guest`) |
| RabbitMQ management UI | `http://localhost:15672` |

---

## 3. Authentication

### How the backend verifies you
- The User Service issues **JWTs (HS256)** signed with `jwt.secret`. The token's `sub` claim is the user's email; `iat` and `exp` are present; **no other claims**. TTL is 24 hours (`jwt.expiration=86400000` ms).
- For every request **outside** `/api/auth/**`:
  1. Spring runs `JwtAuthFilter` first.
  2. The filter reads the `Authorization` header. If it's missing or doesn't start with `Bearer `, the filter does nothing.
  3. Otherwise it strips the `Bearer ` prefix, extracts the email from the token, loads the user by email, validates the token (signature + email match + not expired), and authenticates the request.
  4. After the filter, Spring Security's authorization rule (`anyRequest().authenticated()`) responds **`403 Forbidden`** with empty body when no authentication is present on the context.
- The session is **stateless** — no cookies, every request must carry the token in `Authorization: Bearer <jwt>`.
- **CSRF is disabled.**

### Account-status checks during login
([`CustomUserDetailsService`](src/main/java/com/virtualoffice/service/user/security/CustomUserDetailsService.java))
- `isDisabled = true` → login fails (treated as `UsernameNotFoundException`).
- `accountStatus = SUSPENDED` → login fails (Spring marks the account as locked).
- `ACTIVE`, `INACTIVE`, `PENDING_REPORT_REVIEW` are treated as logable-in.
- The returned `UserDetails` has **no granted authorities/roles**.

---

## 4. Public endpoints — `/api/auth/**`

These two endpoints are accepted without a token. Both are implemented in [`AuthController`](src/main/java/com/virtualoffice/service/user/controller/AuthController.java).

The controller looks at `AuthResponse.errorMessage`:
- `"None"` → returns **`200 OK`**.
- anything else → returns **`403 Forbidden`** with the same JSON body.

### 4.1 `POST /api/auth/register`

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
- The DTO has no validation annotations — fields are passed through to the entity.
- Entity column constraints: `firstName` and `lastName` ≤ 100 chars and required; `email` ≤ 100, required, **unique**; `password` required; `phoneNumber` ≤ 20, optional.
- New users are created with `accountStatus = ACTIVE`, `isEmailVerified = false`, `isPhoneVerified = false`, `isDisabled = false`. Password is BCrypt-hashed before storage.

**Response body** (`AuthResponse`):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "email": "jane@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "errorMessage": "None"
}
```

| Outcome | HTTP | `errorMessage` | Other fields |
|---|---|---|---|
| Success | `200` | `"None"` | populated |
| Email already in use | `403` | `"Such E-mail Already Exist"` | all `null` |

### 4.2 `POST /api/auth/login`

**Request body** (`LoginRequest`):
```json
{ "email": "jane@example.com", "password": "hello12345" }
```

**Response body** is the same `AuthResponse` shape as register.

| Outcome | HTTP | `errorMessage` | Body |
|---|---|---|---|
| Success | `200` | `"None"` | full `AuthResponse` |
| Defensive branch — auth passed but `userRepository.findByEmail` returned empty | `403` | `"User Not Found"` | other fields `null` |
| Wrong password / disabled user / suspended user / unknown email | — | — | `authenticationManager.authenticate` throws an `AuthenticationException`. There is **no** `@RestControllerAdvice` in the codebase, so Spring Security's default behavior produces an empty-body response (typically `403 Forbidden`). |

---

## 5. Authenticated endpoints — `/api/users/**`

All routes below require `Authorization: Bearer <jwt>`. Without it Spring returns **`403 Forbidden`** with an empty body. They are implemented in [`UserController`](src/main/java/com/virtualoffice/service/user/controller/UserController.java) and back onto [`UserService`](src/main/java/com/virtualoffice/service/user/service/UserService.java).

The "current user" is resolved as:
```java
SecurityContextHolder.getContext().getAuthentication().getName()  // -> email
userRepository.findByEmail(email).orElseThrow(new RuntimeException("User not found"))
```
A failure here propagates as an unhandled `RuntimeException` (no exception handler is registered).

### 5.1 `GET /api/users/me`

Returns the current user's profile.

**Response `200 OK`** (`ApiResponse`, dynamic JSON):
```json
{
  "status": "User retrieved",
  "id": 1,
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "phoneNumber": "+1-555-0100",
  "accountStatus": "ACTIVE",
  "isEmailVerified": false,
  "isPhoneVerified": false
}
```

`accountStatus` is one of `ACTIVE`, `INACTIVE`, `PENDING_REPORT_REVIEW`, `SUSPENDED`.
`isDisabled` and the user's `password`, `profilePicture`, `verificationRequests` are **not** included.

### 5.2 `PUT /api/users/me/password`

Changes the current user's password. The old password is verified against the stored BCrypt hash; the new password is BCrypt-hashed and saved.

**Request body** (`UpdatePasswordRequest`):
```json
{ "oldPassword": "hello12345", "newPassword": "newP4ssw0rd" }
```

**Responses** (both bodies use `ApiResponse`):

| Outcome | HTTP | Body |
|---|---|---|
| Success | `200` | `{ "status": "succeeded" }` |
| Old password does not match | `400` | `{ "status": "Failed", "Error": "Old password does not match" }` |

### 5.3 `POST /api/users/me/photo`

Uploads a profile picture for the current user. Stored as a `LONGBLOB` in the `users` table along with the original Content-Type.

**Request**: `multipart/form-data` with a single part named `file`.

```
POST /api/users/me/photo
Content-Type: multipart/form-data; boundary=...

<binary image bytes under field "file">
```

Validation performed by `UserService.uploadPhoto`:
- The part's `Content-Type` must start with `image/`.
- The file's `getSize()` must be **≤ `10 * (1 << 6)` = 640 bytes** (this is the literal expression in the code).

**Responses** (`ApiResponse`):

| Outcome | HTTP | Body |
|---|---|---|
| Success | `200` | `{ "status": "succeeded" }` |
| Content-Type missing or doesn't start with `image/` | `400` | `{ "status": "Failed", "Error": "Invalid Image" }` |
| File size > 640 bytes | `400` | `{ "status": "Failed", "Error": "Image size is too large" }` |
| `IOException` while reading the file | `400` | `{ "status": "Failed", "Error": "Couldn't read the file" }` |

### 5.4 `GET /api/users/me/photo`

Returns the raw bytes of the current user's profile picture.

| Outcome | HTTP | Body |
|---|---|---|
| Picture stored | `200`, `Content-Type` = the stored `profilePictureType` | raw image bytes |
| `profilePicture` is `null` | `404` | empty |

---

## 6. Data Shapes

### Request DTOs

```ts
// RegisterRequest
{
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phoneNumber?: string;
}

// LoginRequest
{
  email: string;
  password: string;
}

// UpdatePasswordRequest
{
  oldPassword: string;
  newPassword: string;
}
```

### Response DTOs

```ts
// AuthResponse (returned by /api/auth/register and /api/auth/login)
{
  token: string | null;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  errorMessage: string;          // "None" on success, otherwise an error label
}
```

`AuthResponse.errorMessage` strings produced by the backend:
- `"None"` — success.
- `"Such E-mail Already Exist"` — register, duplicate email (HTTP 403).
- `"User Not Found"` — login, defensive branch (HTTP 403).

```ts
// ApiResponse (returned by all /api/users/** endpoints that return JSON)
{
  status: string;                // free-form label
  // ...arbitrary additional keys (flattened via @JsonAnyGetter)
}
```

The `add(key, value)` calls inside services determine the additional fields. Examples produced today:
- `GET /me` → `id`, `firstName`, `lastName`, `email`, `phoneNumber`, `accountStatus`, `isEmailVerified`, `isPhoneVerified`.
- Any failure path (`status: "Failed"`) → adds an `"Error"` key with a human-readable string.

---

## 7. JWT details

A token returned by register/login decodes to:
- Header: `{ "alg": "HS256" }`
- Payload:
  - `sub` — the user's email
  - `iat` — issued-at (seconds since epoch)
  - `exp` — `iat` + 24 h
- Signature: HMAC-SHA256 using `jwt.secret` (UTF-8 bytes).

The token does **not** carry the user id, role, name, or any other claim besides the email.

---

## 8. End-to-end frontend recipe

```ts
const BASE = "http://localhost:8081";

// Register
const reg = await fetch(`${BASE}/api/auth/register`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    firstName: "Jane", lastName: "Doe",
    email: "jane@example.com", password: "hello12345"
  })
}).then(r => r.json());
if (reg.errorMessage !== "None") throw new Error(reg.errorMessage);

// Login
const lg = await fetch(`${BASE}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: "jane@example.com", password: "hello12345" })
});
if (lg.status !== 200) throw new Error("login failed");
const { token } = await lg.json();

const auth = { "Authorization": `Bearer ${token}` };

// Me
const me = await fetch(`${BASE}/api/users/me`, { headers: auth }).then(r => r.json());

// Update password
await fetch(`${BASE}/api/users/me/password`, {
  method: "PUT",
  headers: { ...auth, "Content-Type": "application/json" },
  body: JSON.stringify({ oldPassword: "hello12345", newPassword: "newP4ssw0rd" })
});

// Upload photo (must be ≤ 640 bytes per current server validation)
const fd = new FormData();
fd.append("file", fileBlob);  // image/* mime
await fetch(`${BASE}/api/users/me/photo`, { method: "POST", headers: auth, body: fd });

// Read photo
const photo = await fetch(`${BASE}/api/users/me/photo`, { headers: auth });
if (photo.status === 200) {
  const blob = await photo.blob();   // Content-Type comes from server
}
```

---

## 9. Quick reference

```
Base URL        http://localhost:8081

Public          POST /api/auth/register          -> 200 AuthResponse / 403 AuthResponse(errorMessage)
                POST /api/auth/login             -> 200 AuthResponse / 403 (empty or AuthResponse)

Authenticated   GET  /api/users/me               -> 200 ApiResponse with profile fields
(Bearer JWT)    PUT  /api/users/me/password      -> 200 / 400 ApiResponse
                POST /api/users/me/photo         -> 200 / 400 ApiResponse  (multipart "file", image/* , ≤ 640 bytes)
                GET  /api/users/me/photo         -> 200 raw bytes (Content-Type=stored type) / 404 empty

JWT             HS256, sub=email, 24 h TTL
Password        BCrypt
Account flags   accountStatus, isEmailVerified, isPhoneVerified, isDisabled
```
