package com.flowboard.backend.ai;

import com.flowboard.backend.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O orçamento por usuário não basta: cada visitante novo ganha o seu. O teto GLOBAL do dia é o que
 * limita o gasto total, mesmo com alguém criando visitantes em série.
 */
class AiBudgetTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private final UserRepository users = mock(UserRepository.class);

    @Test
    void globalDailyCapStopsAiForEveryoneAndResetsNextDay() {
        when(users.consumeAiSuggestion(anyLong(), any(), anyInt())).thenReturn(1); // todos com saldo próprio
        MovableClock clock = new MovableClock(Instant.parse("2026-11-07T15:00:00Z"));
        AiBudget budget = new AiBudget(users, clock, 30, 2);

        assertThat(budget.tryConsume(1L)).isTrue();
        assertThat(budget.tryConsume(2L)).isTrue();
        assertThat(budget.tryConsume(3L)).isFalse(); // teto global do dia atingido

        clock.now = clock.now.plus(Duration.ofDays(1));
        assertThat(budget.tryConsume(3L)).isTrue();
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        @Override
        public ZoneId getZone() {
            return SP;
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

    @Test
    void globalCapReachedDoesNotSpendTheUsersBudget() {
        when(users.consumeAiSuggestion(anyLong(), any(), anyInt())).thenReturn(1);
        AiBudget budget = new AiBudget(users, Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), SP), 30, 0);

        assertThat(budget.tryConsume(1L)).isFalse();
        verify(users, never()).consumeAiSuggestion(anyLong(), any(), anyInt());
    }

    @Test
    void userWithoutBudgetDoesNotSpendTheGlobalCap() {
        when(users.consumeAiSuggestion(anyLong(), any(), anyInt())).thenReturn(0, 1);
        AiBudget budget = new AiBudget(users, Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), SP), 30, 1);

        assertThat(budget.tryConsume(1L)).isFalse(); // usuário 1 sem saldo
        assertThat(budget.tryConsume(2L)).isTrue();  // o teto global ainda tinha 1
    }
}
