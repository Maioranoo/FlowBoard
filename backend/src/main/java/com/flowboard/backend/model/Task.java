package com.flowboard.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
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

    private String suggestion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}