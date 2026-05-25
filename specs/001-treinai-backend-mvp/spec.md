# Feature Specification: Treinaí — Backend MVP

**Feature Branch**: `001-treinai-backend-mvp`

**Created**: 2026-05-24

**Status**: Draft

**Input**: User description: "Especifique o back-end do MVP do Treinaí, um SaaS B2C onde usuários recebem planos de treino de musculação e dietas gerados por um coach de IA (Google Gemini), com acompanhamento de progresso e assinatura paga."

## Clarifications

### Session 2026-05-24

- Q: Onde deve viver o registro de execução de treino (exercícios feitos, cargas)? → A: No domínio `training`, como log vinculado ao plano de treino; `progress` fica só com biometria (peso/medidas). O log alimenta os ajustes da IA.
- Q: Qual o escopo do acompanhamento proativo da IA no MVP? → A: Sob demanda — o usuário solicita a análise/feedback e a IA processa os dados; o disparo proativo automático fica como arquitetura prevista, fora do escopo de execução do MVP.
- Q: O que é capturado em um registro de execução de treino? → A: Por sessão, por exercício prescrito: séries realizadas (reps reais + carga por série), concluído/pulado, percepção de esforço (RPE) por exercício, duração da sessão, observação por exercício e observação geral do treino ao final.
- Q: O registro de execução de treino exige plano pago ou consome cota? → A: Livre para todos os planos (Free incluso), sem consumo de cota.
- Q: Como a análise por IA sob demanda se encaixa no modelo de planos/cotas? → A: Exclusiva Premium, com dimensão própria de entitlement ("análises por IA"), ilimitada no Premium e indisponível em Free/Plus.
- Q: Quais bibliotecas padronizadas entram no MVP? → A: Biblioteca de Exercícios e Biblioteca de Alimentos, ambas controladas, com nomenclatura de divisões/treinos padronizada (vocabulário controlado).
- Q: Como as bibliotecas são criadas e mantidas? → A: Catálogo-base interno curado/mantido pelo administrador via CRUD administrativo (fonte única confiável, versionável).
- Q: O que acontece quando a IA propõe um item fora da biblioteca? → A: Validar a resposta contra as bibliotecas e re-solicitar com a restrição; falha controlada sem persistir plano parcial se persistir. A regra de usar apenas itens da biblioteca deve ser declarada explicitamente no prompt enviado à IA.
- Q: Como fica a mídia (fotos/vídeos) das bibliotecas frente ao "fora de escopo" de vídeo? → A: Os itens carregam referências de mídia (URLs de foto/vídeo) cadastradas pelo admin e servidas pelo backend; a produção/hospedagem de conteúdo em vídeo próprio segue fora de escopo.

### Session 2026-05-25

- Q: Treino e dieta são gerados/ajustados juntos (cota combinada) ou separadamente (cota independente)? → A: Separadamente, com dimensões de cota independentes (treino e dieta cada um com sua cota de geração e de ajuste). O usuário escolhe o que gerar/ajustar; não há "geração combinada". No Free, o usuário tem direito a 1 geração de treino **e** 1 geração de dieta por trimestre; no Plus, 1 geração e 1 ajuste de cada um (treino e dieta) por mês.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receber treino e dieta personalizados gerados por IA (Priority: P1)

Um novo usuário se autentica com a conta Google, preenche seu perfil (objetivo, nível de experiência, restrições e biometria básica) e solicita a geração de um plano de treino e/ou de um plano de dieta — cada um é uma ação independente, e o usuário escolhe o que gerar. O coach de IA produz um plano de treino estruturado (divisões, exercícios, séries, repetições, descanso) e/ou um plano de dieta estruturado (refeições, alimentos, porções, macros e calorias), que ficam disponíveis para consulta. Como usuário do plano Free, ele tem direito a 1 geração de treino e a 1 geração de dieta a cada 3 meses (cotas independentes).

