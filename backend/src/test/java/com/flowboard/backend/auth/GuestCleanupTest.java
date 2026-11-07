package com.flowboard.backend.auth;

import com.flowboard.backend.TestcontainersConfiguration;
import com.flowboard.backend.user.User;
import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GuestCleanupTest {

    @Autowired
    private GuestCleanup cleanup;
    @Autowired
    private UserRepository users;

    @Test
    void runDeletesOnlyGuestsInactiveForMoreThanSevenDays() {
        users.deleteAll(); // começa do zero: a limpeza também roda quando a aplicação sobe
        User stale = users.saveAndFlush(user("visitante-old01", true, Duration.ofDays(8)));
        User recent = users.saveAndFlush(user("visitante-new01", true, Duration.ofDays(1)));
        User account = users.saveAndFlush(user("conta_antiga", false, Duration.ofDays(30)));

        int deleted = cleanup.run();

        assertThat(deleted).isEqualTo(1);
        assertThat(users.findById(stale.getId())).isEmpty();
        assertThat(users.findById(recent.getId())).isPresent();
        assertThat(users.findById(account.getId())).isPresent();
    }

    private static User user(String username, boolean guest, Duration inactiveFor) {
        Instant lastSeen = Instant.now().minus(inactiveFor);
        User user = new User();
        user.setUsername(username);
        user.setGuest(guest);
        user.setPasswordHash(guest ? null : "$2a$10$hash-de-teste");
        user.setCreatedAt(lastSeen);
        user.setLastSeenAt(lastSeen);
        user.setTokensValidAfter(lastSeen);
        return user;
    }
}
