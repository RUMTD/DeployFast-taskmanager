package com.deployfast.taskmanager.service.impl;

import com.deployfast.taskmanager.dto.request.TaskRequest;
import com.deployfast.taskmanager.dto.response.TaskResponse;
import com.deployfast.taskmanager.entity.Task;
import com.deployfast.taskmanager.entity.User;
import com.deployfast.taskmanager.exception.ResourceNotFoundException;
import com.deployfast.taskmanager.repository.TaskRepository;
import com.deployfast.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des tâches (CRUD complet avec pagination).
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    /** Récupère toutes les tâches de l'utilisateur connecté avec pagination. */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getUserTasks(String username, Pageable pageable) {
        User user = findUserOrThrow(username);
        return taskRepository.findByOwner(user, pageable).map(TaskResponse::from);
    }

    /** Récupère les tâches filtrées par statut. */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getUserTasksByStatus(String username, Task.Status status, Pageable pageable) {
        User user = findUserOrThrow(username);
        return taskRepository.findByOwnerAndStatus(user, status, pageable).map(TaskResponse::from);
    }

    /** Récupère une tâche par son id (vérification propriétaire). */
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id, String username) {
        Task task = findTaskOrThrow(id);
        ensureOwnership(task, username);
        return TaskResponse.from(task);
    }

    /** Crée une nouvelle tâche. */
    @Transactional
    public TaskResponse createTask(TaskRequest request, String username) {
        User user = findUserOrThrow(username);
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : Task.Status.TODO)
                .priority(request.getPriority() != null ? request.getPriority() : Task.Priority.MEDIUM)
                .dueDate(request.getDueDate())
                .owner(user)
                .build();
        return TaskResponse.from(taskRepository.save(task));
    }

    /** Met à jour une tâche existante. */
    @Transactional
    public TaskResponse updateTask(Long id, TaskRequest request, String username) {
        Task task = findTaskOrThrow(id);
        ensureOwnership(task, username);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getDueDate() != null) task.setDueDate(request.getDueDate());
        return TaskResponse.from(taskRepository.save(task));
    }

    /** Supprime une tâche. */
    @Transactional
    public void deleteTask(Long id, String username) {
        Task task = findTaskOrThrow(id);
        ensureOwnership(task, username);
        taskRepository.delete(task);
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable: " + username));
    }

    private Task findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tâche introuvable avec l'id: " + id));
    }

    private void ensureOwnership(Task task, String username) {
        if (!task.getOwner().getUsername().equals(username)) {
            throw new AccessDeniedException("Vous n'avez pas accès à cette tâche");
        }
    }
}
