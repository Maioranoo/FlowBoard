package com.flowboard.backend.ai;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import com.flowboard.backend.model.SuggestionSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskAiSuggestionTest {

    @Test
    void acceptsLowercaseEnumsAndNormalizesText() {
        TaskAiSuggestion s = TaskAiSuggestion.of("  Dica  ", "high", "study",
                Arrays.asList(" Passo 1 ", "", null, "Passo 2"), SuggestionSource.AI);

        assertThat(s.suggestion()).isEqualTo("Dica");
        assertThat(s.suggestedPriority()).isEqualTo(Priority.HIGH);
        assertThat(s.suggestedCategory()).isEqualTo(Category.STUDY);
        assertThat(s.subtasks()).containsExactly("Passo 1", "Passo 2");
    }

    @Test
    void truncatesWhatIsOnlyTooLong() {
        TaskAiSuggestion s = TaskAiSuggestion.of("a".repeat(400), "LOW", "WORK",
                List.of("1", "2", "3", "4", "5", "6", "7"), SuggestionSource.AI);

        assertThat(s.suggestion()).hasSize(TaskAiSuggestion.MAX_SUGGESTION_LENGTH).endsWith("…");
        assertThat(s.subtasks()).hasSize(TaskAiSuggestion.MAX_SUBTASKS);
    }

    @Test
    void rejectsWhatIsWrong() {
        assertThatThrownBy(() -> TaskAiSuggestion.of(" ", "LOW", "WORK", List.of(), SuggestionSource.AI))
                .isInstanceOf(InvalidAiResponseException.class);
        assertThatThrownBy(() -> TaskAiSuggestion.of("Dica", "URGENT", "WORK", List.of(), SuggestionSource.AI))
                .isInstanceOf(InvalidAiResponseException.class);
        assertThatThrownBy(() -> TaskAiSuggestion.of("Dica", "LOW", null, List.of(), SuggestionSource.AI))
                .isInstanceOf(InvalidAiResponseException.class);
    }

    /** Se alguém criar uma categoria nova no enum e esquecer de explicá-la nos prompts, este teste falha. */
    @Test
    void systemPromptsDescribeEveryCategory() throws Exception {
        for (String file : List.of("prompts/task-suggestion-system.txt", "prompts/task-parse-system.txt")) {
            String prompt = new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8);

            for (Category category : Category.values()) {
                assertThat(prompt).as(file).contains("- " + category.name() + ":");
            }
        }
    }
}
