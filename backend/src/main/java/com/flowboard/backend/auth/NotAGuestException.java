package com.flowboard.backend.auth;

/** Só um visitante pode "virar conta". Vira 409 no GlobalExceptionHandler. */
public class NotAGuestException extends RuntimeException {

    public NotAGuestException() {
        super("Esta conta já está registrada.");
    }
}
