# Task Management Service

REST API for managing users, projects and tasks, built with **Spring Boot 3.3 (Java 17)**.

## Stack

- Spring Boot 3.3.x / Java 17 / Maven
- Spring Web, Spring Data JPA, Spring Security, Bean Validation
- PostgreSQL (runtime driver)
- Lombok
- springdoc-openapi (Swagger UI)
- Spring Boot Actuator
- Testing: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL)

## Project structure

```
src/main/java/com/taskmanagement
├── controller   REST controllers (Users, Projects, Tasks)
├── service      Business logic
├── repository   Spring Data JPA repositories
├── model        JPA entities (User, Project, Task) + enums
├── dto          Request/response DTOs with bean validation
├── exception    Custom exceptions + @RestControllerAdvice handler
├── config       OpenAPI/Swagger configuration
└── security     Spring Security configuration + UserDetailsService
```

## Domain model

- **User**: `id, username, email, password, role (ADMIN/MANAGER/USER)`
- **Project**: `id, name, description, ownerId, createdAt` — one Project has many Tasks
- **Task**: `id, title, description, status (TODO/IN_PROGRESS/DONE), priority, project, assignee, dueDate`

Relations are implemented as real JPA/Hibernate associations:
- `Project (1) —— (N) Task` via `@OneToMany`/`@ManyToOne` (`project_id` FK)
- `User (1) —— (N) Task` as assignee via `@OneToMany`/`@ManyToOne` (`assignee_id` FK)

`Task` also exposes convenience `getProjectId()` / `getAssigneeId()` accessors so DTOs can work with plain ids.

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
