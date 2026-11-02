# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

All services use Maven Wrapper with the same commands. Run from each service directory:

```bash
./mvnw clean install          # Full build with tests
./mvnw spring-boot:run        # Start the service
./mvnw test                   # Run all tests
./mvnw test -Dtest=ClassName  # Run a single test class
./mvnw clean package -DskipTests  # Build without running tests
```

**Infrastructure must be running first** (PostgreSQL, MongoDB, Redis, RabbitMQ, Zipkin). The README references `docker-compose -f docker-compose.infra.yml up -d`, but that file is not in the repo — provision infrastructure separately.

**Start order matters:** Launch `productservice`, `cartservice`, `deliveryservice` before `apigateway`.

Service ports: API Gateway (8080), Product (8081), Cart (8082), Delivery (8083).

## Architecture Overview

Spring Boot 3.x / Java 21 microservices system. All services use Maven and expose Prometheus metrics at `/actuator/prometheus`. Distributed tracing uses Micrometer + Brave → Zipkin at `localhost:9411` with 100% sampling.

### Request Flow

```
Client → API Gateway (8080) → [JWT validation, route, inject X-Gateway-Secret header]
  → Product Service (8081) or Cart Service (8082)
Cart Service → RabbitMQ (orders-queue) → Delivery Service (8083) [async consumer]
```

### API Gateway (`apigateway/`)

- Spring Cloud Gateway (reactive/WebFlux)
- Issues and validates JWT tokens using RSA keys; `AuthController` handles `/auth/login` and `/auth/refresh`
- Routes `/api/products/**` → port 8081 and `/api/cart/**` → port 8082
- Injects `X-Gateway-Secret: SuperSecretKey123` into all downstream requests

### Product Service (`productservice/`)

- Serves `POST/GET /api/products` and `GET /api/products/{id}`
- **PostgreSQL** (port 5432, db: `mi_db`) via Spring Data JPA; DDL mode: `update`
- **Redis** (port 6379) caching: products cached 10 min, default TTL 60 min; JSON serialized for RedisInsight visibility
- `GatewayAccessFilter` rejects requests missing the `X-Gateway-Secret` header

### Cart Service (`cartservice/`)

- Serves `POST /api/cart/{username}/add` and `POST /api/cart/{username}/checkout`
- **MongoDB** (port 27017, db: `supermarket_cart`) via Spring Data MongoDB
- **RabbitMQ** producer: publishes to `orders-queue` on checkout
- `GatewayAccessFilter` enforces gateway-only access

### Delivery Service (`deliveryservice/`)

- No REST endpoints — purely a RabbitMQ consumer
- Listens on `orders-queue` with 3 concurrent consumers (`@RabbitListener`)
- Simulates a 2-second processing delay per order

## Security Model

- Clients authenticate through the gateway only; direct calls to services are blocked by `GatewayAccessFilter` (checks `X-Gateway-Secret` header)
- JWTs are RSA-signed; access tokens expire in 1 hour, refresh tokens in 7 days (HttpOnly, Secure, SameSite=Strict cookies)

## Key Configuration Files

| File | What to look at |
|------|----------------|
| `apigateway/src/main/resources/application.yml` | JWT settings, route definitions, gateway secret |
| `productservice/src/main/resources/application.yml` | PostgreSQL/Redis connection, cache TTL |
| `cartservice/src/main/resources/application.yml` | MongoDB URI, RabbitMQ connection, log file path |
| `deliveryservice/src/main/resources/application.yml` | Listener concurrency, log file path |
