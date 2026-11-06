package com.flowboard.backend.service;

import com.flowboard.backend.ai.TaskAiAssistant;
import com.flowboard.backend.ai.TaskAiSuggestion;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Aqui o método é chamado direto (sem o proxy do Spring), então roda de forma síncrona no teste. */
class TaskSuggestionGeneratorTest {

    private final TaskRepository repository = mock(TaskRepository.class);
    private final TaskAiAssistant assistant = mock(TaskAiAssistant.class);
    private final TaskSuggestionGenerator generator = new TaskSuggestionGenerator(repository, assistant);

    private final TaskAiSuggestion suggestion = new TaskAiSuggestion(
            "Dica", Priority.HIGH, Category.STUDY, List.of("Passo 1"), SuggestionSource.AI);

    @Test
    void savesSuggestionOnTask() {
        Task task = task("Estudar", "AVL");
        when(assistant.suggest("Estudar", "AVL", Priority.LOW)).thenReturn(suggestion);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        generator.generate(1L, "Estudar", "AVL", Priority.LOW);

        assertThat(task.getSuggestionSource()).isEqualTo(SuggestionSource.AI);
        assertThat(task.getSubtasks()).containsExactly("Passo 1");
        verify(repository).save(task);
    }

    @Test
    void discardsSuggestionIfTaskWasEditedMeanwhile() {
        Task task = task("Título novo", "AVL");
        when(assistant.suggest(any(), any(), any())).thenReturn(suggestion);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        generator.generate(1L, "Título antigo", "AVL", Priority.LOW);

        verify(repository, never()).save(any());
    }

    @Test
    void neverThrows() {
        when(assistant.suggest(any(), any(), any())).thenThrow(new IllegalStateException("bug"));

        generator.generate(1L, "x", "y", Priority.LOW); // não deve lançar exceção

        verify(repository, never()).save(any());
    }

    private Task task(String title, String description) {
        Task task = new Task();
        task.setId(1L);
        task.setTitle(title);
        task.setDescription(description);
        task.markSuggestionGenerating();
        return task;
    }
}
