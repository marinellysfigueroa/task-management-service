package com.taskmanagement.repository.spec;

import com.taskmanagement.dto.TaskFilter;
import com.taskmanagement.model.Task;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JPA Criteria predicates behind {@code GET /api/v1/tasks}.
 *
 * <p>Design decision: query construction is isolated here rather than inlined in
 * the service. The service asks "give me the tasks matching this filter" and
 * stays free of Criteria API noise, while this class can be unit-tested and
 * extended with new criteria without touching business logic.
 *
 * <p>Note the association filters use {@code root.get("project").get("id")} and
 * not {@code root.join(...)}. Reading the identifier of a {@code @ManyToOne}
 * resolves to the foreign key column that already lives on the {@code tasks}
 * table, so the generated SQL stays a single-table scan — no join, and no risk
 * of dropping rows whose association is null (which an inner join would do to
 * unassigned tasks).
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
        // Utility class: predicates are stateless.
    }

    /**
     * Combines every supplied criterion with AND. Absent ({@code null}) criteria
     * contribute no predicate, so an empty filter yields an always-true
     * specification that matches all tasks.
     */
    public static Specification<Task> matching(TaskFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
            }
            if (filter.priority() != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), filter.priority()));
            }
            if (filter.projectId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("project").get("id"), filter.projectId()));
            }
            if (filter.assigneeId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("assignee").get("id"), filter.assigneeId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