**Why this priority**: É o diferencial central do produto e a menor fatia que entrega valor real ao usuário. Sem ela, o Treinaí é apenas mais um app de treino. Entregar somente esta história já constitui um MVP demonstrável.

**Independent Test**: Autenticar via Google em ambiente de teste, criar/recuperar o usuário, salvar um perfil válido e disparar a geração de treino e a geração de dieta (ações separadas); verificar que cada plano estruturado é persistido e pode ser consultado, e que uma segunda tentativa de geração do mesmo tipo dentro de 3 meses (Free) é bloqueada pela cota daquele tipo.

**Acceptance Scenarios**:

1. **Given** um usuário não cadastrado com credencial Google válida, **When** ele faz login social, **Then** o sistema cria o usuário, emite tokens de acesso e refresh próprios da aplicação e retorna o perfil (vazio ou pré-preenchido).
2. **Given** um usuário autenticado sem perfil completo, **When** ele submete dados de perfil válidos (objetivo, nível, restrições, altura, peso, idade, sexo), **Then** o perfil é persistido e fica disponível para gerações.
3. **Given** um usuário Free com perfil completo e sem geração de treino nos últimos 3 meses, **When** ele solicita a geração de treino, **Then** o sistema produz e persiste um plano de treino estruturado vinculado ao usuário e consome 1 unidade da cota de geração de treino (o mesmo vale, de forma independente, para a geração de dieta).
4. **Given** um usuário Free que já gerou um plano de treino nos últimos 3 meses, **When** ele tenta gerar treino novamente, **Then** o sistema recusa a ação com erro de cota claro e código estável, sem chamar a IA (a cota de dieta permanece independente).
5. **Given** uma geração concluída, **When** o usuário consulta seu plano atual de treino e de dieta, **Then** os planos estruturados são retornados com todos os componentes (divisões/refeições, itens, parâmetros).

---

### User Story 2 - Assinar plano pago e desbloquear cotas maiores (Priority: P2)

Um usuário escolhe um plano pago (Plus ou Premium), realiza o pagamento recorrente via Mercado Pago (PIX ou cartão) e, após confirmação do pagamento, passa a ter as cotas e recursos correspondentes ao plano (mais gerações, ajustes e acompanhamento avançado). O sistema reage a eventos de pagamento para ativar, suspender ou cancelar a assinatura.

**Why this priority**: É o que torna o produto sustentável (monetização) e o que libera o valor completo das demais histórias. Depende da existência da geração (US1) para ter o que monetizar.

**Independent Test**: Selecionar um plano pago, iniciar a assinatura, simular um webhook de pagamento aprovado e verificar que a assinatura fica ativa e as cotas do plano são aplicadas; simular um webhook de falha/cancelamento e verificar suspensão/cancelamento.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado no plano Free, **When** ele assina o plano Plus ou Premium e o pagamento é iniciado, **Then** a assinatura é criada no estado "pendente de pagamento".
2. **Given** uma assinatura pendente, **When** o sistema recebe um webhook de pagamento aprovado, **Then** a assinatura passa a "ativa" e as cotas/entitlements do plano correspondente são liberados ao usuário.
3. **Given** uma assinatura ativa, **When** o sistema recebe um webhook de pagamento recusado ou de cancelamento, **Then** a assinatura é suspensa ou cancelada e as cotas retornam ao nível do plano Free.
4. **Given** um webhook recebido, **When** ele é processado, **Then** o resultado é idempotente (reprocessar o mesmo evento não altera o estado nem duplica cobranças).
5. **Given** um usuário cuja ação requer um recurso não incluído no seu plano, **When** ele tenta executá-la, **Then** o sistema recusa com erro claro e código estável.

---

### User Story 3 - Registrar a execução dos treinos (Priority: P2)

Um usuário visualiza o treino prescrito do dia e, durante ou após a sessão, registra o que executou: para cada exercício, as séries realizadas (repetições reais e carga por série), se concluiu ou pulou o exercício, sua percepção de esforço (RPE), a duração da sessão, observações por exercício e uma observação geral do treino ao final. Esse registro é livre para todos os planos e não consome cota.

