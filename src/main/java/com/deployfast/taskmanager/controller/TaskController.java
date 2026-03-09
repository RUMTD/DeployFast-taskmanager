package com.deployfast.taskmanager.controller;

import com.deployfast.taskmanager.dto.request.TaskRequest;
import com.deployfast.taskmanager.dto.response.ApiResponse;
import com.deployfast.taskmanager.dto.response.TaskResponse;
import com.deployfast.taskmanager.entity.Task;
import com.deployfast.taskmanager.service.impl.TaskServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la gestion des tâches.
 * Toutes les routes requièrent une authentification JWT.
 * Version: v1
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskServiceImpl taskService;

    /** GET /api/v1/tasks - Liste paginée des tâches */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> getAllTasks(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(required = false) Task.Status status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort).descending());
        Page<TaskResponse> tasks = status != null
                ? taskService.getUserTasksByStatus(user.getUsername(), status, pageable)
                : taskService.getUserTasks(user.getUsername(), pageable);
        return ResponseEntity.ok(ApiResponse.success(tasks, "Tâches récupérées", HttpStatus.OK.value()));
    }

    /** GET /api/v1/tasks/{id} - Détail d'une tâche */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTaskById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        TaskResponse task = taskService.getTaskById(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(task, "Tâche récupérée", HttpStatus.OK.value()));
    }

    /** POST /api/v1/tasks - Création d'une tâche */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserDetails user) {
        TaskResponse task = taskService.createTask(request, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(task, "Tâche créée", HttpStatus.CREATED.value()));
    }

    /** PUT /api/v1/tasks/{id} - Mise à jour d'une tâche */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserDetails user) {
        TaskResponse task = taskService.updateTask(id, request, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(task, "Tâche mise à jour", HttpStatus.OK.value()));
    }

    /** DELETE /api/v1/tasks/{id} - Suppression d'une tâche */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        taskService.deleteTask(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null, "Tâche supprimée", HttpStatus.OK.value()));
    }
}
