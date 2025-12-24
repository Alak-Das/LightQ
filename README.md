# LightQ

A lightweight, high-performance message queue service built with Spring Boot 3.3.5 and Java 21. LightQ provides RESTful APIs for asynchronous message processing with:
- Consumer groups (multi-tenant queues)
- Redis caching for fast access
- MongoDB persistence with TTL
- At-least-once delivery using reservation + ack/nack
- Dead Letter Queue (DLQ) with replay

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Cache-red.svg)](https://redis.io/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Database-green.svg)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Table of Contents
1. Project Overview
2. Key Features
3. Architecture Overview
4. Project Structure
5. Technology Stack
6. Getting Started
7. Local Development
8. Configuration
9. Rate Limiting
10. API Documentation
11. Health Check
12. Logging & Observability
13. Testing
14. Docker Deployment
15. Security
16. Performance Considerations
17. Troubleshooting
18. Contributing
19. License
20. Support
21. Quick Reference Card

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
graph TB
    subgraph "Clients"
        P[Producer]
        C[Consumer]
        A[Admin]
    end

    subgraph "LightQ Service"
        F[CorrelationIdFilter]
        RL[RateLimitingInterceptor]
        API[MessageController (/queue/*)]

        subgraph "Service Layer"
            PUSH[PushMessageService]
            POP[PopMessageService (Reservation)]
            ACK[AcknowledgementService (ack/nack/extend)]
            VIEW[ViewMessageService]
            DLQ[DlqService (view/replay)]
        end

        CACHE[CacheService (Redis)]
        ASYNC[ThreadPoolTaskExecutor]
    end

    subgraph "Data Layer"
        R[(Redis Lists)]
        M[(MongoDB Collections per Consumer Group)]
        D[(MongoDB DLQ Collections: group + suffix)]
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
    API --> DLQ

    CACHE <--> R
    ASYNC -.-> M
    POP --> M
    ACK --> M
    VIEW --> M
    DLQ --> M
    DLQ --> D
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
| Framework | Spring Boot | 3.3.5 | Application framework |
| Language | Java | 21 | LTS |
| Web | spring-boot-starter-web | 3.3.5 | REST |
| Monitoring | Spring Boot Actuator | 3.3.5 | Health, metrics |
| Cache | Redis | 7.x | In-memory lists |
| Cache Client | Spring Data Redis | 3.3.5 | Redis template |
| Database | MongoDB | 7.0 | Durable storage |
| DB Client | Spring Data MongoDB | 4.3.5 | MongoTemplate |
| Security | spring-boot-starter-security | 3.3.5 | Basic Auth |
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
# or
./mvnw clean package
```

5) Run
```bash
mvn spring-boot:run
# or the jar
java -jar target/lightq-0.0.1-SNAPSHOT.jar
```

6) Verify
- Swagger: http://localhost:8080/swagger-ui/index.html

## 8. Configuration

All properties can be set via environment variables in containerized deployments.

| Variable | Default | Description |
|----------|---------|-------------|
| MongoDB |
| MONGO_URI | mongodb://admin:password@localhost:27017 | Connection string |
| MONGO_DB | lightq-db | Database name |
| Redis |
| SPRING_DATA_REDIS_HOST | localhost | Redis host |
| SPRING_DATA_REDIS_PORT | 6379 | Redis port |
| SPRING_DATA_REDIS_PASSWORD | (empty) | Redis password |
| Security |
| SECURITY_USER_USERNAME | user | USER role |
| SECURITY_USER_PASSWORD | password | USER password |
| SECURITY_ADMIN_USERNAME | admin | ADMIN role |
| SECURITY_ADMIN_PASSWORD | adminpassword | ADMIN password |
| Rate limiting |
| RATE_LIMIT_PUSH_PER_SECOND | 10 | Push RPS limit (<=0 disables) |
| RATE_LIMIT_POP_PER_SECOND | 20 | Pop RPS limit (<=0 disables) |
| LightQ (view/cache/persist) |
| LIGHTQ_MESSAGE_ALLOWED_TO_FETCH | 50 | /queue/view max results |
| LIGHTQ_PERSISTENCE_DURATION_MINUTES | 1440 | MongoDB TTL (minutes) |
| LIGHTQ_CACHE_TTL_MINUTES | 30 | Redis TTL (minutes) |
| Reservation/Ack/DLQ |
| LIGHTQ_VISIBILITY_TIMEOUT_SECONDS | 30 | Reservation window |
| LIGHTQ_MAX_DELIVERY_ATTEMPTS | 5 | Move to DLQ above this |
| LIGHTQ_DLQ_SUFFIX | -dlq | DLQ collection suffix |
| LIGHTQ_DLQ_TTL_MINUTES | (unset) | TTL for DLQ collection; unset disables |

ThreadPool (see LightQConstants):
- CORE_POOL_SIZE=5, MAX_POOL_SIZE=10, QUEUE_CAPACITY=25, THREAD_NAME_PREFIX=DBDataUpdater-

## 9. Rate Limiting

Fixed window per-second limits per endpoint (push/pop). HTTP 429 is returned when exceeded.

application.properties:
```properties
rate.limit.push-per-second=10
rate.limit.pop-per-second=20
```

## 10. API Documentation

Base URL: http://localhost:8080/queue

Authentication: HTTP Basic Auth

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
- 400 Bad Request if not currently reserved or invalid
- 404 Not Found if message not found

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

Returns application status and dependency checks (Redis, MongoDB).

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
  - Optionally (consumed, createdAt)

## 17. Troubleshooting

Common issues:
- Connection refused (Redis/Mongo): verify services, credentials, networking
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
- lightq.cache-ttl-minutes: 30
- lightq.persistence-duration-minutes: 1440
- rate.limit.push-per-second: 10
- rate.limit.pop-per-second: 20
- lightq.message-allowed-to-fetch: 50
- lightq.visibility-timeout-seconds: 30
- lightq.max-delivery-attempts: 5
- lightq.dlq-suffix: -dlq

—

Built with Spring Boot 3.3.5 and Java 21
Repository: https://github.com/Alak-Das/LightQ
Last Updated: December 2025
