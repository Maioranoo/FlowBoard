package com.flowboard.backend.model;

/** Situação da tarefa. Os nomes são exatamente os valores já gravados no banco ("PENDING", "COMPLETED"). */
public enum TaskStatus {
    PENDING,
    COMPLETED;

    public TaskStatus toggle() {
        return this == PENDING ? COMPLETED : PENDING;
    }
}
