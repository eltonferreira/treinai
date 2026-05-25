# Phase 0 — Research: Treinaí Backend MVP

Este documento consolida as decisões técnicas que sustentam o plano. A stack
macro já está fixada pela constituição e pelo prompt de planejamento; o foco aqui
é resolver as escolhas de design abertas e justificar cada uma. Não restam itens
`NEEDS CLARIFICATION`.

## R1. Modularização com Spring Modulith

- **Decision**: Um módulo Spring Modulith por bounded context sob `br.com.treinai`:
  `identity`, `billing`, `catalog`, `training`, `nutrition`, `aicoach`, `progress`,
  mais um módulo transversal `shared` para tipos compartilhados (Problem Details,
  base de eventos, utilitários de segurança expostos). Cada módulo expõe sua API
  pública no pacote-raiz (interfaces de serviço, DTOs públicos, eventos) e mantém
  `internal/` (domain, persistence, web, config) privado.
- **Rationale**: Alinha 1:1 com os agrupamentos de requisitos da spec (FR por
  módulo) e com o Princípio I. O `shared` evita dependência cíclica para tipos
  realmente comuns, mantendo-o "folha" (não depende de nenhum módulo de negócio).
- **Alternatives considered**:
  - Pacote único por camada (controllers/services/repos): rejeitado — viola
    fronteiras de módulo e impede `ApplicationModules.verify()`.
  - `catalog` dentro de `training`/`nutrition`: rejeitado — as bibliotecas são
    consumidas por ambos e curadas por admin; merecem módulo próprio.

- **Verificação**: teste `ApplicationModules.of(TreinaiApplication.class).verify()`
  no CI; geração de docs via `spring-modulith-docs` (`Documenter`) em uma tarefa de
  teste opcional. Dependência: BOM `spring-modulith-bom` + starters
  `spring-modulith-starter-core`, `spring-modulith-starter-jpa` e
  `spring-modulith-events-api`/`spring-modulith-events-jpa` para persistência de
  eventos (event publication registry, garantindo entrega confiável).

## R2. Autenticação — Google ID token → JWT próprio (Nimbus)

- **Decision**: Fluxo em duas pernas:
  1. Cliente autentica no Google e envia o **ID token do Google** a
     `POST /api/v1/auth/google`.
  2. Backend valida o ID token contra os **JWKs do Google** (issuer
     `https://accounts.google.com`, audience = client-id), usando um
     `JwtDecoder` dedicado (`NimbusJwtDecoder.withJwkSetUri(...)`). Cria/recupera o
     `User`, e **emite JWT próprio** (access curto ~15 min + refresh longo
     persistido) via `NimbusJwtEncoder` com par de chaves **RSA** próprio.
  3. Rotas protegidas validam o access token via
     `spring-boot-starter-security-oauth2-resource-server` apontando para o JWK Set
     **do próprio backend** (issuer da aplicação).
- **Chaves RSA**: par RSA 2048 gerado fora do código; chave privada (PEM) injetada
  por variável de ambiente/secret (`APP_JWT_PRIVATE_KEY` / `APP_JWT_PUBLIC_KEY` ou
  caminho montado). `kid` fixo para permitir rotação futura. Endpoint JWK Set
  interno (`/.well-known/jwks.json` ou configuração local do resource server)
  expõe apenas a chave pública. Nunca commitar a chave (`.env` local /
  Secrets Manager em prod).
- **Refresh token**: persistido em `identity` (hash do token, não o valor cru),
  com `expiresAt`, `revokedAt`, vínculo ao usuário e device opcional. Rotação a
  cada uso (refresh rotation) e revogação no logout.
- **Multi-provedor (FR-006)**: abstração `SocialIdentityVerifier` por provedor
  (`GoogleIdentityVerifier` no MVP); adicionar Apple = nova implementação + entrada
  no endpoint, sem tocar billing/training/etc.
- **Rationale**: `NimbusJwtEncoder` já vem com o Spring Security OAuth2; dispensa o
  peso do Spring Authorization Server para um caso de emissão simples. Dois
  `JwtDecoder` (Google e próprio) coexistem por configuração explícita de beans
  qualificados.
- **Alternatives considered**: Spring Authorization Server (overkill p/ MVP);
  sessão server-side com cookie (não stateless, conflita com deploy).

## R3. Entitlements / cotas configuráveis (billing)

