# Event Ledger Service

Two Spring Boot services implement the event-ledger take-home architecture:

- `event-gateway` (public API, port `8080`)
- `account-service` (internal account state API, port `8081`)

The gateway validates and stores events, then synchronously calls the account service to apply each transaction.

## Architecture Overview

Client -> `event-gateway` -> `account-service`

- Gateway responsibilities:
  - accepts `POST /events`
  - enforces idempotency on `eventId`
  - stores event records in its own H2 database
  - forwards transaction application to account service
- Account service responsibilities:
  - applies account transactions idempotently
  - computes balances as `sum(CREDIT) - sum(DEBIT)`
  - returns account and transaction views from its own H2 database

The services do not share in-process state or a database.

## Design Decisions

**Idempotency.** `eventId` is the primary key on both the gateway's `event_record` table and the account service's `applied_transaction` table. On `POST /events`, the gateway checks for an existing `eventId` first and short-circuits the replay (returns the original event, `200`, no downstream call). The account service has its own idempotency check as a backstop — even if the gateway is bypassed, a duplicate `eventId` cannot be applied twice. The account service also catches the database's unique-constraint violation (not just a read-then-write check), which closes the race where two identical requests arrive at the same time.

**Out-of-order tolerance.** Balance is `sum(CREDIT) - sum(DEBIT)`, a commutative aggregate, so the order transactions arrive in never affects the result. The only place order matters is the listing endpoints, which sort by `eventTimestamp` on read (`findByAccountIdOrderByEventTimestampAsc`) rather than relying on insertion order.

**Consistency under failure (apply-first-then-persist).** On `POST /events`, the gateway calls the account service to apply the transaction *before* persisting its own event record. If the downstream call fails, nothing is persisted on the gateway side — there is no event in the gateway's store without a corresponding applied transaction. This means a failed write leaves no orphaned record, and a client that retries a failed request is safe: the account service's own idempotency check absorbs the duplicate if the first attempt actually succeeded server-side but the response was lost.

**Resiliency pattern: Circuit Breaker + Retry (Resilience4j).** Retry handles short transient failures (`max-attempts: 2`, short backoff). The circuit breaker opens after repeated failures (`failure-rate-threshold: 50%` over a sliding window) and fails fast rather than continuing to hammer a downstream that's already unhealthy. Both paths funnel into the same fallback, which throws `AccountServiceUnavailableException` → mapped to a clean `503 Service Unavailable`. This was chosen over retry-alone because retry alone doesn't shed load from a dependency that's actually down, and a bulkhead protects the gateway's own resources but doesn't give the client a fast, clear failure.

**Graceful degradation.** `GET /events/{id}` and `GET /events?account=` only read from the gateway's own H2 database and never call the account service — so they keep working even when the account service is completely down. Only `POST /events` depends on the downstream call, and it fails clean (`503`) rather than hanging or 500ing.

## Prerequisites

- Java 17+
- Docker Desktop (optional, only for the Compose workflow)

## Run Locally (Manual)

Start each service in its own terminal, from the repository root.

### 1) Start Account Service

```powershell
cd account-service
.\mvnw spring-boot:run
```

### 2) Start Event Gateway

```powershell
cd event-gateway
.\mvnw spring-boot:run
```

### 3) Health checks

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8081/health
```

> **Note:** both services use an in-memory H2 database. Restarting either service wipes its data. Run a full test sequence in one session without restarting mid-stream.

## Run with Docker Compose

From the repository root:

```powershell
docker compose up --build
```

Stop services:

```powershell
docker compose down
```

## Postman Collection

- A ready-to-import collection is included at `CharlesSchwab.postman_collection.json`.
- Import it into Postman to run the gateway and account-service requests without manually creating each call.
- Default requests target local ports (`8080` for `event-gateway`, `8081` for `account-service`).

## API Quick Reference

### Gateway (`event-gateway`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/events` | Submit an event. Returns `201` (new), `200` (replay), or `429` (rate limited; max 5 req/s). |
| `GET` | `/events/{id}` | Fetch a single event by id. `404` if not found. |
| `GET` | `/events?account={accountId}` | List events for an account, sorted by `eventTimestamp`. |
| `GET` | `/accounts/{accountId}/balance` | Proxy to account service; fetch current balance. Gateway's graceful-degradation path. |
| `GET` | `/health` | Liveness/health check. |
| `GET` | `/metrics` | Actuator metrics endpoint. |
| `GET` | `/prometheus` | Prometheus scrape endpoint. |

