package com.flowboard.backend.service;

import com.flowboard.backend.ai.AiBudget;
import com.flowboard.backend.ai.RuleBasedTaskAiAssistant;
import com.flowboard.backend.dto.TaskRequest;
import com.flowboard.backend.exception.TaskNotFoundException;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.model.TaskStatus;
import com.flowboard.backend.repository.TaskRepository;
import com.flowboard.backend.user.User;
import com.flowboard.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Regras de negócio das tarefas. Cada método recebe o id do dono (vindo do token, nunca do corpo
 * da requisição) e só enxerga as tarefas dele: a tarefa de outra pessoa dá TaskNotFoundException (404),
 * a mesma resposta de uma tarefa inexistente, para não revelar que ela existe.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskSuggestionGenerator suggestionGenerator;
    private final RuleBasedTaskAiAssistant ruleBasedAssistant;
    private final AiBudget aiBudget;

    public TaskService(TaskRepository taskRepository,
                       UserRepository userRepository,
                       TaskSuggestionGenerator suggestionGenerator,
                       RuleBasedTaskAiAssistant ruleBasedAssistant,
                       AiBudget aiBudget) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.suggestionGenerator = suggestionGenerator;
        this.ruleBasedAssistant = ruleBasedAssistant;
        this.aiBudget = aiBudget;
    }

    public Task createTask(Long ownerId, TaskRequest request) {
        // getReferenceById não faz SELECT: só precisamos do id para gravar a chave estrangeira.
        Task saved = save(newTask(userRepository.getReferenceById(ownerId), request));
        requestSuggestion(saved, ownerId, true);
        return saved;
    }

    /**
     * Tarefa de exemplo de um visitante novo. {@code useAi = false} quando o limite de visitantes com IA
     * foi atingido: aí a sugestão sai das regras locais e nada do orçamento é gasto.
     */
    public Task createSeedTask(User owner, TaskRequest request, boolean completed, boolean useAi) {
        Task task = newTask(owner, request);
        if (completed) {
            task.setStatus(TaskStatus.COMPLETED);
        }
        Task saved = save(task);
        requestSuggestion(saved, owner.getId(), useAi);
        return saved;
    }

    public List<Task> listTasks(Long ownerId) {
        return taskRepository.findAllByOwnerIdOrderByIdAsc(ownerId);
    }

    public Task getTask(Long ownerId, Long id) {
        return taskRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(TaskNotFoundException::new);
    }

    public Task updateTask(Long ownerId, Long id, TaskRequest request) {
        Task task = getTask(ownerId, id);

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
            requestSuggestion(saved, ownerId, true);
        }
        return saved;
    }

    public Task toggleStatus(Long ownerId, Long id) {
        Task task = getTask(ownerId, id);

        task.setStatus(task.getStatus().toggle());
        task.setUpdatedAt(LocalDateTime.now());

        return taskRepository.save(task);
    }

    public void deleteTask(Long ownerId, Long id) {
        taskRepository.delete(getTask(ownerId, id));
    }

    private Task newTask(User owner, TaskRequest request) {
        // A entidade é montada aqui, a partir do DTO: id, dono, status e datas são decididos pelo servidor.
        Task task = new Task();
        task.setOwner(owner);
        copyEditableFields(request, task);
        task.setStatus(TaskStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.markSuggestionGenerating();
        return task;
    }

    /** Salva PRIMEIRO. A IA nunca impede a criação da tarefa. */
    private Task save(Task task) {
        return taskRepository.save(task);
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
     * Decide de onde vem a sugestão:
     *  - IA em segundo plano, se permitido e se o usuário ainda tem orçamento no dia;
     *  - senão, regras locais na hora (instantâneas e gratuitas).
     * Se não for possível agendar a geração (ex.: fila do executor cheia), também usa as regras.
     */
    private void requestSuggestion(Task task, Long ownerId, boolean useAi) {
        if (!useAi || !aiBudget.tryConsume(ownerId)) {
            applyRules(task);
            return;
        }
        try {
            suggestionGenerator.generate(task.getId(), task.getTitle(), task.getDescription(), task.getPriority());
        } catch (RuntimeException e) {
            log.warn("FlowAI: não foi possível agendar a sugestão da tarefa {} ({}); usando regras locais",
                    task.getId(), e.getClass().getSimpleName());
            applyRules(task);
        }
    }

    private void applyRules(Task task) {
        task.applySuggestion(ruleBasedAssistant.suggest(task.getTitle(), task.getDescription(), task.getPriority()));
        taskRepository.save(task);
    }
}
