# FieldOps Backend API

API REST da plataforma **FieldOps** para planejamento, execução em campo, sincronização offline e revisão de inspeções técnicas.

## Pré-requisitos e Execução Rápida

- **Java 21** (Eclipse Temurin / OpenJDK 21)
- **PostgreSQL 16** (ou **Docker** + Compose v2)
- **Maven Wrapper** (`./mvnw` no Linux/macOS ou `.\mvnw.cmd` no Windows)

### Subida local sem Docker (com PostgreSQL rodando)

```text
SPRING_PROFILES_ACTIVE=local \
DB_URL=jdbc:postgresql://localhost:5433/fieldops \
DB_USER=fieldops \
DB_PASSWORD=secret \
JWT_SECRET=segredo-local-com-pelo-menos-32-caracteres \
./mvnw spring-boot:run
```

### Contrato OpenAPI / Swagger

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON (tempo real):** `http://localhost:8080/v3/api-docs`
- **Snapshot versionado:** [openapi/openapi.json](openapi/openapi.json) (validado por `OpenApiSnapshotTest`; para regenerar: `./mvnw test -Dtest=OpenApiSnapshotTest -DupdateOpenApi=true`).
- **Healthcheck:** `GET /actuator/health`

## Usuarios

O modulo `user` implementa a base de identidade da API:

- `POST /users` cria usuarios com senha persistida via BCrypt.
- `GET /users/{id}` retorna o usuario sem expor `password_hash`.
- `PATCH /users/{id}/status` altera a situacao do usuario.
- `DELETE /users/{id}` executa exclusao logica, marcando `status = INACTIVE`.

A tabela `users` e criada por Flyway em `V2__create_users.sql`, com indice unico em `lower(email)` para impedir e-mails duplicados variando maiusculas/minusculas.

## Testes

`./mvnw verify` roda os testes unitarios e os de integracao. Os de integracao (por exemplo `InspectionPersistenceTest`) usam Testcontainers: sobem um PostgreSQL 16 descartavel, aplicam todas as migracoes Flyway e validam o mapeamento JPA contra o esquema real, por isso **exigem Docker em execucao**. A CI do GitHub ja tem Docker.

Para rodar o Maven dentro de um container no Windows, exponha o Docker do host:

```text
docker run --rm -v //var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v "${PWD}:/app" -v fieldops-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```

## Dados de demonstracao

O profile `demo` acrescenta `classpath:db/demo` as locations do Flyway e aplica `db/demo/R__demo_seed.sql`
depois das migracoes de esquema de `db/migration`. Nenhum outro profile carrega o seed, entao ele nunca
chega a producao. Para subir com o cenario:

```text
SPRING_PROFILES_ACTIVE=demo JWT_SECRET=<32+ caracteres> DB_URL=jdbc:postgresql://localhost:5433/fieldops ./mvnw spring-boot:run
```

Usuarios (senha de demonstracao, fraca de proposito, igual para os tres: `Demo@1234`):

| E-mail | Perfil |
| --- | --- |
| `admin@fieldops.local` | ADMIN |
| `supervisor@fieldops.local` | SUPERVISOR |
| `tecnico@fieldops.local` | TECHNICIAN |

Cenario:

- Cliente `Industria ABC`, locais `Fabrica Sorocaba` e `Unidade Itu`.
- Equipamentos `Gerador GD-001` (QR Code `FIELDOPS-GD-001`), `Empilhadeira EMP-01` (`FIELDOPS-EMP-01`) e
  `Extintor EXT-15` (`FIELDOPS-EXT-15`). O QR do gerador para imprimir ou exibir em tela esta em
  [docs/demo/qr-gerador-gd-001.svg](docs/demo/qr-gerador-gd-001.svg).
- Modelo `Checklist de Inspecao de Geradores`, versao 1 publicada, baseado no Anexo C, com quatro secoes e
  dez itens cobrindo os sete tipos de resposta.
