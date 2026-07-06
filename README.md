# AI Support Platform

> A distributed AI-powered customer support platform built with Spring Boot microservices, Apache Kafka, and OpenAI

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-7.6-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/features/actions)

**Live API:** https://support-api-production-db67.up.railway.app
**Swagger UI:** https://support-api-production-db67.up.railway.app/swagger-ui/index.html

---

## What This Is

A production-grade backend platform that automates the first-touch triage of customer support tickets using AI. Customers submit tickets through a REST API, and within seconds an AI model has classified the issue by category, assigned a confidence score, and flagged it for escalation if needed — without any human involvement in that step.

The engineering challenge is not the AI itself. The AI call to OpenAI takes 3-5 seconds and can fail. A naive implementation would make the customer wait for it, block the HTTP thread, and potentially time out. This platform solves that with an event-driven architecture: the ticket is saved and the HTTP response returns in milliseconds, while AI classification happens asynchronously through Kafka in the background.

**Two independently deployable microservices:**

`support-api` — the customer-facing HTTP layer. Handles authentication, ticket and message management, role-based access control, and analytics. When a ticket is created it publishes a `ticket.created` event to Kafka and returns `201` immediately.

`ai-processor` — the background processing service. Consumes `ticket.created` events, calls OpenAI GPT-4o-mini to classify the ticket, persists a full audit trail with token counts and latency, then publishes a `ticket.processed` event back. `support-api` consumes that result and updates the ticket.

The two services share no direct connection — they only know each other through Kafka topics. If `ai-processor` goes down, tickets still get created. If OpenAI is slow, no HTTP thread is blocked. Failed classifications retry 3 times with 1-second backoff before routing to a dead letter topic.

**Built to demonstrate production backend engineering practices:**
- Event-driven microservices with Kafka and proper DLT error handling
- JWT authentication with Redis token blacklisting and refresh token rotation
- Distributed tracing across both services via OpenTelemetry and Jaeger
- 74 automated tests — unit tests, Testcontainers integration tests, EmbeddedKafka consumer tests
- CI/CD pipeline from GitHub Actions to GHCR, with Railway auto-deploy on push to main
- Full observability stack: Prometheus metrics, Grafana dashboards, structured JSON logging

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
| Framework | Spring Boot 3.5.14 | Auto-configuration, production-ready |
| Security | Spring Security 6 + JWT | Stateless authentication |
| Messaging | Apache Kafka | Async event-driven communication |
| Database | PostgreSQL 16 | Primary data store with Liquibase migrations |
| Cache | Redis 7.2 | Token blacklisting, analytics caching |
| AI | OpenAI GPT-4o-mini | Ticket classification and escalation |
| Tracing | OpenTelemetry + Jaeger | Distributed trace correlation |
| Metrics | Micrometer + Prometheus | Custom business metrics |
| Dashboards | Grafana | Real-time operational monitoring |
| Testing | JUnit 5 + Testcontainers | 74 tests, 80% coverage enforced |
| CI/CD | GitHub Actions + Docker | Automated test, build, deploy pipeline |
| Deployment | Railway + Aiven | Public API, background worker, managed PostgreSQL, managed Kafka |

---

## Key Engineering Decisions

**Decision 1 — Async AI processing via Kafka**

OpenAI calls take 3-5 seconds and can fail. A synchronous implementation blocks the HTTP thread and forces the customer to wait. Publishing to Kafka decouples ticket creation from AI processing: `support-api` returns `201` in milliseconds while `ai-processor` classifies in the background.

**Result:** ticket creation P99 latency is under 50ms regardless of OpenAI response time.

**Decision 2 — Dead letter topic with 3-retry backoff**

Kafka consumer failures without a DLT silently lose messages. A naive infinite retry loop blocks the consumer partition. The 3-retry + 1-second backoff + DLT pattern gives transient failures a chance to recover while ensuring permanent failures are captured and inspectable rather than lost.

**Decision 3 — Redis token blacklisting over stateful sessions**

Stateful sessions require sticky routing or shared session storage, and both add operational complexity. JWT with Redis blacklisting gives stateless scalability while still supporting immediate token revocation on logout. SHA-256 hashing of tokens before storing them as Redis keys prevents raw token exposure in Redis logs.

**Decision 4 — Shared Kafka topics, no direct HTTP between services**

Direct HTTP calls between microservices create tight coupling. If `ai-processor` is down, ticket creation should not fail. Topic-based communication lets each service evolve independently, so `ai-processor` can be redeployed, scaled, or replaced without `support-api` knowing anything changed.

**Decision 5 — Full audit trail in AiResponseAudit**

AI classifications are probabilistic and can be wrong. Without an audit trail there is no way to debug a misclassification, measure model drift, or replay failed events. Every OpenAI response stores the raw prompt, completion, token counts, latency, and confidence score, making the AI behavior fully inspectable.

