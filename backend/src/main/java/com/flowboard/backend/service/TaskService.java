package com.flowboard.backend.service;

import com.flowboard.backend.exception.TaskNotFoundException;
import com.flowboard.backend.model.Task;
import com.flowboard.backend.repository.TaskRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service //logica de negócio, camada de serviço
public class TaskService {

    private final TaskRepository taskRepository;
    private final FlowAiService flowAiService;

    public TaskService(TaskRepository taskRepository, FlowAiService flowAiService) {
        this.taskRepository = taskRepository;
        this.flowAiService = flowAiService;
    }

    public Task createTask(Task task) {
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setSuggestion(
                flowAiService.generateSuggestion(
                        task.getTitle(),
                        task.getDescription(),
                        task.getPriority()
                )
        );

        return taskRepository.save(task);
    }

    public List<Task> listTasks() {
        return taskRepository.findAll();
    }

    public Task updateTask(Long id, Task updatedTask) {

        Task task = taskRepository.findById(id)
                .orElseThrow(TaskNotFoundException::new);

        task.setTitle(updatedTask.getTitle());
        task.setDescription(updatedTask.getDescription());
        task.setPriority(updatedTask.getPriority());
        task.setCategory(updatedTask.getCategory());

        task.setUpdatedAt(LocalDateTime.now());

        task.setSuggestion(
                flowAiService.generateSuggestion(
                        task.getTitle(),
                        task.getDescription(),
                        task.getPriority()
                )
        );

        return taskRepository.save(task);
    }
    public Task toggleStatus(Long id) {

    Task task = taskRepository.findById(id)
            .orElseThrow(TaskNotFoundException::new);

    if (task.getStatus().equals("PENDING")) {
        task.setStatus("COMPLETED");
    } else {
        task.setStatus("PENDING");
    }

    task.setUpdatedAt(LocalDateTime.now());

    return taskRepository.save(task);
    }
    public void deleteTask(Long id) {

    if (!taskRepository.existsById(id)) {
    throw new TaskNotFoundException();
}

    taskRepository.deleteById(id);
    }


}