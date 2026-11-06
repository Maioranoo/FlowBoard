package com.flowboard.backend.ai;

/** A resposta do modelo chegou, mas não pode ser usada (JSON inválido, campo faltando, valor fora do permitido...). */
public class InvalidAiResponseException extends RuntimeException {

    public InvalidAiResponseException(String message) {
        super(message);
    }

    public InvalidAiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
