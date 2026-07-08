//cuida de modal e renderização

let editingTaskId = null;

function translatePriority(priority) {
    const priorities = {
        LOW: "Baixa",
        MEDIUM: "Média",
        HIGH: "Alta"
    };

    return priorities[priority] || priority;
}

function translateCategory(category) {
    const categories = {
        WORK: "Trabalho",
        STUDY: "Estudos",
        PERSONAL: "Pessoal",
        HEALTH: "Saúde"
    };

    return categories[category] || category;
}

function translateStatus(status) {
    const statuses = {
        PENDING: "Pendente",
        COMPLETED: "Concluída"
    };

    return statuses[status] || status;
}

function getPriorityClass(priority) {
    const priorityClasses = {
        LOW: "priority-low",
        MEDIUM: "priority-medium",
        HIGH: "priority-high"
    };

    return priorityClasses[priority] || "";
}

function getStatusClass(status) {
    return status === "COMPLETED" ? "status-completed" : "status-pending";
}
function openCreateModal() {
    editingTaskId = null;
    document.getElementById("task-form").reset();
    document.querySelector(".modal h2").textContent = "Nova tarefa";
    document.querySelector(".modal .primary-btn").textContent = "Salvar tarefa";

    const modal = document.getElementById("task-modal");
    modal.classList.add("show");
}

function closeModal() {
    editingTaskId = null;
    document.getElementById("task-form").reset();

    const modal = document.getElementById("task-modal");
    modal.classList.remove("show");
}

function openEditModal(task) {
    editingTaskId = task.id;

    document.getElementById("title").value = task.title;
    document.getElementById("description").value = task.description;
    document.getElementById("priority").value = task.priority;
    document.getElementById("category").value = task.category;

    document.querySelector(".modal h2").textContent = "Editar tarefa";
    document.querySelector(".modal .primary-btn").textContent = "Salvar alterações";

    const modal = document.getElementById("task-modal");
    modal.classList.add("show");
}

function renderTasks(tasks) {
    const taskList = document.getElementById("task-list");
    taskList.innerHTML = "";

    if (tasks.length === 0) {
        taskList.innerHTML = `
            <div class="empty-state">
                <h3>🎉 Tudo em dia!</h3>
                <p>Parabéns por concluir suas metas. Que tal planejar o próximo objetivo?</p>
            </div>
        `;
        return;
    }

    tasks.forEach(task => {
        const taskCard = document.createElement("div");
        taskCard.classList.add("task-card");

        if (String(task.status).trim().toUpperCase() === "COMPLETED") {
            taskCard.classList.add("completed");
        }

        taskCard.innerHTML = `
            <div>
                <h3>${task.title}</h3>
                <p>${task.description}</p>

                <div class="task-tags">
                    <span class="${getStatusClass(task.status)}">${translateStatus(task.status)}</span>
                    <span class="${getPriorityClass(task.priority)}">${translatePriority(task.priority)}</span>
                    <span>${translateCategory(task.category)}</span>
                </div>

                <small>🤖 ${task.suggestion || "Sem sugestão disponível."}</small>
            </div>

            <div class="task-actions">
                <button class="action-complete" onclick="handleToggleStatus(${task.id})">
                    <i data-lucide="check"></i>
                </button>

                <button class="action-edit" onclick='openEditModal(${JSON.stringify(task)})'>
                    <i data-lucide="pencil"></i>
                </button>

                <button class="action-delete" onclick="handleDeleteTask(${task.id})">
                    <i data-lucide="trash-2"></i>
                </button>
            </div>
        `;

        taskList.appendChild(taskCard);
    });
    lucide.createIcons();
}

function showToast(message) {
    const toast = document.getElementById("toast");

    toast.textContent = message;
    toast.classList.add("show");

    setTimeout(() => {
        toast.classList.remove("show");
    }, 2500);
}