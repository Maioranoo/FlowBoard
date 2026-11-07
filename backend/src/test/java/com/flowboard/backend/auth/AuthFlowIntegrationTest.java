package com.flowboard.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.TestcontainersConfiguration;
import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro, login, visitante e "visitante vira conta", de ponta a ponta contra um Postgres real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    /** Cada teste usa um "IP" próprio, para os limites de tentativas de um não afetarem o outro. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;

    private final String ip = "10.0.0." + NEXT_IP.getAndIncrement();

    @Test
    void registerThenLoginWorksIgnoringCaseAndSpaces() throws Exception {
        String name = unique("Ana_");
        call(post("/auth/register"), credentials(name, "senha-forte-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(name.toLowerCase()))
                .andExpect(jsonPath("$.guest").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty());

        call(post("/auth/login"), credentials("  " + name.toUpperCase() + " ", "senha-forte-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name.toLowerCase()))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void duplicateUsernameDifferentCaseIs409() throws Exception {
        String name = unique("bia");
        call(post("/auth/register"), credentials(name, "senha-forte-1")).andExpect(status().isCreated());

        call(post("/auth/register"), credentials(name.toUpperCase(), "outra-senha-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.username").value("Este nome de usuário já está em uso"));
    }

    @Test
    void reservedUsernameIs409() throws Exception {
        call(post("/auth/register"), credentials("Visitante", "senha-forte-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.username").value("Este nome de usuário já está em uso"));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameMessage() throws Exception {
        String name = unique("caio");
        call(post("/auth/register"), credentials(name, "senha-forte-1")).andExpect(status().isCreated());

        String wrongPassword = call(post("/auth/login"), credentials(name, "senha-errada-9"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = call(post("/auth/login"), credentials(unique("ninguem"), "senha-errada-9"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json(wrongPassword).get("message").asText())
                .isEqualTo("Usuário ou senha incorretos")
                .isEqualTo(json(unknownUser).get("message").asText());
    }

    @Test
    void passwordOver72BytesIs400() throws Exception {
        // 40 "ç" = 40 caracteres, mas 80 bytes em UTF-8: o BCrypt cortaria a senha em silêncio.
        call(post("/auth/register"), credentials(unique("dani"), "ç".repeat(40)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void guestGetsThreeSeedTasksOneCompleted() throws Exception {
        JsonNode guest = json(call(post("/auth/guest"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guest").value(true))
                .andExpect(jsonPath("$.username").value(org.hamcrest.Matchers.startsWith("visitante-")))
                .andReturn().getResponse().getContentAsString());

        JsonNode tasks = json(mvc.perform(get("/tasks").header("Authorization", bearer(guest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(tasks).hasSize(3);
        long completed = 0;
        for (JsonNode task : tasks) {
            if ("COMPLETED".equals(task.get("status").asText())) {
                completed++;
            }
        }
        assertThat(completed).isEqualTo(1);
    }

    @Test
    void upgradeKeepsTasksAndKillsOldGuestToken() throws Exception {
        JsonNode guest = json(call(post("/auth/guest"), null).andReturn().getResponse().getContentAsString());
        String oldToken = bearer(guest);
        String name = unique("joao");

        JsonNode upgraded = json(mvc.perform(withIp(post("/auth/upgrade"))
                        .header("Authorization", oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(name, "senha-forte-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest").value(false))
                .andExpect(jsonPath("$.username").value(name))
                .andReturn().getResponse().getContentAsString());
        String newToken = bearer(upgraded);

        mvc.perform(get("/tasks").header("Authorization", newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        // O token de visitante (que não expira) não pode virar uma chave permanente da conta.
        mvc.perform(get("/tasks").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        // E uma conta já registrada não pode "virar conta" de novo.
        mvc.perform(withIp(post("/auth/upgrade"))
                        .header("Authorization", newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(unique("outro"), "senha-forte-1")))
                .andExpect(status().isConflict());
        // E com o nome novo + senha, o login funciona.
        call(post("/auth/login"), credentials(name, "senha-forte-1")).andExpect(status().isOk());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        String name = unique("eva");
        JsonNode registered = json(call(post("/auth/register"), credentials(name, "senha-forte-1"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/auth/me").header("Authorization", bearer(registered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.guest").value(false));
    }

    @Test
    void tokenOfDeletedUserIs401() throws Exception {
        JsonNode guest = json(call(post("/auth/guest"), null).andReturn().getResponse().getContentAsString());
        users.delete(users.findByUsername(guest.get("username").asText()).orElseThrow());

        mvc.perform(get("/auth/me").header("Authorization", bearer(guest))).andExpect(status().isUnauthorized());
    }

    @Test
    void staleBearerHeaderIsIgnoredOnPublicRoutes() throws Exception {
        String name = unique("fabi");
        call(post("/auth/register"), credentials(name, "senha-forte-1")).andExpect(status().isCreated());

        mvc.perform(withIp(post("/auth/login"))
                        .header("Authorization", "Bearer token-velho-invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(name, "senha-forte-1")))
                .andExpect(status().isOk());
    }

    // --- auxiliares ---

    private ResultActions call(MockHttpServletRequestBuilder request, String body) throws Exception {
        MockHttpServletRequestBuilder builder = withIp(request);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder);
    }

    private MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder request) {
        return request.header("X-Forwarded-For", ip);
    }

    private String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private static String bearer(JsonNode authResponse) {
        return "Bearer " + authResponse.get("token").asText();
    }

    /** Nome único por execução (o banco do container é compartilhado entre os testes da classe). */
    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
