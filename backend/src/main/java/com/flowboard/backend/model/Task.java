package com.flowboard.backend.model;

import com.flowboard.backend.ai.TaskAiSuggestion;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.Table;
//gera os getters e setters automaticos, não preciso escrever igual no apache.
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name ="tasks")
@Getter
@Setter
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    private String status;

    private String priority;

    private String category;

    // A coluna já existe no banco como varchar(255). O ddl-auto=update cria colunas
    // novas, mas NÃO aumenta colunas existentes, por isso o texto é limitado a 255.
    private String suggestion;

    // Colunas novas da Fase 1. Entram como nulas nas linhas antigas (compatível).
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority suggestedPriority;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category suggestedCategory;

    @Convert(converter = StringListConverter.class)
    @Column(length = 2000)
    private List<String> subtasks;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SuggestionSource suggestionSource;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Limpa a sugestão anterior e marca que uma nova está sendo gerada. */
    public void markSuggestionGenerating() {
        this.suggestion = null;
        this.suggestedPriority = null;
        this.suggestedCategory = null;
        this.subtasks = null;
        this.suggestionSource = SuggestionSource.GENERATING;
    }

    /** Copia uma sugestão (já validada) para a tarefa. */
    public void applySuggestion(TaskAiSuggestion suggestion) {
        this.suggestion = suggestion.suggestion();
        this.suggestedPriority = suggestion.suggestedPriority();
        this.suggestedCategory = suggestion.suggestedCategory();
        this.subtasks = suggestion.subtasks();
        this.suggestionSource = suggestion.source();
    }
}
