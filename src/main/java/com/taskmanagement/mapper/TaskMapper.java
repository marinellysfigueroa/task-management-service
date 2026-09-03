package com.taskmanagement.mapper;

import com.taskmanagement.dto.TaskPatchDto;
import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import com.taskmanagement.model.Task;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Entity &lt;-&gt; DTO translation for {@link Task}.
 *
 * <p>Design decision: the mapper never resolves the {@code project} and
 * {@code assignee} associations — it has no repositories and must stay a pure
 * function. Turning an id into a managed entity is a business operation (it can
 * fail with 404), so the service does it and the mapper simply ignores those
 * targets. Read direction is safe: {@code project.id} reads the foreign key
 * already held by the Hibernate proxy, so no extra SELECT is triggered.
 */
@Mapper
public interface TaskMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "assigneeId", source = "assignee.id")
    TaskResponseDto toResponse(Task task);

    /**
     * {@code defaultValue} reproduces the entity's own defaults. It is needed
     * because MapStruct writes through the Lombok builder, and an explicit
     * {@code null} would otherwise override the {@code @Builder.Default} value
     * and violate the NOT NULL constraint.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "status", source = "status", defaultValue = "TODO")
    @Mapping(target = "priority", source = "priority", defaultValue = "MEDIUM")
    Task toEntity(TaskRequestDto request);

    /** Full replace (PUT); associations are re-resolved by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "status", source = "status", defaultValue = "TODO")
    @Mapping(target = "priority", source = "priority", defaultValue = "MEDIUM")
    void updateEntity(TaskRequestDto request, @MappingTarget Task task);

    /** Partial update (PATCH): null source properties are skipped. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    void patchEntity(TaskPatchDto patch, @MappingTarget Task task);
}
