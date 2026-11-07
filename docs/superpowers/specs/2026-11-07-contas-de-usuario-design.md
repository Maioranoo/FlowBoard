# Contas de usuário e modo visitante: design

Data: 2026-11-07
Status: implementado (aprovado pelo usuário em 2026-11-07; plano em docs/superpowers/plans/2026-11-07-contas-de-usuario.md)

## Por que

Hoje o FlowBoard tem **uma única lista de tarefas compartilhada por todos os visitantes**. O link vai circular em candidaturas (vaga de estágio AI Engineer no iFood), então qualquer pessoa vê, edita e apaga as tarefas das outras, e um recrutador pode dar de cara com conteúdo deixado por um estranho.

O objetivo é que **cada pessoa veja só as próprias tarefas**. Também é importante que o recrutador chegue à parte de IA com o mínimo de atrito.

O roteiro esperado de um recrutador:
1. Abre o link e vê que o site funciona.
2. Entra no app.
3. Cria uma tarefa, vê a sugestão da IA chegar, conclui uma tarefa e vê os números do resumo mudarem.

## Objetivos

1. **Contas com usuário e senha:** cadastro, login e sair.
2. **"Testar sem cadastro":** um clique cria uma conta de visitante isolada, que já vem com tarefas de exemplo.
3. **Visitante pode virar conta:** define usuário e senha sem perder as tarefas.
4. **Isolamento garantido no servidor:** a API só mexe nas tarefas do dono do token.
5. **Limpeza automática:** visitantes sem uso há 7 dias são apagados, junto com suas tarefas.
6. **Recomeço limpo:** as tarefas que existem hoje em produção são apagadas.

## Fora do escopo (decisão do usuário)

- Recuperação de senha, de qualquer tipo.
- E-mail: cadastro, verificação ou envio.
- Apagar a conta, trocar a senha e trocar o nome de usuário.
- Login social (Google, GitHub).

## Antiobjetivos (seria um fracasso mesmo funcionando)

- O recrutador ter que preencher um formulário antes de ver a IA. O caminho de visitante tem que ser **um clique**.
- Uma pessoa conseguir ler ou alterar tarefas de outra, nem adivinhando IDs.
- Algum segredo (chave do JWT, senha do banco) aparecer no código ou em log.
- Senha guardada em texto ou com hash reversível.
- Quebrar a funcionalidade de IA existente: sugestão assíncrona, fallback, `/tasks/parse` e rate limit.
- O front e a API publicados ficarem incompatíveis depois que o deploy termina. Uma janela de alguns minutos durante o deploy foi aceita pelo usuário.

## Restrições

- **Stack atual:** Spring Boot 3.5 (Java 17), PostgreSQL no Neon com Flyway (`ddl-auto=validate`), front em HTML/CSS/JS puros na Vercel, API no Render (plano gratuito, dorme sem uso, pouca memória).
- **Domínios diferentes:** o front fica em `flow-board-lovat.vercel.app` e a API em `flowboard-backend-qnut.onrender.com`.
- **Segredos só por variável de ambiente.** A nova variável `JWT_SECRET` precisa ser criada no Render pelo usuário, como passo manual guiado, um por mensagem.
- **Regras do projeto:**
  - toda mudança de contrato da API vem com o ajuste no front, no mesmo commit;
  - `mvn test` verde ao final;
  - explicação didática em português;
  - mensagem de commit no padrão Conventional Commits;
  - nada de comandos git que façam stage (o usuário faz commit pelo VS Code).
- **Visual:** manter a identidade atual: tokens do `:root`, IBM Plex Sans, logo e isotipo, vermelho de destaque. Nada de cara de template de IA.

## Decisões do usuário

- **Login por nome de usuário e senha, sem e-mail.**
- **Duas portas na entrada:** criar conta/entrar ou testar sem cadastro.
- **Tela de entrada no modelo "B, visitante em destaque":**
  - logo e uma frase curta sobre o produto ("Descreva uma tarefa com suas palavras e a IA preenche os campos e sugere os próximos passos.");
  - botão principal **"Testar sem cadastro"**, com a legenda "Cria um espaço só seu, com tarefas de exemplo.";
  - abaixo, um divisor "já tem conta?", um botão secundário **"Entrar"** e o link **"Criar conta"**, que abrem o formulário correspondente.
