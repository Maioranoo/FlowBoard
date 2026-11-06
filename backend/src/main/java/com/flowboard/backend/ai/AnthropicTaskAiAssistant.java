package com.flowboard.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Implementação que chama a API de Mensagens da Anthropic (POST /v1/messages) via RestClient.
 *
 * Fluxo:
 *  1. monta a requisição: prompt do sistema (arquivo) + dados da tarefa + JSON Schema da saída;
 *  2. a API usa "structured outputs" (output_config.format) para obrigar o modelo a responder
 *     exatamente naquele formato;
 *  3. mesmo assim, o código confere tudo: a resposta pode vir cortada (max_tokens), recusada
 *     (refusal) ou com valores fora do permitido. Qualquer problema vira InvalidAiResponseException,
 *     e quem chamou decide o que fazer (o ResilientTaskAiAssistant usa o fallback).
 */
public class AnthropicTaskAiAssistant implements TaskAiAssistant {

    private static final Logger log = LoggerFactory.getLogger(AnthropicTaskAiAssistant.class);

    /** Versão da API de Mensagens enviada no header obrigatório "anthropic-version". */
    static final String ANTHROPIC_VERSION = "2026-10-21";

    private final RestClient restClient;
    private final AnthropicProperties properties;
    private final String systemPrompt;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> outputSchema;

    public AnthropicTaskAiAssistant(RestClient restClient, AnthropicProperties properties,
                                    String systemPrompt, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.systemPrompt = systemPrompt;
        this.objectMapper = objectMapper;
        this.outputSchema = buildOutputSchema();
    }

    @Override
    public TaskAiSuggestion suggest(String title, String description, String priority) {
        Map<String, Object> request = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", buildUserMessage(title, description, priority))),
                "output_config", Map.of(
                        // Tarefa simples: esforço baixo = resposta mais rápida e mais barata.
                        "effort", "low",
                        "format", Map.of("type", "json_schema", "schema", outputSchema)
                )
        );

        long start = System.nanoTime();
        JsonNode response = restClient.post()
                .uri("/v1/messages")
                .header("x-api-key", properties.apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()          // respostas 4xx/5xx viram RestClientResponseException
                .body(JsonNode.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        TaskAiSuggestion suggestion = parseResponse(response);

        log.info("FlowAI: sugestão gerada por {} em {} ms (tokens entrada={}, saída={})",
                properties.model(), elapsedMs,
                response.path("usage").path("input_tokens").asInt(-1),
                response.path("usage").path("output_tokens").asInt(-1));
        return suggestion;
    }

    TaskAiSuggestion parseResponse(JsonNode response) {
        if (response == null) {
            throw new InvalidAiResponseException("resposta vazia da API");
        }

        // "end_turn" = o modelo terminou normalmente. "max_tokens" = cortado no meio (JSON incompleto).
        // "refusal" = o modelo se recusou a responder. Só aceitamos o primeiro.
        String stopReason = response.path("stop_reason").asText("");
        if (!"end_turn".equals(stopReason)) {
            throw new InvalidAiResponseException("stop_reason inesperado: " + stopReason);
        }

        // O conteúdo é uma lista de blocos; pode haver blocos de "thinking" antes do texto.
        String json = null;
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                json = block.path("text").asText();
                break;
            }
        }
        if (json == null || json.isBlank()) {
            throw new InvalidAiResponseException("resposta sem bloco de texto");
        }

        ModelOutput output;
        try {
            output = objectMapper.readValue(json, ModelOutput.class);
        } catch (JsonProcessingException e) {
            throw new InvalidAiResponseException("JSON inválido na resposta do modelo", e);
        }

        return TaskAiSuggestion.of(
                output.suggestion(),
                output.suggestedPriority(),
                output.suggestedCategory(),
                output.subtasks(),
                SuggestionSource.AI
        );
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
                        "suggestedPriority", Map.of("type", "string", "enum", enumNames(Priority.values())),
                        "suggestedCategory", Map.of("type", "string", "enum", enumNames(Category.values())),
                        "subtasks", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("suggestion", "suggestedPriority", "suggestedCategory", "subtasks"),
                "additionalProperties", false
        );
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Formato bruto que o modelo devolve, antes da validação. */
    record ModelOutput(String suggestion, String suggestedPriority, String suggestedCategory, List<String> subtasks) {
    }
}
