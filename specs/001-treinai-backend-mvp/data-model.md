# Phase 1 — Data Model: Treinaí Backend MVP

Modelo de dados por módulo Spring Modulith. Regra transversal (R6): **sem chaves
estrangeiras cruzando módulos** — referências inter-módulo são por ID simples
(coluna `*_id` sem `REFERENCES`); FKs existem apenas **dentro** do mesmo módulo.
Cada módulo é dono de suas tabelas. Tipos em PostgreSQL.

Convenções:

- **PK `UUID v7`** gerado na aplicação (Hibernate `@UuidGenerator(style = TIME)`
  ou gerador de UUID v7) — time-ordered, melhor localidade de índice que v4.
- Timestamps `created_at`/`updated_at` em `timestamptz`.
- **Enums nativos do PostgreSQL** (`CREATE TYPE ... AS ENUM`), criados via Flyway e
  mapeados no Hibernate — não usar `varchar` para conjuntos fechados.
- **Durações sempre em segundos** (coluna `*_seconds`).

### Tipos ENUM PostgreSQL

| Tipo | Valores |
|------|---------|
| `auth_provider` | `GOOGLE` (preparado p/ `APPLE`) |
| `user_status` | `ACTIVE`, `DISABLED` |
| `user_role` | `USER`, `ADMIN` |
| `training_goal` | `HYPERTROPHY`, `FAT_LOSS`, `MAINTENANCE` |
| `experience_level` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `sex` | `MALE`, `FEMALE` |
| `plan_code` | `FREE`, `PLUS`, `PREMIUM` |
| `entitlement_dimension` | `TRAINING_GENERATION`, `NUTRITION_GENERATION`, `TRAINING_ADJUSTMENT`, `NUTRITION_ADJUSTMENT`, `AI_ANALYSIS`, `FEATURE_DIET`, `FEATURE_ADVANCED_FOLLOWUP` |
| `period_type` | `MONTHLY`, `QUARTERLY`, `UNLIMITED`, `NONE` |
| `subscription_status` | `PENDING`, `ACTIVE`, `SUSPENDED`, `CANCELLED`, `TRIAL` (TRIAL previsto/desativado) |
| `payment_event_type` | `PAYMENT_APPROVED`, `PAYMENT_REJECTED`, `SUBSCRIPTION_CANCELLED` |
| `payment_event_status` | `PENDING`, `PROCESSED`, `REJECTED` |
| `library_plan_status` | `CURRENT`, `ARCHIVED` |
| `plan_source` | `GENERATED`, `ADJUSTED` |
| `ai_interaction_kind` | `GENERATION`, `ADJUSTMENT`, `ANALYSIS` |
| `ai_interaction_status` | `SUCCESS`, `ERROR` |

> Enums que podem crescer com configuração (ex.: `plan_code`) são fechados no MVP;
> os limites/preços por plano continuam orientados a dados via `Entitlement`.

---

## Módulo `identity`

### User
Identidade do cliente B2C.
- `id` UUID v7 PK
- `external_provider` `auth_provider`
- `external_subject` varchar — `sub` do provedor (único por provedor)
- `email` varchar, `display_name` varchar, `avatar_url` varchar null
- `status` `user_status`
- timestamps
- Unique: `(external_provider, external_subject)`

### UserRole
Papéis (FR-005). Tabela dentro de `identity`.
- `user_id` UUID v7 FK→User, `role` `user_role`, PK composta

### Profile
Dados para treino/dieta (FR-004). 1:1 com User.
- `id` UUID v7 PK, `user_id` UUID v7 FK→User (unique)
- `goal` `training_goal`
- `experience_level` `experience_level`
- `restrictions` text null (lesões/restrições)
- `height_cm` int, `weight_kg` numeric(5,2), `birth_date` date, `sex` `sex`
- timestamps
- **Validação**: geração bloqueada se campos obrigatórios ausentes (edge case).

