package com.taskmanagement.service;

import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.ProjectPatchDto;
import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.integration.AbstractIntegrationTest;
import com.taskmanagement.model.Project;
import com.taskmanagement.model.Role;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the project use cases against a real PostgreSQL instance, with the
 * emphasis on the rules that only show up once the database is involved: owner
 * existence (there is no FK backing {@code owner_id}) and the cascade guard on
 * delete.
 */
class ProjectServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private Long ownerId;
    private Long otherOwnerId;

    @BeforeEach
    void seedData() {
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = userRepository.save(User.builder()
                .username("owner").email("owner@example.com").password("x").role(Role.MANAGER).build()).getId();
        otherOwnerId = userRepository.save(User.builder()
                .username("other").email("other@example.com").password("x").role(Role.MANAGER).build()).getId();
    }

    @Test
    void shouldCreateAndReadBackAProject() {
        ProjectResponseDto created = projectService.create(
                new ProjectRequestDto("Alpha", "First project", ownerId));

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();

        ProjectResponseDto found = projectService.findById(created.id());
        assertThat(found.name()).isEqualTo("Alpha");
        assertThat(found.ownerId()).isEqualTo(ownerId);
    }

    @Test
    void shouldRejectAnOwnerThatDoesNotExist() {
        // owner_id is a plain column, so nothing at the database level would stop
        // this from creating a dangling reference.
        ProjectRequestDto request = new ProjectRequestDto("Alpha", "First project", 999_999L);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 999999");
    }

    @Test
    void shouldPaginateAndFilterByOwner() {
        projectService.create(new ProjectRequestDto("Alpha", null, ownerId));
        projectService.create(new ProjectRequestDto("Beta", null, ownerId));
        projectService.create(new ProjectRequestDto("Gamma", null, otherOwnerId));

        PageResponse<ProjectResponseDto> all =
                projectService.findAll(null, PageRequest.of(0, 10, Sort.by("name")));
        assertThat(all.totalElements()).isEqualTo(3);

        PageResponse<ProjectResponseDto> mine =
                projectService.findAll(ownerId, PageRequest.of(0, 10, Sort.by("name")));
        assertThat(mine.totalElements()).isEqualTo(2);
        assertThat(mine.content()).extracting(ProjectResponseDto::name).containsExactly("Alpha", "Beta");

        PageResponse<ProjectResponseDto> firstOfTwo =
                projectService.findAll(null, PageRequest.of(0, 2, Sort.by("name")));
        assertThat(firstOfTwo.content()).hasSize(2);
        assertThat(firstOfTwo.totalPages()).isEqualTo(2);
        assertThat(firstOfTwo.last()).isFalse();
    }

    @Test
    void patchShouldOnlyTouchTheSuppliedFields() {
        ProjectResponseDto created = projectService.create(
                new ProjectRequestDto("Alpha", "First project", ownerId));

        ProjectResponseDto patched = projectService.patch(created.id(),
                new ProjectPatchDto(null, "Updated description", null));

        assertThat(patched.name()).isEqualTo("Alpha");
        assertThat(patched.description()).isEqualTo("Updated description");
        assertThat(patched.ownerId()).isEqualTo(ownerId);
        assertThat(patched.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    void replaceShouldClearOmittedFields() {
        ProjectResponseDto created = projectService.create(
                new ProjectRequestDto("Alpha", "First project", ownerId));

        ProjectResponseDto replaced = projectService.replace(created.id(),
                new ProjectRequestDto("Alpha renamed", null, otherOwnerId));

        assertThat(replaced.name()).isEqualTo("Alpha renamed");
        assertThat(replaced.description()).isNull();
        assertThat(replaced.ownerId()).isEqualTo(otherOwnerId);
    }

    @Test
    void shouldRefuseToDeleteAProjectThatStillOwnsTasks() {
        ProjectResponseDto created = projectService.create(
                new ProjectRequestDto("Alpha", "First project", ownerId));
        Project project = projectRepository.findById(created.id()).orElseThrow();
        taskRepository.save(Task.builder().title("Blocking task").project(project).build());

        assertThatThrownBy(() -> projectService.delete(created.id(), false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still has 1 task(s)");

        assertThat(projectRepository.findById(created.id())).isPresent();
    }

    @Test
    void shouldDeleteTheProjectAndItsTasksWhenCascadeIsRequested() {
        ProjectResponseDto created = projectService.create(
                new ProjectRequestDto("Alpha", "First project", ownerId));
        Project project = projectRepository.findById(created.id()).orElseThrow();
        taskRepository.save(Task.builder().title("Doomed task").project(project).build());

        projectService.delete(created.id(), true);

        assertThat(projectRepository.findById(created.id())).isEmpty();
        assertThat(taskRepository.countByProject_Id(created.id())).isZero();
    }

    @Test
    void shouldDeleteAnEmptyProjectWithoutRequiringCascade() {
        ProjectResponseDto created = projectService.create(
                new ProjectRequestDto("Alpha", "First project", ownerId));

        projectService.delete(created.id(), false);

        assertThat(projectRepository.findById(created.id())).isEmpty();
    }

    @Test
    void shouldFailWithNotFoundForAnUnknownProject() {
        assertThatThrownBy(() -> projectService.findById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: 999999");
    }
}
