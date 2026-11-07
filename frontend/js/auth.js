// Entrada, sessão e conta: decide entre a tela de entrada e o app.

// Muda a cada entrada/saída. Respostas que chegam de uma sessão anterior (lista, sugestões)
// comparam este número e são descartadas, em vez de aparecer na tela da próxima pessoa.
let sessionEpoch = 0;
let currentUser = null; // { username, guest }
let authMode = "login"; // "login" | "register"
let sessionExpiredNotified = false;

const SLOW_AUTH_HINT_MS = 4000;

const authView = document.getElementById("auth-view");
const appView = document.getElementById("app-view");
const authChoice = document.getElementById("auth-choice");
const authForm = document.getElementById("auth-form");
const guestBtn = document.getElementById("guest-btn");

// ---------- Trocar de tela ----------

function showAuthView(notice = "") {
    closeAllModals();
    document.getElementById("toast").classList.remove("show"); // aviso da sessão anterior não segue para a entrada
    appView.hidden = true;
    authView.hidden = false;
    document.title = "FlowBoard · Entrar";

    const noticeEl = document.getElementById("auth-notice");
    noticeEl.textContent = notice;
    noticeEl.hidden = !notice;
    showAuthChoice();
}

function showAppView(me) {
    authView.hidden = true;
    appView.hidden = false;
    document.title = "FlowBoard · Tarefas com sugestões de IA";
    sessionExpiredNotified = false;

    resetTaskState();
    renderUserArea(me);
    loadTasks();
}

function renderUserArea(me) {
    currentUser = me;
    const name = document.getElementById("user-name");
    const upgradeBtn = document.getElementById("upgrade-btn");

    if (!me) {
        name.textContent = "";
        upgradeBtn.hidden = true;
        return;
    }
    name.textContent = me.guest ? "Visitante" : me.username;
    upgradeBtn.hidden = !me.guest;
}

// Login, cadastro, visitante e upgrade devolvem { token, username, guest }.
function startSession(auth) {
    setSession(auth.token, auth.guest);
    sessionEpoch++;
    showAppView({ username: auth.username, guest: auth.guest });
}

function endSession(notice = "") {
    clearSession();
    sessionEpoch++;
    currentUser = null;
    showAuthView(notice);
}

// Chamado pelo api.js em qualquer 401 de rota protegida. Age uma vez só por expiração,
// mesmo que várias requisições recebam 401 ao mesmo tempo.
function onSessionExpired() {
    if (sessionExpiredNotified) return;
    sessionExpiredNotified = true;
    endSession("Sua sessão expirou. Entre novamente.");
}

// ---------- Tela de entrada ----------

function showAuthChoice() {
    authChoice.hidden = false;
    authForm.hidden = true;
    clearAuthErrors();
}

function showAuthForm(mode) {
    authMode = mode;
    const isRegister = mode === "register";

    authChoice.hidden = true;
    authForm.hidden = false;
    document.getElementById("auth-form-title").textContent = isRegister ? "Nova conta" : "Entrar na sua conta";
    document.getElementById("auth-submit").textContent = isRegister ? "Criar conta" : "Entrar";
    document.getElementById("auth-switch").textContent = isRegister
        ? "Já tem conta? Entrar"
        : "Não tem conta? Criar conta";
    document.getElementById("auth-confirm-field").hidden = !isRegister;
    // Ajuda o gerenciador de senhas do navegador a sugerir/salvar a senha certa.
    document.getElementById("auth-password").autocomplete = isRegister ? "new-password" : "current-password";

    clearAuthErrors();
    document.getElementById("auth-username").focus();
}

function clearAuthErrors() {
    ["auth-username-error", "auth-password-error", "auth-confirm-error", "auth-error"].forEach(id => {
        document.getElementById(id).textContent = "";
    });
}

/**
 * Desabilita o botão durante a requisição (evita cadastro ou visitante duplicado por clique duplo)
 * e, se demorar, mostra o aviso de servidor acordando. Devolve a função que desfaz tudo.
 */
function setBusy(button, busyLabel) {
    const originalLabel = button.textContent;
    button.disabled = true;
    button.textContent = busyLabel;
    const hint = document.getElementById("auth-slow-hint");
    const timer = setTimeout(() => { hint.hidden = false; }, SLOW_AUTH_HINT_MS);

    return () => {
        clearTimeout(timer);
        hint.hidden = true;
        button.disabled = false;
        button.textContent = originalLabel;
    };
}

/**
 * Mostra o erro da API no lugar certo: mensagens por campo ao lado do campo (400 e 409),
 * o resto numa linha geral do formulário.
 */
function showRequestError(error, prefix) {
    const fields = error.body?.fields ?? {};
    let placed = false;
    for (const [field, message] of Object.entries(fields)) {
        const el = document.getElementById(`${prefix}-${field}-error`);
        if (el) {
            el.textContent = message;
            placed = true;
        }
    }
    if (placed) return;

    let message = "Não foi possível conectar. Tente novamente.";
    if (error.status === 401) message = error.body?.message ?? "Usuário ou senha incorretos";
    else if (error.status === 429) message = "Muitas tentativas. Tente de novo em instantes.";
    else if (error.body?.message) message = error.body.message;
    document.getElementById(`${prefix}-error`).textContent = message;
}