**Why this priority**: É a atividade central de engajamento diário do app e a fonte de dados que alimenta os ajustes e as análises por IA. Sem ela, o usuário não tem como acompanhar o que efetivamente fez nem a IA tem insumo real de execução.

**Independent Test**: Para um usuário com um plano de treino vigente, registrar uma sessão completa (séries, cargas, RPE, duração, observações) e verificar que o registro é persistido, vinculado ao plano e ao usuário, e fica consultável no histórico — funcionando inclusive no plano Free e sem consumir cota.

**Acceptance Scenarios**:

1. **Given** um usuário com plano de treino vigente, **When** ele registra a execução de uma sessão (por exercício: séries com reps reais e carga, concluído/pulado, RPE; mais duração e observações), **Then** o sistema persiste o registro vinculado ao plano e ao usuário.
2. **Given** um usuário no plano Free, **When** ele registra a execução de treinos, **Then** o registro é aceito sem consumir nenhuma cota.
3. **Given** um usuário com várias sessões registradas, **When** ele consulta o histórico de execução, **Then** o sistema retorna as sessões ordenadas cronologicamente com seus detalhes.
4. **Given** um exercício marcado como pulado, **When** o usuário salva a sessão, **Then** o registro reflete o exercício como não executado, preservando a observação associada.

---

### User Story 4 - Ajustar treino e dieta com base na execução e no progresso (Priority: P3)

Um usuário dos planos Plus ou Premium solicita um ajuste do plano de treino e/ou dieta. O coach de IA considera os registros de execução de treino (US3), o progresso biométrico (US5) e o plano vigente para produzir uma versão ajustada, consumindo cota de ajuste conforme o plano.

**Why this priority**: Aumenta a retenção e o valor percebido, mas só faz sentido após existir geração (US1), monetização (US2) e dados de execução (US3). Não é a fatia mínima de valor.

**Independent Test**: Para um usuário com cota de ajuste, execução registrada e progresso, solicitar um ajuste e verificar que um novo plano ajustado é persistido, que o anterior fica no histórico e que a cota de ajuste é consumida; verificar bloqueio quando a cota de ajuste se esgota.

**Acceptance Scenarios**:

1. **Given** um usuário Plus com execução de treino registrada e cota de ajuste de treino disponível no mês, **When** ele solicita um ajuste de treino, **Then** o sistema gera e persiste um plano ajustado considerando a execução e o progresso, mantém o anterior no histórico e consome 1 unidade da cota de ajuste de treino (a cota de ajuste de dieta é independente).
2. **Given** um usuário Plus que já usou seu ajuste de treino no mês, **When** ele solicita outro ajuste de treino, **Then** o sistema recusa com erro de cota claro (a cota de ajuste de dieta permanece independente).
3. **Given** um usuário Premium, **When** ele solicita ajustes, **Then** o sistema permite ajustes ilimitados.

---

### User Story 5 - Acompanhar evolução biométrica (Priority: P3)

Um usuário registra periodicamente sua biometria — peso corporal e medidas — com datas, e consulta seu histórico e evolução ao longo do tempo. Esse acompanhamento básico está disponível a todos os planos. (O registro de cargas por exercício é tratado em US3; a análise por IA, em US6.)

**Why this priority**: Complementa o valor do produto e alimenta os ajustes (US4) e as análises por IA (US6), mas não é a fatia mínima de valor.

**Independent Test**: Registrar múltiplas medições biométricas em datas distintas e verificar que o histórico e a visão de evolução retornam os dados corretos e ordenados cronologicamente.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado, **When** ele registra peso corporal e medidas com datas, **Then** os registros são persistidos e associados ao usuário.
2. **Given** um usuário com vários registros biométricos, **When** ele consulta seu histórico, **Then** o sistema retorna a evolução ordenada cronologicamente.