### RefreshToken
Sessão renovável e revogável (FR-002/FR-003).
- `id` UUID v7 PK, `user_id` UUID v7 FK→User
- `token_hash` varchar (hash, nunca o valor cru)
- `issued_at`, `expires_at` timestamptz, `revoked_at` timestamptz null
- `replaced_by_id` UUID v7 null (rotação)
- Index: `(user_id)`, `(token_hash)` unique

---

## Módulo `billing`

### Plan
Definição de plano (FR-007).
- `id` UUID v7 PK, `code` `plan_code` unique
- `name` varchar, `price_cents` int, `currency` varchar, `active` bool
- timestamps

### Entitlement
Regra configurável de cota/feature por plano (FR-008). Sem `if` por nome de plano.
- `id` UUID v7 PK, `plan_id` UUID v7 FK→Plan
- `dimension` `entitlement_dimension`
- `period_type` `period_type`
- `limit_value` int — `>0` limite; `-1` ilimitado; ausência da linha = indisponível
- Unique: `(plan_id, dimension)`

### Subscription
Assinatura do usuário (FR-012). Referência a User por ID (sem FK cruzada).
- `id` UUID v7 PK, `user_id` UUID v7 (ref. identity, sem FK), `plan_id` UUID v7 FK→Plan
- `status` `subscription_status`
- `external_subscription_id` varchar null (Mercado Pago)
- `period_anchor` date — âncora da janela de cota (R3)
- `current_period_start` timestamptz, `current_period_end` timestamptz
- timestamps
- Index: `(user_id)`, unique `(user_id) where status='ACTIVE'` (uma ativa por user)
- **Estados** (R5): `PENDING → ACTIVE → (SUSPENDED|CANCELLED)`; downgrade/cancel
  retorna entitlements ao nível Free.

### QuotaUsage
Consumo de cota por período, à prova de concorrência (R3, SC-002).
- `id` UUID v7 PK, `user_id` UUID v7, `dimension` `entitlement_dimension`
- `period_start` timestamptz, `period_end` timestamptz
- `used` int default 0
- Unique: `(user_id, dimension, period_start)` — incremento condicional/lock

### PaymentEvent
Evento de pagamento idempotente (FR-011, SC-004).
- `id` UUID v7 PK, `external_id` varchar unique (id da notificação Mercado Pago)
- `subscription_id` UUID v7 FK→Subscription null
- `type` `payment_event_type`
- `raw_payload` jsonb, `processed_at` timestamptz, `status` `payment_event_status`
- Unique `external_id` garante idempotência.

---

## Módulo `catalog` (bibliotecas)

### ExerciseLibraryItem
Catálogo curado de exercícios (FR-039), fonte de verdade.
- `id` UUID v7 PK, `slug` varchar unique (vocabulário controlado)
- `name` varchar, `muscle_group` varchar, `equipment` varchar null
- `description` text null
- `photo_url` varchar null, `video_url` varchar null (referências de mídia, FR-043)
- `active` bool, `version` int, timestamps

### FoodLibraryItem
Catálogo curado de alimentos (FR-039).
- `id` UUID v7 PK, `slug` varchar unique
- `name` varchar
- `kcal_per_portion` numeric, `protein_g` numeric, `carbs_g` numeric,
  `fat_g` numeric, `portion_grams` numeric (dados de referência)
- `photo_url` varchar null, `active` bool, `version` int, timestamps

### MovementVocabulary (nomenclatura de divisões de treino)
Vocabulário controlado para nomes de divisões/treinos (FR-042).
- `id` UUID v7 PK, `code` varchar unique (ex.: `PUSH`, `PULL`, `LEGS`, `UPPER`),
  `label` varchar, `active` bool

### MealVocabulary (nomenclatura de refeições)
Vocabulário controlado para tipos de refeição da dieta.
- `id` UUID v7 PK, `code` varchar unique (ex.: `BREAKFAST`, `LUNCH`, `DINNER`,
  `SNACK`, `PRE_WORKOUT`, `POST_WORKOUT`), `label` varchar (ex.: "Café da manhã",
  "Almoço", "Janta"), `order_index` int, `active` bool

---

## Módulo `training`

