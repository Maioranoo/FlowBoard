package com.flowboard.backend.auth;

import com.flowboard.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Apaga visitantes sem atividade há mais de 7 dias (as tarefas deles vão junto, pelo
 * ON DELETE CASCADE do banco). Sem isso, cada "Testar sem cadastro" deixaria lixo para sempre
 * num banco gratuito com espaço limitado.
 */
@Component
public class GuestCleanup {

    static final Duration GUEST_RETENTION = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(GuestCleanup.class);

    private final UserRepository users;
    private final Clock clock;

    public GuestCleanup(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** Todo dia às 4h (horário de Brasília). */
    @Scheduled(cron = "0 0 4 * * *", zone = "${app.timezone}")
    public void daily() {
        run();
    }

    /**
     * Também roda quando a aplicação sobe: no plano gratuito o Render desliga o servidor quando
     * fica sem uso, e às 4h ele pode estar desligado, então o agendamento sozinho poderia nunca disparar.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    public int run() {
        int deleted = users.deleteInactiveGuests(clock.instant().minus(GUEST_RETENTION));
        log.info("Limpeza: {} visitante(s) inativo(s) há mais de {} dias apagado(s)", deleted, GUEST_RETENTION.toDays());
        return deleted;
    }
}
