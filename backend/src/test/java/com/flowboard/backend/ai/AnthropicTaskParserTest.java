package com.flowboard.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Parse por IA com a API simulada pelo MockRestServiceServer (nenhuma chamada real). */
class AnthropicTaskParserTest {

    private static final String URL = "https://api.anthropic.test/v1/messages";
    /** 2026-11-06 é uma sexta-feira. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private AnthropicTaskParser parser;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.test");
        server = MockRestServiceServer.bindTo(builder).build();
        AnthropicProperties properties = new AnthropicProperties(
                "test-key", "claude-haiku-5-5", "https://api.anthropic.test", Duration.ofSeconds(5), 1024);
        parser = new AnthropicTaskParser(
                new AnthropicClient(builder.build(), properties, objectMapper), "PROMPT DE PARSE");
    }

    @Test
    void sendsTodayWithWeekdayAndParsesDraft() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.system").value("PROMPT DE PARSE"))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("<hoje>2026-11-06 (sexta-feira)</hoje>")))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("estudar AVL pra prova quinta, urgente")))
                .andExpect(jsonPath("$.output_config.format.schema.required.length()").value(5))
                .andRespond(withSuccess(response(Map.of(
                        "title", "Estudar árvores AVL",
                        "description", "Prova de estrutura de dados",
                        "priority", "HIGH",
                        "category", "STUDY",
                        "dueDate", "2026-11-12")), MediaType.APPLICATION_JSON));

        TaskDraft draft = parser.parse("estudar AVL pra prova quinta, urgente", TODAY);

        assertThat(draft.title()).isEqualTo("Estudar árvores AVL");
        assertThat(draft.priority()).isEqualTo(Priority.HIGH);
        assertThat(draft.category()).isEqualTo(Category.STUDY);
        assertThat(draft.dueDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(draft.source()).isEqualTo(SuggestionSource.AI);
        server.verify();
    }

    @Test
    void dropsMalformedDateButKeepsTheRest() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(response(Map.of(
                "title", "Pagar boleto", "description", "", "priority", "MEDIUM",
                "category", "FINANCE", "dueDate", "quinta-feira")), MediaType.APPLICATION_JSON));

        TaskDraft draft = parser.parse("pagar boleto quinta", TODAY);

        assertThat(draft.title()).isEqualTo("Pagar boleto");
        assertThat(draft.dueDate()).isNull();
    }

    @Test
    void rejectsEmptyTitle() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(response(Map.of(
                "title", " ", "description", "", "priority", "LOW",
                "category", "HOME", "dueDate", "")), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> parser.parse("...", TODAY)).isInstanceOf(InvalidAiResponseException.class);
    }

    private String response(Map<String, Object> modelJson) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", "message",
                "content", List.of(Map.of("type", "text", "text", objectMapper.writeValueAsString(modelJson))),
                "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 900, "output_tokens", 70)));
    }
}
