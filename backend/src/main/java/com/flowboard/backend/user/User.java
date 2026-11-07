package com.flowboard.backend.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Uma conta do FlowBoard: com usuário e senha, ou de visitante (guest = true, sem senha).
 *
 * Os instantes são Instant (UTC) e não LocalDateTime: comparar "última atividade há mais de 7 dias"
 * ou "token emitido antes de X" não pode depender do fuso do servidor.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sempre normalizado (sem espaços nas pontas, em minúsculas). */
    private String username;

    /** Hash BCrypt da senha. Nunca a senha em si. Nulo para visitantes. */
    private String passwordHash;

    private boolean guest;

    private Instant createdAt;

    private Instant lastSeenAt;

    /** Tokens com "emitido em" anterior a este instante deixam de valer. */
    private Instant tokensValidAfter;

    private LocalDate aiSuggestionsDay;

    private int aiSuggestionsCount;
}
