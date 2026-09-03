package com.taskmanagement.service;

import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.TaskFilter;
import com.taskmanagement.dto.TaskPatchDto;
import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.integration.AbstractIntegrationTest;
import com.taskmanagement.model.Project;
import com.taskmanagement.model.Role;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the task use cases against a real PostgreSQL instance.
 *
 * <p>The web-layer slice tests mock the service away, so this is where the parts
 * that only exist at runtime are actually verified: that the dynamic
 * {@code Specification} produces the intended AND-combination, that pagination
 * and sorting reach the database, and that the MapStruct mapper reads the lazy
 * associations without blowing up outside a session.
 */
class TaskServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Long alphaProjectId;
    private Long betaProjectId;
    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void seedData() {
        // Children first: the shared container keeps state between test classes.
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User alice = userRepository.save(User.builder()
                .username("alice").email("alice@example.com").password("x").role(Role.USER).build());
        User bob = userRepository.save(User.builder()
                .username("bob").email("bob@example.com").password("x").role(Role.USER).build());
        aliceId = alice.getId();
        bobId = bob.getId();

        Project alpha = projectRepository.save(Project.builder()
                .name("Alpha").description("First project").ownerId(aliceId).build());
        Project beta = projectRepository.save(Project.builder()
                .name("Beta").description("Second project").ownerId(bobId).build());
        alphaProjectId = alpha.getId();
        betaProjectId = beta.getId();

        // Alpha: 2 TODO (alice), 1 IN_PROGRESS (bob), 1 DONE (unassigned)
        persist("Alpha todo 1", TaskStatus.TODO, TaskPriority.LOW, alpha, alice);
        persist("Alpha todo 2", TaskStatus.TODO, TaskPriority.HIGH, alpha, alice);
        persist("Alpha in progress", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, alpha, bob);
        persist("Alpha done", TaskStatus.DONE, TaskPriority.MEDIUM, alpha, null);
        // Beta: 1 TODO (alice)
        persist("Beta todo", TaskStatus.TODO, TaskPriority.CRITICAL, beta, alice);
    }

    private void persist(String title, TaskStatus status, TaskPriority priority, Project project, User assignee) {
        taskRepository.save(Task.builder()
                .title(title).status(status).priority(priority)
                .project(project).assignee(assignee).build());
    }

    @Test
    void shouldReturnEveryTaskWhenNoFilterIsApplied() {
        PageResponse<TaskResponseDto> page =
                taskService.search(new TaskFilter(null, null, null, null), PageRequest.of(0, 10));

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.content()).hasSize(5);
    }

    @Test
    void shouldCombineEveryFilterWithAnd() {
        PageResponse<TaskResponseDto> page = taskService.search(
                new TaskFilter(TaskStatus.TODO, alphaProjectId, aliceId, null), PageRequest.of(0, 10));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(TaskResponseDto::title)
                .containsExactlyInAnyOrder("Alpha todo 1", "Alpha todo 2");
        assertThat(page.content()).allSatisfy(task -> {
            assertThat(task.projectId()).isEqualTo(alphaProjectId);
            assertThat(task.assigneeId()).isEqualTo(aliceId);
            assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        });
    }

    @Test
    void shouldFilterByEachCriterionIndependently() {
        assertThat(taskService.search(new TaskFilter(TaskStatus.TODO, null, null, null),
                PageRequest.of(0, 10)).totalElements()).isEqualTo(3);

        assertThat(taskService.search(new TaskFilter(null, betaProjectId, null, null),
                PageRequest.of(0, 10)).totalElements()).isEqualTo(1);

        assertThat(taskService.search(new TaskFilter(null, null, bobId, null),
                PageRequest.of(0, 10)).totalElements()).isEqualTo(1);

        assertThat(taskService.search(new TaskFilter(null, null, null, TaskPriority.HIGH),
                PageRequest.of(0, 10)).totalElements()).isEqualTo(2);
    }

    @Test
    void shouldReturnAnEmptyPageWhenNoTaskMatches() {
        PageResponse<TaskResponseDto> page = taskService.search(
                new TaskFilter(TaskStatus.DONE, betaProjectId, null, null), PageRequest.of(0, 10));

        assertThat(page.totalElements()).isZero();
        assertThat(page.content()).isEmpty();
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
    }

    @Test
    void shouldKeepUnassignedTasksVisibleWhenNotFilteringByAssignee() {
        // Guards against the association filter being implemented as an inner
        // join, which would silently drop tasks with a null assignee.
        PageResponse<TaskResponseDto> page = taskService.search(
                new TaskFilter(TaskStatus.DONE, null, null, null), PageRequest.of(0, 10));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).title()).isEqualTo("Alpha done");
        assertThat(page.content().get(0).assigneeId()).isNull();
    }

    @Test
    void shouldPaginateAndSortAtTheDatabase() {
        PageRequest firstPage = PageRequest.of(0, 2, Sort.by("title").ascending());
        PageResponse<TaskResponseDto> page = taskService.search(new TaskFilter(null, null, null, null), firstPage);

        assertThat(page.content()).extracting(TaskResponseDto::title)
                .containsExactly("Alpha done", "Alpha in progress");
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isFalse();

        PageResponse<TaskResponseDto> lastPage = taskService.search(
                new TaskFilter(null, null, null, null), PageRequest.of(2, 2, Sort.by("title").ascending()));

        assertThat(lastPage.content()).extracting(TaskResponseDto::title).containsExactly("Beta todo");
        assertThat(lastPage.last()).isTrue();
    }

    @Test
    void shouldRejectSortingByAPropertyOutsideTheWhitelist() {
        PageRequest bySecret = PageRequest.of(0, 10, Sort.by("assignee.password"));

        assertThatThrownBy(() -> taskService.search(new TaskFilter(null, null, null, null), bySecret))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'assignee.password'");
    }

    @Test
    void shouldApplyEntityDefaultsWhenStatusAndPriorityAreOmitted() {
        TaskResponseDto created = taskService.create(new TaskRequestDto(
                "Minimal task", null, null, null, alphaProjectId, null, null));

        assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        assertThat(created.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(created.assigneeId()).isNull();
        assertThat(created.projectId()).isEqualTo(alphaProjectId);
    }

    @Test
    void shouldFailWithNotFoundWhenTheReferencedProjectIsUnknown() {
        TaskRequestDto request = new TaskRequestDto(
                "Orphan", null, null, null, 999_999L, null, null);

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: 999999");
    }

    @Test
    void patchShouldOnlyTouchTheSuppliedFields() {
        TaskResponseDto created = taskService.create(new TaskRequestDto(
                "Original", "Original description", TaskStatus.TODO, TaskPriority.LOW,
                alphaProjectId, aliceId, LocalDate.of(2026, 12, 31)));

        TaskResponseDto patched = taskService.patch(created.id(),
                new TaskPatchDto(null, null, TaskStatus.IN_PROGRESS, null, null, null));

        assertThat(patched.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(patched.title()).isEqualTo("Original");
        assertThat(patched.description()).isEqualTo("Original description");
        assertThat(patched.priority()).isEqualTo(TaskPriority.LOW);
        assertThat(patched.assigneeId()).isEqualTo(aliceId);
        assertThat(patched.dueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void replaceShouldClearOmittedFieldsAndMoveTheTask() {
        TaskResponseDto created = taskService.create(new TaskRequestDto(
                "Original", "Original description", TaskStatus.TODO, TaskPriority.LOW,
                alphaProjectId, aliceId, LocalDate.of(2026, 12, 31)));

        // PUT is a full replacement: no assignee and no due date in the payload
        // means the task ends up unassigned and undated, and it moves to Beta.
        TaskResponseDto replaced = taskService.replace(created.id(), new TaskRequestDto(
                "Replaced", null, TaskStatus.DONE, TaskPriority.CRITICAL, betaProjectId, null, null));

        assertThat(replaced.title()).isEqualTo("Replaced");
        assertThat(replaced.description()).isNull();
        assertThat(replaced.status()).isEqualTo(TaskStatus.DONE);
        assertThat(replaced.priority()).isEqualTo(TaskPriority.CRITICAL);
        assertThat(replaced.projectId()).isEqualTo(betaProjectId);
        assertThat(replaced.assigneeId()).isNull();
        assertThat(replaced.dueDate()).isNull();
    }

    @Test
    void shouldDeleteATaskWithoutAffectingItsProject() {
        List<Task> alphaTasks = taskRepository.findByProject_Id(alphaProjectId);
        Long victimId = alphaTasks.get(0).getId();

        taskService.delete(victimId);

        assertThat(taskRepository.findById(victimId)).isEmpty();
        assertThat(projectRepository.findById(alphaProjectId)).isPresent();
        assertThat(taskRepository.countByProject_Id(alphaProjectId)).isEqualTo(3);
    }

    @Test
    void shouldFailWithNotFoundWhenDeletingAnUnknownTask() {
        assertThatThrownBy(() -> taskService.delete(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task not found with id: 999999");
    }
}
