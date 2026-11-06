// Resumo de produtividade.
// Antes este card se chamava "Flow AI", mostrava "Analisando..." por 800 ms (setTimeout) e
// sorteava uma frase pronta. Não havia IA nenhuma. Agora é um resumo honesto: calculado
// na hora, a partir das tarefas, sem atraso artificial e sem aleatoriedade.
// A IA de verdade fica nas sugestões de cada tarefa (geradas no back-end).

function updateProductivitySummary(tasks) {
    const message = document.getElementById("summary-message");

    const total = tasks.length;
    const completed = tasks.filter(isCompleted).length;
    const pending = total - completed;
    const percent = total === 0 ? 0 : Math.round((completed / total) * 100);
    const highPending = tasks.filter(task => !isCompleted(task) && task.priority === "HIGH").length;

    if (total === 0) {
        message.textContent = "Nenhuma tarefa ainda. Crie a primeira para começar a acompanhar seu progresso.";
    } else if (pending === 0) {
        message.textContent = `Todas as ${total} tarefas concluídas.`;
    } else {
        const plural = pending === 1 ? "tarefa pendente" : "tarefas pendentes";
        let text = `${completed} de ${total} tarefas concluídas (${percent}%). ${pending} ${plural}`;
        text += highPending > 0 ? `, ${highPending} com prioridade alta.` : ".";
        message.textContent = text;
    }
}
