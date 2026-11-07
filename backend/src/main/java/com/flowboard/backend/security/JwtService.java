package com.flowboard.backend.security;

import com.flowboard.backend.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Emite e confere os tokens de login (JWT assinados com HMAC-SHA256).
 *
 * Um JWT é um texto com três partes: cabeçalho, dados ("claims") e assinatura. Os dados NÃO são
 * secretos (qualquer um decodifica), mas a assinatura só pode ser gerada por quem tem a chave.
 * Por isso o servidor confia no "sub" (id do usuário) de um token cuja assinatura confere.
 *
 * Claims usados:
 *  - sub: id do usuário (não o nome, que muda quando um visitante vira conta);
 *  - iat: quando foi emitido (comparado com users.tokens_valid_after);
 *  - exp: validade, 7 dias. Visitantes não têm exp: o token vale enquanto a conta existir.
 */
@Component
public class JwtService {

    static final Duration ACCOUNT_TOKEN_VALIDITY = Duration.ofDays(7);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String secret, Clock clock) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        // HMAC-SHA256 com chave curta é fácil de quebrar por força bruta. Melhor nem subir.
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET ausente ou com menos de " + MIN_SECRET_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.clock = clock;
    }

    /** Token emitido agora. */
    public String issue(User user) {
        return issue(user, clock.instant());
    }

    /**
     * Token com um instante de emissão escolhido. O "iat" de um JWT é em segundos inteiros,
     * por isso o instante é truncado para segundos.
     */
    public String issue(User user, Instant issuedAt) {
        Instant iat = issuedAt.truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(iat);
        if (!user.isGuest()) {
            claims.expiresAt(iat.plus(ACCOUNT_TOKEN_VALIDITY));
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /** Confere assinatura (só HS256) e validade. Quem existe e se o token ainda vale: UserTokenConverter. */
    public JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
