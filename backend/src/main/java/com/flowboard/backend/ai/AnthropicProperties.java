package com.flowboard.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da integração com a Anthropic, lida do application.properties
 * (que por sua vez lê as variáveis de ambiente ANTHROPIC_*).
 * Nenhum valor secreto fica no código: a chave só existe em variável de ambiente.
 *
 * @param apiKey    chave da API (ANTHROPIC_API_KEY). Vazia = IA desligada, usa só as regras.
 * @param model     modelo (ANTHROPIC_MODEL), padrão claude-haiku-5-5
 * @param baseUrl   URL da API (útil para apontar para um mock em testes)
 * @param timeout   tempo máximo para conectar e para receber a resposta (ANTHROPIC_TIMEOUT, ex.: "10s")
 * @param maxTokens limite de tokens da resposta
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout,
        int maxTokens
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Nunca imprime a chave, mesmo se o objeto for parar num log. */
    @Override
    public String toString() {
        return "AnthropicProperties[model=" + model + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                + ", maxTokens=" + maxTokens + ", apiKey=" + (hasApiKey() ? "***" : "<vazia>") + "]";
    }
}
