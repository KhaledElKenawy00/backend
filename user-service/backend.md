# User Service — Backend Summary

A complete walkthrough of every source file, configuration value, and runtime behavior currently in the `user-service` module.

---

## 1. Purpose

The User Service owns user identity and authentication for the Virtual Office platform. It runs on **port 8081**, persists users in **MySQL**, brings up **RabbitMQ** as a sidecar (not yet used by application code), and issues **JWTs** for authenticated sessions.

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
| Messaging       | `spring-boot-starter-amqp` (RabbitMQ — present, no app code uses it yet) |
| Compose runtime | `spring-boot-docker-compose` (auto-launches MySQL + RabbitMQ)    |
| Lombok          | `@Getter / @Setter / @Builder / @NoArgsConstructor / @AllArgsConstructor / @RequiredArgsConstructor` |
| Shared code     | `com.virtualoffice:shared-library` Maven module                  |
| Tests           | JUnit 5 via `spring-boot-starter-test`, `spring-security-test`   |

---

## 3. Project Layout

```
user-service/
├── pom.xml
├── README.md
├── docker-compose.yml
├── mvnw / mvnw.cmd                       (Maven wrappers; .mvn lives at backend/)
├── data/                                 (auto-created at runtime — MySQL + RabbitMQ volumes)
└── src/
    ├── main/
    │   ├── java/com/virtualoffice/service/user/
    │   │   ├── VirtualOfficeUserApplication.java        ← @SpringBootApplication entry
    │   │   ├── controller/
    │   │   │   └── AuthController.java
    │   │   ├── service/
    │   │   │   └── AuthService.java
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
    │   │   │   └── AuthResponse.java
    │   │   └── domain/
    │   │       ├── entity/
    │   │       │   ├── User.java
    │   │       │   └── VerificationRequest.java
    │   │       └── enumuration/                          ← (sic — package name is misspelled)
    │   │           ├── AccountStatus.java
    │   │           └── VerificationRequestStatus.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/virtualoffice/service/user/
            └── VirtualOfficeUserApplicationTests.java   ← context-loads test only
```

The package directory is named `enumuration` (sic) — the typo is consistent across imports, so the code compiles.

---

## 4. Maven Dependencies ([pom.xml](pom.xml))

Direct dependencies declared by `user-service`:

| Group : artifact                                                | Scope     | Purpose                                              |
|------------------------------------------------------------------|-----------|------------------------------------------------------|
| `org.springframework.boot:spring-boot-starter-amqp`             | compile   | RabbitMQ client (not used by application code yet)   |
| `org.springframework.boot:spring-boot-docker-compose`           | runtime   | Auto-runs `docker compose up` on boot                |
| `org.springframework.boot:spring-boot-starter-data-jpa`         | compile   | JPA + Hibernate                                      |
| `org.springframework.boot:spring-boot-starter-security`         | compile   | Spring Security 6.x                                  |
| `org.springframework.boot:spring-boot-starter-web`              | compile   | Tomcat + MVC                                         |
| `io.jsonwebtoken:jjwt-api:0.11.5`                               | compile   | JWT API                                              |
| `io.jsonwebtoken:jjwt-impl:0.11.5`                              | runtime   | JWT impl                                             |
| `io.jsonwebtoken:jjwt-jackson:0.11.5`                           | runtime   | JWT Jackson serializer                               |
| `org.projectlombok:lombok`                                      | compile (optional) | Boilerplate generation                       |
| `com.mysql:mysql-connector-j`                                   | runtime   | MySQL JDBC driver                                    |
| `org.springframework.boot:spring-boot-starter-test`             | test      | JUnit 5, Mockito, AssertJ, MockMvc                   |
| `org.springframework.security:spring-security-test`             | test      | `@WithMockUser`, etc.                                |

The parent `backend/pom.xml` is Spring Boot **3.4.1** with Java **21** and `spring-cloud.version=2024.0.0`. The `shared-library` Maven module is referenced through the parent's dependency-management section (no direct `<dependency>` declared here today).

---

## 5. Configuration ([application.properties](src/main/resources/application.properties))

