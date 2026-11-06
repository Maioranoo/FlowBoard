package com.flowboard.backend.model;

/**
 * Conjunto fechado de categorias. É a fonte única da verdade: o JSON Schema
 * enviado ao modelo é gerado a partir deste enum, então a IA nunca pode
 * sugerir uma categoria que o sistema não conhece.
 *
 * WORK, STUDY, PERSONAL e HEALTH já existem no banco; HOME e FINANCE são novas.
 */
public enum Category {
    WORK,
    STUDY,
    PERSONAL,
    HEALTH,
    HOME,
    FINANCE
}
