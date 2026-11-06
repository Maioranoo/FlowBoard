package com.flowboard.backend.ai;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

/**
 * Rascunho de tarefa interpretado a partir de texto livre. NÃO é salvo: o front-end usa para
 * preencher o formulário, o usuário revisa e só então salva pela rota normal (POST /tasks).
 *
 * Mesma regra de validação das sugestões:
 *  - ERRADO é rejeitado: título vazio, prioridade/categoria fora do enum;
 *  - COSMÉTICO é corrigido: espaços, texto longo demais;
 *  - o prazo é OPCIONAL: se vier malformado ou fora de um intervalo razoável, ele é descartado
 *    (vira "sem prazo") em vez de jogar fora um título e uma categoria que estavam bons.
 */
public record TaskDraft(
        String title,
        String description,
        Priority priority,
        Category category,
        LocalDate dueDate,
        SuggestionSource source
) {

    /** Limite das colunas title e description, que já existem no banco como varchar(255). */
    public static final int MAX_TEXT_LENGTH = 255;
    /** Prazo mais distante aceito. Uma data além disso é mais provavelmente um erro de interpretação. */
    public static final int MAX_YEARS_AHEAD = 2;

    public TaskDraft {
        if (title == null || title.isBlank()) {
            throw new InvalidAiResponseException("title vazio");
        }
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(source, "source");

        title = truncate(title.strip());
        description = description == null ? "" : truncate(description.strip());
    }

    /**
     * Constrói a partir de valores "crus" do modelo.
     *
     * @param dueDate data no formato ISO (AAAA-MM-DD) ou vazio
     * @param today   "hoje" no fuso do usuário, usado para validar o prazo
     */
    public static TaskDraft of(String title, String description, String priority, String category,
                               String dueDate, LocalDate today, SuggestionSource source) {
        return new TaskDraft(
                title,
                description,
                parseEnum(Priority.class, priority, "priority"),
                parseEnum(Category.class, category, "category"),
                parseDueDate(dueDate, today),
                source
        );
    }

    /** Prazo válido = data ISO entre hoje e daqui a MAX_YEARS_AHEAD anos. Qualquer outra coisa vira null. */
    static LocalDate parseDueDate(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.strip());
            if (date.isBefore(today) || date.isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
                return null;
            }
            return date;
        } catch (DateTimeParseException e) {
            return null;
        }
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

    private static String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH - 1).stripTrailing() + "…";
    }
}
