package com.flowboard.backend.ai;

import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;

import java.time.LocalDate;

/**
 * Fallback do parse: regras simples, sem IA.
 *
 * Limitações (honestas): o título é o próprio texto digitado, não entende dias da semana
 * nem datas como "dia 15". Só reconhece "hoje", "amanhã" e "depois de amanhã".
 * O front avisa o usuário para revisar os campos quando o rascunho vem daqui.
 */
public class RuleBasedTaskParser implements TaskParser {

    @Override
    public TaskDraft parse(String text, LocalDate today) {
        String clean = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        String lower = KeywordRules.normalize(clean);

        return new TaskDraft(
                capitalize(clean),
                "",
                KeywordRules.containsAny(lower, "urgente", "urgência", "urgencia", "hoje") ? Priority.HIGH : Priority.MEDIUM,
                KeywordRules.guessCategory(lower),
                guessDueDate(lower, today),
                SuggestionSource.FALLBACK
        );
    }

    // A ordem importa: "depois de amanhã" também contém "amanhã".
    private LocalDate guessDueDate(String lower, LocalDate today) {
        if (KeywordRules.containsAny(lower, "depois de amanhã", "depois de amanha")) {
            return today.plusDays(2);
        }
        if (KeywordRules.containsAny(lower, "amanhã", "amanha")) {
            return today.plusDays(1);
        }
        if (lower.contains("hoje")) {
            return today;
        }
        return null;
    }

    private String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
