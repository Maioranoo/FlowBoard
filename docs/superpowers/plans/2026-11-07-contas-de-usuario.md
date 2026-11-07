# Contas de usuário e modo visitante: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada pessoa vê só as próprias tarefas. Isso vale tanto para quem tem conta (usuário e senha) quanto para quem entra com um clique como visitante isolado.

**Architecture:**
- Spring Security como resource server de JWT HS256, sem sessão.
- Cada requisição carrega o usuário do banco a partir do `sub` (id). Esse passo existe para:
  - recusar tokens de usuários apagados;
  - recusar tokens emitidos antes de `tokens_valid_after`;
  - atualizar a última atividade.
- As tarefas ganham dono (`user_id`). Todas as consultas filtram por ele.
- O front ganha uma tela de entrada (modelo B) e guarda o token no `localStorage`.

**Tech Stack:**
- Java 17, Spring Boot 3.5.
- `spring-boot-starter-security` e `spring-boot-starter-oauth2-resource-server` (Nimbus).
- BCrypt, Flyway, Testcontainers.
- HTML, CSS e JS puros.

**Spec:** `docs/superpowers/specs/2026-11-07-contas-de-usuario-design.md`

## Global Constraints

- **Segredos:** nenhum no código. A chave vem de `app.jwt.secret=${JWT_SECRET:}`, e a aplicação **não sobe** se ela estiver vazia ou tiver menos de 32 bytes. Os testes usam uma chave fixa de teste em `src/test/resources/config/application.properties`.
- **Git:** nada de `git add`, `git mv`, `git rm` nem `git commit`. O usuário faz um único commit e um único push no fim, pelo VS Code. Os passos de "commit" deste plano são substituídos por "rodar os testes e seguir".
- **Contrato atual:** o formato de erro (`timestamp`, `status`, `error`, `message`, `fields`) e o JSON de `TaskResponse` não mudam.
- **Comentários:** em português, didáticos, explicando o porquê, como no resto do código.
- **Regras de nome e senha:**
  - usuário `^[A-Za-z0-9._]{3,20}$`, normalizado com `trim().toLowerCase(Locale.ROOT)`;
  - nomes reservados: `visitante`, `admin`;
  - senha de 8 a 72 **bytes** em UTF-8.
- **Validade dos tokens:**
  - conta: 7 dias;
  - visitante: sem `exp`.
- **Visitante:**
  - nome `visitante-` seguido de 5 caracteres `[a-z0-9]` aleatórios (`SecureRandom`);
  - apagado após 7 dias sem atividade;
  - última atividade gravada no máximo uma vez por hora.
- **Limites:**
  - login e cadastro: 10 por minuto por IP e 300 por minuto no total;
  - visitante com IA: 20 por hora por IP e 100 por hora no total. Acima disso, o visitante é criado e as tarefas de exemplo usam as regras locais;
  - visitante contra abuso: 60 por hora por IP e 1.000 por hora no total, com resposta 429;
  - orçamento de IA: 30 sugestões do modelo por usuário por dia, no fuso `app.timezone`.
- **Mensagens:**
  - login com erro: `Usuário ou senha incorretos`;
  - nome em uso: `Este nome de usuário já está em uso`;
  - sessão expirada (front): `Sua sessão expirou. Entre novamente.`
- **Visual:** tokens do `:root` de `frontend/css/style.css`, IBM Plex Sans, nenhuma cor nova. A tela de entrada segue o modelo B da spec.
- **API publicada:** `https://flowboard-backend-qnut.onrender.com`. Rotas `/auth/*` e `/tasks/*`.

## Review Focus

1. **Nome com maiúsculas ou espaços** (`" Ana "` no login de quem se cadastrou como `ana`) tem que entrar normalmente. O teste fica na Task 3.
2. **Token velho no `localStorage` ao chamar `/auth/login`, `/auth/register` ou `/auth/guest`:** o resource server responderia 401 mesmo em rota pública. O front **nunca** envia `Authorization` para essas rotas, e o back ignora o cabeçalho nelas (um `BearerTokenResolver` que devolve `null` para os caminhos públicos). O teste fica na Task 2.
3. **Clique duplo em "Testar sem cadastro" durante a partida a frio (~50 s)** não pode criar dois visitantes. O botão fica desabilitado, mostra "Preparando seu espaço…" e, depois de 4 s, a mesma dica de servidor dormindo. A verificação fica na Task 7, com Playwright.
4. **Senha com acentos acima de 72 bytes** (40 × `ç` = 80 bytes) responde 400 com `fields.password`, em vez de o BCrypt cortar a senha em silêncio. O teste fica na Task 3.
5. **Token expirado com o app aberto e uma sugestão sendo consultada:** o primeiro 401 limpa a sessão, para todas as consultas de sugestão e volta para a entrada com o aviso. Não pode haver um 401 a cada 1,5 s. A verificação fica na Task 7.

