package com.flowboard.backend.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Limitador de requisições por "janela fixa": cada cliente pode fazer até N chamadas por minuto;
 * quando o minuto vira, a contagem recomeça.
 *
 * Duas camadas, de propósito:
 *  - POR CLIENTE (IP): segura uso acidental (ex.: um loop no front) e abuso casual.
 *    O IP vem do cabeçalho X-Forwarded-For, que o próprio cliente consegue falsificar,
 *    então sozinho ele não basta;
 *  - GLOBAL: soma todos os clientes. Não dá para contornar trocando de IP ou de cabeçalho,
 *    e é o que de fato limita o gasto com a API.
 *
 * Fica em memória: simples e suficiente para um único servidor. Se o back-end rodasse em várias
 * instâncias, cada uma teria sua contagem e o limite real seria multiplicado; aí o certo seria
 * guardar os contadores num lugar compartilhado (ex.: Redis).
 *
 * Os métodos são synchronized: no volume do FlowBoard isso não pesa, e evita condições de corrida
 * entre duas requisições simultâneas do mesmo cliente.
 */
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_CLIENTS = 1_000;
    private static final String GLOBAL_KEY = "*";

    private final int perClientLimit;
    private final int globalLimit;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public RateLimiter(int perClientLimit, int globalLimit, Clock clock) {
        this.perClientLimit = perClientLimit;
        this.globalLimit = globalLimit;
        this.clock = clock;
    }

    /**
     * Registra uma chamada do cliente, ou lança RateLimitExceededException se o limite já foi atingido.
     * Uma chamada recusada não conta no limite.
     */
    public synchronized void acquire(String clientKey) {
        Instant now = clock.instant();
        purgeExpiredIfNeeded(now);

        Window client = current(clientKey, now);
        Window global = current(GLOBAL_KEY, now);

        if (client.count >= perClientLimit) {
            throw new RateLimitExceededException(secondsUntilReset(client, now));
        }
        if (global.count >= globalLimit) {
            throw new RateLimitExceededException(secondsUntilReset(global, now));
        }
        client.count++;
        global.count++;
    }

    private Window current(String key, Instant now) {
        Window window = windows.get(key);
        if (window == null || window.isExpired(now)) {
            window = new Window(now);
            windows.put(key, window);
        }
        return window;
    }

    /** Evita que o mapa cresça para sempre com IPs que não voltam mais. */
    private void purgeExpiredIfNeeded(Instant now) {
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
    }

    private static long secondsUntilReset(Window window, Instant now) {
        long seconds = Duration.between(now, window.start.plus(WINDOW)).toSeconds();
        return Math.max(1, seconds);
    }

    private static final class Window {
        private final Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }

        private boolean isExpired(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }
    }
}
