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

`V12__add_inspection_positions.sql` acrescenta a posicao lida uma unica vez no inicio e na conclusao (`start_*` e `completion_*`: latitude, longitude, precisao em metros e horario da leitura; RN-060 a RN-062). E a posicao do aparelho do tecnico naquele momento, nao o endereco do local nem o equipamento. O banco exige posicao completa, coordenadas no intervalo valido e a acao correspondente registrada; sem posicao, as colunas ficam nulas. Na entidade, `FieldPosition` e gravada por `recordStartPosition`/`recordCompletionPosition`, que o caso de uso de inicio e envio (#20) chama com o `location` recebido.

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
