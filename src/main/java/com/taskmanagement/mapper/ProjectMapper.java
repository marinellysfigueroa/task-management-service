package com.taskmanagement.mapper;

import com.taskmanagement.dto.ProjectPatchDto;
import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import com.taskmanagement.model.Project;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Entity &lt;-&gt; DTO translation for {@link Project}.
 *
 * <p>Design decision: mapping lives in its own component instead of in the
 * service. The service then has exactly one reason to change (business rules),
 * and the mapper another (wire format) — plain SRP. MapStruct generates the
 * implementation at compile time, so there is no reflection cost at runtime and
 * a forgotten field is a compile error rather than a silently null response
 * ({@code unmappedTargetPolicy=ERROR} is set globally in the POM).
 *
 * <p>Server-owned fields ({@code id}, {@code createdAt}) and the lazily loaded
 * {@code tasks} collection are explicitly ignored on every inbound mapping: the
 * ignores are written out rather than inferred so that adding a new entity field
 * forces a deliberate decision about whether clients may write it.
 */
@Mapper
public interface ProjectMapper {

    ProjectResponseDto toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    Project toEntity(ProjectRequestDto request);

    /**
     * Full replace (PUT): copies every client-writable field, including nulls,
     * so that omitting an optional field really does clear it.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    void updateEntity(ProjectRequestDto request, @MappingTarget Project project);

    /**
     * Partial update (PATCH): {@code NullValuePropertyMappingStrategy.IGNORE}
     * makes MapStruct skip null sources, which is exactly the "omitted means
     * unchanged" semantics — and it removes the null-check ladder that this kind
     * of method usually accumulates by hand.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    void patchEntity(ProjectPatchDto patch, @MappingTarget Project project);
}
