package com.taskmanagement.repository;

import com.taskmanagement.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository abstraction for {@link Project}.
 *
 * <p>The paginated overload is the one used by the API; the {@link List}
 * variant is retained for internal callers and tests that legitimately want the
 * full (small) result set.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);
}
