package com.flowboard.backend.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.backend.ai.AnthropicClient;
import com.flowboard.backend.ai.AnthropicProperties;
import com.flowboard.backend.ai.AnthropicTaskParser;
import com.flowboard.backend.ai.RuleBasedTaskParser;
import com.flowboard.backend.ai.TaskDraft;
import com.flowboard.backend.ai.TaskParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AVALIAÇÃO da IA (não é um teste unitário comum): chama a API de verdade com as frases de
 * src/test/resources/eval/parse-cases.json e mede a taxa de acerto POR CAMPO, comparando com
 * o fallback por regras (o "baseline").
 *
 * Por que separado dos outros testes:
 *  - custa dinheiro (centavos) e depende de rede, então nunca roda no CI nem no "mvnw test" normal;
 *  - o resultado é uma MEDIDA, não um passa/falha. Ele não reprova o build por acurácia baixa.
 *
 * Como rodar (precisa da variável ANTHROPIC_API_KEY na sua máquina):
 *   ./mvnw test -Peval
 * O relatório sai no console e em target/eval/parse-report.md.
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ParseEvaluationTest {

    private static final List<String> FIELDS = List.of("category", "priority", "dueDate", "title");

    @Test
    void measureAccuracyPerField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode suite = mapper.readTree(new ClassPathResource("eval/parse-cases.json").getInputStream());
        LocalDate today = LocalDate.parse(suite.path("today").asText());

        TaskParser ai = buildRealAiParser(mapper);
        TaskParser rules = new RuleBasedTaskParser();

        Score aiScore = new Score();
        Score rulesScore = new Score();
        List<String> aiMistakes = new ArrayList<>();
        List<Long> latenciesMs = new ArrayList<>();

        for (JsonNode testCase : suite.path("cases")) {
            String id = testCase.path("id").asText();
            String text = testCase.path("text").asText();
            JsonNode expected = testCase.path("expected");

            long start = System.nanoTime();
            TaskDraft aiDraft;
            try {
                aiDraft = ai.parse(text, today);
            } catch (RuntimeException e) {
                aiDraft = null; // resposta inválida ou erro: conta como erro em todos os campos avaliados
                aiMistakes.add("- `" + id + "`: falhou (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            }
            latenciesMs.add((System.nanoTime() - start) / 1_000_000);

            aiScore.validResponses += aiDraft == null ? 0 : 1;
            aiScore.cases++;
            evaluate(expected, aiDraft, aiScore, id, aiMistakes);
            evaluate(expected, rules.parse(text, today), rulesScore, id, null);
        }

        String report = buildReport(aiScore, rulesScore, aiMistakes, latenciesMs);
        System.out.println(report);
        Path out = Path.of("target", "eval", "parse-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);

        // Única verificação: a avaliação rodou de fato. A acurácia é informação, não critério de build.
        assertThat(aiScore.cases).isPositive();
    }

    /** Compara cada campo esperado com o obtido. Campos ausentes em "expected" não são avaliados. */
    private static void evaluate(JsonNode expected, TaskDraft draft, Score score, String id, List<String> mistakes) {
        for (String field : FIELDS) {
            Boolean correct = switch (field) {
                case "category" -> expected.has("category")
                        ? draft != null && draft.category().name().equals(expected.get("category").asText()) : null;
                case "priority" -> expected.has("priority")
                        ? draft != null && draft.priority().name().equals(expected.get("priority").asText()) : null;
                case "dueDate" -> expected.has("dueDate")
                        ? draft != null && Objects.equals(
                                draft.dueDate() == null ? "" : draft.dueDate().toString(),
                                expected.get("dueDate").asText()) : null;
                case "title" -> expected.has("titleKeywords") || expected.has("titleForbidden")
                        ? draft != null && titleOk(draft.title(), expected) : null;
                default -> null;
            };
            if (correct == null) {
                continue;
            }
            score.total.merge(field, 1, Integer::sum);
            if (correct) {
                score.hits.merge(field, 1, Integer::sum);
            } else if (mistakes != null && draft != null) {
                mistakes.add("- `" + id + "` · **" + field + "**: esperado `" + describeExpected(field, expected)
                        + "`, veio `" + describeActual(field, draft) + "`");
            }
        }
    }

    private static boolean titleOk(String title, JsonNode expected) {
        String lower = title.toLowerCase(Locale.ROOT);
        boolean hasKeyword = !expected.has("titleKeywords");
        for (JsonNode k : expected.path("titleKeywords")) {
            hasKeyword |= lower.contains(k.asText().toLowerCase(Locale.ROOT));
        }
        for (JsonNode f : expected.path("titleForbidden")) {
            if (lower.contains(f.asText().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return hasKeyword;
    }

    private static String describeExpected(String field, JsonNode expected) {
        return switch (field) {
            case "title" -> "contém " + expected.path("titleKeywords") + ", sem " + expected.path("titleForbidden");
            default -> expected.path(field).asText().isEmpty() ? "(sem prazo)" : expected.path(field).asText();
        };
    }

    private static String describeActual(String field, TaskDraft draft) {
        return switch (field) {
            case "category" -> draft.category().name();
            case "priority" -> draft.priority().name();
            case "dueDate" -> draft.dueDate() == null ? "(sem prazo)" : draft.dueDate().toString();
            default -> draft.title();
        };
    }

    private static String buildReport(Score ai, Score rules, List<String> mistakes, List<Long> latencies) {
        List<Long> sorted = latencies.stream().sorted().toList();
        StringBuilder sb = new StringBuilder();
        sb.append("# Avaliação do parse (POST /tasks/parse)\n\n");
        sb.append("Modelo: `").append(System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-haiku-5-5"))
                .append("` · casos: ").append(ai.cases)
                .append(" · respostas válidas da IA: ").append(ai.validResponses).append('/').append(ai.cases).append("\n\n");
        sb.append("| Campo | IA | Regras (baseline) |\n|---|---|---|\n");
        for (String field : FIELDS) {
            sb.append("| ").append(field).append(" | ").append(ai.format(field))
                    .append(" | ").append(rules.format(field)).append(" |\n");
        }
        sb.append("\nLatência da IA: mediana ").append(sorted.get(sorted.size() / 2)).append(" ms, máxima ")
                .append(sorted.get(sorted.size() - 1)).append(" ms\n");
        sb.append("\n## Erros da IA\n\n").append(mistakes.isEmpty() ? "Nenhum.\n" : String.join("\n", mistakes) + "\n");
        return sb.toString();
    }

    /** Monta o parser com a API REAL. Mesmo código de produção, só a configuração vem do ambiente. */
    private static TaskParser buildRealAiParser(ObjectMapper mapper) throws Exception {
        AnthropicProperties properties = new AnthropicProperties(
                System.getenv("ANTHROPIC_API_KEY"),
                System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-haiku-5-5"),
                "https://api.anthropic.com",
                Duration.ofSeconds(30),
                1024);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
        String prompt = new ClassPathResource("prompts/task-parse-system.txt").getContentAsString(StandardCharsets.UTF_8);
        return new AnthropicTaskParser(new AnthropicClient(restClient, properties, mapper), prompt);
    }

    private static final class Score {
        private final Map<String, Integer> hits = new LinkedHashMap<>();
        private final Map<String, Integer> total = new LinkedHashMap<>();
        private int cases;
        private int validResponses;

        private String format(String field) {
            int t = total.getOrDefault(field, 0);
            int h = hits.getOrDefault(field, 0);
            return t == 0 ? "-" : "%d/%d (%.0f%%)".formatted(h, t, 100.0 * h / t);
        }
    }
}
