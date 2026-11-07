package com.flowboard.backend.security;

import com.flowboard.backend.config.CorsConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

/**
 * Para testes @WebMvcTest: carrega a MESMA configuração de segurança da aplicação (e não a
 * padrão do Spring Boot), para que os testes vejam o comportamento real: 401, CORS etc.
 * Quem usa precisa declarar @MockitoBean UserRepository.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, CorsConfig.class, JwtService.class, UserTokenConverter.class,
        JsonAuthenticationEntryPoint.class})
public class SecurityTestSupport {

    @Bean
    Clock testClock() {
        return Clock.systemUTC();
    }
}
