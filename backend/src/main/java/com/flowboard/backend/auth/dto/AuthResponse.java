package com.flowboard.backend.auth.dto;

/** Resposta de cadastro, login, visitante e upgrade: o token e quem é o usuário. */
public record AuthResponse(String token, String username, boolean guest) {
}
