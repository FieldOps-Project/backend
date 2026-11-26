# Relatório de Revisão de Segurança — AC-SECURITY (Issue #35)

## 1. Resumo Executivo

Foi realizada a auditoria sistemática de segurança sobre todos os endpoints, filtros, configurações de CORS, logs, tratamento de exceções e histórico Git do repositório `backend`, tomando como base os 8 critérios do **AC-SECURITY (Documento 17.18)**.

### Achados e Correções Aplicadas

| Severidade | Achado | Ação / Correção |
| --- | --- | --- |
| **Média** | `SecurityConfiguration` não habilitava `.cors(Customizer.withDefaults())` explicitamente no `HttpSecurity`, podendo bloquear requisições preflight `OPTIONS` antes do `CorsConfig` (`WebMvcConfigurer`). | Adicionado `.cors(Customizer.withDefaults())` em `SecurityConfiguration.java` e validado em teste automatizado com origem autorizada vs não autorizada. |
| **OK** | Endpoints protegidos e autorização server-side | Todos os endpoints (exceto login/refresh/logout, Swagger e `/actuator/health`) exigem autenticação (`anyRequest().authenticated()`) e possuem `@PreAuthorize(AuthorizationPolicies.*)` em controller e service. |
| **OK** | Isolamento horizontal e evidências privadas | Consultas mobile de inspeções, respostas, evidências, não-conformidades e histórico filtram por `technicianId`/`supervisorId` e devolvem `404` quando fora do escopo. Download de evidência exige sessão, valida escopo e envia `Cache-Control: no-store`. |
| **OK** | Logs e tratamento de erros | `GlobalExceptionHandler` devolve `ApiError` com `requestId` e mensagem genérica para erros 500 (`INTERNAL_ERROR`), sem stack traces, SQL ou nomes de classe. Nenhum log registra senha, `accessToken`, `refreshToken` ou bytes de arquivos. |
| **OK** | Segredos e histórico Git | Nenhum arquivo `.env`, chave privada ou segredo de produção foi encontrado na árvore atual ou no histórico Git. `JWT_SECRET` e `DB_PASSWORD` são injetados por variáveis de ambiente. |

---

## 2. Inventário Completo de Endpoints

| Caminho | Método | Perfil Exigido | Escopo Aplicado | Verificado |
| --- | --- | --- | --- | --- |
| `/api/v1/auth/login` | POST | Público | Usuário ativo + senha BCrypt | Sim |
| `/api/v1/auth/refresh` | POST | Público | Refresh token válido + sessionVersion | Sim |
| `/api/v1/auth/logout` | POST | Público | Revoga refresh token | Sim |
| `/api/v1/auth/me` | GET | Autenticado | Usuário autenticado | Sim |
| `/api/v1/users` | POST | `ADMIN` | Global | Sim |
| `/api/v1/users`, `/{id}` | GET | `ADMIN`, `SUPERVISOR` | Global | Sim |
| `/api/v1/users/{id}`, `/status`, `/reset-password`, `DELETE` | PUT/PATCH/POST/DELETE | `ADMIN` | Global (invalida sessão) | Sim |
| `/api/v1/clients`, `/{id}`, `/status` | GET/POST/PUT/PATCH | `ADMIN`, `SUPERVISOR` | Global | Sim |
| `/api/v1/sites`, `/clients/{id}/sites`, `/sites/{id}`, `/status` | GET/POST/PUT/PATCH | `ADMIN`, `SUPERVISOR` | Global | Sim |
| `/api/v1/equipment`, `/sites/{id}/equipment`, `/by-qr/{qr}` | GET | Autenticado | Global | Sim |
| `/api/v1/equipment`, `/{id}`, `/status` | POST/PUT/PATCH | `ADMIN`, `SUPERVISOR` | Global | Sim |
| `/api/v1/inspection-templates/**` | GET/POST/PUT/PATCH/DELETE | `ADMIN`, `SUPERVISOR` | Global | Sim |
| `/api/v1/inspections` | GET/POST | `ADMIN`, `SUPERVISOR` | Supervisor restrito ao próprio escopo | Sim |
| `/api/v1/inspections/{id}` | GET/PUT | `ADMIN`, `SUPERVISOR` | Escopo administrativo | Sim |
| `/api/v1/inspections/{id}/assign`, `/cancel` | POST | `ADMIN`, `SUPERVISOR` | Escopo administrativo | Sim |
| `/api/v1/inspections/{id}/reviews`, `/begin-review`, `/approve`, `/reject` | GET/POST | `SUPERVISOR` | Escopo do supervisor | Sim |
| `/api/v1/inspections/{id}/history` | GET | `ADMIN`, `SUPERVISOR`, `TECHNICIAN` | Técnico só vê próprias inspeções (404) | Sim |
| `/api/v1/mobile/inspections`, `/{id}` | GET | `TECHNICIAN` | Somente inspeções atribuídas ao técnico (404) | Sim |
| `/api/v1/inspections/{id}/start`, `/submit`, `/reopen` | POST | `TECHNICIAN` | Somente técnico atribuído (404) | Sim |
| `/api/v1/inspections/{id}/responses`, `:batch` | GET/PUT/POST | `TECHNICIAN` (`SUPERVISOR` leitura) | Técnico atribuído; supervisor só da inspeção que supervisiona (404) | Sim |
| `/api/v1/inspections/{id}/evidence`, `/evidence/{id}` | GET/POST/DELETE | `TECHNICIAN` (`SUPERVISOR` leitura) | Escopo da inspeção + `no-store` (404) | Sim |
| `/api/v1/non-conformities`, `/inspections/{id}/non-conformities` | GET/POST/PUT/PATCH | `TECHNICIAN`, `SUPERVISOR` (leitura) / `TECHNICIAN` (escrita) | Escopo da inspeção (404) | Sim |
| `/api/v1/mobile/sync/pull`, `/push` | GET/POST | `TECHNICIAN` | Somente registros do técnico autenticado | Sim |

---

## 3. Evidência Automatizada

Todos os critérios do AC-SECURITY são verificados automaticamente pela suíte `AcSecurityVerificationTest` (`src/test/java/com/fieldops/security/AcSecurityVerificationTest.java`) executada no pipeline de CI.
