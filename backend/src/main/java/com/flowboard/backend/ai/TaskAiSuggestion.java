package com.flowboard.backend.ai;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Sugestão de IA já validada. Se um objeto deste tipo existe, ele é seguro para salvar e exibir.
 *
 * Regra de validação adotada:
 *  - o que é ERRADO é rejeitado (texto vazio, prioridade/categoria desconhecida, campo ausente);
 *  - o que é só COSMÉTICO é corrigido (espaços extras, texto longo demais, subtarefas em excesso).
 * Assim, uma resposta boa que passou um pouco do tamanho não é jogada fora.
 */
public record TaskAiSuggestion(
        String suggestion,
        Priority suggestedPriority,
        Category suggestedCategory,
        List<String> subtasks,
        SuggestionSource source
) {

    /** Limite imposto pela coluna "suggestion" (varchar(255)) que já existe no banco. */
    public static final int MAX_SUGGESTION_LENGTH = 255;
    public static final int MAX_SUBTASKS = 5;
    public static final int MAX_SUBTASK_LENGTH = 120;

    public TaskAiSuggestion {
        if (suggestion == null || suggestion.isBlank()) {
            throw new InvalidAiResponseException("suggestion vazia");
        }
        Objects.requireNonNull(suggestedPriority, "suggestedPriority");
        Objects.requireNonNull(suggestedCategory, "suggestedCategory");
        Objects.requireNonNull(source, "source");
        if (source == SuggestionSource.GENERATING) {
            throw new IllegalArgumentException("Uma sugestão pronta não pode ter origem GENERATING");
        }

        suggestion = truncate(suggestion.strip(), MAX_SUGGESTION_LENGTH);
        subtasks = subtasks == null ? List.of() : subtasks.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(s -> truncate(s, MAX_SUBTASK_LENGTH))
                .limit(MAX_SUBTASKS)
                .toList();
    }

    /**
     * Constrói a partir de valores "crus" (strings vindas do modelo).
     * Converte prioridade e categoria para os enums, rejeitando valores desconhecidos.
     */
    public static TaskAiSuggestion of(String suggestion, String priority, String category,
                                      List<String> subtasks, SuggestionSource source) {
        return new TaskAiSuggestion(
                suggestion,
                parseEnum(Priority.class, priority, "suggestedPriority"),
                parseEnum(Category.class, category, "suggestedCategory"),
                subtasks,
                source
        );
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidAiResponseException(field + " ausente");
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidAiResponseException(field + " inválido: " + value);
        }
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1).stripTrailing() + "…";
    }
}
