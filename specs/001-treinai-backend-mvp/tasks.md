---
description: "Task list for Treinaí Backend MVP implementation"
---

# Tasks: Treinaí — Backend MVP

**Input**: Design documents from `/specs/001-treinai-backend-mvp/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/rest-api.md, quickstart.md

**Tests**: INCLUDED — a Constituição (Princípio II, não-negociável) e o plano exigem TDD
com camadas de teste (unit, integração Testcontainers, integração de API, fronteira de
módulo, contrato externo via WireMock). H2 e mocks de repositório são proibidos.

**Organization**: tarefas agrupadas por user story para permitir implementação e teste
independentes de cada história. Stack: Java 25, Spring Boot 4.0.6 MVC, Spring Modulith,
PostgreSQL + Flyway (`ddl-auto=validate`), MapStruct, OpenFeign (Gemini), SDK Mercado Pago,
JaCoCo (gate 90%), WireMock, Testcontainers.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependências entre si)
- **[Story]**: a qual user story a tarefa pertence (US1..US7)
- Caminhos de arquivo são relativos à raiz do repositório

## Path Conventions

- Código: `src/main/java/br/com/treinai/{módulo}/...` (API pública no pacote-raiz,
  implementação em `internal/{domain,persistence,web,config,...}`)
- Migrations: `src/main/resources/db/migration/V#__{módulo}_init.sql`
- Testes: `src/test/java/br/com/treinai/{módulo}/...`, suporte em `src/test/java/br/com/treinai/support/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: inicialização do projeto, build e estrutura de pacotes.

- [ ] T001 Configurar `build.gradle.kts` com toolchain Java 25 e dependências: Spring Boot 4.0.6 (`webmvc`, `data-jpa`, `validation`, `actuator`, `security`, `oauth2-resource-server`, `oauth2-client`), BOM Spring Modulith + `spring-modulith-starter-core`/`spring-modulith-starter-jpa`/`spring-modulith-events-jpa`, Flyway, PostgreSQL driver, springdoc-openapi, Micrometer/Prometheus, Lombok, MapStruct + `lombok-mapstruct-binding`, `org.openapitools:jackson-databind-nullable`, `spring-cloud-starter-openfeign`, `com.mercadopago:sdk-java`
- [ ] T002 Configurar a ordem dos annotation processors no `build.gradle.kts` (`lombok` → `lombok-mapstruct-binding` → `mapstruct`) para `annotationProcessor` e `testAnnotationProcessor`
- [ ] T003 [P] Configurar plugin e gate JaCoCo em `build.gradle.kts`: `jacocoTestCoverageVerification` com mínimo 90% de linhas e branches quebrando o build
- [ ] T004 [P] Configurar plugin SonarQube (`org.sonarqube`) e propriedades do projeto em `build.gradle.kts`
- [ ] T005 [P] Adicionar dependências de teste em `build.gradle.kts`: JUnit 5, Testcontainers (postgresql, junit-jupiter), `spring-security-test`, `spring-modulith-starter-test`, `spring-modulith-docs`, WireMock
- [ ] T006 [P] Adicionar `.env` ao `.gitignore` e criar `.env.example` (sem valores) com as variáveis do quickstart.md (DB, APP_JWT_*, GOOGLE_CLIENT_ID, GEMINI_*, MERCADOPAGO_*)
- [ ] T007 [P] Criar `src/main/resources/application.yaml` com perfis `local`/`prod`, datasource, `spring.jpa.hibernate.ddl-auto=validate`, Flyway, actuator e `@ConfigurationProperties` para JWT/Gemini/Mercado Pago lendo do ambiente
- [ ] T008 [P] Criar a estrutura de pacotes dos 8 módulos sob `src/main/java/br/com/treinai/` (`shared`, `identity`, `billing`, `catalog`, `training`, `nutrition`, `aicoach`, `progress`) com `package-info.java` de módulo onde aplicável
- [ ] T009 Criar `src/main/java/br/com/treinai/TreinaiApplication.java` (classe `@SpringBootApplication` principal)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: infraestrutura transversal que TODAS as histórias dependem. Concentra o módulo
`shared`, a base de segurança/JWT do resource server, os tipos ENUM do PostgreSQL e o suporte
de teste (Testcontainers/WireMock). Nenhuma user story pode começar antes desta fase.

**⚠️ CRITICAL**: nenhum trabalho de user story pode iniciar até esta fase estar completa.

- [ ] T010 [P] Implementar Problem Details (RFC 7807) e enum `ErrorCode` (em inglês) em `src/main/java/br/com/treinai/shared/error/` (`UNAUTHENTICATED`, `INVALID_GOOGLE_TOKEN`, `REFRESH_TOKEN_INVALID`, `PROFILE_INCOMPLETE`, `PROFILE_ALREADY_EXISTS`, `QUOTA_EXCEEDED`, `FEATURE_NOT_AVAILABLE`, `AI_UNAVAILABLE`, `AI_RESPONSE_INVALID`, `LIBRARY_INSUFFICIENT`, `NO_CURRENT_TRAINING_PLAN`, `INSUFFICIENT_DATA_FOR_ANALYSIS`, `WEBHOOK_UNKNOWN_SUBSCRIPTION`, `FORBIDDEN_RESOURCE`, `VALIDATION_ERROR`)
- [ ] T011 [P] Implementar `@RestControllerAdvice` global em `src/main/java/br/com/treinai/shared/error/GlobalExceptionHandler.java` mapeando exceções de domínio e validação para Problem Details com campo `errorCode` estável
- [ ] T012 [P] Configurar `ObjectMapper` com `JsonNullableModule` e JavaTime em `src/main/java/br/com/treinai/shared/config/JacksonConfig.java`
- [ ] T013 [P] Configurar springdoc-openapi (info, security scheme Bearer) em `src/main/java/br/com/treinai/shared/config/OpenApiConfig.java`
- [ ] T014 [P] Definir tipos-base de evento de domínio em `src/main/java/br/com/treinai/shared/event/` (interface/record base para eventos Modulith)
- [ ] T015 Implementar `V1__shared_enums.sql` em `src/main/resources/db/migration/` criando todos os tipos ENUM do PostgreSQL (`auth_provider`, `user_status`, `user_role`, `training_goal`, `experience_level`, `sex`, `plan_code`, `entitlement_dimension`, `period_type`, `subscription_status`, `payment_event_type`, `payment_event_status`, `library_plan_status`, `plan_source`, `ai_interaction_kind`, `ai_interaction_status`)
- [ ] T016 Configurar `SecurityFilterChain` base, `JwtDecoder` próprio (resource server validando o JWT da aplicação), method security e regras de rota pública/protegida em `src/main/java/br/com/treinai/shared/config/SecurityConfig.java`
- [ ] T017 [P] Criar classe-base de teste com Testcontainers PostgreSQL (`@ServiceConnection` / container singleton) em `src/test/java/br/com/treinai/support/PostgresIntegrationTest.java`
- [ ] T018 [P] Criar suporte WireMock para stubs externos (Gemini, Mercado Pago, Google JWKs) em `src/test/java/br/com/treinai/support/WireMockSupport.java`
- [ ] T019 [P] Criar `ModulithVerificationTests` em `src/test/java/br/com/treinai/ModulithVerificationTests.java` executando `ApplicationModules.of(TreinaiApplication.class).verify()` e gerando docs via `Documenter`

**Checkpoint**: fundação pronta — implementação de user stories pode começar.

---

## Phase 3: User Story 1 - Receber treino e dieta personalizados gerados por IA (Priority: P1) 🎯 MVP

**Goal**: usuário autentica via Google, cria perfil e gera plano de treino e/ou de dieta via
IA (cotas independentes), com itens restritos às bibliotecas e cota Free de 1 geração de cada
por trimestre.

**Independent Test**: autenticar via Google (stub), salvar perfil, disparar geração de treino
e de dieta (ações separadas), verificar persistência/consulta de cada plano estruturado e
bloqueio por cota na segunda geração do mesmo tipo dentro de 3 meses (Free).

### Tests for User Story 1 ⚠️ (escrever antes da implementação, devem FALHAR primeiro)

- [ ] T020 [P] [US1] Teste de integração de auth (Google login cria/recupera User e retorna tokens) com WireMock de JWKs Google em `src/test/java/br/com/treinai/identity/AuthControllerTest.java`
- [ ] T021 [P] [US1] Teste de integração de refresh/rotação e logout/revogação em `src/test/java/br/com/treinai/identity/RefreshTokenFlowTest.java`
- [ ] T022 [P] [US1] Teste de integração de perfil (POST cria, PATCH parcial com `JsonNullable`, erro se já existe) em `src/test/java/br/com/treinai/identity/ProfileControllerTest.java`
- [ ] T023 [P] [US1] Teste de integração de `CatalogQueryService.validExerciseSlugs()/validFoodSlugs()` em `src/test/java/br/com/treinai/catalog/CatalogQueryServiceTest.java`
- [ ] T024 [P] [US1] Teste de integração do CRUD admin de exercícios e alimentos (role ADMIN; não-admin → `FORBIDDEN_RESOURCE`) em `src/test/java/br/com/treinai/catalog/AdminCatalogControllerTest.java`
- [ ] T025 [P] [US1] Teste unitário de `BillingEntitlementService.check/consume` incluindo concorrência (consumo atômico, `QUOTA_EXCEEDED` no limite) em `src/test/java/br/com/treinai/billing/BillingEntitlementServiceTest.java`
- [ ] T026 [P] [US1] Teste de integração de `GET /api/v1/plans` (planos ativos + entitlements) em `src/test/java/br/com/treinai/billing/PlansControllerTest.java`
- [ ] T027 [P] [US1] Teste de contrato do `GeminiCoachClient` com WireMock (structured output JSON + responseSchema) em `src/test/java/br/com/treinai/aicoach/GeminiCoachClientTest.java`
- [ ] T028 [P] [US1] Teste unitário da validação de itens contra catálogo + re-solicitação limitada (item fora da biblioteca → retry → falha controlada sem plano parcial) em `src/test/java/br/com/treinai/aicoach/AiResponseValidationTest.java`
- [ ] T029 [P] [US1] Teste de integração de auditoria `AIInteraction` (registro em sucesso e em erro, sem PII em log) em `src/test/java/br/com/treinai/aicoach/AIInteractionAuditTest.java`
- [ ] T030 [P] [US1] Teste de integração de `POST /api/v1/training/generate` (caminho dourado persiste plano e consome `TRAINING_GENERATION`; perfil incompleto → `PROFILE_INCOMPLETE`; segunda geração Free no trimestre → `QUOTA_EXCEEDED` sem chamar IA) em `src/test/java/br/com/treinai/training/TrainingGenerateControllerTest.java`
- [ ] T031 [P] [US1] Teste de integração de `POST /api/v1/nutrition/generate` (caminho dourado + cota `NUTRITION_GENERATION` independente) em `src/test/java/br/com/treinai/nutrition/NutritionGenerateControllerTest.java`
- [ ] T032 [P] [US1] Teste de integração ponta-a-ponta do caminho dourado do quickstart (login → perfil → gerar treino e dieta → consultar `current`) em `src/test/java/br/com/treinai/GoldenPathMvpTest.java`

### Implementation — identity (US1)

- [ ] T033 [US1] Migration `V2__identity_init.sql` (tabelas `users`, `user_roles`, `profiles`, `refresh_tokens` com PK UUID v7, enums nativos, índices/uniques do data-model) em `src/main/resources/db/migration/`
- [ ] T034 [P] [US1] Entidades JPA `User`, `UserRole`, `Profile`, `RefreshToken` em `src/main/java/br/com/treinai/identity/internal/domain/`
- [ ] T035 [P] [US1] Repositórios JPA de identity em `src/main/java/br/com/treinai/identity/internal/persistence/`
- [ ] T036 [US1] `SocialIdentityVerifier` (porta) + `GoogleIdentityVerifier` com `NimbusJwtDecoder` dedicado a JWKs Google (issuer/audience) em `src/main/java/br/com/treinai/identity/internal/auth/`
- [ ] T037 [US1] Configuração de emissão de JWT próprio: `NimbusJwtEncoder` com par RSA carregado de env, `kid` fixo, beans `JwtDecoder` qualificados (Google vs próprio) em `src/main/java/br/com/treinai/identity/internal/auth/JwtConfig.java`
- [ ] T038 [US1] `RefreshTokenService` (geração com hash, rotação a cada uso, revogação no logout) em `src/main/java/br/com/treinai/identity/internal/auth/`
- [ ] T039 [US1] `AuthService` (login Google cria/recupera User + emite par de tokens; refresh; logout) em `src/main/java/br/com/treinai/identity/internal/`
- [ ] T040 [US1] `ProfileService` (criar, atualização parcial com `JsonNullable`, leitura) em `src/main/java/br/com/treinai/identity/internal/`
- [ ] T041 [P] [US1] DTOs `record` (`*Request`/`*Response`) e mappers MapStruct de identity em `src/main/java/br/com/treinai/identity/internal/web/`
- [ ] T042 [US1] `AuthController` (`POST /auth/google`, `/auth/refresh`, `/auth/logout`) e `MeController` (`GET /me`, `POST /me/profile`, `PATCH /me/profile`) em `src/main/java/br/com/treinai/identity/internal/web/`

### Implementation — catalog (US1)

- [ ] T043 [US1] Migration `V3__catalog_init.sql` (`exercise_library_items`, `food_library_items`, `movement_vocabulary`, `meal_vocabulary`) + seed inicial mínimo de itens/vocabulários em `src/main/resources/db/migration/`
- [ ] T044 [P] [US1] Entidades JPA `ExerciseLibraryItem`, `FoodLibraryItem`, `MovementVocabulary`, `MealVocabulary` em `src/main/java/br/com/treinai/catalog/internal/domain/`
- [ ] T045 [P] [US1] Repositórios JPA de catalog em `src/main/java/br/com/treinai/catalog/internal/persistence/`
- [ ] T046 [US1] `CatalogQueryService` (API pública: `validExerciseSlugs()`, `validFoodSlugs()`, busca de itens por slug) no pacote-raiz `src/main/java/br/com/treinai/catalog/`
- [ ] T047 [P] [US1] DTOs + mappers MapStruct de catalog em `src/main/java/br/com/treinai/catalog/internal/web/`
- [ ] T048 [US1] Controllers admin de catálogo (`/api/v1/admin/exercises`, `/api/v1/admin/foods`, vocabulários) com GET/POST/PATCH/DELETE e autorização ADMIN em `src/main/java/br/com/treinai/catalog/internal/web/`

### Implementation — billing entitlements (US1)

- [ ] T049 [US1] Migration `V4__billing_entitlements_init.sql` (`plans`, `entitlements`, `quota_usage`) + seed dos planos Free/Plus/Premium e entitlements por dimensão/período conforme mapa de cotas em `src/main/resources/db/migration/`
- [ ] T050 [P] [US1] Entidades JPA `Plan`, `Entitlement`, `QuotaUsage` em `src/main/java/br/com/treinai/billing/internal/domain/`
- [ ] T051 [P] [US1] Repositórios JPA de billing (entitlements/quota) em `src/main/java/br/com/treinai/billing/internal/persistence/`
- [ ] T052 [US1] `BillingEntitlementService` (API pública: `check(userId, dimension)` e `consume(userId, dimension)` com lock pessimista/`UPDATE ... WHERE used < limit`, janela de período determinística ancorada na assinatura/usuário; consumo só efetiva em sucesso) no pacote-raiz `src/main/java/br/com/treinai/billing/`
- [ ] T053 [P] [US1] DTOs + mapper de planos e `PlansController` (`GET /api/v1/plans`) em `src/main/java/br/com/treinai/billing/internal/web/`

### Implementation — aicoach core (US1)

- [ ] T054 [US1] Migration `V5__aicoach_init.sql` (`ai_interactions`, `ai_analyses`) em `src/main/resources/db/migration/`
- [ ] T055 [P] [US1] Entidades JPA `AIInteraction`, `AIAnalysis` + repositórios em `src/main/java/br/com/treinai/aicoach/internal/domain/` e `.../persistence/`
- [ ] T056 [US1] Porta `AiCoachClient` + adapter `GeminiCoachClient` (`@FeignClient` contra a REST do Gemini com `responseMimeType=application/json` + `responseSchema`, timeout/backoff) em `src/main/java/br/com/treinai/aicoach/internal/gemini/`
- [ ] T057 [US1] Builders de prompt de geração (treino e dieta) declarando explicitamente a restrição de usar apenas itens das bibliotecas e injetando slugs válidos do `CatalogQueryService` em `src/main/java/br/com/treinai/aicoach/internal/gemini/prompt/`
- [ ] T058 [US1] Validação da resposta contra o catálogo + re-solicitação limitada (N tentativas, default 2) com falha controlada sem plano parcial (`AI_RESPONSE_INVALID`/`LIBRARY_INSUFFICIENT`) em `src/main/java/br/com/treinai/aicoach/internal/`
- [ ] T059 [US1] Serviço de auditoria que grava `AIInteraction` em toda chamada (sucesso e erro: prompt, resposta, tokens, custo estimado, latência, status) e publica evento `AIInteractionRecorded`; sem PII em log em `src/main/java/br/com/treinai/aicoach/internal/`
- [ ] T060 [US1] `AiCoachService` (API pública: `generateTrainingPlan(...)`, `generateNutritionPlan(...)` retornando estrutura validada) no pacote-raiz `src/main/java/br/com/treinai/aicoach/` + evento `AIInteractionRecorded` em `.../aicoach/`
- [ ] T061 [US1] Resiliência/tier Free: degradação graciosa em indisponibilidade/limite (`AI_UNAVAILABLE`) garantindo NÃO consumir cota em `src/main/java/br/com/treinai/aicoach/internal/`

### Implementation — training generate (US1)

- [ ] T062 [US1] Migration `V6__training_plans_init.sql` (`training_plans`, `training_divisions`, `training_exercises`; FKs internas; refs por ID a catalog/aicoach sem FK cruzada) em `src/main/resources/db/migration/`
- [ ] T063 [P] [US1] Entidades JPA `TrainingPlan`, `TrainingDivision`, `TrainingExercise` + repositórios em `src/main/java/br/com/treinai/training/internal/domain/` e `.../persistence/`
- [ ] T064 [US1] `TrainingPlanService.generate` (valida perfil → `check` cota → chama `AiCoachService` → persiste plano estruturado + versiona CURRENT/ARCHIVED → `consume` cota; em falha de IA não persiste nem consome) em `src/main/java/br/com/treinai/training/internal/`
- [ ] T065 [P] [US1] DTOs + mappers MapStruct de training (plano) em `src/main/java/br/com/treinai/training/internal/web/`
- [ ] T066 [US1] `TrainingController` (`POST /training/generate`, `GET /training/current`, `GET /training/history`) com filtro por `userId` do token e evento `TrainingPlanGenerated` em `src/main/java/br/com/treinai/training/internal/web/` e `src/main/java/br/com/treinai/training/`

### Implementation — nutrition generate (US1)

- [ ] T067 [US1] Migration `V7__nutrition_init.sql` (`nutrition_plans`, `meals`, `meal_items`; refs por ID a catalog/aicoach sem FK cruzada) em `src/main/resources/db/migration/`
- [ ] T068 [P] [US1] Entidades JPA `NutritionPlan`, `Meal`, `MealItem` + repositórios em `src/main/java/br/com/treinai/nutrition/internal/domain/` e `.../persistence/`
- [ ] T069 [US1] `NutritionPlanService.generate` (valida perfil → `check` cota `NUTRITION_GENERATION` → chama `AiCoachService` → persiste dieta estruturada + totais/macros + versiona → `consume`) em `src/main/java/br/com/treinai/nutrition/internal/`
- [ ] T070 [P] [US1] DTOs + mappers MapStruct de nutrition em `src/main/java/br/com/treinai/nutrition/internal/web/`
- [ ] T071 [US1] `NutritionController` (`POST /nutrition/generate`, `GET /nutrition/current`, `GET /nutrition/history`) + evento `NutritionPlanGenerated` em `src/main/java/br/com/treinai/nutrition/internal/web/` e `src/main/java/br/com/treinai/nutrition/`

**Checkpoint**: US1 completa e testável de forma independente — MVP demonstrável.

---

## Phase 4: User Story 2 - Assinar plano pago e desbloquear cotas maiores (Priority: P2)

**Goal**: usuário assina Plus/Premium via Mercado Pago (PIX/cartão); webhooks idempotentes
ativam/suspendem/cancelam a assinatura e ajustam entitlements.

**Independent Test**: iniciar assinatura → `PENDING`; simular webhook aprovado → `ACTIVE` com
cotas do plano; simular webhook recusado/cancelamento → suspensão/cancelamento (volta a Free);
reprocessar o mesmo webhook não altera estado.

### Tests for User Story 2 ⚠️

- [ ] T072 [P] [US2] Teste de integração `POST /api/v1/me/subscription` cria assinatura `PENDING` + dados de pagamento (WireMock/SDK Mercado Pago) em `src/test/java/br/com/treinai/billing/SubscriptionControllerTest.java`
- [ ] T073 [P] [US2] Teste de integração de webhook aprovado → `ACTIVE` e entitlements do plano aplicados em `src/test/java/br/com/treinai/billing/WebhookApprovedTest.java`
- [ ] T074 [P] [US2] Teste de integração de webhook recusado/cancelamento → suspensão/cancelamento e cotas de volta ao Free em `src/test/java/br/com/treinai/billing/WebhookCancelSuspendTest.java`
- [ ] T075 [P] [US2] Teste de integração de idempotência (reprocessar o mesmo `externalId` não altera estado nem duplica efeito) em `src/test/java/br/com/treinai/billing/WebhookIdempotencyTest.java`
- [ ] T076 [P] [US2] Teste de integração de webhook de assinatura desconhecida → `WEBHOOK_UNKNOWN_SUBSCRIPTION` rejeitado e auditado sem mudar estado em `src/test/java/br/com/treinai/billing/WebhookUnknownSubscriptionTest.java`

### Implementation for User Story 2

- [ ] T077 [US2] Migration `V8__billing_subscriptions_init.sql` (`subscriptions`, `payment_events`; unique `external_id`; unique parcial uma `ACTIVE` por usuário) em `src/main/resources/db/migration/`
- [ ] T078 [P] [US2] Entidades JPA `Subscription`, `PaymentEvent` + repositórios em `src/main/java/br/com/treinai/billing/internal/domain/` e `.../persistence/`
- [ ] T079 [US2] Porta `PaymentGateway` + adapter `MercadoPagoGateway` (SDK oficial; criação de assinatura/preferência PIX e cartão; validação de assinatura do webhook) em `src/main/java/br/com/treinai/billing/internal/payment/`
- [ ] T080 [US2] `SubscriptionService` com máquina de estados `PENDING → ACTIVE → (SUSPENDED|CANCELLED)`, ajuste de entitlements ao mudar de estado e publicação dos eventos `SubscriptionActivated/Suspended/Cancelled` em `src/main/java/br/com/treinai/billing/internal/`
- [ ] T081 [US2] Processamento idempotente de webhook (consulta/cria `PaymentEvent` por `externalId`; assinatura desconhecida → rejeitada+auditada) em `src/main/java/br/com/treinai/billing/internal/payment/`
- [ ] T082 [P] [US2] Eventos de domínio `SubscriptionActivated`, `SubscriptionSuspended`, `SubscriptionCancelled` no pacote-raiz `src/main/java/br/com/treinai/billing/`
- [ ] T083 [P] [US2] DTOs + mappers de assinatura em `src/main/java/br/com/treinai/billing/internal/web/`
- [ ] T084 [US2] `SubscriptionController` (`GET /me/subscription`, `POST /me/subscription`, `POST /me/subscription/cancel`) e `MercadoPagoWebhookController` (`POST /billing/webhooks/mercadopago`, público assinado) em `src/main/java/br/com/treinai/billing/internal/web/`

**Checkpoint**: US1 e US2 funcionam de forma independente.

---

## Phase 5: User Story 3 - Registrar a execução dos treinos (Priority: P2)

**Goal**: registrar execução de sessão de treino (séries reais, carga, RPE, concluído/pulado,
duração, observações), livre para todos os planos e sem consumir cota; consultar histórico.

**Independent Test**: para usuário com plano de treino vigente, registrar sessão completa,
verificar persistência vinculada a plano/usuário e consulta cronológica — funcionando no Free
sem consumir cota.

### Tests for User Story 3 ⚠️

- [ ] T085 [P] [US3] Teste de integração `POST /api/v1/training/sessions` persiste sessão (Free, sem consumir cota) em `src/test/java/br/com/treinai/training/WorkoutSessionControllerTest.java`
- [ ] T086 [P] [US3] Teste de integração de histórico `GET /training/sessions` (ordem cronológica) e detalhe `GET /training/sessions/{id}` em `src/test/java/br/com/treinai/training/WorkoutSessionHistoryTest.java`
- [ ] T087 [P] [US3] Teste de integração de exercício pulado (registro reflete não executado, preserva observação) e sessão sem plano vigente → `NO_CURRENT_TRAINING_PLAN` em `src/test/java/br/com/treinai/training/WorkoutSessionEdgeCasesTest.java`

### Implementation for User Story 3

- [ ] T088 [US3] Migration `V9__training_sessions_init.sql` (`workout_sessions`, `workout_session_exercises`, `workout_session_exercise_sets`; FKs internas a divisão/exercício) em `src/main/resources/db/migration/`
- [ ] T089 [P] [US3] Entidades JPA `WorkoutSession`, `WorkoutSessionExercise`, `WorkoutSessionExerciseSet` + repositórios em `src/main/java/br/com/treinai/training/internal/domain/` e `.../persistence/`
- [ ] T090 [US3] `WorkoutSessionService` (registra execução vinculada à divisão/usuário, sem chamar billing; exige plano vigente) em `src/main/java/br/com/treinai/training/internal/`
- [ ] T091 [P] [US3] DTOs + mappers de sessão de treino em `src/main/java/br/com/treinai/training/internal/web/`
- [ ] T092 [US3] Endpoints no `TrainingController` (`POST /training/sessions`, `GET /training/sessions`, `GET /training/sessions/{id}`) com filtro por `userId` em `src/main/java/br/com/treinai/training/internal/web/`
- [ ] T093 [US3] `WorkoutHistoryQuery` (API pública para insumo de ajustes/análise) no pacote-raiz `src/main/java/br/com/treinai/training/`

**Checkpoint**: US1, US2 e US3 funcionam de forma independente.

---

## Phase 6: User Story 5 - Acompanhar evolução biométrica (Priority: P3)

**Goal**: registrar biometria (peso e medidas com data) e consultar histórico/evolução,
disponível a todos os planos. (Implementada antes de US4/US6 por fornecer o `ProgressQuery`
que ambas consomem.)

**Independent Test**: registrar múltiplas medições em datas distintas e verificar histórico
ordenado cronologicamente.

### Tests for User Story 5 ⚠️

- [ ] T094 [P] [US5] Teste de integração `POST /api/v1/progress/entries` persiste biometria associada ao usuário em `src/test/java/br/com/treinai/progress/ProgressControllerTest.java`
- [ ] T095 [P] [US5] Teste de integração `GET /progress/entries` (cronológico) e `PATCH /progress/entries/{id}` (parcial com `JsonNullable`) em `src/test/java/br/com/treinai/progress/ProgressHistoryTest.java`

### Implementation for User Story 5

- [ ] T096 [US5] Migration `V10__progress_init.sql` (`progress_entries` com `measurements` jsonb; índice `(user_id, measured_at)`) em `src/main/resources/db/migration/`
- [ ] T097 [P] [US5] Entidade JPA `ProgressEntry` + repositório em `src/main/java/br/com/treinai/progress/internal/domain/` e `.../persistence/`
- [ ] T098 [US5] `ProgressService` (registrar, atualização parcial, consultar histórico) em `src/main/java/br/com/treinai/progress/internal/`
- [ ] T099 [P] [US5] DTOs + mappers de progress em `src/main/java/br/com/treinai/progress/internal/web/`
- [ ] T100 [US5] `ProgressController` (`POST/GET/PATCH /progress/entries`) com filtro por `userId` em `src/main/java/br/com/treinai/progress/internal/web/`
- [ ] T101 [US5] `ProgressQuery` (API pública para insumo de ajustes/análise) no pacote-raiz `src/main/java/br/com/treinai/progress/`

**Checkpoint**: US1, US2, US3 e US5 independentes.

---

## Phase 7: User Story 4 - Ajustar treino e dieta com base na execução e no progresso (Priority: P3)

**Goal**: usuário Plus/Premium ajusta treino e/ou dieta; a IA considera execução (US3) e
progresso (US5); consome cota de ajuste por dimensão; mantém versão anterior no histórico.

**Independent Test**: com cota de ajuste, execução e progresso, solicitar ajuste e verificar
novo plano persistido, anterior arquivado e cota consumida; bloqueio quando a cota esgota.

### Tests for User Story 4 ⚠️

- [ ] T102 [P] [US4] Teste de integração `POST /api/v1/training/adjust` (Plus consome `TRAINING_ADJUSTMENT`, arquiva anterior, usa execução+progresso; cota esgotada → `QUOTA_EXCEEDED`) em `src/test/java/br/com/treinai/training/TrainingAdjustControllerTest.java`
- [ ] T103 [P] [US4] Teste de integração `POST /api/v1/nutrition/adjust` (cota `NUTRITION_ADJUSTMENT` independente) em `src/test/java/br/com/treinai/nutrition/NutritionAdjustControllerTest.java`
- [ ] T104 [P] [US4] Teste de integração de ajustes ilimitados no Premium em `src/test/java/br/com/treinai/training/AdjustUnlimitedPremiumTest.java`

### Implementation for User Story 4

- [ ] T105 [US4] Builders de prompt de ajuste (treino e dieta) e métodos `AiCoachService.adjustTrainingPlan(...)`/`adjustNutritionPlan(...)` consumindo `WorkoutHistoryQuery` e `ProgressQuery` em `src/main/java/br/com/treinai/aicoach/internal/gemini/prompt/` e pacote-raiz `aicoach/`
- [ ] T106 [US4] `TrainingPlanService.adjust` (check/consume `TRAINING_ADJUSTMENT`, chama IA, arquiva plano anterior, persiste nova versão `ADJUSTED`) em `src/main/java/br/com/treinai/training/internal/`
- [ ] T107 [US4] Endpoint `POST /training/adjust` no `TrainingController` em `src/main/java/br/com/treinai/training/internal/web/`
- [ ] T108 [US4] `NutritionPlanService.adjust` (check/consume `NUTRITION_ADJUSTMENT`, arquiva anterior, persiste versão `ADJUSTED`) em `src/main/java/br/com/treinai/nutrition/internal/`
- [ ] T109 [US4] Endpoint `POST /nutrition/adjust` no `NutritionController` em `src/main/java/br/com/treinai/nutrition/internal/web/`

**Checkpoint**: US1–US5 independentes; ajustes operacionais.

---

## Phase 8: User Story 6 - Análise e feedback por IA sob demanda (Priority: P3)

**Goal**: usuário Premium solicita análise/feedback da IA com base em execução e progresso;
exclusivo Premium e ilimitado (`AI_ANALYSIS`); Free/Plus recusados.

**Independent Test**: Premium com execução e progresso → análise estruturada retornada e
auditada; Free/Plus → `FEATURE_NOT_AVAILABLE`; sem dados → `INSUFFICIENT_DATA_FOR_ANALYSIS`.

### Tests for User Story 6 ⚠️

- [ ] T110 [P] [US6] Teste de integração `POST /api/v1/ai/analysis` (Premium retorna feedback estruturado e registra `AIInteraction`) em `src/test/java/br/com/treinai/aicoach/AiAnalysisControllerTest.java`
- [ ] T111 [P] [US6] Teste de integração Free/Plus → `FEATURE_NOT_AVAILABLE` e dados insuficientes → `INSUFFICIENT_DATA_FOR_ANALYSIS` (sem chamar Gemini) em `src/test/java/br/com/treinai/aicoach/AiAnalysisGatingTest.java`
- [ ] T112 [P] [US6] Teste de integração `GET /api/v1/ai/analysis/{id}` recupera análise gerada (somente do próprio usuário) em `src/test/java/br/com/treinai/aicoach/AiAnalysisRetrievalTest.java`

### Implementation for User Story 6

- [ ] T113 [US6] Builder de prompt de análise + `AiCoachService.analyze(...)` consumindo `WorkoutHistoryQuery`/`ProgressQuery`, gatilho de entitlement `AI_ANALYSIS`, persistência de `AIAnalysis` em `src/main/java/br/com/treinai/aicoach/internal/`
- [ ] T114 [P] [US6] DTOs + mappers de análise em `src/main/java/br/com/treinai/aicoach/internal/web/`
- [ ] T115 [US6] `AiAnalysisController` (`POST /ai/analysis`, `GET /ai/analysis/{id}`) com filtro por `userId` em `src/main/java/br/com/treinai/aicoach/internal/web/`

**Checkpoint**: US1–US6 independentes.

---

## Phase 9: User Story 7 - Rastreabilidade e auditoria das interações de IA (Priority: P4)

**Goal**: cada chamada de IA (geração, ajuste, análise) produz exatamente um registro de
auditoria completo e consultável, inclusive em erro; admin pode visualizar.

**Independent Test**: disparar geração, ajuste e análise e verificar exatamente um registro
de auditoria completo por chamada (incl. erro); admin lista as interações.

### Tests for User Story 7 ⚠️

- [ ] T116 [P] [US7] Teste de integração verificando que geração/ajuste/análise produzem exatamente uma `AIInteraction` completa cada (incl. caminho de erro com status `ERROR`) em `src/test/java/br/com/treinai/aicoach/AIInteractionCompletenessTest.java`
- [ ] T117 [P] [US7] Teste de integração `GET /api/v1/admin/ai-interactions` (somente ADMIN; não-admin → `FORBIDDEN_RESOURCE`) em `src/test/java/br/com/treinai/aicoach/AdminAiInteractionsControllerTest.java`

### Implementation for User Story 7

- [ ] T118 [US7] `AdminAiInteractionsController` (`GET /api/v1/admin/ai-interactions`, role ADMIN, paginado, sem expor PII em log) em `src/main/java/br/com/treinai/aicoach/internal/web/`
- [ ] T119 [US7] Consumidor do evento `AIInteractionRecorded` para métrica de custo/uso de IA em `src/main/java/br/com/treinai/aicoach/internal/`

**Checkpoint**: todas as histórias independentes e auditadas.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: melhorias transversais a múltiplas histórias.

- [ ] T120 [P] Métricas de negócio custom (Micrometer): `ai_generations_total`, `ai_adjustments_total`, `subscription_activations_total`, `quota_rejections_total`, latência Gemini em `src/main/java/br/com/treinai/shared/observability/`
- [ ] T121 [P] Endurecer actuator: `health`/`info` públicos, `prometheus`/`metrics` protegidos em `src/main/resources/application.yaml` e `SecurityConfig`
- [ ] T122 [P] Revisar política de logging sem PII (biometria/prompts apenas em banco) — SC-007
- [ ] T123 [P] Revisar anotações OpenAPI/springdoc dos controllers e exemplos de Problem Details
- [ ] T124 Executar `./gradlew test jacocoTestCoverageVerification` e garantir gate ≥90% linhas e branches
- [ ] T125 Executar e validar `ModulithVerificationTests` (fronteiras de módulo) e gerar docs do Modulith
- [ ] T126 Validar o fluxo do `quickstart.md` (caminho dourado US1) ponta-a-ponta localmente

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências — inicia imediatamente.
- **Foundational (Phase 2)**: depende do Setup — BLOQUEIA todas as user stories.
- **User Stories (Phases 3–9)**: dependem da Foundational.
  - US1 (P1) é o MVP e não depende de outras histórias.
  - US2 (P2) e US3 (P2) dependem da Foundational; reutilizam billing/training de US1 mas são testáveis de forma independente.
  - US5 (P3) é implementada antes de US4/US6 por fornecer `ProgressQuery`.
  - US4 (P3) consome `WorkoutHistoryQuery` (US3) e `ProgressQuery` (US5).
  - US6 (P3) consome `WorkoutHistoryQuery` (US3) e `ProgressQuery` (US5).
  - US7 (P4) consolida auditoria de IA (a criação de `AIInteraction` já nasce em US1).
- **Polish (Phase 10)**: depois das histórias desejadas.

### Within Each User Story

- Testes escritos e FALHANDO antes da implementação (TDD, Princípio II).
- Migration → entidades → repositórios → serviços → controllers.
- Plano/serviço completos antes de integração entre módulos.

### Parallel Opportunities

- Todas as tarefas `[P]` do Setup podem rodar em paralelo.
- Tarefas `[P]` da Foundational (T010–T014, T017–T019) podem rodar em paralelo.
- Concluída a Foundational, blocos de US1 por módulo (identity, catalog, billing, aicoach) podem ser desenvolvidos em paralelo por pessoas diferentes; training/nutrition generate dependem de catalog+billing+aicoach.
- Todos os testes `[P]` de uma mesma história podem ser escritos em paralelo.
- US2, US3 e US5 podem ser tocadas em paralelo após a Foundational.

---

## Parallel Example: User Story 1 (testes primeiro)

```bash
# Escrever em paralelo os testes da US1 (devem falhar antes da implementação):
Task: "Teste de auth Google em identity/AuthControllerTest.java"
Task: "Teste de CatalogQueryService em catalog/CatalogQueryServiceTest.java"
Task: "Teste de BillingEntitlementService (concorrência) em billing/BillingEntitlementServiceTest.java"
Task: "Teste de contrato GeminiCoachClient (WireMock) em aicoach/GeminiCoachClientTest.java"
Task: "Teste de POST /training/generate em training/TrainingGenerateControllerTest.java"

