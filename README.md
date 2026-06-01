# AI Support Platform

> A distributed AI-powered customer support platform built with Spring Boot microservices, Apache Kafka, and OpenAI

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-7.6-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/features/actions)

---

## Architecture Overview

When a customer submits a support ticket, `support-api` persists it to PostgreSQL and publishes a `ticket.created` event to Kafka. `ai-processor` consumes that event, calls OpenAI GPT-4o-mini to classify the ticket (category, confidence score, escalation flag), and publishes a `ticket.processed` event back. `support-api` then consumes that result and updates the ticket status and AI metadata. Both services emit OpenTelemetry traces and Micrometer metrics, collected by a shared observability stack. All communication between microservices is asynchronous — no direct HTTP calls between services.

```
  Customer → REST API → support-api [:8080]
                              │
                  ┌───────────┴───────────┐
                  │                       │
             PostgreSQL               Kafka
              + Redis             (ticket.created)
                  │                       │
                  │              ai-processor [:8081]
                  │                       │
                  │                 OpenAI API
                  │                       │
                  └───────────┬───────────┘
                              │
                           Kafka
                     (ticket.processed)
                              │
                   Observability Stack
            (Prometheus + Grafana + Jaeger)
```

---

## Tech Stack

| Category | Technology | Purpose |
|----------|------------|---------|
| Language | Java 21 | Virtual threads, records, pattern matching |
| Framework | Spring Boot 3.5 | Auto-configuration, production-ready |
| Security | Spring Security 6 + JWT | Stateless authentication |
| Messaging | Apache Kafka | Async event-driven communication |
| Database | PostgreSQL 16 | Primary data store with Liquibase migrations |
| Cache | Redis 7.2 | Token blacklisting, analytics caching |
| AI | OpenAI GPT-4o-mini | Ticket classification and escalation |
| Tracing | OpenTelemetry + Jaeger | Distributed trace correlation |
| Metrics | Micrometer + Prometheus | Custom business metrics |
| Dashboards | Grafana | Real-time operational monitoring |
| Testing | JUnit 5 + Testcontainers | 82 tests, 80% coverage enforced |
| CI/CD | GitHub Actions + Docker | Automated test, build, deploy pipeline |
| Deployment | Oracle Cloud (ARM) | Always-free 4 OCPU 24GB production VM |

---

## Key Features

- **JWT Authentication** with refresh token rotation and Redis-backed token blacklisting — access tokens expire in 15 minutes, refresh tokens in 7 days
- **Event-driven AI processing** — tickets classified asynchronously via Kafka; `support-api` is never blocked waiting for OpenAI
- **OpenAI integration** with configurable retry logic (3 retries, 30s timeout, temperature 0.3), confidence scoring stored on every ticket, and a full audit trail via `AiResponseAudit`
- **Distributed tracing** — trace IDs propagated across both microservices via OTel with 100% sampling; correlate a ticket creation request all the way through AI processing in Jaeger
- **Structured JSON logging** — every log line includes `traceId`, `spanId`, `requestId`, `method`, and `path` fields for machine-readable log correlation
- **Custom Micrometer metrics** — `tickets.created.total`, `ticket.creation.duration`, `users.registered.total`, `users.login.total` (tagged by success/failure)
- **Role-based access control** — `CUSTOMER`, `AGENT`, and `ADMIN` roles enforced at the Spring Security level
- **Database migrations via Liquibase** — versioned schema management with a full changelog history; `ddl-auto: validate` in production
- **82 automated tests** — unit tests with Mockito, integration tests with real Postgres via Testcontainers, Kafka consumer tests with EmbeddedKafka
- **JaCoCo coverage enforcement** — `mvn verify` fails if service-layer line coverage drops below 80%
- **Dead letter queue (DLT) with retry logic** — Kafka consumer retries 3 times with 1-second backoff before sending failed events to the DLT, preventing message loss
- **Production Docker Compose** with health checks, `restart: unless-stopped`, and non-root container users

---

## Project Structure

```
ai-support-platform/
├── support-api/            # Customer-facing REST API (port 8080)
├── ai-processor/           # AI processing microservice (port 8081)
├── docker-compose.yml      # Local development infrastructure
├── docker-compose.prod.yml # Production deployment
├── prometheus/             # Prometheus configuration and alert rules
├── grafana/                # Grafana dashboards and provisioning
├── otel-collector/         # OpenTelemetry collector config
└── .github/workflows/      # CI/CD pipelines
```

---

## Getting Started

### Prerequisites

- Java 21
- Docker Desktop
- Maven 3.9+

### Local Development Setup

