package com.taskmanagement.controller;

import com.taskmanagement.config.ApiExamples;
import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.ProjectPatchDto;
import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import com.taskmanagement.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * HTTP entry point for the project resource.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>Versioned URI prefix ({@code /api/v1}).</b> URI versioning is the
 *       variant that is visible in logs, trivially routable at the gateway and
 *       usable from a browser, which matters more here than the purity of header
 *       or media-type versioning. The version is declared once, on the class.</li>
 *   <li><b>The controller is a thin adapter.</b> It validates, delegates to
 *       {@link ProjectService} (the interface, never the implementation) and
 *       shapes the HTTP response. No business rule and no persistence call lives
 *       here, which is what keeps the same use cases reusable from a different
 *       transport.</li>
 *   <li><b>Every method returns DTOs.</b> Entities never reach the serializer,
 *       so a lazy association can never be dereferenced mid-response.</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api/v1/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
@Tag(name = "Projects", description = "Create, query and maintain projects")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a project",
            description = """
                    Registers a new project owned by an existing user. The identifier and
                    creation timestamp are assigned by the server; sending them has no effect.
                    On success the `Location` header points at the newly created resource.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProjectResponseDto.class),
                            examples = @ExampleObject(name = "Created project", value = ApiExamples.PROJECT_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Unauthorized", value = ApiExamples.PROBLEM_UNAUTHORIZED))),
            @ApiResponse(responseCode = "404", description = "The referenced owner does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProjectResponseDto> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Project to create",
                    content = @Content(examples = @ExampleObject(name = "New project", value = ApiExamples.PROJECT_CREATE_REQUEST)))
            @Valid @RequestBody ProjectRequestDto request) {

        ProjectResponseDto created = projectService.create(request);

        // 201 + Location is the contract for a successful POST that creates a
        // resource; it saves the client from guessing the new URI.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project by id", description = "Returns a single project.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProjectResponseDto.class),
                            examples = @ExampleObject(name = "Project", value = ApiExamples.PROJECT_RESPONSE))),
            @ApiResponse(responseCode = "404", description = "No project with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_PROJECT_NOT_FOUND)))
    })
    public ResponseEntity<ProjectResponseDto> getById(
            @Parameter(description = "Project identifier", example = "1", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id) {

        return ResponseEntity.ok(projectService.findById(id));
    }

    @GetMapping
    @Operation(
            summary = "List projects",
            description = """
                    Returns a page of projects, optionally narrowed to a single owner.
                    Pagination is mandatory rather than opt-in: an unbounded list endpoint
                    degrades silently as the table grows. Use `page`, `size` and `sort`
                    (for example `sort=createdAt,desc`) to navigate.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of projects",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(name = "First page", value = ApiExamples.PROJECT_PAGE_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Unsupported sort property",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PageResponse<ProjectResponseDto>> list(
            @Parameter(description = "Return only projects owned by this user", example = "42")
            @RequestParam(required = false) @Positive(message = "ownerId must be a positive number") Long ownerId,

            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(projectService.findAll(ownerId, pageable));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Replace a project",
            description = """
                    Full replacement: every writable field is overwritten with the payload,
                    so an omitted optional field is cleared. Use PATCH to change a subset.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project replaced",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProjectResponseDto.class),
                            examples = @ExampleObject(name = "Updated project", value = ApiExamples.PROJECT_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION))),
            @ApiResponse(responseCode = "404", description = "Project or owner not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_PROJECT_NOT_FOUND)))
    })
    public ResponseEntity<ProjectResponseDto> replace(
            @Parameter(description = "Project identifier", example = "1", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Complete new state of the project",
                    content = @Content(examples = @ExampleObject(name = "Replacement", value = ApiExamples.PROJECT_REPLACE_REQUEST)))
            @Valid @RequestBody ProjectRequestDto request) {

        return ResponseEntity.ok(projectService.replace(id, request));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Partially update a project",
            description = """
                    Applies only the fields present in the payload; omitted fields keep their
                    current value. Preferred over PUT for small edits because it does not
                    require the client to first read the resource, which removes the
                    lost-update window of a read-modify-write cycle.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProjectResponseDto.class),
                            examples = @ExampleObject(name = "Updated project", value = ApiExamples.PROJECT_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION))),
            @ApiResponse(responseCode = "404", description = "Project or owner not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_PROJECT_NOT_FOUND)))
    })
    public ResponseEntity<ProjectResponseDto> patch(
            @Parameter(description = "Project identifier", example = "1", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Fields to change",
                    content = @Content(examples = @ExampleObject(name = "Change the description", value = ApiExamples.PROJECT_PATCH_REQUEST)))
            @Valid @RequestBody ProjectPatchDto patch) {

        return ResponseEntity.ok(projectService.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete a project",
            description = """
                    Deleting a project also deletes the tasks it owns. Because that is not
                    obvious from the call, a project that still has tasks is rejected with
                    409 unless `cascade=true` is passed explicitly.

                    Requires the ADMIN role.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Project deleted; no response body"),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Forbidden", value = ApiExamples.PROBLEM_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "No project with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_PROJECT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "Project still owns tasks and cascade was not requested",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Project not empty", value = ApiExamples.PROBLEM_PROJECT_HAS_TASKS)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Project identifier", example = "1", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id,

            @Parameter(description = "Also delete the tasks belonging to this project", example = "false")
            @RequestParam(defaultValue = "false") boolean cascade) {

        projectService.delete(id, cascade);
        return ResponseEntity.noContent().build();
    }
}
