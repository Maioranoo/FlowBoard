package com.flowboard.backend.service;

import com.flowboard.backend.ai.RuleBasedTaskAiAssistant;
import com.flowboard.backend.dto.TaskRequest;
import com.flowboard.backend.exception.TaskNotFoundException;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.model.TaskStatus;
import com.flowboard.backend.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service //logica de negócio, camada de serviço
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskSuggestionGenerator suggestionGenerator;
    private final RuleBasedTaskAiAssistant ruleBasedAssistant;

    public TaskService(TaskRepository taskRepository,
                       TaskSuggestionGenerator suggestionGenerator,
                       RuleBasedTaskAiAssistant ruleBasedAssistant) {
        this.taskRepository = taskRepository;
        this.suggestionGenerator = suggestionGenerator;
        this.ruleBasedAssistant = ruleBasedAssistant;
    }

    public Task createTask(TaskRequest request) {
        // A entidade é montada aqui, a partir do DTO: id, status e datas são decididos pelo servidor.
        Task task = new Task();
        copyEditableFields(request, task);
        task.setStatus(TaskStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.markSuggestionGenerating();

        // Salva PRIMEIRO. A IA nunca impede a criação da tarefa.
        Task saved = taskRepository.save(task);
        requestSuggestion(saved);
        return saved;
    }

    public List<Task> listTasks() {
        return taskRepository.findAll();
    }

    public Task getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(TaskNotFoundException::new);
    }

    public Task updateTask(Long id, TaskRequest request) {
        Task task = getTask(id);

        // Só gera nova sugestão se o conteúdo mudou: cada chamada à IA custa tempo e dinheiro.
        boolean contentChanged = !Objects.equals(task.getTitle(), request.title())
                || !Objects.equals(task.getDescription(), request.description());

        copyEditableFields(request, task);
        task.setUpdatedAt(LocalDateTime.now());

        if (contentChanged) {
            task.markSuggestionGenerating();
        }

        Task saved = taskRepository.save(task);
        if (contentChanged) {
            requestSuggestion(saved);
        }
        return saved;
    }

    public Task toggleStatus(Long id) {
        Task task = getTask(id);

        task.setStatus(task.getStatus().toggle());
        task.setUpdatedAt(LocalDateTime.now());

        return taskRepository.save(task);
    }

    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException();
        }

        taskRepository.deleteById(id);
    }

    /** Os únicos campos que o cliente pode alterar. */
    private void copyEditableFields(TaskRequest request, Task task) {
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setCategory(request.category());
        task.setDueDate(request.dueDate());
    }

    /**
     * Agenda a geração da sugestão em segundo plano. Se não for possível agendar
     * (ex.: fila do executor cheia), aplica as regras locais na hora, que são instantâneas.
     */
    private void requestSuggestion(Task task) {
        try {
            suggestionGenerator.generate(task.getId(), task.getTitle(), task.getDescription(), task.getPriority());
        } catch (RuntimeException e) {
            log.warn("FlowAI: não foi possível agendar a sugestão da tarefa {} ({}); usando regras locais",
                    task.getId(), e.getClass().getSimpleName());
            task.applySuggestion(ruleBasedAssistant.suggest(task.getTitle(), task.getDescription(), task.getPriority()));
            taskRepository.save(task);
        }
    }
}
