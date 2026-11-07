package com.flowboard.backend.config;

import com.flowboard.backend.controller.TaskController;
import com.flowboard.backend.ratelimit.RateLimiter;
import com.flowboard.backend.security.SecurityTestSupport;
import com.flowboard.backend.service.TaskParseService;
import com.flowboard.backend.service.TaskService;
import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simula o "preflight" (OPTIONS) que o navegador manda antes de um POST vindo de outro site.
 * Passa pela configuração de segurança real: o preflight não leva token e precisa ser liberado.
 */
@WebMvcTest(TaskController.class)
@Import(SecurityTestSupport.class)
@TestPropertySource(properties = "app.cors.allowed-origins=https://flow-board-lovat.vercel.app,http://localhost:5500")
class CorsConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskService taskService;
    @MockitoBean
    private TaskParseService taskParseService;
    @MockitoBean(name = "parseRateLimiter") // o TaskController pede este bean pelo nome
    private RateLimiter rateLimiter;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void allowsTheFrontendWithTokenHeaderAndLetsTheBrowserCacheThePreflight() throws Exception {
        mvc.perform(options("/tasks")
                        .header("Origin", "https://flow-board-lovat.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://flow-board-lovat.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("Authorization")))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void rejectsOriginsOutsideTheList() throws Exception {
        mvc.perform(options("/tasks")
                        .header("Origin", "https://site-qualquer.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
