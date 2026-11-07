package com.flowboard.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String normalizedUsername);

    boolean existsByUsername(String normalizedUsername);

    /** Atualiza só a última atividade, sem carregar e regravar a entidade inteira. */
    @Transactional
    @Modifying
    @Query("update User u set u.lastSeenAt = :now where u.id = :id")
    int touch(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Apaga visitantes sem atividade desde {@code cutoff}. É um DELETE em massa: o JPA não passa
     * pelas tarefas, quem as apaga é o ON DELETE CASCADE do banco (migração V3).
     */
    @Transactional
    @Modifying
    @Query("delete from User u where u.guest = true and u.lastSeenAt < :cutoff")
    int deleteInactiveGuests(@Param("cutoff") Instant cutoff);

    /**
     * Gasta uma sugestão do orçamento diário de IA. Devolve 1 se havia saldo, 0 se não.
     *
     * Ler o contador, comparar e gravar em passos separados deixaria duas requisições
     * simultâneas passarem do limite. Aqui tudo acontece num único UPDATE: o próprio banco
     * só altera a linha se a condição do WHERE ainda for verdadeira.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE users
               SET ai_suggestions_count = CASE WHEN ai_suggestions_day = :today
                                               THEN ai_suggestions_count + 1 ELSE 1 END,
                   ai_suggestions_day   = :today
             WHERE id = :id
               AND (ai_suggestions_day IS DISTINCT FROM :today OR ai_suggestions_count < :limit)
            """, nativeQuery = true)
    int consumeAiSuggestion(@Param("id") Long id, @Param("today") LocalDate today, @Param("limit") int limit);
}
