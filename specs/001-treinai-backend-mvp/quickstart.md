# Quickstart — Treinaí Backend MVP

Guia para subir o backend localmente e validar o fluxo principal.

## Pré-requisitos

- JDK 25 (gerenciado pelo toolchain Gradle).
- Docker (PostgreSQL local + Testcontainers nos testes).
- Conta Google Cloud com OAuth Client ID (login social) e API key do Gemini.
- Conta Mercado Pago (credenciais de sandbox).

## Configuração de segredos

Segredos ficam em `.env` (gitignored). Há um `.env.example` versionado sem
valores reais. **Antes de qualquer segredo**, garanta que `.env` está no
`.gitignore` (hoje não está — adicionar).

Variáveis necessárias (`.env.example`):

```dotenv
# Database
DB_URL=jdbc:postgresql://localhost:5432/treinai
DB_USERNAME=treinai
DB_PASSWORD=changeme

# App JWT (RSA) — emissão de token próprio via NimbusJwtEncoder
APP_JWT_ISSUER=https://api.treinai.local
APP_JWT_PRIVATE_KEY=
APP_JWT_PUBLIC_KEY=
APP_JWT_KEY_ID=treinai-key-1
APP_JWT_ACCESS_TTL=PT15M
APP_JWT_REFRESH_TTL=P30D

# Google login (validação do ID token via JWKs)
GOOGLE_CLIENT_ID=

# Gemini
GEMINI_API_KEY=
GEMINI_MODEL=gemini-1.5-flash
GEMINI_MAX_RETRIES=2

# Mercado Pago
MERCADOPAGO_ACCESS_TOKEN=
MERCADOPAGO_WEBHOOK_SECRET=
```

Gerar par RSA local:

```bash
openssl genrsa -out app-private.pem 2048
openssl rsa -in app-private.pem -pubout -out app-public.pem
```

## Subir PostgreSQL local

```bash
docker run --name treinai-pg -e POSTGRES_USER=treinai \
  -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=treinai \
  -p 5432:5432 -d postgres:16
```

## Build e execução

```bash
./gradlew build            # compila + testes (Testcontainers sobe PG efêmero)
./gradlew bootRun          # sobe a aplicação (perfil local lê .env)
```

OpenAPI em `http://localhost:8080/swagger-ui.html`.

## Fluxo de validação (US1 — caminho dourado)

1. `POST /api/v1/auth/google` com um ID token Google válido → recebe
   `accessToken` + `refreshToken`.
2. `POST /api/v1/me/profile` com objetivo, nível, restrições e biometria.
3. `POST /api/v1/training/generate` → plano de treino persistido (consome
   `TRAINING_GENERATION`); e/ou `POST /api/v1/nutrition/generate` → plano de dieta
   (consome `NUTRITION_GENERATION`). Free: 1 de cada por trimestre.
4. `GET /api/v1/training/current` e `GET /api/v1/nutrition/current` → planos.
5. Repetir o passo 3 dentro de 3 meses (Free) → `QUOTA_EXCEEDED` sem chamar a IA.
6. `POST /api/v1/training/sessions` → registra execução (livre, sem cota).

## Gates de qualidade (constituição)

```bash
./gradlew test jacocoTestCoverageVerification   # ≥90% linhas e branches
./gradlew sonar                                  # Quality Gate (CI)
```

`ApplicationModules.verify()` roda na suíte de testes e quebra o build se uma
fronteira de módulo for violada.

## Notas de deploy

- Imagem Docker multi-stage (JRE 25), aplicação stateless.
- Free tier: container em EC2 t3.micro + RDS PostgreSQL db.t3.micro.
- Evolução sem mudança de código: ECS Fargate / App Runner + RDS/Aurora
  Serverless v2.
- Segredos em produção via GitHub Secrets / AWS Secrets Manager (mesmas variáveis
  do `.env.example`).
