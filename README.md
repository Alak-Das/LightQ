# LightQ

A lightweight, high-performance message queue service built with Spring Boot, providing RESTful APIs for asynchronous message processing with support for consumer groups, Redis caching, and MongoDB persistence.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Cache-red.svg)](https://redis.io/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Database-green.svg)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED.svg)](https://www.docker.com/)

## Table of Contents
1.  [Project Overview](#1-project-overview)
2.  [Key Features](#2-key-features)
3.  [Architecture Overview](#3-architecture-overview)
4.  [Technology Stack](#4-technology-stack)
5.  [Getting Started](#5-getting-started)
6.  [Configuration](#6-configuration)
7.  [Rate Limiting](#7-rate-limiting)
8.  [API Documentation](#8-api-documentation)
9.  [Testing](#9-testing)
10. [Docker](#10-docker)
11. [Security](#11-security)
12. [Troubleshooting](#12-troubleshooting)
13. [Contributing](#13-contributing)
14. [License](#14-license)
15. [Support](#15-support)

## 1. Project Overview

LightQ is a Spring Boot application that implements a simple, distributed message queue system. It enables asynchronous communication between producers and consumers through a RESTful API, with support for multiple isolated consumer groups.

### Use Cases

- Microservices communication via asynchronous messaging
- Task queue management and background job processing
- Event ingestion with simple durability and TTL
- Load leveling/buffering during traffic spikes

## 2. Key Features

### Core Functionality
- Message push: add messages to consumer-specific queues
- Message pop: retrieve and mark messages as consumed
- Message view: inspect queue contents with filtering (Admin only)
- Consumer groups: isolated streams for multi-tenant scenarios
- TTL management: automatic message expiration with configurable duration

### Performance & Reliability
- Redis caching for fast push/pop operations
- MongoDB persistence with TTL index for storage lifecycle
- Asynchronous writes/updates using a thread pool executor
- Cache-first pop strategy with DB fallback

### Operations & Security
- HTTP Basic Auth with role-based access control (User/Admin)
- Per-endpoint rate limiting for push/pop (configurable)
- OpenAPI/Swagger UI documentation
- Containerized deployment (Dockerfile + docker-compose)

## 3. Architecture Overview

### System Architecture

```mermaid
graph TB
    subgraph "Client Applications"
        P[Producer]
        C[Consumer]
    end

    subgraph "LightQ"
        API[REST API Layer<br/>MessageController]
        SVC[Service Layer<br/>Push/Pop/View Services]
        CACHE[Cache Service<br/>Redis List]
        ASYNC[Async Executor<br/>Thread Pool]
    end

    subgraph "Data Layer"
        R[(Redis<br/>In-Memory Cache)]
        M[(MongoDB<br/>Persistent Storage)]
    end

    P -->|POST /queue/push| API
    C -->|GET /queue/pop| API
    C -->|GET /queue/view| API

    API --> SVC
    SVC --> CACHE
    SVC --> ASYNC
    ASYNC -.->|Async Write/Update| M
    CACHE --> R
    SVC --> M

    style API fill:#e1f5ff
    style SVC fill:#fff4e1
    style CACHE fill:#ffe1e1
    style ASYNC fill:#e1ffe1
    style R fill:#ffcccc
    style M fill:#ccffcc
```

### Data Flow

- Push
  Client → Controller → Service → [Redis + MongoDB (Async)] → Response

- Pop (Cache Hit)
  Client → Controller → Service → Redis → Response + MongoDB Update (Async)

- Pop (Cache Miss)
  Client → Controller → Service → MongoDB (find & modify) → Response

## 4. Technology Stack

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| Framework | Spring Boot | 3.3.4 | Core application framework |
| Language | Java | 21 | Programming language |
| Web | spring-boot-starter-web | 3.3.4 | RESTful API |
| Cache | Redis + Spring Data Redis | Latest | High-speed caching (lists) |
| Database | MongoDB + Spring Data MongoDB | Latest | Persistent storage |
| Security | spring-boot-starter-security | 3.3.4 | Basic Auth + RBAC |
| Async | ThreadPoolTaskExecutor | 3.3.x | Non-blocking operations |
| Documentation | springdoc-openapi | 2.6.0 | Swagger UI |
| Validation | spring-boot-starter-validation | 3.3.4 | Input validation |
| Build Tool | Maven | 3.9+ | Dependency/build |
| Container | Docker | Latest | Deployment packaging |

## 5. Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.9+ (or use the provided Maven Wrapper `mvnw`)
- Redis 6.0+
- MongoDB 5.0+

### Installation

1) Clone
```bash
git clone https://github.com/Alak-Das/LightQ.git
cd LightQ
```

2) Configure application properties
Edit src/main/resources/application.properties:
```properties
spring.application.name=lightq

server.port=8080
logging.level.root=INFO

security.user.username=${SECURITY_USER_USERNAME:user}
security.user.password=${SECURITY_USER_PASSWORD:password}
security.admin.username=${SECURITY_ADMIN_USERNAME:admin}
security.admin.password=${SECURITY_ADMIN_PASSWORD:adminpassword}
no.of.message.allowed.to.fetch = 50

persistence.duration.minutes=30
spring.data.mongodb.uri=${MONGO_URI:mongodb://admin:password@localhost:27017}
spring.data.mongodb.database=${MONGO_DB:lightq-db}

spring.data.redis.host=${SPRING_DATA_REDIS_HOST:localhost}
spring.data.redis.port=6379
cache.ttl.minutes=5

# Per-second rate limits for /queue endpoints
rate.limit.push-per-second=${RATE_LIMIT_PUSH_PER_SECOND:10}
rate.limit.pop-per-second=${RATE_LIMIT_POP_PER_SECOND:10}
```

3) Build
```bash
mvn clean package
```

4) Run
```bash
mvn spring-boot:run
# or
java -jar target/lightq-0.0.1-SNAPSHOT.jar
```

### Verify Installation

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Pop (will be 404 if empty; requires auth):
```bash
curl -u user:password -H "consumerGroup: test" http://localhost:8080/queue/pop -i
```

## 6. Configuration

### Environment Variables

These map to application.properties for container/deployment scenarios.

| Variable | Default | Description |
|----------|---------|-------------|
| MONGO_URI | mongodb://admin:password@localhost:27017 | MongoDB connection string (supports auth) |
| MONGO_DB | lightq-db | MongoDB database name |
| SPRING_DATA_REDIS_HOST | localhost | Redis host (port is 6379 by default) |
| SPRING_DATA_REDIS_PASSWORD | (none) | Redis password if auth is enabled |
| SECURITY_USER_USERNAME | user | Basic auth user username |
| SECURITY_USER_PASSWORD | password | Basic auth user password |
| SECURITY_ADMIN_USERNAME | admin | Basic auth admin username |
| SECURITY_ADMIN_PASSWORD | adminpassword | Basic auth admin password |
| RATE_LIMIT_PUSH_PER_SECOND | 10 | Max push requests/sec (0 or negative disables) |
| RATE_LIMIT_POP_PER_SECOND | 10 | Max pop requests/sec (0 or negative disables) |

### Thread Pool (Async)

From LightQConstants.java:
- Core Pool Size: 5
- Max Pool Size: 10
- Queue Capacity: 25
- Thread Name Prefix: DBDataUpdater-

Tune in LightQConstants if workload requires.

### Redis Cache

- TTL (minutes): cache.ttl.minutes (default 5)
- Key pattern: consumerGroupMessages:{consumerGroup}
- Data Structure: Redis List (LPUSH for push, RPOP for pop)

### MongoDB

- Persistence TTL minutes: persistence.duration.minutes (default 30)
- Collection per consumer group (collection name = consumerGroup)
- TTL index ensured on createdAt on first write to each collection

## 7. Rate Limiting

A simple fixed-window per-second rate limiter protects:
- POST /queue/push → configured by rate.limit.push-per-second
- GET /queue/pop → configured by rate.limit.pop-per-second

Behavior:
- 0 or negative values disable limiting
- Exceeding the limit returns HTTP 429 Too Many Requests with a JSON error body

## 8. API Documentation

### Base URL
```
http://localhost:8080/queue
```

### Authentication

HTTP Basic Authentication is required for all endpoints.

| Role | Username | Password | Permissions |
|------|----------|----------|-------------|
| USER | user | password | push, pop |
| ADMIN | admin | adminpassword | push, pop, view |

Change these via environment variables before deploying.

### Headers

- consumerGroup (required for all endpoints)
- messageCount (required for view)
- consumed (optional for view; values: yes or no)

### Endpoints

1) Push Message
```http
POST /queue/push
Content-Type: text/plain
consumerGroup: my-group
Authorization: Basic dXNlcjpwYXNzd29yZA==

Hello, World!
```

cURL:
```bash
curl -u user:password -X POST "http://localhost:8080/queue/push" \
  -H "Content-Type: text/plain" \
  -H "consumerGroup: my-group" \
  --data "Hello, World!"
```

Response 200:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "content": "Hello, World!",
  "createdAt": "2025-01-01T10:30:00"
}
```

2) Pop Message
```http
GET /queue/pop
consumerGroup: my-group
```

cURL:
```bash
curl -u user:password "http://localhost:8080/queue/pop" -H "consumerGroup: my-group"
```

- 200 with MessageResponse
- 404 if no message available

3) View Messages (Admin only)
```http
GET /queue/view
consumerGroup: my-group
messageCount: 10
consumed: no
```

Query semantics:
- messageCount: 1 to no.of.message.allowed.to.fetch (default 50)
- consumed: "yes" (consumed only), "no" (unconsumed only), omit for all

cURL:
```bash
curl -u admin:adminpassword "http://localhost:8080/queue/view" \
  -H "consumerGroup: my-group" \
  -H "messageCount: 10" \
  -H "consumed: no"
```

Response 200:
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "content": "Hello, World!",
    "consumerGroup": "my-group",
    "createdAt": "2025-01-01T10:30:00.000+00:00",
    "consumed": false
  }
]
```

### Error Responses

Unified error schema (see GlobalExceptionHandler):
```json
{
  "timestamp": "2025-01-01T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Message Count should be 1 to 50.",
  "path": "/queue/view"
}
```

Common codes:
- 400 Bad Request (e.g., invalid messageCount)
- 401 Unauthorized (missing/invalid credentials)
- 403 Forbidden (insufficient role for /queue/view)
- 404 Not Found (no message on pop)
- 429 Too Many Requests (rate limit exceeded on push/pop)
- 500 Internal Server Error

## 9. Testing

Test sources are under src/test/java/com/al/lightq.

Run tests:
```bash
mvn test
```

Run a specific test class:
```bash
mvn -Dtest=MessageControllerTest test
```

Skip tests during build:
```bash
mvn clean package -DskipTests
```

## 10. Docker

This repository includes a multi-stage Dockerfile and a docker-compose.yml for local orchestration.

### Quick start (compose)
```bash
cp .env.example .env  # then edit values as needed
docker compose up -d --build
# if your Docker version uses the legacy plugin:
# docker-compose up -d --build
```

Services:
- mongodb (root user from .env)
- redis
- lightq-service (application)

Notes:
- App is exposed on 127.0.0.1:8080.
- MongoDB and Redis ports are NOT published to the host by default. To access them from the host for debugging, temporarily add:
  - mongodb: ports: ["127.0.0.1:27017:27017"]
  - redis: ports: ["127.0.0.1:6379:6379"]
- Images are pinned (mongo:7.0, redis:7.2-alpine). Containers use restart: unless-stopped.
- Compose reads credentials from .env (see .env.example).

Environment passed to the app (compose):
- MONGO_URI uses .env values:
  mongodb://${MONGO_INITDB_ROOT_USERNAME}:${MONGO_INITDB_ROOT_PASSWORD}@mongodb:27017/?authSource=admin
- SPRING_DATA_REDIS_HOST=redis
- Optionally SPRING_DATA_REDIS_PASSWORD if you enable Redis auth (see below)

Enable Redis authentication (optional):
1) Set SPRING_DATA_REDIS_PASSWORD in .env
2) In docker-compose.yml, uncomment:
   - redis command with --requirepass
   - SPRING_DATA_REDIS_PASSWORD under lightq-service environment
3) The app reads spring.data.redis.password from SPRING_DATA_REDIS_PASSWORD

Diagnostics:
```bash
docker compose ps
docker compose logs -f lightq-service
docker compose logs -f mongodb
docker compose logs -f redis
```

### Manual Docker build/run
```bash
# Build image
docker build -t lightq:latest .

# Run container
docker run -p 8080:8080 \
  -e MONGO_URI="mongodb://admin:password@host.docker.internal:27017" \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  -e SECURITY_USER_USERNAME=user \
  -e SECURITY_USER_PASSWORD=password \
  -e SECURITY_ADMIN_USERNAME=admin \
  -e SECURITY_ADMIN_PASSWORD=adminpassword \
  -e RATE_LIMIT_PUSH_PER_SECOND=10 \
  -e RATE_LIMIT_POP_PER_SECOND=10 \
  lightq:latest
```

Image uses a distroless Java 21 nonroot runtime.

## 11. Security

### Current Model
- HTTP Basic Authentication (in-memory users)
- Roles: USER (push, pop), ADMIN (push, pop, view)
- Credentials configurable via environment variables

### Best Practices
- Change default credentials before deployment
- Use HTTPS/SSL in production
- Place Redis and MongoDB on private networks/non-public interfaces
- Use MongoDB authentication in MONGO_URI
- Optionally configure Redis auth if enabled

## 12. Troubleshooting

1) Connection refused (Redis/MongoDB)
- Verify services are running and reachable
- Check env settings (MONGO_URI, SPRING_DATA_REDIS_HOST)
- With docker-compose:
```bash
docker-compose ps
docker-compose logs mongodb
docker-compose logs redis
docker-compose logs lightq-service
```

2) Messages not persisting
- Check persistence.duration.minutes
- Ensure MongoDB disk space and TTL index created on createdAt
- Ensure async executor isn’t saturated (logs show DBDataUpdater- threads)

3) Slow pop
- Check Redis availability and cache hit rate (expect faster hits)
- Ensure MongoDB has proper indexes (createdAt TTL; consider consumed index)

4) Authentication failures
- Use correct credentials
- Verify SECURITY_* env vars are applied

5) Rate limit 429
- Lower request rate or increase RATE_LIMIT_* values
- Setting to 0 disables limiting

## 13. Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make changes with tests
4. Run `mvn test`
5. Commit: `git commit -m "feat: add amazing feature"`
6. Push and open a PR

Guidelines:
- Follow Java and Spring conventions
- Add unit tests where applicable
- Keep coverage reasonable for new code paths
- Prefer Lombok to reduce boilerplate

## 14. License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 15. Support

- Email: alakdas.mail@gmail.com
- Issues: https://github.com/Alak-Das/LightQ/issues
- API Docs: http://localhost:8080/swagger-ui/index.html

---

Built with Spring Boot 3.3.x and Java 21.
