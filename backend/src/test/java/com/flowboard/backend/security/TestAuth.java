package com.flowboard.backend.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Para testes de controller: faz a requisição chegar como se já tivesse passado pelo login,
 * com o mesmo tipo de usuário (AuthUser) que a segurança real monta a partir do token.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor as(long id, String username, boolean guest) {
        AuthUser user = new AuthUser(id, username, guest);
        return authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