```properties
server.port=8081
spring.application.name=user-service

# MySQL — auto-managed by docker-compose.yml at startup
spring.datasource.url=jdbc:mysql://localhost:3306/virtual_office
spring.datasource.username=root
spring.datasource.password=rootpassword

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect

# RabbitMQ — auto-managed by docker-compose.yml at startup
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

Behavior:
- `ddl-auto=update` — Hibernate auto-creates / updates tables on boot.
- `show-sql=true` — every SQL statement is logged.
- `lifecycle-management=start-and-stop` — Spring Boot calls `docker compose up` at startup and `docker compose down` at shutdown.
- `jwt.secret` and `jwt.expiration` are read by `JwtUtil`.

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

- **MySQL 8** on host port `3306`, root password `rootpassword`, default schema `virtual_office`. Persists to `./data/mysql`.
- **RabbitMQ 3** with the management plugin on `5672` (AMQP) and `15672` (web UI), default `guest/guest`. Persists to `./data/rabbitmq`.
- Containers are named `user-mysql` and `user-rabbitmq` and share the bridge network `user-network`.

---

## 7. Application Entry Point

[`VirtualOfficeUserApplication`](src/main/java/com/virtualoffice/service/user/VirtualOfficeUserApplication.java)
```java
@SpringBootApplication
public class VirtualOfficeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(VirtualOfficeUserApplication.class, args);
    }
}
```
No additional `@EnableXxx` annotations — `@EnableJpaRepositories`, `@EnableWebSecurity`, `@EnableJpaAuditing`, etc. are either auto-configured by Spring Boot or applied on individual `@Configuration` classes (see `SecurityConfig`).

---

## 8. Domain Model

### 8.1 [`User`](src/main/java/com/virtualoffice/service/user/domain/entity/User.java)

```java
@Entity
@Table(name = "users")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "first_name",   nullable = false, length = 100)        private String firstName;
    @Column(name = "last_name",    nullable = false, length = 100)        private String lastName;
    @Column(                       nullable = false, length = 100, unique = true)
                                                                          private String email;
    @Column(name = "phone_number",                  length = 20)          private String phoneNumber; // nullable
    @Column(name = "password_hash", nullable = false, length = 255)       private String password;

    @Column(name = "account_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "is_email_verified",        nullable = false)          private boolean isEmailVerified;
    @Column(name = "is_phone_number_verified", nullable = false)          private boolean isPhoneVerified;
    @Column(name = "is_disabled",              nullable = false)          private boolean isDisabled;
}
```

- The Java field is `password`; the column is `password_hash`. Stored as a BCrypt hash.
- `email` carries a unique index.
- `accountStatus` defaults to `ACTIVE` at object construction; persisted as a string.

### 8.2 [`VerificationRequest`](src/main/java/com/virtualoffice/service/user/domain/entity/VerificationRequest.java)

```java
@Entity
@Table(name = "verification_requests")
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

    @Column(name = "created_at", nullable = false)  private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)  private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

- `otp` is a string, max 10 chars (preserves leading zeros).
- `status` is `PENDING` by default.
- `user_id` foreign key is required and lazily fetched.
- The entity is never written or read by application code today; only the table is created.

### 8.3 Enumerations

[`AccountStatus`](src/main/java/com/virtualoffice/service/user/domain/enumuration/AccountStatus.java)
```java
public enum AccountStatus { ACTIVE, INACTIVE, PENDING_REPORT_REVIEW, SUSPENDED }
```

[`VerificationRequestStatus`](src/main/java/com/virtualoffice/service/user/domain/enumuration/VerificationRequestStatus.java)
```java
public enum VerificationRequestStatus { PENDING, APPROVED }
```

---

## 9. Repositories

