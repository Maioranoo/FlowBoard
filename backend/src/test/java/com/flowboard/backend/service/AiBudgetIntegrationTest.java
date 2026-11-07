package com.flowboard.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.TestcontainersConfiguration;
import com.flowboard.backend.ai.TaskAiAssistant;
import com.flowboard.backend.ai.TaskAiSuggestion;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.security.JwtService;
import com.flowboard.backend.user.User;
import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Orçamento diário de IA por usuário: acima do limite, a sugestão vem das regras locais (FALLBACK)
 * em vez do modelo. Limite reduzido para 1 só neste teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.ai.daily-suggestions-per-user=1")
class AiBudgetIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    // Substitui o assistente "resiliente" (o que o sistema usa) por um que sempre responde como IA.
    @MockitoBean(name = "taskAiAssistant")
    private TaskAiAssistant aiAssistant;

    @Test
    void aiBudgetFallsBackToRules() throws Exception {
        when(aiAssistant.suggest(any(), any(), any())).thenReturn(new TaskAiSuggestion(
                "Comece pelo mais difícil", Priority.HIGH, Category.STUDY, List.of("Passo 1"), SuggestionSource.AI));
        String token = "Bearer " + jwtService.issue(createUser());

        long first = create(token, "Estudar AVL");
        long second = create(token, "Estudar árvores B");

        assertThat(waitForSuggestion(token, first)).isEqualTo("AI");
        assertThat(waitForSuggestion(token, second)).isEqualTo("FALLBACK");
    }

    private long create(String token, String title) throws Exception {
        String body = """
                {"title":"%s","description":"prova","priority":"HIGH","category":"STUDY"}""".formatted(title);
        String json = mvc.perform(post("/tasks").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    /** A sugestão é gerada em outra thread: consulta até sair de GENERATING (no máximo ~5 s). */
    private String waitForSuggestion(String token, long id) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            String json = mvc.perform(get("/tasks/" + id).header("Authorization", token))
                    .andReturn().getResponse().getContentAsString();
            String source = objectMapper.readTree(json).get("suggestionSource").asText();
            if (!"GENERATING".equals(source)) {
                return source;
            }
            Thread.sleep(100);
        }
        return "GENERATING";
    }

    private User createUser() {
        Instant now = Instant.now();
        User user = new User();
        user.setUsername("budget");
        user.setPasswordHash("$2a$10$hash-de-teste");
        user.setCreatedAt(now);
        user.setLastSeenAt(now);
        user.setTokensValidAfter(now.minusSeconds(60));
        return users.saveAndFlush(user);
    }
}
