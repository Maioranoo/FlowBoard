package com.flowboard.backend.auth;

import com.flowboard.backend.auth.dto.AuthResponse;
import com.flowboard.backend.auth.dto.CredentialsRequest;
import com.flowboard.backend.security.AuthUser;
import com.flowboard.backend.security.JwtService;
import com.flowboard.backend.service.TaskService;
import com.flowboard.backend.user.User;
import com.flowboard.backend.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

/**
 * Cadastro, login, visitante e "visitante vira conta".
 *
 * Os nomes são normalizados (sem espaços nas pontas, minúsculas) antes de qualquer uso:
 * "Ana", " ana " e "ANA" são o mesmo usuário.
 */
@Service
public class AuthService {

    /** Ninguém pode se cadastrar com estes nomes (evita se passar pelo sistema ou por visitantes). */
    static final Set<String> RESERVED_USERNAMES = Set.of("visitante", "admin");

    private static final String GUEST_PREFIX = "visitante-";
    private static final String GUEST_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int GUEST_SUFFIX_LENGTH = 5;
    private static final int GUEST_NAME_ATTEMPTS = 5;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TaskService taskService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Hash de uma senha qualquer, calculado uma vez. No login de um usuário que não existe, a senha
     * digitada é comparada com ele mesmo assim: sem isso a resposta viria bem mais rápido para nomes
     * inexistentes, e medir o tempo de resposta revelaria quais usuários existem.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       TaskService taskService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.taskService = taskService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("senha-que-nao-pertence-a-ninguem");
    }

    public AuthResponse register(CredentialsRequest request) {
        String username = normalize(request.username());
        ensureAvailable(username);

        Instant now = now();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setGuest(false);
        user.setCreatedAt(now);
        user.setLastSeenAt(now);
        user.setTokensValidAfter(now);
        saveCheckingUniqueness(user);

        return response(user, jwtService.issue(user, now));
    }

    public AuthResponse login(CredentialsRequest request) {
        User user = users.findByUsername(normalize(request.username())).orElse(null);

        // Visitantes não têm senha, então nunca entram por aqui.
        String hash = user != null && !user.isGuest() && user.getPasswordHash() != null
                ? user.getPasswordHash()
                : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user == null || hash == dummyHash || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return response(user, jwtService.issue(user));
    }

    /**
     * Cria um visitante com as tarefas de exemplo.
     *
     * Sem @Transactional de propósito: cada tarefa precisa estar gravada (commit) antes de a
     * sugestão ser gerada em outra thread, que vai procurá-la no banco.
     *
     * @param useAi false quando o limite de visitantes com IA foi atingido: as sugestões das
     *              tarefas de exemplo vêm das regras locais e nenhum crédito é gasto.
     */
    public AuthResponse createGuest(boolean useAi) {
        Instant now = now();
        User guest = new User();
        guest.setGuest(true);
        guest.setCreatedAt(now);
        guest.setLastSeenAt(now);
        guest.setTokensValidAfter(now);
        saveGuestWithUniqueName(guest);

        LocalDate today = LocalDate.now(clock);
        for (GuestSeedTasks.Seed seed : GuestSeedTasks.forToday(today)) {
            taskService.createSeedTask(guest, seed.request(), seed.completed(), useAi);
        }
        return response(guest, jwtService.issue(guest, now));
    }

    /**
     * Visitante vira conta: a MESMA linha de usuário ganha nome e senha, então as tarefas continuam
     * dele sem precisar copiar nada. O status de visitante é lido do banco, nunca do token.
     */
    public AuthResponse upgrade(AuthUser current, CredentialsRequest request) {
        User user = users.findById(current.id()).orElseThrow(InvalidCredentialsException::new);
        if (!user.isGuest()) {
            throw new NotAGuestException();
        }
        String username = normalize(request.username());
        ensureAvailable(username);

        // O token antigo de visitante não expira. Para ele não virar uma chave permanente da conta,
        // todo token emitido até agora deixa de valer: tokens_valid_after avança 1 segundo além de
        // "agora" (o "emitido em" do JWT é em segundos inteiros) e o token novo nasce nesse instante.
        Instant validAfter = now().plusSeconds(1);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setGuest(false);
        user.setTokensValidAfter(validAfter);
        saveCheckingUniqueness(user);

        return response(user, jwtService.issue(user, validAfter));
    }

    static String normalize(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private void ensureAvailable(String username) {
        if (RESERVED_USERNAMES.contains(username) || users.existsByUsername(username)) {
            throw new UsernameTakenException();
        }
    }

    /** Duas pessoas cadastrando o mesmo nome ao mesmo tempo: quem chegar depois recebe "em uso". */
    private void saveCheckingUniqueness(User user) {
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameTakenException();
        }
    }

    private void saveGuestWithUniqueName(User guest) {
        for (int attempt = 1; ; attempt++) {
            String candidate = GUEST_PREFIX + randomSuffix();
            if (users.existsByUsername(candidate)) {
                if (attempt >= GUEST_NAME_ATTEMPTS) {
                    throw new IllegalStateException("Não foi possível gerar um nome de visitante livre");
                }
                continue;
            }
            guest.setUsername(candidate);
            users.saveAndFlush(guest);
            return;
        }
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(GUEST_SUFFIX_LENGTH);
        for (int i = 0; i < GUEST_SUFFIX_LENGTH; i++) {
            suffix.append(GUEST_ALPHABET.charAt(random.nextInt(GUEST_ALPHABET.length())));
        }
        return suffix.toString();
    }

    /** Em segundos inteiros, a mesma precisão do "emitido em" (iat) do JWT. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static AuthResponse response(User user, String token) {
        return new AuthResponse(token, user.getUsername(), user.isGuest());
    }
}
