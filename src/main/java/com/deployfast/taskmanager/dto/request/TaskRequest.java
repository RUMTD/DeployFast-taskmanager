package com.deployfast.taskmanager.dto.request;

import com.deployfast.taskmanager.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO pour la création et mise à jour d'une tâche.
 * Validation stricte des entrées (protection contre injections).
 */
@Data
public class TaskRequest {
    @NotBlank(message = "Le titre est obligatoire")
    @Size(min = 2, max = 100, message = "Le titre doit comporter entre 2 et 100 caractères")
    private String title;

    @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
    private String description;

    private Task.Status status;
    private Task.Priority priority;
    private LocalDateTime dueDate;
}
