# User Service — Backend Summary

A complete walkthrough of every source file, configuration value, and runtime behavior currently in the `user-service` module. Everything below describes what is in the code today.

---

## 1. Purpose

The User Service owns user identity for the Virtual Office platform. It runs on **port 8081**, persists users in **MySQL**, brings up **RabbitMQ** as a sidecar, and exposes JWT-protected REST endpoints for registration, login, profile retrieval, password change, and profile-picture upload/download.

---

## 2. Tech Stack

| Concern         | Choice                                                           |
|-----------------|------------------------------------------------------------------|
| Framework       | Spring Boot **3.4.1** (inherited from parent `backend/pom.xml`)  |
| Language        | Java **21**                                                      |
| Build           | Maven (multi-module; parent at `backend/pom.xml`)                |
| Persistence     | Spring Data JPA + Hibernate                                      |
| Database        | MySQL 8 (database `virtual_office`)                              |
| HTTP            | `spring-boot-starter-web` (Servlet stack, embedded Tomcat)       |
| Security        | `spring-boot-starter-security` + JWT (`io.jsonwebtoken:jjwt:0.11.5`) |
| Messaging       | `spring-boot-starter-amqp` (RabbitMQ on the classpath; no producers/consumers in app code) |
| Compose runtime | `spring-boot-docker-compose` (auto-launches MySQL + RabbitMQ)    |
| Lombok          | `@Getter / @Setter / @Builder / @NoArgsConstructor / @AllArgsConstructor / @RequiredArgsConstructor` |
| Tests           | JUnit 5 via `spring-boot-starter-test`, `spring-security-test`   |

---

## 3. Project Layout

```
user-service/
├── pom.xml
├── README.md
├── docker-compose.yml
├── mvnw / mvnw.cmd                      (Maven wrappers; .mvn lives at backend/)
├── data/                                (auto-created at runtime — MySQL + RabbitMQ volumes)
└── src/
    ├── main/
    │   ├── java/com/virtualoffice/service/user/
    │   │   ├── VirtualOfficeUserApplication.java        ← @SpringBootApplication entry
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   └── UserController.java
    │   │   ├── service/
    │   │   │   ├── AuthService.java
    │   │   │   └── UserService.java
    │   │   ├── repository/
    │   │   │   ├── UserRepository.java
    │   │   │   └── VerificationRequestRepository.java
    │   │   ├── security/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── JwtUtil.java
    │   │   │   ├── JwtAuthFilter.java
    │   │   │   └── CustomUserDetailsService.java
    │   │   ├── dto/
    │   │   │   ├── RegisterRequest.java
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── UpdatePasswordRequest.java
    │   │   │   ├── AuthResponse.java
    │   │   │   └── ApiResponse.java
    │   │   └── domain/
    │   │       ├── entity/
    │   │       │   ├── User.java
    │   │       │   └── VerificationRequest.java
    │   │       └── enumuration/                         ← (sic — package directory is misspelled)
    │   │           ├── AccountStatus.java
    │   │           └── VerificationRequestStatus.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/virtualoffice/service/user/
            └── VirtualOfficeUserApplicationTests.java   ← context-loads test only
```

---

## 4. Maven dependencies ([pom.xml](pom.xml))

| Group : artifact                                                | Scope     |
|------------------------------------------------------------------|-----------|
| `org.springframework.boot:spring-boot-starter-amqp`             | compile   |
| `org.springframework.boot:spring-boot-docker-compose`           | runtime   |
| `org.springframework.boot:spring-boot-starter-data-jpa`         | compile   |
| `org.springframework.boot:spring-boot-starter-security`         | compile   |
| `org.springframework.boot:spring-boot-starter-web`              | compile   |
| `io.jsonwebtoken:jjwt-api:0.11.5`                               | compile   |
| `io.jsonwebtoken:jjwt-impl:0.11.5`                              | runtime   |
| `io.jsonwebtoken:jjwt-jackson:0.11.5`                           | runtime   |
| `org.projectlombok:lombok`                                      | compile (optional) |
| `com.mysql:mysql-connector-j`                                   | runtime   |
| `org.springframework.boot:spring-boot-starter-test`             | test      |
| `org.springframework.security:spring-security-test`             | test      |