- **Contas de visitante:** podem virar conta definitiva e são apagadas após 7 dias sem uso.
- **Tarefas atuais de produção:** são apagadas.
- **Ordem:** este login vem antes das melhorias voltadas à vaga, que serão um projeto separado.
- **Deploy único:** API, front e migração V3 vão num único push. O usuário aceita alguns minutos de site quebrado (o front da Vercel publica antes de a API do Render terminar), porque o link ainda não está circulando. Não é preciso fazer o lançamento em etapas. Antes do push, a variável `JWT_SECRET` já deve existir no Render.

## Escolhas do Claude (o usuário pode vetar)

- **Usuário:**
  - 3 a 20 caracteres, só letras, números, `.` e `_`;
  - único sem diferenciar maiúsculas (guardar normalizado em minúsculas);
  - nomes reservados que não podem ser usados: `visitante`, `admin`.
- **Senha:** mínimo de 8 e máximo de 72 caracteres (72 é o limite de entrada do BCrypt). Guardada com BCrypt.
- **Sessão:**
  - token JWT assinado com HMAC (HS256) usando `JWT_SECRET`, enviado no cabeçalho `Authorization: Bearer`;
  - o front guarda o token no `localStorage`;
  - **por que não cookie:** front e API estão em domínios diferentes, e cookies entre sites estão sendo bloqueados pelos navegadores;
  - **o risco do `localStorage` é XSS,** já mitigado: todo texto do usuário e da IA é escapado antes de virar HTML.
- **Validade do token:**
  - conta normal: 7 dias;
  - visitante: o token vale enquanto o visitante existir. Expira junto com a conta, que some após 7 dias sem uso;
  - não há refresh token. Quando o token expira, o usuário faz login de novo.
- **Implementação de segurança:**
  - Spring Security com o resource server de JWT (`spring-boot-starter-oauth2-resource-server`, chave simétrica), API sem estado (stateless) e CSRF desligado, porque não há cookie de sessão;
  - é o caminho padrão de mercado e rende boas perguntas de entrevista.
- **Isolamento:**
  - toda consulta de tarefa filtra pelo dono: `findByIdAndOwnerId`, `findAllByOwnerId`;
  - tarefa de outro dono responde **404**, igual a uma tarefa inexistente, para não revelar que ela existe;
  - o dono vem sempre do token, nunca do corpo da requisição.
- **Visitante:**
  - nome gerado no formato `visitante-xxxxx`, sem senha, marcado como visitante;
  - "última atividade" atualizada no máximo uma vez por hora por usuário, para não escrever no banco a cada requisição;
  - **tarefas de exemplo:** 3 tarefas fixas, em português, de categorias diferentes. Uma já vem concluída, para os números do resumo não começarem em zero;
  - as sugestões dessas tarefas são geradas pelo fluxo assíncrono normal (IA, com fallback). São cerca de 3 chamadas ao modelo por visitante, frações de centavo.
- **O token é conferido no banco a cada requisição** (escolha tomada após a revisão):
  - o "dono" do token (`sub`) é o **id** do usuário, não o nome, porque o nome muda quando um visitante vira conta;
  - além da assinatura e da validade, cada requisição carrega o usuário do banco. Se ele não existir mais (por exemplo, visitante apagado na limpeza), a resposta é **401**;
  - essa mesma leitura é aproveitada para atualizar a última atividade;
  - se o usuário é visitante, isso vem do **banco**, não do token;
  - a tabela `users` ganha a coluna `tokens_valid_after`. Tokens emitidos antes dela são recusados. Ao virar conta, ela é atualizada, então o token antigo de visitante (que não expira) deixa de funcionar e não vira uma chave permanente para a conta;
  - na prática, a API deixa de ser 100% "sem estado". É uma leitura por chave primária por requisição, aceita conscientemente, e o README deve explicar por quê.
- **Visitante vira conta:** o usuário informa nome e senha, e a mesma linha de usuário é atualizada (deixa de ser visitante). Assim as tarefas continuam ligadas a ela e não precisam ser copiadas. A API devolve um token novo.
- **Limpeza:**
  - uma tarefa agendada apaga visitantes sem atividade há mais de 7 dias, e as tarefas somem junto (`ON DELETE CASCADE`);
  - como o Render dorme, a limpeza também roda ao iniciar a aplicação, para não depender só do agendamento.
