//cuida de modal e renderização

let editingTaskId = null;

// Escapa caracteres especiais de HTML. Todo texto vindo do usuário OU da IA passa por aqui
// antes de ir para o innerHTML. Sem isso, um título como <img src=x onerror=...> executaria
// código na página (XSS), e um texto com apóstrofo quebrava o botão de editar.
function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

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
        HEALTH: "Saúde",
        HOME: "Casa",
        FINANCE: "Finanças"
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
    document.querySelector("#task-form button[type='submit']").textContent = "Salvar tarefa";

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

    document.getElementById("title").value = task.title ?? "";
    document.getElementById("description").value = task.description ?? "";
    document.getElementById("priority").value = task.priority;
    document.getElementById("category").value = task.category;

    document.querySelector(".modal h2").textContent = "Editar tarefa";
    document.querySelector("#task-form button[type='submit']").textContent = "Salvar alterações";

    const modal = document.getElementById("task-modal");
    modal.classList.add("show");
}

// Bloco da sugestão. Mostra de onde ela veio (IA ou regras) para o usuário saber o que está lendo.
function renderSuggestion(task) {
    if (task.suggestionSource === "GENERATING") {
        if (task.suggestionTimedOut) {
            return `<div class="suggestion muted">Sugestão indisponível no momento.</div>`;
        }
        return `<div class="suggestion muted"><span class="thinking-dot"></span>Gerando sugestão…</div>`;
    }

    if (!task.suggestion) {
        return `<div class="suggestion muted">Sem sugestão disponível.</div>`;
    }

    // Tarefas antigas (sem suggestionSource) também foram geradas pelas regras.
    const badge = task.suggestionSource === "AI"
        ? `<span class="source-badge source-ai" title="Gerada por um modelo de linguagem">IA</span>`
        : `<span class="source-badge source-rules" title="Gerada por regras locais, sem IA">Regras</span>`;

    const hints = [];
    if (task.suggestedPriority) hints.push(`prioridade ${translatePriority(task.suggestedPriority)}`);
    if (task.suggestedCategory) hints.push(translateCategory(task.suggestedCategory));
    const hintsHtml = hints.length
        ? `<p class="suggestion-hints">Sugerido: ${escapeHtml(hints.join(" · "))}</p>`
        : "";

    const subtasks = task.subtasks || [];
    const subtasksHtml = subtasks.length
        ? `<ul class="subtasks">${subtasks.map(item => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`
        : "";

    return `
        <div class="suggestion">
            <p class="suggestion-text">${badge}${escapeHtml(task.suggestion)}</p>
            ${hintsHtml}
            ${subtasksHtml}
        </div>
    `;
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

        if (isCompleted(task)) {
            taskCard.classList.add("completed");
        }

        // Os botões carregam só o id (data-id). O clique é tratado em script.js,
        // que busca a tarefa na lista em memória. Nada de JSON dentro do HTML.
        taskCard.innerHTML = `
            <div class="task-body">
                <h3>${escapeHtml(task.title)}</h3>
                <p>${escapeHtml(task.description)}</p>

                <div class="task-tags">
                    <span class="${getStatusClass(task.status)}">${escapeHtml(translateStatus(task.status))}</span>
                    <span class="${getPriorityClass(task.priority)}">${escapeHtml(translatePriority(task.priority))}</span>
                    <span>${escapeHtml(translateCategory(task.category))}</span>
                </div>

                ${renderSuggestion(task)}
            </div>

            <div class="task-actions">
                <button class="action-complete" data-action="toggle" data-id="${task.id}" aria-label="Concluir ou reabrir">
                    <i data-lucide="check"></i>
                </button>

                <button class="action-edit" data-action="edit" data-id="${task.id}" aria-label="Editar">
                    <i data-lucide="pencil"></i>
                </button>

                <button class="action-delete" data-action="delete" data-id="${task.id}" aria-label="Excluir">
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