---

### User Story 6 - Análise e feedback por IA sob demanda (Priority: P3)

Um usuário Premium solicita uma análise/feedback do coach de IA. A IA considera os registros de execução de treino, o progresso biométrico e os planos vigentes para produzir insights e recomendações sob demanda. Esse acompanhamento avançado é exclusivo do Premium e ilimitado; o disparo proativo automático (sem o usuário pedir) está fora do escopo do MVP.

**Why this priority**: É o diferencial avançado do Premium reframado a partir do "acompanhamento" do prompt original, mas depende de execução (US3) e progresso (US5) já existirem para ter dados a analisar.

**Independent Test**: Para um usuário Premium com execução e progresso registrados, solicitar uma análise e verificar que a IA retorna feedback estruturado e que a interação é auditada; verificar que usuários Free/Plus têm a ação recusada com erro de recurso indisponível para o plano.

**Acceptance Scenarios**:

1. **Given** um usuário Premium com execução e progresso registrados, **When** ele solicita uma análise por IA, **Then** o sistema produz e retorna o feedback estruturado e registra a interação na auditoria.
2. **Given** um usuário Free ou Plus, **When** ele tenta solicitar uma análise por IA, **Then** o sistema recusa com erro de recurso indisponível para o plano.
3. **Given** um usuário Premium, **When** ele solicita análises por IA, **Then** o sistema permite quantidade ilimitada.

---

### User Story 7 - Rastreabilidade e auditoria das interações de IA (Priority: P4)

Para cada chamada ao coach de IA (geração, ajuste ou análise), o sistema registra o prompt enviado, a resposta recebida, o usuário, o timestamp, o uso/tokens, o custo estimado, a latência e o status (sucesso/erro). Esses registros ficam consultáveis para rastreabilidade e análise de custo.

**Why this priority**: Essencial para operação, custo e confiança, mas é uma capacidade transversal de suporte, não uma fatia de valor voltada ao usuário final.

**Independent Test**: Disparar uma geração, um ajuste e uma análise e verificar que cada interação produz exatamente um registro de auditoria completo e consultável, inclusive em caso de erro da IA.

**Acceptance Scenarios**:

1. **Given** qualquer chamada de IA bem-sucedida, **When** ela termina, **Then** um registro de auditoria com prompt, resposta, usuário, timestamp, tokens, custo estimado, latência e status "sucesso" é persistido.
2. **Given** uma chamada de IA que falha ou atinge limite, **When** ela termina, **Then** um registro com status "erro" e a causa é persistido e o usuário recebe uma resposta de degradação graciosa, sem consumir cota de forma indevida.

---

### Edge Cases

- **Credencial Google inválida/expirada**: o login é recusado com erro de autenticação claro; nenhum usuário é criado.
- **Refresh token revogado ou expirado**: a renovação de sessão é negada e o usuário precisa autenticar novamente.
- **Perfil incompleto na geração**: a geração é bloqueada com erro indicando os campos obrigatórios ausentes, antes de consumir cota ou chamar a IA.
- **IA indisponível ou limite do tier Free do Gemini atingido**: o sistema degrada graciosamente, não consome cota e registra a falha na auditoria.
- **Resposta da IA malformada/não-parseável**: a geração falha de forma controlada, não persiste plano parcial, não consome cota e registra o erro.
- **IA referencia item fora da biblioteca**: o sistema valida contra os catálogos e re-solicita com a restrição; persistindo o item inválido após o limite de tentativas, a operação falha de forma controlada sem persistir plano parcial.
- **Biblioteca vazia ou insuficiente**: se não houver itens suficientes no catálogo para atender ao perfil, a geração é recusada com erro claro em vez de inventar itens.
- **Webhook duplicado ou fora de ordem**: o processamento é idempotente e o estado final reflete o evento mais recente válido.
- **Webhook de pagamento de assinatura inexistente/desconhecida**: é rejeitado e auditado sem alterar estado.
- **Cota no limite exato (ex.: virada de período)**: a verificação de cota usa a janela do período corrente do usuário de forma determinística.
- **Downgrade/cancelamento com plano gerado no período**: o usuário mantém acesso de consulta aos planos já gerados; novas ações respeitam a cota do plano vigente.
- **Acesso a recurso de outro usuário**: qualquer tentativa de ler/alterar planos, progresso, registros de execução ou assinatura de outro usuário é negada.
- **Registro de execução sem plano vigente**: registrar a execução de treino exige um plano de treino vigente; na ausência dele, a ação é recusada com erro claro.
- **Análise por IA sem dados suficientes**: quando não há execução nem progresso registrados, a análise por IA sob demanda é recusada (ou retorna aviso de dados insuficientes) sem chamar o Gemini indevidamente.

