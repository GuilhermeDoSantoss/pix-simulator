# PIX Simulator 🏦

Simulador de transações PIX construído com **Java 17 + Spring Boot 3**, demonstrando na prática:

- ✅ Autenticação JWT com isolamento completo por conta
- ✅ Idempotência via Redis (previne duplo débito em retries)
- ✅ Transações atômicas com log completo
- ✅ Detecção de anomalias via Kafka + Z-Score
- ✅ Documentação Swagger UI
- ✅ Tratamento global de erros

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 17 + Spring Boot 3.2 |
| Segurança | Spring Security + JWT (jjwt 0.12) |
| Banco | PostgreSQL 15 |
| Idempotência | Redis 7 |
| Mensageria | Apache Kafka + Zookeeper |
| Documentação | Springdoc OpenAPI (Swagger UI) |
| Testes | JUnit 5 + Mockito |
| Infra | Docker Compose |
| Frontend | HTML + CSS + JS puro |

---

## Como rodar

### Pré-requisitos
- Java 17+
- Maven 3.8+
- Docker e Docker Compose

### 1. Subir a infraestrutura

```bash
docker-compose up -d
```

Aguarde ~30 segundos para todos os serviços estarem saudáveis:

```bash
docker-compose ps
# Todos devem estar "Up (healthy)"
```

### 2. Rodar a aplicação

```bash
mvn spring-boot:run
```

### 3. Acessar

| Recurso | URL |
|---|---|
| Frontend | `frontend/index.html` (abrir no browser) |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Kafka UI | http://localhost:8090 |
| PostgreSQL | `localhost:5432` (user: pix_user / pass: pix_pass) |
| Redis | `localhost:6379` |

---

## Arquitetura

```
pix-simulator/
├── src/main/java/com/pixsimulator/
│   ├── auth/                   # Autenticação JWT
│   │   ├── controller/         # POST /api/auth/login
│   │   ├── service/            # Lógica de login
│   │   ├── dto/                # AuthDTO (request/response)
│   │   └── security/           # JwtService, Filter, Principal
│   │
│   ├── account/                # Contas bancárias
│   │   ├── controller/         # POST /register, GET /me, POST /deposit
│   │   ├── service/            # AccountService
│   │   ├── dto/                # AccountDTO
│   │   ├── entity/             # Account (JPA)
│   │   └── repository/         # AccountRepository
│   │
│   ├── pix/                    # Módulo PIX
│   │   ├── controller/         # POST /send, GET /history, GET /{id}
│   │   ├── service/            # PixService + IdempotencyService
│   │   ├── dto/                # PixDTO
│   │   ├── entity/             # Transaction, TransactionStatus
│   │   ├── repository/         # TransactionRepository
│   │   └── event/              # PixEvent, PixEventProducer
│   │
│   ├── anomaly/                # Detecção de anomalias
│   │   ├── consumer/           # PixEventConsumer (Kafka), AnomalyController
│   │   ├── service/            # AnomalyDetectorService (Z-Score)
│   │   ├── entity/             # AnomalyAlert, AlertStatus
│   │   └── repository/         # AnomalyAlertRepository
│   │
│   └── shared/                 # Utilitários compartilhados
│       ├── config/             # SecurityConfig, KafkaConfig, RedisConfig, SwaggerConfig
│       ├── exception/          # GlobalExceptionHandler, BusinessException, ResourceNotFoundException
│       └── response/           # ApiResponse<T>
│
├── src/test/java/com/pixsimulator/
│   ├── pix/                    # PixServiceTest, IdempotencyServiceTest
│   └── anomaly/                # AnomalyDetectorServiceTest
│
├── frontend/
│   ├── index.html
│   ├── css/style.css
│   └── js/app.js
│
├── docker-compose.yml
└── README.md
```

---

## Fluxo do PIX com Idempotência

```
FRONTEND                     BACKEND                          REDIS            KAFKA
   │                            │                               │                │
   │ 1. Gera UUID               │                               │                │
   │    idempotencyKey          │                               │                │
   │                            │                               │                │
   │──── POST /pix/send ───────►│                               │                │
   │     {idempotencyKey, ...}  │                               │                │
   │                            │── hasKey("pix:1:uuid") ──────►│                │
   │                            │◄── false (não existe) ────────│                │
   │                            │                               │                │
   │                            │ Valida contas e saldo         │                │
   │                            │ Cria Transaction (PENDING)    │                │
   │                            │ Debita remetente              │                │
   │                            │ Credita destinatário          │                │
   │                            │ Status → COMPLETED            │                │
   │                            │                               │                │
   │                            │── set("pix:1:uuid", "10") ───►│                │
   │                            │                               │  TTL 24h       │
   │                            │                               │                │
   │                            │────────────────── publish PixEvent ───────────►│
   │                            │                                                 │
   │◄─── 200 {COMPLETED} ───────│                               │                │
   │                            │                               │                │
   │ 2. Internet cai, retry     │                               │                │
   │    MESMO UUID              │                               │                │
   │──── POST /pix/send ───────►│                               │                │
   │     {idempotencyKey, ...}  │── hasKey("pix:1:uuid") ──────►│                │
   │                            │◄── true (JÁ EXISTE!) ─────────│                │
   │                            │                               │                │
   │                            │ Busca Transaction pelo ID     │                │
   │                            │ NÃO executa débito/crédito    │                │
   │                            │                               │                │
   │◄── 200 {COMPLETED,         │                               │                │
   │    idempotentResponse:true}│                               │                │
```