### TrainingPlan
Plano de treino estruturado, versionado (FR-015, FR-017).
- `id` UUID v7 PK, `user_id` UUID v7 (ref. identity)
- `status` `library_plan_status` (`CURRENT`|`ARCHIVED`)
- `version` int, `source` `plan_source` (`GENERATED`|`ADJUSTED`)
- `ai_interaction_id` UUID v7 null (ref. aicoach, sem FK cruzada)
- timestamps

### TrainingDivision
Divisão/dia do plano. FK interna ao módulo.
- `id` UUID v7 PK, `training_plan_id` UUID v7 FK→TrainingPlan
- `name` varchar, `vocabulary_code` varchar (ref. catalog `MovementVocabulary`)
- `order_index` int

### TrainingExercise
Exercício prescrito numa divisão.
- `id` UUID v7 PK, `training_division_id` UUID v7 FK→TrainingDivision
- `exercise_item_id` UUID v7 (ref. catalog `ExerciseLibraryItem`, sem FK cruzada)
- `order_index` int, `sets` int, `reps` varchar (ex.: `8-12`),
  `rest_seconds` int, `notes` text null

### WorkoutSession (Workout Log)
Registro de execução de uma divisão/dia (FR-034/035/036). Livre p/ todos os
planos, sem cota. Vinculada à **divisão** efetivamente treinada (o plano é
derivável da divisão via `TrainingDivision.training_plan_id`).
- `id` UUID v7 PK, `user_id` UUID v7
- `training_division_id` UUID v7 FK→TrainingDivision
- `performed_at` timestamptz, `duration_seconds` int null
- `general_notes` text null, timestamps

### WorkoutSessionExercise
Execução por exercício prescrito.
- `id` UUID v7 PK, `workout_session_id` UUID v7 FK→WorkoutSession
- `training_exercise_id` UUID v7 FK→TrainingExercise
- `completed` bool (false = pulado, edge case)
- `rpe` int null (percepção de esforço)
- `notes` text null

### WorkoutSessionExerciseSet
Série realizada (reps reais + carga por série).
- `id` UUID v7 PK, `workout_session_exercise_id` UUID v7 FK→WorkoutSessionExercise
- `set_index` int, `reps` int, `load_kg` numeric(6,2)

---

## Módulo `nutrition`

### NutritionPlan
Plano de dieta estruturado, versionado (FR-019, FR-021).
- `id` UUID v7 PK, `user_id` UUID v7
- `status` `library_plan_status` (`CURRENT`|`ARCHIVED`), `version` int,
  `source` `plan_source` (`GENERATED`|`ADJUSTED`)
- `ai_interaction_id` UUID v7 null (ref. aicoach)
- `total_kcal` numeric, `total_protein_g` numeric, `total_carbs_g` numeric,
  `total_fat_g` numeric, timestamps

### Meal
Refeição do plano.
- `id` UUID v7 PK, `nutrition_plan_id` UUID v7 FK→NutritionPlan
- `vocabulary_code` varchar (ref. catalog `MealVocabulary`)
- `name` varchar, `order_index` int
- `kcal` numeric, `protein_g` numeric, `carbs_g` numeric, `fat_g` numeric (totais)

### MealItem
Alimento da refeição.
- `id` UUID v7 PK, `meal_id` UUID v7 FK→Meal
- `food_item_id` UUID v7 (ref. catalog `FoodLibraryItem`, sem FK cruzada)
- `portion_grams` numeric, `kcal` numeric, `protein_g` numeric,
  `carbs_g` numeric, `fat_g` numeric

---

## Módulo `aicoach`

### AIInteraction (Audit)
Auditoria de toda chamada de IA (FR-023, SC-003). Inclusive em erro.
- `id` UUID v7 PK, `user_id` UUID v7
- `kind` `ai_interaction_kind`
- `prompt` text, `response` text null
- `request_at` timestamptz, `latency_ms` int
- `input_tokens` int null, `output_tokens` int null, `estimated_cost` numeric null
- `status` `ai_interaction_status`, `error_detail` text null
- **Privacidade**: persistido em banco (não em log); sem PII em logs (SC-007).