// Validações que o servidor não pode fazer (a confirmação nunca sai do navegador).
function checkCredentials(prefix, { requireConfirm }) {
    const username = document.getElementById(`${prefix}-username`).value.trim();
    const password = document.getElementById(`${prefix}-password`).value;
    let ok = true;

    if (!username) {
        document.getElementById(`${prefix}-username-error`).textContent = "Informe o nome de usuário";
        ok = false;
    }
    if (!password) {
        document.getElementById(`${prefix}-password-error`).textContent = "Informe a senha";
        ok = false;
    }
    if (requireConfirm && password !== document.getElementById(`${prefix}-confirm`).value) {
        document.getElementById(`${prefix}-confirm-error`).textContent = "As senhas não conferem";
        ok = false;
    }
    return ok ? { username, password } : null;
}

guestBtn.addEventListener("click", async () => {
    if (guestBtn.disabled) return;
    const done = setBusy(guestBtn, "Preparando seu espaço…");
    try {
        startSession(await startGuest());
    } catch (error) {
        console.error("Erro ao criar visitante:", error);
        const notice = document.getElementById("auth-notice");
        notice.textContent = error.status === 429
            ? "Muitos acessos em sequência. Tente de novo em alguns minutos."
            : "Não foi possível conectar. Tente novamente.";
        notice.hidden = false;
    } finally {
        done();
    }
});

document.getElementById("show-login-btn").addEventListener("click", () => showAuthForm("login"));
document.getElementById("show-register-btn").addEventListener("click", () => showAuthForm("register"));
document.getElementById("auth-back").addEventListener("click", showAuthChoice);
document.getElementById("auth-switch").addEventListener("click", () => {
    showAuthForm(authMode === "login" ? "register" : "login");
});

authForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearAuthErrors();

    const isRegister = authMode === "register";
    const credentials = checkCredentials("auth", { requireConfirm: isRegister });
    if (!credentials) return;

    const submit = document.getElementById("auth-submit");
    const done = setBusy(submit, isRegister ? "Criando conta…" : "Entrando…");
    try {
        const auth = isRegister
            ? await register(credentials.username, credentials.password)
            : await login(credentials.username, credentials.password);
        authForm.reset();
        startSession(auth);
    } catch (error) {
        console.error("Erro ao entrar:", error);
        showRequestError(error, "auth");
    } finally {
        done();
    }
});

// ---------- Visitante: criar conta e sair ----------

const upgradeModal = document.getElementById("upgrade-modal");
const upgradeForm = document.getElementById("upgrade-form");
const logoutGuestModal = document.getElementById("logout-guest-modal");

function openUpgradeModal() {
    logoutGuestModal.classList.remove("show");
    upgradeForm.reset();
    ["upgrade-username-error", "upgrade-password-error", "upgrade-confirm-error", "upgrade-error"]
        .forEach(id => { document.getElementById(id).textContent = ""; });
    upgradeModal.classList.add("show");
    document.getElementById("upgrade-username").focus();
}

function closeAllModals() {
    document.querySelectorAll(".modal-overlay.show").forEach(modal => modal.classList.remove("show"));
}

document.getElementById("upgrade-btn").addEventListener("click", openUpgradeModal);
document.getElementById("logout-to-upgrade-btn").addEventListener("click", openUpgradeModal);
document.getElementById("cancel-upgrade-btn").addEventListener("click", () => upgradeModal.classList.remove("show"));

upgradeForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    ["upgrade-username-error", "upgrade-password-error", "upgrade-confirm-error", "upgrade-error"]
        .forEach(id => { document.getElementById(id).textContent = ""; });

    const credentials = checkCredentials("upgrade", { requireConfirm: true });
    if (!credentials) return;

    const submit = document.getElementById("upgrade-submit");
    submit.disabled = true;
    try {
        const auth = await upgradeGuest(credentials.username, credentials.password);
        // Mesmas tarefas, token novo: o antigo, de visitante, deixou de valer no servidor.
        setSession(auth.token, auth.guest);
        renderUserArea({ username: auth.username, guest: auth.guest });
        upgradeModal.classList.remove("show");
        showToast("Conta criada. Suas tarefas foram mantidas.");
    } catch (error) {
        console.error("Erro ao criar conta:", error);
        showRequestError(error, "upgrade");
    } finally {
        submit.disabled = false;
    }
});

document.getElementById("logout-btn").addEventListener("click", () => {
    // Visitante não tem senha: sair significa perder o acesso às tarefas. Confirma antes.
    if (currentUser?.guest) {
        logoutGuestModal.classList.add("show");
        return;
    }
    endSession();
});

document.getElementById("cancel-logout-btn").addEventListener("click", () => {
    logoutGuestModal.classList.remove("show");
});

document.getElementById("confirm-logout-btn").addEventListener("click", () => endSession());

// ---------- Início ----------

// Com token guardado: abre o app já carregando (a lista e o "quem sou eu" vão em paralelo).
// Se o token não valer mais, o primeiro 401 volta para a entrada.
function boot() {
    if (!getToken()) {
        showAuthView();
        return;
    }
    sessionEpoch++;
    // Até o servidor responder, já sabemos se é visitante (guardado no login): assim o "Sair"
    // de um visitante pede confirmação mesmo durante a partida a frio.
    showAppView({ username: "", guest: isStoredGuest() });
    const epoch = sessionEpoch;
    getMe()
        .then(me => { if (epoch === sessionEpoch) renderUserArea(me); })
        .catch(error => console.error("Erro ao identificar usuário:", error));
}

boot();
