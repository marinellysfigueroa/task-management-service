# Task Management Service

REST API for managing users, projects and tasks, built with **Spring Boot 3.3 (Java 17)**.

## Stack

- Spring Boot 3.3.x / Java 17 / Maven
- Spring Web, Spring Data JPA, Spring Security, Bean Validation
- PostgreSQL (runtime driver)
- Lombok + **MapStruct** (compile-time entity ↔ DTO mapping)
- springdoc-openapi (Swagger UI)
- Spring Boot Actuator
- Testing: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL)

## Project structure

```
src/main/java/com/taskmanagement
├── controller        REST controllers (Users, Projects, Tasks) — thin HTTP adapters
├── service           Use-case interfaces (ProjectService, TaskService)
│   ├── impl          Implementations holding the business rules
│   └── support       SortWhitelist and other cross-cutting helpers
├── mapper            MapStruct mappers, generated at compile time
├── repository        Spring Data JPA repositories
│   └── spec          JPA Specifications for dynamic filtering
├── model             JPA entities (User, Project, Task) + enums
├── dto               Immutable request/response records with bean validation
├── exception         Custom exceptions + RFC 7807 @RestControllerAdvice handler
├── config            OpenAPI/Swagger configuration and documentation examples
└── security          Spring Security configuration + ProblemDetail error writers
```

### Layering

`Controller → Service (interface) → Repository → Entity`, with DTOs on the
boundary in both directions. Controllers depend on the service *interfaces*, so
the web layer never sees an implementation, a transaction or an entity; entities
never reach the JSON serializer, which is what keeps lazy associations from
being dereferenced mid-response.

## Domain model

- **User**: `id, username, email, password, role (ADMIN/MANAGER/USER)`
- **Project**: `id, name, description, ownerId, createdAt` — one Project has many Tasks
- **Task**: `id, title, description, status (TODO/IN_PROGRESS/DONE), priority, project, assignee, dueDate`

Relations are implemented as real JPA/Hibernate associations:
- `Project (1) —— (N) Task` via `@OneToMany`/`@ManyToOne` (`project_id` FK)
- `User (1) —— (N) Task` as assignee via `@OneToMany`/`@ManyToOne` (`assignee_id` FK)

`Task` also exposes convenience `getProjectId()` / `getAssigneeId()` accessors so DTOs can work with plain ids.

## API

All endpoints are versioned under `/api/v1`. Breaking changes ship as a new
prefix (`/api/v2`) served alongside the previous version; additive changes are
made in place.

### Projects

| Method   | Path                        | Notes |
|----------|-----------------------------|-------|
| `POST`   | `/api/v1/projects`          | 201 + `Location` header |
| `GET`    | `/api/v1/projects/{id}`     | |
| `GET`    | `/api/v1/projects`          | Paginated; optional `ownerId` filter |
| `PUT`    | `/api/v1/projects/{id}`     | Full replacement — omitted optional fields are cleared |
| `PATCH`  | `/api/v1/projects/{id}`     | Partial update — omitted fields keep their value |
| `DELETE` | `/api/v1/projects/{id}`     | ADMIN only; 409 if the project still owns tasks unless `?cascade=true` |

### Tasks

| Method   | Path                     | Notes |
|----------|--------------------------|-------|
| `POST`   | `/api/v1/tasks`          | 201 + `Location` header; `status` defaults to `TODO`, `priority` to `MEDIUM` |
| `GET`    | `/api/v1/tasks/{id}`     | |
| `GET`    | `/api/v1/tasks`          | Paginated + filtered (see below) |
| `PUT`    | `/api/v1/tasks/{id}`     | Full replacement — omitting `assigneeId` unassigns, a new `projectId` moves the task |
| `PATCH`  | `/api/v1/tasks/{id}`     | Partial update — the endpoint for `{"status": "DONE"}` |
| `DELETE` | `/api/v1/tasks/{id}`     | Never affects the project |

### Filtering and pagination

`GET /api/v1/tasks` accepts `status`, `projectId`, `assigneeId` and `priority`.
All are optional and combined with **AND**, resolved into a single SQL query by a
JPA `Specification` (no branching over filter combinations, no in-memory
filtering).

```bash
GET /api/v1/tasks?status=TODO&page=0&size=20
GET /api/v1/tasks?projectId=1&assigneeId=42&status=IN_PROGRESS
GET /api/v1/tasks?projectId=1&sort=dueDate,asc
```

Pagination is always applied — `page` (zero-based), `size` (default 20, capped
at 100) and `sort` (`property,asc|desc`). Sorting is restricted to an explicit
whitelist per resource (`id`, `title`, `status`, `priority`, `dueDate` for
tasks); anything else is rejected with 400 rather than silently ignored, which
also prevents ordering by columns the API never exposes.

Responses use a `PageResponse` envelope rather than Spring Data's `Page`, whose
JSON shape is an implementation detail:

```json
{
"content": [],
  "page": 0, "size": 20,
  "totalElements": 42, "totalPages": 3,
  "first": true, "last": false
}
```

