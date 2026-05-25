# Phase 1 — REST API Contracts: Treinaí Backend MVP

Contrato de alto nível dos endpoints por módulo. Todos sob `/api/v1`. Entrada =
DTO `*Request` (Bean Validation); saída = DTO `*Response`. **Criação via POST,
atualização parcial via PATCH com `JsonNullable`. PUT nunca é usado.** Erros em
**RFC 7807 (Problem Details)** com campo `errorCode` estável em inglês.
Autenticação via Bearer JWT próprio, exceto endpoints marcados como públicos.

Documentação executável gerada por **springdoc** (`/v3/api-docs`,
`/swagger-ui.html`); este arquivo é a fonte de design.

## Códigos de erro estáveis (errorCode)

`UNAUTHENTICATED`, `INVALID_GOOGLE_TOKEN`, `REFRESH_TOKEN_INVALID`,
`PROFILE_INCOMPLETE`, `PROFILE_ALREADY_EXISTS`, `QUOTA_EXCEEDED`, `FEATURE_NOT_AVAILABLE`,
`AI_UNAVAILABLE`, `AI_RESPONSE_INVALID`, `LIBRARY_INSUFFICIENT`,
`NO_CURRENT_TRAINING_PLAN`, `INSUFFICIENT_DATA_FOR_ANALYSIS`,
`WEBHOOK_UNKNOWN_SUBSCRIPTION`, `FORBIDDEN_RESOURCE`, `VALIDATION_ERROR`.

---

## identity

| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/api/v1/auth/google` | público | Recebe `{ idToken }` do Google; valida via JWKs Google; **cria ou recupera** o User (auto-criação no login, FR-001); retorna `{ accessToken, refreshToken, user }`. |
| POST | `/api/v1/auth/refresh` | público (refresh) | `{ refreshToken }` → novo par; rotação; revoga anterior. |
| POST | `/api/v1/auth/logout` | Bearer | Revoga o refresh token corrente. |
| GET | `/api/v1/me` | Bearer | Retorna usuário + perfil (perfil ausente se ainda não criado). |
| POST | `/api/v1/me/profile` | Bearer | Cria o perfil (objetivo, nível, restrições, biometria). Erro se já existe. |
| PATCH | `/api/v1/me/profile` | Bearer | Atualização parcial (`JsonNullable`). |

Erros: `INVALID_GOOGLE_TOKEN` (401), `REFRESH_TOKEN_INVALID` (401),
`VALIDATION_ERROR` (400).

---

## billing

| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| GET | `/api/v1/plans` | público | Lista planos ativos e seus entitlements. |
| GET | `/api/v1/me/subscription` | Bearer | Assinatura atual + entitlements efetivos + uso de cota. |
| POST | `/api/v1/me/subscription` | Bearer | Inicia assinatura a um plano (`{ planCode, paymentMethod }` PIX/CARD) → `PENDING` + dados de pagamento. |
| POST | `/api/v1/me/subscription/cancel` | Bearer | Solicita cancelamento. |
| POST | `/api/v1/billing/webhooks/mercadopago` | público (assinado) | Webhook idempotente de pagamento. |

Erros: `FEATURE_NOT_AVAILABLE` (403), `WEBHOOK_UNKNOWN_SUBSCRIPTION` (422 — auditado).

---

## training

| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/api/v1/training/generate` | Bearer | Gera **apenas** o plano de treino (consome `TRAINING_GENERATION`). |
| POST | `/api/v1/training/adjust` | Bearer | Ajusta treino (consome `TRAINING_ADJUSTMENT`; Plus/Premium). |
| GET | `/api/v1/training/current` | Bearer | Plano de treino vigente. |
| GET | `/api/v1/training/history` | Bearer | Histórico de planos. |
| POST | `/api/v1/training/sessions` | Bearer | Registra execução de sessão (livre, sem cota). |
| GET | `/api/v1/training/sessions` | Bearer | Histórico de sessões (ordem cronológica). |
| GET | `/api/v1/training/sessions/{id}` | Bearer | Detalhe de uma sessão. |

Erros: `QUOTA_EXCEEDED` (429/403), `PROFILE_INCOMPLETE` (422),
`NO_CURRENT_TRAINING_PLAN` (409), `LIBRARY_INSUFFICIENT` (422),
`AI_UNAVAILABLE` (503), `AI_RESPONSE_INVALID` (502), `FORBIDDEN_RESOURCE` (403).

> Treino e dieta são gerados **independentemente** — o usuário escolhe o que gerar.
> Cada `generate`/`adjust` valida perfil e cota da sua dimensão **antes** de chamar
> a IA; em falha de IA, não persiste plano parcial e não consome cota.

---

## nutrition

| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/api/v1/nutrition/generate` | Bearer | Gera o plano de dieta (consome `NUTRITION_GENERATION`). |
| POST | `/api/v1/nutrition/adjust` | Bearer | Ajusta dieta (consome `NUTRITION_ADJUSTMENT`; Plus/Premium). |
| GET | `/api/v1/nutrition/current` | Bearer | Dieta vigente. |
| GET | `/api/v1/nutrition/history` | Bearer | Histórico de dietas. |

Erros: mesmos da geração de treino, com as dimensões de cota de `nutrition`.

---

## progress

| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/api/v1/progress/entries` | Bearer | Registra biometria (peso/medidas com data). |
| GET | `/api/v1/progress/entries` | Bearer | Histórico ordenado cronologicamente. |
| PATCH | `/api/v1/progress/entries/{id}` | Bearer | Atualização parcial (`JsonNullable`). |

---

## aicoach

| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/api/v1/ai/analysis` | Bearer | Análise/feedback sob demanda (Premium, `AI_ANALYSIS`). |
| GET | `/api/v1/ai/analysis/{id}` | Bearer | Recupera análise gerada. |

Erros: `FEATURE_NOT_AVAILABLE` (403 — Free/Plus),
`INSUFFICIENT_DATA_FOR_ANALYSIS` (422), `AI_UNAVAILABLE` (503).

---

## admin (escopo mínimo — role ADMIN)

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET/POST/PATCH/DELETE | `/api/v1/admin/exercises[/{id}]` | CRUD Biblioteca de Exercícios (mídia inclusa). |
| GET/POST/PATCH/DELETE | `/api/v1/admin/foods[/{id}]` | CRUD Biblioteca de Alimentos. |
| GET/POST/PATCH | `/api/v1/admin/plans[/{id}]` | Gestão de planos e entitlements. |
| GET | `/api/v1/admin/ai-interactions` | Visualização da auditoria de IA. |

Autorização: `ADMIN` obrigatório; demais retornam `FORBIDDEN_RESOURCE` (403).

---

## Formato de erro (RFC 7807)

```json
{
  "type": "https://api.treinai.com.br/errors/quota-exceeded",
  "title": "Quota exceeded",
  "status": 429,
  "detail": "Generation quota for the current period has been consumed.",
  "errorCode": "QUOTA_EXCEEDED",
  "instance": "/api/v1/training/generate"
}
```

## Regras de autorização transversais

- Todo recurso de usuário é filtrado por `userId` do token; acesso a recurso de
  outro usuário → `FORBIDDEN_RESOURCE` (403) — SC-005, FR-029.
- Cota verificada **antes** da execução em toda ação que consome (FR-009, SC-002).
- Registro de execução e biometria nunca consomem cota (FR-035, SC-009).
