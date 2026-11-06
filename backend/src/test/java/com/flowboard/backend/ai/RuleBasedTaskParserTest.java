package com.flowboard.backend.ai;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedTaskParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private final RuleBasedTaskParser parser = new RuleBasedTaskParser();

    @Test
    void usesTextAsTitleAndGuessesWhatItCan() {
        TaskDraft draft = parser.parse("  estudar   AVL pra prova amanhã, urgente ", TODAY);

        assertThat(draft.title()).isEqualTo("Estudar AVL pra prova amanhã, urgente");
        assertThat(draft.priority()).isEqualTo(Priority.HIGH);
        assertThat(draft.category()).isEqualTo(Category.STUDY);
        assertThat(draft.dueDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(draft.source()).isEqualTo(SuggestionSource.FALLBACK);
    }

    @Test
    void depoisDeAmanhaIsNotAmanha() {
        assertThat(parser.parse("pagar boleto depois de amanhã", TODAY).dueDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    void noDateAndMediumPriorityByDefault() {
        TaskDraft draft = parser.parse("ligar para a vó", TODAY);

        assertThat(draft.dueDate()).isNull();
        assertThat(draft.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(draft.category()).isEqualTo(Category.PERSONAL);
    }
}