### Account Service (`account-service`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | Apply a transaction. Idempotent on `eventId`. |
| `GET` | `/accounts/{accountId}/balance` | Current balance. |
| `GET` | `/accounts/{accountId}` | Account details + transactions. |
| `GET` | `/health` | Liveness/health check. |
| `GET` | `/metrics` | Actuator metrics endpoint. |
| `GET` | `/prometheus` | Prometheus scrape endpoint. |

### Example: submit an event

```powershell
curl.exe -s -X POST localhost:8080/events -H "Content-Type: application/json" -d '{"eventId":"evt-001","accountId":"acct-900","type":"CREDIT","amount":200.00,"currency":"USD","eventTimestamp":"2026-05-15T10:00:00Z"}'
```

### Example: graceful degradation

Stop the account service, then:

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" localhost:8080/events/evt-001        # 200 — reads still work
curl.exe -s -o NUL -w "%{http_code}`n" -X POST localhost:8080/events -H "Content-Type: application/json" -d '{"eventId":"evt-002","accountId":"acct-900","type":"CREDIT","amount":10,"currency":"USD","eventTimestamp":"2026-05-15T11:00:00Z"}'   # 503 — write fails clean
```

## Run Tests

### Account Service tests

```powershell
cd account-service
.\mvnw test
```

### Event Gateway tests

```powershell
cd event-gateway
.\mvnw test
```

Notable test coverage:
- Idempotency and out-of-order ordering (`AccountServiceCoreIntegrationTest`)
- Validation, replay, and not-found behavior (`AccountControllerWebMvcTest`, `EventControllerWebMvcTest`)
- Circuit breaker opening under repeated downstream failure, verified against a WireMock stub (`AccountServiceResiliencyIntegrationTest`)
- Trace ID propagation from gateway to account service (`TracePropagationIntegrationTest`)
- Gateway's balance proxy and graceful degradation when account service is down (`AccountBalanceProxyIntegrationTest`)
- Rate limiting on POST /events with 429 response (`EventSubmissionRateLimitIntegrationTest`)
- Service startup and initialization (`EventGatewayApplicationTests`, `AccountServiceApplicationTests`, `HealthEndpointIntegrationTest`)
- Event idempotency race conditions and concurrent duplicate handling (`EventServiceTest`)

## Notes on Observability

- Structured JSON logging is configured in both services (Logback + Logstash encoder).
- Distributed trace IDs are propagated from gateway to account service via an `X-Trace-Id` header, set from the current span's trace ID.
- Actuator management base path is `/`, so metrics endpoints are at `/metrics` and `/prometheus`.
- Custom gateway metrics:
  - `gateway.account.apply.success`
  - `gateway.account.apply.failure`
  - `gateway.account.apply.circuit_open`
  - `gateway.account.balance.success`
  - `gateway.account.balance.failure`
  - `gateway.account.balance.circuit_open`
- Tracing is exported over OTLP HTTP using `management.otlp.tracing.endpoint` (defaults to `http://localhost:4318/v1/traces`).
- In Docker Compose, traces are sent to Jaeger (`jaeger:4318`), and the Jaeger UI is available at `http://localhost:16686`.

## Resilience Notes

- `POST /events` is rate-limited with Resilience4j (`eventSubmission`): `limit-for-period=5`, `limit-refresh-period=1s`, `timeout-duration=0` (fail-fast).
- Exceeding the rate limit returns `429 Too Many Requests` with error code `RATE_LIMIT_EXCEEDED`.
- Downstream account-service retry uses exponential backoff + jitter (`max-attempts=2`, `wait-duration=200ms`, multiplier `2`, randomized wait factor `0.5`).
