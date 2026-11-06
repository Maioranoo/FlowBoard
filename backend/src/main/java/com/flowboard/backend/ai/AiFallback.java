package com.flowboard.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Regra de resiliência comum a todas as funcionalidades de IA:
 * tenta o modelo e, se QUALQUER coisa der errado, usa as regras locais.
 * Do ponto de vista de quem chama, sempre volta um resultado válido.
 *
 * Casos de fallback (todos registrados em log com o motivo):
 *  - ANTHROPIC_API_KEY não configurada;
 *  - timeout ou erro de rede (ResourceAccessException);
 *  - erro HTTP da API, ex.: 401 chave inválida, 429 limite, 529 sobrecarga (RestClientResponseException);
 *  - resposta inválida: JSON quebrado, campo faltando, valor fora do enum (InvalidAiResponseException).
 */
final class AiFallback {

    private static final Logger log = LoggerFactory.getLogger(AiFallback.class);

    private AiFallback() {
    }

    static <T> T withFallback(String purpose, boolean primaryEnabled, Supplier<T> primary, Supplier<T> fallback) {
        if (!primaryEnabled) {
            log.info("FlowAI: fallback usado em {} (motivo: ANTHROPIC_API_KEY não configurada)", purpose);
            return fallback.get();
        }

        try {
            return primary.get();
        } catch (RuntimeException e) {
            log.warn("FlowAI: fallback usado em {} (motivo: {}) - {}", purpose, describe(e), e.getMessage());
            return fallback.get();
        }
    }

    private static String describe(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return "timeout ou erro de rede";
        }
        if (e instanceof RestClientResponseException http) {
            return "API respondeu HTTP " + http.getStatusCode().value();
        }
        if (e instanceof InvalidAiResponseException) {
            return "resposta inválida do modelo";
        }
        return "erro inesperado " + e.getClass().getSimpleName();
    }
}
