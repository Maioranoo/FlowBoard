package com.flowboard.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.flowboard.backend.model.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {

}