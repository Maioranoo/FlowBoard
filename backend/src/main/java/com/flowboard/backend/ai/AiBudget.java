package com.flowboard.backend.ai;

import com.flowboard.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Quantas sugestões o MODELO pode gerar. Acima do limite, a sugestão vem das regras locais,
 * que não custam nada. Dois tetos:
 *  - por usuário por dia (padrão 30), contado no banco;
 *  - GLOBAL por dia (padrão 1.000), somando todo mundo. O teto por usuário sozinho não basta:
 *    cada visitante novo ganha o seu, então criar visitantes em série multiplicaria o gasto.
 *    Pelo custo medido (~US$ 0,00024 por chamada), 1.000 sugestões custam cerca de US$ 0,25.
 *
 * O "dia" é o do fuso do usuário (app.timezone), via o Clock da aplicação. O teto global fica em
 * memória: reinicia se o servidor reiniciar, o que no pior caso permite um pouco mais num dia.
 */
@Component
public class AiBudget {

    private final UserRepository users;
    private final Clock clock;
    private final int perUserDailyLimit;
    private final int globalDailyLimit;

    private LocalDate globalDay;
    private int globalCount;

    public AiBudget(UserRepository users, Clock clock,
                    @Value("${app.ai.daily-suggestions-per-user}") int perUserDailyLimit,
                    @Value("${app.ai.daily-suggestions-global}") int globalDailyLimit) {
        this.users = users;
        this.clock = clock;
        this.perUserDailyLimit = perUserDailyLimit;
        this.globalDailyLimit = globalDailyLimit;
    }

    /** Gasta uma sugestão do dia, se houver saldo no teto global E no do usuário. */
    public synchronized boolean tryConsume(Long userId) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(globalDay)) {
            globalDay = today;
            globalCount = 0;
        }
        if (globalCount >= globalDailyLimit) {
            return false; // nem consulta o usuário: o saldo dele fica intacto
        }
        // Conta do usuário: um UPDATE atômico no banco (ver UserRepository.consumeAiSuggestion).
        if (users.consumeAiSuggestion(userId, today, perUserDailyLimit) != 1) {
            return false;
        }
        globalCount++;
        return true;
    }
}
