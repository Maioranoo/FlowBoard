package com.flowboard.backend.auth;

import com.flowboard.backend.dto.TaskRequest;
import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;

import java.time.LocalDate;
import java.util.List;

/**
 * Tarefas com que um visitante começa, para não cair numa tela vazia. Uma já vem concluída, para
 * os números do resumo (concluídas, produtividade) não começarem zerados. As sugestões delas são
 * geradas pelo fluxo normal, mostrando a IA logo no primeiro contato.
 */
final class GuestSeedTasks {

    record Seed(TaskRequest request, boolean completed) {
    }

    private GuestSeedTasks() {
    }

    static List<Seed> forToday(LocalDate today) {
        return List.of(
                new Seed(new TaskRequest("Estudar para a prova de estrutura de dados",
                        "Árvores AVL e rotações, prova na quinta", Priority.HIGH, Category.STUDY,
                        today.plusDays(3)), false),
                new Seed(new TaskRequest("Preparar apresentação do projeto",
                        "Slides com os resultados do trimestre para a reunião de segunda", Priority.MEDIUM,
                        Category.WORK, today.plusDays(5)), false),
                new Seed(new TaskRequest("Marcar consulta no dentista", "", Priority.LOW, Category.HEALTH, null),
                        true));
    }
}
