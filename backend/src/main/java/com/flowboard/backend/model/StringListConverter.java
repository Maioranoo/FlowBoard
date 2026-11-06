package com.flowboard.backend.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * Guarda uma List<String> numa única coluna de texto, serializada como JSON
 * (ex.: ["Revisar slides","Ensaiar apresentação"]).
 *
 * Alternativa seria @ElementCollection (uma tabela extra task_subtasks), mas isso
 * gera uma consulta a mais por tarefa ao listar. Como as subtarefas são só
 * exibidas e nunca consultadas individualmente, uma coluna basta.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar a lista", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Valor inválido na coluna de lista: " + json, e);
        }
    }
}
