package com.flowboard.backend.auth;

/**
 * Login recusado. A mensagem é a MESMA para usuário inexistente e para senha errada: dizer
 * "usuário não existe" ajudaria alguém a descobrir quais nomes estão cadastrados.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Usuário ou senha incorretos");
    }
}
