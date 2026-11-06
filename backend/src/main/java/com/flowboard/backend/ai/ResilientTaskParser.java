package com.flowboard.backend.ai;

import java.time.LocalDate;

/** Parse com resiliência: tenta a IA e cai para as regras locais se algo der errado (ver AiFallback). */
public class ResilientTaskParser implements TaskParser {

    private final TaskParser primary;
    private final TaskParser fallback;
    private final boolean primaryEnabled;

    public ResilientTaskParser(TaskParser primary, TaskParser fallback, boolean primaryEnabled) {
        this.primary = primary;
        this.fallback = fallback;
        this.primaryEnabled = primaryEnabled;
    }

    @Override
    public TaskDraft parse(String text, LocalDate today) {
        return AiFallback.withFallback("parse", primaryEnabled,
                () -> primary.parse(text, today),
                () -> fallback.parse(text, today));
    }
}
