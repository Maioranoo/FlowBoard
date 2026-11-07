package com.flowboard.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS: diz ao NAVEGADOR quais sites podem chamar esta API. Só as origens da lista
 * (variável ALLOWED_ORIGINS, separadas por vírgula).
 * Obs.: CORS não é autenticação. Ele impede que outro site use a API pelo navegador de
 * um visitante, mas não impede chamadas diretas (curl, scripts). Quem protege os dados é o login.
 *
 * Com o Spring Security, o CORS precisa ser resolvido pelo próprio filtro de segurança (que roda
 * antes dos controllers); por isso a configuração é exposta como um CorsConfigurationSource.
 */
@Configuration
public class CorsConfig {

    /** Por quanto tempo (segundos) o navegador pode reaproveitar a resposta do preflight (OPTIONS). */
    private static final long PREFLIGHT_CACHE_SECONDS = 3600;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Authorization: o front manda o token de login neste cabeçalho.
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        // Sem isto o navegador repete o OPTIONS antes de quase toda escrita (~0,5 s a mais).
        config.setMaxAge(PREFLIGHT_CACHE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