- Cinco inspecoes do gerador atribuidas ao tecnico: `ASSIGNED` (a da demonstracao, agendada para o dia em
  que o seed rodou), `IN_PROGRESS` com a primeira secao respondida, `SUBMITTED` aguardando revisao,
  `APPROVED` com uma nao conformidade aberta e `REJECTED` com motivo e o item do horimetro a corrigir.

O seed usa ids fixos e `ON CONFLICT DO NOTHING`: rodar de novo nao duplica registros e tambem nao desfaz o
que foi alterado num ensaio. As inspecoes do seed nao tem eventos de auditoria, entao o historico delas
comeca na primeira acao feita pela API.

Para resetar o cenario do zero, recrie o banco e suba a API de novo no profile `demo`:

```text
psql -h localhost -p 5433 -U fieldops -d fieldops -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

Com Docker Compose, `docker compose down -v` seguido de `docker compose up -d` faz o mesmo e tambem apaga
as evidencias enviadas.

## Executando com Docker

Pre-requisito: Docker com Compose v2. O `docker-compose.yml` sobe tres pecas:

| Servico | Imagem | Porta no host | Dados |
| --- | --- | --- | --- |
| `db` | `postgres:16-alpine` | `5433` | volume `fieldops-pgdata` |
| `api` | build do `Dockerfile` | `8080` | volume `fieldops-evidence` em `/app/evidence` (storage de evidencias) |

```text
cp .env.example .env
docker compose up -d --build
docker compose ps
```

A API so inicia depois do `pg_isready` do banco, aplica as migracoes do Flyway na subida e fica `healthy`
quando `GET /actuator/health` responde `UP`. Swagger em `http://localhost:8080/swagger-ui.html`.
Os dois servicos usam `restart: unless-stopped`.

Variaveis lidas do `.env` (nenhum valor fica na imagem nem no compose versionado):

| Variavel | Obrigatoria | Uso |
| --- | --- | --- |
| `DB_USER` | sim | usuario do PostgreSQL, usado pelo banco e pela API |
| `DB_PASSWORD` | sim | senha do PostgreSQL |
| `JWT_SECRET` | sim | segredo HMAC dos tokens, pelo menos 32 caracteres |
| `SPRING_PROFILES_ACTIVE` | nao (padrao `demo`) | `demo` carrega o cenario de demonstracao |

Os valores do `.env.example` sao de demonstracao; troque `DB_PASSWORD` e `JWT_SECRET` em qualquer outro uso.

A imagem final usa somente o JRE e roda como o usuario `fieldops`, sem root. `docker compose down` preserva
os volumes; `docker compose down -v` apaga banco e evidencias. Logs: `docker compose logs -f api`.

## Inspecoes

`V11__create_inspections.sql` cria `inspections` e `inspection_item_snapshots` (documento 10, 10.8). O banco garante, alem da aplicacao:

- local pertencente ao cliente e equipamento pertencente ao local (FKs compostas);
- versao de modelo publicada (trigger `trg_inspection_published_version`);
- os oito estados de negocio e as quatro prioridades (CHECK);
- cancelamento com data, autor e motivo; horario do servidor somente com o horario do dispositivo correspondente;
- snapshot imutavel (trigger `trg_snapshot_immutable` bloqueia `UPDATE`).

Os horarios vem em pares: `*_at_device` e o relogio do aparelho no momento da acao, `*_at_server` e quando a API recebeu (RN-034, RN-074). O snapshot copia texto e regras do item; `source_template_item_id` e apenas rastro historico.

### Maquina de estados da inspecao

`InspectionStateMachine` concentra as dez transicoes permitidas. Atribuicao e cancelamento usam os endpoints:

```text
POST /api/v1/inspections/{id}/assign  { "technicianId": "uuid" }
POST /api/v1/inspections/{id}/cancel  { "reason": "texto obrigatorio" }
```

Somente ADMIN ou SUPERVISOR podem executar essas operacoes. O tecnico precisa estar ativo e ter o perfil `TECHNICIAN`; transicoes invalidas retornam `409 INSPECTION_INVALID_TRANSITION`. O cancelamento grava autor, data e justificativa na inspecao e registra o estado anterior e o novo em `audit_events`.