### AIAnalysis
Feedback/análise sob demanda Premium (FR-037/038).
- `id` UUID v7 PK, `user_id` UUID v7, `ai_interaction_id` UUID v7 FK→AIInteraction
- `summary` text, `recommendations` jsonb, `created_at` timestamptz

---

## Módulo `progress`

### ProgressEntry (Biometria)
Peso e medidas com data (FR-026/027). Cargas por exercício ficam em `training`.
- `id` UUID v7 PK, `user_id` UUID v7
- `measured_at` date
- `weight_kg` numeric(5,2) null
- `measurements` jsonb null (cintura, braço, etc.)
- `notes` text null, `created_at` timestamptz
- Index: `(user_id, measured_at)`

---

## Eventos de Domínio (Spring Modulith)

| Evento | Publica | Consome | Propósito |
|--------|---------|---------|-----------|
| `SubscriptionActivated` | billing | (observabilidade; entitlements internos ao billing) | Assinatura ativada após webhook aprovado (FR-013). |
| `SubscriptionSuspended` | billing | observabilidade | Pagamento recusado → suspensão. |
| `SubscriptionCancelled` | billing | observabilidade | Cancelamento → volta a Free. |
| `TrainingPlanGenerated` | training | observabilidade/métricas | Plano de treino gerado/persistido. |
| `NutritionPlanGenerated` | nutrition | observabilidade/métricas | Plano de dieta gerado/persistido. |
| `AIInteractionRecorded` | aicoach | observabilidade/custo | Toda interação de IA auditada (métrica de custo). |

> **Autorização de cota NÃO usa eventos** (assíncrono não serve para autorização
> prévia). training/nutrition/aicoach chamam `billing` **sincronamente** via API
> pública (`BillingEntitlementService`) antes de agir (R3).

### APIs públicas síncronas entre módulos

| Chamador | Módulo alvo | Interface pública | Uso |
|----------|-------------|-------------------|-----|
| training, nutrition, aicoach | billing | `BillingEntitlementService.check/consume(userId, dimension)` | Autorização e consumo de cota (FR-009). |
| aicoach | catalog | `CatalogQueryService.validExerciseSlugs()/validFoodSlugs()` | Itens válidos p/ prompt e validação (FR-040/041). |
| training, nutrition | aicoach | `AiCoachService.generate*/adjust*(...)` | Solicitar geração/ajuste e receber estrutura validada (FR-022). |
| aicoach (analysis) | training, progress | `WorkoutHistoryQuery`, `ProgressQuery` | Insumos para análise sob demanda (FR-037). |
| training (sessão) | training | (interno) | Registro de execução não chama billing (livre, FR-035). |

### Mapa de consumo de cota por ação

| Ação | Dimensão | Free | Plus | Premium |
|------|----------|------|------|---------|
| Gerar treino | `TRAINING_GENERATION` | 1 / trimestre | 1 / mês | configurável/ilimitado |
| Gerar dieta | `NUTRITION_GENERATION` | 1 / trimestre | 1 / mês | configurável/ilimitado |
| Ajustar treino | `TRAINING_ADJUSTMENT` | indisponível | 1 / mês | ilimitado |
| Ajustar dieta | `NUTRITION_ADJUSTMENT` | indisponível | 1 / mês | ilimitado |
| Análise por IA sob demanda | `AI_ANALYSIS` | indisponível | indisponível | ilimitado |
| Registrar execução de treino | — | livre | livre | livre |
| Registrar biometria | — | livre | livre | livre |

> Treino e dieta são gerados/ajustados **separadamente** (endpoints distintos em
> `training` e `nutrition`); cada ação consome sua própria dimensão de cota. No
> Free, o usuário tem direito a 1 treino **e** 1 dieta por trimestre, podendo
> escolher gerar apenas um deles. Isso substitui a noção de "geração combinada" da
> spec original (SC-008/Assumption), que deve ser atualizada na próxima revisão da
> spec.
