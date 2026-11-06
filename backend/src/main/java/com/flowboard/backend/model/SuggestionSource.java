package com.flowboard.backend.model;

/**
 * De onde veio a sugestão de uma tarefa.
 * GENERATING: a tarefa foi salva e a sugestão ainda está sendo gerada em segundo plano.
 * AI:         gerada pelo modelo da Anthropic e validada.
 * FALLBACK:   gerada pelas regras locais (API sem chave, fora do ar, lenta ou resposta inválida).
 * Tarefas antigas, criadas antes desta coluna existir, ficam com null.
 */
public enum SuggestionSource {
    GENERATING,
    AI,
    FALLBACK
}
