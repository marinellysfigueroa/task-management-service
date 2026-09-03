package com.taskmanagement.repository;

import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Repository abstraction for {@link Task}.
 *
 * <p>Design decision: this extends {@link JpaSpecificationExecutor} instead of
 * growing one derived query per filter combination. With three optional filters
 * the derived-query approach needs 2^3 methods and a branching ladder in the
 * service to pick one; a {@code Specification} composes the same predicates at
 * runtime into a single query, and stays Open/Closed as filters are added.
 *
 * <p>The named finders below are kept because they are cheap, self-documenting,
 * and already used by the persistence tests.
 */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findByProject_Id(Long projectId);

    List<Task> findByAssignee_Id(Long assigneeId);

    List<Task> findByStatus(TaskStatus status);

    /** Used to reject deletion of a project that still holds tasks. */
    long countByProject_Id(Long projectId);
}
