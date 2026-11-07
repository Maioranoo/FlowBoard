package com.flowboard.backend.auth;

/** Nome já usado por outra conta (ou reservado, como "visitante"). Vira 409 no GlobalExceptionHandler. */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException() {
        super("Este nome de usuário já está em uso");
    }
}
