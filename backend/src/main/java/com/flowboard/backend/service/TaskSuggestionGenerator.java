package com.flowboard.backend.service;

import com.flowboard.backend.ai.AiConfig;
import com.flowboard.backend.ai.TaskAiAssistant;
import com.flowboard.backend.ai.TaskAiSuggestion;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Gera a sugestão de uma tarefa em SEGUNDO PLANO, depois que ela já foi salva.
 *
 * Por que assíncrono? A chamada ao LLM leva de 1 a 3 segundos. Se fosse feita dentro do POST,
 * o usuário esperaria esse tempo para salvar. Assim, o POST responde na hora com a tarefa
 * marcada como GENERATING, e o front-end busca a sugestão quando ela fica pronta.
 *
 * Importante: @Async só funciona quando o método é chamado de OUTRO bean (o Spring intercepta
 * a chamada por um proxy). Por isso esta lógica está numa classe separada do TaskService.
 */
@Component
public class TaskSuggestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(TaskSuggestionGenerator.class);

    private final TaskRepository taskRepository;
    private final TaskAiAssistant taskAiAssistant;

    public TaskSuggestionGenerator(TaskRepository taskRepository, TaskAiAssistant taskAiAssistant) {
        this.taskRepository = taskRepository;
        this.taskAiAssistant = taskAiAssistant;
    }

    /**
     * Recebe o título e a descrição usados no momento do pedido. Antes de gravar, confere se
     * a tarefa não foi editada nesse meio-tempo; se foi, descarta esta sugestão (uma nova
     * geração já foi disparada pela edição) para não gravar uma sugestão velha por cima.
     */
    @Async(AiConfig.AI_EXECUTOR)
    public void generate(Long taskId, String title, String description, Priority priority) {
        try {
            TaskAiSuggestion suggestion = taskAiAssistant.suggest(title, description, priority);

            // Recarrega a tarefa DEPOIS da chamada à IA, para não sobrescrever edições feitas durante ela.
            Task task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                log.info("FlowAI: tarefa {} foi excluída antes de a sugestão ficar pronta", taskId);
                return;
            }
            if (!Objects.equals(task.getTitle(), title) || !Objects.equals(task.getDescription(), description)) {
                log.info("FlowAI: tarefa {} mudou durante a geração; sugestão antiga descartada", taskId);
                return;
            }

            task.applySuggestion(suggestion);
            taskRepository.save(task);
        } catch (RuntimeException e) {
            // Não deveria acontecer (o assistente já tem fallback), mas uma falha aqui nunca pode
            // derrubar nada: a tarefa já está salva. Ela fica como GENERATING e o front para de esperar.
            log.error("FlowAI: falha ao gravar a sugestão da tarefa {}", taskId, e);
        }
    }
}