The parent `backend/pom.xml` is Spring Boot **3.4.1** with Java **21** and `spring-cloud.version=2024.0.0`.

---

## 5. Configuration ([application.properties](src/main/resources/application.properties))

```properties
server.port=8081
spring.application.name=user-service

# MySQL — auto-managed by docker-compose.yml
spring.datasource.url=jdbc:mysql://localhost:3306/virtual_office
spring.datasource.username=root
spring.datasource.password=rootpassword

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect

# RabbitMQ — auto-managed by docker-compose.yml
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Spring Boot brings docker compose up on startup and tears it down on shutdown
spring.docker.compose.lifecycle-management=start-and-stop

# JWT — 24 hour expiration
jwt.secret=your-very-strong-secret-key-must-be-at-least-32-characters-long
jwt.expiration=86400000
```

- `ddl-auto=update` — Hibernate creates/updates tables on each boot.
- `show-sql=true` — every SQL statement is logged.
- `lifecycle-management=start-and-stop` — Spring Boot calls `docker compose up` at startup and `docker compose down` at shutdown.
- `jwt.secret` and `jwt.expiration` (ms) are read by `JwtUtil`.

---

## 6. Docker Compose ([docker-compose.yml](docker-compose.yml))

```yaml
services:

  mysql:
    image: mysql:8
    container_name: user-mysql
    restart: unless-stopped
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: virtual_office
    volumes:
      - ./data/mysql:/var/lib/mysql
    networks:
      - user-network

  rabbitmq:
    image: rabbitmq:3-management-alpine
    container_name: user-rabbitmq
    restart: unless-stopped
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    volumes:
      - ./data/rabbitmq:/var/lib/rabbitmq
    networks:
      - user-network

networks:
  user-network:
    driver: bridge
```

- **MySQL 8** on host `:3306`, root password `rootpassword`, default schema `virtual_office`. Persists to `./data/mysql`.
- **RabbitMQ 3** with management plugin on `:5672` (AMQP) and `:15672` (web UI), default `guest/guest`. Persists to `./data/rabbitmq`.

---

## 7. Application entry point

[`VirtualOfficeUserApplication`](src/main/java/com/virtualoffice/service/user/VirtualOfficeUserApplication.java)
```java
@SpringBootApplication
public class VirtualOfficeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(VirtualOfficeUserApplication.class, args);
    }
}
```
No additional `@EnableXxx` annotations beyond what `@SpringBootApplication` and `@EnableWebSecurity` (on `SecurityConfig`) bring in.

---

## 8. Domain model

### 8.1 [`User`](src/main/java/com/virtualoffice/service/user/domain/entity/User.java)

```java
@Entity @Table(name = "users")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name",   nullable = false, length = 100)        private String firstName;
    @Column(name = "last_name",    nullable = false, length = 100)        private String lastName;
    @Column(                       nullable = false, length = 100, unique = true)
                                                                          private String email;
    @Column(name = "phone_number",                  length = 20)          private String phoneNumber; // nullable
    @Column(name = "password_hash", nullable = false, length = 255)       private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "is_email_verified", nullable = false)                 private boolean isEmailVerified;
    @Column(name = "is_phone_verified", nullable = false)                 private boolean isPhoneVerified;
    @Column(name = "is_disabled",       nullable = false)                 private boolean isDisabled;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VerificationRequest> verificationRequests = new ArrayList<>();

    @Column(name = "profile_picture", columnDefinition = "LONGBLOB")
    private byte[] profilePicture;

    @Column(name = "profile_picture_type")
    private String profilePictureType;
}
```

- `email` carries a unique index.
- `password` field maps to column `password_hash` and stores a BCrypt hash.
- `accountStatus` defaults to `ACTIVE`; persisted as the enum name (`STRING`).
- `verificationRequests` cascades all operations and removes orphans.
- `profilePicture` is a `LONGBLOB`; `profilePictureType` stores the original Content-Type.

### 8.2 [`VerificationRequest`](src/main/java/com/virtualoffice/service/user/domain/entity/VerificationRequest.java)

