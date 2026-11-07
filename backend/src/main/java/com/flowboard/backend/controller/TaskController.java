package com.flowboard.backend.controller;

import com.flowboard.backend.ai.TaskDraft;
import com.flowboard.backend.dto.ParseTaskRequest;
import com.flowboard.backend.dto.TaskRequest;
import com.flowboard.backend.dto.TaskResponse;
import com.flowboard.backend.ratelimit.RateLimiter;
import com.flowboard.backend.security.AuthUser;
import com.flowboard.backend.service.TaskParseService;
import com.flowboard.backend.service.TaskService;
import com.flowboard.backend.web.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Camada HTTP. Recebe e devolve só DTOs (TaskRequest / TaskResponse): a entidade Task
 * nunca sai nem entra diretamente por aqui.
 *
 * O usuário logado chega pronto em @AuthenticationPrincipal (montado pela segurança a partir do
 * token). O dono das tarefas vem SEMPRE daí, nunca de um campo enviado pelo cliente.
 */
@RestController//recebe requições HTTP
@RequestMapping("/tasks")
@Tag(name = "Tarefas", description = "CRUD de tarefas e criação por linguagem natural")
public class TaskController {

    private final TaskService taskService;
    private final TaskParseService taskParseService;
    private final RateLimiter parseRateLimiter;

    public TaskController(TaskService taskService, TaskParseService taskParseService,
                          @Qualifier("parseRateLimiter") RateLimiter parseRateLimiter) {
        this.taskService = taskService;
        this.taskParseService = taskParseService;
        this.parseRateLimiter = parseRateLimiter;
    }

    @PostMapping
    @Operation(summary = "Cria uma tarefa",
            description = "Salva na hora e responde com suggestionSource=GENERATING; a sugestão da IA é gerada em segundo plano.")
    public TaskResponse createTask(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TaskRequest request) {
        return TaskResponse.from(taskService.createTask(user.id(), request));
    }

    @GetMapping
    @Operation(summary = "Lista as tarefas do usuário logado")
    public List<TaskResponse> listTasks(@AuthenticationPrincipal AuthUser user) {
        return taskService.listTasks(user.id()).stream().map(TaskResponse::from).toList();
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
        parseRateLimiter.acquire(ClientIp.of(http));
        return taskParseService.parse(request.text());
    }

    // Usado pelo front para buscar a sugestão quando ela termina de ser gerada em segundo plano.
    @GetMapping("/{id}")
    @Operation(summary = "Busca uma tarefa pelo id")
    public TaskResponse getTask(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return TaskResponse.from(taskService.getTask(user.id(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita uma tarefa",
            description = "Se título ou descrição mudarem, uma nova sugestão é gerada em segundo plano.")
    public TaskResponse updateTask(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                   @Valid @RequestBody TaskRequest request) {
        return TaskResponse.from(taskService.updateTask(user.id(), id, request));
    }

    @PatchMapping("/{id}/toggle-status")//cria a rota para trocar de pendente para completo(toggle)
    @Operation(summary = "Alterna o status entre PENDING e COMPLETED")
    public TaskResponse toggleStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return TaskResponse.from(taskService.toggleStatus(user.id(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma tarefa")
    public void deleteTask(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        taskService.deleteTask(user.id(), id);
    }

}
