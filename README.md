# backend
backend projeto fieldops


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

O tecnico consulta e sincroniza as respostas do snapshot por:

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