- **Decision**: Modelo genérico orientado a dados, sem `if` por nome de plano:
  - `Plan` (Free/Plus/Premium, preço, ativo).
  - `Entitlement`: `(plan, dimension, periodType, limit)` onde `dimension ∈
    {TRAINING_GENERATION, NUTRITION_GENERATION, TRAINING_ADJUSTMENT,
    NUTRITION_ADJUSTMENT, AI_ANALYSIS}` (treino e dieta têm cotas independentes —
    o usuário escolhe o que gerar/ajustar), `periodType ∈ {MONTHLY, QUARTERLY,
    UNLIMITED, NONE}`, `limit` inteiro (`-1` ou flag = ilimitado; ausência =
    indisponível). Flags de feature (dieta, acompanhamento avançado) modeladas como
    dimensão de acesso booleano (`FeatureAccess`).
  - `QuotaUsage`: consumo por `(user, dimension, periodStart)` — contador
    incrementado transacionalmente.
- **Verificação de cota (concorrência)**: API pública
  `BillingEntitlementService.consume(userId, dimension)` executada em transação;
  usa **`SELECT ... FOR UPDATE`** (lock pessimista) ou `UNIQUE(user, dimension,
  periodStart)` + incremento condicional (`UPDATE ... WHERE used < limit`) para ser
  à prova de concorrência — duas requisições simultâneas não furam a cota.
  Padrão **check-and-consume atômico**: o consumo só efetiva se a ação a jusante
  for bem-sucedida; em falha de IA, **não consome** (rollback / consumo diferido).
- **Janela de período determinística**: `periodStart` derivado da data de início da
  assinatura do usuário (âncora), não do mês-calendário — cobre o edge case da
  virada de período. Para Free, janela trimestral ancorada na criação do usuário.