- **Rate limits** (reaproveitando o `RateLimiter` existente, com novas instâncias):
  - login e cadastro: 10 por minuto por IP;
  - criação de visitante: 20 por hora por IP (vários recrutadores podem compartilhar o IP do escritório) e 100 por hora no total;
  - **se o limite de visitantes estourar, "Testar sem cadastro" continua funcionando:** o visitante é criado normalmente, mas as sugestões das tarefas de exemplo vêm das regras locais, sem chamar a IA. O caminho de um clique nunca é bloqueado; só o gasto com o modelo é cortado. Quem bloqueia de fato é o limite contra abuso, bem mais alto (60 por hora por IP), que devolve 429;
  - **orçamento de IA por usuário:** no máximo 30 sugestões geradas pelo modelo por usuário por dia, contando criação e edição de tarefas. Acima disso, a sugestão vem das regras locais, e a interface já mostra a origem ("Regras"). Isso fecha o gasto ilimitado via `POST /tasks`;
  - `/tasks/parse`: continua por IP e global, como hoje.
- **Erro de login:** a mensagem é sempre genérica ("Usuário ou senha incorretos"), para não revelar quais nomes existem. No cadastro é inevitável informar "nome já em uso".
- **Banco:** uma migração Flyway V3:
  - cria a tabela `users` (id, username único, password_hash anulável, guest, created_at, last_seen_at);
  - apaga as tarefas atuais;
  - adiciona `tasks.user_id` como `NOT NULL`, com chave estrangeira e `ON DELETE CASCADE`;
  - cria um índice em `tasks.user_id`.
- **Front:**
  - **sem token:** mostra a tela de entrada;
  - **com token:** mostra o app;
  - **qualquer resposta 401:** apaga o token e volta para a entrada, com o aviso "Sua sessão expirou";
  - **no header,** depois de entrar, aparecem o nome do usuário e "Sair". Para visitante, aparece "Visitante" e um botão discreto "Criar conta para salvar", que abre o formulário de conversão;
  - **visitante clicando em "Sair":** uma confirmação avisa que as tarefas serão perdidas.
- **CORS:** passar a aceitar o cabeçalho `Authorization` e manter as origens de `ALLOWED_ORIGINS`.
- **Swagger:** continua aberto em produção, com suporte a "Authorize" (Bearer) para testar rotas protegidas.

## Contrato da API (proposto)

| Método | Rota | Autenticação | Descrição |
|---|---|---|---|
| `POST` | `/auth/register` | não | Cria a conta; devolve `{ token, username, guest: false }` |
| `POST` | `/auth/login` | não | Devolve `{ token, username, guest }` |
| `POST` | `/auth/guest` | não | Cria um visitante com as tarefas de exemplo; devolve o token |
| `POST` | `/auth/upgrade` | sim (visitante) | Transforma o visitante em conta; devolve um token novo |
| `GET` | `/auth/me` | sim | Devolve `{ username, guest }`, usado para validar o token ao abrir o site |
| `*` | `/tasks/**` | sim | Mesmas rotas de hoje, agora restritas ao dono |

Sem token, ou com token inválido, as rotas protegidas respondem **401**. Erros de validação seguem o formato atual (`400` com `fields`).

Não há rota de logout: sair é apagar o token no front. Com JWT sem estado, o servidor não guarda sessão. Essa limitação está aceita e deve ir para as limitações do README.

## Testes esperados

- Isolamento: o usuário A não lista, lê, edita, conclui nem apaga tarefas do usuário B (todos esses casos respondem 404).
- 401 sem token e com token inválido ou expirado.
- Cadastro: usuário duplicado (com maiúsculas e minúsculas diferentes), nome inválido, senha curta.
- Login: a mesma mensagem para usuário inexistente e para senha errada.
- Visitante: é criado com 3 tarefas e vira conta mantendo as tarefas.
- Limpeza: apaga só visitantes inativos há mais de 7 dias, junto com as tarefas.
- Rate limit dos novos endpoints.
- Os testes existentes continuam passando, ajustados para autenticar.
- A migração V3 roda no Testcontainers.

## Fica a critério de quem construir

- A organização interna dos pacotes (`auth`, `security`, `user`…).
- A forma exata do texto das tarefas de exemplo, desde que mostre bem a IA, por exemplo com uma tarefa de estudo e outra de trabalho.
- Detalhes visuais dos formulários, seguindo o modelo B e os tokens atuais.
- Como dividir o trabalho em etapas locais, desde que tudo chegue à `main` num único push.

## Atualizações de documentação

- **README:** novas rotas, a seção de autenticação (JWT, BCrypt, isolamento, visitante), a variável `JWT_SECRET` na tabela de execução local, e as limitações (sem recuperação de senha, sem revogação de token).
