# Implementation Plan: Treinaí — Backend MVP

**Branch**: `001-treinai-backend-mvp` | **Date**: 2026-05-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-treinai-backend-mvp/spec.md`

## Summary

Back-end de um SaaS B2C que gera planos de treino e dieta via coach de IA (Google
Gemini), com registro de execução, acompanhamento biométrico, análise por IA sob
demanda e assinatura paga (Mercado Pago). A solução é um **monólito modular Spring
Modulith** (Java 25 / Spring Boot 4.0.6, MVC servlet, PostgreSQL + Flyway), com
sete módulos de negócio (`identity`, `billing`, `catalog`, `training`, `nutrition`,
`aicoach`, `progress`) mais `shared`. Comunicação entre módulos por eventos de
domínio do Modulith, com chamadas síncronas via API pública apenas para
autorização de cota e validação de catálogo. Autenticação: valida ID token Google
e emite JWT próprio via `NimbusJwtEncoder`, validado pelo resource server.
Detalhes técnicos em [research.md](./research.md); modelo em
[data-model.md](./data-model.md); contratos em [contracts/rest-api.md](./contracts/rest-api.md).

## Technical Context

**Language/Version**: Java 25 (toolchain Gradle).

**Primary Dependencies**: Spring Boot 4.0.6 (`webmvc`, `data-jpa`, `validation`,
`actuator`, `security`, `oauth2-resource-server`, **`oauth2-client`**), Spring
Modulith (core + jpa + events-jpa), Flyway, PostgreSQL driver, springdoc-openapi,
Micrometer/Prometheus, Lombok, **MapStruct** + `lombok-mapstruct-binding`,
**`org.openapitools:jackson-databind-nullable`**, Nimbus JOSE (via oauth2),
**Spring Cloud OpenFeign** (cliente Gemini), **SDK oficial Mercado Pago**
(`com.mercadopago:sdk-java`).

**Storage**: PostgreSQL (Spring Data JPA). Migrations via Flyway
(`ddl-auto=validate`). Sem FK cruzando módulos (referência por ID).

**Testing**: JUnit 5, Testcontainers (PostgreSQL real), `spring-security-test`,
WireMock (stubs Gemini/Mercado Pago/Google JWKs), MockMvc/TestRestTemplate para
integração de API. JaCoCo (gate 90% linhas e branches). SonarCloud Quality Gate.

**Target Platform**: Linux server containerizado (Docker), stateless. Free tier
EC2 t3.micro + RDS db.t3.micro; evolução ECS Fargate/App Runner sem mudança de
código.

**Project Type**: Web service (back-end único, monólito modular).

**Performance Goals**: MVP sem SLA rígido; chamadas Gemini com timeout + backoff;
verificação de cota O(1) com lock transacional. Foco em correção e auditoria, não
em throughput.

**Constraints**: Tier Free Gemini (rate limits → degradação graciosa, sem
consumir cota); idempotência de webhooks; concorrência de cota; sem PII em logs;
segredos fora do repo (`.env` local + Secrets Manager em prod).

**Scale/Scope**: MVP B2C, baixo volume inicial. 7 módulos de negócio + shared,
~16 entidades principais, ~30 endpoints REST.

## Constitution Check

*GATE: avaliado antes da Fase 0 e re-avaliado após a Fase 1. Sem violações.*

| Princípio | Status | Como o plano cumpre |
|-----------|--------|---------------------|
| I. Fronteiras de Módulo | PASS | Spring Modulith, um módulo por bounded context, API pública no pacote-raiz, `internal/` privado, `ApplicationModules.verify()` no CI, sem FK cruzada. Chamada síncrona só p/ autorização de cota e validação de catálogo (resposta imediata exigida) — uso explicitamente permitido. |
| II. TDD (não-negociável) | PASS | Camadas de teste definidas (unit, integração Testcontainers, integração de API, fronteira de módulo, contrato externo via WireMock). H2/mocks de banco proibidos. |
| III. Cobertura e Qualidade | PASS | JaCoCo ≥ 90% linhas+branches quebrando build; SonarCloud Quality Gate verde no CI. |
| IV. Clean Code e Design | PASS | DTOs `record` com sufixos `Request`/`Response`; entidades JPA nunca expostas; MapStruct para conversões; PATCH+`JsonNullable`; sem regra de negócio em controllers. |
| V. API e Contratos | PASS | `/api/v1`, Problem Details (RFC 7807) com `errorCode` estável em inglês, springdoc. |
| VI. Idioma | PASS | Código/commits/logs em inglês; specs e plano em pt-BR. |
| VII. Observabilidade | PASS | `AIInteraction` audita toda chamada de IA; Micrometer/Prometheus; logs estruturados sem PII. |
| VIII. Segurança | PASS | Segredos em `.env` (gitignored) + `.env.example`; menor privilégio (roles + entitlement por plano); validação em todas as fronteiras. **Ação pendente**: incluir `.env` no `.gitignore`. |

**Restrições Técnicas Imutáveis**: todas respeitadas (Java 25, Spring Boot 4.0.6
MVC, PostgreSQL+Flyway `validate`, Spring Modulith, OAuth2 + JWT próprio, Gemini,
Micrometer/Prometheus, Mercado Pago, Gradle Kotlin DSL + Lombok, JUnit 5 +
Testcontainers, Docker stateless + GitHub Actions).

## Project Structure

### Documentation (this feature)

```text
specs/001-treinai-backend-mvp/
├── plan.md              # Este arquivo
├── research.md          # Fase 0 — decisões técnicas
├── data-model.md        # Fase 1 — entidades, tabelas, eventos
├── quickstart.md        # Fase 1 — setup e validação local
├── contracts/
│   └── rest-api.md      # Fase 1 — contratos REST por módulo
└── tasks.md             # Fase 2 — gerado por /speckit-tasks (NÃO criado aqui)
```

### Source Code (repository root)

```text
src/main/java/br/com/treinai/
├── TreinaiApplication.java
├── shared/                       # módulo transversal "folha" (sem dep de negócio)
│   ├── error/                    # ProblemDetails, @RestControllerAdvice, errorCodes
│   ├── event/                    # tipos-base de eventos de domínio
│   └── config/                   # ObjectMapper (JsonNullableModule), OpenAPI, security base
├── identity/                     # API pública: serviços, DTOs, eventos
│   └── internal/
│       ├── domain/               # User, Profile, RefreshToken, UserRole
│       ├── persistence/          # repositórios JPA
│       ├── auth/                 # GoogleIdentityVerifier, JwtEncoder/Decoder config
│       ├── web/                  # controllers + mappers MapStruct
│       └── config/
├── billing/
│   └── internal/{domain,persistence,payment,web,config}
│       # Plan, Entitlement, Subscription, QuotaUsage, PaymentEvent
│       # BillingEntitlementService (API pública), MercadoPagoGateway, webhook
├── catalog/
│   └── internal/{domain,persistence,web,config}
│       # ExerciseLibraryItem, FoodLibraryItem, MovementVocabulary, MealVocabulary; CatalogQueryService
├── training/
│   └── internal/{domain,persistence,web,config}
│       # TrainingPlan, Division, Exercise, WorkoutSession(+Exercise,+ExerciseSet)
├── nutrition/
│   └── internal/{domain,persistence,web,config}
│       # NutritionPlan, Meal, MealItem
├── aicoach/
│   └── internal/{domain,persistence,gemini,web,config}
│       # AIInteraction, AIAnalysis; AiCoachClient (porta) + GeminiCoachClient (adapter)
└── progress/
    └── internal/{domain,persistence,web,config}
        # ProgressEntry

