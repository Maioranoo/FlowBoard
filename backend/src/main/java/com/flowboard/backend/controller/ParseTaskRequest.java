package com.flowboard.backend.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo do POST /tasks/parse. O limite de tamanho também limita quanto cada chamada custa em tokens. */
public record ParseTaskRequest(
        @NotBlank(message = "O texto é obrigatório")
        @Size(max = 500, message = "O texto pode ter no máximo 500 caracteres")
        String text
) {
}
