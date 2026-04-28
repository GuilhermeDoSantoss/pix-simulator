# PIX Simulator 🏦

Simulador de transações PIX construído com Java 17 e Spring Boot 3. O projeto não é apenas um CRUD — ele demonstra na prática quatro conceitos avançados de engenharia de software que aparecem em sistemas financeiros reais: isolamento de segurança por JWT, idempotência em operações financeiras, transações atômicas no banco de dados e detecção de anomalias com processamento assíncrono via Kafka.

O frontend é servido pelo próprio Spring Boot (sem servidor separado) e se comunica com a API na mesma origem, eliminando qualquer problema de CORS.

---

## Índice

1. [Stack e dependências](#1-stack-e-dependências)
2. [Pré-requisitos](#2-pré-requisitos)
3. [Como rodar](#3-como-rodar)
4. [Acessos após subir](#4-acessos-após-subir)
5. [Estrutura do projeto](#5-estrutura-do-projeto)
6. [Arquitetura geral](#6-arquitetura-geral)
7. [Módulo Auth — JWT e isolamento de contas](#7-módulo-auth--jwt-e-isolamento-de-contas)
8. [Módulo Account — Contas bancárias](#8-módulo-account--contas-bancárias)
9. [Módulo PIX — Transação atômica e idempotência](#9-módulo-pix--transação-atômica-e-idempotência)
10. [Módulo Anomaly — Kafka e detecção por Z-Score](#10-módulo-anomaly--kafka-e-detecção-por-z-score)
11. [Shared — Configurações e tratamento de erros](#11-shared--configurações-e-tratamento-de-erros)
12. [Frontend — Interface web integrada](#12-frontend--interface-web-integrada)
13. [Endpoints da API](#13-endpoints-da-api)
14. [Exemplos de requisição](#14-exemplos-de-requisição)
15. [Testes unitários](#15-testes-unitários)
16. [Infraestrutura Docker](#16-infraestrutura-docker)
17. [Variáveis de configuração](#17-variáveis-de-configuração)
18. [Decisões de design e trade-offs](#18-decisões-de-design-e-trade-offs)

---

## 1. Stack e dependências

| Responsabilidade | Tecnologia | Versão |
|---|---|---|
| Linguagem e framework | Java + Spring Boot | 17 / 3.2.0 |
| Segurança | Spring Security + jjwt | 6.x / 0.12.3 |
| Persistência | Spring Data JPA + Hibernate | 6.x |
| Banco de dados | PostgreSQL | 15 |
| Cache / Idempotência | Spring Data Redis | 7 |
| Mensageria | Apache Kafka + Zookeeper | 7.5.0 (Confluent) |
| Documentação | Springdoc OpenAPI (Swagger UI) | 2.3.0 |
| Validação | Jakarta Bean Validation | 3.x |
| Testes | JUnit 5 + Mockito | 5.x |
| Utilitário | Lombok | 1.18.x |
| Infra local | Docker Compose | 3.8 |
| Frontend | HTML + CSS + JS (sem framework) | — |

---

## 2. Pré-requisitos

- **Java 17** ou superior (`java -version`)
- **Maven 3.8** ou superior (`mvn -version`)
- **Docker** e **Docker Compose** (`docker -v` e `docker compose version`)
- Portas livres: `8080` (app), `5432` (PostgreSQL), `6379` (Redis), `9092` (Kafka), `2181` (Zookeeper), `8090` (Kafka UI)

---

## 3. Como rodar

### Passo 1 — Subir a infraestrutura

```bash
docker-compose up -d
```

Este comando sobe em background: PostgreSQL, Redis, Zookeeper, Kafka e Kafka UI. Aguarde aproximadamente 30 segundos para todos os health checks passarem.

Para verificar se está tudo saudável:

```bash
docker-compose ps
```

Todos os serviços devem aparecer com status `Up (healthy)`. Se algum ainda estiver como `starting`, aguarde mais alguns segundos e repita o comando.

### Passo 2 — Subir a aplicação

```bash
mvn spring-boot:run
```

O Spring Boot vai criar automaticamente as tabelas no PostgreSQL (via `ddl-auto: update`) e os tópicos no Kafka. Aguarde aparecer a mensagem `Started PixSimulatorApplication` no console.

### Passo 3 — Acessar

Abra o browser em `http://localhost:8080`. O frontend já está disponível diretamente nessa URL — não precisa abrir arquivo HTML.

---

## 4. Acessos após subir

| Recurso | URL | Observação |
|---|---|---|
| Frontend (PIX Simulator) | http://localhost:8080 | Interface principal |
| Swagger UI | http://localhost:8080/swagger-ui.html | Documentação interativa da API |
| API Docs (JSON) | http://localhost:8080/api-docs | Schema OpenAPI |
| Kafka UI | http://localhost:8090 | Monitorar tópicos e mensagens |
| PostgreSQL | `localhost:5432` | User: `pix_user` / Pass: `pix_pass` / DB: `pixdb` |
| Redis CLI | `localhost:6379` | `redis-cli keys "pix:*"` para ver chaves de idempotência |

---

## 5. Estrutura do projeto

```
pix-simulator/
│
├── src/main/java/com/pixsimulator/
│   │
│   ├── PixSimulatorApplication.java          # Ponto de entrada da aplicação
│   │
│   ├── auth/                                 # Módulo de autenticação
│   │   ├── controller/AuthController.java    # POST /api/auth/login
│   │   ├── service/AuthService.java          # Lógica de autenticação
│   │   ├── dto/AuthDTO.java                  # LoginRequest / LoginResponse
│   │   └── security/
│   │       ├── JwtService.java               # Gera e valida tokens JWT
│   │       ├── JwtAuthenticationFilter.java  # Intercepta requisições e valida o token
│   │       ├── AccountPrincipal.java         # Representa o usuário autenticado no Spring Security
│   │       └── AccountUserDetailsService.java# Carrega a conta do banco pelo CPF
│   │
│   ├── account/                              # Módulo de contas bancárias
│   │   ├── controller/AccountController.java # /register, /me, /deposit
│   │   ├── service/AccountService.java       # Lógica de negócio das contas
│   │   ├── dto/AccountDTO.java               # CreateRequest / Response / DepositRequest
│   │   ├── entity/Account.java               # Entidade JPA da conta
│   │   └── repository/AccountRepository.java # Queries por CPF, chave PIX, etc.
│   │
│   ├── pix/                                  # Módulo de transações PIX
│   │   ├── controller/PixController.java     # /send, /history, /{id}
│   │   ├── service/
│   │   │   ├── PixService.java               # Orquestra todo o fluxo do PIX (9 passos)
│   │   │   └── IdempotencyService.java       # Verifica e armazena chaves no Redis
│   │   ├── dto/PixDTO.java                   # PixRequest / PixResponse / TransactionHistoryItem
│   │   ├── entity/
│   │   │   ├── Transaction.java              # Entidade JPA da transação
│   │   │   └── TransactionStatus.java        # Enum: PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
│   │   ├── repository/TransactionRepository.java # Queries para histórico e análise de anomalia
│   │   └── event/
│   │       ├── PixEvent.java                 # Payload publicado no Kafka após cada PIX
│   │       └── PixEventProducer.java         # Publica eventos no tópico pix.events
│   │
│   ├── anomaly/                              # Módulo de detecção de anomalias
│   │   ├── consumer/
│   │   │   ├── PixEventConsumer.java         # Consumer Kafka do tópico pix.events
│   │   │   └── AnomalyController.java        # GET /api/anomaly/alerts
│   │   ├── service/AnomalyDetectorService.java # Algoritmo Z-Score para detecção
│   │   ├── entity/
│   │   │   ├── AnomalyAlert.java             # Entidade JPA do alerta gerado
│   │   │   └── AlertStatus.java             # Enum: OPEN, REVIEWED, FALSE_POSITIVE
│   │   └── repository/AnomalyAlertRepository.java
│   │
│   └── shared/                               # Utilitários transversais
│       ├── config/
│       │   ├── SecurityConfig.java           # Spring Security: filtros, CORS, sessão stateless
│       │   ├── KafkaConfig.java              # Criação dos tópicos Kafka
│       │   ├── RedisConfig.java              # RedisTemplate com serialização String
│       │   ├── SwaggerConfig.java            # OpenAPI: esquema bearerAuth, metadados
│       │   └── WebMvcConfig.java             # Serve o frontend estático em /
│       ├── exception/
│       │   ├── BusinessException.java        # Regras de negócio → HTTP 400
│       │   ├── ResourceNotFoundException.java# Recurso não encontrado → HTTP 404
│       │   └── GlobalExceptionHandler.java   # @RestControllerAdvice: centraliza todos os erros
│       └── response/
│           └── ApiResponse.java              # Envelope padrão de todas as respostas JSON
│
├── src/main/resources/
│   ├── application.yml                       # Todas as configurações da aplicação
│   └── static/
│       └── index.html                        # Frontend completo (HTML + CSS + JS inline)
│
├── src/test/java/com/pixsimulator/
│   ├── pix/
│   │   ├── PixServiceTest.java               # 6 cenários do fluxo PIX
│   │   └── IdempotencyServiceTest.java       # 5 cenários da idempotência
│   └── anomaly/
│       └── AnomalyDetectorServiceTest.java   # 6 cenários do detector Z-Score
│
└── docker-compose.yml                        # PostgreSQL + Redis + Kafka + Kafka UI
```

---

## 6. Arquitetura geral

O projeto é um **monolito modular**: um único processo Spring Boot com quatro módulos internos bem delimitados. Cada módulo tem seu próprio pacote, suas próprias entidades, repositórios, serviços e controllers. Eles se comunicam diretamente quando a chamada é síncrona (PIX chama AccountService para buscar contas) e via Kafka quando a comunicação deve ser assíncrona (PIX publica evento, Anomaly consome).

```
Browser
  │
  │  HTTP (mesma origem, sem CORS)
  ▼
Spring Boot :8080
  ├── SecurityFilterChain
  │     └── JwtAuthenticationFilter → valida token em toda requisição autenticada
  │
  ├── /api/auth/**        → AuthController  → AuthService
  ├── /api/accounts/**    → AccountController → AccountService → PostgreSQL
  ├── /api/pix/**         → PixController   → PixService
  │                                              ├── IdempotencyService → Redis
  │                                              ├── AccountService     → PostgreSQL
  │                                              ├── TransactionRepository → PostgreSQL
  │                                              └── PixEventProducer   → Kafka (pix.events)
  │
  ├── /api/anomaly/**     → AnomalyController → AnomalyDetectorService → PostgreSQL
  │
  └── Kafka Consumer (background)
        └── PixEventConsumer ← Kafka (pix.events)
              └── AnomalyDetectorService → PostgreSQL (AnomalyAlert)
```

---

## 7. Módulo Auth — JWT e isolamento de contas

### O que resolve

Em um sistema multiusuário, o maior risco é um usuário acessar dados de outro. O JWT resolve isso de forma stateless: o servidor não guarda sessão — toda a identidade do usuário viaja dentro do próprio token.

### Como o JWT é gerado

Quando o usuário faz login com CPF + senha, o `AuthService` chama o `AuthenticationManager` do Spring Security. Ele usa o `AccountUserDetailsService` para carregar a conta do banco pelo CPF e o `BCryptPasswordEncoder` para verificar a senha. Se as credenciais estiverem corretas, o `JwtService` gera um token assinado com HS256 contendo:

```json
{
  "sub": "12345678901",
  "accountId": 42,
  "iat": 1700000000,
  "exp": 1700086400
}
```

O campo `accountId` é o dado mais importante. Ele identifica univocamente a conta sem depender de nenhum parâmetro da requisição.

### Como o JWT é validado

O `JwtAuthenticationFilter` (que estende `OncePerRequestFilter`) intercepta cada requisição antes de chegar ao controller. Ele:

1. Extrai o token do header `Authorization: Bearer <token>`
2. Valida assinatura e expiração via `JwtService.isTokenValid()`
3. Extrai o CPF do payload
4. Chama `AccountUserDetailsService.loadUserByUsername(cpf)` para carregar a conta do banco
5. Cria um `UsernamePasswordAuthenticationToken` com o `AccountPrincipal`
6. Registra no `SecurityContextHolder`

A partir desse ponto, qualquer controller pode usar `@AuthenticationPrincipal AccountPrincipal principal` para obter o `accountId` sem nenhuma chamada extra ao banco.

### Por que não usar o accountId da URL

```java
// ERRADO — qualquer usuário poderia passar accountId=999 e ver dados de outro
@GetMapping("/accounts/{accountId}")
public ResponseEntity<?> getAccount(@PathVariable Long accountId) { ... }

// CERTO — accountId vem sempre do token, não pode ser manipulado pelo cliente
@GetMapping("/accounts/me")
public ResponseEntity<?> getAccount(@AuthenticationPrincipal AccountPrincipal principal) {
    return accountService.getAccount(principal.getAccountId());
}
```

### Fluxo completo do login

```
POST /api/auth/login
{ "cpf": "12345678901", "password": "senha123" }
                │
                ▼
        AuthenticationManager
                │
                ▼
        AccountUserDetailsService.loadUserByUsername("12345678901")
                │ carrega Account do PostgreSQL
                ▼
        BCryptPasswordEncoder.matches("senha123", hash_do_banco)
                │ OK
                ▼
        JwtService.generateToken(42, "12345678901")
                │ assina com HS256
                ▼
        Retorna:
        {
          "token": "eyJhbGciOiJIUzI1NiJ9...",
          "tokenType": "Bearer",
          "accountId": 42,
          "name": "João Silva",
          "expiresIn": 86400000
        }
```

---

## 8. Módulo Account — Contas bancárias

### Entidade Account

Cada conta armazena: nome, CPF (único, usado como login), senha em hash BCrypt, chave PIX (única no sistema), saldo como `BigDecimal` (obrigatório para valores monetários — `float` e `double` têm erro de ponto flutuante), e flag `active`.

### Por que BigDecimal para saldo

```java
// ERRADO — erro de ponto flutuante
double a = 0.1 + 0.2;  // 0.30000000000000004

// CERTO — precisão exata
BigDecimal a = new BigDecimal("0.1").add(new BigDecimal("0.2")); // 0.3
```

### Mascaramento de CPF

O CPF nunca é retornado completo nas respostas. O `AccountService.toResponse()` aplica mascaramento antes de montar o DTO:

```
12345678901  →  123.***.***-01
```

### Endpoints disponíveis

- `POST /api/accounts/register` — público, cria uma nova conta
- `GET /api/accounts/me` — autenticado, retorna dados da conta do token
- `POST /api/accounts/deposit` — autenticado, adiciona saldo (útil para testes)

---

## 9. Módulo PIX — Transação atômica e idempotência

Este é o coração do projeto. O `PixService.sendPix()` executa 9 passos ordenados com garantias de atomicidade e idempotência.

### O problema da idempotência

Imagine este cenário: o usuário clica "Enviar PIX" de R$ 150. A requisição chega ao servidor, processa, debita a conta — mas a resposta não volta (timeout de rede). O usuário não sabe se o PIX foi ou não. Clica de novo. Sem idempotência: dois PIX de R$ 150, R$ 300 debitados.

A solução é simples: o cliente gera um UUID **antes** de enviar. O servidor usa esse UUID como chave de idempotência. Se a mesma chave aparecer duas vezes, a segunda chamada retorna o resultado da primeira sem reprocessar.

### Como o cliente gera o UUID

```javascript
// Frontend — gera UUID antes de montar o payload
const idempotencyKey = crypto.randomUUID();
// ex: "550e8400-e29b-41d4-a716-446655440000"

fetch('/api/pix/send', {
  method: 'POST',
  body: JSON.stringify({
    idempotencyKey: idempotencyKey,
    receiverPixKey: "destino@email.com",
    amount: 150.00
  })
});
```

### Os 9 passos do PixService

```
1. VERIFICAÇÃO DE IDEMPOTÊNCIA
   └── Redis: existe "pix:{senderId}:{uuid}"?
       SIM → retorna transação original (idempotentResponse: true)
       NÃO → continua

2. VALIDA CONTA REMETENTE
   └── AccountService.findActiveAccountById(senderId)
       → lança ResourceNotFoundException se não existir
       → lança BusinessException se inativa

3. VALIDA CONTA DESTINATÁRIA
   └── AccountRepository.findByPixKey(receiverPixKey)
       → lança ResourceNotFoundException se chave não cadastrada
       → lança BusinessException se conta inativa

4. VALIDA QUE SÃO CONTAS DIFERENTES
   └── sender.getId().equals(receiver.getId())
       → lança BusinessException("PIX para a própria conta")

5. VALIDA SALDO
   └── sender.getBalance().compareTo(amount) < 0
       → lança BusinessException("Saldo insuficiente")

6. CRIA REGISTRO DA TRANSAÇÃO (status: PENDING)
   └── Salva no banco ANTES de movimentar saldo
       → garante log mesmo em caso de falha posterior

7. DÉBITO E CRÉDITO (dentro do @Transactional)
   └── sender.balance  -= amount  →  save
   └── receiver.balance += amount →  save
   └── transaction.status = COMPLETED → save
       Se qualquer passo falhar → rollback automático

8. SALVA CHAVE DE IDEMPOTÊNCIA NO REDIS
   └── set("pix:{senderId}:{uuid}", transactionId, TTL=24h)
       Feito APÓS sucesso — só salva se o PIX foi OK

9. PUBLICA EVENTO NO KAFKA (assíncrono)
   └── PixEvent → tópico "pix.events"
       Feito APÓS commit do banco — não publica evento de rollback
```

### Por que @Transactional é essencial

Sem `@Transactional`, se o servidor cair entre o débito e o crédito, o saldo da conta remetente seria reduzido mas o destinatário não receberia nada. Com `@Transactional`, todas as operações de banco acontecem dentro de uma única transação: ou tudo funciona, ou tudo é revertido.

### Estrutura da chave no Redis

```
pix:{accountId}:{idempotencyKey}

Exemplo:
pix:42:550e8400-e29b-41d4-a716-446655440000

Valor: "137"  (ID da transação no banco)
TTL:   24 horas
```

O namespace com `accountId` evita colisão entre duas contas diferentes que, por acaso, gerem o mesmo UUID.

### Fluxo completo com idempotência

```
CLIENTE                    BACKEND                        REDIS             BANCO
   │                           │                             │                 │
   │  UUID gerado: "abc-123"   │                             │                 │
   │──── POST /api/pix/send ──►│                             │                 │
   │     idempotencyKey:       │──── hasKey("pix:42:abc") ──►│                 │
   │     "abc-123"             │◄─── false ──────────────────│                 │
   │                           │                             │                 │
   │                           │ valida contas, saldo        │                 │
   │                           │──── INSERT Transaction ─────────────────────►│
   │                           │──── UPDATE sender.balance ──────────────────►│
   │                           │──── UPDATE receiver.balance ────────────────►│
   │                           │──── UPDATE Transaction(COMPLETED) ──────────►│
   │                           │                             │                 │
   │                           │──── set("pix:42:abc","137")►│                 │
   │                           │     TTL 24h                 │                 │
   │◄─── 200 COMPLETED ────────│                             │                 │
   │                           │                             │                 │
   │  Internet cai             │                             │                 │
   │  Usuário clica de novo    │                             │                 │
   │──── POST /api/pix/send ──►│                             │                 │
   │     idempotencyKey:       │──── hasKey("pix:42:abc") ──►│                 │
   │     "abc-123" (mesmo)     │◄─── true ───────────────────│                 │
   │                           │                             │                 │
   │                           │──── get("pix:42:abc") ─────►│                 │
   │                           │◄─── "137" ──────────────────│                 │
   │                           │                             │                 │
   │                           │ busca Transaction #137 no banco               │
   │                           │ NÃO movimenta nenhum saldo                    │
   │◄─── 200 COMPLETED ────────│                             │                 │
   │     idempotentResponse:   │                             │                 │
   │     true                  │                             │                 │
```

### Status da transação

| Status | Significado |
|---|---|
| `PENDING` | Criada, aguardando processamento. Saldo ainda não movimentado. |
| `PROCESSING` | Em processamento (reservado para fluxos assíncronos futuros). |
| `COMPLETED` | Concluída com sucesso. Débito e crédito realizados. |
| `FAILED` | Falhou após início do processamento. Saldo revertido. |
| `CANCELLED` | Cancelada antes do processamento. |

---

## 10. Módulo Anomaly — Kafka e detecção por Z-Score

### Por que Kafka aqui

O PIX precisa responder rápido para o usuário. A análise de anomalia não é bloqueante — não faz sentido o usuário esperar o resultado da análise para receber a confirmação do PIX. Kafka resolve isso com desacoplamento assíncrono:

- O `PixService` publica o evento no Kafka e já responde ao usuário
- O `PixEventConsumer` processa o evento em background, no seu próprio ritmo
- Se o consumer estiver offline, os eventos ficam no Kafka e são processados quando ele voltar

### Tópicos

| Tópico | Produzido por | Consumido por | Partições |
|---|---|---|---|
| `pix.events` | `PixEventProducer` | `PixEventConsumer` | 3 |
| `pix.alerts` | Reservado para extensão | — | 1 |

A chave de publicação no tópico `pix.events` é o `senderId` (como String). Isso garante que todas as transações de uma mesma conta vão para a mesma partição, mantendo a ordem cronológica por usuário — importante para que o histórico usado no cálculo de anomalia seja correto.

### Algoritmo Z-Score

O Z-Score (também chamado de escore padronizado) mede quantos desvios padrão um valor está afastado da média do conjunto. É amplamente usado em detecção de anomalias por ser simples, interpretável e eficaz.

**Fórmula:**
```
z = (valor_atual - média_histórica) / desvio_padrão_amostral
```

**Implementação:**

```java
// 1. Busca transações dos últimos 30 dias (excluindo a atual)
List<BigDecimal> historicalAmounts = history.stream()
    .filter(t -> !t.getId().equals(transactionId))
    .map(Transaction::getAmount)
    .collect(toList());

// 2. Calcula média: Σ(xi) / n
BigDecimal average = calculateAverage(historicalAmounts);

// 3. Calcula desvio padrão amostral: √( Σ(xi - μ)² / (n-1) )
BigDecimal stdDev = calculateStandardDeviation(historicalAmounts, average);

// 4. Calcula Z-Score
BigDecimal zScore = amount.subtract(average)
    .divide(stdDev, 4, RoundingMode.HALF_UP);

// 5. Verifica threshold (padrão: 3.0)
if (zScore.compareTo(threshold) > 0) {
    createAlert(...);
}
```

**Exemplo com números reais:**

| Transação histórica | Valor |
|---|---|
| PIX 1 | R$ 50,00 |
| PIX 2 | R$ 45,00 |
| PIX 3 | R$ 55,00 |
| PIX 4 | R$ 48,00 |
| PIX 5 | R$ 52,00 |

- Média: R$ 50,00
- Desvio padrão amostral: ≈ R$ 3,74
- Threshold (3.0): R$ 50 + 3 × R$ 3,74 = R$ **61,22**

Se o próximo PIX for de R$ 5.000:
```
z = (5000 - 50) / 3.74 ≈ 1.323
```
O Z-Score de **1.323** é muito maior que o threshold de **3.0** → alerta gerado.

### Condições para análise ser executada

1. A conta deve ter pelo menos **5 transações históricas** (configurável). Abaixo disso, não há base estatística suficiente para calcular um desvio padrão confiável.
2. O desvio padrão deve ser maior que zero. Se todos os PIX históricos tiverem exatamente o mesmo valor, o desvio é zero e não é possível calcular Z-Score (divisão por zero é tratada explicitamente).

### O que fica salvo no alerta

Cada alerta registra: ID da conta, ID da transação, valor do PIX, média histórica, desvio padrão, Z-Score calculado, quantidade de transações usadas na base e o motivo em texto legível. Por exemplo:

```
PIX de R$ 5.000,00 está 1323,5 desvios padrão acima da média histórica de 
R$ 50,00 (desvio padrão: R$ 3,74, baseado em 5 transações nos últimos 30 dias)
```

---

## 11. Shared — Configurações e tratamento de erros

### ApiResponse — envelope padrão

Todas as respostas da API seguem o mesmo formato JSON:

```json
{
  "success": true,
  "message": "PIX realizado com sucesso",
  "data": { ... },
  "timestamp": "2024-01-15T10:30:00"
}
```

Em erros de validação, o campo `data` traz um mapa dos campos com problema:

```json
{
  "success": false,
  "message": "Erro de validação nos campos",
  "data": {
    "cpf": "CPF deve conter exatamente 11 dígitos numéricos",
    "amount": "Valor mínimo do PIX é R$ 0,01"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

### GlobalExceptionHandler — tratamento centralizado

O `@RestControllerAdvice` captura todas as exceções lançadas pelos controllers e as converte em respostas HTTP padronizadas. Sem ele, o Spring retornaria stack traces em HTML — inseguro e inconsistente.

| Exceção | HTTP | Quando ocorre |
|---|---|---|
| `BusinessException` | 400 | Saldo insuficiente, conta inativa, PIX para si mesmo |
| `ResourceNotFoundException` | 404 | Conta com ID inexistente, chave PIX não cadastrada |
| `MethodArgumentNotValidException` | 422 | Campos inválidos no DTO (CPF com letras, valor negativo, etc.) |
| `BadCredentialsException` | 401 | CPF ou senha incorretos no login |
| `DisabledException` | 401 | Tentativa de login com conta inativa |
| `Exception` (genérica) | 500 | Qualquer erro não previsto — loga stack trace, retorna mensagem genérica |

### SecurityConfig

Principais decisões de configuração:

- **CSRF desabilitado**: APIs REST stateless com JWT não precisam de CSRF. O CSRF protege contra ataques via cookies de sessão — como não usamos cookies, não há superfície de ataque.
- **Sessão STATELESS**: o servidor não armazena nenhum estado de sessão. Cada requisição é autônoma e carrega sua própria identidade no JWT.
- **CORS**: configurado para aceitar qualquer origem em desenvolvimento. Em produção, substituir `allowedOriginPatterns("*")` pelo domínio real do frontend.
- **BCrypt**: algoritmo padrão da indústria para hash de senhas. Gera um salt aleatório em cada chamada — dois hashes da mesma senha são diferentes, mas ambos verificam corretamente com `matches()`.

### WebMvcConfig

Resolve o problema de servir o frontend integrado ao backend:

```java
// Rota raiz → index.html
registry.addViewController("/").setViewName("forward:/index.html");

// Arquivos estáticos de src/main/resources/static/
registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/static/");
```

---

## 12. Frontend — Interface web integrada

### Como está integrado ao backend

O frontend está em `src/main/resources/static/index.html`. O Spring Boot serve esse arquivo diretamente em `http://localhost:8080/`. Como o HTML e a API estão na **mesma origem**, o browser não aplica restrições de CORS — qualquer `fetch('/api/...')` funciona sem configuração adicional.

```javascript
// Toda comunicação usa caminho relativo — mesma origem, sem CORS
const API = '/api';

async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  
  const res  = await fetch(API + path, { ...opts, headers });
  const body = await res.json();
  
  if (!res.ok) throw new Error(body.message || `Erro ${res.status}`);
  return body;
}
```

### Gestão do token JWT no frontend

O token é armazenado no `sessionStorage` (não `localStorage`). Isso significa que persiste enquanto a aba do browser estiver aberta, mas é limpo quando o usuário fecha a aba — comportamento adequado para sistemas financeiros.

```javascript
// Ao fazer login
sessionStorage.setItem('pix_token', token);
sessionStorage.setItem('pix_aid',   accountId);

// Ao recarregar a página — restaura sessão automaticamente
const saved = sessionStorage.getItem('pix_token');
if (saved) {
  api('/accounts/me')
    .then(res => enterApp(res.data.name))
    .catch(() => sessionStorage.clear()); // token expirado
}
```

### Como a idempotência funciona no frontend

```javascript
// UUID gerado ANTES de montar o payload
function newIdpKey() {
  idpKey = crypto.randomUUID();
  document.getElementById('idp-key').textContent = idpKey;
}

// Payload sempre inclui o mesmo UUID enquanto não gerar outro
async function sendPix() {
  lastPayload = {
    idempotencyKey: idpKey,   // mesmo UUID em retries
    receiverPixKey: key,
    amount: val,
    description: desc
  };
  await execPix(lastPayload);
}

// Botão "Reenviar" usa o mesmo lastPayload (mesmo UUID)
async function retryPix() {
  await execPix(lastPayload); // backend detecta duplicata
}
```

### Segurança no frontend — escape de HTML

Todo conteúdo dinâmico inserido no DOM passa pela função `esc()` para prevenir XSS:

```javascript
function esc(s) {
  return String(s)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
```

---

## 13. Endpoints da API

### Autenticação (público)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/auth/login` | Login com CPF e senha. Retorna token JWT. |

### Contas

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| `POST` | `/api/accounts/register` | ❌ | Cria nova conta bancária. |
| `GET` | `/api/accounts/me` | ✅ | Retorna dados da conta autenticada. |
| `POST` | `/api/accounts/deposit` | ✅ | Deposita saldo (endpoint de teste). |

### PIX

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| `POST` | `/api/pix/send` | ✅ | Envia PIX com garantia de idempotência. |
| `GET` | `/api/pix/history` | ✅ | Lista todas as transações enviadas pela conta. |
| `GET` | `/api/pix/{id}` | ✅ | Detalhes de uma transação específica. |

### Anomalias

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| `GET` | `/api/anomaly/alerts` | ✅ | Lista alertas de anomalia da conta autenticada. |

---

## 14. Exemplos de requisição

### Criar conta

```bash
curl -X POST http://localhost:8080/api/accounts/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "João Silva",
    "cpf": "12345678901",
    "password": "senha123",
    "pixKey": "joao@email.com",
    "initialBalance": 1000.00
  }'
```

Resposta:
```json
{
  "success": true,
  "message": "Conta criada com sucesso",
  "data": {
    "id": 1,
    "name": "João Silva",
    "cpf": "123.***.***-01",
    "pixKey": "joao@email.com",
    "balance": 1000.00,
    "active": true,
    "createdAt": "2024-01-15T10:00:00"
  }
}
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"cpf": "12345678901", "password": "senha123"}'
```

Resposta:
```json
{
  "success": true,
  "message": "Login realizado com sucesso",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "accountId": 1,
    "name": "João Silva",
    "expiresIn": 86400000
  }
}
```

### Enviar PIX

```bash
curl -X POST http://localhost:8080/api/pix/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
    "receiverPixKey": "maria@email.com",
    "amount": 150.00,
    "description": "Almoço"
  }'
```

Resposta (primeiro envio):
```json
{
  "success": true,
  "message": "PIX processado",
  "data": {
    "transactionId": 10,
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
    "status": "COMPLETED",
    "amount": 150.00,
    "receiverPixKey": "maria@email.com",
    "receiverName": "Maria Souza",
    "message": "PIX realizado com sucesso",
    "idempotentResponse": false,
    "processedAt": "2024-01-15T10:05:00"
  }
}
```

Resposta (mesmo UUID, segundo envio — idempotência):
```json
{
  "data": {
    "transactionId": 10,
    "status": "COMPLETED",
    "amount": 150.00,
    "idempotentResponse": true,
    "message": "Requisição duplicada detectada. Retornando resultado original."
  }
}
```

### Exemplo de erro — saldo insuficiente

```json
{
  "success": false,
  "message": "Saldo insuficiente. Disponível: R$ 100,00 | Solicitado: R$ 500,00",
  "timestamp": "2024-01-15T10:10:00"
}
```

---

## 15. Testes unitários

Os testes são unitários puros: usam `@ExtendWith(MockitoExtension.class)` sem subir o contexto Spring. Isso os torna rápidos (milissegundos cada) e independentes de infraestrutura (sem banco, sem Redis, sem Kafka).

```bash
# Rodar todos os testes
mvn test

# Rodar uma classe específica
mvn test -Dtest=PixServiceTest
mvn test -Dtest=IdempotencyServiceTest
mvn test -Dtest=AnomalyDetectorServiceTest
```

### PixServiceTest — 6 cenários

| Cenário | O que verifica |
|---|---|
| PIX bem-sucedido | Débito correto no remetente (1000 - 150 = 850), crédito no destinatário (500 + 150 = 650), evento publicado no Kafka, chave salva no Redis |
| Idempotência ativada | Com UUID duplicado, `accountService` nunca é chamado, `accountRepository.save()` nunca é chamado, `pixEventProducer` nunca é chamado |
| Saldo insuficiente | Lança `BusinessException` com mensagem de saldo, nenhuma operação de banco executada |
| Chave PIX inexistente | Lança `ResourceNotFoundException` ao não encontrar destinatário |
| PIX para si mesmo | Lança `BusinessException` ao detectar mesmo ID nas contas |
| Destinatário inativo | Lança `BusinessException` com mensagem de conta inativa |

### IdempotencyServiceTest — 5 cenários

| Cenário | O que verifica |
|---|---|
| Chave existente | `isDuplicate()` retorna `true` quando Redis tem a chave |
| Chave inexistente | `isDuplicate()` retorna `false` quando Redis não tem a chave |
| Salvar chave | `saveKey()` chama `opsForValue().set()` com chave, valor e TTL corretos |
| Recuperar ID | `getTransactionId()` converte o valor String do Redis para Long |
| Namespace por conta | Contas diferentes com mesmo UUID geram chaves Redis diferentes |

### AnomalyDetectorServiceTest — 6 cenários

| Cenário | O que verifica |
|---|---|
| Anomalia detectada | R$ 5.000 com histórico de ~R$ 50 gera alerta com Z-Score > 3 |
| Valor normal | R$ 60 com histórico de ~R$ 50 não gera alerta |
| Histórico insuficiente | Menos de 5 transações → análise ignorada, sem alerta |
| Sem histórico | Conta nova sem transações → análise ignorada |
| Desvio padrão zero | Todos os valores iguais → sem divisão por zero, sem alerta |
| Valor limítrofe | Valor exatamente abaixo do threshold → não gera alerta |

---

## 16. Infraestrutura Docker

O `docker-compose.yml` sobe cinco serviços:

### PostgreSQL
Banco principal. Armazena contas, transações e alertas de anomalia. O Spring Boot cria as tabelas automaticamente via `ddl-auto: update`. Em produção, substituir por migrations com Flyway ou Liquibase.

### Redis
Armazena as chaves de idempotência no formato `pix:{accountId}:{uuid}` com TTL de 24 horas. Configurado com `appendonly yes` para persistência em disco.

### Zookeeper
Coordenador obrigatório para o Kafka (versões < 3.x). Gerencia eleição de líder, metadados dos tópicos e sincronização entre brokers.

### Kafka
Message broker. Recebe eventos do `PixEventProducer` no tópico `pix.events` e entrega ao `PixEventConsumer`. O producer usa `acks=all` para garantir que a mensagem foi gravada antes de confirmar.

### Kafka UI
Interface web em `http://localhost:8090` para visualizar tópicos, mensagens, consumers e offsets. Útil para observar os eventos sendo publicados em tempo real durante os testes.

### Comandos úteis

```bash
# Subir tudo
docker-compose up -d

# Ver status dos serviços
docker-compose ps

# Acompanhar logs de um serviço
docker-compose logs -f kafka

# Ver chaves de idempotência no Redis
docker exec -it pix-redis redis-cli keys "pix:*"

# Ver valor de uma chave específica
docker exec -it pix-redis redis-cli get "pix:1:550e8400-e29b-41d4-a716-446655440000"

# Listar tópicos do Kafka
docker exec -it pix-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Ver mensagens de um tópico
docker exec -it pix-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic pix.events \
  --from-beginning

# Parar tudo
docker-compose down

# Parar e remover volumes (apaga todos os dados)
docker-compose down -v
```

---

## 17. Variáveis de configuração

Todas as configurações ficam em `src/main/resources/application.yml`. Em produção, os valores sensíveis devem ser injetados via variáveis de ambiente.

| Propriedade | Padrão | Descrição |
|---|---|---|
| `server.port` | `8080` | Porta da aplicação |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/pixdb` | URL do PostgreSQL |
| `spring.datasource.username` | `pix_user` | Usuário do banco |
| `spring.datasource.password` | `pix_pass` | Senha do banco |
| `spring.data.redis.host` | `localhost` | Host do Redis |
| `spring.data.redis.port` | `6379` | Porta do Redis |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Broker Kafka |
| `app.jwt.secret` | (string longa) | Segredo HS256. Em produção: mínimo 256 bits, variável de ambiente |
| `app.jwt.expiration` | `86400000` | Expiração do token em ms (padrão: 24h) |
| `app.idempotency.ttl-hours` | `24` | TTL das chaves no Redis |
| `app.anomaly.threshold-multiplier` | `3.0` | Z-Score mínimo para gerar alerta. Aumente para menos alertas |
| `app.anomaly.min-history-count` | `5` | Mínimo de transações históricas para análise |
| `kafka.topics.pix-events` | `pix.events` | Nome do tópico de eventos PIX |
| `kafka.topics.pix-alerts` | `pix.alerts` | Nome do tópico de alertas |

---

## 18. Decisões de design e trade-offs

**Monolito modular em vez de microserviços**
O escopo do projeto não justifica a complexidade operacional de múltiplos serviços. Um monolito modular entrega os mesmos conceitos (separação de responsabilidades, comunicação via eventos) com muito menos overhead. Se o volume crescer, cada módulo pode ser extraído para um serviço independente sem reescrever a lógica de negócio.

**Redis para idempotência em vez de banco**
A verificação de idempotência acontece em toda requisição de PIX. Usar o banco para isso adicionaria latência e carga desnecessária. O Redis opera em memória com latência de sub-milissegundo e o TTL nativo elimina a necessidade de jobs de limpeza.

**Kafka para anomalia em vez de chamada síncrona**
Se a análise de anomalia fosse síncrona, o tempo de resposta do PIX dependeria do tempo de análise. Com Kafka, o PIX responde imediatamente e a análise ocorre em background. O custo é a consistência eventual: o alerta pode aparecer alguns segundos após o PIX.

**ddl-auto: update em vez de Flyway**
Para um simulador de desenvolvimento, `update` é suficiente e mais simples. Em produção real, o correto é usar Flyway ou Liquibase para ter controle total sobre as mudanças de schema e garantir que migrations sejam executadas exatamente uma vez.

**sessionStorage em vez de localStorage no frontend**
`sessionStorage` limpa automaticamente ao fechar a aba, enquanto `localStorage` persiste indefinidamente. Para um sistema financeiro, tokens não devem persistir mais do que a sessão ativa do usuário.

**BigDecimal em vez de Double para valores monetários**
`Double` tem erro de ponto flutuante intrínseco ao formato IEEE 754. Para valores monetários, qualquer imprecisão é inaceitável. `BigDecimal` com `precision=15, scale=2` garante precisão exata até R$ 9.999.999.999.999,99.