- **Autorização prévia entre módulos**: training/nutrition/aicoach chamam billing
  **sincronamente** via API pública antes de agir (autorização não pode ser
  assíncrona). Justificativa registrada no plano (desvio consciente da preferência
  por eventos, permitido pelo Princípio I quando há "necessidade de resposta
  imediata").
- **Rationale**: Configurável por dados = preços/limites mudam sem deploy
  (Assumption da spec). Lock no consumo atende SC-002.
- **Alternatives considered**: Redis counter (adiciona infra; PostgreSQL com lock
  basta no MVP); cota por mês-calendário (falha no edge da virada).

## R4. Integração Gemini (aicoach)

- **Decision**: Interface `AiCoachClient` (porta) com implementação
  `GeminiCoachClient` (adapter) usando **Spring Cloud OpenFeign** (`@FeignClient`)
  contra a REST API `generativelanguage.googleapis.com`. Saída forçada a JSON via
  `responseMimeType=application/json` + `responseSchema` (structured output do
  Gemini) **e** validação local do JSON contra um schema de domínio.
  Dependência: `spring-cloud-starter-openfeign` (atenção ao alinhamento de versão
  do Spring Cloud com Spring Boot 4 — ponto de atenção no primeiro build).
- **Prompt estruturado**: builders de prompt por caso (geração de treino, geração
  de dieta, ajuste, análise). O prompt declara explicitamente a restrição de usar
  **apenas itens das bibliotecas** (FR-040), injetando a lista de itens válidos.
- **Validação e re-solicitação (FR-041)**: parser tolerante → valida cada item
  contra `catalog`; se houver item fora do catálogo, re-solicita com a restrição
  reforçada até **N tentativas (config, default 2)**; persistindo a falha → erro
  controlado, **sem plano parcial** e **sem consumir cota** (FR-025, edge cases).
- **Auditoria (FR-023)**: toda chamada grava `AIInteraction` (prompt, resposta,
  user, timestamp, tokens, custo estimado, latência, status) — inclusive em erro.
- **Resiliência / tier Free (FR-024)**: timeout explícito, retry com backoff
  exponencial apenas para erros transitórios (429/5xx), circuit-breaker simples
  (config). Ao atingir limite/indisponibilidade → degradação graciosa, sem cota.
- **Rationale**: Porta/adapter permite mock em teste (Princípio II) e troca de
  provedor. Feign dá um cliente declarativo enxuto, alinhado à preferência por libs
  modernas em vez de cliente HTTP manual. Structured output reduz respostas
  malformadas, mas a validação local é a rede de segurança real.
- **Alternatives considered**: `RestClient` manual (rejeitado — preferir cliente
  declarativo); Vertex AI SDK (mais peso/credenciais GCP; REST do AI Studio basta
  no tier Free); free-text parsing sem schema (frágil).

## R5. Pagamentos — Mercado Pago (billing)

- **Decision**: Adapter `PaymentGateway` com `MercadoPagoGateway` usando o **SDK
  oficial `com.mercadopago:sdk-java`**. Suporta criação de assinatura/preferência
  para **PIX** e **cartão recorrente**. O SDK é isolado atrás da porta
  `PaymentGateway`, então a testabilidade vem do mock da porta + stubs de contrato.
- **Webhook (FR-011)**: `POST /api/v1/billing/webhooks/mercadopago` público,
  **idempotente**: cada notificação gera/consulta um `PaymentEvent` por
  `externalId`; reprocessamento não altera estado nem duplica efeito (SC-004).
  Validação de assinatura/origem do webhook (secret) antes de processar. Webhook de
  assinatura desconhecida → rejeitado e auditado sem mudar estado.
- **Máquina de estados da assinatura**: `PENDING → ACTIVE → (SUSPENDED | CANCELLED)`
  com transições disparadas por eventos de pagamento. Ao mudar de estado, publica
  **evento de domínio Modulith** (`SubscriptionActivated`, `SubscriptionSuspended`,
  `SubscriptionCancelled`) — billing é a fonte; demais módulos reagem (ex.: ajuste
  de entitlements é interno ao billing, mas métricas/observabilidade consomem).
- **Rationale**: Idempotência por `externalId` persistido é o requisito-chave
  (SC-004) e fica do nosso lado, independente do SDK. Usar o SDK oficial evita
  reimplementar a integração e segue a preferência por libs.
- **Alternatives considered**: REST manual (rejeitado — há SDK oficial); confiar no
  SDK para idempotência (não garante; mantemos o controle); processar webhook de
  forma síncrona acoplada (preferir registrar evento + reagir).

## R6. Persistência, migrations e fronteiras de dados

- **Decision**: Cada módulo é dono de suas tabelas, **sem FK cruzando módulos** —
  referências por ID (ex.: `TrainingPlan.userId`), integridade garantida na
  aplicação. Migrations **Flyway** versionadas; organização por prefixo de módulo
  para legibilidade (ex.: `V1__identity_init.sql`, `V2__billing_init.sql`, ...),
  todas no diretório padrão `db/migration`. `spring.jpa.hibernate.ddl-auto=validate`.
- **Convenções de coluna** (ver [data-model.md](./data-model.md)): PK **UUID v7**
  gerado na aplicação (Hibernate `@UuidGenerator(style = TIME)` — time-ordered,
  melhor localidade de índice); **enums nativos do PostgreSQL** (`CREATE TYPE ...
  AS ENUM` via Flyway, mapeados no Hibernate) em vez de `varchar` para conjuntos
  fechados; **durações sempre em segundos** (`*_seconds`).
- **Rationale**: Ausência de FK cruzada preserva a independência de evolução e o
  caminho de extração de serviço (Princípio I); o custo é validação de integridade
  referencial na aplicação, aceitável no MVP. Diretório único de migrations evita
  configuração multi-location e mantém ordem global determinística.
- **Alternatives considered**: schemas Postgres por módulo (adiciona complexidade
  de migration/grants sem ganho no MVP); FK cruzadas (viola Princípio I).

## R7. API, DTOs, PATCH e mapeamento

- **Decision**:
  - Controllers REST sob `/api/v1`, sem lógica de negócio.
  - **DTOs como `record`** com sufixos `Request` / `Response`; Bean Validation nos
    `Request`. Entidades JPA nunca cruzam a fronteira.
  - **Criação via POST; atualizações via PATCH** com `JsonNullable<T>` (dependência
    `org.openapitools:jackson-databind-nullable`) + registro do
    `JsonNullableModule` no `ObjectMapper`. **PUT nunca é usado.**
  - **MapStruct** (`componentModel = "spring"`) para toda conversão; um mapper por
    módulo no `internal/web` ou `internal/mapper`. Ordem dos annotation processors
    no Gradle: `lombok` → `lombok-mapstruct-binding` → `mapstruct`.
  - `@RestControllerAdvice` global → **Problem Details (RFC 7807)** com `type`,
    `title`, `status`, `detail` e **código de erro estável em inglês** (campo
    `errorCode`, ex.: `QUOTA_EXCEEDED`, `FEATURE_NOT_AVAILABLE`,
    `AI_UNAVAILABLE`, `LIBRARY_INSUFFICIENT`).
  - OpenAPI via **springdoc** (já no build).
- **Rationale**: Cumpre Princípios IV e V diretamente; `JsonNullable` resolve o
  "ausente vs null" exigido para PATCH.
- **Alternatives considered**: mapeamento manual (proibido salvo exceção);
  `Optional` em DTO (não distingue ausente de null em JSON como `JsonNullable`).

## R8. Observabilidade e segurança operacional

- **Decision**: Micrometer + Prometheus (já no build). Métricas de negócio via
  `Counter`/`Timer` custom: `ai_generations_total`, `ai_adjustments_total`,
  `subscription_activations_total`, `quota_rejections_total`, latência das chamadas
  Gemini. Actuator exposto de forma protegida (somente `health`/`info` públicos;
  `prometheus`/`metrics` atrás de auth ou rede interna). Logs estruturados (JSON) em
  inglês, **sem PII/segredos** (SC-007) — biometria e prompts não vão para log.
- **Rationale**: Atende Princípio VII e FR-031/FR-032.

## R9. Testes (TDD, meta 90%)

- **Decision**: Camadas:
  - Unitários: domínio/serviços (lógica de cota, máquina de estados, parsing IA).
  - Integração: Testcontainers PostgreSQL real (repos, fluxos transacionais de
    cota/concorrência).
  - **Integração de API**: `@SpringBootTest(webEnvironment=RANDOM_PORT)` com
    `MockMvc`/`TestRestTemplate` sobre Postgres (Testcontainers), validando status,
    payload, RFC 7807 e autorização/cota.
  - Fronteira de módulo: `ApplicationModules.verify()`.
  - Contrato de integrações externas: stubs (WireMock) para Gemini/Mercado
    Pago/Google JWKs.
  - Segurança: testes com `spring-security-test` (`@WithMockUser`/jwt()).
- **Gates**: JaCoCo ≥ 90% linhas e branches quebrando o build; SonarCloud Quality
  Gate verde.
- **Rationale**: Princípios II e III. WireMock isola provedores externos sem rede.
- **Alternatives considered**: H2 (proibido pela constituição); mocks de repo em
  vez de Testcontainers (proibido).

## R10. Build, CI/CD e deploy

- **Decision**:
  - `build.gradle.kts`: adicionar BOM Spring Modulith + starters; `oauth2-client`
    (validação do ID token Google via JWKs); MapStruct + `lombok-mapstruct-binding`
    com ordem correta de processors; `jackson-databind-nullable`; cliente
    Mercado Pago (REST/SDK); JaCoCo plugin com gate 90%; WireMock para testes;
    plugin Sonar (`org.sonarqube`). Nimbus JWT já disponível via
    `oauth2-resource-server` (sem authorization-server).
  - CI GitHub Actions: build → testes (Testcontainers) → JaCoCo → Sonar → Docker
    multi-stage (JRE 25) → push ECR → deploy.
  - Config por perfil Spring (`application-{profile}.yaml`); segredos locais em
    `.env` (gitignored) com `.env.example` versionado; prod via GitHub Secrets /
    AWS Secrets Manager.
  - Deploy: free tier EC2 t3.micro + RDS db.t3.micro; evolução ECS Fargate/App
    Runner + RDS/Aurora Serverless v2 sem mudança de código (stateless).
- **Pontos de atenção**: `.gitignore` atual **não** lista `.env` — o plano exige
  adicioná-lo (e criar `.env.example`) antes de qualquer segredo entrar no repo.
- **Rationale**: Cumpre os gates da constituição e o requisito de portabilidade AWS.

## Riscos e pontos de atenção

| Risco | Mitigação |
|-------|-----------|
| Limites do tier Free do Gemini (429/quota) | Backoff + circuit-breaker, degradação graciosa, sem consumir cota (FR-024). |
| Idempotência de webhook Mercado Pago | `PaymentEvent` por `externalId`, processamento idempotente (SC-004). |
| Concorrência de cota (dupla geração) | Consumo transacional com lock/UPDATE condicional (SC-002). |
| Resposta de IA malformada / item fora da biblioteca | Structured output + validação local + re-solicitação limitada + falha sem plano parcial (FR-025/FR-041). |
| Vazamento de chave RSA / segredos | `.env` gitignored + Secrets Manager; chave nunca no código; `.gitignore` precisa incluir `.env`. |
| Spring Boot 4.0.6 / Spring Modulith — coordenadas de versão | Fixar via BOMs e validar no primeiro build; ajustar versões na implementação. |
| PII em logs (biometria/prompts) | Política de logging sem PII, revisão no code review (SC-007). |
