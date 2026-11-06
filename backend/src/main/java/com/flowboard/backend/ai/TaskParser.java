package com.flowboard.backend.ai;

import java.time.LocalDate;

/**
 * Converte texto livre ("estudar AVL pra prova quinta, urgente") em um rascunho de tarefa.
 * Mesmo desenho do TaskAiAssistant: uma implementação com IA, uma com regras e uma resiliente.
 */
public interface TaskParser {

    /**
     * @param text  frase digitada pelo usuário
     * @param today data de hoje no fuso do usuário. É necessária para resolver "amanhã", "quinta" etc.
     */
    TaskDraft parse(String text, LocalDate today);
}
