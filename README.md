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
