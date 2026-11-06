package com.flowboard.backend.controller;

import com.flowboard.backend.ai.TaskDraft;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.ratelimit.RateLimiter;
import com.flowboard.backend.service.TaskParseService;
import com.flowboard.backend.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController//recebe requições HTTP
@RequestMapping("/tasks")
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
    public Task createTask(@RequestBody Task task) {
        return taskService.createTask(task);
    }

    /**
     * Interpreta uma frase e devolve a tarefa preenchida, SEM salvar.
     * O front mostra o rascunho no formulário; o usuário revisa e salva pelo POST /tasks.
     * Responde 400 para texto vazio ou longo demais e 429 quando o limite de uso é atingido.
     */
    @PostMapping("/parse")
    public TaskDraft parseTask(@Valid @RequestBody ParseTaskRequest request, HttpServletRequest http) {
        parseRateLimiter.acquire(clientKey(http));
        return taskParseService.parse(request.text());
    }

    @GetMapping
    public List<Task> listTasks() {
        return taskService.listTasks();
    }

    // Usado pelo front para buscar a sugestão quando ela termina de ser gerada em segundo plano.
    @GetMapping("/{id}")
    public Task getTask(@PathVariable Long id) {
        return taskService.getTask(id);
    }

    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id, @RequestBody Task task) {
        return taskService.updateTask(id, task);
    }
    @PatchMapping("/{id}/toggle-status")//cria a rota para trocar de pendente para completo(toggle)
    public Task toggleStatus(@PathVariable Long id) {
        return taskService.toggleStatus(id);
    }
    @DeleteMapping("/{id}")
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