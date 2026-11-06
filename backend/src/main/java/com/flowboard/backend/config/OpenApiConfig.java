package com.flowboard.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação da API gerada automaticamente pelo springdoc a partir dos controllers e DTOs.
 *  - Swagger UI (interativo): /swagger-ui.html
 *  - Especificação OpenAPI em JSON: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flowBoardOpenApi() {
        return new OpenAPI().info(new Info()
                .title("FlowBoard API")
                .version("1.0")
                .description("""
                        Gerenciador de tarefas com IA (Claude, da Anthropic).
                        - Sugestões de produtividade geradas em segundo plano ao criar/editar tarefas.
                        - Criação de tarefas a partir de texto livre (POST /tasks/parse).
                        Quando a IA está indisponível, um fallback por regras mantém tudo funcionando."""));
    }
}