---

### Task 1: Banco: usuários, dono da tarefa e orçamento de IA

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__users_and_task_owner.sql`
- Create: `backend/src/main/java/com/flowboard/backend/user/User.java`
- Create: `backend/src/main/java/com/flowboard/backend/user/UserRepository.java`
- Modify: `backend/src/main/java/com/flowboard/backend/model/Task.java` (campo `owner`)
- Modify: `backend/src/main/java/com/flowboard/backend/repository/TaskRepository.java`
- Test: `backend/src/test/java/com/flowboard/backend/user/UserPersistenceTest.java`
- Modify: `backend/src/test/java/com/flowboard/backend/repository/TaskPersistenceTest.java` (os inserts passam a ter `user_id`)

**Interfaces:**
- Produces:
  - `User` (tabela `users`), com Lombok `@Getter @Setter` e os campos:
    - `Long id`
    - `String username`
    - `String passwordHash` (anulável)
    - `boolean guest`
    - `Instant createdAt`
    - `Instant lastSeenAt`
    - `Instant tokensValidAfter`
    - `LocalDate aiSuggestionsDay`
    - `int aiSuggestionsCount`
  - Em `Task`, o campo `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "user_id") User owner`.
  - `UserRepository extends JpaRepository<User, Long>`:
    - `Optional<User> findByUsername(String normalized)`
    - `boolean existsByUsername(String normalized)`
    - `@Modifying int touch(Long id, Instant now)`: atualiza `lastSeenAt`.
    - `@Modifying int deleteInactiveGuests(Instant cutoff)`: JPQL `delete from User u where u.guest = true and u.lastSeenAt < :cutoff`. As tarefas somem pelo `ON DELETE CASCADE` do banco.
    - `@Modifying int consumeAiSuggestion(Long id, LocalDate today, int limit)`: SQL nativo, abaixo.
  - `TaskRepository`:
    - `List<Task> findAllByOwnerIdOrderByIdAsc(Long ownerId)`
    - `Optional<Task> findByIdAndOwnerId(Long id, Long ownerId)`

`consumeAiSuggestion` é **atômico**. Duas requisições simultâneas não podem passar do limite. Devolve 1 se a sugestão foi permitida e 0 se não:

```sql
UPDATE users
   SET ai_suggestions_count = CASE WHEN ai_suggestions_day = :today THEN ai_suggestions_count + 1 ELSE 1 END,
       ai_suggestions_day   = :today
 WHERE id = :id
   AND (ai_suggestions_day IS DISTINCT FROM :today OR ai_suggestions_count < :limit)
```

A migração V3, nesta ordem:
1. `CREATE TABLE users`:
   - `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
   - `username VARCHAR(20) NOT NULL UNIQUE`
   - `password_hash VARCHAR(100)`
   - `guest BOOLEAN NOT NULL`
   - `created_at`, `last_seen_at` e `tokens_valid_after`: `TIMESTAMP(6) WITH TIME ZONE NOT NULL`
   - `ai_suggestions_day DATE`
   - `ai_suggestions_count INTEGER NOT NULL DEFAULT 0`
2. `DELETE FROM tasks`, com um comentário explicando que as tarefas sem dono são descartadas por decisão do usuário.
3. `ALTER TABLE tasks ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`
4. `CREATE INDEX idx_tasks_user_id ON tasks(user_id)`

- [ ] **Step 1: Escrever `UserPersistenceTest`** (`@DataJpaTest`, Testcontainers, mesmo padrão do `TaskPersistenceTest`):
  - `usernameIsUnique`: salvar dois `User` com `ana` → `DataIntegrityViolationException` no `saveAndFlush`.
  - `deletingGuestCascadesToTasks`: visitante com `lastSeenAt` de 8 dias atrás, uma tarefa dele e uma conta normal com uma tarefa → `deleteInactiveGuests(now - 7 dias)` devolve `1`, `select count(*) from tasks` dá `1`, e a conta normal continua existindo.
  - `deleteInactiveGuestsKeepsRecentGuests`: visitante visto há 6 dias → devolve `0`.
  - `consumeAiSuggestionStopsAtLimit`: com limit 3, chamar 4 vezes no mesmo dia dá `1, 1, 1, 0`; no dia seguinte volta a dar `1`.
  - `findAllByOwnerReturnsOnlyOwnTasks`: duas contas com uma tarefa cada → cada uma vê só a sua.
