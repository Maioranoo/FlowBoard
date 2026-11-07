package com.flowboard.backend.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** Relógio controlado pelo teste, para "avançar o tempo" sem esperar. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-06T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    @Test
    void limitsEachClientAndResetsAfterTheWindow() {
        RateLimiter limiter = new RateLimiter(2, 100, clock);

        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");
        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);

        // Outro cliente não é afetado.
        assertThatCode(() -> limiter.acquire("2.2.2.2")).doesNotThrowAnyException();

        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> limiter.acquire("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void globalLimitCannotBeBypassedByChangingIp() {
        RateLimiter limiter = new RateLimiter(10, 3, clock);

        limiter.acquire("a");
        limiter.acquire("b");
        limiter.acquire("c");

        assertThatThrownBy(() -> limiter.acquire("d"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isBetween(1L, 60L));
    }

    @Test
    void tryAcquireReturnsFalseInsteadOfThrowing() {
        RateLimiter limiter = new RateLimiter(1, 100, clock);

        org.assertj.core.api.Assertions.assertThat(limiter.tryAcquire("a")).isTrue();
        org.assertj.core.api.Assertions.assertThat(limiter.tryAcquire("a")).isFalse();
        org.assertj.core.api.Assertions.assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void rejectedRequestsFromNewClientsAreNotTracked() {
        RateLimiter limiter = new RateLimiter(5, 1, clock);
        limiter.acquire("a"); // gasta o limite global

        // Sem isto, cada IP inventado num ataque ocuparia memória mesmo sendo recusado.
        for (int i = 0; i < 1_000; i++) {
            org.assertj.core.api.Assertions.assertThat(limiter.tryAcquire("forjado-" + i)).isFalse();
        }
        org.assertj.core.api.Assertions.assertThat(limiter.trackedClients()).isEqualTo(1);
    }

    @Test
    void trackedClientsAreCappedEvenWithinTheWindow() {
        RateLimiter limiter = new RateLimiter(5, 1_000_000, clock);

        for (int i = 0; i < RateLimiter.MAX_TRACKED_CLIENTS + 500; i++) {
            limiter.acquire("cliente-" + i);
        }
        org.assertj.core.api.Assertions.assertThat(limiter.trackedClients())
                .isLessThanOrEqualTo(RateLimiter.MAX_TRACKED_CLIENTS);
    }

    @Test
    void customWindowResetsAfterOneHour() {
        RateLimiter limiter = new RateLimiter(1, 100, Duration.ofHours(1), clock);

        limiter.acquire("a");
        clock.advance(Duration.ofMinutes(59));
        assertThatThrownBy(() -> limiter.acquire("a"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isBetween(1L, 60L));

        clock.advance(Duration.ofMinutes(1));
        limiter.acquire("a"); // nova janela: não lança
    }
}
