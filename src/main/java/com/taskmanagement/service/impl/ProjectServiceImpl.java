package com.taskmanagement.service.impl;

import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.ProjectPatchDto;
import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.mapper.ProjectMapper;
import com.taskmanagement.model.Project;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.service.ProjectService;
import com.taskmanagement.service.support.SortWhitelist;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Default {@link ProjectService} implementation.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Dependencies are injected through the constructor (Lombok generates it
 *       from the {@code final} fields). Constructor injection makes the
 *       collaborators explicit, keeps the class immutable, and means it can be
 *       unit-tested with plain mocks and no Spring context.</li>
 *   <li>The class is read-only transactional by default, with {@code @Transactional}
 *       re-declared on mutating methods. Read paths then run without a flush and
 *       can be routed to a replica later.</li>
 *   <li>Updates rely on JPA dirty checking: inside the transaction the loaded
 *       entity is managed, so mutating it is enough and an explicit
 *       {@code save()} would only add noise.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    /**
     * Properties a client is allowed to sort by. Deliberately a subset of the
     * entity's fields — see {@link SortWhitelist}.
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "name", "ownerId", "createdAt");

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;

    @Override
    @Transactional
    public ProjectResponseDto create(ProjectRequestDto request) {
        requireExistingOwner(request.ownerId());
        Project project = projectMapper.toEntity(request);
        return projectMapper.toResponse(projectRepository.save(project));
    }

    @Override
    public ProjectResponseDto findById(Long id) {
        return projectMapper.toResponse(getOrThrow(id));
    }

    @Override
    public PageResponse<ProjectResponseDto> findAll(Long ownerId, Pageable pageable) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        Page<Project> projects = ownerId == null
                ? projectRepository.findAll(pageable)
                : projectRepository.findByOwnerId(ownerId, pageable);

        // Map inside the transaction, then hand the controller DTOs only: no
        // lazy proxy ever escapes to the serializer.
        return PageResponse.from(projects.map(projectMapper::toResponse));
    }

    @Override
    @Transactional
    public ProjectResponseDto replace(Long id, ProjectRequestDto request) {
        Project project = getOrThrow(id);
        requireExistingOwner(request.ownerId());
        projectMapper.updateEntity(request, project);
        return projectMapper.toResponse(project);
    }

    @Override
    @Transactional
    public ProjectResponseDto patch(Long id, ProjectPatchDto patch) {
        Project project = getOrThrow(id);
        if (patch.ownerId() != null) {
            requireExistingOwner(patch.ownerId());
        }
        projectMapper.patchEntity(patch, project);
        return projectMapper.toResponse(project);
    }

    @Override
    @Transactional
    public void delete(Long id, boolean cascade) {
        Project project = getOrThrow(id);

        // The Project -> Task association is mapped with CascadeType.ALL and
        // orphanRemoval, so deleting a project also deletes its tasks. That is a
        // lot of collateral damage to trigger from a bare DELETE, so it has to be
        // asked for explicitly; otherwise the caller gets a 409 telling them how
        // many tasks are in the way.
        long taskCount = taskRepository.countByProject_Id(id);
        if (taskCount > 0 && !cascade) {
            throw new ConflictException(
                    ("Project %d still has %d task(s). Delete or reassign them first, "
                            + "or repeat the request with ?cascade=true to remove them along with the project.")
                            .formatted(id, taskCount));
        }

        projectRepository.delete(project);
    }

    private Project getOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", id));
    }

    /**
     * {@code ownerId} is a plain column rather than a mapped association (see
     * {@link Project}), so the database will not enforce that it points at a real
     * user. Validating it here keeps the invariant even without the FK, and turns
     * what would otherwise be a silent dangling reference into a 404.
     */
    private void requireExistingOwner(Long ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw ResourceNotFoundException.of("User", ownerId);
        }
    }
}
