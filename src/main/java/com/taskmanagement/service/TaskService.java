package com.taskmanagement.service;

import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import com.taskmanagement.dto.TaskUpdateDto;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.model.Project;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public TaskResponseDto createTask(TaskRequestDto request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> ResourceNotFoundException.of("Project", request.getProjectId()));

        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("User", request.getAssigneeId()));
        }

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
                .project(project)
                .assignee(assignee)
                .dueDate(request.getDueDate())
                .build();

        return toResponseDto(taskRepository.save(task));
    }

    public TaskResponseDto getTaskById(Long id) {
        return toResponseDto(findTaskOrThrow(id));
    }

    public List<TaskResponseDto> getAllTasks() {
        return taskRepository.findAll().stream().map(this::toResponseDto).toList();
    }

    public List<TaskResponseDto> getTasksByProject(Long projectId) {
        return taskRepository.findByProject_Id(projectId).stream().map(this::toResponseDto).toList();
    }

    public List<TaskResponseDto> getTasksByAssignee(Long assigneeId) {
        return taskRepository.findByAssignee_Id(assigneeId).stream().map(this::toResponseDto).toList();
    }

    public List<TaskResponseDto> getTasksByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status).stream().map(this::toResponseDto).toList();
    }

    @Transactional
    public TaskResponseDto updateTask(Long id, TaskUpdateDto request) {
        Task task = findTaskOrThrow(id);

        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("User", request.getAssigneeId()));
            task.setAssignee(assignee);
        }

        return toResponseDto(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Task", id);
        }
        taskRepository.deleteById(id);
    }

    protected Task findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", id));
    }

    private TaskResponseDto toResponseDto(Task task) {
        return TaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .projectId(task.getProjectId())
                .assigneeId(task.getAssigneeId())
                .dueDate(task.getDueDate())
                .build();
    }
}
