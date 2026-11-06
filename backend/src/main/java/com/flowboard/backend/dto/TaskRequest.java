package com.flowboard.backend.dto;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * O que o cliente PODE enviar ao criar ou editar uma tarefa. Nada além disso.
 *
 * Antes, o controller recebia a entidade Task direto, e o cliente podia mandar qualquer campo,
 * inclusive "id" (o save() do JPA sobrescreveria a tarefa com esse id), "status" ou "suggestion".
 * Esse problema se chama mass assignment. Com um DTO, id, status, sugestão e datas de
 * criação/edição são controlados só pelo servidor.
 *
 * Os limites de 255 caracteres são os das colunas que já existem no banco (varchar(255)).
 */
public record TaskRequest(
        @NotBlank(message = "O título é obrigatório")
        @Size(max = 255, message = "O título pode ter no máximo 255 caracteres")
        String title,

        @Size(max = 255, message = "A descrição pode ter no máximo 255 caracteres")
        String description,

        @NotNull(message = "A prioridade é obrigatória")
        Priority priority,

        @NotNull(message = "A categoria é obrigatória")
        Category category,

        // Opcional: null = sem prazo.
        LocalDate dueDate
) {
}
