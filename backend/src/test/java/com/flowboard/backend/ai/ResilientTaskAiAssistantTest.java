package com.flowboard.backend.ai;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResilientTaskAiAssistantTest {

    private final TaskAiAssistant primary = mock(TaskAiAssistant.class);
    private final TaskAiAssistant fallback = new RuleBasedTaskAiAssistant();

    @Test
    void usesAiWhenItWorks() {
        TaskAiSuggestion aiSuggestion = new TaskAiSuggestion(
                "Dica da IA", Priority.HIGH, Category.STUDY, List.of("Passo 1"), SuggestionSource.AI);
        when(primary.suggest(any(), any(), any())).thenReturn(aiSuggestion);

        TaskAiSuggestion result = new ResilientTaskAiAssistant(primary, fallback, true)
                .suggest("Estudar", null, "LOW");

        assertThat(result).isEqualTo(aiSuggestion);
    }

    @Test
    void fallsBackOnTimeout() {
        when(primary.suggest(any(), any(), any())).thenThrow(new ResourceAccessException("timeout"));

        TaskAiSuggestion result = new ResilientTaskAiAssistant(primary, fallback, true)
                .suggest("Estudar para a prova", null, "LOW");

        assertThat(result.source()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(result.suggestedCategory()).isEqualTo(Category.STUDY);
    }

    @Test
    void fallsBackOnInvalidResponse() {
        when(primary.suggest(any(), any(), any())).thenThrow(new InvalidAiResponseException("JSON inválido"));

        TaskAiSuggestion result = new ResilientTaskAiAssistant(primary, fallback, true)
                .suggest("Pagar boleto", null, "HIGH");

        assertThat(result.source()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(result.suggestedCategory()).isEqualTo(Category.FINANCE);
    }

    @Test
    void doesNotCallApiWithoutKey() {
        TaskAiSuggestion result = new ResilientTaskAiAssistant(primary, fallback, false)
                .suggest("Ir na academia", null, null);

        assertThat(result.source()).isEqualTo(SuggestionSource.FALLBACK);
        verifyNoInteractions(primary);
    }
}
