# Redis-Based Distributed Rate Limiting Service

A distributed REST API rate limiting service built with **Spring Boot** and **Redis** implementing the **Sliding Window Log** algorithm using atomic **Lua scripting**.

## Table of Contents
- [Overview](#overview)
- [Architecture & Design](#architecture--design)
  - [Sliding Window Log Algorithm](#sliding-window-log-algorithm)
  - [Atomic Operations with Lua](#atomic-operations-with-lua)
  - [Redis Data Structures](#redis-data-structures)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [1. Start Redis Container](#1-start-redis-container)
  - [2. Build the Application](#2-build-the-application)
  - [3. Run the Application](#3-run-the-application)
- [API Reference & Examples](#api-reference--examples)
  - [1. Protected Endpoint](#1-protected-endpoint)
  - [2. Configure Rate Limit](#2-configure-rate-limit)
  - [3. Check Rate Limit Status](#3-check-rate-limit-status)
  - [4. Reset Rate Limit](#4-reset-rate-limit)
- [Rate Limit Headers](#rate-limit-headers)
- [Running Tests](#running-tests)

---

## Overview
Rate limiting protects microservices and REST APIs from overuse, DDoS attacks, and resource exhaustion. In distributed deployments where application instances run behind a load balancer, local in-memory counters fail to maintain accurate client state. This service utilizes Redis as a central, high-performance state store to enforce rolling rate limits consistently across all application instances.

---

## Architecture & Design

### Sliding Window Log Algorithm
Unlike fixed window algorithms which reset counters at boundary intervals (and are susceptible to request bursts at boundaries), the **Sliding Window Log** algorithm tracks individual request timestamps within a continuous rolling window (default: 60 seconds / 60,000 ms).

When a new request arrives at time $t$:
1. Purge all timestamps older than $t - \text{window}$.
2. Count the remaining timestamps in the current window log.
3. If count < limit: add timestamp $t$ to log and allow the request.
4. Otherwise: reject the request with HTTP `429 Too Many Requests`.

### Atomic Operations with Lua
To avoid race conditions between reading request counts and appending new timestamps across multiple app instances, the core rate limiting logic is executed inside a single, atomic Redis Lua script (`src/main/resources/scripts/rate_limiter.lua`).

### Redis Data Structures
- **Redis Sorted Set (`rate_limit:log:{apiKey}`)**: Stores request timestamps as scores. Provides $O(\log N)$ window trimming (`ZREMRANGEBYSCORE`), element counting (`ZCARD`), and timestamp retrieval (`ZRANGE`).
- **Redis Hash (`rate-limit-configs`)**: Persists custom rate limits per API key (`apiKey` $\rightarrow$ `requestsPerMinute`). Unconfigured keys default to 10 requests per minute.

---

## Prerequisites
- **Java 17+**
- **Maven 3.8+**
- **Docker Desktop** (for running Redis 7 container)

---

## Project Structure
```
.
├── setup-redis.sh                         # Shell script to launch Redis Docker container
├── pom.xml                                # Maven dependencies and build configuration
├── README.md                              # Application documentation
└── src
    ├── main
    │   ├── java/com/example/ratelimiter
    │   │   ├── RateLimiterApplication.java # Spring Boot entry point
    │   │   ├── config/
    │   │   │   └── RedisConfig.java       # RedisTemplate & RedisScript beans
    │   │   ├── controller/
    │   │   │   └── RateLimiterController.java # REST API Endpoints
    │   │   ├── dto/
    │   │   │   ├── ApiResponse.java
    │   │   │   ├── RateLimitConfigRequest.java
    │   │   │   ├── RateLimitStatusResponse.java
    │   │   │   └── RateLimiterResult.java
    │   │   ├── exception/
    │   │   │   └── GlobalExceptionHandler.java # Error handling
    │   │   └── service/
    │   │       └── RateLimiterService.java# Core rate limiter logic
    │   └── resources
    │       ├── application.properties
    │       └── scripts
    │           └── rate_limiter.lua       # Atomic Lua script for Redis
    └── test
        └── java/com/example/ratelimiter
            └── RateLimiterApplicationTests.java # Integration tests
```

---

## Getting Started

### 1. Start Redis Container
Run the included shell script to pull and start a Redis 7 Docker container named `rate-limiter-redis` listening on port `6379`:

```bash
chmod +x setup-redis.sh
./setup-redis.sh
```

Verification output:
```
Starting new Redis container: rate-limiter-redis
Verifying Redis connection...
Redis is up and running!
```

### 2. Build the Application
Compile and package the Spring Boot application with Maven:

```bash
mvn clean package -DskipTests
```

### 3. Run the Application
Start the service locally on port `8080`:

```bash
mvn spring-boot:run
```

---

## API Reference & Examples

### 1. Protected Endpoint
**`POST /api/data`**

Requires `X-API-Key` header. Evaluates current request rate against client's limit. Unconfigured keys use default limit of 10 requests/min.

#### Request Example
```bash
curl -i -X POST http://localhost:8080/api/data \
  -H "X-API-Key: key123"
```

#### Response (Success - 200 OK)
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 9
X-RateLimit-Reset: 1723654380
Content-Type: application/json

{
  "message": "Data request processed successfully",
  "data": "Sample REST API protected data payload"
}
```

#### Response (Exceeded Limit - 429 Too Many Requests)
```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1723654380
Content-Type: application/json

{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded for API key: key123"
}
```

---

### 2. Configure Rate Limit
**`POST /api/rate-limit/configure`**

Sets custom requests-per-minute limit for a specific API key. Saved persistently in Redis Hash `rate-limit-configs`.

#### Request Example
```bash
curl -i -X POST http://localhost:8080/api/rate-limit/configure \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "key123",
    "requestsPerMinute": 20
  }'
```

#### Response (200 OK)
```json
{
  "message": "Rate limit configured successfully",
  "apiKey": "key123",
  "requestsPerMinute": 20
}
```

---

### 3. Check Rate Limit Status
**`GET /api/rate-limit/status/{apiKey}`**

Queries current active request count, remaining capacity, total limit, and limit reset time for an API key.

#### Request Example
```bash
curl -i -X GET http://localhost:8080/api/rate-limit/status/key123
```

#### Response (200 OK)
```json
{
  "apiKey": "key123",
  "limit": 20,
  "remaining": 18,
  "resetTime": 1723654380
}
```

---

### 4. Reset Rate Limit
**`DELETE /api/rate-limit/reset/{apiKey}`**

Clears the request log and sequence keys in Redis for the specified API key.

#### Request Example
```bash
curl -i -X DELETE http://localhost:8080/api/rate-limit/reset/key123
```

#### Response (200 OK)
```json
{
  "message": "Rate limit reset successfully for API key: key123"
}
```

---

## Rate Limit Headers

Every call to `POST /api/data` returns standard HTTP rate limit headers:

| Header | Description |
| :--- | :--- |
| `X-RateLimit-Limit` | Total requests allowed per minute for this API key. |
| `X-RateLimit-Remaining` | Remaining requests permitted within current rolling window. |
| `X-RateLimit-Reset` | Unix epoch timestamp (seconds) when the oldest request in the window expires. |

---

## Running Tests

Ensure Redis container is running (`./setup-redis.sh`), then run the unit and integration test suite:

```bash
mvn test
```