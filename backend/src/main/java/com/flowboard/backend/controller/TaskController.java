package com.flowboard.backend.controller;

import com.flowboard.backend.model.Task;
import com.flowboard.backend.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController//recebe requições HTTP
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public Task createTask(@RequestBody Task task) {
        return taskService.createTask(task);
    }

    @GetMapping
    public List<Task> listTasks() {
        return taskService.listTasks();
    }

    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id, @RequestBody Task task) {
        return taskService.updateTask(id, task);
    }
    @PatchMapping("/{id}/toggle-status")//cria a rota para trocar de pendente para completo(toggle)
    public Task toggleStatus(@PathVariable Long id) {
        return taskService.toggleStatus(id);
    }
    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
    }
}