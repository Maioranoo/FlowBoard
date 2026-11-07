package com.flowboard.backend.user;

import com.flowboard.backend.TestcontainersConfiguration;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.model.TaskStatus;
import com.flowboard.backend.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tabela de usuários e a ligação tarefa → dono, contra um Postgres de verdade (Testcontainers).
 * Aqui mora o que só o BANCO garante: nome único, exclusão em cascata e o contador de IA atômico.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Autowired
    private UserRepository users;
    @Autowired
    private TaskRepository tasks;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void usernameIsUnique() {
        users.saveAndFlush(account("ana", NOW));

        assertThatThrownBy(() -> users.saveAndFlush(account("ana", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingGuestCascadesToTasks() {
        User oldGuest = users.saveAndFlush(guest("visitante-aaaaa", NOW.minus(Duration.ofDays(8))));
        User account = users.saveAndFlush(account("ana", NOW.minus(Duration.ofDays(30))));
        tasks.saveAndFlush(task(oldGuest, "do visitante"));
        tasks.saveAndFlush(task(account, "da conta"));

        int deleted = users.deleteInactiveGuests(NOW.minus(Duration.ofDays(7)));
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tasks", Integer.class)).isEqualTo(1);
        assertThat(users.findById(account.getId())).isPresent();
    }

    @Test
    void deleteInactiveGuestsKeepsRecentGuests() {
        users.saveAndFlush(guest("visitante-bbbbb", NOW.minus(Duration.ofDays(6))));

        assertThat(users.deleteInactiveGuests(NOW.minus(Duration.ofDays(7)))).isZero();
    }

    @Test
    void consumeAiSuggestionStopsAtLimit() {
        Long id = users.saveAndFlush(account("ana", NOW)).getId();
        LocalDate today = LocalDate.of(2026, 11, 7);

        assertThat(users.consumeAiSuggestion(id, today, 3)).isEqualTo(1);
        assertThat(users.consumeAiSuggestion(id, today, 3)).isEqualTo(1);
        assertThat(users.consumeAiSuggestion(id, today, 3)).isEqualTo(1);
        assertThat(users.consumeAiSuggestion(id, today, 3)).isZero();
        // Dia novo: a contagem recomeça.
        assertThat(users.consumeAiSuggestion(id, today.plusDays(1), 3)).isEqualTo(1);
    }

    @Test
    void findAllByOwnerReturnsOnlyOwnTasks() {
        User ana = users.saveAndFlush(account("ana", NOW));
        User bia = users.saveAndFlush(account("bia", NOW));
        tasks.saveAndFlush(task(ana, "da Ana"));
        Task biaTask = tasks.saveAndFlush(task(bia, "da Bia"));

        assertThat(tasks.findAllByOwnerIdOrderByIdAsc(ana.getId()))
                .extracting(Task::getTitle).containsExactly("da Ana");
        assertThat(tasks.findByIdAndOwnerId(biaTask.getId(), ana.getId())).isEmpty();
        assertThat(tasks.findByIdAndOwnerId(biaTask.getId(), bia.getId())).isPresent();
    }

    private static User account(String username, Instant lastSeen) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("$2a$10$hash-de-teste");
        user.setGuest(false);
        user.setCreatedAt(lastSeen);
        user.setLastSeenAt(lastSeen);
        user.setTokensValidAfter(lastSeen);
        return user;
    }

    private static User guest(String username, Instant lastSeen) {
        User user = account(username, lastSeen);
        user.setPasswordHash(null);
        user.setGuest(true);
        return user;
    }

    private static Task task(User owner, String title) {
        Task task = new Task();
        task.setOwner(owner);
        task.setTitle(title);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(Priority.MEDIUM);
        task.setCategory(Category.WORK);
        return task;
    }
}