- [ ] **Step 2: Rodar e ver falhar.** `cd backend; ./mvnw test -Dtest=UserPersistenceTest`. Falha de compilação: `User` não existe.
- [ ] **Step 3: Implementar a migração V3, `User`, `UserRepository`, o campo `owner` e as consultas do `TaskRepository`.**
- [ ] **Step 4: Ajustar `TaskPersistenceTest`.** Os dois testes passam a criar um usuário antes, e o insert com SQL cru inclui `user_id`.
- [ ] **Step 5: Rodar.** `./mvnw test -Dtest=UserPersistenceTest,TaskPersistenceTest` → PASS. O Docker precisa estar rodando.

---

### Task 2: Segurança: token JWT, filtro, CORS e 401

**Files:**
- Modify: `backend/pom.xml`: adicionar `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` e `spring-security-test` (escopo test).
- Modify: `backend/src/main/resources/application.properties`: `app.jwt.secret=${JWT_SECRET:}`, com comentário dizendo que o valor é obrigatório e vem do Render.
- Create: `backend/src/test/resources/config/application.properties`: `app.jwt.secret=` com uma chave fixa de teste de 40+ caracteres, e um comentário explicando que ela só vale nos testes.
- Create: `backend/src/main/java/com/flowboard/backend/security/AuthUser.java`
- Create: `backend/src/main/java/com/flowboard/backend/security/JwtService.java`
- Create: `backend/src/main/java/com/flowboard/backend/security/UserTokenConverter.java`
- Create: `backend/src/main/java/com/flowboard/backend/security/SecurityConfig.java`
- Modify: `backend/src/main/java/com/flowboard/backend/config/CorsConfig.java`: passa a expor um bean `CorsConfigurationSource`, que a Security usa. Cabeçalhos permitidos: `Content-Type` e `Authorization`. O `maxAge` 3600 continua.
- Create: `backend/src/main/java/com/flowboard/backend/web/ClientIp.java`: o `clientKey` sai do `TaskController` para cá.
- Modify: `backend/src/main/java/com/flowboard/backend/config/OpenApiConfig.java`: esquema de segurança `bearerAuth` (HTTP bearer, JWT) aplicado globalmente, para o botão "Authorize" do Swagger.
- Test: `backend/src/test/java/com/flowboard/backend/security/JwtServiceTest.java`
- Modify: `backend/src/test/java/com/flowboard/backend/config/CorsConfigTest.java`

**Interfaces:**
- Produces:
  - `record AuthUser(Long id, String username, boolean guest)`: o *principal*. Os controllers o recebem com `@AuthenticationPrincipal AuthUser user`.
  - `JwtService`:
    - `String issue(User user)`: `sub` = id em texto, `iat` = agora, `exp` = agora + 7 dias **só se não for visitante**;
    - `JwtDecoder decoder()`: Nimbus HS256;
    - construtor `JwtService(@Value("${app.jwt.secret}") String secret, Clock clock)`, que lança `IllegalStateException("JWT_SECRET ausente ou com menos de 32 bytes")` se a chave for inválida.
  - `UserTokenConverter implements Converter<Jwt, AbstractAuthenticationToken>`:
    - carrega o `User` pelo `sub`;
    - lança `InvalidBearerTokenException` se o usuário não existe ou se `iat < tokensValidAfter` (comparação em segundos inteiros);
    - chama `touch` se `lastSeenAt` tem mais de 1 hora;
    - devolve `UsernamePasswordAuthenticationToken(authUser, null, List.of())`.
  - `ClientIp.of(HttpServletRequest)`: primeiro IP do `X-Forwarded-For`; senão, `getRemoteAddr()`.
  - **`GET /health`:** pública, responde `200` com `{"status":"ok"}` e **não toca no banco**. Fica num `HealthController` em `web/`. É o alvo do "despertador" (UptimeRobot, a cada 10 min), que impede o Render de dormir; como não consulta o banco, o Neon continua podendo dormir.
  - **Rotas públicas:**
    - `GET /health`;
    - `POST /auth/register`, `/auth/login` e `/auth/guest`;
    - `OPTIONS /**`;
    - `/swagger-ui/**`, `/swagger-ui.html` e `/v3/api-docs/**`;
    - todas as outras exigem autenticação.
  - **Formato do 401:** o mesmo dos outros erros, com `error` = `Não autenticado` e `message` = `Faça login para continuar.`. É escrito por um `AuthenticationEntryPoint` próprio, registrado tanto em `http.exceptionHandling` quanto em `oauth2ResourceServer`.
  - **Rotas públicas ignoram o `Authorization`:** um `BearerTokenResolver` próprio devolve `null` para elas e delega ao `DefaultBearerTokenResolver` nas demais.
  - **`SecurityConfig`:** `SessionCreationPolicy.STATELESS`, CSRF desligado (com um comentário explicando que não há cookie de sessão) e um bean `PasswordEncoder` (BCrypt).