## Requirements *(mandatory)*

### Functional Requirements

#### identity

- **FR-001**: O sistema MUST permitir login social via Google, validando a credencial recebida do cliente e criando ou recuperando o usuário correspondente.
- **FR-002**: O sistema MUST emitir tokens próprios da aplicação — um token de acesso de curta duração e um refresh token — após autenticação bem-sucedida.
- **FR-003**: O sistema MUST permitir renovar a sessão via refresh token e MUST permitir logout/revogação do refresh token.
- **FR-004**: O sistema MUST armazenar o perfil do usuário com objetivo, nível de experiência, restrições e biometria básica (altura, peso, idade, sexo).
- **FR-005**: O sistema MUST suportar ao menos os papéis `USER` e `ADMIN` e aplicar autorização baseada em papel.
- **FR-006**: A arquitetura de autenticação MUST permitir adicionar novos provedores de login (ex.: Apple) sem reescrita dos módulos dependentes.

#### billing

- **FR-007**: O sistema MUST oferecer três planos no MVP — Free, Plus e Premium — com cotas e recursos distintos e valores configuráveis.
- **FR-008**: O sistema MUST modelar cotas/entitlements por plano de forma genérica e configurável, com dimensões independentes para geração de treino, geração de dieta, ajuste de treino, ajuste de dieta (cada uma com seu limite por período), acesso a análises por IA e acesso a recursos (acompanhamento avançado).
- **FR-009**: O sistema MUST verificar a cota/entitlement ANTES de executar qualquer ação que a consuma, recusando com erro claro e código estável quando excedida ou indisponível para o plano.
- **FR-010**: O sistema MUST suportar assinatura recorrente via Mercado Pago com pagamento por PIX e cartão.
- **FR-011**: O sistema MUST processar webhooks de pagamento para ativar, suspender e cancelar assinaturas, de forma idempotente.
- **FR-012**: O sistema MUST representar os estados de assinatura: ativa, pendente de pagamento, suspensa e cancelada (trial opcional, fora do escopo padrão do MVP).
- **FR-013**: Ao mudar o estado da assinatura, o sistema MUST refletir o ajuste das cotas/entitlements disponíveis ao usuário.

#### training

- **FR-014**: O sistema MUST gerar, via IA, um plano de treino completo a partir do perfil e objetivos do usuário, respeitando a cota de geração.
- **FR-015**: O sistema MUST persistir o plano de treino de forma estruturada: divisões/dias, exercícios (referenciando itens da Biblioteca de Exercícios), séries, repetições, descanso e observações.
- **FR-016**: O sistema MUST permitir ajustar, via IA, um plano de treino existente considerando os registros de execução e o progresso biométrico, consumindo cota de ajuste.
- **FR-017**: O sistema MUST permitir consultar o plano de treino atual e o histórico de planos do usuário.
- **FR-034**: O sistema MUST permitir registrar a execução de uma sessão de treino baseada no plano vigente, capturando, por exercício prescrito: séries realizadas (repetições reais e carga por série), marcação de concluído/pulado, percepção de esforço (RPE) e observação por exercício; além de duração da sessão e observação geral do treino.
- **FR-035**: O registro de execução de treino MUST estar disponível a todos os planos (incluindo Free) e MUST NÃO consumir cota.
- **FR-036**: O sistema MUST permitir consultar o histórico de sessões de treino registradas e MUST disponibilizar esses registros como insumo para os ajustes (FR-016/FR-020) e para as análises por IA (FR-037).

