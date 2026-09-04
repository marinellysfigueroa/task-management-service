package com.taskmanagement.config;

/**
 * JSON snippets referenced from {@code @ExampleObject} annotations.
 *
 * <p>Design decision: Swagger examples must be compile-time constants, which
 * pushes long JSON literals into the annotations and quickly drowns the
 * controller signatures. Parking them here keeps the controllers readable and
 * lets the same error example be reused by every endpoint that can return it —
 * so the documented error shape cannot drift between operations.
 */
public final class ApiExamples {

    private ApiExamples() {
    }

    // ------------------------------------------------------------------
    // Projects
    // ------------------------------------------------------------------

    public static final String PROJECT_CREATE_REQUEST = """
            {
              "name": "Platform Migration",
              "description": "Migrate the legacy monolith to the new service platform",
              "ownerId": 42
            }
            """;

    public static final String PROJECT_REPLACE_REQUEST = """
            {
              "name": "Platform Migration - Phase 2",
              "description": "Extend the migration to the reporting subsystem",
              "ownerId": 42
            }
            """;

    public static final String PROJECT_PATCH_REQUEST = """
            {
              "description": "Now also covers the reporting subsystem"
            }
            """;

    public static final String PROJECT_RESPONSE = """
            {
              "id": 1,
              "name": "Platform Migration",
              "description": "Migrate the legacy monolith to the new service platform",
              "ownerId": 42,
              "createdAt": "2026-09-03T10:15:30"
            }
            """;

    public static final String PROJECT_PAGE_RESPONSE = """
            {
              "content": [
                {
                  "id": 1,
                  "name": "Platform Migration",
                  "description": "Migrate the legacy monolith to the new service platform",
                  "ownerId": 42,
                  "createdAt": "2026-09-03T10:15:30"
                },
                {
                  "id": 2,
                  "name": "Mobile App Revamp",
                  "description": "Rebuild the mobile client on the new design system",
                  "ownerId": 42,
                  "createdAt": "2026-09-04T08:02:11"
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 2,
              "totalPages": 1,
              "first": true,
              "last": true
            }
            """;

    // ------------------------------------------------------------------
    // Tasks
    // ------------------------------------------------------------------

    public static final String TASK_CREATE_REQUEST = """
            {
              "title": "Set up the CI pipeline",
              "description": "Configure build, test and container publishing stages",
              "status": "TODO",
              "priority": "HIGH",
              "projectId": 1,
              "assigneeId": 42,
              "dueDate": "2026-12-31"
            }
            """;

    public static final String TASK_MINIMAL_CREATE_REQUEST = """
            {
              "title": "Write the migration runbook",
              "projectId": 1
            }
            """;

    public static final String TASK_REPLACE_REQUEST = """
            {
              "title": "Set up the CI/CD pipeline",
              "description": "Build, test, publish the image and deploy to staging",
              "status": "IN_PROGRESS",
              "priority": "CRITICAL",
              "projectId": 1,
              "assigneeId": 43,
              "dueDate": "2027-01-15"
            }
            """;

    public static final String TASK_PATCH_REQUEST = """
            {
              "status": "IN_PROGRESS"
            }
            """;

    public static final String TASK_RESPONSE = """
            {
              "id": 10,
              "title": "Set up the CI pipeline",
              "description": "Configure build, test and container publishing stages",
              "status": "IN_PROGRESS",
              "priority": "HIGH",
              "projectId": 1,
              "assigneeId": 42,
              "dueDate": "2026-12-31"
            }
            """;

    public static final String TASK_PAGE_RESPONSE = """
            {
              "content": [
                {
                  "id": 10,
                  "title": "Set up the CI pipeline",
                  "description": "Configure build, test and container publishing stages",
                  "status": "IN_PROGRESS",
                  "priority": "HIGH",
                  "projectId": 1,
                  "assigneeId": 42,
                  "dueDate": "2026-12-31"
                },
                {
                  "id": 11,
                  "title": "Write the migration runbook",
                  "status": "TODO",
                  "priority": "MEDIUM",
                  "projectId": 1
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 42,
              "totalPages": 3,
              "first": true,
              "last": false
            }
            """;

    // ------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------

    public static final String AUTH_REGISTER_REQUEST = """
            {
              "username": "mfigueroa",
              "email": "marinellys.figueroa@gmail.com",
              "password": "S3curePass!23"
            }
            """;

