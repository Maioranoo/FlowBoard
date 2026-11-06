package com.flowboard.backend.dto;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.model.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * O que a API devolve sobre uma tarefa. Separar isso da entidade permite mudar o banco
 * (renomear coluna, adicionar campo interno) sem mudar o contrato com o front-end.
 *
 * Os nomes e formatos dos campos são IGUAIS aos que a entidade gerava antes, por isso o
 * front-end não precisou mudar nada.
 */
public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Priority priority,
        Category category,
        LocalDate dueDate,
        String suggestion,
        Priority suggestedPriority,
        Category suggestedCategory,
        List<String> subtasks,
        SuggestionSource suggestionSource,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getCategory(),
                task.getDueDate(),
                task.getSuggestion(),
                task.getSuggestedPriority(),
                task.getSuggestedCategory(),
                task.getSubtasks(),
                task.getSuggestionSource(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
