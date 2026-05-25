<!--
SYNC IMPACT REPORT
==================
Version change: (template / não versionado) → 1.0.0
Bump rationale: Ratificação inicial da constituição. Primeira definição formal
  de todos os princípios e seções de governança a partir do template vazio.

Modified principles: N/A (ratificação inicial)
Added sections:
  - Core Principles I–VIII (Module Boundaries, TDD, Cobertura e Qualidade,
    Clean Code e Design, API e Contratos, Idioma, Observabilidade e
    Rastreabilidade, Segurança)
  - Restrições Técnicas Imutáveis (stack tecnológica não-negociável)
  - Fluxo de Desenvolvimento e Quality Gates
  - Governance
Removed sections: Nenhuma (template substituído integralmente)

Templates requiring updates:
  - ✅ .specify/templates/plan-template.md  (Constitution Check alinhado; sem
       edição estrutural necessária — o gate referencia este arquivo)
  - ✅ .specify/templates/spec-template.md  (sem conflito; specs em pt-BR já
       compatível com Princípio VI)
  - ✅ .specify/templates/tasks-template.md (testes são OPCIONAIS no template
       genérico, mas o Princípio II torna TDD/testes OBRIGATÓRIOS neste projeto;
       o comando /speckit-tasks DEVE gerar tarefas de teste sempre)
  - ⚠ Nenhum README.md de runtime presente para sincronizar

Follow-up TODOs: Nenhum. Todos os placeholders preenchidos.
-->

# Constituição do Treinaí — Back-end

O Treinaí é um SaaS B2C de planos de treino de musculação e dietas gerados por um
coach de Inteligência Artificial. Esta constituição rege **exclusivamente o
back-end** nesta fase. Ela é a autoridade máxima sobre decisões de arquitetura,
qualidade e processo; em caso de conflito com qualquer outro guia, esta
constituição prevalece.

## Core Principles

### I. Fronteiras de Módulo (Module Boundaries)

A aplicação é um monólito modular (único deployable) construído com **Spring
Modulith**, e suas fronteiras são invioláveis:

- Nenhum módulo acessa o repositório (camada de persistência) de outro módulo,
  nem suas entidades JPA internas.
- A comunicação entre módulos é feita preferencialmente por **eventos de domínio
  do Spring Modulith** (`ApplicationEventPublisher` + `@ApplicationModuleListener`).
  Chamadas síncronas via API pública do módulo (interfaces de serviço expostas no
  pacote-raiz do módulo) são permitidas APENAS quando há necessidade de resposta
  imediata.
- Cada módulo expõe apenas o que está em seu pacote-raiz; classes em subpacotes
  (`internal`, `domain`, `persistence`) são privadas ao módulo.
- A integridade das fronteiras DEVE ser garantida por um teste
  `ApplicationModules.verify()` executado no CI.

**Rationale**: Fronteiras explícitas preservam a evolução independente dos módulos
e mantêm aberto o caminho para extração de serviços no futuro, sem o custo
operacional de microsserviços hoje.

### II. Test-Driven Development (NÃO-NEGOCIÁVEL)

- TDD é mandatório: escreva o teste que falha, implemente o mínimo para passar,
  refatore (Red-Green-Refactor).
- Nenhum PR é aceito sem testes correspondentes à mudança.
- Testes de integração usam **Testcontainers (PostgreSQL real)**, NUNCA H2 ou
  mocks de banco.
- **Testes de integração de API** (camada web ponta a ponta: requisição HTTP real
  → resposta, sobre PostgreSQL via Testcontainers) cobrem os endpoints, validando
  status, payload, formato de erro e autorização.
- Chamadas externas (Gemini, Mercado Pago, Google) são isoladas atrás de
  interfaces e mockadas/stubadas nos testes; contratos validados por testes
  dedicados.

**Rationale**: O comportamento real sobre PostgreSQL difere de mocks e de H2;
testar contra o banco de produção real elimina classes inteiras de defeitos que
só apareceriam em produção.

### III. Cobertura e Qualidade

