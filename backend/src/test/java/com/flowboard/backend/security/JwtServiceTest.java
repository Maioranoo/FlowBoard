package com.flowboard.backend.security;

import com.flowboard.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-bytes-ok";

    // Relógio fixo em "agora": o decodificador confere a validade com o relógio real.
    private final Clock clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
    private final JwtService service = new JwtService(SECRET, clock);

    @Test
    void accountTokenExpiresInSevenDays() {
        Jwt jwt = service.decoder().decode(service.issue(user(5L, false)));

        assertThat(jwt.getSubject()).isEqualTo("5");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void guestTokenHasNoExpiry() {
        Jwt jwt = service.decoder().decode(service.issue(user(6L, true)));

        assertThat(jwt.getSubject()).isEqualTo("6");
        assertThat(jwt.getExpiresAt()).isNull();
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("curta-demais", clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void decoderRejectsTokenSignedWithAnotherSecret() {
        JwtService other = new JwtService("outro-segredo-tambem-com-mais-de-32-bytes", clock);
        String foreignToken = other.issue(user(5L, false));

        assertThatThrownBy(() -> service.decoder().decode(foreignToken)).isInstanceOf(JwtException.class);
    }

    private static User user(Long id, boolean guest) {
        User user = new User();
        user.setId(id);
        user.setGuest(guest);
        return user;
    }
}
