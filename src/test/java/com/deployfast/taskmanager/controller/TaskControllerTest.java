package com.deployfast.taskmanager.controller;

import com.deployfast.taskmanager.dto.request.RegisterRequest;
import com.deployfast.taskmanager.dto.request.TaskRequest;
import com.deployfast.taskmanager.entity.Task;
import com.deployfast.taskmanager.repository.TaskRepository;
import com.deployfast.taskmanager.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration (Feature Tests) pour TaskController.
 * Teste le flux complet avec authentification JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests Feature - TaskController")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        jwtToken = registerAndGetToken("taskuser", "taskuser@test.com", "password123");
    }

    @Test
    @DisplayName("Doit créer une tâche avec authentification JWT")
    void shouldCreateTaskWithJwt() throws Exception {
        TaskRequest request = buildTaskRequest("Tâche Feature Test", Task.Priority.HIGH);

        mockMvc.perform(post("/api/v1/tasks")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Tâche Feature Test"))
                .andExpect(jsonPath("$.data.status").value("TODO"));
    }

    @Test
    @DisplayName("Doit refuser l'accès sans token JWT")
    void shouldRejectAccessWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Doit retourner une liste paginée de tâches")
    void shouldReturnPaginatedTasks() throws Exception {
        // Créer 2 tâches
        createTask("Tâche 1", Task.Priority.LOW);
        createTask("Tâche 2", Task.Priority.HIGH);

        mockMvc.perform(get("/api/v1/tasks?page=0&size=10")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("Doit mettre à jour une tâche")
    void shouldUpdateTask() throws Exception {
        Long taskId = createTaskAndGetId("Tâche originale", Task.Priority.LOW);
        TaskRequest updateRequest = buildTaskRequest("Tâche modifiée", Task.Priority.CRITICAL);
        updateRequest.setStatus(Task.Status.IN_PROGRESS);

        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tâche modifiée"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Doit supprimer une tâche")
    void shouldDeleteTask() throws Exception {
        Long taskId = createTaskAndGetId("Tâche à supprimer", Task.Priority.LOW);

        mockMvc.perform(delete("/api/v1/tasks/" + taskId)
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Doit retourner 404 pour une tâche inexistante")
    void shouldReturn404ForNonExistentTask() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/9999")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    // --- Méthodes utilitaires ---

    private String registerAndGetToken(String username, String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private TaskRequest buildTaskRequest(String title, Task.Priority priority) {
        TaskRequest req = new TaskRequest();
        req.setTitle(title);
        req.setDescription("Description de test");
        req.setPriority(priority);
        return req;
    }

    private void createTask(String title, Task.Priority priority) throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildTaskRequest(title, priority))));
    }

    private Long createTaskAndGetId(String title, Task.Priority priority) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildTaskRequest(title, priority))))
                .andReturn();
        String response = result.getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
}
