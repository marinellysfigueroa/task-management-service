package com.taskmanagement.service.impl;

import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.TaskFilter;
import com.taskmanagement.dto.TaskPatchDto;
import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.mapper.TaskMapper;
import com.taskmanagement.model.Project;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.repository.spec.TaskSpecifications;
import com.taskmanagement.service.TaskService;
import com.taskmanagement.service.support.SortWhitelist;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Default {@link TaskService} implementation.
 *
 * <p>The service owns the rules the mapper cannot express: resolving the
 * {@code projectId}/{@code assigneeId} references into managed entities, and
 * failing with 404 when they do not exist. Everything that is a pure field copy
 * is delegated to {@link TaskMapper}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskServiceImpl implements TaskService {

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of("id", "title", "status", "priority", "dueDate");

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;

    @Override
    @Transactional
    public TaskResponseDto create(TaskRequestDto request) {
        Task task = taskMapper.toEntity(request);
        task.setProject(requireProject(request.projectId()));
        task.setAssignee(resolveAssignee(request.assigneeId()));
        return taskMapper.toResponse(taskRepository.save(task));
    }

    @Override
    public TaskResponseDto findById(Long id) {
        return taskMapper.toResponse(getOrThrow(id));
    }

    @Override
    public PageResponse<TaskResponseDto> search(TaskFilter filter, Pageable pageable) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        // One specification handles every combination of the optional filters,
        // including none at all, so there is no branching ladder here and the
        // database does the filtering and the paging in a single query.
        Page<Task> tasks = taskRepository.findAll(TaskSpecifications.matching(filter), pageable);

        return PageResponse.from(tasks.map(taskMapper::toResponse));
    }

    @Override
    @Transactional
    public TaskResponseDto replace(Long id, TaskRequestDto request) {
        Task task = getOrThrow(id);
        taskMapper.updateEntity(request, task);
        // PUT is a full replacement, so both associations are re-derived from the
        // payload: an omitted assigneeId genuinely unassigns the task, and a
        // different projectId moves it.
        task.setProject(requireProject(request.projectId()));
        task.setAssignee(resolveAssignee(request.assigneeId()));
        return taskMapper.toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponseDto patch(Long id, TaskPatchDto patch) {
        Task task = getOrThrow(id);
        taskMapper.patchEntity(patch, task);
        // Null here means "not supplied", so the current assignee is kept.
        if (patch.assigneeId() != null) {
            task.setAssignee(requireUser(patch.assigneeId()));
        }
        return taskMapper.toResponse(task);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Task task = getOrThrow(id);
        taskRepository.delete(task);
    }

    private Task getOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", id));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    /** A task may legitimately have no assignee, so null is a valid outcome. */
    private User resolveAssignee(Long assigneeId) {
        return assigneeId == null ? null : requireUser(assigneeId);
    }
}
