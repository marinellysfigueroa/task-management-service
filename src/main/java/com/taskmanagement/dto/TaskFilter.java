package com.taskmanagement.dto;

import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;

/**
 * Criteria object for {@code GET /api/v1/tasks}.
 *
 * <p>Design decision: bundling the filters into one immutable value keeps the
 * service signature stable as filters are added — the alternative, one method
 * parameter per criterion, forces every caller and every test to change each
 * time the API grows (a small Open/Closed win). It also keeps the controller
 * free of any query-building logic; translating criteria into predicates is the
 * job of {@code TaskSpecifications}.
 *
 * <p>A {@code null} field means "do not filter on this attribute". All present
 * criteria are combined with AND.
 */
public record TaskFilter(
        TaskStatus status,
        Long projectId,
        Long assigneeId,
        TaskPriority priority
) {

    /** True when no criterion was supplied, i.e. the query matches every task. */
    public boolean isEmpty() {
        return status == null && projectId == null && assigneeId == null && priority == null;
    }
}
