package com.flowboard.backend.controller;

import com.flowboard.backend.dto.TaskRequest;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.model.TaskStatus;
import com.flowboard.backend.ratelimit.RateLimiter;
import com.flowboard.backend.service.TaskParseService;
import com.flowboard.backend.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de criação: validação (400 com mensagens por campo) e formato da resposta. */
@WebMvcTest(TaskController.class)
class TaskControllerValidationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskService taskService;
    @MockitoBean
    private TaskParseService taskParseService;
    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    void createReturnsSameJsonShapeAsBefore() throws Exception {
        Task saved = new Task();
        saved.setId(7L);
        saved.setTitle("Estudar AVL");
        saved.setDescription("");
        saved.setStatus(TaskStatus.PENDING);
        saved.setPriority(Priority.HIGH);
        saved.setCategory(Category.STUDY);
        saved.setDueDate(LocalDate.of(2026, 11, 12));
        saved.setSuggestionSource(SuggestionSource.GENERATING);
        saved.setCreatedAt(LocalDateTime.of(2026, 11, 6, 10, 0));
        saved.setUpdatedAt(LocalDateTime.of(2026, 11, 6, 10, 0));
        when(taskService.createTask(any())).thenReturn(saved);

        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "Estudar AVL", "description": "", "priority": "HIGH",
                         "category": "STUDY", "dueDate": "2026-11-12"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-12"))
                .andExpect(jsonPath("$.suggestionSource").value("GENERATING"));
    }

    /** Mass assignment: campos que o cliente não controla são simplesmente ignorados. */
    @Test
    void clientCannotSetIdOrStatus() throws Exception {
        Task saved = new Task();
        saved.setId(1L);
        when(taskService.createTask(any())).thenReturn(saved);

        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": 5, "status": "COMPLETED", "suggestion": "hack",
                         "title": "Nova", "priority": "LOW", "category": "HOME"}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<TaskRequest> captor = ArgumentCaptor.forClass(TaskRequest.class);
        verify(taskService).createTask(captor.capture());
        // O DTO nem tem campos para id/status/suggestion: não há como eles chegarem ao serviço.
        assertThat(captor.getValue().title()).isEqualTo("Nova");
    }

    @Test
    void returnsEveryInvalidFieldWithItsMessage() throws Exception {
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "  ", "description": "%s"}""".formatted("x".repeat(300))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dados inválidos"))
                .andExpect(jsonPath("$.fields.title").value("O título é obrigatório"))
                .andExpect(jsonPath("$.fields.description").value("A descrição pode ter no máximo 255 caracteres"))
                .andExpect(jsonPath("$.fields.priority").value("A prioridade é obrigatória"))
                .andExpect(jsonPath("$.fields.category").value("A categoria é obrigatória"));

        verifyNoInteractions(taskService);
    }

    @Test
    void explainsWhichEnumValuesAreAccepted() throws Exception {
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "x", "priority": "URGENTE", "category": "WORK"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("'priority'")))
                .andExpect(jsonPath("$.message").value(containsString("LOW, MEDIUM, HIGH")));
    }
}
