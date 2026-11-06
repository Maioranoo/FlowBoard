function showFlowAIThinking() {
    const aiMessage = document.getElementById("ai-message");

    aiMessage.innerHTML = `
        <span class="thinking-dot"></span>
        Analisando sua produtividade...
    `;
}

function updateFlowAI(tasks) {
    const aiMessage = document.getElementById("ai-message");

    showFlowAIThinking();

    setTimeout(() => {
        const total = tasks.length;
        const completed = tasks.filter(task => String(task.status).trim().toUpperCase() === "COMPLETED").length;
        const pending = total - completed;
        const percent = total === 0 ? 0 : Math.round((completed / total) * 100);

        const messages = {
            empty: [
                "Vamos começar? Crie sua primeira tarefa e organize suas prioridades.",
                "Sua área está livre. Que tal planejar o próximo objetivo?"
            ],
            completed: [
                "Parabéns! Você concluiu todas as suas metas. Excelente ritmo.",
                "Tudo em dia. Aproveite esse momento para planejar seus próximos resultados."
            ],
            high: [
                `Você concluiu ${percent}% das tarefas. Falta pouco para finalizar tudo.`,
                `Excelente progresso: ${percent}% concluído. Continue nesse ritmo.`
            ],
            medium: [
                `Você está avançando bem. Ainda existem ${pending} tarefas em andamento.`,
                `Bom progresso. Priorize as tarefas mais importantes para ganhar ritmo.`
            ],
            low: [
                "Você está criando mais tarefas do que concluindo. Comece pelas prioridades mais importantes.",
                "Sua produtividade ainda está baixa. Escolha uma tarefa simples e dê o primeiro passo."
            ]
        };

        let selectedList;

        if (total === 0) selectedList = messages.empty;
        else if (pending === 0) selectedList = messages.completed;
        else if (percent >= 70) selectedList = messages.high;
        else if (percent >= 40) selectedList = messages.medium;
        else selectedList = messages.low;

        const randomIndex = Math.floor(Math.random() * selectedList.length);
        aiMessage.textContent = selectedList[randomIndex];

    }, 800);
}