- [ ] **Step 1: Escrever `JwtServiceTest`** (unitário, com `Clock` fixo):
  - `accountTokenExpiresInSevenDays`: `exp - iat` = 7 dias.
  - `guestTokenHasNoExpiry`: `exp` nulo.
  - `rejectsShortSecret`: chave de 10 caracteres → `IllegalStateException`.
  - `decoderRejectsTokenSignedWithAnotherSecret`: dá `JwtException`.

  E em `backend/src/test/java/com/flowboard/backend/web/HealthControllerTest.java` (`@WebMvcTest(HealthController.class)` com a Security importada):
  - `healthIsPublicAndDoesNotNeedToken`: `GET /health` sem token → 200 com `status` = `ok`.
- [ ] **Step 2: Ajustar `CorsConfigTest`** para o preflight passar pela Security:
  - origem da Vercel com `Access-Control-Request-Headers: Content-Type, Authorization` → 200, com `Access-Control-Allow-Headers` contendo `Authorization` e `Max-Age` 3600;
  - outra origem → 403.

  O teste importa `SecurityConfig`, `CorsConfig` e `JwtService` e usa `@MockitoBean UserRepository`.
- [ ] **Step 3: Rodar e ver falhar.** `./mvnw test -Dtest=JwtServiceTest,CorsConfigTest`
- [ ] **Step 4: Implementar os arquivos listados.**
- [ ] **Step 5: Rodar.** `./mvnw test -Dtest=JwtServiceTest,CorsConfigTest` → PASS. Os testes de `/tasks` ainda vão falhar com 401 e são ajustados na Task 4.

---

### Task 3: Rotas de conta: cadastro, login, visitante, upgrade e "quem sou eu"

**Files:**
- Create: `backend/src/main/java/com/flowboard/backend/auth/AuthController.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/AuthService.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/GuestSeedTasks.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/dto/CredentialsRequest.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/dto/MeResponse.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/UsernameTakenException.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/InvalidCredentialsException.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/NotAGuestException.java`
- Create: `backend/src/main/java/com/flowboard/backend/auth/MaxUtf8Bytes.java` (anotação de validação e o validador)
- Modify: `backend/src/main/java/com/flowboard/backend/ratelimit/RateLimiter.java`
- Modify: `backend/src/main/java/com/flowboard/backend/ai/AiConfig.java`: o `parseRateLimiter` passa a receber `Duration.ofMinutes(1)`, e são criados os beans `authRateLimiter`, `guestAiRateLimiter` e `guestAbuseRateLimiter`, com os valores de Global Constraints.
- Modify: `backend/src/main/java/com/flowboard/backend/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/flowboard/backend/auth/AuthFlowIntegrationTest.java`
- Modify: `backend/src/test/java/com/flowboard/backend/ratelimit/RateLimiterTest.java`

**Interfaces:**
- Consumes:
  - `UserRepository`, `JwtService`, `PasswordEncoder` e `ClientIp`;
  - `TaskService.createSeedTask(User owner, TaskRequest request, boolean completed, boolean useAi)`, da Task 4. Faça a Task 4 antes, ou crie o método junto.
