package com.flowboard.backend.repository;

import com.flowboard.backend.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Toda consulta usada pela API filtra pelo dono. Buscar só pelo id (findById) deixaria um usuário
 * ler a tarefa de outro apenas trocando o número na URL.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);
}
