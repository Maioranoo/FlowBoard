package com.flowboard.backend.service;

import org.springframework.stereotype.Service;

@Service
public class FlowAiService {

    public String generateSuggestion(String title, String description, String priority) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description)).toLowerCase();

        if (text.contains("xcl") || text.contains("projeto") || text.contains("spring") || text.contains("api")) {
            return "Divida o projeto em pequenas etapas e finalize primeiro as funcionalidades principais.";
        }

        if (text.contains("prova") || text.contains("estudar") || text.contains("escola") || text.contains("atividade")
             || text.contains("faculdade")) {
            return "Organize o conteúdo em blocos de estudo e revise os pontos mais importantes primeiro.";
        }

        if (text.contains("mercado") || text.contains("lista") || text.contains("comprar")) {
            return "Faça uma lista antes de sair para economizar tempo e evitar esquecimentos.";
        }

        if (text.contains("academia") || text.contains("agua") || text.contains("treino") || text.contains("corrida")) {
            return "Defina um horário fixo para manter consistência na rotina.";
        }

        if ("HIGH".equalsIgnoreCase(priority)) {
            return "Essa tarefa parece importante. Comece por ela antes das demais.";
        }

        return "Divida essa tarefa em pequenas ações para facilitar a execução.";
    }
}