- Produces:
  - `record CredentialsRequest(String username, String password)`:
    - `username`: `@NotBlank`, `@Pattern(regexp = "^\\s*[A-Za-z0-9._]{3,20}\\s*$", message = "Use de 3 a 20 letras, números, ponto ou _")`;
    - `password`: `@NotBlank`, `@Size(min = 8, message = "A senha precisa ter pelo menos 8 caracteres")` e `@MaxUtf8Bytes(72)`, com a mensagem "Senha longa demais".
  - `record AuthResponse(String token, String username, boolean guest)` e `record MeResponse(String username, boolean guest)`.
  - **Rotas:**

    | Rota | Status | Resposta |
    |---|---|---|
    | `POST /auth/register` | 201 | `AuthResponse` |
    | `POST /auth/login` | 200 | `AuthResponse` |
    | `POST /auth/guest` | 201 | `AuthResponse` |
    | `POST /auth/upgrade` | 200 | `AuthResponse` (body `CredentialsRequest`) |
    | `GET /auth/me` | 200 | `MeResponse` |

  - **Novos erros no `GlobalExceptionHandler`:**
    - `UsernameTakenException` → 409, `fields.username` = `Este nome de usuário já está em uso`. Nomes reservados geram a mesma exceção.
    - `InvalidCredentialsException` → 401, `message` = `Usuário ou senha incorretos`.
    - `NotAGuestException` → 409, `message` = `Esta conta já está registrada.`
  - **`RateLimiter`:**
    - ganha um construtor com `Duration window` (o atual delega com 1 minuto);
    - ganha `boolean tryAcquire(String clientKey)`, que conta e devolve `false` em vez de lançar exceção;
    - `acquire` passa a ser `if (!tryAcquire(k)) throw ...`, mantendo o cálculo do Retry-After.
- **Comportamento:**
  - **`register` e `login`:** passam por `authRateLimiter.acquire(ip)`. O nome é normalizado. No login, um nome inexistente, um visitante (sem senha) ou uma senha errada dão o **mesmo** `InvalidCredentialsException`.
  - **`guest`:**
    1. `guestAbuseRateLimiter.acquire(ip)`;
    2. `useAi = guestAiRateLimiter.tryAcquire(ip)`;
    3. cria o usuário visitante, tentando outro nome se houver colisão;
    4. cria as 3 tarefas de `GuestSeedTasks`;
    5. devolve o token.
  - **`upgrade`:** lê o usuário **do banco**. Se não for visitante, lança `NotAGuestException`. Senão: nome livre (normalizado), hash, `guest = false`, `tokensValidAfter = agora` e um token novo de 7 dias, emitido 1 segundo depois de `tokensValidAfter` para não ser recusado pela comparação em segundos.
- **Tarefas de exemplo** (`GuestSeedTasks.forToday(LocalDate today)`):
  1. "Estudar para a prova de estrutura de dados" / "Árvores AVL e rotações, prova na quinta" / HIGH / STUDY / today+3 / pendente
  2. "Preparar apresentação do projeto" / "Slides com os resultados do trimestre para a reunião de segunda" / MEDIUM / WORK / today+5 / pendente
  3. "Marcar consulta no dentista" / "" / LOW / HEALTH / sem prazo / **concluída**

- [ ] **Step 1: Escrever `AuthFlowIntegrationTest`** (`@SpringBootTest`, `@AutoConfigureMockMvc` e Testcontainers):
  - `registerThenLoginWorksIgnoringCaseAndSpaces`: cadastro com `Ana_Dev` → 201 com `username` = `ana_dev`; login com `"  ANA_DEV "` → 200 com token.
  - `duplicateUsernameDifferentCaseIs409`
  - `reservedUsernameIs409`: `Visitante` → 409.
  - `wrongPasswordAndUnknownUserGiveSameMessage`: os dois dão 401 com o mesmo `message`.
  - `passwordOver72BytesIs400`: senha com 40 `ç` → 400, com `fields.password` presente.
  - `guestGetsThreeSeedTasksOneCompleted`: `/auth/guest` → 201 e `guest = true`; `GET /tasks` com o token → 3 tarefas, 1 com `status` = `COMPLETED`.
  - `upgradeKeepsTasksAndKillsOldGuestToken`: visitante → `/auth/upgrade` com `joao` → 200 e `guest = false`. O novo token vê as 3 tarefas. O token antigo dá 401 em `GET /tasks`, e uma segunda chamada a `/auth/upgrade` com o token novo dá 409.
  - `meReturnsCurrentUser`
  - `tokenOfDeletedUserIs401`: cria visitante, apaga com `userRepository.deleteById` → `GET /auth/me` = 401.
  - `staleBearerHeaderIsIgnoredOnPublicRoutes`: `POST /auth/login` com `Authorization: Bearer lixo` e credenciais válidas → 200.
- [ ] **Step 2: Escrever em `RateLimiterTest`:**
  - `tryAcquireReturnsFalseInsteadOfThrowing`
  - `customWindowResetsAfterOneHour`: com `Clock` controlado.