**Decision 6 — Distributed tracing across both services**

With two services communicating via Kafka, a single customer request spans multiple process boundaries. Without trace propagation, correlating a slow ticket classification to a specific OpenAI call is impossible. OTel trace IDs are injected into Kafka message headers so Jaeger shows the full request lifecycle, from HTTP ingestion through Kafka publish to AI processing, as a single trace.

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

# 2. Create your local environment file
cp .env.example .env
# Edit .env and fill in your values:
# - JWT_SECRET: any 32+ character string for local dev
# - OPENAI_API_KEY: your OpenAI API key
# - All other values can stay as the defaults in .env.example

# 3. Create the local database (first time only)
# Option A: if using Docker PostgreSQL
docker compose up -d postgres redis zookeeper kafka
docker exec -it support-postgres psql -U postgres -c "CREATE DATABASE support_db;"

# Option B: if using local PostgreSQL installation
psql -U postgres -c "CREATE DATABASE support_db;"

# 4. Start all infrastructure
docker compose up -d

# 5. Run support-api (in a new terminal)
cd support-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # Mac/Linux
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local  # Windows

# 6. Run ai-processor (in a new terminal)
cd ai-processor
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # Mac/Linux
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local  # Windows
```

### Windows Convenience Script

A PowerShell script template is provided to start both services automatically:

```powershell
# Copy the example script
cp run-local.example.ps1 run-local.ps1

# Open run-local.ps1 and fill in your values:
# - JWT_SECRET: any 32+ character string
# - OPENAI_API_KEY: your real OpenAI key

# Run it — opens both services in separate terminal windows
.\run-local.ps1
```

> `run-local.ps1` is gitignored — your secrets never leave your machine.

Once running, access the services at:

| Service | URL | Credentials |
|---------|-----|-------------|
| Swagger UI | http://localhost:8080/swagger-ui/index.html | — |
| Grafana | http://localhost:3000 | admin / admin |
| Jaeger | http://localhost:16686 | — |
| Prometheus | http://localhost:9090 | — |

> **Note:** Integration tests use Testcontainers and require Docker to be running.
> If you have a local PostgreSQL installation running on port 5432, it may conflict
> with the Docker PostgreSQL container. Either stop the local installation or create
> the support_db database in your local PostgreSQL.

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

---

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | None | Register new user |
| `POST` | `/api/v1/auth/login` | None | Login and get JWT |
| `POST` | `/api/v1/auth/refresh` | Bearer | Refresh access token |
| `POST` | `/api/v1/tickets` | Any | Create support ticket |
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

For production, runtime secrets are stored in Railway service variables and Aiven credentials. GitHub Actions uses repository secrets for GHCR publishing.

---

## Deployment

The platform is deployed on Railway:
- support-api: https://support-api-production-db67.up.railway.app
- ai-processor: deployed as a background service (no public URL)
- PostgreSQL: Railway managed database
- Kafka: Aiven managed Kafka with SASL_SSL

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
  │  Job 3      │  Railway auto-deploys from GHCR
  │   deploy    │  on push to main.
  └─────────────┘
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

**Traces** — 100% of requests are sampled and exported via OTLP to Jaeger. Trace IDs appear in every structured log line, enabling log-to-trace correlation. Access Jaeger at `http://localhost:16686`.

**Dashboards** — Grafana dashboards auto-provision from `./grafana/provisioning` on startup. Access Grafana at `http://localhost:3000`.

**Alerts** — Prometheus alerting rules are defined in `./prometheus/alerts.yml` and routed through Alertmanager on port `9093`.

> **Local only:** The full observability stack (Prometheus, Grafana, Jaeger) runs locally via Docker Compose. The live Railway deployment disables OTLP tracing — structured JSON logging is still available via Railway's log viewer.

---

## Screenshots

### Swagger UI — API Documentation
Interactive API documentation showing all endpoints across ticket, 
message, auth, and admin controllers with JWT bearer authentication.

![Swagger UI](docs/screenshots/swagger-ui.png)
![Swagger Schemas](docs/screenshots/swagger-schemas.png)

### Grafana Dashboard — Real-time Metrics
Live operational dashboard showing AI confidence score (0.950), 
HTTP request rate by endpoint, JVM heap memory for both services, 
active database connections, and zero 5xx errors.

![Grafana Dashboard](docs/screenshots/grafana-dashboard.png)

### Jaeger — Distributed Tracing
Full distributed tracing across both microservices via OpenTelemetry. 
Traces show request flow through Spring Security filter chain, 
authorization, and business logic with millisecond-level timing.

![Jaeger support-api Traces](docs/screenshots/jaeger-support-api-traces.png)
![Jaeger Trace Detail](docs/screenshots/jaeger-trace-detail.png)
![Jaeger ai-processor Traces](docs/screenshots/jaeger-ai-processor-traces.png)