# Entidades de módulos diferentes em paralelo:
Task: "Entidades identity (User, Profile, RefreshToken, UserRole)"
Task: "Entidades catalog (ExerciseLibraryItem, FoodLibraryItem, vocabulários)"
Task: "Entidades billing (Plan, Entitlement, QuotaUsage)"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup.
2. Phase 2: Foundational (CRÍTICO — bloqueia tudo).
3. Phase 3: User Story 1 (auth + perfil + catálogo + entitlements + Gemini + geração de treino/dieta).
4. **PARAR e VALIDAR**: testar US1 de forma independente (caminho dourado do quickstart).
5. Deploy/demo do MVP.

### Incremental Delivery

1. Setup + Foundational → fundação pronta.
2. US1 → testar → demo (MVP).
3. US2 (monetização) → testar → demo.
4. US3 (registro de execução) → testar → demo.
5. US5 → US4 → US6 → US7 → cada um agrega valor sem quebrar os anteriores.
6. Polish.

### Parallel Team Strategy

Após a Foundational: desenvolvedores podem dividir US1 por módulo (identity / catalog / billing
/ aicoach) e, em seguida, US2 / US3 / US5 em paralelo, integrando de forma independente.

---

## Notes

- `[P]` = arquivos diferentes, sem dependências entre si.
- `[Story]` mapeia a tarefa à user story para rastreabilidade.
- Testes de integração usam Testcontainers (PostgreSQL real); WireMock para Gemini/Mercado Pago/Google JWKs. H2 e mocks de repositório são proibidos.
- Criação via POST, atualização via PATCH (`JsonNullable`); PUT nunca é usado.
- Erros em RFC 7807 com `errorCode` estável em inglês; código/commits/logs em inglês.
- Verificar que os testes falham antes de implementar; commit após cada tarefa ou grupo lógico.
