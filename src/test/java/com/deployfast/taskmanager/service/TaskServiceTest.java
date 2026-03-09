package com.deployfast.taskmanager.service;

import com.deployfast.taskmanager.dto.request.TaskRequest;
import com.deployfast.taskmanager.dto.response.TaskResponse;
import com.deployfast.taskmanager.entity.Task;
import com.deployfast.taskmanager.entity.User;
import com.deployfast.taskmanager.exception.ResourceNotFoundException;
import com.deployfast.taskmanager.repository.TaskRepository;
import com.deployfast.taskmanager.repository.UserRepository;
import com.deployfast.taskmanager.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour TaskServiceImpl.
 * Couverture des cas nominaux et cas d'erreur.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests unitaires - TaskService")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private User testUser;
    private Task testTask;
    private TaskRequest taskRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@test.com")
                .password("encoded")
                .role(User.Role.ROLE_USER)
                .build();

        testTask = Task.builder()
                .id(1L)
                .title("Tâche test")
                .description("Description test")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .owner(testUser)
                .build();

        taskRequest = new TaskRequest();
        taskRequest.setTitle("Nouvelle tâche");
        taskRequest.setDescription("Description");
        taskRequest.setStatus(Task.Status.TODO);
        taskRequest.setPriority(Task.Priority.HIGH);
    }

    @Test
    @DisplayName("Doit créer une tâche avec succès")
    void shouldCreateTaskSuccessfully() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        TaskResponse result = taskService.createTask(taskRequest, "testuser");

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Tâche test");
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    @DisplayName("Doit retourner les tâches paginées de l'utilisateur")
    void shouldReturnPaginatedUserTasks() {
        Page<Task> taskPage = new PageImpl<>(List.of(testTask));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(taskRepository.findByOwner(eq(testUser), any())).thenReturn(taskPage);

        Page<TaskResponse> result = taskService.getUserTasks("testuser", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Tâche test");
    }

    @Test
    @DisplayName("Doit lever ResourceNotFoundException si tâche introuvable")
    void shouldThrowWhenTaskNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(99L, "testuser"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Doit lever AccessDeniedException si l'utilisateur n'est pas propriétaire")
    void shouldThrowWhenNotOwner() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        assertThatThrownBy(() -> taskService.getTaskById(1L, "autreuser"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Doit mettre à jour une tâche avec succès")
    void shouldUpdateTaskSuccessfully() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(taskRepository.save(any(Task.class))).thenReturn(testTask);

        TaskResponse result = taskService.updateTask(1L, taskRequest, "testuser");

        assertThat(result).isNotNull();
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    @DisplayName("Doit supprimer une tâche avec succès")
    void shouldDeleteTaskSuccessfully() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        doNothing().when(taskRepository).delete(any(Task.class));

        assertThatCode(() -> taskService.deleteTask(1L, "testuser"))
                .doesNotThrowAnyException();
        verify(taskRepository, times(1)).delete(testTask);
    }

    @Test
    @DisplayName("Doit lever ResourceNotFoundException si utilisateur introuvable")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.createTask(taskRequest, "inconnu"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
