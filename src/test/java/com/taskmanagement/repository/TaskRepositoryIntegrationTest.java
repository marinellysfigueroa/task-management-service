package com.taskmanagement.repository;

import com.taskmanagement.integration.AbstractIntegrationTest;
import com.taskmanagement.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldPersistTaskWithProjectAndAssigneeRelations() {
        User assignee = userRepository.save(User.builder()
                .username("jane.doe")
                .email("jane.doe@example.com")
                .password("encoded-password")
                .role(Role.USER)
                .build());

        Project project = projectRepository.save(Project.builder()
                .name("Platform Migration")
                .description("Migrate legacy services to the new platform")
                .ownerId(1L)
                .build());

        Task task = taskRepository.save(Task.builder()
                .title("Set up CI pipeline")
                .description("Configure build and test pipeline")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .project(project)
                .assignee(assignee)
                .build());

        List<Task> tasksForProject = taskRepository.findByProjectId(project.getId());
        List<Task> tasksForAssignee = taskRepository.findByAssigneeId(assignee.getId());

        assertThat(tasksForProject).hasSize(1).extracting(Task::getId).containsExactly(task.getId());
        assertThat(tasksForAssignee).hasSize(1).extracting(Task::getId).containsExactly(task.getId());
        assertThat(taskRepository.findByStatus(TaskStatus.TODO)).isNotEmpty();
    }
}
