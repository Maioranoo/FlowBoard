//fala com o back

const API_URL = "https://flowboard-backend-qnut.onrender.com/tasks";

// Função central: faz a requisição, lança erro se o status não for 2xx
// e devolve o JSON da resposta (ou null quando o corpo vem vazio, como no DELETE).
async function request(path, options = {}) {
    const response = await fetch(`${API_URL}${path}`, options);

    if (!response.ok) {
        const error = new Error(`Erro HTTP ${response.status}`);
        error.status = response.status; // permite tratar 404, 429 etc. sem comparar texto
        throw error;
    }

    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

function jsonOptions(method, body) {
    return {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    };
}

async function getTasks() {
    return request("");
}

async function getTaskById(id) {
    return request(`/${id}`);
}

// Devolve a tarefa salva. O front usa essa resposta direto, sem recarregar a lista inteira.
async function createTask(task) {
    return request("", jsonOptions("POST", task));
}

// Interpreta uma frase e devolve a tarefa preenchida (NÃO salva nada).
async function parseTask(text) {
    return request("/parse", jsonOptions("POST", { text }));
}

async function updateTask(id, task) {
    return request(`/${id}`, jsonOptions("PUT", task));
}

async function toggleTaskStatus(id) {
    return request(`/${id}/toggle-status`, { method: "PATCH" });
}

async function deleteTaskById(id) {
    return request(`/${id}`, { method: "DELETE" });
}
