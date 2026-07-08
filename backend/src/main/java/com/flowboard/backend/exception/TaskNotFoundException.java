package com.flowboard.backend.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException() {
        super("Tarefa não encontrada");
    }
}