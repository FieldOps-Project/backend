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

## Regra de Ouro (Dependências)
- domain não conhece o Spring (exceção: anotações lógicas caso inevitável, mas prefira POJOs).
- pplication conhece domain e define contratos para infrastructure (Dependency Inversion).
- DTOs da presentation **NUNCA** devem transitar nas camadas internas de domain. Faça o mapping no controller ou em services de aplicação periféricos.