```java
@Entity @Table(name = "verification_requests")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class VerificationRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "otp", nullable = false, length = 10)
    private String otp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VerificationRequestStatus status = VerificationRequestStatus.PENDING;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

The entity is mapped and its table is created by Hibernate, but no controller, service, or repository call writes or reads it today.

### 8.3 Enumerations

[`AccountStatus`](src/main/java/com/virtualoffice/service/user/domain/enumuration/AccountStatus.java) — `ACTIVE`, `INACTIVE`, `PENDING_REPORT_REVIEW`, `SUSPENDED`.

[`VerificationRequestStatus`](src/main/java/com/virtualoffice/service/user/domain/enumuration/VerificationRequestStatus.java) — `PENDING`, `APPROVED`.

---

## 9. Repositories

### [`UserRepository`](src/main/java/com/virtualoffice/service/user/repository/UserRepository.java)
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

### [`VerificationRequestRepository`](src/main/java/com/virtualoffice/service/user/repository/VerificationRequestRepository.java)
```java
public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {
    Optional<VerificationRequest> findByUserIdAndStatus(Long userId, VerificationRequestStatus status);
}
```
Has no callers in application code today.

---

## 10. DTOs

### Request DTOs

[`RegisterRequest`](src/main/java/com/virtualoffice/service/user/dto/RegisterRequest.java)
```java
@Getter @Setter
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phoneNumber;
}
```

[`LoginRequest`](src/main/java/com/virtualoffice/service/user/dto/LoginRequest.java)
```java
@Getter @Setter
public class LoginRequest {
    private String email;
    private String password;
}
```

[`UpdatePasswordRequest`](src/main/java/com/virtualoffice/service/user/dto/UpdatePasswordRequest.java)
```java
@Getter @Setter
public class UpdatePasswordRequest {
    private String oldPassword;
    private String newPassword;
}
```

None of the request DTOs use `@Valid` / Bean-Validation annotations.

### Response DTOs

[`AuthResponse`](src/main/java/com/virtualoffice/service/user/dto/AuthResponse.java)
```java
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String firstName;
    private String lastName;
    private String errorMessage;

    public static AuthResponse withError(String errorMessage) { /* sets only errorMessage */ }
}
```

[`ApiResponse`](src/main/java/com/virtualoffice/service/user/dto/ApiResponse.java)
```java
@Getter
public class ApiResponse {
    private String status;
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public ApiResponse(String message) { this.status = message; }