---

## Detecção de Anomalia

O algoritmo usa **Z-Score** para identificar transações fora do padrão:

```
z = (valor_atual - média_histórica) / desvio_padrão
```

Se `z > 3.0` (configurável), o PIX é considerado anômalo e um alerta é gerado.

**Exemplo:**
- Histórico: R$50, R$45, R$55, R$48, R$52 (média ≈ R$50, desvio ≈ R$3.74)
- Novo PIX: R$5.000
- Z-Score = (5000 - 50) / 3.74 ≈ **1.323** → ALERTA!

**Como testar:**
1. Crie uma conta e faça 5+ PIX de valores similares (ex: R$50 cada)
2. Faça um PIX de valor muito alto (ex: R$5.000)
3. Aguarde ~2s e consulte `GET /api/anomaly/alerts` ou abra a aba "Alertas" no frontend

---

## Endpoints da API

### Autenticação
| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | ❌ | Login com CPF + senha |

### Contas
| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/api/accounts/register` | ❌ | Criar nova conta |
| GET | `/api/accounts/me` | ✅ | Dados da conta autenticada |
| POST | `/api/accounts/deposit` | ✅ | Depositar saldo (para testes) |

### PIX
| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/api/pix/send` | ✅ | Enviar PIX com idempotência |
| GET | `/api/pix/history` | ✅ | Histórico de transações |
| GET | `/api/pix/{id}` | ✅ | Detalhes de uma transação |

### Anomalias
| Método | Rota | Auth | Descrição |
|---|---|---|---|
| GET | `/api/anomaly/alerts` | ✅ | Alertas da conta autenticada |

---

## Rodando os Testes

```bash
# Todos os testes
mvn test

# Testes de um módulo específico
mvn test -Dtest=PixServiceTest
mvn test -Dtest=IdempotencyServiceTest
mvn test -Dtest=AnomalyDetectorServiceTest
```

Cobertura dos testes:
- `PixServiceTest`: 6 cenários (sucesso, idempotência, saldo insuficiente, chave inexistente, conta inativa, valor exato)
- `IdempotencyServiceTest`: 5 cenários (chave existente, inexistente, salvar, recuperar, namespace por conta)
- `AnomalyDetectorServiceTest`: 6 cenários (anomalia, normal, histórico insuficiente, sem histórico, desvio zero, valor limítrofe)

---

## Conceitos demonstrados

**Idempotência**
O cliente gera um UUID antes de enviar o PIX. O backend armazena esse UUID no Redis após processar com sucesso. Em caso de retry com o mesmo UUID, o backend retorna o resultado cacheado sem reprocessar. Chave no Redis: `pix:{accountId}:{uuid}` com TTL de 24h.

**Isolamento JWT**
O `accountId` fica embutido no payload do token. Em todos os endpoints autenticados, o Spring Security extrai o `accountId` do token — nunca da URL ou do body. É impossível acessar dados de outra conta com um token válido de outra conta.

**Transação atômica**
`@Transactional` no `PixService.sendPix()` garante que débito + crédito + atualização de status ocorrem em uma única transação de banco. Se qualquer operação falhar, tudo é revertido automaticamente.

**Detecção de anomalia via Kafka**
O processamento é assíncrono e desacoplado: o PIX é confirmado imediatamente para o usuário, e o evento é publicado no Kafka. O consumer processa na sequência sem bloquear a resposta. Se o consumer estiver offline, os eventos aguardam no Kafka.

---

## Variáveis de Configuração

| Propriedade | Padrão | Descrição |
|---|---|---|
| `app.jwt.secret` | (ver yml) | Segredo de assinatura do JWT |
| `app.jwt.expiration` | `86400000` | Expiração do token em ms (24h) |
| `app.idempotency.ttl-hours` | `24` | TTL das chaves de idempotência |
| `app.anomaly.threshold-multiplier` | `3.0` | Z-Score mínimo para gerar alerta |
| `app.anomaly.min-history-count` | `5` | Mínimo de transações para análise |