- Cobertura mínima de **90%** (linhas e branches), verificada por **JaCoCo**,
  quebrando o build abaixo da meta.
- O código DEVE passar nas métricas do **SonarQube/SonarCloud** sem novos *code
  smells*, *bugs* ou *vulnerabilities* de severidade relevante (**Quality Gate
  verde obrigatório no CI**).
- Zero warnings novos do LSP e do Sonar; warnings existentes DEVEM ser tratados,
  não suprimidos sem justificativa documentada.

**Rationale**: Metas de qualidade verificadas automaticamente são as únicas que se
sustentam; gates no CI impedem a erosão silenciosa da qualidade ao longo do tempo.

### IV. Clean Code e Design

- Métodos curtos, responsabilidade única, nomes expressivos; complexidade
  ciclomática controlada.
- Sem lógica de negócio em controllers; controllers apenas orquestram. Regra de
  negócio vive em serviços/domínio.
- **Entidades JPA NUNCA são expostas** em nenhuma fronteira da API — nem na
  entrada, nem na saída, nem aninhadas. Toda fronteira usa DTOs dedicados.
- **DTOs seguem convenção de sufixo**: `Request` para entrada (ex.:
  `CreateTrainingRequest`) e `Response` para saída (ex.: `TrainingPlanResponse`).
- **Toda conversão DTO ↔ entidade (e vice-versa) é feita via MapStruct.**
  Mapeamento manual só é permitido em casos justificados que o MapStruct não cobre.
- **PATCH é preferido a PUT** para atualizações parciais. Os DTOs de atualização
  usam `JsonNullable` para distinguir entre "campo não enviado" (não alterar) e
  "campo enviado como null" (limpar valor). PUT só é usado quando a substituição
  total do recurso for semanticamente correta.
- Imutabilidade preferida onde possível (records para DTOs e eventos).

**Rationale**: Separar contratos de API das entidades de persistência impede que
mudanças de schema vazem para clientes e que entradas maliciosas escrevam campos
não pretendidos; convenções uniformes reduzem a carga cognitiva de revisão.

### V. API e Contratos

- API REST versionada sob `/api/v1`.
- Erros seguem **RFC 7807 (Problem Details)**, suportado nativamente pelo Spring
  Boot.
- **Mensagens de erro da API em inglês** e voltadas a desenvolvedores; o front-end
  é responsável por traduzir/exibir a mensagem final ao usuário. Use códigos de
  erro estáveis e legíveis por máquina.
- Documentação OpenAPI gerada via **springdoc**, sempre atualizada.

**Rationale**: Contratos versionados e formatos de erro padronizados permitem que
clientes evoluam de forma previsível; códigos estáveis de erro desacoplam a lógica
do cliente do texto exibido ao usuário.

### VI. Idioma

- **Todo o código, nomes de pacotes, comentários, commits e logs em inglês.**
- **Especificações e documentação de projeto em português (pt-BR).**

**Rationale**: Código em inglês maximiza a interoperabilidade com bibliotecas e
contratações futuras; documentação em pt-BR atende à equipe e aos stakeholders do
produto.

### VII. Observabilidade e Rastreabilidade

- Toda interação com a IA (prompt enviado, resposta recebida, tokens/uso, custo
  estimado, latência, usuário, timestamp, status) DEVE ser persistida para
  auditoria e rastreabilidade.
- Métricas de negócio e técnicas expostas via **Prometheus** (Micrometer).
- Logs estruturados, sem dados sensíveis (PII, tokens, segredos) em texto plano.

**Rationale**: O coach de IA é o centro de custo e de risco do produto; auditar
cada interação é essencial para controle de gasto, depuração de qualidade das
respostas e conformidade.

### VIII. Segurança

- Segredos NUNCA no código nem no repositório. **Em ambiente local, segredos ficam
  em um arquivo `.env`** (que DEVE estar no `.gitignore` e nunca ser commitado); um
  `.env.example` sem valores reais é versionado como referência. Em ambientes
  gerenciados, uso de variáveis de ambiente / AWS Secrets Manager.
