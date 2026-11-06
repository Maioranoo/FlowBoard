package com.flowboard.backend.ai;

import com.flowboard.backend.model.Category;

import java.util.Locale;

/**
 * Regras por palavra-chave compartilhadas pelos fallbacks (sugestão e parse).
 * Simples de propósito: são a rede de segurança para quando a IA não está disponível.
 */
final class KeywordRules {

    private KeywordRules() {
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /** Categoria por palavra-chave. A ordem importa: a primeira regra que casar vence. */
    static Category guessCategory(String lowerText) {
        if (containsAny(lowerText, "prova", "estudar", "escola", "atividade", "faculdade", "aula", "trabalho de")) {
            return Category.STUDY;
        }
        if (containsAny(lowerText, "xcl", "projeto", "spring", "api", "reunião", "reuniao", "cliente", "deploy")) {
            return Category.WORK;
        }
        if (containsAny(lowerText, "pagar", "conta", "boleto", "fatura", "banco", "imposto", "investir", "orçamento")) {
            return Category.FINANCE;
        }
        if (containsAny(lowerText, "academia", "agua", "água", "treino", "corrida", "médico", "medico", "consulta", "dentista")) {
            return Category.HEALTH;
        }
        if (containsAny(lowerText, "mercado", "comprar", "lista", "limpar", "lavar", "casa", "faxina", "consertar")) {
            return Category.HOME;
        }
        return Category.PERSONAL;
    }

    static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
