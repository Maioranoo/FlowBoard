package com.flowboard.backend.controller;

import com.flowboard.backend.ai.TaskDraft;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.ratelimit.RateLimitExceededException;
import com.flowboard.backend.ratelimit.RateLimiter;
import com.flowboard.backend.service.TaskParseService;
import com.flowboard.backend.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa só a camada HTTP do POST /tasks/parse (@WebMvcTest sobe o MVC sem banco e sem IA).
 * Os serviços são mocks: aqui interessa o contrato da API (status, JSON, cabeçalhos).
 */
@WebMvcTest(TaskController.class)
class TaskControllerParseTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskService taskService;
    @MockitoBean
    private TaskParseService taskParseService;
    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    void returnsDraftWithoutSaving() throws Exception {
        when(taskParseService.parse("pagar boleto quinta")).thenReturn(new TaskDraft(
                "Pagar boleto", "", Priority.MEDIUM, Category.FINANCE, LocalDate.of(2026, 11, 12), SuggestionSource.AI));

        mvc.perform(post("/tasks/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "200.1.1.1, 10.0.0.1")
                        .content("{\"text\": \"pagar boleto quinta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Pagar boleto"))
                .andExpect(jsonPath("$.category").value("FINANCE"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-12"))
                .andExpect(jsonPath("$.source").value("AI"));

        verify(rateLimiter).acquire("200.1.1.1"); // primeiro IP da lista = cliente original
        verifyNoInteractions(taskService);        // nada é salvo
    }

    @Test
    void rejectsBlankText() throws Exception {
        mvc.perform(post("/tasks/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskParseService);
    }

    @Test
    void returns429WithRetryAfterWhenLimited() throws Exception {
        doThrow(new RateLimitExceededException(42)).when(rateLimiter).acquire(anyString());

        mvc.perform(post("/tasks/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"qualquer coisa\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"));

        verifyNoInteractions(taskParseService);
    }
}
