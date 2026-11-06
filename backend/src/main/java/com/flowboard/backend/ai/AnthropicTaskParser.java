package com.flowboard.backend.ai;

import com.flowboard.backend.model.SuggestionSource;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interpreta texto livre com o modelo da Anthropic. Reaproveita o AnthropicClient
 * (requisição, structured outputs, logs); aqui ficam só mensagem, schema e validação.
 */
public class AnthropicTaskParser implements TaskParser {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final AnthropicClient client;
    private final String systemPrompt;
    private final Map<String, Object> outputSchema = buildOutputSchema();

    public AnthropicTaskParser(AnthropicClient client, String systemPrompt) {
        this.client = client;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public TaskDraft parse(String text, LocalDate today) {
        return client.call("parse", systemPrompt, buildUserMessage(text, today), outputSchema,
                json -> {
                    ModelOutput output = client.readJson(json, ModelOutput.class);
                    return TaskDraft.of(
                            output.title(),
                            output.description(),
                            output.priority(),
                            output.category(),
                            output.dueDate(),
                            today,
                            SuggestionSource.AI);
                });
    }

    /**
     * O modelo não sabe que dia é hoje: sem essa informação, "quinta" ou "amanhã" não têm como
     * virar uma data. Por isso a data (e o dia da semana, para facilitar) vai junto na mensagem.
     */
    String buildUserMessage(String text, LocalDate today) {
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        return """
                <hoje>%s (%s)</hoje>
                <texto>%s</texto>""".formatted(today, weekday, text == null ? "" : text);
    }

    /**
     * O prazo é uma string (vazia quando não há prazo) em vez de um campo que aceita null:
     * deixa o schema mais simples, e a validação de data acontece no Java (TaskDraft.parseDueDate).
     */
    private static Map<String, Object> buildOutputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "description", Map.of("type", "string"),
                        "priority", AiSchemas.priorityProperty(),
                        "category", AiSchemas.categoryProperty(),
                        "dueDate", Map.of("type", "string")
                ),
                "required", List.of("title", "description", "priority", "category", "dueDate"),
                "additionalProperties", false
        );
    }

    /** Formato bruto que o modelo devolve, antes da validação. */
    record ModelOutput(String title, String description, String priority, String category, String dueDate) {
    }
}