- [ ] **Step 3: Rodar e ver falhar.** `./mvnw test -Dtest=AuthFlowIntegrationTest,RateLimiterTest`
- [ ] **Step 4: Implementar.**
- [ ] **Step 5: Rodar.** `./mvnw test -Dtest=AuthFlowIntegrationTest,RateLimiterTest` → PASS.

---

### Task 4: Tarefas por dono e orçamento de IA

**Files:**
- Modify: `backend/src/main/java/com/flowboard/backend/service/TaskService.java`
- Modify: `backend/src/main/java/com/flowboard/backend/controller/TaskController.java`
- Create: `backend/src/main/java/com/flowboard/backend/ai/AiBudget.java`
- Modify: `backend/src/main/resources/application.properties`: `app.ai.daily-suggestions-per-user=${AI_DAILY_SUGGESTIONS_PER_USER:30}`
- Test: `backend/src/test/java/com/flowboard/backend/service/TaskOwnershipIntegrationTest.java`
- Modify: `backend/src/test/java/com/flowboard/backend/service/TaskServiceTest.java`
- Modify: `backend/src/test/java/com/flowboard/backend/controller/TaskControllerValidationTest.java`
- Modify: `backend/src/test/java/com/flowboard/backend/controller/TaskControllerParseTest.java`
- Create: `backend/src/test/java/com/flowboard/backend/security/TestAuth.java`

**Interfaces:**
- Consumes:
  - `AuthUser`;
  - `UserRepository.consumeAiSuggestion`, `findAllByOwnerIdOrderByIdAsc` e `findByIdAndOwnerId`.
- Produces:
  - **`TaskService`** (todo método recebe `Long ownerId` primeiro):
    - `createTask(Long ownerId, TaskRequest)`
    - `listTasks(Long ownerId)`
    - `getTask(Long ownerId, Long id)`
    - `updateTask(Long ownerId, Long id, TaskRequest)`
    - `toggleStatus(Long ownerId, Long id)`
    - `deleteTask(Long ownerId, Long id)`
    - `createSeedTask(User owner, TaskRequest request, boolean completed, boolean useAi)`
    - Tarefa de outro dono ou inexistente → `TaskNotFoundException`.
    - O dono de uma tarefa nova vem de `userRepository.getReferenceById(ownerId)`.
  - **`AiBudget`:** `boolean tryConsume(Long userId)` chama `consumeAiSuggestion(userId, LocalDate.now(clock), limit) == 1`. O `clock` é o bean `appClock`.
  - **`requestSuggestion`** passa a decidir entre IA e regras:
    - `useAi && aiBudget.tryConsume(ownerId)` → agenda no `TaskSuggestionGenerator`, como hoje;
    - senão → aplica `ruleBasedAssistant.suggest(...)` na hora e salva.
    - O fallback atual (fila cheia → regras) continua.
  - **Controller:** todo método recebe `@AuthenticationPrincipal AuthUser user`. O `parse` usa `ClientIp.of(http)`.
  - **`TestAuth.as(long id, String username, boolean guest)`:** devolve um `RequestPostProcessor` via `SecurityMockMvcRequestPostProcessors.authentication(...)`. Os testes `@WebMvcTest` passam a importar `SecurityConfig`, `CorsConfig` e `JwtService` e a usar `@MockitoBean UserRepository`.

- [ ] **Step 1: Escrever `TaskOwnershipIntegrationTest`** (Spring Boot, MockMvc e Testcontainers; usuários A e B criados por `/auth/register`):
  - `otherUsersTaskIsInvisible`: A cria uma tarefa; B recebe 404 em `GET`, `PUT`, `PATCH` (toggle-status) e `DELETE /tasks/{id}`, e `GET /tasks` de B volta `[]`. A tarefa de A continua intacta.
  - `noTokenIs401WithErrorShape`: `GET /tasks` sem token → 401 com `status` 401 e `error` = `Não autenticado`.
  - `garbageTokenIs401`
  - `expiredTokenIs401`: emitido pelo `JwtService` com um `Clock` 8 dias no passado.
  - `aiBudgetFallsBackToRules`: com `app.ai.daily-suggestions-per-user=1` via `@TestPropertySource` e um `TaskAiAssistant` mockado que devolve uma sugestão com fonte `AI`: a primeira tarefa sai com fonte `AI`, a segunda com fonte `RULES`.
- [ ] **Step 2: Ajustar `TaskServiceTest`** para a nova assinatura (owner 1L) e acrescentar:
  - `getOfAnotherOwnerThrowsNotFound`
  - `exhaustedBudgetUsesRulesWithoutScheduling`: o generator não é chamado, e a fonte é `RULES`.