```bash
# 1. Clone the repository
git clone https://github.com/kishnahai0806/AI-Support-Platform.git
cd AI-Support-Platform

# 2. Start all infrastructure (Postgres, Redis, Kafka, Prometheus, Grafana, Jaeger)
docker compose up -d

# 3. Run support-api (in a new terminal)
cd support-api
./mvnw spring-boot:run        # Mac/Linux
mvnw.cmd spring-boot:run      # Windows

# 4. Run ai-processor (in a new terminal)
cd ai-processor
./mvnw spring-boot:run        # Mac/Linux
mvnw.cmd spring-boot:run      # Windows
```

Once running, access the services at:

| Service | URL | Credentials |
|---------|-----|-------------|
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| Grafana | http://localhost:3000 | admin / admin |
| Jaeger | http://localhost:16686 | — |
| Prometheus | http://localhost:9090 | — |

### Running Tests

```bash
# support-api — runs all tests and enforces 80% service-layer coverage
cd support-api
./mvnw verify        # Mac/Linux
mvnw.cmd verify      # Windows

# ai-processor
cd ai-processor
./mvnw verify        # Mac/Linux
mvnw.cmd verify      # Windows
```

> **Note:** Integration tests use Testcontainers and require Docker to be running.

---

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | None | Register new user |
| `POST` | `/api/v1/auth/login` | None | Login and get JWT |
| `POST` | `/api/v1/auth/refresh` | Bearer | Refresh access token |
| `POST` | `/api/v1/tickets` | CUSTOMER | Create support ticket |
| `GET` | `/api/v1/tickets` | Any | List tickets (role-filtered) |
| `GET` | `/api/v1/tickets/{id}` | Any | Get ticket details |
| `PATCH` | `/api/v1/tickets/{id}/status` | AGENT/ADMIN | Update ticket status |
| `PATCH` | `/api/v1/tickets/{id}/assign` | AGENT/ADMIN | Assign to agent |
| `POST` | `/api/v1/tickets/{id}/messages` | Any | Send message |
| `GET` | `/api/v1/admin/analytics/overview` | ADMIN | Get analytics |
| `GET` | `/api/v1/admin/users` | ADMIN | List all users |

Full interactive documentation is available at `/swagger-ui.html` when the service is running.

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/support_db` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `your-password` |
| `JWT_SECRET` | JWT signing secret (minimum 32 characters) | `your-32-char-secret-key` |
| `JWT_ACCESS_EXPIRY_MS` | Access token lifetime in milliseconds | `900000` (15 min) |
| `JWT_REFRESH_EXPIRY_MS` | Refresh token lifetime in milliseconds | `604800000` (7 days) |
| `OPENAI_API_KEY` | OpenAI API key | `sk-...` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | `localhost:9092` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry collector endpoint | `http://localhost:4317` |

For production, all secrets are stored as GitHub Actions repository secrets and injected at deploy time.

---

## CI/CD Pipeline

Each microservice has an independent GitHub Actions workflow triggered by changes to its own source directory or workflow file. Workflows can also be triggered manually via `workflow_dispatch`.

```
push / PR to main
        │
        ▼
  ┌─────────────┐
  │  Job 1      │  Runs all tests against real Postgres 16 and
  │    test     │  Redis 7.2 (GitHub Actions services containers).
  └──────┬──────┘  EmbeddedKafka used for Kafka consumer tests.
         │ needs
         ▼
  ┌─────────────┐
  │  Job 2      │  Builds multi-stage Docker image (JDK build
  │ build-push  │  → JRE runtime, non-root user). Pushes to
  └──────┬──────┘  GitHub Container Registry (GHCR).
         │ needs   Only runs on push to main (not PRs).
         ▼
  ┌─────────────┐
  │  Job 3      │  SSH into Oracle Cloud VM. Pulls new image,
  │   deploy    │  restarts container via docker compose.
  └─────────────┘  Requires ORACLE_HOST and ORACLE_SSH_KEY secrets.
```

Docker images are published to:
- `ghcr.io/<owner>/support-api:latest`
- `ghcr.io/<owner>/ai-processor:latest`

---

## Observability

Both services expose metrics, traces, and structured logs out of the box.

**Metrics** — available at `/actuator/prometheus` on each service:

| Metric | Description |
|--------|-------------|
| `tickets.created.total` | Counter tagged by `priority` and `category` |
| `ticket.creation.duration` | Timer for the full ticket creation flow |
| `users.registered.total` | Counter for new user registrations |
| `users.login.total` | Counter tagged by `success=true/false` |

**Traces** — 100% of requests are sampled and exportedYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polishYou are a senior Java backend engineer fixing critical security 
issues found in a security audit of support-api.

