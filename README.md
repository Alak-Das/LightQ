# LightQ

A lightweight, high-performance message queue service built with Spring Boot 3.3.5 and Java 21, providing RESTful APIs for asynchronous message processing with support for consumer groups, Redis caching, and MongoDB persistence.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Cache-red.svg)](https://redis.io/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Database-green.svg)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Key Features](#2-key-features)
3. [Architecture Overview](#3-architecture-overview)
4. [Project Structure](#4-project-structure)
5. [Technology Stack](#5-technology-stack)
6. [Getting Started](#6-getting-started)
7. [Local Development](#7-local-development)
8. [Configuration](#8-configuration)
9. [Rate Limiting](#9-rate-limiting)
10. [API Documentation](#10-api-documentation)
11. [Health Check](#11-health-check)
12. [Logging & Observability](#12-logging--observability)
13. [Testing](#13-testing)
14. [Docker Deployment](#14-docker-deployment)
15. [Security](#15-security)
16. [Performance Considerations](#16-performance-considerations)
17. [Troubleshooting](#17-troubleshooting)
18. [Contributing](#18-contributing)
19. [License](#19-license)
20. [Support](#20-support)
21. [Quick Reference Card](#21-quick-reference-card)

## 1. Project Overview

LightQ is a production-ready Spring Boot application that implements a simple yet powerful distributed message queue system. It enables asynchronous communication between producers and consumers through a RESTful API, with support for multiple isolated consumer groups (multi-tenancy).

### Use Cases

- **Microservices Communication**: Enable async messaging between distributed services
- **Task Queue Management**: Process background jobs with guaranteed delivery
- **Event Ingestion**: Buffer and process events with configurable TTL
- **Load Leveling**: Smooth traffic spikes by queueing requests
- **Multi-tenant Systems**: Isolated message streams per consumer group

### Design Philosophy

LightQ prioritizes:
- **Simplicity**: Clean REST API, minimal configuration
- **Performance**: Redis-first caching with MongoDB persistence
- **Reliability**: Async writes, graceful degradation, comprehensive error handling
- **Observability**: Structured logging with correlation IDs (MDC)

## 2. Key Features

### Core Functionality
- **Message Push**: Add messages to consumer-specific queues with validation
- **Message Pop**: Retrieve and atomically mark messages as consumed
- **Message View**: Inspect queue contents with filtering (Admin only)
- **Consumer Groups**: Complete isolation between different message streams
- **TTL Management**: Automatic message expiration using MongoDB TTL indexes

### Performance & Reliability
- **Redis Caching**: In-memory lists for sub-millisecond push/pop operations
- **MongoDB Persistence**: Durable storage with automatic document expiration
- **Asynchronous Processing**: Non-blocking database writes via thread pool executor
- **Cache-First Strategy**: Pop from cache, fallback to DB; async DB updates
- **Graceful Shutdown**: Thread pool waits up to 30s for tasks to complete

### Operations & Security
- **HTTP Basic Authentication**: Role-based access control (USER/ADMIN)
- **Per-Endpoint Rate Limiting**: Configurable fixed-window rate limits on push/pop
- **Request Tracing**: Correlation IDs for distributed request tracking
- **OpenAPI Documentation**: Interactive Swagger UI with complete API specs
- **Health Checks**: Application health endpoint and Docker Compose health checks for dependencies
- **Containerized Deployment**: Multi-stage Dockerfile with distroless runtime

## 3. Architecture Overview

### System Architecture

```mermaid
graph TB
    subgraph "Client Applications"
        P[Producer]
        C[Consumer]
    end

    subgraph "LightQ Service"
        F[CorrelationIdFilter<br/>Request Tracking]
        RL[RateLimitingInterceptor<br/>Request Throttling]
        API[MessageController<br/>REST Endpoints]
        
        subgraph "Service Layer"
            PUSH[PushMessageService]
            POP[PopMessageService]
            VIEW[ViewMessageService]
        end
        
        CACHE[CacheService<br/>Redis Operations]
        ASYNC[ThreadPoolTaskExecutor<br/>5-10 threads, queue=25]
    end

    subgraph "Data Layer"
        R[(Redis<br/>In-Memory Lists<br/>TTL: 5min)]
        M[(MongoDB<br/>Per-Group Collections<br/>TTL: 30min)]
    end

    P -->|POST /queue/push| F
    C -->|GET /queue/pop| F
    C -->|GET /queue/view| F
    
    F --> RL
    RL --> API
    API --> PUSH
    API --> POP
    API --> VIEW
    
    PUSH --> CACHE
    PUSH --> ASYNC
    POP --> CACHE
    POP --> ASYNC
    VIEW --> CACHE
    
    CACHE <--> R
    ASYNC -.->|Async Write| M
    POP --> M
    VIEW --> M

    style API fill:#e1f5ff
    style PUSH fill:#fff4e1
    style POP fill:#fff4e1
    style VIEW fill:#fff4e1
    style CACHE fill:#ffe1e1
    style ASYNC fill:#e1ffe1
    style R fill:#ffcccc
    style M fill:#ccffcc
```

### Data Flow

#### Push Flow
1. Client sends message to `/queue/push` with `consumerGroup` header
2. `CorrelationIdFilter` adds correlation ID to MDC
3. `RateLimitingInterceptor` checks rate limit (default: 10/sec)
4. `MessageController` validates input and generates UUID
5. `PushMessageService`:
    - Immediately adds message to Redis list (sync)
    - Schedules async MongoDB write via `taskExecutor`
    - Ensures TTL index exists on first write to collection
6. Returns `MessageResponse` to client

#### Pop Flow (Cache Hit)
1. Client requests from `/queue/pop` with `consumerGroup` header
2. Rate limiting and correlation tracking apply
3. `PopMessageService`:
    - Performs `RPOP` on Redis list (FIFO)
    - If message found, returns immediately
    - Schedules async MongoDB update to mark consumed
4. Returns `MessageResponse` to client

#### Pop Flow (Cache Miss)
1. Client requests from `/queue/pop`
2. `PopMessageService`:
    - Redis `RPOP` returns null
    - Queries MongoDB with `findAndModify` (atomic read + update)
    - Finds oldest unconsumed message, marks as consumed
    - Returns message if found, or empty Optional
3. Returns `MessageResponse` or HTTP 404

#### View Flow (Admin Only)
1. Admin requests `/queue/view` with an optional `consumed` header
2. `ViewMessageService`:
    - If `consumed=yes`: Query MongoDB only for consumed messages
    - If `consumed=no` or null:
        - Fetch from Redis cache (unconsumed messages)
        - If count not met, query MongoDB excluding cached IDs
        - Merge results, sort by `createdAt`, limit to `lightq.message-allowed-to-fetch`
3. Returns list of `Message` objects

### Collection Strategy

Each consumer group gets its own MongoDB collection:
- Collection name = `consumerGroup` value
- Enables efficient queries and independent TTL management
- TTL index created on first write: `createdAt` field, expires after `lightq.persistence-duration-minutes` (default 30)

## 4. Project Structure

```
src/main/java/com/al/lightq/
├── LightQApplication.java          # Main application entry point
├── config/                         # Spring configuration classes
│   ├── AsyncConfig.java            # Configuration for asynchronous processing
│   ├── CorrelationIdFilter.java    # Filter for adding correlation IDs to requests
│   ├── LightQProperties.java       # Type-safe configuration properties
│   ├── RateLimitProperties.java    # Properties for rate limiting
│   ├── RateLimitingInterceptor.java # Interceptor for rate limiting
│   ├── RedisConfig.java            # Configuration for Redis
│   ├── SecurityConfig.java         # Configuration for security
│   ├── StartupLogger.java          # Logger for startup information
│   └── WebConfig.java              # Configuration for web
├── controller/                     # REST controllers
│   └── MessageController.java      # Controller for handling message-related requests
├── exception/                      # Custom exception classes
│   ├── ErrorResponse.java          # Response model for errors
│   ├── GlobalExceptionHandler.java # Global exception handler
│   └── RateLimitExceededException.java # Exception for rate limit exceeded
├── model/                          # Data models
│   ├── Message.java                # Model for a message
│   └── MessageResponse.java        # Response model for a message
├── service/                        # Business logic
│   ├── CacheService.java           # Service for interacting with the cache
│   ├── PopMessageService.java      # Service for popping messages
│   ├── PushMessageService.java     # Service for pushing messages
│   └── ViewMessageService.java     # Service for viewing messages
└── util/                           # Utility classes
    └── LightQConstants.java        # Constants used throughout the application
```

## 5. Technology Stack

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| **Framework** | Spring Boot | 3.3.5 | Application framework with auto-configuration |
| **Language** | Java | 21 | LTS release with virtual threads support |
| **Web** | spring-boot-starter-web | 3.3.5 | Embedded Tomcat, REST controllers |
| **Monitoring** | Spring Boot Actuator | 3.3.5 | Health checks and application monitoring |
| **Cache** | Redis | 7.2+ | High-speed in-memory data structure store |
| **Cache Client** | Spring Data Redis | 3.3.5 | Redis integration with template abstraction |
| **Database** | MongoDB | 7.0+ | Document-oriented NoSQL database |
| **DB Client** | Spring Data MongoDB | 3.3.5 | MongoDB integration with template and repositories |
| **Security** | spring-boot-starter-security | 3.3.5 | HTTP Basic Auth with BCrypt password encoding |
| **Async** | ThreadPoolTaskExecutor | 3.3.5 | Asynchronous task execution with thread pool |
| **Documentation** | springdoc-openapi | 2.6.0 | OpenAPI 3.0 specs and Swagger UI |
| **Validation** | spring-boot-starter-validation | 3.3.5 | JSR-380 Bean Validation (Hibernate Validator) |
| **Utilities** | Lombok | Latest | Boilerplate reduction via annotations |
| **Utilities** | Apache Commons Lang3 | (transitive) | String utilities, null-safe operations |
| **Build Tool** | Maven | 3.9+ | Dependency management and build lifecycle |
| **Container** | Docker | Latest | Containerization with multi-stage builds |
| **Base Image** | Distroless Java 21 | Latest | Minimal, security-hardened runtime |
| **Testing** | JUnit 5 | 5.10+ | Unit and integration testing framework |
| **Testing** | Mockito | 5.x | Mocking framework for unit tests |
| **Testing** | Spring Security Test | 3.3.5 | Security testing with @WithMockUser |

## 6. Getting Started

### Prerequisites

- **Java 21** or higher ([Download](https://adoptium.net/))
- **Maven 3.9+** (or use included Maven Wrapper: `./mvnw`)
- **Redis 6.0+** ([Installation Guide](https://redis.io/docs/getting-started/installation/))
- **MongoDB 5.0+** ([Installation Guide](https://www.mongodb.com/docs/manual/installation/))

For Docker deployment, only Docker and Docker Compose are required.

## 7. Local Development

### 1. Clone the Repository
```bash
git clone https://github.com/Alak-Das/LightQ.git
cd LightQ
```

### 2. Start Infrastructure Services

**Option A: Using Docker Compose (Recommended)**
```bash
# Create .env file from example
cp .env.example .env

# Edit .env with your preferred credentials (optional)
nano .env

# Start MongoDB and Redis in the background
docker compose up -d mongodb redis
```

**Option B: Local Installations**
```bash
# Start MongoDB (default port 27017)
mongod --dbpath /path/to/data --auth

# Start Redis (default port 6379)
redis-server
```

### 3. Configure Application

Edit `src/main/resources/application.properties` to match your local setup. The default values are configured to work with the provided `docker-compose.yml`.

### 4. Build the Application
```bash
# Using Maven
mvn clean package

# Using Maven Wrapper (no Maven installation needed)
./mvnw clean package

# Skip tests for faster builds
mvn clean package -DskipTests
```

### 5. Run the Application
```bash
# Using Maven
mvn spring-boot:run

# Using JAR file
java -jar target/lightq-0.0.1-SNAPSHOT.jar

# With custom port
java -jar target/lightq-0.0.1-SNAPSHOT.jar --server.port=9090
```

### 6. Verify Installation

#### 1. Check Application Logs
You should see logs indicating that the application has started successfully.

#### 2. Access Swagger UI
Open your browser and navigate to [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

#### 3. Test API Endpoints
```bash
# Push a message (requires authentication)
curl -u user:password -X POST "http://localhost:8080/queue/push" \
  -H "Content-Type: text/plain" \
  -H "consumerGroup: test-group" \
  -d "Hello, LightQ!"

# Pop a message
curl -u user:password "http://localhost:8080/queue/pop" \
  -H "consumerGroup: test-group"

# View messages (admin only)
curl -u admin:adminpassword "http://localhost:8080/queue/view" \
  -H "consumerGroup: test-group" \
  -H "messageCount: 10"
```

## 8. Configuration

### Environment Variables

All configuration can be overridden via environment variables for containerized deployments:

| Variable | Default | Description |
|----------|---------|-------------|
| **MongoDB** |
| `MONGO_URI` | `mongodb://admin:password@localhost:27017` | Full MongoDB connection string with auth |
| `MONGO_DB` | `lightq-db` | Database name for message storage |
| **Redis** |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis server hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis server port |
| `SPRING_DATA_REDIS_PASSWORD` | (empty) | Redis password (if authentication enabled) |
| **Security** |
| `SECURITY_USER_USERNAME` | `user` | Basic auth username for USER role |
| `SECURITY_USER_PASSWORD` | `password` | Basic auth password for USER role |
| `SECURITY_ADMIN_USERNAME` | `admin` | Basic auth username for ADMIN role |
| `SECURITY_ADMIN_PASSWORD` | `adminpassword` | Basic auth password for ADMIN role |
| **Rate Limiting** |
| `RATE_LIMIT_PUSH_PER_SECOND` | `10` | Max push requests/second (0 or negative disables) |
| `RATE_LIMIT_POP_PER_SECOND` | `10` | Max pop requests/second (0 or negative disables) |
| **LightQ** |
| `LIGHTQ_MESSAGE_ALLOWED_TO_FETCH` | `50` | Maximum messages returned by /queue/view |
| `LIGHTQ_PERSISTENCE_DURATION_MINUTES` | `30` | TTL for messages in MongoDB (in minutes) |
| `LIGHTQ_CACHE_TTL_MINUTES` | `5` | TTL for Redis cache entries (in minutes) |
| **Server** |
| `SERVER_PORT` | `8080` | HTTP server port |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level (DEBUG, INFO, WARN, ERROR) |
| **Actuator** |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health` | Exposes the health endpoint |
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | Shows details in the health endpoint |

### Thread Pool Configuration

Async executor settings are defined in `LightQConstants.java`:

```java
public static final int CORE_POOL_SIZE = 5;      // Minimum active threads
public static final int MAX_POOL_SIZE = 10;      // Maximum active threads
public static final int QUEUE_CAPACITY = 25;     // Pending task queue size
public static final String THREAD_NAME_PREFIX = "DBDataUpdater-";
```

**Tuning Guidelines:**
- **Low Volume** (< 100 msg/sec): Default settings (5-10 threads) are sufficient
- **Medium Volume** (100-500 msg/sec): Increase to 10-20 threads, queue 50
- **High Volume** (> 500 msg/sec): Increase to 20-50 threads, queue 100-200
- Monitor thread pool saturation via logs (rejected execution warnings)

### Redis Cache Configuration

- **Key Pattern**: `consumerGroupMessages:{consumerGroup}`
- **Data Structure**: Redis List
    - `LPUSH` for push operations (add to left)
    - `RPOP` for pop operations (remove from right, FIFO)
- **TTL**: Configurable via `lightq.cache-ttl-minutes` (default 5 minutes)
- **Serialization**: GenericJackson2JsonRedisSerializer for Message objects

### MongoDB Configuration

- **Collection Strategy**: One collection per consumer group
    - Collection name = value of `consumerGroup` header
    - Example: `consumerGroup: orders` → Collection: `orders`
- **TTL Index**: Automatically created on first write to each collection
    - Field: `createdAt`
    - Expiration: Configurable via `lightq.persistence-duration-minutes` (default 30)
    - Background cleanup by MongoDB
- **Authentication**: Supports username/password via `MONGO_URI`
    - Example: `mongodb://user:pass@host:port/?authSource=admin`

## 9. Rate Limiting

LightQ implements a simple yet effective fixed-window per-second rate limiter to protect against abuse and ensure fair resource allocation.

### How It Works

- **Scope**: Per-endpoint (push, pop) global rate limits
- **Algorithm**: Fixed window (resets every second)
- **Implementation**: `RateLimitingInterceptor` using `ConcurrentHashMap` and `AtomicInteger`
- **Response**: HTTP 429 (Too Many Requests) when limit exceeded

### Configuration

Set limits in `application.properties` or via environment variables:

```properties
# 0 or negative value disables rate limiting for that endpoint
rate.limit.push-per-second=10
rate.limit.pop-per-second=10
```

### Example Behavior

With `rate.limit.push-per-second=10`:
- Requests 1-10: HTTP 200 (success)
- Request 11: HTTP 429 (rate limit exceeded)
- After 1 second: Counter resets, requests allowed again

### Error Response Format

```json
{
  "timestamp": "2025-01-01T10:30:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded for /queue/push",
  "path": "/queue/push",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### Scaling Considerations

- **Current Limitation**: Global rate limit (not per-user or per-IP)
- **Production Recommendation**: Consider implementing:
    - Per-user rate limiting (keyed by username)
    - Sliding window algorithm for smoother enforcement
    - Distributed rate limiting (Redis-based) for multi-instance deployments
    - Token bucket algorithm for burst handling

## 10. API Documentation

### Base URL
```
http://localhost:8080/queue
```

### Authentication

All endpoints require HTTP Basic Authentication.

| Role | Default Username | Default Password | Permissions |
|------|------------------|------------------|-------------|
| **USER** | `user` | `password` | push, pop |
| **ADMIN** | `admin` | `adminpassword` | push, pop, view |

**⚠️ Security Warning**: Change default credentials before deploying to production!

### Common Headers

| Header | Required | Values | Description |
|--------|----------|--------|-------------|
| `consumerGroup` | Yes (all endpoints) | alphanumeric, hyphens, underscores (1-50 chars) | Identifies the message queue/group |
| `messageCount` | No (view only) | 1 to 50 (configurable) | Number of messages to retrieve. Defaults to `lightq.message-allowed-to-fetch` if not provided. |
| `consumed` | No (view only) | `yes`, `no` | Filter by consumption status |
| `Content-Type` | Yes (push only) | `text/plain`, `application/json`, etc. | Message content type |

### Endpoints

---

#### 1. Push Message

Add a message to the queue for a specific consumer group.

**Request**
```http
POST /queue/push
Authorization: Basic dXNlcjpwYXNzd29yZA==
Content-Type: text/plain
consumerGroup: my-group

Hello, World!
```

**cURL Example**
```bash
curl -u user:password -X POST "http://localhost:8080/queue/push" \
  -H "Content-Type: text/plain" \
  -H "consumerGroup: my-group" \
  -d "Hello, World!"

# JSON message
curl -u user:password -X POST "http://localhost:8080/queue/push" \
  -H "Content-Type: application/json" \
  -H "consumerGroup: my-group" \
  -d '{"orderId": 12345, "status": "pending"}'
```

**Validation Rules**
- `consumerGroup`: Must match pattern `^[a-zA-Z0-9-_]{1,50}$`
- `content`: Cannot be blank, max size 1MB (1,048,576 bytes)

**Success Response (200 OK)**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "content": "Hello, World!",
  "createdAt": "2025-01-01T10:30:00"
}
```

**Error Responses**
- `400 Bad Request`: Invalid consumer group or empty message
- `401 Unauthorized`: Missing or invalid credentials
- `413 Payload Too Large`: Message exceeds 1MB
- `429 Too Many Requests`: Rate limit exceeded

---

#### 2. Pop Message

Retrieve and consume the oldest message.

**Request**
```http
GET /queue/pop
Authorization: Basic dXNlcjpwYXNzd29yZA==
consumerGroup: my-group
```

**cURL Example**
```bash
curl -u user:password "http://localhost:8080/queue/pop" \
  -H "consumerGroup: my-group"
```

**Success Response (200 OK)**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "content": "Hello, World!",
  "createdAt": "2025-01-01T10:30:00"
}
```

**Empty Queue Response (404 Not Found)**
```
(Empty response body)
```

**Error Responses**
- `401 Unauthorized`: Missing or invalid credentials
- `404 Not Found`: No unconsumed messages available
- `429 Too Many Requests`: Rate limit exceeded

**Notes**
- Pop operation atomically marks the message as consumed
- If cache contains the message, DB update happens asynchronously
- If cache is empty, DB is queried directly with atomic findAndModify

---

#### 3. View Messages (Admin Only)

Retrieve messages without consuming them.

**Request**
```http
GET /queue/view
Authorization: Basic YWRtaW46YWRtaW5wYXNzd29yZA==
consumerGroup: my-group
messageCount: 10
consumed: no
```

**cURL Examples**
```bash
# View 10 unconsumed messages
curl -u admin:adminpassword "http://localhost:8080/queue/view" \
  -H "consumerGroup: my-group" \
  -H "messageCount: 10" \
  -H "consumed: no"

# View all messages (consumed and unconsumed) up to the configured limit
curl -u admin:adminpassword "http://localhost:8080/queue/view" \
  -H "consumerGroup: my-group"

# View only consumed messages
curl -u admin:adminpassword "http://localhost:8080/queue/view" \
  -H "consumerGroup: my-group" \
  -H "consumed: yes"
```

**Query Semantics**
- `messageCount` (optional): The maximum number of messages to retrieve. If not provided, the default is used. The value is capped at the server-side configuration `lightq.message-allowed-to-fetch`.
- `consumed` (optional):
    - `"yes"`: Return only consumed messages
    - `"no"`: Return only unconsumed messages
    - Omit: Return all messages (consumed and unconsumed)

**Success Response (200 OK)**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "content": "Hello, World!",
    "consumerGroup": "my-group",
    "createdAt": "2025-01-01T10:30:00.000+00:00",
    "consumed": false
  },
  {
    "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "content": "Second message",
    "consumerGroup": "my-group",
    "createdAt": "2025-01-01T10:31:00.000+00:00",
    "consumed": true
  }
]
```

**Error Responses**
- `400 Bad Request`: Invalid `consumed` value
- `401 Unauthorized`: Missing or invalid credentials
- `403 Forbidden`: Insufficient permissions (USER role cannot access)

**Performance Notes**
- Unconsumed messages are fetched from Redis cache first
- If cache doesn't contain enough messages, MongoDB is queried
- Results are sorted by `createdAt` in ascending order (oldest first)

---

### Error Response Format

All errors follow a consistent JSON structure:

```json
{
  "timestamp": "2025-01-01T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid input",
  "path": "/queue/view",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Common HTTP Status Codes**
- `200 OK`: Successful operation
- `400 Bad Request`: Invalid input (validation failure)
- `401 Unauthorized`: Missing or invalid credentials
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found (e.g., empty queue on pop)
- `413 Payload Too Large`: Message exceeds size limit
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Server-side error (check logs)

### Interactive Documentation

**Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- Try out APIs directly from the browser
- View request/response schemas
- See all available endpoints and parameters

**OpenAPI Spec (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- Machine-readable API specification
- Import into Postman, Insomnia, or other API clients

## 11. Health Check

LightQ includes a health check endpoint provided by Spring Boot Actuator. This endpoint can be used to monitor the application's health and the status of its dependencies.

### Endpoint
```
GET /actuator/health
```

### Response
The health check endpoint returns a JSON response with the status of the application and its dependencies.

**Example Response**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "lightq-db"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 107374182400,
        "free": 53687091200,
        "threshold": 10485760
      }
    },
    "ping": {
      "status": "UP"
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.2.4"
      }
    }
  }
}
```

## 12. Logging & Observability

LightQ implements structured logging with correlation tracking for production-grade observability.

### Correlation IDs

Every request is assigned a unique correlation ID for distributed tracing:

- **Header Sources** (checked in order):
    1. `X-Request-Id` header (if provided by client)
    2. `X-Correlation-Id` header (alternative)
    3. Auto-generated UUID if neither present

- **Propagation**: Correlation ID is:
    - Added to MDC (Mapped Diagnostic Context) for all log statements
    - Included in response headers: `X-Request-Id` and `X-Correlation-Id`
    - Available in error responses as `requestId` field

**Example Request/Response**:
```bash
# Client sends request
curl -H "X-Request-Id: abc123" -u user:password "http://localhost:8080/queue/pop" -H "consumerGroup: test"

# Response includes same ID
HTTP/1.1 200 OK
X-Request-Id: abc123
X-Correlation-Id: abc123
```

### Log Format

**Pattern** (defined in `logback-spring.xml`):
```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - rid=%X{requestId:-} method=%X{method:-} path=%X{path:-} cg=%X{consumerGroup:-} - %msg%n%ex{full}
```

**Example Output**:
```
2025-01-01 10:30:00.123 INFO  [http-nio-8080-exec-1] c.a.l.c.CorrelationIdFilter - rid=a1b2c3d4-e5f6-7890-abcd-ef1234567890 method=POST path=/queue/push cg=orders - Incoming request: POST /queue/push (requestId=a1b2c3d4-e5f6-7890-abcd-ef1234567890)
2025-01-01 10:30:00.145 DEBUG [http-nio-8080-exec-1] c.a.l.c.MessageController - rid=a1b2c3d4-e5f6-7890-abcd-ef1234567890 method=POST path=/queue/push cg=orders - Received push request for consumer group: orders with contentLength=25 chars
2025-01-01 10:30:00.167 DEBUG [http-nio-8080-exec-1] c.a.l.s.CacheService - rid=a1b2c3d4-e5f6-7890-abcd-ef1234567890 method=POST path=/queue/push cg=orders - Cache add: key=consumerGroupMessages:orders, messageId=msg123, ttlMinutes=5
2025-01-01 10:30:00.189 INFO  [http-nio-8080-exec-1] c.a.l.c.CorrelationIdFilter - rid=a1b2c3d4-e5f6-7890-abcd-ef1234567890 method=POST path=/queue/push cg=orders - Completed POST /queue/push -> status=200 in 66 ms
2025-01-01 10:30:00.201 INFO  [DBDataUpdater-1] c.a.l.s.PushMessageService - rid=a1b2c3d4-e5f6-7890-abcd-ef1234567890 method=POST path=/queue/push cg=orders - Message with ID msg123 asynchronously saved to DB for Consumer Group: orders
```

### MDC Context

All log statements automatically include:
- `requestId`: Correlation/trace ID
- `method`: HTTP method (GET, POST, etc.)
- `path`: Request URI path
- `consumerGroup`: Consumer group header value (when present)

### Log Levels

Configured in `application.properties` and `logback-spring.xml`:

| Logger | Level | Purpose |
|--------|-------|---------|
| `com.al.lightq` | `DEBUG` | Application-level detailed instrumentation |
| `org.springframework` | `INFO` | Spring framework logs |
| `org.mongodb.driver` | `WARN` | MongoDB driver (reduce noise) |
| `io.lettuce.core` | `INFO` | Redis client (Lettuce) |
| Root | `INFO` | Default for all other loggers |

**Change Log Level at Runtime**:
```bash
# Enable DEBUG for entire application
curl -X POST http://localhost:8080/actuator/loggers/com.al.lightq \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```
*(Requires Spring Boot Actuator dependency)*

### Key Log Events

**Push Operation**:
- Request received with content length
- Cache add confirmation
- Async DB save completion (from thread pool)

**Pop Operation**:
- Cache hit or miss indication
- Async DB update for cached messages
- DB query for cache misses

**View Operation**:
- Cache consultation with count
- DB query for additional messages
- Final result count with filtering

**Rate Limiting**:
- Warning when rate limit exceeded with endpoint and limit details

**TTL Index**:
- Info when TTL index created for new consumer group
- Debug when TTL index already exists

### Startup Logs

`StartupLogger` displays configuration at application start:
```
INFO  [main] c.a.l.c.StartupLogger - Startup configuration: rateLimits pushPerSec=10, popPerSec=10
INFO  [main] c.a.l.c.StartupLogger - Startup configuration: messageAllowedCount=50
INFO  [main] c.a.l.c.StartupLogger - Startup configuration: redis host=localhost, port=6379, ttlMinutes=5
INFO  [main] c.a.l.c.StartupLogger - Startup configuration: mongo database=lightq-db, persistenceTTLMinutes=30
```

**Security Note**: Credentials are NOT logged to prevent exposure.

### Production Logging Recommendations

1. **Centralized Logging**: Ship logs to ELK, Splunk, or CloudWatch
2. **Structured Format**: Consider JSON logging for better parsing
3. **Retention**: Keep logs for at least 30 days for troubleshooting
4. **Correlation Tracking**: Use `requestId` to trace requests across services
5. **Alerting**: Set up alerts on ERROR level logs and rate limit warnings

## 13. Testing

LightQ includes comprehensive unit and integration tests with **60+ test cases** covering all service layers, controllers, and configurations.

### Test Structure

```
src/test/java/com/al/lightq/
├── LightQApplicationTest.java          # Context loading
├── config/
│   ├── AsyncConfigTest.java            # Thread pool configuration
│   ├── RateLimitingInterceptorTest.java # Rate limiting behavior
│   ├── RedisConfigTest.java            # Redis template & cache manager
│   └── SecurityConfigTest.java         # User authentication & roles
├── controller/
│   └── MessageControllerTest.java      # API endpoint tests (@WebMvcTest)
├── exception/
│   └── GlobalExceptionHandlerTest.java # Error handling
└── service/
    ├── CacheServiceTest.java           # Redis operations
    ├── PopMessageServiceTest.java      # Pop logic & async updates
    ├── PushMessageServiceTest.java     # Push logic & TTL index
    └── ViewMessageServiceTest.java     # View logic & filtering
```

### Running Tests

**Run all tests**:
```bash
mvn test
```

**Run specific test class**:
```bash
mvn -Dtest=MessageControllerTest test
```

**Run specific test method**:
```bash
mvn -Dtest=MessageControllerTest#testPush test
```

**Run tests with coverage** (requires JaCoCo plugin):
```bash
mvn clean test jacoco:report
# Report: target/site/jacoco/index.html
```

**Skip tests during build**:
```bash
mvn clean package -DskipTests
```

### Test Coverage Highlights

#### Controller Tests (`MessageControllerTest`)
- Push message with USER role ✓
- Push message with ADMIN role ✓
- Pop message with USER and ADMIN roles ✓
- View endpoint forbidden for USER role ✓
- View with ADMIN role (various `consumed` filters) ✓
- Uses `@WithMockUser` for security testing

#### Service Tests

**PushMessageServiceTest**:
- Message push to cache and async DB save
- TTL index creation on first write
- Handling existing TTL index

**PopMessageServiceTest**:
- Pop from cache (async DB update triggered)
- Pop from DB when cache miss
- Empty queue handling

**ViewMessageServiceTest**:
- View with no consumed filter (cache + DB merge)
- View with `consumed=yes` filter (DB only)
- View with `consumed=no` filter (cache + DB unconsumed)
- Cache-only fast path
- Empty result handling

**CacheServiceTest**:
- Add message to Redis list
- Pop message from Redis
- View messages in cache
- Empty/null cache handling

#### Configuration Tests
- Async executor bean configuration
- Rate limiting enforcement
- Redis template serialization
- Security user details and password encoding

### Test Technologies

- **JUnit 5**: Test framework with assertions and lifecycle management
- **Mockito**: Mocking dependencies (MongoTemplate, RedisTemplate, etc.)
- **Spring Boot Test**: `@SpringBootTest`, `@WebMvcTest` for integration tests
- **Spring Security Test**: `@WithMockUser` for authentication in tests
- **MockMvc**: HTTP request/response simulation without starting server

### Writing New Tests

**Example Unit Test**:
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock
    private MongoTemplate mongoTemplate;
    
    @InjectMocks
    private MyService myService;
    
    @Test
    void testMyMethod() {
        when(mongoTemplate.find(any(), any(), any()))
            .thenReturn(List.of(new Message(...)));
        
        List<Message> result = myService.myMethod("test");
        
        assertEquals(1, result.size());
        verify(mongoTemplate, times(1)).find(any(), any(), any());
    }
}
```

**Example Controller Test**:
```java
@WebMvcTest(MessageController.class)
@Import(SecurityConfig.class)
class MessageControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private PushMessageService pushMessageService;
    
    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testPush() throws Exception {
        mockMvc.perform(post("/queue/push")
                .header("consumerGroup", "test")
                .content("message")
                .with(csrf()))
            .andExpect(status().isOk());
    }
}
```

## 14. Docker Deployment

LightQ provides production-ready containerization with multi-stage builds and Docker Compose orchestration.

### Quick Start with Docker Compose

#### 1. Clone and Configure
```bash
git clone https://github.com/Alak-Das/LightQ.git
cd LightQ

# Create environment file from the example
cp .env.example .env

# Edit credentials (recommended for production)
nano .env
```

**Example `.env` file**:
```bash
# MongoDB Credentials
MONGO_INITDB_ROOT_USERNAME=admin
MONGO_INITDB_ROOT_PASSWORD=securePassword123

# Redis Authentication (optional, uncomment to enable)
# SPRING_DATA_REDIS_PASSWORD=redisPassword123

# Application Security
SECURITY_USER_USERNAME=appuser
SECURITY_USER_PASSWORD=userPass456
SECURITY_ADMIN_USERNAME=appadmin
SECURITY_ADMIN_PASSWORD=adminPass789

# Rate Limiting
RATE_LIMIT_PUSH_PER_SECOND=20
RATE_LIMIT_POP_PER_SECOND=20
```

#### 2. Start All Services
```bash
# Build and start all services in the background
docker compose up -d --build

# View logs for all services
docker compose logs -f

# View logs for a specific service
docker compose logs -f lightq-service
```

#### 3. Verify Deployment
```bash
# Check the status of all services
docker compose ps

# Test the API
curl -u appuser:userPass456 -X POST "http://localhost:8080/queue/push" \
  -H "consumerGroup: docker-test" \
  -H "Content-Type: text/plain" \
  -d "Docker deployment successful!"
```

#### 4. Stop Services
```bash
# Stop services without removing volumes
docker compose stop

# Stop and remove containers (keeps volumes)
docker compose down

# Stop and remove everything, including volumes
docker compose down -v
```

### Docker Compose Architecture

```yaml
services:
  lightq-service:
    build:
      context: .
    ports:
      - "127.0.0.1:8080:8080"  # Bind to localhost only
    environment:
      - MONGO_URI=mongodb://${MONGO_INITDB_ROOT_USERNAME:-admin}:${MONGO_INITDB_ROOT_PASSWORD:-password}@mongodb:27017/?authSource=admin
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_DATA_REDIS_PORT=6379
      # - SPRING_DATA_REDIS_PASSWORD=${SPRING_DATA_REDIS_PASSWORD}  # enable if Redis auth is configured
      - JAVA_TOOL_OPTIONS=-XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=75 -XX:+UseG1GC
    depends_on:
      mongodb:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped

  mongodb:
    image: mongo:latest
    environment:
      - MONGO_INITDB_ROOT_USERNAME=${MONGO_INITDB_ROOT_USERNAME:-admin}
      - MONGO_INITDB_ROOT_PASSWORD=${MONGO_INITDB_ROOT_PASSWORD:-password}
    volumes:
      - mongo_data:/data/db
    healthcheck:
      test: ["CMD-SHELL", "mongosh --quiet \"mongodb://$$MONGO_INITDB_ROOT_USERNAME:$$MONGO_INITDB_ROOT_PASSWORD@localhost:27017/admin?authSource=admin\" --eval \"db.adminCommand({ ping: 1 }).ok\""]
      interval: 10s
      timeout: 10s
      retries: 10
      start_period: 60s
    restart: unless-stopped

  redis:
    image: redis:latest
    # To enable authentication, uncomment and set SPRING_DATA_REDIS_PASSWORD in .env
    # command: ["redis-server", "--requirepass", "${SPRING_DATA_REDIS_PASSWORD}"]
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

volumes:
  mongo_data:
  redis_data:
```

**Key Features**:
- Health checks ensure dependencies start before application
- Named volumes persist data across container restarts
- Restart policies for high availability
- Port binding to localhost prevents external access (remove for remote access)
- Environment variable injection from `.env`

### Dockerfile Details

**Multi-Stage Build**:
```dockerfile
# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B  # Cache dependencies
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Benefits**:
- **Small Image Size**: Distroless base (~150MB vs ~500MB+ with full JDK)
- **Security**: No shell, package managers, or unnecessary utilities
- **Non-root**: Runs as unprivileged user
- **Layer Caching**: Dependencies downloaded separately for faster rebuilds

### Manual Docker Build & Run

**Build Image**:
```bash
docker build -t lightq:latest .
```

**Run Container**:
```bash
docker run -d \
  --name lightq \
  -p 8080:8080 \
  -e MONGO_URI="mongodb://admin:password@host.docker.internal:27017/?authSource=admin" \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  -e SECURITY_USER_USERNAME=user \
  -e SECURITY_USER_PASSWORD=password \
  lightq:latest
```

**Notes**:
- Use `host.docker.internal` to connect to MongoDB/Redis running on host machine
- For Linux, you may need `--add-host=host.docker.internal:host-gateway`

### Accessing MongoDB & Redis from Host

By default, MongoDB and Redis ports are **not published** to the host for security. To access them for debugging:

**Temporarily add port mappings**:
```yaml
# docker-compose.yml
mongodb:
  ports:
    - "127.0.0.1:27017:27017"  # MongoDB

redis:
  ports:
    - "127.0.0.1:6379:6379"    # Redis
```

Then restart:
```bash
docker compose up -d
```

### Production Recommendations

1. **Use external MongoDB/Redis**: Don't run databases in same compose file as app
2. **Enable Redis authentication**: Uncomment password settings in compose file
3. **Use secrets management**: Integrate with Docker Swarm secrets or Kubernetes secrets
4. **Set resource limits**:
```yaml
lightq-service:
  deploy:
    resources:
      limits:
        cpus: '2'
        memory: 2G
      reservations:
        cpus: '1'
        memory: 1G
```
5. **Use specific image tags**: Replace `latest` with version tags for reproducibility
6. **Enable monitoring**: Add Prometheus, Grafana, or application performance monitoring
7. **Configure logging driver**: Ship logs to centralized logging system

## 15. Security

LightQ implements multiple security layers to protect against common vulnerabilities.

### Authentication & Authorization

**HTTP Basic Authentication** with role-based access control (RBAC):

| Role | Endpoints | Permissions |
|------|-----------|-------------|
| **USER** | `/queue/push`<br>`/queue/pop` | Create and consume messages |
| **ADMIN** | `/queue/push`<br>`/queue/pop`<br>`/queue/view` | All USER permissions + view queue contents |

**Implementation**:
- Spring Security with `BCryptPasswordEncoder`
- In-memory user store (configurable via environment variables)
- Passwords hashed with bcrypt (cost factor 10)

### Credentials Management

**Default Credentials** (⚠️ CHANGE IN PRODUCTION):
```properties
security.user.username=user
security.user.password=password
security.admin.username=admin
security.admin.password=adminpassword
```

**Override via Environment Variables**:
```bash
export SECURITY_USER_USERNAME=production_user
export SECURITY_USER_PASSWORD=complex_password_123!
export SECURITY_ADMIN_USERNAME=production_admin
export SECURITY_ADMIN_PASSWORD=even_more_complex_456!
```

**Best Practices**:
- Use strong passwords (16+ characters, mixed case, numbers, symbols)
- Rotate credentials regularly (90 days recommended)
- Store credentials in secrets management (Vault, AWS Secrets Manager, etc.)
- Never commit credentials to version control

### Input Validation

All endpoints validate inputs to prevent injection attacks:

**Consumer Group Header**:
```java
@Pattern(regexp = "^[a-zA-Z0-9-_]{1,50}$", 
         message = "Invalid consumer group name.")
```
- Prevents MongoDB injection via collection names
- Limits length to prevent resource exhaustion

**Message Content**:
```java
@NotBlank(message = "Message content cannot be empty")
@Size(max = 1048576, message = "Message size cannot exceed 1MB")
```
- Prevents empty messages
- Protects against memory exhaustion attacks

### Network Security

**Docker Compose Defaults**:
- Application port bound to `127.0.0.1:8080` (localhost only)
- MongoDB and Redis ports not published (internal network only)

**Production Recommendations**:
1. **Use HTTPS/TLS**: Deploy behind reverse proxy (Nginx, Traefik) with SSL certificates
2. **Enable MongoDB Authentication**: Always use `MONGO_URI` with credentials
3. **Enable Redis Authentication**: Set `requirepass` in Redis configuration
4. **Firewall Rules**: Restrict access to trusted IPs/networks
5. **VPC/Private Networks**: Keep databases on private subnets

### CSRF Protection

CSRF is **disabled** in `SecurityConfig.java`:
```java
.csrf(AbstractHttpConfigurer::disable)
```

**Rationale**:
- LightQ is a RESTful API without browser-based session cookies
- Authentication is stateless (HTTP Basic Auth on every request)

**If adding session-based auth**: Re-enable CSRF and use CSRF tokens

### Security Headers

**Current State**: Standard Spring Security defaults applied

**Production Enhancements**:
Add security headers in reverse proxy (Nginx example):
```nginx
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

### Rate Limiting

Acts as a defense against:
- **DoS attacks**: Prevents single client from overwhelming the system
- **Brute force**: Limits login attempt rate (HTTP 401 responses)
- **Resource exhaustion**: Protects thread pool and database from overload

See [Section 9: Rate Limiting](#9-rate-limiting) for configuration details.

### Dependency Security

**Keep Dependencies Updated**:
```bash
# Check for known vulnerabilities
mvn dependency-check:check

# Update to latest patch versions
mvn versions:use-latest-releases
```

**Critical Dependencies**:
- Spring Boot: Check CVE database regularly
- MongoDB Driver: Update for security patches
- Redis Client (Lettuce): Monitor for vulnerabilities

### Logging Security

**What's Logged**:
- Request paths, methods, status codes
- Consumer group names
- Message sizes (not content)
- Correlation IDs

**What's NOT Logged**:
- Passwords or credentials
- Full MongoDB URIs with passwords
- Message content (configurable if needed)

### Security Checklist for Production

- [ ] Change all default passwords
- [ ] Enable HTTPS/TLS (reverse proxy with Let's Encrypt or commercial cert)
- [ ] Enable MongoDB authentication with strong passwords
- [ ] Enable Redis authentication (optional but recommended)
- [ ] Use secrets management system (Vault, AWS Secrets Manager, etc.)
- [ ] Configure firewall rules (allow only trusted IPs)
- [ ] Set up intrusion detection (fail2ban, AWS GuardDuty, etc.)
- [ ] Enable audit logging (track admin actions)
- [ ] Regular security updates (monthly dependency updates)
- [ ] Penetration testing (annual or after major changes)
- [ ] Monitor for suspicious activity (unusual rate limit hits, failed auth)

## 16. Performance Considerations

### Throughput Estimates

**Typical Performance** (single instance on 4 CPU, 8GB RAM):
- **Push**: 1,000-2,000 msg/sec (cached write + async DB)
- **Pop (cache hit)**: 2,000-5,000 msg/sec (Redis read + async DB update)
- **Pop (cache miss)**: 500-1,000 msg/sec (MongoDB query + update)
- **View**: 100-500 requests/sec (depends on `lightq.message-allowed-to-fetch` and filtering)

**Bottlenecks**:
1. MongoDB write throughput (async executor queue capacity)
2. Redis network latency (use Redis on same network segment)
3. Thread pool saturation (tune core/max pool sizes)

### Optimization Strategies

#### 1. Redis Caching
- **Benefit**: 10-100x faster reads than MongoDB
- **Tuning**: Increase `lightq.cache-ttl-minutes` for longer cache retention
- **Monitoring**: Track cache hit rate via logs or metrics

#### 2. Async Thread Pool
- **Benefit**: Non-blocking DB writes, higher push throughput
- **Tuning**: Adjust in `LightQConstants.java`:
  ```java
  public static final int CORE_POOL_SIZE = 10;  // Increase for high load
  public static final int MAX_POOL_SIZE = 20;
  public static final int QUEUE_CAPACITY = 50;
  ```
- **Monitoring**: Watch for "rejected execution" warnings in logs

#### 3. MongoDB Indexes
- **TTL Index**: Automatically created on `createdAt` field
- **Additional Indexes**: Consider adding:
    - Index on `consumed` field if view operations are frequent
    - Compound index on `(consumed, createdAt)` for filtered views

#### 4. Connection Pooling
- **MongoDB**: Default connection pool size is 100
- **Redis**: Lettuce client uses connection pooling by default
- **Tuning**: Increase pool sizes if connection exhaustion occurs

#### 5. JVM Tuning
Docker compose includes GC tuning:
```yaml
JAVA_TOOL_OPTIONS: -XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=75 -XX:+UseG1GC
```

**Additional Options**:
```bash
# Larger heap for high-volume scenarios
-Xms2g -Xmx4g

# Optimize for low latency
-XX:MaxGCPauseMillis=200

# Enable GC logging
-Xlog:gc*:file=/var/log/lightq-gc.log
```

### Scaling Strategies

#### Vertical Scaling
Increase resources for single instance:
- CPU: 2 cores minimum, 4-8 cores recommended
- RAM: 2GB minimum, 4-8GB recommended
- Disk: SSD for MongoDB data directory

#### Horizontal Scaling
Multiple LightQ instances with shared infrastructure:

**Setup**:
```
┌─────────────┐
│ Load        │
│ Balancer    │
└──────┬──────┘
       │
   ┌───┴───┬───────┬───────┐
   │       │       │       │
┌──▼──┐ ┌──▼──┐ ┌──▼──┐ ┌──▼──┐
│ LQ  │ │ LQ  │ │ LQ  │ │ LQ  │
│  1  │ │  2  │ │  3  │ │  4  │
└──┬──┘ └──┬──┘ └──┬──┘ └──┬──┘
   │       │       │       │
   └───┬───┴───┬───┴───┬───┘
       │       │       │
    ┌──▼───────▼───────▼──┐
    │   Shared Redis      │
    └─────────────────────┘
    ┌─────────────────────┐
    │  MongoDB Replica    │
    │  Set or Cluster     │
    └─────────────────────┘
```

**Considerations**:
- **Rate Limiting**: Current implementation is per-instance; consider Redis-based global rate limiting
- **Sticky Sessions**: Not required (stateless authentication)
- **Monitoring**: Aggregate metrics across all instances

### Performance Monitoring

**Key Metrics to Track**:
1. **Request Rate**: Requests per second per endpoint
2. **Response Time**: P50, P95, P99 latencies
3. **Cache Hit Rate**: Percentage of pop operations served from Redis
4. **Thread Pool**: Active threads, queue size, rejected tasks
5. **Database**: Connection pool usage, query latency
6. **Error Rate**: 4xx and 5xx responses per second

**Tools**:
- **Prometheus + Grafana**: Add Spring Boot Actuator + Micrometer
- **Application Performance Monitoring**: New Relic, Datadog, Dynatrace
- **Log Analytics**: ELK Stack, Splunk

## 17. Troubleshooting

### Common Issues & Solutions

#### 1. Connection Refused (Redis/MongoDB)

**Symptoms**:
```
org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis
```

**Solutions**:
```bash
# Check services are running
docker compose ps

# Check Redis
docker compose logs redis
redis-cli -h localhost ping  # Should return PONG

# Check MongoDB
docker compose logs mongodb
mongosh --host localhost --port 27017 --eval "db.adminCommand({ ping: 1 })"

# Verify network connectivity
docker compose exec lightq-service ping redis -c 3
docker compose exec lightq-service ping mongodb -c 3
```

**Fix**:
- Ensure services started successfully (check health checks)
- Verify `SPRING_DATA_REDIS_HOST` and `MONGO_URI` are correct
- Check firewall rules aren't blocking connections

---

#### 2. Messages Not Persisting

**Symptoms**:
- Messages pushed successfully but disappear after TTL
- Messages not visible in MongoDB

**Diagnosis**:
```bash
# Check MongoDB collections
mongosh "mongodb://admin:password@localhost:27017/lightq-db?authSource=admin"
> show collections
> db.your_consumer_group.find().limit(5)

# Check TTL index
> db.your_consumer_group.getIndexes()
```

**Solutions**:
- Verify `lightq.persistence-duration-minutes` is set appropriately
- Check MongoDB disk space: `df -h`
- Ensure async thread pool isn't saturated (check logs for rejected tasks)
- Verify MongoDB user has write permissions

---

#### 3. Slow Pop Operations

**Symptoms**:
- Pop requests taking > 1 second
- High database query latency

**Diagnosis**:
```bash
# Check cache hit rate in logs
docker compose logs lightq-service | grep "Cache hit\|Cache miss"

# Monitor MongoDB slow queries
mongosh
> db.setProfilingLevel(2, { slowms: 100 })
> db.system.profile.find().sort({ ts: -1 }).limit(10)
```

**Solutions**:
- **Low cache hit rate**: Increase `lightq.cache-ttl-minutes`
- **High DB load**: Add index on `consumed` field:
  ```javascript
  db.your_consumer_group.createIndex({ consumed: 1, createdAt: 1 })
  ```
- **Network latency**: Move Redis/MongoDB closer to application
- **Resource contention**: Scale vertically (more CPU/RAM) or horizontally

---

#### 4. Authentication Failures

**Symptoms**:
```
HTTP/1.1 401 Unauthorized
```

**Solutions**:
```bash
# Verify credentials
curl -v -u user:password http://localhost:8080/queue/pop -H "consumerGroup: test"

# Check environment variables are loaded
docker compose exec lightq-service env | grep SECURITY_

# Restart service after changing credentials
docker compose restart lightq-service
```

---

#### 5. Rate Limit 429 Errors

**Symptoms**:
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded for /queue/push"
}
```

**Solutions**:
```bash
# Check current rate limits
docker compose logs lightq-service | grep "Startup configuration: rateLimits"

# Temporarily disable rate limiting
docker compose exec lightq-service \
  sh -c 'echo "rate.limit.push-per-second=0" >> application.properties'

# Or increase limits via environment variables
# Edit docker-compose.yml:
environment:
  - RATE_LIMIT_PUSH_PER_SECOND=100
  - RATE_LIMIT_POP_PER_SECOND=100
```

---

#### 6. Thread Pool Saturation

**Symptoms**:
```
WARN - Task execution took longer than expected
ERROR - Failed to persist message asynchronously
```

**Diagnosis**:
```bash
# Count async thread pool threads
docker compose logs lightq-service | grep "DBDataUpdater-" | wc -l

# Check for rejected execution errors
docker compose logs lightq-service | grep "rejected"
```

**Solutions**:
- Increase thread pool size in `LightQConstants.java`
- Add more application instances (horizontal scaling)
- Reduce message push rate

---

#### 7. Docker Container Won't Start

**Symptoms**:
```bash
docker compose ps
# lightq-service exited with code 1
```

**Diagnosis**:
```bash
# View startup errors
docker compose logs lightq-service | tail -50

# Check MongoDB/Redis health
docker compose ps
```

**Common Causes**:
- MongoDB authentication failed (wrong credentials in `MONGO_URI`)
- Redis connection timeout (service not healthy)
- Java process crashed (check JVM heap size)

**Solutions**:
- Wait for dependencies to become healthy (check `depends_on` conditions)
- Verify environment variables: `docker compose config`
- Increase `start_period` in health checks

---

#### 8. High Memory Usage

**Symptoms**:
- Application OOM (Out of Memory) errors
- Container killed by Docker

**Diagnosis**:
```bash
# Check memory usage
docker stats lightq-service

# Check JVM heap
docker compose exec lightq-service jmap -heap 1
```

**Solutions**:
```yaml
# Adjust JVM heap in docker-compose.yml
environment:
  - JAVA_TOOL_OPTIONS=-Xms1g -Xmx2g -XX:+UseG1GC

# Or set container memory limit
deploy:
  resources:
    limits:
      memory: 3G
```

---

### Debugging Tips

**Enable DEBUG logging**:
```properties
# application.properties
logging.level.com.al.lightq=DEBUG
logging.level.org.springframework.data.mongodb=DEBUG
logging.level.org.springframework.data.redis=DEBUG
```

**Access container shell** (for distroless images, use debug variant):
```bash
# Use debug image temporarily
docker run -it --entrypoint /bin/sh lightq:latest
# Note: Distroless doesn't have shell, use 'exec' on running container instead

# Or check logs with correlation ID
docker compose logs lightq-service | grep "rid=a1b2c3d4"
```

**Test connectivity from container**:
```bash
# Install netcat for testing
docker compose exec lightq-service apt-get update && apt-get install -y netcat
docker compose exec lightq-service nc -zv redis 6379
docker compose exec lightq-service nc -zv mongodb 27017
```

## 18. Contributing

We welcome contributions to LightQ! Here's how you can help improve the project.

### How to Contribute

1. **Fork the Repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/LightQ.git
   cd LightQ
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```

3. **Make Your Changes**
    - Write clean, readable code
    - Follow existing code style and conventions
    - Add tests for new functionality
    - Update documentation (README, JavaDoc)

4. **Run Tests**
   ```bash
   mvn clean test
   # Ensure all tests pass
   ```

5. **Commit Your Changes**
   ```bash
   git add .
   git commit -m "feat: add amazing feature"
   ```

   Use conventional commit format:
    - `feat:` New feature
    - `fix:` Bug fix
    - `docs:` Documentation changes
    - `test:` Adding or updating tests
    - `refactor:` Code refactoring
    - `perf:` Performance improvements
    - `chore:` Maintenance tasks

6. **Push to Your Fork**
   ```bash
   git push origin feature/amazing-feature
   ```

7. **Open a Pull Request**
    - Go to the original repository
    - Click "New Pull Request"
    - Describe your changes clearly
    - Reference any related issues

### Contribution Guidelines

**Code Style**:
- Follow Java naming conventions
- Use Lombok annotations to reduce boilerplate
- Keep methods focused and small (< 30 lines preferred)
- Add JavaDoc for public methods

**Testing**:
- Write unit tests for new services and utilities
- Use integration tests for controllers
- Aim for reasonable coverage (60%+ for new code)
- Mock external dependencies (MongoDB, Redis)

**Documentation**:
- Update README if adding features or changing configuration
- Add inline comments for complex logic
- Update OpenAPI annotations for API changes

**Pull Request Checklist**:
- [ ] Tests pass locally
- [ ] Code follows project style
- [ ] Documentation updated
- [ ] No merge conflicts
- [ ] Descriptive PR title and description

### Areas for Contribution

**High Priority**:
- [ ] Distributed rate limiting (Redis-based)
- [ ] Metrics and monitoring (Prometheus/Micrometer integration)
- [ ] Sliding window rate limiting algorithm
- [ ] Per-user or per-IP rate limiting
- [ ] Message priority queues
- [ ] Dead letter queue for failed messages

**Medium Priority**:
- [ ] WebSocket support for real-time message streaming
- [ ] Batch push/pop operations
- [ ] Message search and filtering enhancements
- [ ] Retry mechanism for async operations
- [ ] Circuit breaker for database connections
- [ ] Health check endpoint implementation

**Low Priority**:
- [ ] UI dashboard for queue monitoring
- [ ] Message encryption at rest
- [ ] Multi-region support
- [ ] Message compression
- [ ] Integration with message brokers (Kafka, RabbitMQ)

### Reporting Issues

**Found a Bug?**
1. Check existing issues to avoid duplicates
2. Create a new issue with:
    - Clear description of the problem
    - Steps to reproduce
    - Expected vs actual behavior
    - Environment details (Java version, OS, Docker version)
    - Relevant logs or stack traces

**Feature Request?**
1. Describe the feature and its use case
2. Explain why it's valuable
3. Propose an implementation approach (if possible)

### Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Focus on the best solution, not ego
- Help others learn and grow

## 19. License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for full details.

```
MIT License

Copyright (c) 2025 Alak Das

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

**What this means**:
- ✅ Commercial use allowed
- ✅ Modification allowed
- ✅ Distribution allowed
- ✅ Private use allowed
- ⚠️ No warranty provided
- ⚠️ No liability accepted

## 20. Support

### Getting Help

**Documentation**:
- **README**: You're reading it! Comprehensive guide to LightQ
- **API Docs**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

**Community**:
- **GitHub Issues**: [https://github.com/Alak-Das/LightQ/issues](https://github.com/Alak-Das/LightQ/issues)
    - Bug reports
    - Feature requests
    - General questions
- **GitHub Discussions**: [https://github.com/Alak-Das/LightQ/discussions](https://github.com/Alak-Das/LightQ/discussions)
    - Q&A
    - Show and tell
    - Ideas

**Direct Contact**:
- **Email**: [alakdas.mail@gmail.com](mailto:alakdas.mail@gmail.com)
- **Response Time**: Usually within 1-2 business days

### Enterprise Support

For commercial support, SLA agreements, consulting, or custom feature development:
- Contact: [alakdas.mail@gmail.com](mailto:alakdas.mail@gmail.com)
- Subject: "LightQ Enterprise Support Inquiry"

### Acknowledgments

Special thanks to:
- Spring Boot team for the excellent framework
- Redis and MongoDB communities
- All contributors and users of LightQ

---

## 21. Quick Reference Card

### Essential Commands

```bash
# Local Development
mvn spring-boot:run                    # Start application
mvn test                               # Run tests
mvn clean package -DskipTests          # Build JAR

# Docker Deployment
docker compose up -d --build           # Start all services
docker compose logs -f lightq-service  # View logs
docker compose down                    # Stop services

# API Testing
curl -u user:password -X POST "http://localhost:8080/queue/push" \
  -H "consumerGroup: test" -d "message"

curl -u user:password "http://localhost:8080/queue/pop" \
  -H "consumerGroup: test"

curl -u admin:adminpassword "http://localhost:8080/queue/view" \
  -H "consumerGroup: test"
```

### Default Configuration

| Parameter | Default Value |
|-----------|---------------|
| Server Port | 8080 |
| USER credentials | user:password |
| ADMIN credentials | admin:adminpassword |
| Redis host | localhost:6379 |
| MongoDB URI | mongodb://admin:password@localhost:27017 |
| `lightq.cache-ttl-minutes` | 5 |
| `lightq.persistence-duration-minutes` | 30 |
| `rate.limit.push-per-second` | 10 |
| `rate.limit.pop-per-second` | 10 |
| `lightq.message-allowed-to-fetch` | 50 |

### Architecture at a Glance

```
Client → [Rate Limiter] → [Controller] → [Service Layer]
                                           ↓
                              ┌───────────┴──────────┐
                              ↓                      ↓
                        [Redis Cache]         [MongoDB]
                        (5 min TTL)          (30 min TTL)
                              ↑                      ↑
                              └──────────┬───────────┘
                                    [Async Pool]
                                    (5-10 threads)
```

---

**Built with ❤️ using Spring Boot 3.3.5 and Java 21**

**Repository**: [https://github.com/Alak-Das/LightQ](https://github.com/Alak-Das/LightQ)

**Last Updated**: January 2025
