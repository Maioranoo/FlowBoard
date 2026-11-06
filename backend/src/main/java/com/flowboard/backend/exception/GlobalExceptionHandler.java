package com.flowboard.backend.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.flowboard.backend.ratelimit.RateLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converte exceções em respostas HTTP com um formato único:
 * { "timestamp", "status", "error", "message" } e, em erros de validação, "fields" com
 * uma mensagem por campo, ex.: { "title": "O título é obrigatório" }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTaskNotFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage()));
    }

    /** @Valid falhou: devolve TODOS os campos inválidos de uma vez, cada um com sua mensagem. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = body(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Corrija os campos indicados e tente novamente.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * O JSON nem pôde ser lido: sintaxe quebrada, ou um valor que não cabe no tipo,
     * como "priority": "URGENTE" (fora do enum) ou "dueDate": "12/11/2026" (formato errado).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        String message = "JSON inválido no corpo da requisição.";

        if (ex.getCause() instanceof InvalidFormatException invalid && !invalid.getPath().isEmpty()) {
            String field = invalid.getPath().get(invalid.getPath().size() - 1).getFieldName();
            Class<?> type = invalid.getTargetType();
            if (type != null && type.isEnum()) {
                String accepted = Arrays.stream(type.getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                message = "Valor inválido para '" + field + "'. Valores aceitos: " + accepted + ".";
            } else {
                message = "Valor inválido para '" + field + "'.";
            }
        }

        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Dados inválidos", message));
    }

    // 429 Too Many Requests, com o cabeçalho padrão Retry-After dizendo quando tentar de novo.
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, "Muitas requisições", ex.getMessage()));
    }

    private static Map<String, Object> body(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}
