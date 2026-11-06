package com.flowboard.backend.controller;

import com.flowboard.backend.ai.TaskDraft;
import com.flowboard.backend.dto.ParseTaskRequest;
import com.flowboard.backend.dto.TaskRequest;
import com.flowboard.backend.dto.TaskResponse;
import com.flowboard.backend.ratelimit.RateLimiter;
import com.flowboard.backend.service.TaskParseService;
import com.flowboard.backend.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Camada HTTP. Recebe e devolve só DTOs (TaskRequest / TaskResponse): a entidade Task
 * nunca sai nem entra diretamente por aqui.
 */
@RestController//recebe requições HTTP
@RequestMapping("/tasks")
@Tag(name = "Tarefas", description = "CRUD de tarefas e criação por linguagem natural")
public class TaskController {

    private final TaskService taskService;
    private final TaskParseService taskParseService;
    private final RateLimiter parseRateLimiter;

    public TaskController(TaskService taskService, TaskParseService taskParseService, RateLimiter parseRateLimiter) {
        this.taskService = taskService;
        this.taskParseService = taskParseService;
        this.parseRateLimiter = parseRateLimiter;
    }

    @PostMapping
    @Operation(summary = "Cria uma tarefa",
            description = "Salva na hora e responde com suggestionSource=GENERATING; a sugestão da IA é gerada em segundo plano.")
    public TaskResponse createTask(@Valid @RequestBody TaskRequest request) {
        return TaskResponse.from(taskService.createTask(request));
    }

    @GetMapping
    @Operation(summary = "Lista todas as tarefas")
    public List<TaskResponse> listTasks() {
        return taskService.listTasks().stream().map(TaskResponse::from).toList();
    }

    /**
     * Interpreta uma frase e devolve a tarefa preenchida, SEM salvar.
     * O front mostra o rascunho no formulário; o usuário revisa e salva pelo POST /tasks.
     * Responde 400 para texto vazio ou longo demais e 429 quando o limite de uso é atingido.
     */
    @PostMapping("/parse")
    @Operation(summary = "Interpreta texto livre e devolve um rascunho de tarefa (não salva)",
            description = "Ex.: \"estudar AVL pra prova quinta, urgente\". Limite: 10 chamadas/min por cliente e 60/min no total (429).")
    public TaskDraft parseTask(@Valid @RequestBody ParseTaskRequest request, HttpServletRequest http) {
        parseRateLimiter.acquire(clientKey(http));
        return taskParseService.parse(request.text());
    }

    // Usado pelo front para buscar a sugestão quando ela termina de ser gerada em segundo plano.
    @GetMapping("/{id}")
    @Operation(summary = "Busca uma tarefa pelo id")
    public TaskResponse getTask(@PathVariable Long id) {
        return TaskResponse.from(taskService.getTask(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita uma tarefa",
            description = "Se título ou descrição mudarem, uma nova sugestão é gerada em segundo plano.")
    public TaskResponse updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return TaskResponse.from(taskService.updateTask(id, request));
    }

    @PatchMapping("/{id}/toggle-status")//cria a rota para trocar de pendente para completo(toggle)
    @Operation(summary = "Alterna o status entre PENDING e COMPLETED")
    public TaskResponse toggleStatus(@PathVariable Long id) {
        return TaskResponse.from(taskService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma tarefa")
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
    }

    /**
     * Identifica o cliente para o limite de uso. Atrás do proxy do Render, getRemoteAddr() é o IP
     * do proxy (igual para todo mundo); o IP original vem no X-Forwarded-For. Esse cabeçalho pode
     * ser falsificado pelo cliente, por isso também existe um limite global (ver RateLimiter).
     */
    private static String clientKey(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return http.getRemoteAddr();
    }
}
