package com.flowboard.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação da API gerada automaticamente pelo springdoc a partir dos controllers e DTOs.
 *  - Swagger UI (interativo): /swagger-ui.html
 *  - Especificação OpenAPI em JSON: /v3/api-docs
 *
 * O esquema "bearerAuth" faz aparecer o botão "Authorize": cole ali o token devolvido por
 * /auth/login ou /auth/guest para testar as rotas protegidas.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI flowBoardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowBoard API")
                        .version("1.1")
                        .description("""
                                Gerenciador de tarefas com IA (Claude, da Anthropic).
                                - Contas com usuário e senha, ou visitante com um clique (POST /auth/guest).
                                - Cada usuário só acessa as próprias tarefas (token JWT no cabeçalho Authorization).
                                - Sugestões de produtividade geradas em segundo plano ao criar/editar tarefas.
                                - Criação de tarefas a partir de texto livre (POST /tasks/parse).
                                Quando a IA está indisponível, um fallback por regras mantém tudo funcionando."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