    public ApiResponse add(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getFields() { return fields; }
}
```

`@JsonAnyGetter` flattens the `fields` map directly into the JSON object alongside `status` (e.g. `{ "status": "...", "id": 1, "email": "..." }`).

---

## 11. Security

### 11.1 [`SecurityConfig`](src/main/java/com/virtualoffice/service/user/security/SecurityConfig.java)

```java
@Configuration @EnableWebSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain filterChain(HttpSecurity http) {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated())
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c) {
        return c.getAuthenticationManager();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

- CSRF disabled.
- Stateless session policy — no `JSESSIONID`, no server-side session.
- `/api/auth/**` is public; **everything else requires authentication**.
- `DaoAuthenticationProvider` uses `CustomUserDetailsService` and BCrypt.
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`.

### 11.2 [`JwtAuthFilter`](src/main/java/com/virtualoffice/service/user/security/JwtAuthFilter.java) (`extends OncePerRequestFilter`)

- `shouldNotFilter` returns `true` for any request whose `getServletPath()` starts with `/api/auth/`.
- For all other paths:
  1. Read `Authorization` header.
  2. If missing or not starting with `Bearer `, the filter chain continues without authentication.
  3. Otherwise extract the token (skip `"Bearer "`), call `jwtUtil.extractEmail(token)`.
  4. If the email is non-null and the security context isn't already authenticated, load the user via `CustomUserDetailsService.loadUserByUsername(email)`.
  5. If `jwtUtil.isTokenValid(token, userDetails)`, build a `UsernamePasswordAuthenticationToken` (with the user's authorities — empty list, see §11.4) and set it on `SecurityContextHolder`.
- The filter never writes a response itself. Rejection happens in the authorization filter further down the chain.

### 11.3 [`JwtUtil`](src/main/java/com/virtualoffice/service/user/security/JwtUtil.java)

```java
@Component
public class JwtUtil {
    @Value("${jwt.secret}")     private String secret;
    @Value("${jwt.expiration}") private long expiration;

    private Key getSigningKey() { return Keys.hmacShaKeyFor(secret.getBytes(UTF_8)); }

    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) { return claims.getSubject(); }
    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractEmail(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }
    private boolean isTokenExpired(String token)        { return claims.getExpiration().before(new Date()); }
    private Claims  extractAllClaims(String token)      { ... Jwts.parserBuilder().setSigningKey(...).parseClaimsJws(token).getBody(); }
}
```

- HS256, signed with `jwt.secret` UTF-8 bytes.
- Token claims: `sub` (email), `iat`, `exp`.
- Validation: signature parses, email matches `userDetails.getUsername()`, token not expired.

### 11.4 [`CustomUserDetailsService`](src/main/java/com/virtualoffice/service/user/security/CustomUserDetailsService.java) (`implements UserDetailsService`)

```java
public UserDetails loadUserByUsername(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));

    if (user.isDisabled()) {
        throw new UsernameNotFoundException("User account is disabled: " + email);
    }

    return org.springframework.security.core.userdetails.User
        .withUsername(user.getEmail())
        .password(user.getPassword())
        .accountExpired(false)
        .accountLocked(user.getAccountStatus() == AccountStatus.SUSPENDED)
        .credentialsExpired(false)
        .disabled(user.isDisabled())
        .build();
}
```

- Disabled users surface as `UsernameNotFoundException`.
- `SUSPENDED` users surface as account-locked (`DaoAuthenticationProvider` then throws `LockedException`).
- The returned `UserDetails` has **no granted authorities** — `.authorities(...)` is never called.

---

## 12. Service layer

### 12.1 [`AuthService`](src/main/java/com/virtualoffice/service/user/service/AuthService.java)

Constructor-injected: `UserRepository`, `PasswordEncoder`, `JwtUtil`, `AuthenticationManager`.

`register(RegisterRequest request)`:
1. If `userRepository.existsByEmail(request.getEmail())` → return `AuthResponse.withError("Such E-mail Already Exist")`.
2. Build a `User` with `passwordEncoder.encode(request.getPassword())`, `accountStatus = ACTIVE`, the three boolean flags `false`. (Note: `verificationRequests`, `profilePicture`, `profilePictureType` are not set by the builder here.)
3. `userRepository.save(user)`.
4. `String token = jwtUtil.generateToken(user.getEmail())`.
5. Return `new AuthResponse(token, email, firstName, lastName, "None")`.

`login(LoginRequest request)`:
1. `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))`. On failure (`BadCredentialsException`, `LockedException`, `DisabledException`, etc.), the exception propagates uncaught — Spring Security's default handler emits the response (typically `403 Forbidden`, empty body).
2. Look up the user by email; if absent, return `AuthResponse.withError("User Not Found")`.
3. Generate a JWT and return `new AuthResponse(token, email, firstName, lastName, "None")`.

### 12.2 [`UserService`](src/main/java/com/virtualoffice/service/user/service/UserService.java)

Constructor-injected: `UserRepository`, `PasswordEncoder`.

`getCurrentUser()` (private):
```java
String email = SecurityContextHolder.getContext().getAuthentication().getName();
return userRepository.findByEmail(email)
    .orElseThrow(() -> new RuntimeException("User not found"));
```

`getUserData()` → `ResponseEntity<ApiResponse>`: 200 OK with an `ApiResponse("User retrieved")` augmented with `id`, `firstName`, `lastName`, `email`, `phoneNumber`, `accountStatus`, `isEmailVerified`, `isPhoneVerified`.

`uploadPhoto(MultipartFile file)` → `ResponseEntity<ApiResponse>`:
1. If `file.getContentType()` is null or doesn't start with `image/` → 400 with `ApiResponse("Failed").add("Error", "Invalid Image")`.
2. If `file.getSize() > 10 * (1 << 6)` (= 640 bytes) → 400 with `ApiResponse("Failed").add("Error", "Image size is too large")`. (The literal expression is `10 * (1 << 6)`; the source comment says "10MB" but the math evaluates to 640 bytes.)
3. Set `user.profilePicture = file.getBytes()`. On `IOException` → 400 with `"Couldn't read the file"`.
4. Set `user.profilePictureType = contentType`, save user, return 200 with `ApiResponse("succeeded")`.

`updatePassword(UpdatePasswordRequest request)` → `ResponseEntity<ApiResponse>`:
1. Load current user. If `passwordEncoder.matches(oldPassword, user.getPassword())` is `false` → 400 with `ApiResponse("Failed").add("Error", "Old password does not match")`.
2. Otherwise set `user.password = passwordEncoder.encode(newPassword)`, save, return 200 with `ApiResponse("succeeded")`.

`getCurrentUserProfile()` → returns the `User` entity directly (used by `UserController.getPhoto` to access `profilePicture` / `profilePictureType` byte arrays).

---

## 13. Controllers

### 13.1 [`AuthController`](src/main/java/com/virtualoffice/service/user/controller/AuthController.java) — base path `/api/auth`

```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
    AuthResponse r = authService.register(request);
    if (!r.getErrorMessage().equals("None")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
    }
    return ResponseEntity.ok(r);
}

