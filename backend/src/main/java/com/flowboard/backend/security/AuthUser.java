package com.flowboard.backend.security;

/**
 * Quem está fazendo a requisição, já conferido no banco. Os controllers recebem isto com
 * {@code @AuthenticationPrincipal AuthUser user} e repassam {@code user.id()} aos serviços.
 */
public record AuthUser(Long id, String username, boolean guest) {
}
