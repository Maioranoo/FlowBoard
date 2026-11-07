package com.flowboard.backend.security;

import com.flowboard.backend.user.User;
import com.flowboard.backend.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Depois que a assinatura e a validade do token foram conferidas, transforma o token no usuário
 * da requisição, CONFERINDO NO BANCO.
 *
 * Um JWT puro seria "sem estado": o servidor confiaria no token até ele expirar. Aqui fazemos uma
 * leitura por chave primária a cada requisição porque precisamos de três coisas que o token sozinho
 * não garante:
 *  1. o usuário ainda existe (visitantes inativos são apagados);
 *  2. o token não foi invalidado (ex.: o token de visitante deixa de valer quando ele vira conta);
 *  3. registrar a última atividade, que decide quando um visitante é apagado.
 */
@Component
public class UserTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Gravar a atividade a cada requisição seria uma escrita no banco por clique. Uma por hora basta. */
    static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    private final UserRepository users;
    private final Clock clock;

    public UserTokenConverter(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = findUser(jwt.getSubject());

        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null || issuedAt.isBefore(user.getTokensValidAfter())) {
            throw new InvalidBearerTokenException("Token não vale mais");
        }

        Instant now = clock.instant();
        if (user.getLastSeenAt() == null || user.getLastSeenAt().isBefore(now.minus(TOUCH_INTERVAL))) {
            users.touch(user.getId(), now);
        }

        AuthUser principal = new AuthUser(user.getId(), user.getUsername(), user.isGuest());
        return new UsernamePasswordAuthenticationToken(principal, jwt, List.of());
    }

    private User findUser(String subject) {
        long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidBearerTokenException("Token sem usuário válido");
        }
        return users.findById(id).orElseThrow(() -> new InvalidBearerTokenException("Usuário não existe mais"));
    }
}