src/main/resources/
├── application.yaml              # config base (perfis: local, prod)
└── db/migration/                 # V1__identity_init.sql, V2__billing_init.sql, ...

src/test/java/br/com/treinai/
├── ModulithVerificationTests.java     # ApplicationModules.verify() + docs
├── <module>/                          # unit + integração por módulo
└── support/                           # Testcontainers config, WireMock stubs
```

**Structure Decision**: Web service único (monólito modular). Um pacote por módulo
sob `br.com.treinai`, API pública no pacote-raiz e implementação em `internal/`
(subpacotes `domain`/`persistence`/`web`/`config` e adapters específicos). O módulo
`shared` é folha (não depende de módulos de negócio) e concentra error handling,
config Jackson/OpenAPI/security base e tipos-base de evento. Migrations Flyway num
diretório único com prefixo de módulo no nome para ordem global determinística.

## Build & dependency adjustments (`build.gradle.kts`)

Adições necessárias (ver R10):

- **BOM Spring Modulith** + `spring-modulith-starter-core`,
  `spring-modulith-starter-jpa`, `spring-modulith-events-jpa`; teste:
  `spring-modulith-starter-test`, `spring-modulith-docs`.
- `spring-boot-starter-oauth2-client` (validação do ID token Google via JWKs).
- **MapStruct**: `org.mapstruct:mapstruct` + `org.mapstruct:mapstruct-processor`;
  `org.projectlombok:lombok-mapstruct-binding`. **Ordem dos annotation processors**:
  `lombok` → `lombok-mapstruct-binding` → `mapstruct` (e o mesmo para
  `testAnnotationProcessor`).
- `org.openapitools:jackson-databind-nullable` (+ registrar `JsonNullableModule`).
- **Spring Cloud OpenFeign** (`spring-cloud-starter-openfeign`) para o cliente
  Gemini — atenção ao alinhamento de versão do Spring Cloud com Spring Boot 4.
- **SDK oficial Mercado Pago** (`com.mercadopago:sdk-java`).
- **JaCoCo** plugin + `jacocoTestCoverageVerification` com gate 90% (linhas e
  branches) quebrando o build.
- Plugin **SonarQube** (`org.sonarqube`).
- **WireMock** (`org.wiremock:wiremock-standalone` ou
  `spring-cloud-contract-wiremock`) para stubs de contrato em teste.
- Nimbus JWT: já disponível via `oauth2-resource-server` — **não** adicionar
  `oauth2-authorization-server`.

## Deliverables (entregáveis do plano)

- **Estrutura de pacotes/módulos**: seção *Project Structure* acima.
- **Entidades por módulo e tabelas**: [data-model.md](./data-model.md).
- **Eventos de domínio do Modulith (publica/consome)**: tabela em
  [data-model.md](./data-model.md#eventos-de-domínio-spring-modulith).
- **Endpoints REST por módulo**: [contracts/rest-api.md](./contracts/rest-api.md).
- **Ajustes no build + ordem de annotation processors**: seção acima.
- **Riscos e pontos de atenção**: tabela em
  [research.md](./research.md#riscos-e-pontos-de-atenção).

## Complexity Tracking

> Sem violações da constituição. A única chamada síncrona inter-módulo
> (autorização de cota / validação de catálogo) é explicitamente permitida pelo
> Princípio I quando há necessidade de resposta imediata — autorização prévia não
> pode ser assíncrona — portanto não constitui desvio a justificar.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (nenhuma) | — | — |