- Princípio do menor privilégio em roles e autorização por plano/assinatura.
- Validação de entrada em todas as fronteiras (`spring-boot-starter-validation`).

**Rationale**: Vazamento de segredos e autorização frouxa são as falhas de maior
impacto em um SaaS B2C com pagamentos; controles na fronteira e gestão disciplinada
de segredos são pré-requisitos, não melhorias.

## Restrições Técnicas Imutáveis

A stack abaixo é não-negociável nesta fase. Qualquer desvio exige emenda formal a
esta constituição (ver Governance).

- **Linguagem**: Java 25 (toolchain via Gradle).
- **Framework**: Spring Boot 4.0.6 com `spring-boot-starter-webmvc` (stack
  servlet/MVC, **NÃO** reactive).
- **Persistência**: PostgreSQL via Spring Data JPA. Migrations exclusivamente via
  **Flyway** — nunca `ddl-auto` em modos que alterem o schema (`validate` é o
  máximo permitido).
- **Arquitetura**: Spring Modulith (monólito modular, único deployable).
- **Segurança**: OAuth2 (login social Google no MVP) + emissão de JWT próprio da
  aplicação. Validação de JWT via `spring-boot-starter-oauth2-resource-server`.
- **IA**: Google Gemini (tier Free) como provedor do coach.
- **Observabilidade**: Micrometer + Prometheus.
- **Pagamentos**: Mercado Pago (PIX e cartão recorrente).
- **Build**: Gradle Kotlin DSL. **Lombok** para boilerplate.
- **Testes**: JUnit 5 + Testcontainers (PostgreSQL real em testes de integração).
- **Deploy**: Artefato containerizado (Docker), stateless, agnóstico ao serviço
  AWS (EC2 no free tier; ECS/App Runner depois). CI/CD via GitHub Actions.

## Fluxo de Desenvolvimento e Quality Gates

- O trabalho de cada feature segue o fluxo Spec Kit: especificação → plano →
  tarefas → implementação, com a Constitution Check validada no plano.
- Todo PR DEVE, antes do merge, passar nos seguintes gates automatizados no CI:
  1. `ApplicationModules.verify()` (fronteiras de módulo — Princípio I).
  2. Suíte de testes completa, incluindo integração com Testcontainers
     (Princípio II).
  3. Cobertura JaCoCo ≥ 90% linhas e branches (Princípio III).
  4. SonarQube/SonarCloud Quality Gate verde, sem novos issues relevantes
     (Princípio III).
  5. Build sem warnings novos (LSP e Sonar).
- Revisão de código DEVE verificar conformidade com todos os princípios desta
  constituição; violações bloqueiam o merge até correção ou justificativa
  documentada na seção de Complexity Tracking do plano.
- O comando de geração de tarefas DEVE produzir tarefas de teste para cada
  unidade de comportamento — testes não são opcionais neste projeto (Princípio II),
  ainda que o template genérico os trate como tais.

## Governance

- Esta constituição supersede todas as demais práticas e guias do projeto. Em caso
  de conflito, ela prevalece.
- **Emendas**: qualquer alteração a princípios, restrições técnicas ou gates DEVE
  ser proposta por escrito (PR alterando este arquivo), com justificativa e, quando
  aplicável, plano de migração para o código existente. A emenda só vigora após
  merge.
- **Versionamento** (semântico):
  - **MAJOR**: remoção ou redefinição incompatível de princípios/governança.
  - **MINOR**: adição de princípio/seção ou expansão material de orientação.
  - **PATCH**: esclarecimentos, correções de redação, refinamentos não-semânticos.
- **Revisão de conformidade**: todo PR e revisão DEVE confirmar aderência aos
  princípios. Desvios justificados são registrados na tabela de Complexity Tracking
  do plano da feature correspondente.
- **Orientação de runtime**: o arquivo `CLAUDE.md` na raiz fornece orientação
  operacional para agentes e desenvolvedores e DEVE permanecer consistente com esta
  constituição.

**Version**: 1.0.0 | **Ratified**: 2026-05-24 | **Last Amended**: 2026-05-24