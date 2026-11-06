package com.flowboard.backend.ai;

/**
 * Contrato do "assistente de tarefas". O resto do sistema só conhece esta interface,
 * não sabe se por trás existe um LLM, regras locais ou as duas coisas combinadas.
 *
 * Implementações:
 *  - AnthropicTaskAiAssistant: chama a API de Mensagens da Anthropic.
 *  - RuleBasedTaskAiAssistant: regras por palavra-chave (o antigo FlowAiService), usado como fallback.
 *  - ResilientTaskAiAssistant: tenta a Anthropic e cai para as regras se algo der errado.
 */
public interface TaskAiAssistant {

    /**
     * @param title       título da tarefa
     * @param description descrição (pode ser nula)
     * @param priority    prioridade escolhida pelo usuário (pode ser nula)
     * @return sugestão já validada
     * @throws InvalidAiResponseException se a resposta do modelo não puder ser usada
     */
    TaskAiSuggestion suggest(String title, String description, String priority);
}
