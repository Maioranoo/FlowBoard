//atualiza os numeros

function isCompleted(task) {
    return String(task.status).trim().toUpperCase() === "COMPLETED";
}

function updateDashboard(tasks) {
    const total = tasks.length;
    const completed = tasks.filter(isCompleted).length;
    const pending = total - completed;
    const percent = total === 0 ? 0 : Math.round((completed / total) * 100);

    document.getElementById("total-tasks").textContent = total;
    document.getElementById("completed-tasks").textContent = completed;
    document.getElementById("pending-tasks").textContent = pending;
    document.getElementById("productivity").textContent = `${percent}%`;

    const bar = document.getElementById("productivity-bar");
    bar.style.width = `${percent}%`;
}