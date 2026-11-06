package com.flowboard.backend.ai;

import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;

import java.util.List;
import java.util.Locale;

/**
 * Antigo FlowAiService: regras por palavra-chave, sem nenhum modelo de linguagem.
 * Agora é o FALLBACK, usado quando a API da Anthropic não está disponível.
 *
 * Vantagens: instantâneo, gratuito e nunca falha. Por isso é a rede de segurança.
 * Limitações (honestas): não entende contexto, não gera subtarefas e só repete a
 * prioridade escolhida pelo usuário.
 */
public class RuleBasedTaskAiAssistant implements TaskAiAssistant {

    @Override
    public TaskAiSuggestion suggest(String title, String description, String priority) {
        String text = KeywordRules.normalize((title == null ? "" : title) + " " + (description == null ? "" : description));

        return new TaskAiSuggestion(
                generateSuggestion(text, priority),
                parsePriority(priority),
                KeywordRules.guessCategory(text),
                List.of(),
                SuggestionSource.FALLBACK
        );
    }

    // Regras originais do FlowAiService, mantidas como estavam.
    private String generateSuggestion(String text, String priority) {
        if (text.contains("xcl") || text.contains("projeto") || text.contains("spring") || text.contains("api")) {
            return "Divida o projeto em pequenas etapas e finalize primeiro as funcionalidades principais.";
        }

        if (text.contains("prova") || text.contains("estudar") || text.contains("escola") || text.contains("atividade")
             || text.contains("faculdade")) {
            return "Organize o conteúdo em blocos de estudo e revise os pontos mais importantes primeiro.";
        }

        if (text.contains("mercado") || text.contains("lista") || text.contains("comprar")) {
            return "Faça uma lista antes de sair para economizar tempo e evitar esquecimentos.";
        }

        if (text.contains("academia") || text.contains("agua") || text.contains("treino") || text.contains("corrida")) {
            return "Defina um horário fixo para manter consistência na rotina.";
        }

        if ("HIGH".equalsIgnoreCase(priority)) {
            return "Essa tarefa parece importante. Comece por ela antes das demais.";
        }

        return "Divida essa tarefa em pequenas ações para facilitar a execução.";
    }

    private Priority parsePriority(String priority) {
        if (priority == null) {
            return Priority.MEDIUM;
        }
        try {
            return Priority.valueOf(priority.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Priority.MEDIUM;
        }
    }

}
