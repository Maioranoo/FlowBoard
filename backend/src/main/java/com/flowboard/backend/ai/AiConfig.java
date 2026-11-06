package com.flowboard.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executor;

/**
 * Monta os objetos da IA. Fica tudo aqui, num lugar só, para ser fácil de enxergar:
 * qual implementação é a principal, qual é o fallback, de onde vem o prompt e o timeout.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AnthropicProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    public static final String AI_EXECUTOR = "aiTaskExecutor";

    @Bean
    public RuleBasedTaskAiAssistant ruleBasedTaskAiAssistant() {
        return new RuleBasedTaskAiAssistant();
    }

    /** Cliente HTTP da Anthropic, compartilhado por todas as funcionalidades de IA. */
    @Bean
    public AnthropicClient anthropicClient(RestClient.Builder restClientBuilder,
                                           AnthropicProperties properties,
                                           ObjectMapper objectMapper) {
        // Timeout de conexão e de leitura. Sem isso, uma API lenta prenderia a thread indefinidamente.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();

        return new AnthropicClient(restClient, properties, objectMapper);
    }

    @Bean
    public AnthropicTaskAiAssistant anthropicTaskAiAssistant(
            AnthropicClient client,
            @Value("classpath:prompts/task-suggestion-system.txt") Resource systemPromptFile) throws IOException {
        return new AnthropicTaskAiAssistant(client, systemPromptFile.getContentAsString(StandardCharsets.UTF_8));
    }

    // --- Fase 2: criação de tarefa por linguagem natural ---

    @Bean
    public AnthropicTaskParser anthropicTaskParser(
            AnthropicClient client,
            @Value("classpath:prompts/task-parse-system.txt") Resource systemPromptFile) throws IOException {
        return new AnthropicTaskParser(client, systemPromptFile.getContentAsString(StandardCharsets.UTF_8));
    }

    @Bean
    public RuleBasedTaskParser ruleBasedTaskParser() {
        return new RuleBasedTaskParser();
    }

    @Bean
    @Primary
    public TaskParser taskParser(AnthropicTaskParser anthropic, RuleBasedTaskParser rules,
                                 AnthropicProperties properties) {
        return new ResilientTaskParser(anthropic, rules, properties.hasApiKey());
    }

    /**
     * Relógio no fuso do usuário. Injetar um Clock (em vez de chamar LocalDate.now() direto)
     * também permite que os testes fixem "hoje" numa data conhecida.
     */
    @Bean
    public Clock appClock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }

    @Bean
    public RateLimiter parseRateLimiter(@Value("${app.parse.rate-limit.per-client}") int perClient,
                                        @Value("${app.parse.rate-limit.global}") int global,
                                        Clock clock) {
        return new RateLimiter(perClient, global, clock);
    }

    /**
     * @Primary: quando alguém pedir um TaskAiAssistant, recebe esta versão "resiliente".
     */
    @Bean
    @Primary
    public TaskAiAssistant taskAiAssistant(AnthropicTaskAiAssistant anthropic,
                                           RuleBasedTaskAiAssistant rules,
                                           AnthropicProperties properties) {
        if (properties.hasApiKey()) {
            log.info("FlowAI: IA ativada com o modelo {} (timeout {})", properties.model(), properties.timeout());
        } else {
            log.warn("FlowAI: ANTHROPIC_API_KEY não configurada. As sugestões virão apenas das regras locais.");
        }
        return new ResilientTaskAiAssistant(anthropic, rules, properties.hasApiKey());
    }

    /**
     * Pool de threads só para gerar sugestões em segundo plano.
     * Pequeno de propósito: o plano gratuito do Render tem pouca memória, e a IA é chamada
     * só na criação/edição de tarefas. Se a fila encher, o TaskService usa as regras na hora.
     */
    @Bean(name = AI_EXECUTOR)
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("flowai-");
        // Ao desligar o servidor (ex.: novo deploy), espera as sugestões em andamento terminarem.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
