package com.flowboard.backend.ai;

import com.flowboard.backend.model.Category;
import com.flowboard.backend.model.Priority;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Pedaços de JSON Schema reaproveitados pelas funcionalidades de IA, gerados a partir dos enums. */
final class AiSchemas {

    private AiSchemas() {
    }

    static Map<String, Object> priorityProperty() {
        return Map.of("type", "string", "enum", enumNames(Priority.values()));
    }

    static Map<String, Object> categoryProperty() {
        return Map.of("type", "string", "enum", enumNames(Category.values()));
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
