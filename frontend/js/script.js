const openModalBtn = document.getElementById("open-modal-btn");
const closeModalBtn = document.getElementById("close-modal-btn");
const taskForm = document.getElementById("task-form");
let currentFilter = "ALL";
let currentTasks = [];
let taskIdToDelete = null;

openModalBtn.addEventListener("click", openCreateModal);
closeModalBtn.addEventListener("click", closeModal);

taskForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const task = {
        title: document.getElementById("title").value,
        description: document.getElementById("description").value,
        priority: document.getElementById("priority").value,
        category: document.getElementById("category").value
    };

    if (editingTaskId) {
        await updateTask(editingTaskId, task);
    } else {
        await createTask(task);
    }

    closeModal();

    showToast("✅ Tarefa salva com sucesso!");

    loadTasks();
});

async function loadTasks() {
    try {
        currentTasks = await getTasks();

        updateDashboard(currentTasks);
        updateFlowAI(currentTasks);
        applyFilter();

    } catch (error) {
        console.error("Erro ao carregar tarefas:", error);
        document.getElementById("ai-message").textContent =
            "Não consegui conectar com o servidor. Verifique se o back-end está rodando.";
    }
}

function applyFilter() {
    let filteredTasks = currentTasks;

    if (currentFilter === "PENDING") {
        filteredTasks = currentTasks.filter(task => task.status === "PENDING");
    }

    if (currentFilter === "COMPLETED") {
        filteredTasks = currentTasks.filter(task => task.status === "COMPLETED");
    }

    renderTasks(filteredTasks);
}

document.querySelectorAll(".filter-btn").forEach(button => {
    button.addEventListener("click", () => {
        document.querySelectorAll(".filter-btn").forEach(btn => {
            btn.classList.remove("active");
        });

        button.classList.add("active");

        const filterText = button.textContent.trim();

        if (filterText === "Todas") currentFilter = "ALL";
        if (filterText === "Pendentes") currentFilter = "PENDING";
        if (filterText === "Concluídas") currentFilter = "COMPLETED";

        applyFilter();
    });
});

async function handleToggleStatus(id) {
    await toggleTaskStatus(id);
    showToast("🎉 Tarefa atualizada!");
    loadTasks();
}

function handleDeleteTask(id) {
    taskIdToDelete = id;
    document.getElementById("delete-modal").classList.add("show");
}
document.getElementById("cancel-delete-btn").addEventListener("click", () => {
    taskIdToDelete = null;
    document.getElementById("delete-modal").classList.remove("show");
});

document.getElementById("confirm-delete-btn").addEventListener("click", async () => {
    if (!taskIdToDelete) return;

    await deleteTaskById(taskIdToDelete);

    taskIdToDelete = null;
    document.getElementById("delete-modal").classList.remove("show");

    showToast("🗑️ Tarefa removida!");
    loadTasks();
});

loadTasks();