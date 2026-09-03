package com.taskmanagement.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * Document-level OpenAPI metadata.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>The description states the API-wide conventions once — versioning,
 *       pagination, the RFC 7807 error contract — so each operation only has to
 *       document what is specific to it.</li>
 *   <li>The security requirement is declared globally and the two public
 *       endpoints opt out implicitly; the alternative (repeating it per
 *       operation) drifts the moment an endpoint is added.</li>
 *   <li>The version lives in the URI ({@code /api/v1}), so the OpenAPI
 *       {@code version} field tracks the same major version. A future v2 is a
 *       second set of controllers under {@code /api/v2} served alongside v1,
 *       which is what makes a deprecation window possible at all.</li>
 * </ul>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Task Management Service API",
                version = "v1",
                description = """
                        REST API for managing users, projects and tasks.

                        **Versioning** — every endpoint is served under `/api/v1`. Breaking changes
                        ship as a new prefix (`/api/v2`) served alongside the previous version;
                        additive changes are made in place.

                        **Pagination** — collection endpoints are always paginated. They accept
                        `page` (zero-based), `size` and `sort` (`property,asc|desc`) and return a
                        `PageResponse` envelope carrying the items plus the page metadata.
                        Sorting is restricted to an explicit set of properties per resource;
                        anything else is rejected with 400.

                        **Errors** — all failures are returned as RFC 7807 problem documents with
                        content type `application/problem+json`. The `type` field is a stable
                        identifier for the problem category and is the field to branch on;
                        validation failures additionally carry a structured `errors` array.

                        **Authentication** — HTTP Basic. Creating a user (`POST /api/v1/users`)
                        and the Swagger/actuator endpoints are public; everything else requires
                        credentials, and a few destructive operations require the ADMIN role.
                        """,
                contact = @Contact(name = "Task Management Team", email = "marinellys.figueroa@gmail.com"),
                license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local development")
        },
        tags = {
                @Tag(name = "Projects", description = "Create, query and maintain projects"),
                @Tag(name = "Tasks", description = "Create, query and maintain tasks"),
                @Tag(name = "Users", description = "User management endpoints")
        },
        security = @SecurityRequirement(name = "basicAuth")
)
@SecurityScheme(
        name = "basicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic",
        description = "HTTP Basic credentials of a registered user"
)
public class OpenApiConfig {
}