#### nutrition

- **FR-018**: O sistema MUST gerar, via IA, um plano de dieta completo a partir do perfil, objetivos e dados biométricos do usuário, respeitando a cota.
- **FR-019**: O sistema MUST persistir o plano de dieta de forma estruturada: refeições, alimentos (referenciando itens da Biblioteca de Alimentos)/porções, macros e calorias por refeição e totais.
- **FR-020**: O sistema MUST permitir ajustar, via IA, a dieta existente considerando os registros de execução e o progresso biométrico, consumindo cota de ajuste.
- **FR-021**: O sistema MUST permitir consultar a dieta atual e o histórico de dietas do usuário.

#### catalog (bibliotecas)

- **FR-039**: O sistema MUST manter uma Biblioteca de Exercícios e uma Biblioteca de Alimentos como catálogos-base curados pelo administrador (CRUD administrativo), cada item com nome padronizado, atributos relevantes e referências de mídia opcionais (URLs de foto/vídeo).
- **FR-040**: A geração e o ajuste de treino MUST usar exclusivamente exercícios da Biblioteca de Exercícios; a geração e o ajuste de dieta MUST usar exclusivamente alimentos da Biblioteca de Alimentos. Essa restrição MUST ser declarada explicitamente no prompt enviado à IA.
- **FR-041**: O sistema MUST validar as respostas da IA contra as bibliotecas; ao referenciar item inexistente, MUST re-solicitar à IA com a restrição/itens válidos por um número limitado de tentativas e, persistindo a falha, MUST falhar de forma controlada sem persistir plano parcial (consistente com FR-025).
- **FR-042**: A nomenclatura de divisões/treinos compostos no plano MUST seguir um vocabulário controlado padronizado vinculado ao catálogo.
- **FR-043**: Os itens de biblioteca MUST suportar referências de mídia (URLs de foto/vídeo) servidas pelo backend; a produção/hospedagem de conteúdo em vídeo próprio permanece fora de escopo.

#### aicoach

- **FR-022**: O sistema MUST receber pedidos dos módulos training, nutrition e progress, montar o prompt, chamar o Gemini e retornar respostas estruturadas que os módulos persistem como entidades.
- **FR-037**: O sistema MUST oferecer análise/feedback por IA sob demanda (acompanhamento avançado), acionada explicitamente pelo usuário, considerando registros de execução de treino e progresso biométrico; o disparo proativo automático fica fora do escopo do MVP.
- **FR-038**: A análise por IA sob demanda MUST ser exclusiva do plano Premium (ilimitada) e indisponível em Free/Plus, verificada via a dimensão de entitlement "análises por IA".
- **FR-023**: O sistema MUST auditar toda interação de IA registrando prompt, resposta, usuário, timestamp, uso/tokens, custo estimado, latência e status (sucesso/erro).
- **FR-024**: O sistema MUST respeitar os limites de uso da IA (tier Free do Gemini) e degradar graciosamente em caso de indisponibilidade ou limite atingido, sem consumir cota indevidamente.
- **FR-025**: O sistema MUST tratar respostas de IA malformadas ou não-parseáveis como falha controlada, sem persistir planos parciais.

#### progress

- **FR-026**: O sistema MUST permitir registrar a biometria do usuário — peso corporal e medidas — com datas. (Cargas por exercício são registradas via execução de treino, FR-034.)
- **FR-027**: O sistema MUST permitir consultar o histórico e a visão de evolução biométrica do usuário ao longo do tempo.
- **FR-028**: O sistema MUST oferecer acompanhamento básico (consulta de histórico e evolução de execução e biometria) a todos os planos; o acompanhamento avançado é a análise por IA sob demanda, exclusiva do Premium (FR-037/FR-038).

