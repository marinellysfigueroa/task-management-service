package com.taskmanagement.service;

import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.TaskFilter;
import com.taskmanagement.dto.TaskPatchDto;
import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import org.springframework.data.domain.Pageable;

/**
 * Use cases available for tasks.
 *
 * <p>See {@link ProjectService} for the rationale behind the interface/impl
 * split. Note {@link #search} takes a single {@link TaskFilter} value rather
 * than one parameter per criterion, so adding a filter does not ripple through
 * every implementation and caller.
 */
public interface TaskService {

    TaskResponseDto create(TaskRequestDto request);

    TaskResponseDto findById(Long id);

    /** Paginated lookup; {@code filter} criteria are combined with AND. */
    PageResponse<TaskResponseDto> search(TaskFilter filter, Pageable pageable);

    /** Full replacement (PUT): omitted optional fields are cleared. */
    TaskResponseDto replace(Long id, TaskRequestDto request);

    /** Partial update (PATCH): only non-null fields of {@code patch} are applied. */
    TaskResponseDto patch(Long id, TaskPatchDto patch);

    void delete(Long id);
}
