package com.deployfast.taskmanager.repository;

import com.deployfast.taskmanager.entity.Task;
import com.deployfast.taskmanager.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository JPA pour l'entité Task avec support de la pagination.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByOwner(User owner, Pageable pageable);
    Page<Task> findByOwnerAndStatus(User owner, Task.Status status, Pageable pageable);
    long countByOwner(User owner);
}