#### Transversais (cross-cutting)

- **FR-029**: O sistema MUST garantir que cada usuário só acesse e altere seus próprios planos, registros de execução, progresso e assinatura.
- **FR-030**: O sistema MUST expor uma API REST versionada sob `/api/v1`, com respostas de erro no formato Problem Details (RFC 7807) e mensagens de erro em inglês.
- **FR-031**: O sistema MUST expor métricas de negócio (gerações, ajustes, conversões de plano) e técnicas para observabilidade.
- **FR-032**: O sistema MUST tratar dados biométricos e de saúde com cuidado de privacidade e MUST NÃO registrar PII em logs.
- **FR-033**: O sistema MUST oferecer ao administrador, em escopo mínimo, a gestão de planos, das bibliotecas (Exercícios e Alimentos, incluindo mídia) e a visualização da auditoria de IA.

### Key Entities *(include if feature involves data)*

- **User**: identidade do cliente B2C; vinculada a um provedor de login externo, possui papéis e um perfil.
- **Profile**: dados pessoais para treino/dieta — objetivo, nível de experiência, restrições e biometria básica (altura, peso, idade, sexo).
- **AuthSession / RefreshToken**: representa a sessão renovável da aplicação; pode ser revogada.
- **Plan (Subscription Plan)**: definição de um plano (Free/Plus/Premium) e suas cotas/entitlements configuráveis.
- **Entitlement / Quota**: regra configurável que define limites por período (gerações, ajustes, análises por IA) e acesso a recursos por plano.
- **Subscription**: assinatura do usuário a um plano, com estado (ativa, pendente, suspensa, cancelada) e vínculo ao provedor de pagamento.
- **PaymentEvent / Webhook**: evento de pagamento recebido do Mercado Pago, processado de forma idempotente.
- **ExerciseLibraryItem**: item do catálogo-base de exercícios, curado pelo admin — nome padronizado, atributos (grupo muscular, equipamento etc.) e referências de mídia (foto/vídeo URLs).
- **FoodLibraryItem**: item do catálogo-base de alimentos, curado pelo admin — nome padronizado, dados nutricionais de referência (macros/calorias por porção) e referências de mídia opcionais.
- **TrainingPlan**: plano de treino estruturado (divisões/dias → exercícios referenciando ExerciseLibraryItem → séries, repetições, descanso, observações), com nomenclatura de divisões padronizada, versionado por geração/ajuste.
- **NutritionPlan**: plano de dieta estruturado (refeições → alimentos referenciando FoodLibraryItem/porções, macros, calorias por refeição e totais), versionado por geração/ajuste.
- **AIInteraction (Audit)**: registro de cada chamada de IA (geração, ajuste ou análise) — prompt, resposta, usuário, timestamp, tokens, custo estimado, latência, status.
- **WorkoutSession (Workout Log)**: registro da execução de uma sessão de treino vinculado ao TrainingPlan e ao usuário — por exercício: séries realizadas (reps reais + carga por série), concluído/pulado, RPE, observação; mais duração da sessão e observação geral, com data.
- **ProgressEntry (Biometria)**: medição biométrica (peso corporal e medidas) com data.
- **AIAnalysis**: feedback/análise por IA sob demanda (Premium), derivado de execução e biometria; sua chamada é registrada como AIInteraction.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um novo usuário consegue ir do login até receber seu primeiro plano de treino e dieta gerados em uma única sessão, sem assistência.
- **SC-002**: 100% das tentativas de ação que consomem cota são verificadas antes da execução; nenhuma ação consome cota além do limite do plano do usuário.
- **SC-003**: 100% das chamadas de IA (sucesso ou erro) produzem exatamente um registro de auditoria consultável.
- **SC-004**: Um webhook de pagamento aprovado resulta na assinatura ativa e nas cotas do plano liberadas, e reprocessar o mesmo webhook não altera o estado nem duplica efeitos.
- **SC-005**: Tentativas de acesso a recursos de outro usuário são negadas em 100% dos casos.
- **SC-006**: Quando a IA está indisponível ou no limite, o usuário recebe uma resposta de degradação clara e nenhuma cota é consumida em 100% desses casos.
- **SC-007**: Nenhum dado de PII ou biométrico sensível aparece nos logs da aplicação.
- **SC-008**: Para o plano Free, no máximo 1 geração de treino e 1 geração de dieta (cotas independentes) são permitidas por janela de 3 meses; para o Plus, no máximo 1 geração e 1 ajuste de treino e 1 geração e 1 ajuste de dieta por mês (cada tipo com cota própria).
- **SC-009**: O registro de execução de treino funciona para usuários de qualquer plano (inclusive Free) sem consumir cota em 100% dos casos.
- **SC-010**: A análise por IA sob demanda é permitida exclusivamente a usuários Premium; tentativas de Free/Plus são recusadas em 100% dos casos.
- **SC-011**: 100% dos planos de treino e dieta persistidos contêm apenas exercícios e alimentos pertencentes às bibliotecas; nenhum item fora do catálogo é salvo.

