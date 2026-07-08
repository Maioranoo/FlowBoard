//fala com o back

const API_URL = "https://flowboard-backend-qnut.onrender.com/tasks";

async function getTasks() {
    const response = await fetch(API_URL);
    return await response.json();
}

async function createTask(task) {
    await fetch(API_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(task)
    });
}

async function updateTask(id, task) {
    await fetch(`${API_URL}/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(task)
    });
}

async function toggleTaskStatus(id) {
    await fetch(`${API_URL}/${id}/toggle-status`, {
        method: "PATCH"
    });
}

async function deleteTaskById(id) {
    await fetch(`${API_URL}/${id}`, {
        method: "DELETE"
    });
}