### Errors (RFC 7807)

Every failure — including the ones raised by Spring MVC and by the security
filter chain — is returned as `application/problem+json`:

```json
{
  "type": "https://api.taskmanagement.com/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request body failed validation. See 'errors' for details.",
  "instance": "/api/v1/tasks",
  "timestamp": "2026-09-03T10:15:30.123Z",
  "errors": [
    { "field": "title", "message": "title is required", "rejectedValue": "" }
  ]
}
```

`type` is the stable, machine-readable discriminator — branch on it rather than
on the status code, since three different 400s call for three different fixes.

| `type` suffix        | Status | Raised when |
|----------------------|--------|-------------|
| `validation-error`   | 400    | Bean Validation rejected the body or a parameter |
| `bad-request`        | 400    | Unknown enum value, unsupported sort property |
| `unauthorized`       | 401    | Missing or invalid credentials |
| `access-denied`      | 403    | Authenticated but not permitted |
| `resource-not-found` | 404    | The resource, or one it references, does not exist |
| `conflict`           | 409    | Request valid but incompatible with current state |
| `internal-error`     | 500    | Unexpected failure (details logged, never returned) |

## Running locally

### Option A — Postgres via Docker, app from your IDE/CLI

```bash
cp .env.example .env
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Option B — Everything via Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

The API will be available at `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Actuator health: `http://localhost:8080/actuator/health`

## Configuration profiles

| Profile  | Purpose                                         | Notes |
|----------|--------------------------------------------------|-------|
| `local`  | Local development against Postgres on localhost   | `ddl-auto: update`, SQL logging on |
| `docker` | Running the app itself inside docker-compose      | Connects to the `postgres` service by hostname |
| `aws`    | Production-style deployment (e.g. RDS)            | `ddl-auto: validate`, requires `DB_HOST/DB_NAME/DB_USERNAME/DB_PASSWORD` env vars, SSL enforced |

Activate a profile with `SPRING_PROFILES_ACTIVE` (defaults to `local`).

## Security

Stateless authentication with JWT (HS256), backed by the `users` table
(`CustomUserDetailsService`) and Spring Security 6's `SecurityFilterChain` bean
— no `WebSecurityConfigurerAdapter`, which has been removed as of Spring
Security 6.

### Auth endpoints (public, `/api/v1/auth/**`)

| Method | Path                    | Notes |
|--------|-------------------------|-------|
| `POST` | `/api/v1/auth/register` | Always creates a `USER` account — no `role` field accepted; returns a token immediately |
| `POST` | `/api/v1/auth/login`    | Verifies credentials, returns a token |

Every other endpoint requires `Authorization: Bearer <accessToken>`.

### Roles and `@PreAuthorize`

Role checks live as `@PreAuthorize` next to the method they guard, not as URL
rules in `SecurityConfig` — `authorizeHttpRequests` only draws the
public/authenticated line:

- `UserController` — every endpoint requires `ADMIN` (class-level
  `@PreAuthorize`). This is where an admin creates an account with an arbitrary
  role, since `POST /api/v1/auth/register` deliberately cannot.
- `ProjectController.delete` — requires `ADMIN`.
- Everything else just requires an authenticated user (any role).

### Token validation (`JwtAuthenticationFilter`)

An `OncePerRequestFilter` reads `Authorization: Bearer <token>`, verifies
signature/issuer/expiry, and re-loads the user's authorities from the database
on every request rather than trusting the token's own `role` claim — a role
change or account deletion then takes effect immediately instead of only once
the (short-lived) token expires.

### 401 vs 403

Both render as `application/problem+json` (see the [Errors](#errors-rfc-7807)
section), but mean different things:

- **401** (`.../problems/unauthorized`) — no valid identity at all: missing
  token, expired token, malformed token, or bad login credentials.
- **403** (`.../problems/access-denied`) — a real, valid identity that is not
  allowed to do this specific thing (an authenticated `USER` hitting an
  `ADMIN`-only endpoint).

### Configuration

| Variable                 | Purpose                                   | Default |
|---------------------------|--------------------------------------------|---------|
| `JWT_SECRET`               | Base64-encoded HS256 signing key (≥ 32 bytes) | Dev-only key baked into `application.yml`; **the `aws` profile has no default and refuses to start without it** |
| `JWT_EXPIRATION_MINUTES`   | Token lifetime                              | `60` |

Passwords are hashed with BCrypt.

### Trying it out

`api-requests.http` at the repo root walks through register → login →
authenticated call → 403 vs 401 using IntelliJ's built-in HTTP Client (Preferences
> Plugins > HTTP Client). In Swagger UI, the "Authorize" button takes the raw
token (no `Bearer` prefix — Swagger adds it).

## Tests

```bash
mvn test
```

Integration tests spin up a real PostgreSQL instance via **Testcontainers** (requires Docker to be running).

## Build

```bash
mvn clean package
java -jar target/task-management-service.jar
```