## Assumptions

- **Geração independente**: treino e dieta são gerados e ajustados em ações separadas, cada uma com sua própria dimensão de cota (geração de treino, geração de dieta, ajuste de treino, ajuste de dieta). Não há "geração combinada": o usuário escolhe o que gerar/ajustar. No Free, 1 geração de treino e 1 geração de dieta por trimestre; no Plus, 1 geração e 1 ajuste de cada tipo por mês.
- **Trial**: o MVP não inclui período de trial; o estado "em trial" fica previsto no modelo, mas desativado por padrão.
- **Valores e regras de plano**: os preços e limites exatos são configuráveis e podem ser ajustados após o MVP sem mudança de comportamento estrutural.
- **Provedor de login no MVP**: apenas Google está ativo; a arquitetura prevê adicionar Apple posteriormente sem reescrita.
- **Provedor de pagamento**: Mercado Pago é o único provedor no MVP, com PIX e cartão.
- **Provedor de IA**: Google Gemini no tier Free; a degradação graciosa cobre limites e indisponibilidade.
- **Idioma**: a API e mensagens de erro são em inglês; o conteúdo gerado de treino/dieta segue o idioma definido pelo coach de IA.
- **Escopo de administração**: funcionalidades de admin são mínimas no MVP (gestão de planos/catálogo e visualização de auditoria).
- **Janela de cota**: períodos de cota (mensal, trimestral) são calculados a partir de uma janela determinística associada ao usuário/assinatura.
- **Registro de execução vs. acompanhamento por IA**: são capacidades distintas — o registro de execução de treino é manual, livre e sem cota (no domínio `training`), enquanto a análise/feedback por IA é sob demanda e exclusiva do Premium (no domínio `aicoach`).
- **Acompanhamento proativo**: a análise por IA do MVP é sempre acionada pelo usuário; o disparo automático/proativo (a IA gerar insights sem solicitação) fica previsto na arquitetura, mas fora do escopo de execução do MVP.
- **Bibliotecas como fonte de verdade**: Exercícios e Alimentos vêm de catálogos-base internos curados pelo admin; a IA é restrita a esses itens via instrução explícita no prompt e validação pós-resposta, e os itens carregam referências de mídia (URLs), não arquivos hospedados pelo backend no MVP.

## Out of Scope (MVP)

- Front-end e aplicativos móveis.
- Login Apple (apenas arquitetura preparada).
- Funcionalidades sociais (feed, amigos, compartilhamento).
- Integração com wearables.
- Produção/hospedagem de conteúdo em vídeo próprio de exercícios (as bibliotecas armazenam apenas referências de mídia — URLs — cadastradas pelo admin).