- [ ] **Step 3: Ajustar os testes `@WebMvcTest`.** Cada requisição usa `.with(TestAuth.as(1L, "ana", false))`. Acrescentar em `TaskControllerValidationTest` o teste `createWithoutAuthIs401`.
- [ ] **Step 4: Rodar e ver falhar.** `./mvnw test -Dtest=TaskOwnershipIntegrationTest,TaskServiceTest`
- [ ] **Step 5: Implementar.**
- [ ] **Step 6: Rodar a suíte inteira.** `./mvnw test` → `BUILD SUCCESS`, sem nenhum teste antigo removido.

---

### Task 5: Limpeza de visitantes inativos

**Files:**
- Create: `backend/src/main/java/com/flowboard/backend/auth/GuestCleanup.java`
- Modify: `backend/src/main/java/com/flowboard/backend/BackendApplication.java`: `@EnableScheduling`
- Test: `backend/src/test/java/com/flowboard/backend/auth/GuestCleanupTest.java`

**Interfaces:**
- Consumes: `UserRepository.deleteInactiveGuests(Instant cutoff)` e o bean `appClock`.
- Produces: `GuestCleanup.run()`, que devolve `int` (quantos foram apagados) e registra a contagem em log. Ele é chamado:
  - por `@Scheduled(cron = "0 0 4 * * *", zone = "${app.timezone}")`;
  - por `@EventListener(ApplicationReadyEvent.class)`, com um comentário explicando que o Render dorme e o cron pode não disparar.

- [ ] **Step 1: Escrever `GuestCleanupTest`** (Testcontainers):
  - `runDeletesOnlyGuestsInactiveForMoreThanSevenDays`: um visitante visto há 8 dias, um visto há 1 dia e uma conta vista há 30 dias → `run()` devolve `1`, e a conta continua existindo.
- [ ] **Step 2: Rodar e ver falhar.** `./mvnw test -Dtest=GuestCleanupTest`
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar.** `./mvnw test -Dtest=GuestCleanupTest` → PASS.

---

### Task 6: Front: sessão, chamadas autenticadas e tela de entrada

**Files:**
- Modify: `frontend/js/api.js`
- Create: `frontend/js/auth.js`
- Modify: `frontend/index.html`
- Modify: `frontend/css/style.css`
- Modify: `frontend/js/script.js`: a carga inicial passa a vir do `auth.js`, e o polling de sugestão para em 401.

**Interfaces:**
- Produces (em `api.js`):
  - `const API_BASE = "https://flowboard-backend-qnut.onrender.com"`. As rotas de tarefa usam `${API_BASE}/tasks`.
  - `getToken()`, `setSession(token)` e `clearSession()` usam a chave `flowboard.token`, sempre dentro de `try/catch`, porque o `localStorage` pode estar bloqueado.
  - `request()` envia `Authorization: Bearer` **exceto** para `/auth/login`, `/auth/register` e `/auth/guest`. Em qualquer 401 fora dessas rotas, chama `onSessionExpired()` (definida em `auth.js`) antes de lançar o erro.
  - Novas funções: `register(username, password)`, `login(username, password)`, `startGuest()`, `upgradeGuest(username, password)` e `getMe()`.
- Produces (em `auth.js`):
  - `showAuthView(notice?)`
  - `showAppView(me)`: preenche o header e chama `loadTasks()`
  - `onSessionExpired()`: limpa a sessão, para o polling (esvazia `watchedTaskIds`) e chama `showAuthView("Sua sessão expirou. Entre novamente.")`. Só age uma vez por expiração.
  - `boot()`: com token, chama `getMe()` e depois `showAppView`; sem token, ou com 401, chama `showAuthView()`.
- **HTML:**
  - **`<section id="auth-view">`** no modelo B:
    - logo e a frase da spec;
    - botão primário `#guest-btn` "Testar sem cadastro" e a legenda;
    - divisor "já tem conta?", botão secundário "Entrar" e link "Criar conta". Os dois revelam um único `<form id="auth-form">` (usuário, senha e, só no cadastro, "Confirmar senha") com o título e o botão trocados pelo modo;
    - `<p id="auth-error" role="alert">`, `<p id="auth-notice">` e o mesmo `slow-hint` de partida a frio.
  - O conteúdo atual do app fica dentro de `<div id="app-view" hidden>`.
  - **Header:**
    - `#user-area` com o nome e o botão "Sair";
    - para visitante: o texto "Visitante" e o botão `#upgrade-btn` "Criar conta para salvar", que abre o modal `#upgrade-modal` (usuário, senha e confirmação);
    - "Sair" de visitante abre o modal de confirmação `#logout-guest-modal` ("Sair do modo visitante?" / "Você perde o acesso a estas tarefas. Para mantê-las, crie uma conta antes de sair.").
