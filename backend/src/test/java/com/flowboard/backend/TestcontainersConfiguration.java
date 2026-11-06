package com.flowboard.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe um PostgreSQL 18 de verdade num container Docker para os testes de integração,
 * a mesma versão do Neon em produção.
 *
 * @ServiceConnection faz o Spring Boot apontar o datasource para esse container automaticamente
 * (URL, usuário e senha), ignorando o banco configurado no application.properties.
 * Assim os testes não dependem de um Postgres instalado na máquina.
 *
 * Requisito: Docker rodando (Docker Desktop no Windows; no GitHub Actions já vem instalado).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));
    }
}
