package com.taskmanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Represents a Task that belongs to a Project and is optionally assigned to a User.
 *
 * Relationships:
 *  - Many Tasks belong to one Project  (Project 1-N Task)
 *  - Many Tasks are assigned to one User (User 1-N Task as assignee)
 */
@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_project_id", columnList = "project_id"),
        @Index(name = "idx_tasks_assignee_id", columnList = "assignee_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    /**
     * Owning side of the Project 1-N Task relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Project project;

    /**
     * Owning side of the User 1-N Task (assignee) relationship. Nullable: a
     * task may be unassigned.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    @JsonIgnore
    @ToString.Exclude
    private User assignee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /* Convenience read-only accessors so callers/DTOs can work with plain ids
       without forcing lazy relations to be fetched eagerly. */

    @Transient
    public Long getProjectId() {
        return project != null ? project.getId() : null;
    }

    @Transient
    public Long getAssigneeId() {
        return assignee != null ? assignee.getId() : null;
    }
}
