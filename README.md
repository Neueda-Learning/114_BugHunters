# Payment Processing Platform

A full-stack payment processing system with a Spring Boot REST API backend and a
React (Vite) frontend console for creating, validating, and tracking payments with
OTP-based authorization.

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Prerequisites](#prerequisites)
4. [Getting Started](#getting-started)
5. [Configuration](#configuration)
6. [Payment Lifecycle](#payment-lifecycle)
7. [API Endpoints](#api-endpoints)
8. [Testing](#testing)

## Architecture

- **Backend**: Spring Boot 4.1.0 (Java 21) REST API backed by MySQL, using Spring
  Data JPA for persistence and Bean Validation (`@Valid`) plus a custom
  `PaymentValidator` for business rule enforcement (amount limits, currency codes,
  account checks, sufficient funds, idempotency keys, status transitions).
- **Frontend**: React + Vite single-page app served via nginx in production. It
  calls the backend REST API (`client/src/api`) to list accounts, create payments,
  trigger OTP verification, and display a payment dashboard.
- **Database**: MySQL with three core tables — `account`, `payment`, and
  `payment_history` (an audit trail of status transitions).
- **Authentication for payment execution**: Email-based OTP verification
  (`OtpVerificationService` / `EmailOtpVerificationService`) required before a
  validated payment can be processed and funds transferred.
- **Deployment**: Dockerized backend and frontend, orchestrated with
  `docker-compose.yml`, and built/tested/deployed via a Jenkins pipeline
  (`Jenkinsfile`). See [docs/cd-deployment.md](docs/cd-deployment.md) for details.

## Project Structure

```
114_BugHunters/
├── src/main/java/com/example/paymentprocessing/
│   ├── controller/     # REST endpoints (PaymentController, AccountController)
│   ├── service/        # Business logic (PaymentService, AccountService, OTP services)
│   ├── model/          # JPA entities (Payment, Account, PaymentHistory)
│   ├── repository/     # Spring Data JPA repositories
│   ├── dto/            # Request/response payloads (e.g. OtpVerificationRequest)
│   ├── enums/          # PaymentStatus lifecycle enum
│   ├── validation/     # PaymentValidator - business rule validation
│   ├── exception/      # Custom exceptions + @RestControllerAdvice handler
│   └── config/         # Spring configuration/beans
├── src/main/resources/
│   ├── application.properties
│   └── schema/         # SQL DDL for payment, account, payment_history tables
├── src/test/java/...   # Unit/integration tests (controller + service layers)
├── client/             # React frontend (Vite)
│   └── src/
│       ├── api/                 # HTTP clients (accounts.js, payments.js)
│       ├── features/payments/   # Dashboard, OTP modal, and related components
│       ├── App.jsx              # Root application shell (layout, footer, etc.)
│       └── main.jsx             # React entry point
├── docs/               # Deployment and development documentation
├── Dockerfile          # Backend container image
├── client/Dockerfile   # Frontend container image (nginx)
├── docker-compose.yml  # Multi-container orchestration (backend, frontend, MySQL)
└── Jenkinsfile         # CI/CD pipeline definition
```

## Prerequisites

- Java 21
- Maven (or use the bundled `mvnw` / `mvnw.cmd` wrapper)
- Node.js (for the `client` frontend)
- MySQL 8.x (or use the provided `docker-compose.yml`)
- Docker & Docker Compose (optional, for containerized runs)

## Getting Started

### Backend (local)

```powershell
# from the repo root
.\mvnw.cmd -q -DskipTests compile   # build
.\mvnw.cmd spring-boot:run          # run (defaults to http://localhost:8080)
```

Create a `paymentdb` MySQL database and update `src/main/resources/application.properties`
(datasource URL/username/password) to match your local MySQL instance before running.
Tables are auto-managed via `spring.jpa.hibernate.ddl-auto=update`; DDL references live
in `src/main/resources/schema/`.

### Frontend (local)

```powershell
cd client
npm install
npm run dev   # starts the Vite dev server
```

### Full stack via Docker Compose

```powershell
docker compose up --build
```

This starts MySQL, the backend (`http://localhost:8081`), and the frontend
(`http://localhost:8082`). See [docs/cd-deployment.md](docs/cd-deployment.md) for CI/CD details.

## Configuration

Key settings in `src/main/resources/application.properties`:

- `spring.datasource.*` — MySQL connection (URL/username/password).
- `spring.mail.*` — SMTP settings used to send OTP emails.
- `app.otp.expiry-minutes` / `app.otp.recipient-email` — OTP behavior.

When running via Docker Compose, the datasource is instead configured through the
`MYSQL_ROOT_PASSWORD` / `MYSQL_DATABASE` environment variables (see `docker-compose.yml`).

> **Note:** `application.properties` currently contains real-looking database and email
> credentials committed directly in the file. These should be moved to environment
> variables/secrets and rotated rather than kept in source control.

## Payment Lifecycle

Each payment moves through the following statuses (`PaymentStatus` enum), recorded
in `payment_history` on every transition:

```
CREATED → VALIDATED → SENT → COMPLETED
   \            \         \
    \            \         \--> FAILED
     \            \--> FAILED
      \--> FAILED
```

1. **CREATED** — `POST /api/payments` creates a new payment with a server-generated
   idempotency key.
2. **VALIDATED** — `POST /api/payments/{id}/validate` runs `PaymentValidator`
   business checks (amount, currency, account existence/format, distinct accounts,
   sufficient funds, duplicate idempotency key).
3. **OTP verification** — `POST /api/payments/{id}/send-otp` sends a one-time code
   to authorize the transfer; the code is verified in the next step.
4. **SENT → COMPLETED** — `POST /api/payments/{id}/process` (with the OTP code)
   verifies the OTP, then debits the source account and credits the destination
   account under a pessimistic lock (accounts locked in sorted order to avoid
   deadlocks), transitioning the payment through `SENT` to `COMPLETED`.
5. **FAILED** — Can occur from any stage (e.g. failed OTP verification or a
   business rule violation), which is recorded via `updatePaymentStatus` and does
   not roll back the OTP failure record.

Supporting endpoints let clients query payments (`GET /api/payments`, optionally
filtered by `status`), fetch a single payment, view its full status-change history
(`GET /api/payments/{id}/history`), and list accounts (`GET /api/accounts`).

## API Endpoints

### Payments (`/api/payments`)

| Method | Endpoint                        | Description                                                        |
| ------ | -------------------------------- | -------------------------------------------------------------------- |
| POST   | `/api/payments`                  | Create a new payment                   |
| GET    | `/api/payments`                  | List all payments; optional `?status=` query param to filter.        |
| GET    | `/api/payments/{id}`             | Get a single payment by ID.                                           |
| GET    | `/api/payments/{id}/history`     | Get the status-change history for a payment.                         |
| PATCH  | `/api/payments/{id}/status`      | Update a payment's status directly (`?status=` query param).          |
| POST   | `/api/payments/{id}/validate`    | Run business validation and transition `CREATED` → `VALIDATED`.      |
| POST   | `/api/payments/{id}/send-otp`    | Send an OTP code to authorize processing of the payment.             |
| POST   | `/api/payments/{id}/process`     | Verify OTP (request body `{ "otpCode": "..." }`) and transfer funds, transitioning `VALIDATED` → `SENT` → `COMPLETED`. |

### Accounts (`/api/accounts`)

| Method | Endpoint         | Description            |
| ------ | ---------------- | ----------------------- |
| GET    | `/api/accounts`  | List all accounts.      |

## Testing

```powershell
.\mvnw.cmd -q test
```

Unit/integration tests live under `src/test/java/.../controller` and `.../service`.
Note: `PaymentprocessingApplicationTests.contextLoads` requires a live local MySQL
`paymentdb` instance to pass.
