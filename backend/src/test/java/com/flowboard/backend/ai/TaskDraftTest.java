package com.flowboard.backend.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDraftTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @Test
    void acceptsDatesFromTodayUpToTwoYears() {
        assertThat(TaskDraft.parseDueDate("2026-11-06", TODAY)).isEqualTo(TODAY);
        assertThat(TaskDraft.parseDueDate(" 2027-03-01 ", TODAY)).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(TaskDraft.parseDueDate("2028-11-06", TODAY)).isEqualTo(LocalDate.of(2028, 11, 6));
    }

    @Test
    void dropsEmptyMalformedPastOrFarFutureDates() {
        assertThat(TaskDraft.parseDueDate("", TODAY)).isNull();
        assertThat(TaskDraft.parseDueDate(null, TODAY)).isNull();
        assertThat(TaskDraft.parseDueDate("12/11/2026", TODAY)).isNull();
        assertThat(TaskDraft.parseDueDate("2026-02-30", TODAY)).isNull();
        assertThat(TaskDraft.parseDueDate("2026-11-05", TODAY)).isNull();
        assertThat(TaskDraft.parseDueDate("2030-01-01", TODAY)).isNull();
    }
}
