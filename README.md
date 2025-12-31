# LightQ

A lightweight, high-performance message queue service built with Spring Boot 4.0.1 and Java 21. LightQ provides RESTful APIs for asynchronous message processing with:
- Consumer groups (multi-tenant queues)
- Redis caching for fast access
- MongoDB persistence with TTL
- At-least-once delivery using reservation + ack/nack
- Dead Letter Queue (DLQ) with replay

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Cache-red.svg)](https://redis.io/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Database-green.svg)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Table of Contents
- [1. Project Overview](#1-project-overview)
- [2. Key Features](#2-key-features)
- [3. Architecture Overview](#3-architecture-overview)
- [4. Project Structure](#4-project-structure)
- [5. Technology Stack](#5-technology-stack)
- [6. Getting Started](#6-getting-started)
- [7. Local Development](#7-local-development)
- [8. Configuration](#8-configuration)
  - [8.1 MongoDB](#81-mongodb)
  - [8.2 Redis](#82-redis)
  - [8.3 Security](#83-security)
  - [8.4 Rate Limiting](#84-rate-limiting)
  - [8.5 LightQ Core](#85-lightq-core)
  - [8.6 Reservation / Ack / DLQ](#86-reservation--ack--dlq)
  - [8.7 Thread Pool](#87-thread-pool)
  - [8.8 Redis Client Tuning](#88-redis-client-tuning)
  - [8.9 Cache/Index Bounds](#89-cacheindex-bounds)
- [9. Rate Limiting](#9-rate-limiting)
- [10. API Documentation](#10-api-documentation)
- [11. Health Check](#11-health-check)
- [12. Logging & Observability](#12-logging--observability)
- [13. Testing](#13-testing)
- [14. Docker Deployment](#14-docker-deployment)
- [15. Security](#15-security)
- [16. Performance Considerations](#16-performance-considerations)
- [17. Troubleshooting](#17-troubleshooting)
- [18. Contributing](#18-contributing)
- [19. License](#19-license)
- [20. Support](#20-support)
- [21. Quick Reference Card](#21-quick-reference-card)

## 1. Project Overview

LightQ is a production-ready Spring Boot application implementing a simple yet robust distributed message queue. It enables asynchronous communication between producers and consumers via REST, with isolated consumer groups. Delivery semantics are at-least-once via a reservation model (visibility timeout) followed by explicit acknowledgement.

### Use Cases
- Microservices async messaging
- Background jobs / task queues
- Event ingestion with TTL
- Load leveling and buffering
- Multi-tenant queues via consumer groups
- Safety via DLQ and replay

### Design Philosophy
- Simplicity: Clean REST API, minimal config
- Performance: Redis-first with MongoDB persistence
- Reliability: Reservation + ack/nack, DLQ and replay
- Observability: Structured logging with correlation IDs

## 2. Key Features

### Core
- Push: Enqueue message to a consumer group
- Batch Push: Efficient bulk enqueue with pipelined variadic LPUSH and async bulk inserts
- Pop (Reserve): Reserve oldest available message with a visibility timeout
- Ack/Nack: Explicit acknowledgement to complete or re-queue
- Extend Visibility: Increase reservation window
- View: Inspect messages (admin)
- DLQ: Auto-move over-retried messages; view and replay

### Delivery Semantics
- At-least-once delivery with reservation (visibility timeout)
- Delivery count increments on each reservation (pop)
- Messages exceeding max delivery attempts are moved to DLQ
- Replay DLQ entries individually or in batches

### Performance & Reliability
- Redis cache for sub-millisecond list operations
- MongoDB durable storage with TTL on createdAt
- Async thread pool for background operations (e.g., TTL index, etc.)
- Cache-first strategies where applicable

### Operations & Security
- HTTP Basic Auth with USER/ADMIN roles
- Fixed-window per-second rate limiting
- Correlation ID tracing
- OpenAPI (Swagger UI)
- Actuator health endpoint
- Dockerized

## 3. Architecture Overview

### System Architecture

```mermaid
flowchart TB
  subgraph Clients
    P[Producer]
    C[Consumer]
    A[Admin]
  end

  subgraph LightQ_Service
    F[CorrelationIdFilter]
    RL[RateLimitingInterceptor]
    API[MessageController]

    subgraph Service_Layer
      PUSH[PushMessageService]
      POP[PopMessageService]
      ACK[AcknowledgementService]
      VIEW[ViewMessageService]
      DLQS[DlqService]
    end

    CACHE[CacheService]
    ASYNC[ThreadPoolTaskExecutor]
  end

  subgraph Data_Layer
    R[(Redis Lists)]
    M[(MongoDB Collections)]
    D[(MongoDB DLQ Collections)]
  end

  P -->|POST /queue/push| F
  C -->|GET /queue/pop| F
  C -->|POST /queue/ack| F
  C -->|POST /queue/nack| F
  C -->|POST /queue/extend-visibility| F
  A -->|GET /queue/view| F
  A -->|GET /queue/dlq/view| F
  A -->|POST /queue/dlq/replay| F

  F --> RL --> API
  API --> PUSH --> CACHE
  API --> POP --> CACHE
  API --> ACK
  API --> VIEW
  API --> DLQS

  CACHE <--> R
  ASYNC -.-> M
  POP --> M
  ACK --> M
  VIEW --> M
  DLQS --> M
  DLQS --> D
```

### Data Flow

#### Push
1. Client POSTs to /queue/push with consumerGroup
2. Validate; generate UUID; create message with consumed=false
3. Add to Redis list immediately (fast path)
4. Persist to MongoDB; ensure TTL index on createdAt
5. Return MessageResponse

#### Pop (Reservation)
1. Client GET /queue/pop with consumerGroup
2. System tries cache candidates first, then DB
3. Reserve the oldest unconsumed message by:
   - Incrementing deliveryCount
   - Setting reservedUntil = now + visibilityTimeoutSeconds
   - Leaving consumed=false
4. If deliveryCount exceeds maxDeliveryAttempts, move to DLQ and repeat
5. Return reserved message (client must ack or nack)

#### Ack / Nack / Extend Visibility
- Ack: Mark consumed=true, clear reservedUntil
- Nack: Set reservedUntil=now; optionally record reason; becomes immediately visible for re-delivery
- Extend: Extend reservedUntil by requested seconds if still reserved

#### DLQ
- When deliveryCount exceeds maxDeliveryAttempts, copy message to DLQ collection with failure metadata and mark original consumed=true
- Admins can view DLQ entries and replay selected IDs (reinsert to main collection + cache; remove from DLQ)

## 4. Project Structure

```
src/main/java/com/al/lightq/
├── LightQApplication.java
├── LightQConstants.java
├── config/
│   ├── AsyncConfig.java
│   ├── CorrelationIdFilter.java
│   ├── LightQProperties.java
│   ├── RateLimitProperties.java
│   ├── RateLimitingInterceptor.java
│   ├── RedisConfig.java
│   ├── SecurityConfig.java
│   ├── StartupLogger.java
│   └── WebConfig.java
├── controller/
│   └── MessageController.java
├── dto/
│   ├── ErrorResponse.java
│   └── MessageResponse.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── RateLimitExceededException.java
├── model/
│   └── Message.java
├── service/
│   ├── AcknowledgementService.java
│   ├── CacheService.java
│   ├── DlqService.java
│   ├── PopMessageService.java
│   ├── PushMessageService.java
│   └── ViewMessageService.java
└── resources/
    ├── application.properties
    └── logback-spring.xml
```

## 5. Technology Stack

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| Framework | Spring Boot | 4.0.1 | Application framework |
| Language | Java | 21 | LTS |
| Web | spring-boot-starter-web | 4.0.1 | REST |
| Monitoring | Spring Boot Actuator | 4.0.1 | Health, metrics |
| Cache | Redis | 7.x | In-memory lists |
| Cache Client | Spring Data Redis + Caffeine | Managed by Spring Boot 4.0.x | RedisTemplate + in-memory caches |
| Database | MongoDB | 7.0 | Durable storage |
| DB Client | Spring Data MongoDB | Managed by Spring Boot 4.0.x | MongoTemplate |
| Security | spring-boot-starter-security | 4.0.1 | Basic Auth |
| Docs | springdoc-openapi | 2.6.0 | Swagger UI |
| Build | Maven | 3.9+ | Build & deps |
| Container | Docker + Distroless | latest | Containerization |
| Testing | JUnit 5, Mockito | latest | Unit/integration tests |

## 6. Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+
- Redis 6.0+
- MongoDB 5.0+

For Docker, only Docker and Docker Compose are required.

## 7. Local Development

1) Clone
```bash
git clone https://github.com/Alak-Das/LightQ.git
cd LightQ
```

2) Infra (Option A: Docker Compose)
```bash
cp .env.example .env
docker compose up -d mongodb redis
```

Option B: Local installations (start mongod and redis as you prefer).

3) Configure
- application.properties has sensible defaults (match docker-compose.yml)

4) Build
```bash
mvn clean package
```

5) Run
```bash
mvn spring-boot:run
# or the jar
java -jar target/lightq-0.0.1-SNAPSHOT.jar
```

6) Verify
- Swagger UI: http://localhost:8080/swagger-ui/index.html
  - Note: endpoints are protected by HTTP Basic auth (see Security section).

## 8. Configuration

All properties can be set via environment variables in containerized deployments. The repository’s application.properties provides opinionated defaults.

### 8.1 MongoDB
| Variable | Default | Description |
|----------|---------|-------------|
| MONGO_URI | mongodb://admin:password@localhost:27017 | Connection string |
| MONGO_DB | lightq-db | Database name |

### 8.2 Redis
| Variable | Default | Description |
|----------|---------|-------------|
| SPRING_DATA_REDIS_HOST | localhost | Redis host |
| SPRING_DATA_REDIS_PORT | 6379 | Redis port |
| SPRING_DATA_REDIS_PASSWORD | (empty) | Redis password |

### 8.3 Security
| Variable | Default | Description |
|----------|---------|-------------|
| SECURITY_USER_USERNAME | user | USER role |
| SECURITY_USER_PASSWORD | password | USER password |
| SECURITY_ADMIN_USERNAME | admin | ADMIN role |
| SECURITY_ADMIN_PASSWORD | adminpassword | ADMIN password |

### 8.4 Rate Limiting
| Variable | Default | Description |
|----------|---------|-------------|
| RATE_LIMIT_PUSH_PER_SECOND | 100 | Push RPS limit (<=0 disables) |
| RATE_LIMIT_POP_PER_SECOND | 200 | Pop RPS limit (<=0 disables) |

Note: .env.example demonstrates values and may differ (e.g., 10 for POP). If unset, application.properties defaults apply.

### 8.5 LightQ Core
| Variable | Default | Description |
|----------|---------|-------------|
| LIGHTQ_MESSAGE_ALLOWED_TO_FETCH | 50 | /queue/view max results |
| LIGHTQ_PERSISTENCE_DURATION_MINUTES | 1440 | MongoDB TTL (minutes) |
| LIGHTQ_CACHE_TTL_MINUTES | 30 | Redis TTL (minutes) |

### 8.6 Reservation / Ack / DLQ
| Variable | Default | Description |
|----------|---------|-------------|
| LIGHTQ_VISIBILITY_TIMEOUT_SECONDS | 30 | Reservation window |
| LIGHTQ_MAX_DELIVERY_ATTEMPTS | 5 | Move to DLQ above this |
| LIGHTQ_DLQ_SUFFIX | -dlq | DLQ collection suffix |
| LIGHTQ_DLQ_TTL_MINUTES | (unset) | TTL for DLQ collection; unset disables |

### 8.7 Thread Pool
- CORE_POOL_SIZE=5
- MAX_POOL_SIZE=10
- QUEUE_CAPACITY=25
- THREAD_NAME_PREFIX=DBDataUpdater-
- ALLOW_CORE_THREAD_TIMEOUT=true
- AWAIT_TERMINATION_SECONDS=30

### 8.8 Redis Client Tuning
| Variable | Default | Description |
|----------|---------|-------------|
| LIGHTQ_REDIS_COMMAND_TIMEOUT_SECONDS | 5 | Lettuce command timeout (seconds) |
| LIGHTQ_REDIS_SHUTDOWN_TIMEOUT_SECONDS | 2 | Lettuce shutdown timeout (seconds) |
| LIGHTQ_REDIS_POOL_MAX_TOTAL | 64 | Max total pooled connections |
| LIGHTQ_REDIS_POOL_MAX_IDLE | 32 | Max idle pooled connections |
| LIGHTQ_REDIS_POOL_MIN_IDLE | 8 | Min idle pooled connections |

### 8.9 Cache/Index Bounds
| Variable | Default | Description |
|----------|---------|-------------|
| LIGHTQ_CACHE_MAX_ENTRIES_PER_GROUP | 100 | Max cached entries per consumer group (affects Redis LTRIM window) |
| LIGHTQ_INDEX_CACHE_MAX_GROUPS | 1000 | Max groups tracked for ensured MongoDB indexes |
| LIGHTQ_INDEX_CACHE_EXPIRE_MINUTES | 60 | Expiration (minutes) for index-tracking cache entries |

## 9. Rate Limiting

Fixed window per-second limits per endpoint (push/pop). HTTP 429 is returned when exceeded.

application.properties:
```properties
rate.limit.push-per-second=100
rate.limit.pop-per-second=200
```

## 10. API Documentation

Base URL: http://localhost:8080/queue

Authentication: HTTP Basic Auth
OpenAPI:
- Swagger UI: http://localhost:8080/swagger-ui/index.html (requires Basic Auth)
- JSON: http://localhost:8080/v3/api-docs

| Role | Username | Password | Permissions |
|------|----------|----------|-------------|
| USER | user | password | push, pop, ack, nack, extend-visibility |
| ADMIN | admin | adminpassword | USER permissions + view, dlq/view, dlq/replay |

Common headers
- consumerGroup (required for all endpoints): 1-50 chars, alphanumeric, hyphens, underscores
- messageCount (optional for view)
- consumed (optional for view): yes|no

---

### 1) Push
Add a message to a consumer group.

Request
```http
POST /queue/push
Content-Type: text/plain
consumerGroup: my-group

Hello, World!
```

cURL
```bash
curl -u user:password -X POST "http://localhost:8080/queue/push" \
  -H "Content-Type: text/plain" -H "consumerGroup: my-group" \
  -d "Hello, World!"
```

Validation
- consumerGroup: ^[a-zA-Z0-9-_]{1,50}$
- content: not blank, <= 1MB

Response 200
```json
{"id":"<uuid>","content":"Hello, World!","createdAt":"2025-01-01T10:30:00"}
```

### 1a) Batch Push (Bulk)
Add multiple messages to a consumer group in one request. Minimizes per-message overhead and uses a single Redis pipeline per call, with async bulk inserts to MongoDB.

Request
```http
POST /queue/batch/push
Content-Type: application/json
consumerGroup: my-group

["message-1", "message-2", "message-3"]
```

cURL
```bash
curl -u user:password -X POST "http://localhost:8080/queue/batch/push" \
  -H "consumerGroup: my-group" -H "Content-Type: application/json" \
  -d '["message-1","message-2","message-3"]'
```

Response 200
```json
[
  {"id":"<uuid1>","content":"message-1","createdAt":"..."},
  {"id":"<uuid2>","content":"message-2","createdAt":"..."},
  {"id":"<uuid3>","content":"message-3","createdAt":"..."}
]
```

Validation
- consumerGroup: ^[a-zA-Z0-9-_]{1,50}$
- each content: not blank, <= 1MB
- body must be a non-empty JSON array of strings

---

### 2) Pop (Reservation)
Reserve the oldest available message for processing. The message remains unconsumed until acked.

Request
```http
GET /queue/pop
consumerGroup: my-group
```

cURL
```bash
curl -u user:password "http://localhost:8080/queue/pop" \
  -H "consumerGroup: my-group"
```

Response 200
```json
{"id":"<uuid>","content":"...","createdAt":"..."}
```

Response 404
- No reservable messages currently available

Notes
- Increments deliveryCount and sets reservedUntil to now + visibilityTimeoutSeconds
- Client must call ack (success) or nack (failure) or extend-visibility if needed
- If not acked in time, message becomes visible again (re-delivered)

---

### 3) Ack
Mark a reserved message as consumed (complete).

Request
```http
POST /queue/ack?id=<messageId>
consumerGroup: my-group
```

cURL
```bash
curl -u user:password -X POST "http://localhost:8080/queue/ack?id=<id>" \
  -H "consumerGroup: my-group" -i
```

Response
- 200 OK on success (idempotent: already-consumed is also treated as success)
- 404 Not Found if message doesn’t exist in group

---

### 4) Nack
Negative acknowledgement. Immediately re-queues the message by setting reservedUntil to now. Optionally record reason.

Request
```http
POST /queue/nack?id=<messageId>&reason=<optional>
consumerGroup: my-group
```

cURL
```bash
curl -u user:password -X POST "http://localhost:8080/queue/nack?id=<id>&reason=timeout" \
  -H "consumerGroup: my-group" -i
```

Response
- 200 OK if updated
- 404 Not Found / no-op if not found or already consumed

---

### 5) Extend Visibility
Extend visibility timeout for a reserved message.

Request
```http
POST /queue/extend-visibility?id=<messageId>&seconds=<n>
consumerGroup: my-group
```

cURL
```bash
curl -u user:password -X POST "http://localhost:8080/queue/extend-visibility?id=<id>&seconds=60" \
  -H "consumerGroup: my-group" -i
```

Response
- 200 OK if extended
- 400 Bad Request if not currently reserved or message not found

---

### 6) View (Admin)
View messages (consumed/unconsumed).

Request
```http
GET /queue/view
consumerGroup: my-group
messageCount: 10
consumed: no
```

Notes
- When consumed=no or unset, combines Redis + MongoDB (excluding duplicates), sorted by createdAt asc
- When consumed=yes, queries MongoDB only

---

### 7) DLQ View (Admin)
View entries in DLQ.

Request
```http
GET /queue/dlq/view?limit=50
consumerGroup: my-group
```

Response 200
```json
[
  { "id":"...", "content":"...", "consumerGroup":"...", "createdAt":"...", "deliveryCount": 6, "failedAt":"...", "dlqReason":"max-deliveries" }
]
```

---

### 8) DLQ Replay (Admin)
Replay DLQ entries back to the main queue (and Redis), removing them from DLQ.

Request
```http
POST /queue/dlq/replay
consumerGroup: my-group
Content-Type: application/json

["id1","id2","id3"]
```

Response 200
```json
3
```

---

### Error Response Format
```json
{
  "timestamp": "2025-01-01T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid input",
  "path": "/queue/ack",
  "requestId": "..."
}
```

## 11. Health Check

Actuator:
```
GET /actuator/health
```

Returns application status and dependency checks (Redis, MongoDB). This endpoint is publicly accessible.

## 12. Logging & Observability

- Correlation IDs
  - X-Request-Id or X-Correlation-Id respected; auto-generated if absent
  - Added to MDC; echoed back in response headers and error JSON
- Log format (logback-spring.xml) includes requestId, method, path, consumerGroup
- Key events:
  - Push: cache insert, DB save, TTL index
  - Pop: reservation results, deliveryCount
  - Ack/Nack/Extend: outcomes
  - DLQ: insertion and replay

Runtime log level may be adjusted via Actuator if enabled for loggers.

## 13. Testing

JUnit 5 and Mockito tests cover:
- Config (Async, Security, Redis, RateLimiting)
- Controller (MessageController)
- Services (Push/Pop/View/Cache, Acknowledgement)
- Exceptions (Global handler)

Run:
```bash
mvn test
```

## 14. Docker Deployment

See docker-compose.yml for production-ready stack:
- lightq-service
- mongodb (+ healthcheck)
- redis (+ healthcheck)

Quick start:
```bash
cp .env.example .env
docker compose up -d --build
```

Notes:
- The service container uses MONGO_URI with authSource=admin to connect to the MongoDB service.
- To enable Redis authentication, set SPRING_DATA_REDIS_PASSWORD and uncomment the redis command in docker-compose.yml.

## 15. Security

- HTTP Basic Auth with BCrypted in-memory users
- Roles:
  - USER: push, pop, ack, nack, extend-visibility
  - ADMIN: USER + view, dlq/view, dlq/replay
- CSRF disabled (stateless REST)
- Recommendation: put behind TLS-terminating proxy; enable Redis/Mongo auth; rotate credentials

## 16. Performance Considerations

- Pop tries Redis candidates first; falls back to MongoDB
- Visibility timeout limits reservation window; tune based on processing time
- Increase thread pool for higher async throughput
- Monitor deliveryCount and DLQ volume
- Suggested indexes:
  - createdAt (TTL)
  - Compound read-path index: { consumed: 1, reservedUntil: 1, createdAt: 1 }

## 17. Troubleshooting

Common issues:
- Connection refused (Redis/Mongo): verify services, credentials, networking
- Postman error 'url is empty': ensure the selected Environment has a non-empty Current Value for baseUrl. In Postman, an empty environment variable overrides the collection variable and resolves {{baseUrl}} to empty. In Runner/Newman, pass the environment file via -e.
- Slow pop: review cache hit rate, indexes, network latency
- Excessive redeliveries: increase visibility timeout; fix consumer processing; inspect DLQ
- Frequent 429: raise limits or scale horizontally

## 18. Contributing

- Fork, branch, implement, test, open PR
- Follow existing style
- Add/adjust tests and documentation
- Conventional commits preferred

## 19. License

MIT License. See [LICENSE](LICENSE).

## 20. Support

- Issues: https://github.com/Alak-Das/LightQ/issues
- Discussions: https://github.com/Alak-Das/LightQ/discussions
- Email: alakdas.mail@gmail.com

## 21. Quick Reference Card

Essential cURL
```bash
# Push
curl -u user:password -X POST "http://localhost:8080/queue/push" \
  -H "consumerGroup: test" -H "Content-Type: text/plain" \
  -d "message"

# Batch Push (bulk)
curl -u user:password -X POST "http://localhost:8080/queue/batch/push" \
  -H "consumerGroup: test" -H "Content-Type: application/json" \
  -d '["m1","m2","m3"]'

# Pop (reserve)
curl -u user:password "http://localhost:8080/queue/pop" \
  -H "consumerGroup: test"

# Ack
curl -u user:password -X POST "http://localhost:8080/queue/ack?id=<id>" \
  -H "consumerGroup: test"

# Nack
curl -u user:password -X POST "http://localhost:8080/queue/nack?id=<id>&reason=retry" \
  -H "consumerGroup: test"

# Extend
curl -u user:password -X POST "http://localhost:8080/queue/extend-visibility?id=<id>&seconds=60" \
  -H "consumerGroup: test"

# View (admin)
curl -u admin:adminpassword "http://localhost:8080/queue/view" \
  -H "consumerGroup: test" -H "messageCount: 10" -H "consumed: no"

# DLQ View (admin)
curl -u admin:adminpassword "http://localhost:8080/queue/dlq/view?limit=10" \
  -H "consumerGroup: test"

# DLQ Replay (admin)
curl -u admin:adminpassword -X POST "http://localhost:8080/queue/dlq/replay" \
  -H "consumerGroup: test" -H "Content-Type: application/json" \
  -d '["id1","id2"]'
```

Defaults
- Server Port: 8080
- USER: user/password
- ADMIN: admin/adminpassword
- Redis: localhost:6379
- MongoDB: mongodb://admin:password@localhost:27017
- rate.limit.push-per-second: 100
- rate.limit.pop-per-second: 200
- lightq.message-allowed-to-fetch: 50
- lightq.persistence-duration-minutes: 1440
- lightq.cache-ttl-minutes: 30
- lightq.visibility-timeout-seconds: 30
- lightq.max-delivery-attempts: 5
- lightq.dlq-suffix: -dlq
- lightq.dlq-ttl-minutes: (unset)

—

Built with Spring Boot 4.0.1 and Java 21
Repository: https://github.com/Alak-Das/LightQ
## 22. Postman/Newman Integration Tests

This repository includes a Postman collection and environment for end-to-end integration tests against a running LightQ instance.

Artifacts:
- postman/LightQ.postman_collection.json
- postman/LightQ.local.postman_environment.json

Environment variables expected by the collection (provided via the environment file or Newman --env-var):
- baseUrl (required), example: http://localhost:8080
- consumerGroup (required), example: postman-it
- userUser, userPass
- adminUser, adminPass
- rateLimitPush (optional, used by rate limit demo)
- messageContent, messageContent2 (sample payloads)
- requestId, messageId, messageId2 (created/used by tests; can be empty initially)

Notes:
- The collection no longer defines collection-level defaults. Ensure the selected Postman environment has baseUrl set (Current Value), otherwise requests may fail with "request url is empty". When using Newman, pass -e postman/LightQ.local.postman_environment.json or provide --env-var baseUrl=... etc.

Prerequisites
- MongoDB and Redis running (docker compose up -d mongodb redis)
- LightQ app running on http://localhost:8080 (mvn spring-boot:run)
- Default credentials (can be overridden via env vars):
  - USER: user/password
  - ADMIN: admin/adminpassword

Run via Newman (local Node)
1) Install Newman
   npm install -g newman
2) Start infra and app (in separate terminal):
   docker compose up -d mongodb redis
   mvn spring-boot:run
3) Execute tests
   newman run postman/LightQ.postman_collection.json -e postman/LightQ.local.postman_environment.json --reporters cli,junit --reporter-junit-export target/newman-results.xml

Run via Newman (Docker)
- macOS/Windows (use host.docker.internal):
   docker run --rm --network host -v "$PWD/postman":/etc/newman -t postman/newman:alpine \
     run /etc/newman/LightQ.postman_collection.json \
     -e /etc/newman/LightQ.local.postman_environment.json \
     --env-var baseUrl=http://host.docker.internal:8080
- Linux (Docker host networking reaches localhost):
   docker run --rm --network host -v "$PWD/postman":/etc/newman -t postman/newman:alpine \
     run /etc/newman/LightQ.postman_collection.json \
     -e /etc/newman/LightQ.local.postman_environment.json

Notes
- Correlation IDs: a pre-request script generates and sends X-Request-Id/X-Correlation-Id; echoed by the server.
- Security: collection folders use Basic Auth; environment variables control credentials.
- Rate limit scenario is provided but disabled by default; to demo 429s:
  1) Lower RATE_LIMIT_PUSH_PER_SECOND (e.g., 2) and restart the app
  2) Enable the "Rate limit" item in the collection (uncheck "disabled")
- DLQ tests are non-destructive; replay test uses an empty list; adjust as needed for your environment.

Optional CI (GitHub Actions example)
Add a workflow (snippet) to run Newman in CI with MongoDB and Redis services:
```yaml
name: API IT (Postman)
on:
  push:
  pull_request:
jobs:
  it:
    runs-on: ubuntu-latest
    services:
      mongodb:
        image: mongo:7
        ports: ['27017:27017']
        options: >-
          --health-cmd="mongosh --quiet --eval 'db.runCommand({ ping: 1 }).ok'"
          --health-interval=10s --health-timeout=5s --health-retries=10
      redis:
        image: redis:7
        ports: ['6379:6379']
        options: >-
          --health-cmd="redis-cli ping"
          --health-interval=10s --health-timeout=5s --health-retries=10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Build
        run: mvn -q -DskipTests package
      - name: Run app
        run: |
          nohup mvn -q -DskipTests spring-boot:run &
          for i in {1..40}; do
            if curl -sf http://localhost:8080/actuator/health >/dev/null; then
              break
            fi
            sleep 3
          done
      - name: Install newman
        run: npm install -g newman
      - name: Run Postman integration tests
        run: |
          newman run postman/LightQ.postman_collection.json \
            -e postman/LightQ.local.postman_environment.json \
            --reporters cli,junit \
            --reporter-junit-export target/newman-results.xml
      - uses: actions/upload-artifact@v4
        with:
          name: newman-results
          path: target/newman-results.xml
```

Last Updated: December 2025
