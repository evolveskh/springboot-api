# springboot-api

A RESTful banking API built with Spring Boot 3.4.2, featuring JWT authentication, Redis caching, IP-based rate limiting, and PostgreSQL persistence.

**Live:** https://springboot-api-production-7cbd.up.railway.app
**Docs:** https://springboot-api-production-7cbd.up.railway.app/swagger-ui.html

## Features

- JWT-based authentication with access and refresh tokens
- Role-based access control (USER / ADMIN)
- Ownership-based authorization — users can only access their own accounts and transactions
- Account management with unique crypto-style account numbers
- Financial transactions: deposits, withdrawals, and transfers
- Transaction status tracking (PENDING → COMPLETED / FAILED) with retry support
- Optimistic locking on accounts — concurrent conflicts return 409 Conflict
- Account statements with opening and closing balances (paginated, date-filtered)
- IP-based rate limiting with per-endpoint configuration
- Redis caching for account and balance lookups
- Database schema versioning via Flyway
- OpenAPI / Swagger UI documentation
- Integration tests using TestContainers

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.4.2 |
| Language | Java 17 |
| Database | PostgreSQL |
| Caching | Redis |
| Auth | JWT (JJWT 0.12.3) |
| Migrations | Flyway |
| Mapping | MapStruct |
| Docs | SpringDoc OpenAPI |
| Testing | JUnit 5 + TestContainers |
| Infrastructure | Railway (app), Neon (Postgres), Upstash (Redis) |

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

## Getting Started

### 1. Start Infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d
```

This starts PostgreSQL on port `5432`. Redis must be running separately (or added to the compose file).

### 2. Run the Application

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 3. View API Documentation

Open `http://localhost:8080/swagger-ui.html` in your browser.

## API Endpoints

### Authentication

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/api/auth/register` | Register a new user | No |
| POST | `/api/auth/login` | Login and receive tokens | No |
| POST | `/api/auth/refresh` | Refresh access token | No |

### Accounts

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/api/accounts` | Create a new account | Yes |
| GET | `/api/accounts` | List all accounts | Yes (Admin) |
| GET | `/api/accounts/{id}` | Get account by ID | Yes (Owner / Admin) |
| GET | `/api/accounts/number/{accountNumber}` | Get account by number | Yes (Owner / Admin) |
| GET | `/api/accounts/user/{userId}` | Get accounts for a user | Yes (Owner / Admin) |
| GET | `/api/accounts/{id}/balance` | Get account balance | Yes (Owner / Admin) |
| GET | `/api/accounts/{id}/statement` | Get account statement (paginated, date range) | Yes (Owner / Admin) |
| DELETE | `/api/accounts/{id}` | Delete account | Yes (Owner / Admin) |

Statement query parameters: `from` (ISO date, e.g. `2026-01-01`), `to` (ISO date), `page`, `size`.

### Transactions

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/api/transactions` | Create a transaction | Yes |
| GET | `/api/transactions` | List all transactions (paginated) | Yes (Admin) |
| GET | `/api/transactions/{id}` | Get transaction by ID | Yes (Owner / Admin) |
| GET | `/api/transactions/from/{accountId}` | Transactions sent from account | Yes (Owner / Admin) |
| GET | `/api/transactions/to/{accountId}` | Transactions received by account | Yes (Owner / Admin) |
| GET | `/api/transactions/status/{status}` | Filter by status | Yes (Admin sees all; User sees own) |
| POST | `/api/transactions/{id}/retry` | Retry a FAILED transaction | Yes (Owner / Admin) |

### Users

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/users` | List all users | Yes (Admin) |

## Authentication Flow

1. Register via `POST /api/auth/register`
2. Login via `POST /api/auth/login` — receive an `accessToken` and `refreshToken`
3. Include the access token in subsequent requests:
   ```
   Authorization: Bearer <accessToken>
   ```
4. Use `POST /api/auth/refresh` with the refresh token to obtain a new access token when it expires

**Token expiry:**
- Access token: 15 minutes
- Refresh token: 7 days

## Authorization

All `/api/**` endpoints require a valid JWT. Authorization is enforced at the service layer:

- **ADMIN** can read and operate on any resource.
- **USER** can only access accounts and transactions they own. Accessing another user's resource returns `403 Forbidden`.
- `GET /api/accounts` and `GET /api/transactions` are restricted to ADMIN only.

## Transaction Types