Read these files:
- support-api/src/main/java/com/krish/supportapi/exception/GlobalExceptionHandler.java
- support-api/src/main/java/com/krish/supportapi/controller/TicketController.java
- support-api/src/main/java/com/krish/supportapi/config/SecurityConfig.java
- support-api/src/main/java/com/krish/supportapi/domain/dto/request/CreateTicketRequest.java
- ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java
- docker-compose.prod.yml

Apply ALL fixes:

FIX 1 — CRITICAL: Fix RuntimeException handler
File: GlobalExceptionHandler.java

Find the @ExceptionHandler(RuntimeException.class) handler.
Change it from returning exception.getMessage() to:
- Log the exception at ERROR level
- Return HTTP 500 INTERNAL_SERVER_ERROR
- Return generic message: "An unexpected error occurred"

The handler should look like:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ApiError> handleRuntimeException(
    RuntimeException exception) {
  log.error("Unhandled runtime exception", exception);
  return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
    "Internal Server Error", "An unexpected error occurred");
}

Add @Slf4j to the class if not already present.

FIX 2 — IMPORTANT: Whitelist sortBy parameter
File: TicketController.java

Add this constant at the top of the class:
private static final Set<String> ALLOWED_SORT_FIELDS =
  Set.of("createdAt", "updatedAt", "status", 
         "priority", "ticketNumber");

In the getTickets() method, before building Pageable,
add this validation:
if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
  sortBy = "createdAt";
}

Import: java.util.Set

FIX 3 — IMPORTANT: Add pagination size cap
File: TicketController.java and AdminController.java

In getTickets() in TicketController add before building Pageable:
size = Math.min(size, 100);

In getUsers() in AdminController add before building Pageable:
size = Math.min(size, 100);

FIX 4 — IMPORTANT: Fix account enumeration
File: GlobalExceptionHandler.java

Find the @ExceptionHandler for EmailAlreadyExistsException.
Change the response to return HTTP 400 BAD_REQUEST with 
generic message: "Registration failed"
instead of exposing the email address.

FIX 5 — IMPORTANT: Add description length validation
File: CreateTicketRequest.java

Add @Size(max = 10000) to the description field.
Import: jakarta.validation.constraints.Size

FIX 6 — IMPORTANT: Sanitize OpenAI prompt input
File: ai-processor/src/main/java/com/krish/aiprocessor/service/OpenAiClientService.java

Add this private method:
private String sanitizeForPrompt(String input) {
  if (input == null) return "";
  return input.replaceAll("[<>{}\\[\\]\\\\]", "")
    .substring(0, Math.min(input.length(), 2000));
}

In buildUserPrompt() wrap title and description:
sanitizeForPrompt(title)
sanitizeForPrompt(description)

FIX 7 — IMPORTANT: Fix Grafana password in prod compose
File: docker-compose.prod.yml

Change:
GF_SECURITY_ADMIN_PASSWORD: admin
To:
GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}

The :-admin fallback keeps local dev working while 
allowing override in production .env file.

FIX 8 — IMPORTANT: Restrict CORS methods
File: SecurityConfig.java

Change:
configuration.setAllowedMethods(List.of("*"))
To:
configuration.setAllowedMethods(
  List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"))

Add:
configuration.setMaxAge(3600L)

HARD RULES:
- Do not break existing functionality
- Do not change any business logic
- All imports must be correct
- Keep @Slf4j import if adding logging

OUTPUT:
1. Apply all fixes directly in workspace
2. Run: cd support-api && .\mvnw.cmd -q -DskipTests compile
3. Run: cd ..\ai-processor && .\mvnw.cmd -q -DskipTests compile
4. Provide summary table: file | fix | status
5. Show compile results
6. Commit and push:
   git add .
   git commit -m "fix: apply security audit fixes - exception handler, input validation, CORS, prompt injection"
   git push origin feature/polish via OTLP to Jaeger. Trace IDs appear in every structured log line, enabling log-to-trace correlation. Access Jaeger at `http://localhost:16686`.

**Dashboards** — Grafana dashboards auto-provision from `./grafana/provisioning` on startup. Access Grafana at `http://localhost:3000`.

**Alerts** — Prometheus alerting rules are defined in `./prometheus/alerts.yml` and routed through Alertmanager on port `9093`.

---

## Screenshots

> Screenshots will be added after live deployment.

| Feature | Screenshot |
|---------|-----------|
| Swagger UI | Coming soon |
| Grafana Dashboard | Coming soon |
| Jaeger Distributed Traces | Coming soon |
