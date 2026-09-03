package com.taskmanagement.service;

import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.ProjectPatchDto;
import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import org.springframework.data.domain.Pageable;

/**
 * Use cases available for projects.
 *
 * <p>Design decision: the controller depends on this interface, not on the
 * implementation (DIP). The abstraction is defined in terms the caller cares
 * about — DTOs in, DTOs out, never entities — so the transaction and persistence
 * details stay behind it, and an alternative implementation (a caching
 * decorator, a read-model backed one) can be substituted without touching the
 * web layer.
 */
public interface ProjectService {

    ProjectResponseDto create(ProjectRequestDto request);

    ProjectResponseDto findById(Long id);

    /**
     * @param ownerId optional filter; {@code null} returns projects for all owners
     */
    PageResponse<ProjectResponseDto> findAll(Long ownerId, Pageable pageable);

    /** Full replacement (PUT): every writable field is overwritten. */
    ProjectResponseDto replace(Long id, ProjectRequestDto request);

    /** Partial update (PATCH): only non-null fields of {@code patch} are applied. */
    ProjectResponseDto patch(Long id, ProjectPatchDto patch);

    /**
     * @param cascade when false, deleting a project that still owns tasks is
     *                rejected with a conflict instead of silently removing them
     */
    void delete(Long id, boolean cascade);
}
