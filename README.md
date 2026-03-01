# springboot-api

A RESTful banking API built with Spring Boot 3.4.2, featuring JWT authentication, Redis caching, and PostgreSQL persistence.

## Features

- JWT-based authentication with access and refresh tokens
- Role-based access control (USER / ADMIN)
- Account management with unique crypto-style account numbers
- Financial transactions: deposits, withdrawals, and transfers
- Transaction status tracking (PENDING → COMPLETED / FAILED) with retry support
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
| Infrastructure | Docker Compose |

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

## Getting Started

### 1. Start Infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d
```

This starts PostgreSQL on port `5432`.

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
| GET | `/api/accounts` | List all accounts | Yes |
| GET | `/api/accounts/{id}` | Get account by ID | Yes |
| GET | `/api/accounts/number/{accountNumber}` | Get account by number | Yes |
| GET | `/api/accounts/user/{userId}` | Get accounts for a user | Yes |
| GET | `/api/accounts/{id}/balance` | Get account balance | Yes |
| DELETE | `/api/accounts/{id}` | Delete account | Yes |

### Transactions

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/api/transactions` | Create a transaction | Yes |
| GET | `/api/transactions` | List all transactions (paginated) | Yes (Admin) |
| GET | `/api/transactions/{id}` | Get transaction by ID | Yes |
| GET | `/api/transactions/from/{accountId}` | Transactions sent from account | Yes |
| GET | `/api/transactions/to/{accountId}` | Transactions received by account | Yes |
| GET | `/api/transactions/status/{status}` | Filter transactions by status | Yes |
| POST | `/api/transactions/{id}/retry` | Retry a FAILED transaction | Yes |

### Users (Admin Only)

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/users` | List all users | Admin |

## Authentication Flow

1. Register a user via `POST /api/auth/register`
2. Login via `POST /api/auth/login` — receive an `accessToken` and `refreshToken`
3. Include the access token in subsequent requests:
   ```
   Authorization: Bearer <accessToken>
   ```
4. Use `POST /api/auth/refresh` with the refresh token to obtain a new access token when it expires

**Token expiry:**
- Access token: 15 minutes
- Refresh token: 7 days

## Transaction Types

| Type | Description |
|---|---|
| `DEPOSIT` | Add funds to an account |
| `WITHDRAWAL` | Remove funds from an account |
| `TRANSFER` | Move funds between two accounts |

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

Every transaction is persisted with a status field, even when the operation fails. This enables debugging and retry workflows.

| Status | Meaning |
|---|---|
| `PENDING` | Balance update in progress |
| `COMPLETED` | Balance update succeeded |
| `FAILED` | Balance update failed (e.g. insufficient funds, optimistic lock conflict) |

Failed transactions can be retried via `POST /api/transactions/{id}/retry` once the underlying issue is resolved (e.g. after adding funds). Retrying a `COMPLETED` transaction returns `409 Conflict`.

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
./mvnw test
```

Integration tests use TestContainers and spin up a real PostgreSQL instance automatically — no manual setup required.

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
        ├── exception/       # Custom exceptions
        ├── mapper/          # MapStruct mappers
        ├── repository/      # Spring Data repositories
        ├── security/        # JWT filter, UserDetailsService
        └── service/         # Business logic
    └── resources/
        ├── application.properties
        └── db/migration/    # Flyway SQL migrations (V1, V2, V3)
```
