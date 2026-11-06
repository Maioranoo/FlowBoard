package com.flowboard.backend.repository;

import com.flowboard.backend.TestcontainersConfiguration;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.model.TaskStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o mapeamento JPA contra um Postgres 18 de verdade (Testcontainers), não um banco simulado.
 * Interessa principalmente a COMPATIBILIDADE: os enums precisam ler e gravar exatamente os
 * textos que já existem no banco de produção.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // usa o container, não um banco em memória
@Import(TestcontainersConfiguration.class)
class TaskPersistenceTest {

    @Autowired
    private TaskRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void enumsAreStoredAsTheirNamesNotNumbers() {
        Task task = new Task();
        task.setTitle("Estudar AVL");
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(Priority.HIGH);
        task.setCategory(Category.STUDY);
        task.setDueDate(LocalDate.of(2026, 11, 12));
        task.setSubtasks(List.of("Revisar rotações", "Fazer exercícios"));
        task.setSuggestionSource(SuggestionSource.AI);
        Long id = repository.saveAndFlush(task).getId();

        // Lê a linha "crua", com SQL, para ver o que de fato foi gravado no banco.
        Map<String, Object> row = jdbc.queryForMap(
                "select status, priority, category, due_date, subtasks from tasks where id = ?", id);

        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("priority")).isEqualTo("HIGH");
        assertThat(row.get("category")).isEqualTo("STUDY");
        assertThat(row.get("due_date").toString()).isEqualTo("2026-11-12");
        assertThat(row.get("subtasks")).isEqualTo("[\"Revisar rotações\",\"Fazer exercícios\"]");
    }

    /** Uma linha gravada ANTES da Fase 3 (strings, sem as colunas novas) continua sendo lida normalmente. */
    @Test
    void readsRowsWrittenBeforeTheEnumMigration() {
        jdbc.update("insert into tasks (title, description, status, priority, category) values (?, ?, ?, ?, ?)",
                "Tarefa antiga", "criada na versão anterior", "COMPLETED", "LOW", "WORK");
        Long id = jdbc.queryForObject("select id from tasks where title = 'Tarefa antiga'", Long.class);
        entityManager.clear(); // garante que a leitura vem do banco, não do cache do JPA

        Task task = repository.findById(id).orElseThrow();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getPriority()).isEqualTo(Priority.LOW);
        assertThat(task.getCategory()).isEqualTo(Category.WORK);
        assertThat(task.getSubtasks()).isNull();
        assertThat(task.getSuggestionSource()).isNull();
        assertThat(task.getDueDate()).isNull();
    }
}
