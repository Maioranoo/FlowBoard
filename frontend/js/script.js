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
        category: document.getElementById("category").value,
        // Campo de data vazio vira null ("sem prazo"), não string vazia.
        dueDate: document.getElementById("dueDate").value || null
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
        showToast("Tarefa salva.");
        watchSuggestion(saved);
    } catch (error) {
        console.error("Erro ao salvar tarefa:", error);
        showToast(validationMessage(error) ?? "Não foi possível salvar. Tente novamente.", "error");
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = originalLabel;
    }
});

// Em um erro 400, devolve a primeira mensagem de campo enviada pelo back (ou null).
function validationMessage(error) {
    if (error.status !== 400 || !error.body) return null;
    const fieldMessages = Object.values(error.body.fields ?? {});
    return fieldMessages[0] ?? error.body.message ?? null;
}

// --- Criar com IA: interpreta a frase e PREENCHE o formulário. Quem salva é o usuário. ---
const aiFillBtn = document.getElementById("ai-fill-btn");

aiFillBtn.addEventListener("click", async () => {
    const text = document.getElementById("ai-text").value.trim();
    if (!text) {
        setAiFillStatus("Escreva a tarefa com suas palavras primeiro.", "error");
        return;
    }

    const originalLabel = aiFillBtn.textContent;
    aiFillBtn.disabled = true;
    aiFillBtn.textContent = "Interpretando…";
    setAiFillStatus("");

    try {
        const draft = await parseTask(text);

        document.getElementById("title").value = draft.title;
        document.getElementById("description").value = draft.description;
        document.getElementById("priority").value = draft.priority;
        document.getElementById("category").value = draft.category;
        document.getElementById("dueDate").value = draft.dueDate ?? "";

        if (draft.source === "AI") {
            setAiFillStatus("Preenchido pela IA. Revise os campos antes de salvar.", "ok");
        } else {
            setAiFillStatus("A IA não respondeu agora; preenchi com regras simples. Revise os campos.", "warn");
        }
        document.getElementById("title").focus();
    } catch (error) {
        console.error("Erro ao interpretar tarefa:", error);
        const message = error.status === 429
            ? "Muitas tentativas seguidas. Aguarde um minuto e tente de novo."
            : "Não foi possível interpretar agora. Preencha os campos manualmente.";
        setAiFillStatus(message, "error");
    } finally {
        aiFillBtn.disabled = false;
        aiFillBtn.textContent = originalLabel;
    }
});

// Se a primeira carga demorar, explica o motivo (servidor gratuito "acordando").
const SLOW_LOAD_HINT_MS = 4000;

async function loadTasks() {
    const taskList = document.getElementById("task-list");
    const slowHintTimer = setTimeout(() => {
        const hint = document.getElementById("slow-hint");
        if (hint) hint.hidden = false;
    }, SLOW_LOAD_HINT_MS);

    try {
        currentTasks = await getTasks();
        refreshView();
        currentTasks.forEach(watchSuggestion);
    } catch (error) {
        console.error("Erro ao carregar tarefas:", error);
        document.getElementById("summary-message").textContent =
            "Não consegui conectar com o servidor.";
        taskList.innerHTML = `
            <div class="empty-state">
                <h3>Não foi possível carregar as tarefas</h3>
                <p>Verifique sua conexão e recarregue a página.</p>
            </div>
        `;
    } finally {
        clearTimeout(slowHintTimer);
        taskList.setAttribute("aria-busy", "false");
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
            if (error.status === 404) {
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
        showToast(updated.status === "COMPLETED" ? "Tarefa concluída." : "Tarefa reaberta.");
    } catch (error) {
        console.error("Erro ao atualizar status:", error);
        showToast("Não foi possível atualizar a tarefa.", "error");
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
        showToast("Tarefa excluída.");
    } catch (error) {
        console.error("Erro ao excluir tarefa:", error);
        showToast("Não foi possível excluir a tarefa.", "error");
    }
});

loadTasks();
