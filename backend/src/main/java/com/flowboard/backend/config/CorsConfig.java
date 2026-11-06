package com.flowboard.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: diz ao NAVEGADOR quais sites podem chamar esta API. Antes era "*" (qualquer site);
 * agora só as origens da lista (variável ALLOWED_ORIGINS, separadas por vírgula).
 * Obs.: CORS não é autenticação. Ele impede que outro site use a API pelo navegador de
 * um visitante, mas não impede chamadas diretas (curl, scripts).
 */
@Configuration
public class CorsConfig {

    /** Por quanto tempo (segundos) o navegador pode reaproveitar a resposta do preflight (OPTIONS). */
    private static final long PREFLIGHT_CACHE_SECONDS = 3600;

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type")
                        // Sem isto o navegador repete o OPTIONS antes de quase toda escrita (~0,5 s a mais).
                        .maxAge(PREFLIGHT_CACHE_SECONDS);
            }
        };
    }
}