`V12__add_inspection_positions.sql` acrescenta a posicao lida uma unica vez no inicio e na conclusao (`start_*` e `completion_*`: latitude, longitude, precisao em metros e horario da leitura; RN-060 a RN-062). E a posicao do aparelho do tecnico naquele momento, nao o endereco do local nem o equipamento. O banco exige posicao completa, coordenadas no intervalo valido e a acao correspondente registrada; sem posicao, as colunas ficam nulas. Na entidade, `FieldPosition` e gravada por `recordStartPosition`/`recordCompletionPosition`, que o caso de uso de inicio e envio (#20) chama com o `location` recebido.

### Listagem administrativa

O acompanhamento usa `GET /api/v1/inspections` e devolve o envelope paginado padrao (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`). Os filtros disponiveis sao `search`, `status`, `technicianId`, `supervisorId`, `clientId`, `siteId`, `equipmentId`, `priority`, `scheduledFrom`, `scheduledTo` e `overdue`; a ordenacao usa `sort=scheduledFor,asc` por padrao e aceita `title`, `priority`, `status`, `scheduledFor`, `createdAt` ou `updatedAt`, com direcao `asc` ou `desc`.

Somente `ADMIN` e `SUPERVISOR` acessam a listagem. O administrador consulta todos os registros ou filtra por supervisor; o supervisor autenticado sempre fica restrito ao proprio escopo, mesmo que outro `supervisorId` seja enviado. O tamanho da pagina e limitado a 100. `overdue` e calculado em tempo de consulta como data prevista anterior ao momento atual e estado `ASSIGNED` ou `IN_PROGRESS`; estados enviados, aprovados e cancelados nao sao considerados atrasados.

Cada linha traz os identificadores e nomes de cliente, local, equipamento e tecnico, alem de prioridade, estado, data prevista, ultima atualizacao, itens aplicaveis, itens respondidos, percentual de progresso e atraso. A contagem de itens do snapshot e feita em agregacao e os relacionamentos sao carregados em lote para evitar N+1; a contagem de respostas sera preenchida pelo modelo de execucao da inspecao.

### Endpoints mobile

O aplicativo usa `GET /api/v1/mobile/inspections?status=&updatedSince=` para sincronizar a lista atribuida ao tecnico autenticado e `GET /api/v1/mobile/inspections/{id}` para baixar o payload completo de uma inspecao. O servidor ignora qualquer escopo enviado pelo cliente: a consulta sempre usa o `technicianId` do token, e uma inspecao atribuida a outro tecnico responde `404`.

O detalhe inclui a versao da inspecao, cliente, local, equipamento, snapshot ordenado por secao e item, respostas existentes e informacoes de revisao. `updatedSince` filtra por `updated_at` para permitir sincronizacao incremental antes do cursor completo. O payload nao inclui listas de outros clientes, locais, equipamentos ou tecnicos.

### Execucao da inspecao

O tecnico responsavel inicia e envia a inspecao por:

```text
POST /api/v1/inspections/{id}/start
POST /api/v1/inspections/{id}/submit
```

Cada request envia somente o horario registrado no dispositivo e, opcionalmente, a posicao capturada. O horario de recebimento e gerado pelo servidor; qualquer campo de horario de servidor enviado pelo cliente e ignorado. `submit` repete no servidor a validacao de itens obrigatorios, observacao e evidencia e responde `422 INSPECTION_REQUIRED_ITEMS_MISSING` com os itens pendentes. A transicao gera auditoria e bloqueia o estado em `SUBMITTED`.

### Respostas do checklist

O tecnico consulta e sincroniza as respostas do snapshot por (o supervisor so le, e apenas das inspecoes sob sua responsabilidade, para conduzir a revisao; o administrador nao acessa):

```text
GET  /api/v1/inspections/{inspectionId}/responses
PUT  /api/v1/inspections/{inspectionId}/responses/{responseId}
POST /api/v1/inspections/{inspectionId}/responses:batch
```

Cada resposta usa somente o campo tipado correspondente ao `responseType` congelado no snapshot. O servidor grava os horarios do dispositivo e do recebimento, atribui `answeredBy` ao tecnico autenticado e rejeita itens de outra inspecao. O `PUT` e idempotente pelo id gerado no dispositivo; o batch devolve `APPLIED` ou `REJECTED` por item sem abortar os demais. Respostas ficam bloqueadas depois de `SUBMITTED`, `UNDER_REVIEW`, `REJECTED` (ate a reabertura) ou `APPROVED`.

### Evidencias

As fotografias sao enviadas como multipart e ficam em storage privado; o PostgreSQL guarda apenas os metadados e a chave gerada pelo servidor:

```text
POST   /api/v1/inspections/{inspectionId}/evidence
GET    /api/v1/inspections/{inspectionId}/evidence
GET    /api/v1/evidence/{id}
DELETE /api/v1/evidence/{id}
```

O MVP aceita JPEG e PNG, valida a assinatura dos bytes, limita o tamanho por `APP_EVIDENCE_MAX_SIZE_BYTES` e nunca usa o nome original para criar a chave. O download exige autenticacao e escopo de tecnico ou supervisor. O upload e idempotente por `id`/`evidenceId`; a exclusao e logica, remove o objeto privado, preserva os metadados e gera `EVIDENCE_REMOVED`. Evidencias de inspecoes `APPROVED` sao somente leitura.

### Nao conformidades

As nao conformidades sao sincronizadas com o id gerado no dispositivo e podem ser consultadas por tecnico ou supervisor no escopo da inspecao:

```text
GET   /api/v1/non-conformities?inspectionId=&severity=&status=&clientId=&page=&size=
GET   /api/v1/non-conformities/{id}
POST  /api/v1/inspections/{inspectionId}/non-conformities
PUT   /api/v1/non-conformities/{id}
PATCH /api/v1/non-conformities/{id}/status
```

O `POST` e idempotente pelo `id`/`nonConformityId`, exige titulo, descricao e severidade (`LOW`, `MEDIUM`, `HIGH` ou `CRITICAL`) e rejeita item/resposta de outra inspecao. A criacao de uma `CRITICAL` sem evidencia e aceita durante a sincronizacao offline; a validacao e aplicada no ponto de consolidacao, imediatamente antes da transicao `submit`, bloqueando o envio com `422` ate que exista uma evidencia ativa vinculada. O MVP possui somente o status `OPEN`; valores futuros sao rejeitados.

Nao ha endpoint de exclusao: depois do envio a nao conformidade permanece no historico e qualquer mudanca e auditada. A criacao gera `NON_CONFORMITY_CREATED`.

### Revisao da inspecao

O supervisor revisa a inspecao enviada por:

```text
GET  /api/v1/inspections/{inspectionId}/reviews
POST /api/v1/inspections/{inspectionId}/begin-review
POST /api/v1/inspections/{inspectionId}/approve  { "comments": "opcional" }
POST /api/v1/inspections/{inspectionId}/reject   { "reason": "obrigatorio", "itemsToCorrect": ["uuid"] }
```

Somente `SUPERVISOR` acessa esses endpoints. `begin-review` exige `SUBMITTED`; aprovar ou reprovar exige `UNDER_REVIEW`, e qualquer outro estado responde `409 INSPECTION_INVALID_TRANSITION`. A reprovacao sem motivo responde `422 INSPECTION_REJECTION_REASON_REQUIRED`, e cada id de `itemsToCorrect` precisa pertencer ao snapshot da inspecao.

Cada decisao cria um registro proprio em `inspection_reviews` (`V19__create_inspection_reviews.sql`) com revisor, data do servidor, motivo, comentario e `review_cycle` sequencial por inspecao; os itens a corrigir ficam em `inspection_review_items`. O banco garante um unico registro por ciclo e o motivo obrigatorio na reprovacao. A aprovacao grava `approved_at` e bloqueia respostas, evidencias e nao conformidades. Os eventos `REVIEW_STARTED`, `INSPECTION_APPROVED` e `INSPECTION_REJECTED` sao registrados em `audit_events`. Nao existe endpoint para o supervisor alterar respostas do tecnico.

### Correcao apos reprovacao

O tecnico assume a correcao de uma inspecao `REJECTED` de forma explicita com `POST /api/v1/inspections/{id}/reopen`, que a devolve para `IN_PROGRESS` e gera `INSPECTION_REOPENED` (ADR-002). Enquanto a inspecao estiver `REJECTED`, respostas sao recusadas com `422 INSPECTION_REOPEN_REQUIRED`; inspecoes `APPROVED` nao podem ser reabertas.

Depois da reabertura, se a ultima reprovacao listou `itemsToCorrect`, somente esses itens aceitam respostas (`422 INSPECTION_ITEM_NOT_IN_CORRECTION`); sem a lista, toda a inspecao volta a ser editavel. Antes de sobrescrever uma resposta, o servidor registra `RESPONSE_UPDATED` com o valor anterior e o novo. O detalhe mobile traz em `review` o motivo e os itens da ultima reprovacao enquanto a correcao estiver pendente, e o novo envio gera um novo ciclo de revisao.

### Auditoria e historico da inspecao

`AuditEventService` e o unico ponto de escrita de `audit_events`. Os casos de uso de inspecao chamam `recordInspectionEvent`, que grava autor, acao, entidade, `inspection_id`, horario do servidor, horario do dispositivo quando o evento veio do campo (`device_occurred_at`), o `X-Request-Id` da requisicao (`request_id`) e, na sincronizacao, o `device_id`. Os valores anterior e novo usam apenas campos permitidos e nunca incluem senha, token ou conteudo de arquivo.

`V20__extend_audit_events.sql` acrescenta essas colunas, vincula os eventos antigos a sua inspecao, cria os indices por inspecao, horario e request e bloqueia `UPDATE`, `DELETE` e `TRUNCATE` na tabela por trigger. Nao existe endpoint de alteracao ou exclusao de evento.

`GET /api/v1/inspections/{id}/history` devolve a linha do tempo ordenada por `occurred_at`, com autor, acao, horario do servidor e do dispositivo. O tecnico so consulta o historico das proprias inspecoes; ADMIN e SUPERVISOR consultam qualquer inspecao. Os autores sao carregados em uma unica consulta.

### Push de sincronizacao offline

O aplicativo envia operacoes acumuladas offline por `POST /api/v1/mobile/sync/push`. O lote aceita no maximo `APP_SYNC_MAX_OPERATIONS` operacoes (100 por padrao); acima disso a API responde `413 SYNC_BATCH_TOO_LARGE`. Cada operacao precisa de `operationId`, `entityType`, `entityId`, `operationType`, `baseVersion`, dependencias opcionais e um payload que identifique a `inspectionId`.

As operacoes sao ordenadas por `dependsOn` e executadas em transacoes independentes. Respostas, evidencias e nao conformidades sao autorizadas contra o tecnico do token; uma inspeccao de outro tecnico ou `APPROVED` e rejeitada. A tabela `sync_processed_operations` registra o resultado para que reenvios retornem `ALREADY_APPLIED` sem repetir a escrita. A resposta sempre inclui `results`, `changes`, `nextCursor` e `serverTime`.

### Política de conflitos na sincronização

- **Divergência de versão (`baseVersion`):** quando `baseVersion` enviada pelo dispositivo é menor/diferente da versão atual no servidor, a operação é recusada com `status = CONFLICT`, `errorCode = SYNC_VERSION_CONFLICT`, `entityVersion` atual e `serverState` (snapshot dos campos atuais no servidor). O servidor **nunca sobrescreve** o dado atual nem descarta silenciosamente o dado pendente do dispositivo.
- **Precedência de estados terminais:** inspeções `APPROVED` (RN-076) ou `CANCELED` (RN-077) no servidor rejeitam mutações tardias com `status = CONFLICT` (`SYNC_INSPECTION_APPROVED_CONFLICT` ou `SYNC_INSPECTION_CANCELED_CONFLICT`) e `serverState` da inspeção, preservando os dados locais no app para análise do técnico.
- **Reenvios idempotentes de conflitos:** o reenvio do mesmo `operationId` previamente marcado como `CONFLICT` mantém o status `CONFLICT`, a versão e o `serverState` persistidos em `sync_processed_operations`.
- **Observabilidade:** conflitos são registrados em log com `requestId`, `deviceId`, `operationId`, tipo/id da entidade e versões envolvidas, sem expor conteúdo sensível.

Evidencias podem ser sincronizadas em dois passos (RN-078). A operacao `INSPECTION_EVIDENCE` com `CREATE`/`UPSERT` aplica apenas os metadados e exige `mimeType`, `sizeBytes` e o `checksum` SHA-256 do arquivo; a evidencia fica com `server_received_at` preenchido e `uploaded_at` nulo ("dados enviados, foto pendente"). A operacao `UPLOAD` envia o binario em `contentBase64`, deve declarar em `dependsOn` a operacao da resposta relacionada e grava o arquivo na mesma `storage_key`. O reenvio do mesmo `evidenceId` nunca cria um segundo registro; com checksum identico o objeto nao e regravado. Uma falha de upload rejeita somente essa operacao e nao reverte as respostas ja aplicadas; o reenvio usa um novo `operationId`. Evidencia com arquivo pendente nao pode ser baixada.

### Pull incremental

O aplicativo baixa as alteracoes do tecnico autenticado por `GET /api/v1/mobile/sync/pull?cursor=&limit=200`.
O cursor e uma string opaca gerada pelo servidor: o app deve apenas persistir o valor de `nextCursor` e
devolve-lo na proxima chamada, sem interpretar ou montar seu conteudo. A ordenacao usa o par `(updated_at, id)`
e o endpoint devolve `hasMore` quando ainda existem alteracoes no lote. No primeiro pull, omita `cursor`.

O pull inclui inspecoes, snapshots, respostas, metadados de evidencias (com `fileReceived` indicando se o arquivo ja chegou), nao conformidades e os clientes,
locais e equipamentos relacionados. Cancelamentos e exclusoes de evidencia aparecem como `DELETE_LOGICAL`
com o estado preservado no payload; nenhum registro e removido do resultado por estar cancelado.

O escopo e aplicado pelo servidor usando o tecnico autenticado. O limite padrao e 200 e o maximo aceito e 1000.

## Contribuição
Veja o [Guia de Contribuição](https://github.com/FieldOps-Project/docs/blob/main/CONTRIBUTING.md) para detalhes sobre fluxo de trabalho, padrões de commit e nomenclatura de branches.

## Autenticacao

A API usa JWT stateless. Configure `JWT_SECRET` com pelo menos 32 caracteres fora do repositorio. Os valores opcionais `JWT_ACCESS_TOKEN_TTL` e `JWT_REFRESH_TOKEN_TTL` aceitam duracoes ISO-8601 e assumem `PT15M` e `P30D`.

- `POST /api/v1/auth/login` recebe `{ "email", "password" }` e devolve access token, refresh token, `expiresIn` e perfil publico.
- `POST /api/v1/auth/refresh` recebe `{ "refreshToken" }` e faz a rotacao do par de tokens.
- `POST /api/v1/auth/logout` recebe `{ "refreshToken" }` e revoga o refresh token apresentado.
- `GET /api/v1/auth/me` exige `Authorization: Bearer <accessToken>` e devolve `id`, `name`, `email` e `role`.

Refresh tokens sao armazenados somente como hash. Logout, troca de senha e mudancas de status invalidam sessoes conforme a versao de sessao do usuario; o access token ja emitido permanece valido apenas ate sua expiracao natural no caso de logout.

## Autorizacao

A API valida autorizacao no servidor com `@PreAuthorize` nos endpoints e nos servicos de aplicacao. O perfil e carregado do claim `role` do JWT, enquanto a identidade usada para escopo vem do `sub` validado pelo filtro JWT e do usuario recarregado no banco.

| Recurso | ADMIN | SUPERVISOR | TECHNICIAN |
| --- | --- | --- | --- |
| Usuarios | CRUD | leitura | nenhum |
| Clientes, locais e equipamentos | CRUD | CRUD | leitura no escopo da inspecao |
| Modelos de inspecao | CRUD | CRUD | nenhum |
| Agendar, atribuir e cancelar inspecao | sim | sim | nao |
| Executar inspecao | nao | nao | somente as proprias |
| Revisar, aprovar e reprovar | nao | sim | nao |
| Evidencias | nao | leitura no escopo | upload, leitura e exclusao antes da aprovacao |
| Nao conformidades | nao | leitura no escopo | criacao e edicao no proprio escopo |
| Auditoria | leitura | leitura no escopo | nenhum |

Os endpoints existentes de usuarios aplicam essa matriz: ADMIN pode gerenciar, ADMIN e SUPERVISOR podem consultar, e os demais perfis recebem `403 AUTH_FORBIDDEN`. Recursos pertencentes a outro tecnico devem filtrar pelo usuario autenticado e devolver `404`, sem aceitar `technicianId` ou outro identificador de escopo no payload.

## Gestao de usuarios

Os endpoints versionados de usuarios exigem perfil ADMIN para alteracoes e aceitam `/users` como alias legado:

```text
GET    /api/v1/users?name=&email=&role=&status=&page=&size=&sort=
GET    /api/v1/users/{id}
POST   /api/v1/users
PUT    /api/v1/users/{id}
PATCH  /api/v1/users/{id}/status
POST   /api/v1/users/{id}/reset-password
```

A listagem usa pagina zero-based, tamanho padrao 20 e limite de 100 itens. O sort aceita somente `name`, `email`, `role`, `status`, `createdAt` ou `updatedAt`, seguido opcionalmente de `asc` ou `desc`.

O reset gera uma senha temporaria aleatoria, persiste somente o hash, invalida as sessoes existentes e devolve a credencial apenas na resposta do endpoint de reset. Senhas nao sao registradas em logs nem em eventos de auditoria.

Operacoes de criacao, edicao, alteracao de perfil, alteracao de situacao e reset geram eventos em `audit_events`, com autor, data, acao e valores antes/depois sem campos sensiveis. A inativacao e logica e preserva o historico do usuario.

## Catálogo de Erros de Negócio

Todas as falhas retornam o envelope padronizado `ApiError` (`timestamp`, `status`, `code`, `message`, `path`, `requestId`, `fieldErrors`):

| HTTP | Código | Contexto |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR`, `INVALID_REQUEST`, `INVALID_SORT`, `INVALID_COORDINATES`, `INVALID_CLIENT_DOCUMENT`, `INVALID_QR_CODE`, `INVALID_TEMPLATE_STATUS`, `SCHEDULED_FOR_REQUIRED`, `RESPONSE_ID_MISMATCH`, `SYNC_CURSOR_INVALID`, `SYNC_LIMIT_INVALID`, `SYNC_PAYLOAD_INVALID`, `SYNC_ENTITY_TYPE_UNSUPPORTED`, `SYNC_OPERATION_TYPE_UNSUPPORTED`, `SYNC_INSPECTION_ID_REQUIRED`, `EVIDENCE_FILE_DESCRIPTION_REQUIRED`, `EVIDENCE_CONTENT_REQUIRED`, `EVIDENCE_CONTENT_INVALID` | Validações de entrada, sintaxe ou parâmetros inválidos |
| `401` | `AUTH_AUTHENTICATION_REQUIRED`, `AUTH_INVALID_CREDENTIALS`, `AUTH_INVALID_REFRESH_TOKEN`, `AUTH_INVALID_TOKEN`, `AUTH_USER_INACTIVE` | Sessão ausente, token expirado/inválido ou usuário inativo |
| `403` | `AUTH_FORBIDDEN` | Usuário autenticado sem perfil autorizado para a operação |
| `404` | `*_NOT_FOUND` (ex: `EQUIPMENT_NOT_FOUND`, `INSPECTION_TEMPLATE_NOT_FOUND`, `TEMPLATE_SECTION_NOT_FOUND`, `TEMPLATE_ITEM_NOT_FOUND`, `TEMPLATE_VERSION_NOT_FOUND`, `TEMPLATE_DRAFT_NOT_FOUND`) | Recurso inexistente ou fora do escopo do usuário |
| `409` | `DATA_CONFLICT`, `EMAIL_ALREADY_EXISTS`, `EQUIPMENT_QR_CODE_ALREADY_EXISTS`, `TEMPLATE_TITLE_ALREADY_EXISTS`, `TEMPLATE_VERSION_PUBLISHED`, `INSPECTION_INVALID_TRANSITION`, `INSPECTION_RESPONSE_VERSION_CONFLICT`, `INSPECTION_RESPONSE_ALREADY_EXISTS`, `INSPECTION_RESPONSE_ID_REUSED`, `NON_CONFORMITY_ID_REUSED`, `NON_CONFORMITY_VERSION_CONFLICT`, `EVIDENCE_ID_REUSED`, `EVIDENCE_VERSION_CONFLICT`, `EVIDENCE_DELETED_CONFLICT` | Conflitos de unicidade, estado ou concorrência otimista |
| `409` *(push)* | `SYNC_VERSION_CONFLICT`, `SYNC_INSPECTION_APPROVED_CONFLICT`, `SYNC_INSPECTION_CANCELED_CONFLICT` | Retornados por item no `SyncOperationResult` quando `status = CONFLICT` |
| `413` | `EVIDENCE_TOO_LARGE`, `SYNC_BATCH_TOO_LARGE` | Arquivo de evidência ou lote de sync excede limite configurado |
| `415` | `EVIDENCE_UNSUPPORTED_FORMAT` | Formato de arquivo não suportado (aceitos: JPEG e PNG) |
| `422` | `CLIENT_INACTIVE`, `INACTIVE_SITE`, `INSPECTION_SITE_INACTIVE`, `SITE_CLIENT_MISMATCH`, `EQUIPMENT_INACTIVE`, `EQUIPMENT_REQUIRED`, `EQUIPMENT_SITE_MISMATCH`, `TECHNICIAN_NOT_ELIGIBLE`, `TEMPLATE_NOT_PUBLISHED`, `TEMPLATE_PUBLISH_INVALID`, `TEMPLATE_VERSION_NOT_PUBLISHED`, `TEMPLATE_VERSION_UNAVAILABLE`, `TEMPLATE_REORDER_MISMATCH`, `TEMPLATE_ITEM_*`, `INSPECTION_NOT_EDITABLE`, `INSPECTION_SNAPSHOT_INVALID`, `INSPECTION_CANCEL_REASON_REQUIRED`, `INSPECTION_LOCATION_INVALID`, `INSPECTION_REQUIRED_ITEMS_MISSING`, `INSPECTION_CRITICAL_NON_CONFORMITY_EVIDENCE_MISSING`, `INSPECTION_APPROVED_READ_ONLY`, `INSPECTION_REOPEN_REQUIRED`, `INSPECTION_RESPONSES_LOCKED`, `INSPECTION_ITEM_NOT_IN_CORRECTION`, `INSPECTION_ITEM_NOT_IN_SNAPSHOT`, `INSPECTION_RESPONSE_INVALID`, `EVIDENCE_READ_ONLY`, `EVIDENCE_LOCATION_INVALID`, `EVIDENCE_RESPONSE_NOT_IN_INSPECTION`, `EVIDENCE_NON_CONFORMITY_NOT_IN_INSPECTION`, `NON_CONFORMITY_ITEM_NOT_IN_INSPECTION`, `NON_CONFORMITY_RESPONSE_NOT_IN_INSPECTION`, `NON_CONFORMITY_STATUS_UNSUPPORTED` | Violações de regras de negócio |
| `500` | `INTERNAL_ERROR` | Falha interna inesperada (sem exposição de stack trace ou SQL; correlacionada por `requestId`) |
