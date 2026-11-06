package com.flowboard.backend.service;

import com.flowboard.backend.ai.TaskParser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskParseServiceTest {

    /**
     * 01:00 de sexta em UTC (horário do servidor) ainda é 22:00 de quinta em São Paulo.
     * "Hoje" tem que ser quinta, senão "amanhã" viraria sábado para o usuário.
     */
    @Test
    void computesTodayInTheUserTimezoneNotTheServers() {
        TaskParser parser = mock(TaskParser.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), ZoneId.of("America/Sao_Paulo"));

        new TaskParseService(parser, clock).parse("pagar boleto amanhã");

        verify(parser).parse("pagar boleto amanhã", LocalDate.of(2026, 11, 5));
    }
}
