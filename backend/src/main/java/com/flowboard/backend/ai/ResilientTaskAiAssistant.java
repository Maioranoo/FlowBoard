package com.flowboard.backend.ai;

/**
 * Sugestões com resiliência: tenta a IA e cai para as regras locais se algo der errado.
 * A regra de fallback em si (e os logs) fica em AiFallback, compartilhada com o parse.
 */
public class ResilientTaskAiAssistant implements TaskAiAssistant {

    private final TaskAiAssistant primary;
    private final TaskAiAssistant fallback;
    private final boolean primaryEnabled;

    public ResilientTaskAiAssistant(TaskAiAssistant primary, TaskAiAssistant fallback, boolean primaryEnabled) {
        this.primary = primary;
        this.fallback = fallback;
        this.primaryEnabled = primaryEnabled;
    }

    @Override
    public TaskAiSuggestion suggest(String title, String description, String priority) {
        return AiFallback.withFallback("sugestão", primaryEnabled,
                () -> primary.suggest(title, description, priority),
                () -> fallback.suggest(title, description, priority));
    }
}
