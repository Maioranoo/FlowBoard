package com.flowboard.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Sobe a aplicação inteira (todos os beans, JPA, IA, executor...) contra um Postgres de verdade
 * em container. Se alguma configuração estiver quebrada, a aplicação não sobe e o teste falha.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