@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    AuthResponse r = authService.login(request);
    if (!r.getErrorMessage().equals("None")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
    }
    return ResponseEntity.ok(r);
}
```

- Both methods return `200 OK` when the service produces `errorMessage = "None"`.
- Otherwise return `403 Forbidden` with the `AuthResponse` body.
- No `@Valid` annotations.

### 13.2 [`UserController`](src/main/java/com/virtualoffice/service/user/controller/UserController.java) — base path `/api/users`

```java
@GetMapping("/me")
public ResponseEntity<ApiResponse> getMe() { return userService.getUserData(); }

@PutMapping("/me/password")
public ResponseEntity<ApiResponse> updatePassword(@RequestBody UpdatePasswordRequest request) {
    return userService.updatePassword(request);
}

@PostMapping("/me/photo")
public ResponseEntity<ApiResponse> uploadPhoto(@RequestParam("file") MultipartFile file) {
    return userService.uploadPhoto(file);
}

@GetMapping("/me/photo")
public ResponseEntity<byte[]> getPhoto() {
    User user = userService.getCurrentUserProfile();
    if (user.getProfilePicture() == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(user.getProfilePictureType()))
        .body(user.getProfilePicture());
}
```

- All four endpoints require an authenticated request (no `permitAll` for `/api/users/**`).
- `GET /me/photo` returns the raw byte array with `Content-Type` set from `profilePictureType`, or `404 Not Found` (empty body) when no picture is stored.

---

## 14. Endpoint summary

| Method | Path | Auth | Body in | Body out (success) | Failure status / body |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | public | `RegisterRequest` | 200 `AuthResponse(errorMessage="None")` | 403 `AuthResponse(errorMessage="Such E-mail Already Exist")` |
| POST | `/api/auth/login` | public | `LoginRequest` | 200 `AuthResponse(errorMessage="None")` | 403 `AuthResponse(errorMessage="User Not Found")` (defensive); 403 empty body when `authenticationManager` throws |
| GET | `/api/users/me` | Bearer JWT | — | 200 `ApiResponse` with profile fields | 403 empty (no token) |
| PUT | `/api/users/me/password` | Bearer JWT | `UpdatePasswordRequest` | 200 `ApiResponse(status="succeeded")` | 400 `ApiResponse(status="Failed", Error="Old password does not match")` |
| POST | `/api/users/me/photo` | Bearer JWT | multipart `file` | 200 `ApiResponse(status="succeeded")` | 400 `ApiResponse(status="Failed", Error="Invalid Image" \| "Image size is too large" \| "Couldn't read the file")` |
| GET | `/api/users/me/photo` | Bearer JWT | — | 200 raw bytes, `Content-Type` from `profilePictureType` | 404 empty (no picture stored) |

---

## 15. Request / response flow

### Register (success)
```
POST /api/auth/register
 → JwtAuthFilter.shouldNotFilter == true (path /api/auth/**)
 → AuthorizationFilter: permitAll
 → AuthController.register
 → AuthService.register
     existsByEmail? no → save user, generateToken, return AuthResponse(...)
 → 200 AuthResponse
```

### Register (duplicate email)
```
POST /api/auth/register
 → AuthService.register → existsByEmail? yes → AuthResponse.withError("Such E-mail Already Exist")
 → AuthController returns 403 with the same body
```

### Login (success)
```
POST /api/auth/login
 → AuthService.login
     authenticationManager.authenticate(...)        ← BCrypt match against users.password_hash
     userRepository.findByEmail(...)
     jwtUtil.generateToken(email)
     return AuthResponse(...)
 → 200 AuthResponse
```

### Login (bad credentials / disabled / suspended)
```
POST /api/auth/login
 → AuthService.login
     authenticationManager.authenticate(...) throws AuthenticationException
 → uncaught (no @RestControllerAdvice) → Spring Security default handler → 403 empty
```

### Authenticated request
```
GET /api/users/me
 → JwtAuthFilter
     no Authorization header → context unauthenticated
     "Bearer <jwt>"
       extractEmail → loadUserByUsername → isTokenValid
       if valid → SecurityContextHolder.setAuthentication(...)
 → AuthorizationFilter: anyRequest().authenticated()
     authenticated → controller; not authenticated → 403 empty
 → UserController.getMe → UserService.getUserData
     SecurityContextHolder.getContext().getAuthentication().getName() → email
     userRepository.findByEmail(email) → ApiResponse with profile fields
 → 200 ApiResponse
```

---

## 16. Database schema (effective, generated by Hibernate)

```sql
CREATE TABLE users (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    first_name               VARCHAR(100) NOT NULL,
    last_name                VARCHAR(100) NOT NULL,
    email                    VARCHAR(100) NOT NULL UNIQUE,
    phone_number             VARCHAR(20),
    password_hash            VARCHAR(255) NOT NULL,
    account_status           VARCHAR(255) NOT NULL,        -- enum stored as STRING
    is_email_verified        BIT NOT NULL,
    is_phone_verified        BIT NOT NULL,
    is_disabled              BIT NOT NULL,
    profile_picture          LONGBLOB,
    profile_picture_type     VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE verification_requests (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    otp         VARCHAR(10) NOT NULL,
    status      VARCHAR(255) NOT NULL,                     -- enum stored as STRING
    created_at  DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    user_id     BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_verification_requests_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

(Booleans are rendered as `BIT(1)` in MySQL via Hibernate defaults.)

---

## 17. Tests

[`VirtualOfficeUserApplicationTests`](src/test/java/com/virtualoffice/service/user/VirtualOfficeUserApplicationTests.java)
```java
@SpringBootTest
class VirtualOfficeUserApplicationTests {
    @Test void contextLoads() {}
}
```

`@SpringBootTest` boots the full application context, requiring a reachable MySQL on `localhost:3306` and RabbitMQ on `localhost:5672` (provided by Docker Compose when running).

Run from the repo:
```bash
./mvnw -pl user-service test
```

---

## 18. How to run

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

Spring Boot runs `docker compose up` for `user-service/docker-compose.yml`, waits until MySQL and RabbitMQ are healthy, runs Hibernate's schema update, then starts Tomcat on `:8081`. Ctrl+C runs `docker compose down`.

Port 3306 (MySQL) and 5672/15672 (RabbitMQ) must be free on the host or container start fails.

---

## 19. Quick reference

```
Port              : 8081
Spring Boot       : 3.4.1
Java              : 21
DB                : MySQL 8 — jdbc:mysql://localhost:3306/virtual_office (root / rootpassword)
Broker            : RabbitMQ 3 — localhost:5672 (guest / guest), management UI :15672
Entry point       : com.virtualoffice.service.user.VirtualOfficeUserApplication
Entities          : User, VerificationRequest
Enums             : AccountStatus { ACTIVE, INACTIVE, PENDING_REPORT_REVIEW, SUSPENDED }
                    VerificationRequestStatus { PENDING, APPROVED }
Repositories      : UserRepository (findByEmail, existsByEmail)
                    VerificationRequestRepository (findByUserIdAndStatus)  [unused]
DTOs (in)         : RegisterRequest, LoginRequest, UpdatePasswordRequest
DTOs (out)        : AuthResponse, ApiResponse (dynamic, @JsonAnyGetter)
Security          : Stateless, CSRF off, BCrypt, JWT HS256 (24 h), no roles
Public endpoints  : POST /api/auth/register, POST /api/auth/login
Authenticated     : GET  /api/users/me
                    PUT  /api/users/me/password
                    POST /api/users/me/photo   (multipart "file", image/*, ≤ 640 bytes)
                    GET  /api/users/me/photo
Tests             : 1 (contextLoads)
```
