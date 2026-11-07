package com.flowboard.backend.model;

import com.flowboard.backend.ai.TaskAiSuggestion;
import com.flowboard.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
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

    // Fase 3: enums em vez de String. EnumType.STRING grava o NOME ("PENDING", "HIGH", "WORK"),
    // que é exatamente o texto que já está nas linhas existentes, então nada precisa ser migrado.
    // (O padrão do JPA, EnumType.ORDINAL, gravaria 0, 1, 2... e quebraria os dados atuais.)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    private Category category;

    // Prazo (Fase 2): só a data, sem hora, e opcional. Coluna nova, nula nas tarefas antigas.
    private LocalDate dueDate;

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

    // Dono da tarefa. LAZY: listar tarefas não precisa carregar o usuário junto.
    // Nunca sai na API (o TaskResponse não tem esse campo).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User owner;

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
