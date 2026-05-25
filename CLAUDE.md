# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan at
`specs/001-treinai-backend-mvp/plan.md` (and its companion `research.md`,
`data-model.md`, `quickstart.md`, and `contracts/`).
<!-- SPECKIT END -->

## Commands

```bash
# Build completo (compila + todos os testes com Testcontainers)
./gradlew build

# Subir a aplicação localmente (requer PostgreSQL em localhost:5432 e .env configurado)
./gradlew bootRun

# Rodar todos os testes
./gradlew test

# Rodar um único teste por nome de classe
./gradlew test --tests "br.com.treinai.identity.internal.web.AuthControllerTest"

# Rodar um único método de teste
./gradlew test --tests "br.com.treinai.billing.internal.BillingEntitlementServiceTest.shouldRejectWhenQuotaExceeded"

# Verificar cobertura (gate ≥90% linhas e branches — quebra o build se não atingir)
./gradlew test jacocoTestCoverageVerification

# Análise SonarCloud (executada no CI)
./gradlew sonar
```

PostgreSQL local (Docker):
```bash
docker run --name treinai-pg -e POSTGRES_USER=treinai \
  -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=treinai \
  -p 5432:5432 -d postgres:16
```

Gerar par de chaves RSA para JWT próprio:
```bash
openssl genrsa -out app-private.pem 2048
openssl rsa -in app-private.pem -pubout -out app-public.pem
```

OpenAPI disponível em `http://localhost:8080/swagger-ui.html` quando a aplicação estiver rodando.

## Architecture

**Monólito modular** (Spring Modulith) com sete módulos de negócio + `shared`, todos sob `br.com.treinai`. Java 25, Spring Boot 4.0.6 MVC servlet, PostgreSQL + Flyway (`ddl-auto=validate`).

### Módulos e responsabilidades

| Módulo      | Responsabilidade |
|-------------|-----------------|
| `identity` | Autenticação (Google ID token → JWT próprio via Nimbus RSA), perfil do usuário, refresh token |
| `billing` | Planos, entitlements, cotas (QuotaUsage), assinaturas, webhooks Mercado Pago (idempotentes por `externalId`) |
| `catalog` | Bibliotecas curadas de exercícios e alimentos + vocabulários controlados (`MovementVocabulary`, `MealVocabulary`) — fonte de verdade para a IA |
| `training` | Planos de treino gerados/ajustados pela IA (endpoint próprio); registro de sessões e séries (livre, sem cota) |
| `nutrition` | Planos de dieta gerados/ajustados pela IA (endpoint próprio, independente de treino) |
| `aicoach` | Integração com Google Gemini (porta `AiCoachClient` + adapter `GeminiCoachClient`); auditoria `AIInteraction` de toda chamada |
| `progress` | Registro biométrico (peso, medidas) |
| `shared` | Problem Details (RFC 7807), tipos-base de eventos de domínio, config Jackson/OpenAPI/security base — módulo folha sem dependência de negócio |

### Regras de fronteira (obrigatórias)

- API pública de cada módulo fica no **pacote-raiz** do módulo; implementação fica em `internal/` (privado ao Modulith).
- **Sem FK cruzando módulos** — referências inter-módulo são por ID simples (coluna sem `REFERENCES`).
- `ApplicationModules.verify()` roda nos testes e quebra o build se uma fronteira for violada.

### Comunicação entre módulos

- **Eventos de domínio** (Spring Modulith Events JPA) para notificações assíncronas: `SubscriptionActivated/Suspended/Cancelled`, `TrainingPlanGenerated`, `NutritionPlanGenerated`, `AIInteractionRecorded`.
- **Chamadas síncronas** apenas onde resposta imediata é obrigatória:
  - `training`/`nutrition`/`aicoach` → `BillingEntitlementService.check/consume(userId, dimension)` (autorização de cota — não pode ser assíncrona).
  - `aicoach` → `CatalogQueryService.validExerciseSlugs()/validFoodSlugs()` (validação de itens no prompt).
  - `training`/`nutrition` → `AiCoachService.generate*/adjust*()` (solicitar geração estruturada).

### Autenticação

Fluxo em duas pernas: cliente envia **ID token Google** → backend valida contra JWKs do Google usando `NimbusJwtDecoder` dedicado → emite **JWT próprio** (access ~15 min + refresh persistido com hash) via `NimbusJwtEncoder` com par RSA. Rotas protegidas validam o JWT próprio via `oauth2-resource-server`. Dois `JwtDecoder` beans coexistem por qualificação explícita.

### Cotas (billing)

`BillingEntitlementService.consume()` executa em transação com lock pessimista (`SELECT ... FOR UPDATE`) ou `UPDATE ... WHERE used < limit` — à prova de concorrência. Dimensões: `TRAINING_GENERATION`, `NUTRITION_GENERATION`, `TRAINING_ADJUSTMENT`, `NUTRITION_ADJUSTMENT`, `AI_ANALYSIS` (treino e dieta têm cotas independentes). Consumo só efetiva se a ação a jusante for bem-sucedida; falha de IA → rollback/não consome.

### Integração Gemini (aicoach)

`GeminiCoachClient` usa **Spring Cloud OpenFeign** (`@FeignClient`) contra a REST API do Gemini com `responseMimeType=application/json` + `responseSchema`. Após receber a resposta, valida cada item contra o `catalog`; se houver item fora do catálogo, re-solicita até N tentativas (config, default 2). Falha persistente → erro controlado, sem plano parcial e **sem consumir cota**. Toda chamada (inclusive erro) grava `AIInteraction` com prompt, resposta, tokens, latência e custo estimado. Dados de `AIInteraction` ficam **apenas em banco**, nunca em log (SC-007).

### Convenções de código

- DTOs como `record` com sufixos `Request` / `Response`; entidades JPA nunca cruzam fronteiras.
- **Criação é POST, atualização é PATCH — nunca use PUT.**
- PATCH usa `JsonNullable<T>` (`org.openapitools:jackson-databind-nullable`) + `JsonNullableModule` registrado no `ObjectMapper`.
- **PKs são UUID v7** (Hibernate `@UuidGenerator(style = TIME)`); **enums nativos do PostgreSQL** (não `varchar`); **durações sempre em segundos** (`*_seconds`).
- Integrações externas via libs/SDK oficiais: **Feign** para Gemini, **SDK** do Mercado Pago — preferir libs a clientes manuais.
- **MapStruct** (`componentModel = "spring"`) para toda conversão; ordem dos annotation processors no Gradle: `lombok` → `lombok-mapstruct-binding` → `mapstruct`.
- Erros retornam **Problem Details (RFC 7807)** com campo `errorCode` estável em inglês (ex.: `QUOTA_EXCEEDED`, `AI_UNAVAILABLE`).
- Código, commits, logs e `errorCode` em **inglês**; specs e plano em pt-BR.
- Sem PII em logs — biometria e prompts são persistidos em banco, não logados.

### Testes

- Testes de integração usam **Testcontainers com PostgreSQL real** — H2 e mocks de repositório são proibidos.
- WireMock para stubs de Gemini, Mercado Pago e Google JWKs.
- `spring-security-test` para cenários de autenticação/autorização.
- Gate: JaCoCo ≥ 90% linhas e branches quebrando o build; SonarCloud Quality Gate verde no CI.

### Segredos

Ficam em `.env` (gitignored). Versionado apenas `.env.example` sem valores. Em produção: GitHub Secrets / AWS Secrets Manager com as mesmas variáveis. Chaves RSA nunca vão para o repositório.

# Rules

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
