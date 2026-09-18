# Arquitetura do Backend

Estrutura definida no documento [11 - Arquitetura](https://github.com/FieldOps-Project/docs/blob/main/notion/11-arquitetura.md).
Adotamos o padrão **Clean Architecture** (ou Arquitetura Hexagonal/Ports and Adapters) organizado por **Bounded Contexts** (Contextos Delimitados).

## Estrutura de Pacotes

`	ext
src/main/java/com/fieldops/
├── auth/                 # Autenticação e Autorização (JWT)
├── user/                 # Gestão de Usuários
├── client/               # Gestão de Clientes e Locais (Sites)
├── equipment/            # Gestão de Equipamentos
├── inspection/           # Modelos de Inspeção (Templates) e Execuções
├── evidence/             # Fotografias e Não Conformidades
├── synchronization/      # Processamento de Offline Sync (Outbox)
└── shared/               # Exceções globais, utilitários e infraestrutura comum
`

## Responsabilidade das Camadas (Dentro de cada Contexto)

Em cada pasta de contexto (ex: client/), o código deve ser dividido da seguinte forma:

| Subpacote | Camada correspondente | Responsabilidade |
| --------- | --------------------- | ---------------- |
| domain | Core / Entities | Entidades ricas de negócio, Enums, Exceções de negócio. |
| pplication| Use Cases / Services| Casos de uso (Services), validando regras de negócio e orquestrando o fluxo. |
| infrastructure| Adapters (Out) | Repositórios Spring Data JPA (Interfaces e Implementações), comunicação externa. |
| presentation| Adapters (In) | Controllers REST, DTOs de entrada (Requests) e saída (Responses). |

## Clientes

O modulo `client` gerencia a organizacao atendida pela operacao:

- `GET /api/v1/clients?search=&status=&page=&size=&sort=` lista clientes por nome ou razao social.
- `GET /api/v1/clients/{id}` consulta clientes ativos e inativos.
- `POST /api/v1/clients` cria um cliente ativo por padrao.
- `PUT /api/v1/clients/{id}` atualiza os dados cadastrais sem alterar a situacao.
- `PATCH /api/v1/clients/{id}/status` altera a situacao sem excluir o registro.

Somente ADMIN e SUPERVISOR podem operar o cadastro. Nao existe exclusao fisica. O documento e opcional,
aceita CPF ou CNPJ com ou sem mascara e e persistido somente com digitos; nao ha unicidade global para
esse campo opcional. Clientes inativos continuam disponiveis para consulta historica, mas o servico de
agendamento os rejeita com `CLIENT_INACTIVE`.

A tabela `clients` e criada por Flyway em `V5__create_clients.sql`, com restricao de banco para status e
formato do documento, indices de busca e controle de concorrencia otimista por `version`.

## Locais de inspeção

O modulo `client` tambem gerencia locais vinculados a clientes:

- `GET /api/v1/sites?clientId=&search=&status=&page=&size=&sort=` lista locais globalmente ou por cliente.
- `GET /api/v1/clients/{clientId}/sites` lista somente os locais do cliente informado.
- `GET /api/v1/sites/{id}` consulta locais ativos e inativos.
- `POST /api/v1/sites` cria um local para um cliente existente.
- `PUT /api/v1/sites/{id}` atualiza o local sem aceitar alteracao de `clientId`.
- `PATCH /api/v1/sites/{id}/status` altera a situacao sem excluir o registro.

Somente ADMIN e SUPERVISOR podem operar o cadastro. O `clientId` e obrigatorio na criacao e protegido
por FK e pelo mapeamento imutavel da entidade. Coordenadas devem ser informadas em conjunto e respeitar
latitude entre -90 e 90 e longitude entre -180 e 180. Locais inativos, assim como seus clientes inativos,
sao rejeitados pelo servico de agendamento com erro de regra de negocio.

A tabela `inspection_sites` e criada por Flyway em `V6__create_inspection_sites.sql`, com indices por
cliente e situacao, FK obrigatoria, constraints de status/coordenadas e controle otimista por `version`.

## Regra de Ouro (Dependências)
- domain não conhece o Spring (exceção: anotações lógicas caso inevitável, mas prefira POJOs).
- pplication conhece domain e define contratos para infrastructure (Dependency Inversion).
- DTOs da presentation **NUNCA** devem transitar nas camadas internas de domain. Faça o mapping no controller ou em services de aplicação periféricos.