### 9.1 [`UserRepository`](src/main/java/com/virtualoffice/service/user/repository/UserRepository.java)
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```
Two derived queries: lookup by email and existence check.

### 9.2 [`VerificationRequestRepository`](src/main/java/com/virtualoffice/service/user/repository/VerificationRequestRepository.java)
```java
public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {
    Optional<VerificationRequest> findByUserIdAndStatus(Long userId, VerificationRequestStatus status);
}
```
One derived query traversing the `user.id` association. Currently has no callers in application code.

---

## 10. DTOs

### 10.1 [`RegisterRequest`](src/main/java/com/virtualoffice/service/user/dto/RegisterRequest.java)
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
Plain DTO. **No bean-validation annotations** — values flow straight to the entity.

### 10.2 [`LoginRequest`](src/main/java/com/virtualoffice/service/user/dto/LoginRequest.java)
```java
@Getter @Setter
public class LoginRequest {
    private String email;
    private String password;
}
```

### 10.3 [`AuthResponse`](src/main/java/com/virtualoffice/service/user/dto/AuthResponse.java)
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
- Always serialized as JSON in responses.
- On success, `errorMessage` is the literal string `"None"`.
- `withError(...)` creates an instance with everything `null` except `errorMessage`.

---

## 11. Security

### 11.1 [`SecurityConfig`](src/main/java/com/virtualoffice/service/user/security/SecurityConfig.java)

```java
@Configuration
@EnableWebSecurity
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

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c) { return c.getAuthenticationManager(); }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

Key behavior:
- **CSRF disabled.**
- **Stateless session policy** — no `JSESSIONID`, no server-side session.
- `/api/auth/**` is public; **everything else requires authentication**.
- Authentication is delegated to a `DaoAuthenticationProvider` that uses `CustomUserDetailsService` and **BCrypt** for password matching.
- The JWT filter runs **before** `UsernamePasswordAuthenticationFilter`.

### 11.2 [`JwtAuthFilter`](src/main/java/com/virtualoffice/service/user/security/JwtAuthFilter.java) (extends `OncePerRequestFilter`)

- `shouldNotFilter` returns `true` for any path starting with `/api/auth/`, so register and login skip the filter entirely.
- For all other paths:
  1. Reads `Authorization` header.
  2. If missing or not starting with `Bearer `, the filter chain continues without setting authentication.
  3. Otherwise extracts the token (drops `Bearer `), uses `JwtUtil.extractEmail` to get the email, looks the user up via `CustomUserDetailsService`, and if `JwtUtil.isTokenValid(...)` returns true, builds a `UsernamePasswordAuthenticationToken` and stores it on `SecurityContextHolder`.
- The filter never explicitly writes a `401` / `403`. Rejection happens later in the authorization filter when no authentication is present.

### 11.3 [`JwtUtil`](src/main/java/com/virtualoffice/service/user/security/JwtUtil.java)

- Reads `jwt.secret` and `jwt.expiration` (ms) from properties.
- Signs with **HS256** using the secret bytes (UTF-8) via `Keys.hmacShaKeyFor`.
- `generateToken(email)` builds a JWT with:
  - `sub` = email
  - `iat` = now
  - `exp` = now + `expiration`
- `extractEmail(token)` reads `sub`.
- `isTokenValid(token, userDetails)` returns `true` only if the email in the token matches `userDetails.getUsername()` **and** the token is not expired.
- `isTokenExpired` and `extractAllClaims` are private helpers using `Jwts.parserBuilder()` with the same signing key.

### 11.4 [`CustomUserDetailsService`](src/main/java/com/virtualoffice/service/user/security/CustomUserDetailsService.java) (implements `UserDetailsService`)

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

- Disabled users are surfaced as `UsernameNotFoundException` (so login fails with a generic auth error).
- `SUSPENDED` users are returned as `accountLocked`, which causes `DaoAuthenticationProvider` to reject login with `LockedException`.
- The returned `UserDetails` has **no granted authorities / roles** — the constructed Spring Security `User` is built without calling `.authorities(...)`.

---

## 12. Service Layer

### [`AuthService`](src/main/java/com/virtualoffice/service/user/service/AuthService.java)

Constructor-injected: `UserRepository`, `PasswordEncoder`, `JwtUtil`, `AuthenticationManager`.

`register(RegisterRequest request)`:
1. If `userRepository.existsByEmail(request.getEmail())` → return `AuthResponse.withError("Such E-mail Already Exist")` (HTTP 200 with all other fields null).
2. Build a `User` with `passwordEncoder.encode(request.getPassword())`, `accountStatus = ACTIVE`, the three boolean flags set to `false`.
3. `userRepository.save(user)`.
4. `String token = jwtUtil.generateToken(user.getEmail())`.
5. Return `new AuthResponse(token, email, firstName, lastName, "None")`.

