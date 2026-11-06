const openModalBtn = document.getElementById("open-modal-btn");
const closeModalBtn = document.getElementById("close-modal-btn");
const taskForm = document.getElementById("task-form");
const submitBtn = taskForm.querySelector("button[type='submit']");
let currentFilter = "ALL";
let currentTasks = [];
let taskIdToDelete = null;

// Busca da sugestão gerada em segundo plano: a cada 1,5 s, por no máximo ~18 s.
const SUGGESTION_POLL_INTERVAL_MS = 1500;
const SUGGESTION_POLL_MAX_ATTEMPTS = 12;
const watchedTaskIds = new Set();

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

    const taskId = editingTaskId;
    const originalLabel = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = "Salvando…";

    try {
        // O back devolve a tarefa salva; usamos ela direto, sem buscar a lista inteira de novo.
        const saved = taskId ? await updateTask(taskId, task) : await createTask(task);

        upsertTask(saved);
        closeModal();
        showToast("✅ Tarefa salva com sucesso!");
        watchSuggestion(saved);
    } catch (error) {
        console.error("Erro ao salvar tarefa:", error);
        showToast("❌ Não foi possível salvar. Tente novamente.");
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = originalLabel;
    }
});

async function loadTasks() {
    try {
        currentTasks = await getTasks();
        refreshView();
        currentTasks.forEach(watchSuggestion);
    } catch (error) {
        console.error("Erro ao carregar tarefas:", error);
        document.getElementById("summary-message").textContent =
            "Não consegui conectar com o servidor. Verifique se o back-end está rodando.";
    }
}

function refreshView() {
    updateDashboard(currentTasks);
    updateProductivitySummary(currentTasks);
    applyFilter();
}

// Insere a tarefa na lista em memória (ou substitui, se já existir) e redesenha a tela.
function upsertTask(task) {
    const index = currentTasks.findIndex(t => t.id === task.id);
    if (index >= 0) {
        currentTasks[index] = task;
    } else {
        currentTasks.push(task);
    }
    refreshView();
}

function removeTask(id) {
    currentTasks = currentTasks.filter(task => task.id !== id);
    refreshView();
}

// A sugestão é gerada no back depois que a tarefa é salva. Enquanto ela estiver
// "GENERATING", consultamos GET /tasks/{id} de tempos em tempos até ficar pronta.
function watchSuggestion(task) {
    if (task.suggestionSource !== "GENERATING" || watchedTaskIds.has(task.id)) {
        return;
    }
    watchedTaskIds.add(task.id);

    let attempts = 0;

    const check = async () => {
        attempts++;
        try {
            const updated = await getTaskById(task.id);
            if (updated.suggestionSource !== "GENERATING") {
                watchedTaskIds.delete(task.id);
                upsertTask(updated);
                return;
            }
        } catch (error) {
            // 404 = a tarefa foi excluída; outros erros: tenta de novo até o limite.
            if (String(error.message).includes("404")) {
                watchedTaskIds.delete(task.id);
                return;
            }
        }

        if (attempts < SUGGESTION_POLL_MAX_ATTEMPTS) {
            setTimeout(check, SUGGESTION_POLL_INTERVAL_MS);
        } else {
            watchedTaskIds.delete(task.id);
            const current = currentTasks.find(t => t.id === task.id);
            if (current && current.suggestionSource === "GENERATING") {
                current.suggestionTimedOut = true;
                refreshView();
            }
        }
    };

    setTimeout(check, SUGGESTION_POLL_INTERVAL_MS);
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

// Um único "ouvinte" de clique para todos os botões dos cards (delegação de eventos).
document.getElementById("task-list").addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;

    const id = Number(button.dataset.id);
    const action = button.dataset.action;

    if (action === "toggle") handleToggleStatus(id);
    if (action === "edit") {
        const task = currentTasks.find(t => t.id === id);
        if (task) openEditModal(task);
    }
    if (action === "delete") handleDeleteTask(id);
});

async function handleToggleStatus(id) {
    try {
        const updated = await toggleTaskStatus(id);
        upsertTask(updated);
        showToast("🎉 Tarefa atualizada!");
    } catch (error) {
        console.error("Erro ao atualizar status:", error);
        showToast("❌ Não foi possível atualizar a tarefa.");
    }
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

    const id = taskIdToDelete;
    taskIdToDelete = null;
    document.getElementById("delete-modal").classList.remove("show");

    try {
        await deleteTaskById(id);
        removeTask(id);
        showToast("🗑️ Tarefa removida!");
    } catch (error) {
        console.error("Erro ao excluir tarefa:", error);
        showToast("❌ Não foi possível excluir a tarefa.");
    }
});

loadTasks();
