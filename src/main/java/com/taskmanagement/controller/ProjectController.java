package com.taskmanagement.controller;

import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import com.taskmanagement.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management endpoints")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ProjectResponseDto> createProject(@Valid @RequestBody ProjectRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project by id")
    public ResponseEntity<ProjectResponseDto> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @GetMapping
    @Operation(summary = "List all projects, optionally filtered by owner")
    public ResponseEntity<List<ProjectResponseDto>> getAllProjects(
            @RequestParam(required = false) Long ownerId) {
        if (ownerId != null) {
            return ResponseEntity.ok(projectService.getProjectsByOwner(ownerId));
        }
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing project")
    public ResponseEntity<ProjectResponseDto> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectRequestDto request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
