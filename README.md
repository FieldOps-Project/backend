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