    public static final String AUTH_LOGIN_REQUEST = """
            {
              "username": "mfigueroa",
              "password": "S3curePass!23"
            }
            """;

    public static final String AUTH_RESPONSE = """
            {
              "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtZmlndWVyb2EiLCJyb2xlIjoiVVNFUiJ9.dQw4w9WgXcQ",
              "tokenType": "Bearer",
              "expiresInSeconds": 3600,
              "username": "mfigueroa",
              "role": "USER"
            }
            """;

    public static final String PROBLEM_INVALID_CREDENTIALS = """
            {
              "type": "https://api.taskmanagement.com/problems/unauthorized",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Invalid username or password",
              "instance": "/api/v1/auth/login",
              "timestamp": "2026-09-03T10:15:30.123Z"
            }
            """;

    public static final String PROBLEM_DUPLICATE_USERNAME = """
            {
              "type": "https://api.taskmanagement.com/problems/bad-request",
              "title": "Invalid request",
              "status": 400,
              "detail": "Username already in use: mfigueroa",
              "instance": "/api/v1/auth/register",
              "timestamp": "2026-09-03T10:15:30.123Z"
            }
            """;

    // ------------------------------------------------------------------
    // Problem responses (RFC 7807)
    // ------------------------------------------------------------------

    public static final String PROBLEM_VALIDATION = """
            {
              "type": "https://api.taskmanagement.com/problems/validation-error",
              "title": "Validation failed",
              "status": 400,
              "detail": "The request body failed validation. See 'errors' for details.",
              "instance": "/api/v1/tasks",
              "timestamp": "2026-09-03T10:15:30.123Z",
              "errors": [
                { "field": "title", "message": "title is required", "rejectedValue": "" },
                { "field": "projectId", "message": "projectId is required", "rejectedValue": null }
              ]
            }
            """;

    public static final String PROBLEM_INVALID_ENUM = """
            {
              "type": "https://api.taskmanagement.com/problems/bad-request",
              "title": "Invalid request",
              "status": 400,
              "detail": "'FINISHED' is not a valid value for 'status'. Accepted values: TODO, IN_PROGRESS, DONE.",
              "instance": "/api/v1/tasks",
              "timestamp": "2026-09-03T10:15:30.123Z",
              "parameter": "status",
              "acceptedValues": ["TODO", "IN_PROGRESS", "DONE"]
            }
            """;

    public static final String PROBLEM_TASK_NOT_FOUND = """
            {
              "type": "https://api.taskmanagement.com/problems/resource-not-found",
              "title": "Resource not found",
              "status": 404,
              "detail": "Task not found with id: 999",
              "instance": "/api/v1/tasks/999",
              "timestamp": "2026-09-03T10:15:30.123Z",
              "resource": "Task",
              "identifier": 999
            }
            """;

    public static final String PROBLEM_PROJECT_NOT_FOUND = """
            {
              "type": "https://api.taskmanagement.com/problems/resource-not-found",
              "title": "Resource not found",
              "status": 404,
              "detail": "Project not found with id: 999",
              "instance": "/api/v1/projects/999",
              "timestamp": "2026-09-03T10:15:30.123Z",
              "resource": "Project",
              "identifier": 999
            }
            """;

    public static final String PROBLEM_PROJECT_HAS_TASKS = """
            {
              "type": "https://api.taskmanagement.com/problems/conflict",
              "title": "Conflicting resource state",
              "status": 409,
              "detail": "Project 1 still has 7 task(s). Delete or reassign them first, or repeat the request with ?cascade=true to remove them along with the project.",
              "instance": "/api/v1/projects/1",
              "timestamp": "2026-09-03T10:15:30.123Z"
            }
            """;

    public static final String PROBLEM_FORBIDDEN = """
            {
              "type": "https://api.taskmanagement.com/problems/access-denied",
              "title": "Access denied",
              "status": 403,
              "detail": "You do not have permission to perform this action",
              "instance": "/api/v1/projects/1",
              "timestamp": "2026-09-03T10:15:30.123Z"
            }
            """;

    public static final String PROBLEM_UNAUTHORIZED = """
            {
              "type": "https://api.taskmanagement.com/problems/unauthorized",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Authentication is required to access this resource",
              "instance": "/api/v1/tasks",
              "timestamp": "2026-09-03T10:15:30.123Z"
            }
            """;
}
