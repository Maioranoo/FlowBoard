package com.flowboard.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Quem pode acessar o quê.
 *
 * - Rotas públicas: health check, cadastro, login, criar visitante, Swagger e o preflight de CORS.
 * - Todo o resto exige "Authorization: Bearer <token>".
 *
 * STATELESS: o servidor não guarda sessão; cada requisição traz o próprio token.
 * CSRF desligado: esse ataque depende de o navegador enviar sozinho um cookie de sessão.
 * Aqui não há cookie; o token só vai no cabeçalho quando o NOSSO JavaScript o coloca lá.
 */
@Configuration
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATH = PathPatternRequestMatcher.withDefaults();

    /** Rotas abertas. Nelas o cabeçalho Authorization é ignorado (ver bearerTokenResolver). */
    static final RequestMatcher PUBLIC_ROUTES = new OrRequestMatcher(
            PATH.matcher(HttpMethod.OPTIONS, "/**"),
            PATH.matcher(HttpMethod.GET, "/health"),
            PATH.matcher(HttpMethod.POST, "/auth/register"),
            PATH.matcher(HttpMethod.POST, "/auth/login"),
            PATH.matcher(HttpMethod.POST, "/auth/guest"),
            PATH.matcher("/swagger-ui/**"),
            PATH.matcher("/swagger-ui.html"),
            PATH.matcher("/v3/api-docs/**"),
            // Para onde o Spring encaminha erros internos; sem isto, um erro viraria um 401 confuso.
            PATH.matcher("/error"));

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   JwtService jwtService,
                                                   UserTokenConverter userTokenConverter,
                                                   JsonAuthenticationEntryPoint entryPoint) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt
                                .decoder(jwtService.decoder())
                                .jwtAuthenticationConverter(userTokenConverter)))
                .build();
    }

    /**
     * Em rotas públicas o token é ignorado. Sem isto, um token velho guardado no navegador faria
     * até o LOGIN responder 401 (o filtro tentaria validar o token antes de chegar na rota).
     */
    static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> PUBLIC_ROUTES.matches(request) ? null : standard.resolve(request);
    }

    /**
     * BCrypt: hash lento de propósito e com "sal" aleatório. Mesmo com o banco vazado, descobrir
     * as senhas exige testar candidatas uma a uma, cada tentativa custando milissegundos.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
