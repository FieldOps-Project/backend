# backend
backend projeto fieldops


## Usuarios

O modulo `user` implementa a base de identidade da API:

- `POST /users` cria usuarios com senha persistida via BCrypt.
- `GET /users/{id}` retorna o usuario sem expor `password_hash`.
- `PATCH /users/{id}/status` altera a situacao do usuario.
- `DELETE /users/{id}` executa exclusao logica, marcando `status = INACTIVE`.

A tabela `users` e criada por Flyway em `V2__create_users.sql`, com indice unico em `lower(email)` para impedir e-mails duplicados variando maiusculas/minusculas.

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