- **Erros exibidos:** `fields.*` do 400 ao lado do campo; 409 no campo usuário; 401 do login em `#auth-error`; 429 com "Muitas tentativas. Tente de novo em instantes."; "As senhas não conferem" é validado no próprio front.
- **Partida a frio:** "Testar sem cadastro" e "Entrar" ficam desabilitados durante a requisição, com o texto "Preparando seu espaço…" ou "Entrando…", e a dica de servidor dormindo aparece depois de 4 s.

- [ ] **Step 1: Implementar `api.js` e `auth.js`, e ajustar `script.js`.**
- [ ] **Step 2: Implementar o HTML e o CSS** com os tokens existentes: mobile primeiro, área de toque de pelo menos 44 px, `label` em todos os campos e `autocomplete` (`username`, `current-password` e `new-password`).
- [ ] **Step 3: Teste temporário em `api.js`.** Apontar `API_BASE` para `http://localhost:8080`, subir a API local (`JWT_SECRET` e `DATABASE_PASSWORD` definidos na sessão do terminal) e servir o front na porta 5500, que está nos `ALLOWED_ORIGINS` padrão. **Voltar o `API_BASE` para produção ao final da Task 7.**

---

### Task 7: Verificação no navegador, documentação e preparação do deploy

**Files:**
- Modify: `README.md`: rotas `/auth/*`; uma seção "Autenticação" (JWT HS256, BCrypt, isolamento com 404, leitura do usuário a cada requisição e por quê, visitante, orçamento de IA); `JWT_SECRET` na tabela de variáveis; limitações (sem recuperação de senha, sem revogação de tokens de conta antes de 7 dias, `localStorage`).
- Modify: `docs/superpowers/specs/2026-11-07-contas-de-usuario-design.md`: status "implementado".

- [ ] **Step 1: Playwright, com a API local, em 375 px e 1366 px:**
  - **Caminho do visitante:** clicar em "Testar sem cadastro" (com clique duplo rápido) → só **um** `POST /auth/guest` na aba de rede; app com 3 tarefas e números Total 3, Concluídas 1.
  - **Upgrade:** "Criar conta para salvar" → as tarefas continuam e o header mostra o nome.
  - **Sair e entrar:** "Sair" → entrada; "Entrar" com o nome em maiúsculas → mesmas tarefas.
  - **Cadastro:** senhas diferentes → erro local; nome já usado → erro no campo.
  - **Sessão expirada:** trocar o token no `localStorage` por lixo, com uma sugestão sendo consultada → volta para a entrada com o aviso, e a rede para de chamar `/tasks/{id}`.
  - **Geral:** sem rolagem horizontal, sem erros no console e detector da impeccable sem achados.
- [ ] **Step 2: Voltar `API_BASE` para produção** e conferir com `grep -n "localhost" frontend/js/api.js`, que não deve encontrar nada.
- [ ] **Step 3: Rodar a suíte inteira.** `cd backend; ./mvnw test` → `BUILD SUCCESS`. Registrar o número de testes no relatório.
- [ ] **Step 4: Atualizar o README e a spec.**
- [ ] **Step 5: Entregar ao usuário, um passo manual por mensagem:**
  1. Gerar o segredo localmente, sem exibir na tela. O comando PowerShell gera 48 bytes aleatórios em Base64 e copia para a área de transferência.
  2. Criar `JWT_SECRET` no Render, com "Save" **sem** rebuild, se o Render oferecer essa opção.
  3. Relatório da fase: o que mudou, por quê, como testar, resultado do `mvn test` e a mensagem de commit `feat(auth): add user accounts, guest mode and per-user tasks`.
  4. Commit e push pelo VS Code (lembrar do "+" em Changes).
  5. Depois do deploy, conferir o caminho do visitante em produção.
  6. Configurar o despertador no UptimeRobot (plano gratuito): monitor HTTP para `https://flowboard-backend-qnut.onrender.com/health` a cada 10 minutos. Depois, documentar no README (seção de limitações: "a partida a frio é evitada por um monitor externo").
