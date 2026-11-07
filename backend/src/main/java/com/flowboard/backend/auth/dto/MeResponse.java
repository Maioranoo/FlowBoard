package com.flowboard.backend.auth.dto;

/** "Quem sou eu": usado pelo front ao abrir o site, para saber se o token guardado ainda vale. */
public record MeResponse(String username, boolean guest) {
}
