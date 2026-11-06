package com.flowboard.backend.service;

import com.flowboard.backend.ai.TaskDraft;
import com.flowboard.backend.ai.TaskParser;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Criação de tarefa por linguagem natural: interpreta o texto e devolve um rascunho, SEM salvar.
 *
 * "Hoje" é calculado com um Clock configurado no fuso do usuário (America/Sao_Paulo por padrão).
 * Isso importa porque o servidor do Render roda em UTC: às 22h de quarta no Brasil já é quinta
 * em UTC, e "amanhã" viraria sexta.
 */
@Service
public class TaskParseService {

    private final TaskParser taskParser;
    private final Clock clock;

    public TaskParseService(TaskParser taskParser, Clock clock) {
        this.taskParser = taskParser;
        this.clock = clock;
    }

    public TaskDraft parse(String text) {
        LocalDate today = LocalDate.now(clock);
        return taskParser.parse(text, today);
    }
}
