package com.flowboard.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.TestcontainersConfiguration;
import com.flowboard.backend.security.JwtService;
import com.flowboard.backend.user.User;
import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento entre usuários, de ponta a ponta: HTTP → segurança → serviço → Postgres real.
 * O ponto central: a tarefa de outra pessoa responde 404, como se não existisse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskOwnershipIntegrationTest {

    private static final String TASK_JSON = """
            {"title":"Relatório da Ana","description":"fechar números","priority":"HIGH","category":"WORK"}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${app.jwt.secret}")
    private String secret;

    @Test
    void otherUsersTaskIsInvisible() throws Exception {
        String ana = bearer(createUser());
        String bia = bearer(createUser());

        String created = mvc.perform(post("/tasks").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON).content(TASK_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mvc.perform(get("/tasks/" + id).header("Authorization", bia)).andExpect(status().isNotFound());
        mvc.perform(put("/tasks/" + id).header("Authorization", bia)
                        .contentType(MediaType.APPLICATION_JSON).content(TASK_JSON))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/tasks/" + id + "/toggle-status").header("Authorization", bia))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/tasks/" + id).header("Authorization", bia)).andExpect(status().isNotFound());
        mvc.perform(get("/tasks").header("Authorization", bia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // A tarefa da Ana continua intacta.
        JsonNode task = objectMapper.readTree(mvc.perform(get("/tasks/" + id).header("Authorization", ana))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(task.get("title").asText()).isEqualTo("Relatório da Ana");
        assertThat(task.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void noTokenIs401WithErrorShape() throws Exception {
        mvc.perform(get("/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Não autenticado"));
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mvc.perform(get("/tasks").header("Authorization", "Bearer isto-nao-e-um-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        User user = createUser();
        // Emitido 8 dias atrás: a validade de 7 dias já passou.
        Clock eightDaysAgo = Clock.fixed(Instant.now().minus(Duration.ofDays(8)), ZoneOffset.UTC);
        String expired = new JwtService(secret, eightDaysAgo).issue(user);
        // O usuário precisa ter sido criado "antes" do token, senão a recusa viria de tokensValidAfter.
        user.setTokensValidAfter(Instant.now().minus(Duration.ofDays(30)));
        users.saveAndFlush(user);

        mvc.perform(get("/tasks").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    private User createUser() {
        Instant now = Instant.now();
        User user = new User();
        user.setUsername("u" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("$2a$10$hash-de-teste");
        user.setCreatedAt(now);
        user.setLastSeenAt(now);
        user.setTokensValidAfter(now.minusSeconds(60));
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user);
    }
}
