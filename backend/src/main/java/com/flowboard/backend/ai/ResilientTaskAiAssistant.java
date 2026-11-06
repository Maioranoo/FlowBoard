package com.flowboard.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Combina as duas implementações: tenta a IA e, se QUALQUER coisa der errado,
 * usa as regras locais. Do ponto de vista de quem chama, sempre volta uma sugestão válida.
 *
 * Casos de fallback (todos registrados em log com o motivo):
 *  - ANTHROPIC_API_KEY não configurada;
 *  - timeout ou erro de rede (ResourceAccessException);
 *  - erro HTTP da API, ex.: 401 chave inválida, 429 limite, 529 sobrecarga (RestClientResponseException);
 *  - resposta inválida: JSON quebrado, campo faltando, valor fora do enum (InvalidAiResponseException).
 */
public class ResilientTaskAiAssistant implements TaskAiAssistant {

    private static final Logger log = LoggerFactory.getLogger(ResilientTaskAiAssistant.class);

    private final TaskAiAssistant primary;
    private final TaskAiAssistant fallback;
    private final boolean primaryEnabled;

    public ResilientTaskAiAssistant(TaskAiAssistant primary, TaskAiAssistant fallback, boolean primaryEnabled) {
        this.primary = primary;
        this.fallback = fallback;
        this.primaryEnabled = primaryEnabled;
    }

    @Override
    public TaskAiSuggestion suggest(String title, String description, String priority) {
        if (!primaryEnabled) {
            log.info("FlowAI: fallback usado (motivo: ANTHROPIC_API_KEY não configurada)");
            return fallback.suggest(title, description, priority);
        }

        try {
            return primary.suggest(title, description, priority);
        } catch (RuntimeException e) {
            log.warn("FlowAI: fallback usado (motivo: {}) - {}", describe(e), e.getMessage());
            return fallback.suggest(title, description, priority);
        }
    }

    private String describe(RuntimeException e) {
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
