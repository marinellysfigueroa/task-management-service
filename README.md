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

HTTP Basic authentication backed by the `users` table (`CustomUserDetailsService`).

- `POST /api/v1/users` is open (self-registration).
- `DELETE` on `/api/v1/users/**` and `/api/v1/projects/**` requires the `ADMIN` role.
- All other endpoints require an authenticated user.
- Swagger UI and `/actuator/health` are public.

Passwords are hashed with BCrypt.

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
