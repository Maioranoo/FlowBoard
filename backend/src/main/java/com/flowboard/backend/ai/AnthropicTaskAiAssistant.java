package com.flowboard.backend.ai;

import com.flowboard.backend.model.SuggestionSource;

import java.util.List;
import java.util.Map;

/**
 * Sugestão de produtividade para uma tarefa, gerada pelo modelo da Anthropic.
 *
 * A chamada HTTP, a checagem do stop_reason e os logs ficam no AnthropicClient (compartilhado).
 * Aqui fica só o que é específico desta funcionalidade: a mensagem, o schema e a validação.
 */
public class AnthropicTaskAiAssistant implements TaskAiAssistant {

    private final AnthropicClient client;
    private final String systemPrompt;
    private final Map<String, Object> outputSchema = buildOutputSchema();

    public AnthropicTaskAiAssistant(AnthropicClient client, String systemPrompt) {
        this.client = client;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public TaskAiSuggestion suggest(String title, String description, String priority) {
        return client.call("sugestão", systemPrompt, buildUserMessage(title, description, priority), outputSchema,
                json -> {
                    ModelOutput output = client.readJson(json, ModelOutput.class);
                    return TaskAiSuggestion.of(
                            output.suggestion(),
                            output.suggestedPriority(),
                            output.suggestedCategory(),
                            output.subtasks(),
                            SuggestionSource.AI);
                });
    }

    /**
     * Os dados do usuário vão delimitados por tags. O prompt do sistema instrui o modelo a tratar
     * esse conteúdo como dado, não como instrução (mitigação básica de prompt injection).
     */
    private String buildUserMessage(String title, String description, String priority) {
        return """
                <tarefa>
                <titulo>%s</titulo>
                <descricao>%s</descricao>
                <prioridade_informada>%s</prioridade_informada>
                </tarefa>""".formatted(
                nullToEmpty(title), nullToEmpty(description), priority == null ? "não informada" : priority);
    }

    /**
     * JSON Schema da resposta. Os valores permitidos de prioridade e categoria vêm dos enums,
     * então código, banco e IA nunca ficam dessincronizados.
     * Limites de tamanho/quantidade não ficam no schema: são pedidos no prompt e garantidos
     * pela validação em TaskAiSuggestion.
     */
    private static Map<String, Object> buildOutputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "suggestion", Map.of("type", "string"),
                        "suggestedPriority", AiSchemas.priorityProperty(),
                        "suggestedCategory", AiSchemas.categoryProperty(),
                        "subtasks", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("suggestion", "suggestedPriority", "suggestedCategory", "subtasks"),
                "additionalProperties", false
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Formato bruto que o modelo devolve, antes da validação. */
    record ModelOutput(String suggestion, String suggestedPriority, String suggestedCategory, List<String> subtasks) {
    }
}