| Type | Description |
|---|---|
| `DEPOSIT` | Add funds to an account (`toAccountId` required) |
| `WITHDRAWAL` | Remove funds from an account (`fromAccountId` required) |
| `TRANSFER` | Move funds between two accounts (both IDs required) |

Example request body for a transfer:

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 250.00,
  "type": "TRANSFER"
}
```

## Transaction Status

Every transaction is saved immediately as `PENDING`. The status is updated once the balance operation completes.

| Status | Meaning |
|---|---|
| `PENDING` | Balance update in progress |
| `COMPLETED` | Balance update succeeded |
| `FAILED` | Balance update failed (e.g. insufficient funds, optimistic lock conflict) |

`FAILED` transactions can be retried via `POST /api/transactions/{id}/retry` once the underlying issue is resolved (e.g. after topping up funds). Retrying a `COMPLETED` transaction returns `409 Conflict`.

## Rate Limiting

All `/api/**` endpoints are protected by an IP-based rate limiter backed by Redis. When a limit is exceeded, the API returns `429 Too Many Requests`.

| Endpoint pattern | Limit |
|---|---|
| `POST /api/auth/login` | 5 req / 60 s |
| `POST /api/auth/register` | 10 req / 3600 s |
| `POST /api/transactions` | 20 req / 60 s |
| All other `/api/**` | 100 req / 60 s |

Every response includes rate limit headers:

```
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 17
X-RateLimit-Retry-After: 42   (only on 429)
```

Limits are configurable in `application.properties`:

```properties
rate-limit.login.max-requests=5
rate-limit.login.window-seconds=60
rate-limit.register.max-requests=10
rate-limit.register.window-seconds=3600
rate-limit.transactions.max-requests=20
rate-limit.transactions.window-seconds=60
rate-limit.default.max-requests=100
rate-limit.default.window-seconds=60
```

## Error Responses

All errors follow a consistent JSON structure:

```json
{
  "timestamp": "2026-03-03T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found with id: 42",
  "path": "/api/accounts/42"
}
```

| HTTP Status | Cause |
|---|---|
| 400 | Validation failure, insufficient funds |
| 401 | Missing or invalid JWT |
| 403 | Accessing another user's resource |
| 404 | Resource not found |
| 409 | Concurrent update conflict, retrying a completed transaction, duplicate user |
| 429 | Rate limit exceeded |
| 500 | Unexpected server error |

## Configuration

Key settings in `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.cache.redis.time-to-live=600000

# JWT
app.jwt.access-token-expiration=900000
app.jwt.refresh-token-expiration=604800000
```

## Running Tests

```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TransactionServiceIntegrationTest

# Run a single test method
./mvnw test -Dtest=TransactionServiceIntegrationTest#createTransfer_success

# Build without tests
./mvnw package -DskipTests
```

Integration tests use TestContainers and spin up real PostgreSQL 15 and Redis 7 containers automatically — no manual infrastructure setup required.

HTTP request files for manual testing are available in `src/test/http/` (compatible with IntelliJ HTTP Client and VS Code REST Client):

- `auth-requests.http`
- `account-requests.http`
- `transaction-requests.http`
- `transaction-status.http`
- `user-requests.http`
- `railway-deployed.http` — targets the live Railway deployment

## Database Migrations

Schema is managed exclusively by Flyway. JPA is set to `ddl-auto=validate`. New schema changes require a new versioned migration file under `src/main/resources/db/migration/`.

| Migration | Description |
|---|---|
| `V1__init_schema.sql` | Initial schema: users, accounts, transactions |
| `V2__add_account_version.sql` | Adds `version` column for optimistic locking |
| `V3__add_transaction_status.sql` | Adds `status` column to transactions |

## Project Structure

```
src/
└── main/
    └── java/com/example/springbootapi/
        ├── config/          # Security configuration
        ├── controller/      # REST controllers
        ├── dto/             # Request / response DTOs
        ├── entity/          # JPA entities
        ├── enums/           # Role, TransactionType, TransactionStatus
        ├── exception/       # Custom exceptions and GlobalExceptionHandler
        ├── filter/          # RateLimitingFilter
        ├── mapper/          # MapStruct mappers
        ├── repository/      # Spring Data repositories
        ├── security/        # JWT filter, UserDetailsService
        └── service/         # Business logic
    └── resources/
        ├── application.properties
        └── db/migration/    # Flyway SQL migrations (V1, V2, V3)
└── test/
    ├── java/                # Unit and integration tests
    └── http/                # HTTP request files for manual testing
```
