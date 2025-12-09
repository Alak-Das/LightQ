# LightQ

A lightweight, high-performance message queue service built with Spring Boot, providing RESTful APIs for asynchronous message processing with support for consumer groups, Redis caching, and MongoDB persistence.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Cache-red.svg)](https://redis.io/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Database-green.svg)](https://www.mongodb.com/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-blue.svg)](https://github.com/features/actions)

## Table of Contents
1.  [Project Overview](#1-project-overview)
2.  [Key Features](#2-key-features)
3.  [Architecture Overview](#3-architecture-overview)
4.  [Technology Stack](#4-technology-stack)
5.  [Getting Started](#5-getting-started)
6.  [Configuration](#6-configuration)
7.  [API Documentation](#7-api-documentation)
8.  [Design Documents](#8-design-documents)
9.  [Testing](#9-testing)
10. [CI/CD Pipeline](#10-cicd-pipeline)
11. [Performance Considerations](#11-performance-considerations)
12. [Security](#12-security)
13. [Troubleshooting](#13-troubleshooting)
14. [Contributing](#14-contributing)
15. [License](#15-license)

## 1. Project Overview

The LightQ is a production-ready Spring Boot application that implements a distributed message queue system. It enables asynchronous communication between producers and consumers through a RESTful API, with support for multiple isolated consumer groups.

### Use Cases

- **Microservices Communication**: Decouple services with asynchronous messaging
- **Task Queue Management**: Distribute work across multiple consumers
- **Event Processing**: Handle events with guaranteed delivery and processing
- **Load Leveling**: Buffer requests during traffic spikes
- **Background Job Processing**: Queue long-running tasks for asynchronous execution

## 2. Key Features

### Core Functionality
- ✅ **Message Pushing**: Add messages to consumer-specific queues
- ✅ **Message Popping**: Retrieve and automatically mark messages as consumed
- ✅ **Message Viewing**: Inspect queue contents with filtering options (Admin only)
- ✅ **Consumer Groups**: Isolated message streams for multi-tenant scenarios
- ✅ **TTL Management**: Automatic message expiration with configurable duration

### Performance & Reliability
- ⚡ **Redis Caching**: Sub-millisecond message retrieval for hot data
- 💾 **MongoDB Persistence**: Durable storage with automatic TTL cleanup
- 🔄 **Asynchronous Processing**: Non-blocking database operations with thread pool executor
- 📊 **Scalable Design**: Horizontal scaling support via external data stores
- 🎯 **Cache-First Strategy**: Optimized read performance with fallback to database

### Operations & Security
- 🔐 **Role-Based Access Control**: User and Admin roles with HTTP Basic Auth
- 📝 **Comprehensive API Documentation**: Interactive Swagger UI
- 🐳 **Docker Support**: Containerized deployment with docker-compose
- 🔧 **Configurable Thread Pool**: Customizable async executor for workload tuning
- 📈 **Production Ready**: Exception handling, logging, and comprehensive test coverage
- ⚙️ **CI/CD Integration**: GitHub Actions workflow for automated testing and builds

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
        CACHE[Cache Service<br/>Redis Abstraction]
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
    ASYNC -.->|Async Write| M
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

#### Push Operation
```
Client → Controller → Service → [Redis Cache + MongoDB (Async)] → Response
```

#### Pop Operation (Cache Hit)
```
Client → Controller → Service → Redis Cache → Response + MongoDB Update (Async)
```

#### Pop Operation (Cache Miss)
```
Client → Controller → Service → MongoDB (Find & Update) → Response
```

## 4. Technology Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| **Framework** | Spring Boot | 3.2.6 | Core application framework |
| **Language** | Java | 21 | Programming language |
| **Web** | Spring Web | 3.2.6 | RESTful API endpoints |
| **Cache** | Redis + Spring Data Redis | Latest | High-speed message caching |
| **Database** | MongoDB + Spring Data MongoDB | Latest | Persistent message storage |
| **Security** | Spring Security | 3.2.6 | Authentication & authorization |
| **Async** | Spring Async + ThreadPoolTaskExecutor | 3.2.6 | Non-blocking operations |
| **Documentation** | Springdoc OpenAPI | 2.5.0 | Swagger UI for API docs |
| **Validation** | Spring Validation | 3.2.6 | Input validation |
| **Utilities** | Lombok | Latest | Code simplification |
| **Build Tool** | Maven | 3.11.0 | Dependency management |
| **Container** | Docker | Latest | Deployment packaging |
| **CI/CD** | GitHub Actions | Latest | Automated testing & builds |

## 5. Getting Started

### Prerequisites

- **Java 21** or higher ([Download](https://adoptium.net/))
- **Maven 3.6+** ([Download](https://maven.apache.org/download.cgi))
- **Redis 6.0+** ([Installation Guide](https://redis.io/docs/getting-started/installation/))
- **MongoDB 5.0+** ([Installation Guide](https://www.mongodb.com/docs/manual/installation/))

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd lightq
   ```

2. **Configure application properties**

   Edit `src/main/resources/application.properties`:
   ```properties
   # Server Configuration
   server.port=8080
   logging.level.root=INFO
   
   # Security Configuration
   security.user.username=user
   security.user.password=password
   security.admin.username=admin
   security.admin.password=adminpassword
   
   # Redis Configuration
   spring.data.redis.host=localhost
   spring.data.redis.port=6379
   cache.ttl.minutes=5
   
   # MongoDB Configuration
   spring.data.mongodb.uri=mongodb://admin:password@localhost:27017
   spring.data.mongodb.database=lightq-db
   persistence.duration.minutes=30
   
   # API Configuration
   no.of.message.allowed.to.fetch=50
   ```

3. **Build the application**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   Or run the JAR directly:
   ```bash
   java -jar target/lightq-0.0.1-SNAPSHOT.jar
   ```

### Docker Deployment

The project includes a multi-stage Dockerfile and docker-compose configuration for easy deployment.

1. **Using Docker Compose (Recommended)**
   ```bash
   docker-compose up -d
   ```

   This will start:
    - MongoDB with authentication
    - Redis cache server
    - LightQ application

2. **Manual Docker Build**
   ```bash
   # Build the image
   docker build -t lightq:latest .
   
   # Run the container
   docker run -p 8080:8080 \
     -e MONGO_URI=mongodb://admin:password@mongodb:27017 \
     -e SPRING_DATA_REDIS_HOST=redis \
     lightq:latest
   ```

### Verify Installation

- **Application Health**: `http://localhost:8080/queue/pop` (will return 404 if no messages)
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **API Documentation**: `http://localhost:8080/v3/api-docs`

## 6. Configuration

### Environment Variables

The application supports configuration via environment variables for containerized deployments:

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGO_URI` | `mongodb://admin:password@localhost:27017` | MongoDB connection string |
| `MONGO_DB` | `lightq-db` | MongoDB database name |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis server host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis server port |
| `SECURITY_USER_USERNAME` | `user` | User role username |
| `SECURITY_USER_PASSWORD` | `password` | User role password |
| `SECURITY_ADMIN_USERNAME` | `admin` | Admin role username |
| `SECURITY_ADMIN_PASSWORD` | `adminpassword` | Admin role password |

### Thread Pool Configuration

Async operations are handled by a configurable thread pool (defined in `AsyncConfig.java`):

```java
Core Pool Size: 5
Max Pool Size: 10
Queue Capacity: 25
Thread Name Prefix: DBDataUpdater-
```

Adjust these values in `SQSConstants.java` based on your workload:
- Increase for high-throughput scenarios
- Decrease for resource-constrained environments

### Redis Cache Settings

- **TTL (Time-To-Live)**: `cache.ttl.minutes=5` (default)
- **Key Pattern**: `consumerGroupMessages:{consumerGroup}`
- **Data Structure**: Redis List (FIFO queue)

### MongoDB Settings

- **Message Persistence**: `persistence.duration.minutes=30` (default)
- **Collection Naming**: Each consumer group gets its own collection
- **TTL Index**: Automatically created on `createdAt` field
- **Authentication**: Supports MongoDB authentication via connection URI

## 7. API Documentation

### Base URL
```
http://localhost:8080/queue
```

### Authentication

All endpoints require HTTP Basic Authentication:

| Role | Username | Password | Permissions |
|------|----------|----------|-------------|
| User | `user` | `password` | Push, Pop |
| Admin | `admin` | `adminpassword` | Push, Pop, View |

### Endpoints

#### 1. Push Message

Add a new message to a consumer group's queue.

```http
POST /queue/push
Content-Type: text/plain
consumerGroup: my-consumer-group
Authorization: Basic dXNlcjpwYXNzd29yZA==

Hello, World!
```

**Response (200 OK):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "content": "Hello, World!",
  "createdAt": "2024-12-05T10:30:00"
}
```

**Features:**
- Message immediately added to Redis cache
- Asynchronously persisted to MongoDB with TTL index
- Returns unique message ID

#### 2. Pop Message

Retrieve and consume the oldest message from a queue.

```http
GET /queue/pop
consumerGroup: my-consumer-group
Authorization: Basic dXNlcjpwYXNzd29yZA==
```

**Response (200 OK):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "content": "Hello, World!",
  "createdAt": "2024-12-05T10:30:00"
}
```

**Response (404 Not Found):** No messages available

**Features:**
- Cache-first retrieval for performance
- Automatic fallback to MongoDB if cache miss
- Message marked as consumed atomically

#### 3. View Messages (Admin Only)

Inspect messages in a queue without consuming them.

```http
GET /queue/view
consumerGroup: my-consumer-group
messageCount: 10
consumed: no
Authorization: Basic YWRtaW46YWRtaW5wYXNzd29yZA==
```

**Query Parameters:**
- `messageCount` (required): Number of messages to retrieve (1-50)
- `consumed` (optional): Filter by status - "yes" (consumed), "no" (unconsumed), or omit for all

**Response (200 OK):**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "content": "Hello, World!",
    "consumerGroup": "my-consumer-group",
    "createdAt": "2024-12-05T10:30:00.000+00:00",
    "consumed": false
  }
]
```

**Features:**
- Combines results from cache and MongoDB
- Removes duplicates automatically
- Sorts by creation time

### Error Responses

All errors follow this structure:
```json
{
  "timestamp": "2024-12-05T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Message Count should be 1 to 50.",
  "path": "/queue/view"
}
```

**Common Error Codes:**
- `400 Bad Request`: Invalid parameters or missing headers
- `401 Unauthorized`: Missing or invalid credentials
- `403 Forbidden`: Insufficient permissions (e.g., User trying to view)
- `404 Not Found`: No messages available in queue
- `500 Internal Server Error`: Server-side errors

## 8. Design Documents

### High-Level Design (HLD)

#### System Components

1. **REST API Layer** (`MessageController`): Handles HTTP requests/responses, validation, and header parsing
2. **Service Layer**: Business logic for push, pop, view operations with optimized data flow
3. **Cache Layer** (`CacheService`): Redis-based fast access to recent messages with TTL management
4. **Persistence Layer**: MongoDB for durable storage with automatic TTL cleanup
5. **Security Layer** (`SecurityConfig`): HTTP Basic authentication with role-based access control
6. **Async Layer** (`AsyncConfig`): Thread pool executor for non-blocking database operations

### Low-Level Design (LLD)

#### Class Diagram

```mermaid
classDiagram
    class MessageController {
        -PushMessageService pushMessageService
        -PopMessageService popMessageService
        -ViewMessageService viewMessageService
        +MessageResponse push(String, String)
        +ResponseEntity~MessageResponse~ pop(String)
        +ResponseEntity~List~ view(String, int, String)
    }
    
    class PushMessageService {
        -MongoTemplate mongoTemplate
        -CacheService cacheService
        -Executor taskExecutor
        -MongoClient mongoClient
        +Message push(Message)
        -void createTTLIndex(Message)
    }
    
    class PopMessageService {
        -MongoTemplate mongoTemplate
        -CacheService cacheService
        -Executor taskExecutor
        +Optional~Message~ pop(String)
        -void updateMessageInMongo(String, String)
    }
    
    class ViewMessageService {
        -MongoTemplate mongoTemplate
        -CacheService cacheService
        +List~Message~ view(String, int, String)
    }
    
    class CacheService {
        -RedisTemplate redisTemplate
        +void addMessage(Message)
        +Message popMessage(String)
        +List~Message~ viewMessages(String)
    }
    
    class Message {
        +String id
        +String content
        +String consumerGroup
        +Date createdAt
        +boolean consumed
        +Message markConsumed()
    }
    
    class MessageResponse {
        +String id
        +String content
        +LocalDateTime createdAt
    }
    
    class GlobalExceptionHandler {
        +ResponseEntity handleException(Exception)
        +ResponseEntity handleIllegalArgumentException(IllegalArgumentException)
    }
    
    MessageController --> PushMessageService
    MessageController --> PopMessageService
    MessageController --> ViewMessageService
    PushMessageService --> CacheService
    PopMessageService --> CacheService
    ViewMessageService --> CacheService
    MessageController ..> MessageResponse
    Message ..> MessageResponse
```

### Database Design

#### MongoDB Schema

**Collection Naming Convention**: `{consumerGroup}` (e.g., `payments-queue`, `notifications-queue`)

**Document Structure**:
```json
{
  "_id": "uuid-string",
  "content": "message payload",
  "consumerGroup": "group-name",
  "createdAt": ISODate("2024-12-05T10:30:00Z"),
  "consumed": false
}
```

**Indexes**:
- `_id`: Primary key (unique, auto-indexed)
- `createdAt`: TTL index (auto-deletion after configured minutes)
- `consumed`: Regular index (filter unconsumed messages efficiently)

**Index Creation Strategy:**
- TTL index created dynamically on first push to each consumer group
- Automatically checks for existing indexes before creation
- Configurable expiration via `persistence.duration.minutes`

#### Redis Data Structure

**Key Pattern**: `consumerGroupMessages:{consumerGroup}`

**Data Type**: List (FIFO queue implementation)

**Operations**:
- **Push**: `LPUSH` (left push - add to head for newest messages)
- **Pop**: `RPOP` (right pop - remove from tail for oldest messages)
- **View**: `LRANGE` (range query for inspection)

**TTL Management**:
- Configured via `cache.ttl.minutes`
- Automatically refreshed on each push operation
- Prevents memory bloat from abandoned queues

### Sequence Diagrams

#### Push Message Flow
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant PushService
    participant CacheService
    participant Redis
    participant MongoDB
    participant AsyncExecutor
    
    Client->>Controller: POST /queue/push
    Controller->>PushService: push(message)
    PushService->>CacheService: addMessage(message)
    CacheService->>Redis: LPUSH + EXPIRE
    Redis-->>CacheService: OK
    CacheService-->>PushService: Success
    PushService->>AsyncExecutor: execute(saveTask)
    PushService-->>Controller: Message
    Controller-->>Client: 200 OK + MessageResponse
    
    Note over AsyncExecutor,MongoDB: Async Operation
    AsyncExecutor->>MongoDB: Check TTL Index
    AsyncExecutor->>MongoDB: save(message)
    MongoDB-->>AsyncExecutor: Success
```

#### Pop Message Flow (Cache Hit)
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant PopService
    participant CacheService
    participant Redis
    participant MongoDB
    participant AsyncExecutor
    
    Client->>Controller: GET /queue/pop
    Controller->>PopService: pop(consumerGroup)
    PopService->>CacheService: popMessage(consumerGroup)
    CacheService->>Redis: RPOP
    Redis-->>CacheService: Message
    CacheService-->>PopService: Message
    PopService->>AsyncExecutor: execute(updateTask)
    PopService-->>Controller: Optional[Message]
    Controller-->>Client: 200 OK + MessageResponse
    
    Note over AsyncExecutor,MongoDB: Async Update
    AsyncExecutor->>MongoDB: findAndModify(consumed=true)
    MongoDB-->>AsyncExecutor: Success
```

## 9. Testing

The project includes comprehensive unit tests with over **80% code coverage**.

### Test Structure

```
src/test/java/com/al/simplequeueservice/
├── SimpleQueueServiceApplicationTest.java    # Context loading test
├── config/
│   ├── AsyncConfigTest.java                  # Thread pool configuration tests
│   ├── RedisConfigTest.java                  # Redis setup tests
│   └── SecurityConfigTest.java               # Security configuration tests
├── controller/
│   └── MessageControllerTest.java            # API endpoint tests
├── exception/
│   └── GlobalExceptionHandlerTest.java       # Exception handling tests
└── service/
    ├── CacheServiceTest.java                 # Redis cache operations tests
    ├── PopMessageServiceTest.java            # Pop logic tests
    ├── PushMessageServiceTest.java           # Push logic tests
    └── ViewMessageServiceTest.java           # View logic tests
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MessageControllerTest

# Run with coverage report
mvn test jacoco:report

# Skip tests during build
mvn clean install -DskipTests
```

### Test Coverage

| Component | Coverage | Test Count |
|-----------|----------|------------|
| Controllers | 95%+ | 8 tests |
| Services | 90%+ | 15+ tests |
| Configuration | 85%+ | 6 tests |
| Exception Handlers | 100% | 2 tests |
| **Overall** | **~85%** | **31+ tests** |

### Key Test Features

- **Mocking**: Uses Mockito for dependency mocking
- **Security Testing**: Tests with `@WithMockUser` for role-based access
- **Integration**: `@WebMvcTest` for controller integration tests
- **Parameterized Tests**: Multiple scenarios per test method
- **Async Testing**: Verifies async executor behavior

## 10. CI/CD Pipeline

The project includes a GitHub Actions workflow for automated testing and builds.

### Workflow Configuration

**Location**: `.github/workflows/maven.yml`

**Triggers:**
- Push to `main` branch
- Pull requests to `main` branch

### Pipeline Stages

```yaml
Build Environment:
├── Ubuntu Latest
├── JDK 21 (Eclipse Temurin)
├── Maven with dependency caching
└── Service Containers:
    ├── MongoDB (port 27017)
    └── Redis (port 6379)

Build Steps:
1. Checkout code
2. Set up JDK 21
3. Build with Maven (clean package)
4. Update dependency graph (Dependabot integration)
```

### Environment Variables in CI

```yaml
MONGO_URI: mongodb://admin:password@localhost:27017
MONGO_DB: lightq-db
SECURITY_USER_USERNAME: user
SECURITY_USER_PASSWORD: password
SECURITY_ADMIN_USERNAME: admin
SECURITY_ADMIN_PASSWORD: adminpassword
```

### Running Locally

To simulate the CI environment locally:

```bash
# Start MongoDB and Redis
docker run -d -p 27017:27017 --name test-mongo mongo
docker run -d -p 6379:6379 --name test-redis redis

# Run tests with CI environment variables
MONGO_URI=mongodb://admin:password@localhost:27017 \
MONGO_DB=lightq-db \
mvn clean package
```

### Build Status

The workflow ensures:
- ✅ All tests pass
- ✅ Code compiles successfully
- ✅ Dependencies are up to date
- ✅ No critical vulnerabilities (via Dependabot)

## 11. Performance Considerations

### Caching Strategy

- **Write-Through Cache**: Messages written to Redis immediately, MongoDB asynchronously
- **Cache-First Read**: Pop operations check Redis before MongoDB
- **TTL Management**: Automatic expiration prevents memory bloat
- **List-Based Queue**: Redis Lists provide O(1) push/pop operations

### Optimization Tips

1. **Tune Thread Pool**: Adjust `AsyncConfig` executor settings based on load
   ```java
   // In SQSConstants.java
   CORE_POOL_SIZE = 10;      // Increase for high throughput
   MAX_POOL_SIZE = 50;       // Scale for burst traffic
   QUEUE_CAPACITY = 500;     // Buffer for async operations
   ```

2. **Redis Connection Pooling**: Configure `RedisTemplate` with connection pool
   ```properties
   spring.data.redis.lettuce.pool.max-active=20
   spring.data.redis.lettuce.pool.max-idle=10
   spring.data.redis.lettuce.pool.min-idle=5
   ```

3. **MongoDB Indexes**: Ensure indexes exist on high-query fields
    - Automatically created: `createdAt` (TTL index)
    - Consider adding: `consumed` (filter index)

4. **Message Size**: Keep message payloads under 1MB for optimal performance
    - Redis: Better memory efficiency
    - MongoDB: Faster document operations

5. **Consumer Groups**: Use separate groups to parallelize processing
    - Each group has isolated collections
    - Enables horizontal scaling of consumers

### Performance Metrics

**Expected Performance (single instance):**
- Push throughput: 1,000+ msgs/sec
- Pop latency (cache hit): <5ms
- Pop latency (cache miss): <50ms
- View latency: <100ms for 50 messages

### Scalability

- **Horizontal Scaling**: Run multiple instances (stateless design)
  ```bash
  docker-compose up --scale queue-service=3
  ```
- **Redis Clustering**: Use Redis Cluster for distributed caching
- **MongoDB Sharding**: Shard by `consumerGroup` for large-scale deployments
- **Load Balancing**: Use NGINX or similar for request distribution

### Monitoring Recommendations

Add Spring Boot Actuator for production monitoring:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Key metrics to monitor:
- Cache hit/miss ratio
- Async executor queue size
- MongoDB connection pool stats
- Redis memory usage
- Message processing latency

## 12. Security

### Authentication Methods

- **HTTP Basic Auth**: Default for quick setup (current implementation)
- **Recommended for Production**:
    - JWT tokens with Spring Security OAuth2
    - API Keys with rate limiting
    - mTLS for service-to-service communication

### Current Security Model

**In-Memory User Store** (`SecurityConfig.java`):
```java
User Role:
  - Username: configurable via env
  - Password: BCrypt encoded
  - Permissions: Push, Pop

Admin Role:
  - Username: configurable via env
  - Password: BCrypt encoded
  - Permissions: Push, Pop, View
  - Inherits: User role
```

**Role-Based Access Control:**
```java
/queue/push  → USER, ADMIN
/queue/pop   → USER, ADMIN
/queue/view  → ADMIN only
```

### Best Practices

1. **Change Default Credentials**: Update environment variables before deployment
   ```bash
   export SECURITY_USER_PASSWORD="$(openssl rand -base64 32)"
   export SECURITY_ADMIN_PASSWORD="$(openssl rand -base64 32)"
   ```

2. **Use HTTPS**: Enable SSL/TLS in production
   ```properties
   server.ssl.enabled=true
   server.ssl.key-store=classpath:keystore.p12
   server.ssl.key-store-password=${SSL_PASSWORD}
   ```

3. **Network Isolation**: Deploy Redis/MongoDB in private networks
    - Use Docker networks
    - Configure firewall rules
    - Disable public access

4. **MongoDB Authentication**: Always use authentication
   ```
   MONGO_URI=mongodb://username:password@host:port/?authSource=admin
   ```

5. **Redis Security**:
   ```properties
   spring.data.redis.password=${REDIS_PASSWORD}
   ```

6. **Rate Limiting**: Implement API rate limiting
   ```java
   @Bean
   public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
       // Configure rate limiting per IP/user
   }
   ```

### Security Headers

Configure security headers for web protection:
```java
http.headers()
    .contentSecurityPolicy("default-src 'self'")
    .and()
    .xssProtection()
    .and()
    .frameOptions().deny();
```

## 13. Troubleshooting

### Common Issues

#### 1. Connection Refused (Redis/MongoDB)

**Problem**: Service can't connect to Redis or MongoDB

**Solution**:
```bash
# Check Redis is running
redis-cli ping  # Should return PONG

# Check MongoDB is running
mongosh --eval "db.adminCommand('ping')"

# Verify connection settings
echo $MONGO_URI
echo $SPRING_DATA_REDIS_HOST

# Check Docker services
docker-compose ps
docker-compose logs redis
docker-compose logs mongodb
```

#### 2. Messages Not Persisting

**Problem**: Messages disappear unexpectedly

**Solution**:
- Check TTL settings: `persistence.duration.minutes` in application.properties
- Verify MongoDB TTL indexes:
  ```javascript
  use lightq-db
  db.{consumerGroup}.getIndexes()
  ```
- Check async executor isn't overloaded:
  ```bash
  # Look for thread pool exhaustion in logs
  grep "DBDataUpdater" application.log
  ```
- Ensure MongoDB has disk space

#### 3. Slow Pop Operations

**Problem**: Pop requests taking too long

**Solution**:
- Monitor Redis cache hit rate
  ```bash
  redis-cli INFO stats | grep hits
  ```
- Check MongoDB indexes on `consumed` field
  ```javascript
  db.{consumerGroup}.getIndexes()
  db.{consumerGroup}.createIndex({consumed: 1})
  ```
- Review network latency between service and databases
- Check async executor queue size in logs

#### 4. Authentication Failures

**Problem**: 401 Unauthorized errors

**Solution**:
```bash
# Test with curl
curl -u user:password http://localhost:8080/queue/pop \
  -H "consumerGroup: test-group"

# Verify credentials in environment
echo $SECURITY_USER_USERNAME
echo $SECURITY_USER_PASSWORD

# Check application logs for details
docker-compose logs queue-service | grep -i auth
```

#### 5. Docker Compose Issues

**Problem**: Services not starting correctly

**Solution**:
```bash
# Check service health
docker-compose ps

# View service logs
docker-compose logs -f

# Restart specific service
docker-compose restart mongodb

# Clean rebuild
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

#### 6. Out of Memory (Redis)

**Problem**: Redis runs out of memory

**Solution**:
```properties
# Configure Redis maxmemory
maxmemory 256mb
maxmemory-policy allkeys-lru

# Reduce cache TTL
cache.ttl.minutes=5

# Monitor memory usage
redis-cli INFO memory
```

### Debug Mode

Enable debug logging for troubleshooting:
```properties
logging.level.root=DEBUG
logging.level.com.al.simplequeueservice=DEBUG
logging.level.org.springframework.data.mongodb=DEBUG
logging.level.org.springframework.data.redis=DEBUG
```

### Health Checks

Access health endpoints (requires Actuator):
- `GET /actuator/health` - Overall health
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/env` - Environment properties

## 14. Contributing

We welcome contributions! Please follow these guidelines:

### Development Setup

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Run tests: `mvn test`
5. Run build: `mvn clean install`
6. Commit: `git commit -m 'Add amazing feature'`
7. Push: `git push origin feature/amazing-feature`
8. Open a Pull Request

### Code Standards

- Follow Java naming conventions (CamelCase for classes, camelCase for methods)
- Add JavaDoc comments for public methods
- Write unit tests for new features (maintain >80% coverage)
- Use Lombok annotations to reduce boilerplate
- Follow Spring Boot best practices
- Add integration tests for new endpoints

### Testing Requirements

Before submitting a PR:
```bash
# Run all tests
mvn test

# Check code coverage
mvn test jacoco:report
# View report at target/site/jacoco/index.html

# Verify build
mvn clean install

# Run with Docker
docker-compose build
docker-compose up
```

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Example:
```
feat(cache): add Redis connection pooling

- Configure Lettuce connection pool
- Add pool monitoring
- Update documentation

Closes #123
```

### Reporting Issues

Please include:
- Clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Environment details (Java version, OS, Docker version)
- Relevant logs and screenshots
- Sample code or curl commands

## 15. License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Redis Documentation](https://redis.io/docs/)
- [MongoDB Documentation](https://www.mongodb.com/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [REST API Best Practices](https://restfulapi.net/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## Project Statistics

- **Lines of Code**: ~2,500
- **Test Coverage**: ~85%
- **Dependencies**: 13 direct
- **Java Version**: 21 (LTS)
- **Spring Boot Version**: 3.2.6

## Support

For questions or support:
- 📧 Email: [alakdas.mail@gmail.com](mailto:alakdas.mail@gmail.com)
- 🐛 Issues: [GitHub Issues](https://github.com/Alak-Das/lightq/issues)
- 📖 Documentation: [Swagger UI](http://localhost:8080/swagger-ui.html)

---

**Built with ❤️ using Spring Boot | Maintained by the Community**
