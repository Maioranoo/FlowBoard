package com.flowboard.backend.service;

import com.flowboard.backend.ai.AiBudget;
import com.flowboard.backend.ai.RuleBasedTaskAiAssistant;
import com.flowboard.backend.dto.TaskRequest;
import com.flowboard.backend.exception.TaskNotFoundException;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.model.TaskStatus;
import com.flowboard.backend.repository.TaskRepository;
import com.flowboard.backend.user.User;
import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras de negócio do TaskService, sem banco e sem IA: repositórios, gerador de sugestões e
 * orçamento de IA são mocks do Mockito. Toda operação recebe o id do dono (OWNER).
 */
class TaskServiceTest {

    private static final Long OWNER = 10L;
    private static final Long OTHER_OWNER = 20L;

    private final TaskRepository repository = mock(TaskRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TaskSuggestionGenerator generator = mock(TaskSuggestionGenerator.class);
    private final AiBudget aiBudget = mock(AiBudget.class);
    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repository, users, generator, new RuleBasedTaskAiAssistant(), aiBudget);
        when(aiBudget.tryConsume(anyLong())).thenReturn(true);
        when(users.getReferenceById(OWNER)).thenReturn(owner(OWNER));
        // Simula o banco: devolve a própria tarefa, atribuindo um id na primeira gravação.
        when(repository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(1L);
            }
            return t;
        });
    }

    @Test
    void createBuildsTaskFromRequestSavesImmediatelyAndSchedulesSuggestion() {
        TaskRequest request = new TaskRequest("Estudar AVL", "prova quinta", Priority.HIGH, Category.STUDY,
                LocalDate.of(2026, 11, 12));

        Task saved = service.createTask(OWNER, request);

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getOwner().getId()).isEqualTo(OWNER);
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(saved.getCategory()).isEqualTo(Category.STUDY);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getSuggestionSource()).isEqualTo(SuggestionSource.GENERATING);
        verify(generator).generate(1L, "Estudar AVL", "prova quinta", Priority.HIGH);
    }

    @Test
    void createStillSavesWithRulesWhenSchedulingFails() {
        doThrow(new TaskRejectedException("fila cheia"))
                .when(generator).generate(anyLong(), any(), any(), any());

        Task saved = service.createTask(OWNER, request("Pagar boleto", "conta de luz"));

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getSuggestionSource()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(saved.getSuggestion()).isNotBlank();
    }

    @Test
    void exhaustedBudgetUsesRulesWithoutScheduling() {
        when(aiBudget.tryConsume(OWNER)).thenReturn(false);

        Task saved = service.createTask(OWNER, request("Pagar boleto", "conta de luz"));

        verify(generator, never()).generate(anyLong(), any(), any(), any());
        assertThat(saved.getSuggestionSource()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(saved.getSuggestion()).isNotBlank();
    }

    @Test
    void seedTaskWithoutAiUsesRulesAndDoesNotSpendBudget() {
        Task saved = service.createSeedTask(owner(OWNER), request("Marcar dentista", ""), true, false);

        assertThat(saved.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(saved.getSuggestionSource()).isEqualTo(SuggestionSource.FALLBACK);
        verify(aiBudget, never()).tryConsume(anyLong());
        verify(generator, never()).generate(anyLong(), any(), any(), any());
    }

    @Test
    void updateOnlyRegeneratesSuggestionWhenContentChanges() {
        Task existing = existingTask("Estudar AVL", "prova quinta");
        when(repository.findByIdAndOwnerId(1L, OWNER)).thenReturn(Optional.of(existing));

        TaskRequest sameContent = new TaskRequest("Estudar AVL", "prova quinta", Priority.LOW, Category.STUDY, null);
        service.updateTask(OWNER, 1L, sameContent);
        assertThat(existing.getPriority()).isEqualTo(Priority.LOW);
        verify(generator, never()).generate(anyLong(), any(), any(), any());

        service.updateTask(OWNER, 1L, request("Estudar árvores B", "prova quinta"));
        verify(generator).generate(1L, "Estudar árvores B", "prova quinta", Priority.HIGH);
    }

    @Test
    void toggleStatusAlternatesBetweenPendingAndCompleted() {
        Task existing = existingTask("x", "y");
        when(repository.findByIdAndOwnerId(1L, OWNER)).thenReturn(Optional.of(existing));

        assertThat(service.toggleStatus(OWNER, 1L).getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(service.toggleStatus(OWNER, 1L).getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void unknownIdThrowsNotFound() {
        when(repository.findByIdAndOwnerId(99L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTask(OWNER, 99L)).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.toggleStatus(OWNER, 99L)).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.deleteTask(OWNER, 99L)).isInstanceOf(TaskNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void getOfAnotherOwnerThrowsNotFound() {
        // A tarefa 1 existe, mas pertence a OWNER: para OTHER_OWNER ela "não existe".
        when(repository.findByIdAndOwnerId(1L, OWNER)).thenReturn(Optional.of(existingTask("x", "y")));
        when(repository.findByIdAndOwnerId(1L, OTHER_OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTask(OTHER_OWNER, 1L)).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.deleteTask(OTHER_OWNER, 1L)).isInstanceOf(TaskNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    private TaskRequest request(String title, String description) {
        return new TaskRequest(title, description, Priority.HIGH, Category.STUDY, null);
    }

    private static User owner(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Task existingTask(String title, String description) {
        Task task = new Task();
        task.setId(1L);
        task.setOwner(owner(OWNER));
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(Priority.HIGH);
        task.setCategory(Category.STUDY);
        return task;
    }
}