`login(LoginRequest request)`:
1. `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))`. If credentials are wrong, the user is disabled, or the account is locked (suspended), this throws an `AuthenticationException`. There is no `try/catch`, so the exception propagates and Spring Security translates it to **`403 Forbidden`** with an empty body (since no exception handler is registered).
2. If authentication succeeds, look up the user by email.
   - If `null` (defensive — shouldn't happen because authentication just succeeded with that email), return `AuthResponse.withError("User Not Found")`.
3. Generate a fresh JWT and return `new AuthResponse(token, email, firstName, lastName, "None")`.

---

## 13. Controller

### [`AuthController`](src/main/java/com/virtualoffice/service/user/controller/AuthController.java)

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

- Both endpoints always return **`200 OK`** when the controller method itself completes.
- No `@Valid` — DTO validation is not enforced.
- No additional endpoints (no profile, no logout, no refresh, no verification routes).

---

## 14. Request / Response Flow

### Register
```
POST /api/auth/register
  → SecurityFilterChain: JwtAuthFilter.shouldNotFilter == true (path starts /api/auth/)
  → AuthorizationFilter: /api/auth/** is permitAll
  → AuthController.register
  → AuthService.register
       existsByEmail? yes → return AuthResponse.withError("Such E-mail Already Exist") [200]
                       no  → save user, generate JWT, return AuthResponse(token, ...) [200]
```

### Login (success)
```
POST /api/auth/login
  → JwtAuthFilter skipped
  → AuthService.login
       authenticationManager.authenticate(...)            ← BCrypt match against users.password_hash
       userRepository.findByEmail(...)
       jwtUtil.generateToken(email)
       return AuthResponse(token, ...) [200]
```

### Login (bad credentials / disabled / suspended)
```
POST /api/auth/login
  → AuthService.login
       authenticationManager.authenticate(...) throws AuthenticationException
       no exception handler → Spring Security default handler
       → 403 Forbidden, empty body
```

### Any other authenticated route
```
GET /something
  → JwtAuthFilter
       no Authorization header → context not authenticated
       Authorization: Bearer <jwt>
            extractEmail → loadUserByUsername → isTokenValid
            if all good: SecurityContextHolder.setAuthentication(...)
  → AuthorizationFilter: anyRequest().authenticated()
       authenticated → controller
       not authenticated → 403 Forbidden
```

---

## 15. Database Schema (effective)

With `ddl-auto=update`, Hibernate generates roughly:

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
    is_phone_number_verified BIT NOT NULL,
    is_disabled              BIT NOT NULL,
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

## 16. Tests

[`VirtualOfficeUserApplicationTests`](src/test/java/com/virtualoffice/service/user/VirtualOfficeUserApplicationTests.java)
```java
@SpringBootTest
class VirtualOfficeUserApplicationTests {
    @Test void contextLoads() {}
}
```

A single `contextLoads()` test. `@SpringBootTest` boots the full application context, which means the test requires:
- A reachable MySQL on `localhost:3306` (provided by Docker Compose if running).
- A reachable RabbitMQ on `localhost:5672`.

Run from the repo:
```bash
./mvnw -pl user-service test
```

---

## 17. How to Run

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

Port 3306 (MySQL) and 5672/15672 (RabbitMQ) must be free on the host or the container start fails.

---

## 18. Quick Reference

```
Port              : 8081
Spring Boot       : 3.4.1
Java              : 21
DB                : MySQL 8 — jdbc:mysql://localhost:3306/virtual_office (root / rootpassword)
Broker            : RabbitMQ 3 — localhost:5672 (guest / guest), management UI :15672
Entry point       : com.virtualoffice.service.user.VirtualOfficeUserApplication
Entities          : User, VerificationRequest
Enums             : AccountStatus  { ACTIVE, INACTIVE, PENDING_REPORT_REVIEW, SUSPENDED }
                    VerificationRequestStatus { PENDING, APPROVED }
Repositories      : UserRepository (findByEmail, existsByEmail)
                    VerificationRequestRepository (findByUserIdAndStatus)  [unused]
DTOs              : RegisterRequest, LoginRequest, AuthResponse
Security          : Stateless, CSRF off, BCrypt, JWT HS256 (24h)
Public endpoints  : POST /api/auth/register
                    POST /api/auth/login
Authenticated     : everything else (none implemented)
Tests             : 1 (contextLoads)
```
