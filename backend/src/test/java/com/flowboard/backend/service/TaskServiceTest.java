package com.flowboard.backend.service;

import com.flowboard.backend.ai.RuleBasedTaskAiAssistant;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private final TaskRepository repository = mock(TaskRepository.class);
    private final TaskSuggestionGenerator generator = mock(TaskSuggestionGenerator.class);
    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repository, generator, new RuleBasedTaskAiAssistant());
        // Simula o banco: devolve a própria tarefa, com um id.
        when(repository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(1L);
            }
            return t;
        });
    }

    @Test
    void createSavesImmediatelyAndSchedulesSuggestion() {
        Task task = newTask("Estudar AVL", "prova quinta");

        Task saved = service.createTask(task);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getSuggestionSource()).isEqualTo(SuggestionSource.GENERATING);
        verify(generator).generate(1L, "Estudar AVL", "prova quinta", "HIGH");
    }

    @Test
    void createStillSavesWhenSchedulingFails() {
        doThrow(new TaskRejectedException("fila cheia"))
                .when(generator).generate(anyLong(), anyString(), anyString(), anyString());

        Task saved = service.createTask(newTask("Pagar boleto", "conta de luz"));

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getSuggestionSource()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(saved.getSuggestion()).isNotBlank();
    }

    @Test
    void updateOnlyRegeneratesWhenContentChanges() {
        Task existing = newTask("Estudar AVL", "prova quinta");
        existing.setId(1L);
        existing.setStatus("PENDING");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        Task sameContent = newTask("Estudar AVL", "prova quinta");
        sameContent.setPriority("LOW");
        service.updateTask(1L, sameContent);
        verify(generator, never()).generate(anyLong(), any(), any(), any());

        service.updateTask(1L, newTask("Estudar árvores B", "prova quinta"));
        verify(generator).generate(1L, "Estudar árvores B", "prova quinta", "HIGH");
    }

    private Task newTask(String title, String description) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority("HIGH");
        task.setCategory("STUDY");
        return task;
    }
}
