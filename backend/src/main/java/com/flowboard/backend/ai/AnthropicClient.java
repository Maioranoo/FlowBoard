package com.flowboard.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Cliente da API de Mensagens da Anthropic (POST /v1/messages), compartilhado por todas as
 * funcionalidades de IA do FlowBoard (sugestões, criação por linguagem natural...).
 *
 * Ele cuida do que é IGUAL em toda chamada:
 *  - montar a requisição com prompt do sistema, mensagem e JSON Schema (structured outputs);
 *  - conferir o stop_reason e extrair o texto da resposta;
 *  - registrar em log tempo e tokens (sucesso) ou o conteúdo rejeitado (falha).
 *
 * Cada funcionalidade só fornece o que é DIFERENTE: prompt, schema e como validar o JSON.
 */
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    /** Versão da API de Mensagens enviada no header obrigatório "anthropic-version". */
    static final String ANTHROPIC_VERSION = "2026-10-21";

    private final RestClient restClient;
    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;

    public AnthropicClient(RestClient restClient, AnthropicProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Faz uma chamada e converte a resposta.
     *
     * @param purpose      nome curto da funcionalidade, usado nos logs (ex.: "sugestão", "parse")
     * @param systemPrompt instruções do sistema (vindas de um arquivo em resources/prompts)
     * @param userMessage  dados da requisição
     * @param schema       JSON Schema que a resposta deve seguir
     * @param parser       converte e valida o JSON; lança InvalidAiResponseException se não servir
     */
    public <T> T call(String purpose, String systemPrompt, String userMessage,
                      Map<String, Object> schema, Function<String, T> parser) {
        Map<String, Object> request = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage)),
                "output_config", Map.of(
                        // Tarefas simples: esforço baixo = resposta mais rápida e mais barata.
                        "effort", "low",
                        "format", Map.of("type", "json_schema", "schema", schema)
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

        try {
            T result = parser.apply(extractJson(response));
            log.info("FlowAI: {} gerado por {} em {} ms (tokens entrada={}, saída={})",
                    purpose, properties.model(), elapsedMs, inputTokens(response), outputTokens(response));
            return result;
        } catch (InvalidAiResponseException e) {
            // Diagnóstico: registra o que o modelo devolveu (só o conteúdo gerado, nunca a chave).
            log.warn("FlowAI: {} rejeitado em {} ms (stop_reason={}, tokens entrada={}, saída={}). Conteúdo: {}",
                    purpose, elapsedMs,
                    response == null ? null : response.path("stop_reason").asText(),
                    inputTokens(response), outputTokens(response), describeContent(response));
            throw e;
        }
    }

    /** Converte o JSON do modelo para um tipo Java, transformando erro de formato em InvalidAiResponseException. */
    public <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new InvalidAiResponseException("JSON inválido na resposta do modelo", e);
        }
    }

    private String extractJson(JsonNode response) {
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
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                String text = block.path("text").asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        throw new InvalidAiResponseException("resposta sem bloco de texto");
    }

    private static int inputTokens(JsonNode response) {
        return response == null ? -1 : response.path("usage").path("input_tokens").asInt(-1);
    }

    private static int outputTokens(JsonNode response) {
        return response == null ? -1 : response.path("usage").path("output_tokens").asInt(-1);
    }

    /** Resume os blocos de conteúdo da resposta em uma linha curta: tipo de cada bloco e o texto (truncado). */
    private static String describeContent(JsonNode response) {
        if (response == null) {
            return "<sem resposta>";
        }
        StringBuilder out = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            String type = block.path("type").asText();
            out.append('[').append(type);
            if ("text".equals(type)) {
                out.append(": ").append(block.path("text").asText());
            }
            out.append("] ");
        }
        String oneLine = out.toString().replaceAll("\\s+", " ").strip();
        return oneLine.length() > 800 ? oneLine.substring(0, 800) + "…" : oneLine;
    }
}
