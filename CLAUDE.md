# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Start infrastructure (PostgreSQL on 5432; Redis must be run separately or added to compose)
docker compose -f docker/docker-compose.yml up -d

# Run the application
./mvnw spring-boot:run

# Build (skip tests)
./mvnw package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TransactionServiceIntegrationTest

# Run a single test method
./mvnw test -Dtest=TransactionServiceIntegrationTest#createTransfer_success
```

Integration tests spin up PostgreSQL and Redis automatically via TestContainers — no running infrastructure needed for tests.

## Architecture

This is a RESTful banking API. The base package is `com.example.springbootapi`.

**Layer flow:** Controller → Service → Repository (Spring Data JPA) → PostgreSQL

**Key cross-cutting concerns:**
- `JwtAuthenticationFilter` (in `security/`) intercepts every request, validates the Bearer token via `JwtService`, and sets the `SecurityContext`.
- `RateLimitingFilter` (in `filter/`) runs at `HIGHEST_PRECEDENCE`, applies IP-based rate limits using Redis counters with TTL. Per-endpoint limits are configured via `application.properties` (`rate-limit.*`). Only applies to `/api/**` paths.
- Redis is used for two purposes: (1) caching `accounts` and `balances` via Spring Cache (`@Cacheable`/`@CacheEvict`), and (2) rate limit counters in `RateLimitingFilter`.

**Security model:**
- Stateless JWT; no sessions. `SecurityConfig` permits `/api/auth/**`, Swagger, and actuator endpoints. `POST /api/users` is open for registration; all other `/api/users/**` requires `ROLE_ADMIN`.
- JWT claims include a `role` claim on access tokens and a `type: refresh` claim on refresh tokens. Tokens are signed with HMAC-SHA using the base64 key from `jwt.secret`.

**Transaction logic** (`TransactionService`): `TRANSFER` requires both `fromAccountId` and `toAccountId`; `DEPOSIT` only `toAccountId`; `WITHDRAWAL` only `fromAccountId`. Balance updates happen in the same `@Transactional` method, and `balances` cache is fully evicted after each write. `Account` uses a JPA `@Version` field for optimistic locking — concurrent updates throw `ObjectOptimisticLockingFailureException`, which `GlobalExceptionHandler` maps to HTTP 409.

**MapStruct mappers** in `mapper/` convert between JPA entities and DTOs. Lombok and MapStruct annotation processors are wired together in `pom.xml` using `lombok-mapstruct-binding` — the order in `annotationProcessorPaths` matters.

**Database schema** is managed exclusively by Flyway (`src/main/resources/db/migration/`). JPA is set to `ddl-auto=validate`. New schema changes require a new `V{n}__description.sql` migration file.

**Integration test base class** (`BaseIntegrationTest`) starts a shared PostgreSQL 15 and Redis 7 container for the test suite and wires them via `@DynamicPropertySource`. All integration tests extend this class.

**HTTP test files** live in `src/test/http/` and can be run directly from IntelliJ or VS Code REST Client.
