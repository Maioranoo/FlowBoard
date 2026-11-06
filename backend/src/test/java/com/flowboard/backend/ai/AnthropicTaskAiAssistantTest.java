package com.flowboard.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Testa o cliente da Anthropic SEM chamar a API de verdade.
 * O MockRestServiceServer substitui a camada HTTP do RestClient: confere a requisição
 * que o nosso código montou e devolve a resposta que o teste escolher.
 */
class AnthropicTaskAiAssistantTest {

    private static final String URL = "https://api.anthropic.test/v1/messages";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private AnthropicTaskAiAssistant assistant;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.test");
        server = MockRestServiceServer.bindTo(builder).build();
        AnthropicProperties properties = new AnthropicProperties(
                "test-key", "claude-haiku-5-5", "https://api.anthropic.test", Duration.ofSeconds(5), 1024);
        assistant = new AnthropicTaskAiAssistant(
                new AnthropicClient(builder.build(), properties, objectMapper), "PROMPT DO SISTEMA");
    }

    @Test
    void sendsWellFormedRequestAndParsesStructuredResponse() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", AnthropicClient.ANTHROPIC_VERSION))
                .andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
                .andExpect(jsonPath("$.system").value("PROMPT DO SISTEMA"))
                .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("Estudar AVL")))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.output_config.format.schema.properties.suggestedCategory.enum.length()")
                        .value(Category.values().length))
                .andRespond(withSuccess(anthropicResponse("end_turn", Map.of(
                        "suggestion", "Faça exercícios de rotação à mão antes de ler a teoria.",
                        "suggestedPriority", "HIGH",
                        "suggestedCategory", "STUDY",
                        "subtasks", List.of("Revisar rotações simples", "Resolver 3 exercícios"))), MediaType.APPLICATION_JSON));

        TaskAiSuggestion result = assistant.suggest("Estudar AVL", "prova quinta", Priority.MEDIUM);

        assertThat(result.source()).isEqualTo(SuggestionSource.AI);
        assertThat(result.suggestedPriority()).isEqualTo(Priority.HIGH);
        assertThat(result.suggestedCategory()).isEqualTo(Category.STUDY);
        assertThat(result.subtasks()).containsExactly("Revisar rotações simples", "Resolver 3 exercícios");
        server.verify();
    }

    @Test
    void rejectsCategoryOutsideTheAllowedSet() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(anthropicResponse("end_turn", Map.of(
                "suggestion", "Dica", "suggestedPriority", "LOW",
                "suggestedCategory", "SHOPPING", "subtasks", List.of())), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> assistant.suggest("x", null, null))
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("suggestedCategory");
    }

    @Test
    void rejectsTruncatedResponse() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                anthropicResponseWithText("max_tokens", "{\"suggestion\": \"cort"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> assistant.suggest("x", null, null))
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("max_tokens");
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                anthropicResponseWithText("end_turn", "isto não é JSON"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> assistant.suggest("x", null, null))
                .isInstanceOf(InvalidAiResponseException.class);
    }

    @Test
    void propagatesHttpErrors() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> assistant.suggest("x", null, null))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void propagatesTimeouts() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> assistant.suggest("x", null, null))
                .isInstanceOf(ResourceAccessException.class);
    }

    // --- helpers que montam respostas no formato da API de Mensagens ---

    private String anthropicResponse(String stopReason, Map<String, Object> modelJson) throws Exception {
        return anthropicResponseWithText(stopReason, objectMapper.writeValueAsString(modelJson));
    }

    private String anthropicResponseWithText(String stopReason, String text) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", "message",
                "role", "assistant",
                "model", "claude-haiku-5-5",
                "content", List.of(
                        Map.of("type", "thinking", "thinking", "", "signature", "sig"),
                        Map.of("type", "text", "text", text)),
                "stop_reason", stopReason,
                "usage", Map.of("input_tokens", 300, "output_tokens", 60)));
    }
}
