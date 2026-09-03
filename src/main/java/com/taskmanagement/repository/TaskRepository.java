package com.taskmanagement.repository;

import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProject_Id(Long projectId);
    List<Task> findByAssignee_Id(Long assigneeId);
    List<Task> findByStatus(TaskStatus status);
    List<Task> findByProject_IdAndStatus(Long projectId, TaskStatus status);
}
