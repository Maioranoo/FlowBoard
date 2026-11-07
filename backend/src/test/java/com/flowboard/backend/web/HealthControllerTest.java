package com.flowboard.backend.web;

import com.flowboard.backend.security.SecurityTestSupport;
import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rota chamada pelo "despertador" externo para o Render não dormir. */
@WebMvcTest(HealthController.class)
@Import(SecurityTestSupport.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void healthIsPublicAndDoesNotNeedToken() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        // Não pode tocar no banco: senão o despertador também manteria o Neon acordado.
        verifyNoInteractions(userRepository);
    }
}
