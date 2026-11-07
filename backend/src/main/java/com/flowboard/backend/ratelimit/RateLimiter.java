package com.flowboard.backend.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limitador de requisições por "janela fixa": cada cliente pode fazer até N chamadas por janela
 * (1 minuto por padrão; 1 hora para criação de visitantes); quando a janela vira, a contagem recomeça.
 *
 * Duas camadas, de propósito:
 *  - POR CLIENTE (IP): segura uso acidental (ex.: um loop no front) e abuso casual;
 *  - GLOBAL: soma todos os clientes. Não dá para contornar trocando de IP, e é o que de fato
 *    limita o gasto com a API.
 *
 * Proteção da própria memória: uma chamada recusada NÃO cria registro para o cliente (senão um
 * ataque com IPs inventados encheria o mapa), e o mapa tem um teto; acima dele, o cliente menos
 * recente é esquecido. O pior caso disso é alguém ter sua contagem zerada antes da hora; o limite
 * global continua valendo.
 *
 * Fica em memória: simples e suficiente para um único servidor. Se o back-end rodasse em várias
 * instâncias, cada uma teria sua contagem e o limite real seria multiplicado; aí o certo seria
 * guardar os contadores num lugar compartilhado (ex.: Redis).
 *
 * Os métodos são synchronized: no volume do FlowBoard isso não pesa, e evita condições de corrida
 * entre duas requisições simultâneas do mesmo cliente.
 */
public class RateLimiter {

    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
    static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int perClientLimit;
    private final int globalLimit;
    private final Duration window;
    private final Clock clock;

    /** Ordem de acesso + remoção do mais antigo acima do teto (um cache LRU simples). */
    private final Map<String, Window> clients = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > MAX_TRACKED_CLIENTS;
        }
    };
    private Window global;

    public RateLimiter(int perClientLimit, int globalLimit, Clock clock) {
        this(perClientLimit, globalLimit, DEFAULT_WINDOW, clock);
    }

    public RateLimiter(int perClientLimit, int globalLimit, Duration window, Clock clock) {
        this.perClientLimit = perClientLimit;
        this.globalLimit = globalLimit;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Registra uma chamada do cliente, ou lança RateLimitExceededException se o limite já foi atingido.
     * Uma chamada recusada não conta no limite.
     */
    public synchronized void acquire(String clientKey) {
        if (!tryAcquire(clientKey)) {
            Instant now = clock.instant();
            Window client = activeClientWindow(clientKey, now);
            Window blocking = client != null && client.count >= perClientLimit ? client : global;
            throw new RateLimitExceededException(secondsUntilReset(blocking, now));
        }
    }

    /**
     * Igual a acquire, mas devolve false em vez de lançar exceção. Útil quando passar do limite não
     * deve bloquear a pessoa, só mudar o comportamento (ex.: visitante criado sem gastar IA).
     */
    public synchronized boolean tryAcquire(String clientKey) {
        Instant now = clock.instant();
        if (global == null || global.isExpired(now, window)) {
            global = new Window(now);
        }
        Window client = activeClientWindow(clientKey, now);

        boolean clientFull = client != null && client.count >= perClientLimit;
        if (clientFull || global.count >= globalLimit) {
            return false; // recusado: nada é gravado
        }

        if (client == null) {
            client = new Window(now);
            clients.put(clientKey, client);
        }
        client.count++;
        global.count++;
        return true;
    }

    /** Quantos clientes estão sendo acompanhados (para testes). */
    synchronized int trackedClients() {
        return clients.size();
    }

    /** A janela atual do cliente, ou null se ele não tem uma (nunca chamou ou a dele expirou). */
    private Window activeClientWindow(String clientKey, Instant now) {
        Window w = clients.get(clientKey);
        if (w != null && w.isExpired(now, window)) {
            clients.remove(clientKey);
            return null;
        }
        return w;
    }

    private long secondsUntilReset(Window w, Instant now) {
        long seconds = Duration.between(now, w.start.plus(window)).toSeconds();
        return Math.max(1, seconds);
    }

    private static final class Window {
        private final Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }

        private boolean isExpired(Instant now, Duration length) {
            return !now.isBefore(start.plus(length));
        }
    }
}
