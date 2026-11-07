package com.flowboard.backend.auth;

import com.flowboard.backend.auth.dto.AuthResponse;
import com.flowboard.backend.auth.dto.CredentialsRequest;
import com.flowboard.backend.auth.dto.MeResponse;
import com.flowboard.backend.ratelimit.RateLimiter;
import com.flowboard.backend.security.AuthUser;
import com.flowboard.backend.web.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Conta", description = "Cadastro, login e modo visitante")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter authRateLimiter;
    private final RateLimiter guestAiRateLimiter;
    private final RateLimiter guestAbuseRateLimiter;

    public AuthController(AuthService authService,
                          @Qualifier("authRateLimiter") RateLimiter authRateLimiter,
                          @Qualifier("guestAiRateLimiter") RateLimiter guestAiRateLimiter,
                          @Qualifier("guestAbuseRateLimiter") RateLimiter guestAbuseRateLimiter) {
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
        this.guestAiRateLimiter = guestAiRateLimiter;
        this.guestAbuseRateLimiter = guestAbuseRateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma conta com usuário e senha", description = "409 se o nome já estiver em uso.")
    public AuthResponse register(@Valid @RequestBody CredentialsRequest request, HttpServletRequest http) {
        authRateLimiter.acquire(ClientIp.of(http));
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Entra com usuário e senha", description = "401 com a mesma mensagem para usuário inexistente e senha errada.")
    public AuthResponse login(@Valid @RequestBody CredentialsRequest request, HttpServletRequest http) {
        authRateLimiter.acquire(ClientIp.of(http));
        return authService.login(request);
    }

    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um visitante com tarefas de exemplo (sem cadastro)")
    public AuthResponse guest(HttpServletRequest http) {
        String ip = ClientIp.of(http);
        guestAbuseRateLimiter.acquire(ip);
        // Passou do limite de visitantes com IA? Cria mesmo assim, só que sem gastar créditos.
        boolean useAi = guestAiRateLimiter.tryAcquire(ip);
        return authService.createGuest(useAi);
    }

    @PostMapping("/upgrade")
    @Operation(summary = "Transforma o visitante logado numa conta, mantendo as tarefas")
    public AuthResponse upgrade(@AuthenticationPrincipal AuthUser user,
                                @Valid @RequestBody CredentialsRequest request, HttpServletRequest http) {
        authRateLimiter.acquire(ClientIp.of(http));
        return authService.upgrade(user, request);
    }

    @GetMapping("/me")
    @Operation(summary = "Quem está logado")
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return new MeResponse(user.username(), user.guest());
    }
}
