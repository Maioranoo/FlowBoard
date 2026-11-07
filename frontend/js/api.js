//fala com o back

const API_BASE = "https://flowboard-backend-qnut.onrender.com";

// --- Sessão: o token de login fica no localStorage do navegador ---
// O token só vai no cabeçalho Authorization quando ESTE código o coloca lá (não é um cookie que o
// navegador envia sozinho). O risco do localStorage é um script malicioso na página lê-lo (XSS);
// por isso todo texto do usuário e da IA é escapado antes de virar HTML (ver escapeHtml em ui.js).
const TOKEN_KEY = "flowboard.token";
// Se a sessão é de visitante. Guardado junto do token para o "Sair" pedir confirmação mesmo antes
// de o servidor (talvez acordando) responder quem é o usuário.
const GUEST_KEY = "flowboard.guest";
let memoryToken = null; // reserva, caso o navegador bloqueie o localStorage (ex.: modo privado restrito)
let memoryGuest = false;

function getToken() {
    try {
        return localStorage.getItem(TOKEN_KEY) ?? memoryToken;
    } catch {
        return memoryToken;
    }
}

function isStoredGuest() {
    try {
        const stored = localStorage.getItem(GUEST_KEY);
        return stored === null ? memoryGuest : stored === "true";
    } catch {
        return memoryGuest;
    }
}

function setSession(token, guest) {
    memoryToken = token;
    memoryGuest = guest;
    try {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(GUEST_KEY, String(guest));
    } catch {
        // Sem localStorage a sessão vale só enquanto a aba estiver aberta.
    }
}

function clearSession() {
    memoryToken = null;
    memoryGuest = false;
    try {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(GUEST_KEY);
    } catch {
        // nada a limpar
    }
}

// Rotas que não usam token. Mandar um token velho para elas faria até o login falhar.
const PUBLIC_PATHS = ["/auth/login", "/auth/register", "/auth/guest"];

// Função central: faz a requisição, lança erro se o status não for 2xx
// e devolve o JSON da resposta (ou null quando o corpo vem vazio, como no DELETE).
async function request(path, options = {}, isRetry = false) {
    const isPublic = PUBLIC_PATHS.includes(path);
    const headers = { ...(options.headers ?? {}) };
    const sentToken = getToken();
    if (sentToken && !isPublic) {
        headers.Authorization = `Bearer ${sentToken}`;
    }

    const response = await fetch(`${API_BASE}${path}`, { ...options, headers });

    // 401 com um token que JÁ FOI TROCADO (ex.: visitante virou conta enquanto esta requisição
    // viajava): não é a sessão atual que expirou. Repete uma vez com o token atual.
    if (response.status === 401 && !isPublic && !isRetry && sentToken && getToken() && sentToken !== getToken()) {
        return request(path, options, true);
    }

    if (!response.ok) {
        const error = new Error(`Erro HTTP ${response.status}`);
        error.status = response.status; // permite tratar 401, 404, 429 etc. sem comparar texto
        // O back devolve { message, fields: { campo: "mensagem" } } nos erros de validação.
        try {
            error.body = await response.json();
        } catch {
            error.body = null;
        }
        // 401 numa rota protegida, com o token que AINDA é o atual = sessão expirada, token inválido
        // ou visitante já apagado. Um 401 atrasado de uma sessão anterior não derruba a nova.
        if (response.status === 401 && !isPublic && sentToken && sentToken === getToken()) {
            onSessionExpired();
        }
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

// --- Conta ---

async function register(username, password) {
    return request("/auth/register", jsonOptions("POST", { username, password }));
}

async function login(username, password) {
    return request("/auth/login", jsonOptions("POST", { username, password }));
}

// Cria um visitante com tarefas de exemplo. Não precisa de corpo.
async function startGuest() {
    return request("/auth/guest", { method: "POST" });
}

// Visitante vira conta, mantendo as tarefas. Devolve um token novo.
async function upgradeGuest(username, password) {
    return request("/auth/upgrade", jsonOptions("POST", { username, password }));
}

async function getMe() {
    return request("/auth/me");
}

// --- Tarefas (sempre do usuário logado) ---

async function getTasks() {
    return request("/tasks");
}

async function getTaskById(id) {
    return request(`/tasks/${id}`);
}

// Devolve a tarefa salva. O front usa essa resposta direto, sem recarregar a lista inteira.
async function createTask(task) {
    return request("/tasks", jsonOptions("POST", task));
}

// Interpreta uma frase e devolve a tarefa preenchida (NÃO salva nada).
async function parseTask(text) {
    return request("/tasks/parse", jsonOptions("POST", { text }));
}

async function updateTask(id, task) {
    return request(`/tasks/${id}`, jsonOptions("PUT", task));
}

async function toggleTaskStatus(id) {
    return request(`/tasks/${id}/toggle-status`, { method: "PATCH" });
}

async function deleteTaskById(id) {
    return request(`/tasks/${id}`, { method: "DELETE" });
}
