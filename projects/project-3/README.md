# Microservices Ticketing Platform

> Senior-level backend engineering project focused on microservices, scalability, resilience, observability, cloud deployment, and operational trade-offs.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Why This Project Exists](#why-this-project-exists)
3. [Learning Goals](#learning-goals)
4. [Business Domain](#business-domain)
5. [Core User Flows](#core-user-flows)
6. [High-Level Architecture](#high-level-architecture)
7. [Service Boundaries](#service-boundaries)
8. [System Context](#system-context)
9. [Local Infrastructure](#local-infrastructure)
10. [Technology Stack](#technology-stack)
11. [Repository Structure](#repository-structure)
12. [Microservices](#microservices)
13. [Data Ownership](#data-ownership)
14. [Synchronous vs Asynchronous Communication](#synchronous-vs-asynchronous-communication)
15. [Resilience Patterns](#resilience-patterns)
16. [Caching Strategy](#caching-strategy)
17. [Rate Limiting Strategy](#rate-limiting-strategy)
18. [Observability](#observability)
19. [Metrics](#metrics)
20. [Logging](#logging)
21. [Distributed Tracing](#distributed-tracing)
22. [Dashboards](#dashboards)
23. [Alerts](#alerts)
24. [Runbooks](#runbooks)
25. [Load Testing](#load-testing)
26. [Failure Scenarios](#failure-scenarios)
27. [Cloud Deployment](#cloud-deployment)
28. [Cost Analysis](#cost-analysis)
29. [Security Considerations](#security-considerations)
30. [Development Phases](#development-phases)
31. [Definition of Done](#definition-of-done)
32. [Architecture Decision Records](#architecture-decision-records)
33. [Interview / Review Questions](#interview--review-questions)
34. [Useful Commands](#useful-commands)
35. [License](#license)

---

## Project Overview

**Microservices Ticketing Platform** is a backend engineering project designed to simulate a realistic, high-traffic ticket reservation and ordering system.

The system allows users to browse events, check ticket availability, create temporary reservations, pay for orders, and receive notifications. The implementation intentionally includes distributed dependencies, cacheable reads, critical writes, payment failures, broker lag, database bottlenecks, and degraded modes.

The main purpose of this project is not to build another CRUD application. The purpose is to design, implement, observe, test, and defend a distributed backend system under realistic constraints.

The project should demonstrate the ability to:

- design service boundaries,
- reason about scaling and bottlenecks,
- use metrics, logs, and traces to debug production-like issues,
- apply resilience patterns intentionally,
- run load tests and interpret results,
- deploy a stateless workload to cloud infrastructure,
- discuss cost and operational trade-offs.

---

## Why This Project Exists

Microservices are often introduced too early and for the wrong reasons. Splitting a simple application into many services does not automatically create a senior-level system. In many cases it only creates distributed complexity without operational maturity.

This project exists to practice the hard parts of microservices:

- partial failure,
- latency propagation,
- dependency timeouts,
- retries that make outages worse,
- cache consistency trade-offs,
- request correlation across services,
- alerting based on symptoms,
- capacity planning,
- cost-aware cloud design.

A successful implementation should be judged by whether the architecture can be explained and defended using evidence from metrics, logs, traces, and load tests.

---

## Learning Goals

After completing this project, you should be able to:

### System Design

- Design a system on a whiteboard and defend major decisions.
- Explain the difference between vertical and horizontal scaling.
- Identify bottlenecks under increasing RPS.
- Reason about throughput, latency, saturation, and error rate.
- Apply caching, rate limiting, autoscaling, circuit breakers, retries, and graceful degradation.
- Estimate which part of the system fails first under load and why.

### Observability

- Use structured logs instead of unstructured text logs.
- Propagate correlation IDs and trace context across services.
- Expose application and infrastructure metrics.
- Build dashboards for latency, traffic, errors, saturation, cache, database, broker, and dependencies.
- Use distributed tracing to locate latency in downstream dependencies.
- Design alerts based on user-visible symptoms, not only technical causes.
- Write runbooks for common production incidents.

### Cloud

- Deploy stateless workloads to a cloud provider.
- Understand compute, storage, and networking in a practical deployment.
- Configure managed database, Redis, load balancer, and private networking.
- Explain VPC, public/private subnets, routing, and security groups.
- Compare autoscaling with overprovisioning.
- Identify major cost drivers.
- Propose a technically correct but too expensive solution, then optimize it.

---

## Business Domain

The chosen domain is **ticket reservation for events**.

Users can:

- browse available events,
- view event details,
- check ticket availability,
- reserve tickets for a limited time,
- pay for reservations,
- receive confirmation notifications.

Administrators can:

- create events,
- configure ticket pools,
- inspect orders and reservations,
- monitor operational dashboards.

This domain is useful because it naturally introduces hard backend problems:

- traffic spikes before popular events,
- highly cacheable catalog data,
- critical inventory consistency,
- overselling risk,
- payment dependency failures,
- asynchronous notifications,
- reservation expiration,
- idempotent order creation,
- high read-to-write ratio.

---

## Core User Flows

### 1. Browse Events

```text
Client
  -> API Gateway
  -> Event Catalog Service
  -> Redis Cache
  -> PostgreSQL
```

Expected behavior:

- Most reads should be served from cache.
- Cache misses should be protected against stampede.
- The endpoint should remain fast even under high traffic.

---

### 2. Check Availability

```text
Client
  -> API Gateway
  -> Event Catalog Service
  -> Reservation Service
  -> PostgreSQL
```

Expected behavior:

- Availability can be slightly stale if explicitly documented.
- Fresh availability is required before creating a reservation.
- The system should distinguish between display availability and transactional availability.

---

### 3. Create Reservation

```text
Client
  -> API Gateway
  -> Reservation Service
  -> PostgreSQL
```

Expected behavior:

- Reservation is temporary.
- Inventory must not be oversold.
- Concurrent requests must be handled safely.
- Reservation has an expiration time.

---

### 4. Create Order and Pay

```text
Client
  -> API Gateway
  -> Order Service
  -> Reservation Service
  -> Payment Mock Service
  -> Broker
  -> Notification Service
```

Expected behavior:

- Order creation should be idempotent.
- Payment failures should not corrupt order state.
- Payment timeouts should be handled with circuit breaker and retry policy.
- Notification should happen asynchronously.

---

### 5. Receive Notification

```text
Order Service
  -> Broker
  -> Notification Service
  -> Mock Email/SMS Provider
```

Expected behavior:

- Notification failure should not fail the order.
- Broker lag should be measurable.
- Consumers should be horizontally scalable.

---

## High-Level Architecture

```text
                         +------------------+
                         |      Client      |
                         +---------+--------+
                                   |
                                   v
                         +------------------+
                         |   API Gateway    |
                         | rate limiting    |
                         | correlation IDs  |
                         +----+--------+----+
                              |        |
          +-------------------+        +--------------------+
          |                                             |
          v                                             v
+---------------------+                      +----------------------+
| Event Catalog       |                      | Order Service         |
| cache-heavy reads   |                      | order workflow        |
+----------+----------+                      +----------+-----------+
           |                                            |
           v                                            v
+---------------------+                      +----------------------+
| Redis               |                      | Reservation Service   |
| cache + rate limit  |                      | inventory consistency |
+---------------------+                      +----------+-----------+
                                                        |
                                                        v
                                             +----------------------+
                                             | Payment Mock Service |
                                             | failures + latency   |
                                             +----------+-----------+
                                                        |
                                                        v
                                             +----------------------+
                                             | Message Broker       |
                                             | events + lag         |
                                             +----------+-----------+
                                                        |
                                                        v
                                             +----------------------+
                                             | Notification Service |
                                             | async delivery       |
                                             +----------------------+

                         +------------------+
                         | PostgreSQL       |
                         | service data     |
                         +------------------+
```

---

## Service Boundaries

The system is split into services based on business capability, not technical layers.

Good service boundaries:

- own their data,
- expose stable APIs,
- avoid sharing database tables,
- are independently deployable,
- can fail independently,
- have clear operational metrics.

Bad service boundaries:

- split by controller/service/repository layers,
- require distributed transactions for every operation,
- share the same internal tables,
- force synchronous calls for every simple read,
- make local development impossible.

---

## System Context

External actors and systems:

```text
Users
Administrators
Payment provider mock
Email/SMS provider mock
Cloud provider
Monitoring stack
```

Internal systems:

```text
API Gateway
Catalog Service
Reservation Service
Order Service
Payment Mock Service
Notification Service
PostgreSQL
Redis
Message Broker
Prometheus
Grafana
OpenTelemetry Collector
Jaeger / Tempo
Loki / ELK
```

---

## Local Infrastructure

The local environment should be runnable with Docker Compose.

Expected components:

```text
docker-compose.yml
├── api-gateway
├── catalog-service
├── reservation-service
├── order-service
├── payment-mock-service
├── notification-service
├── postgres
├── redis
├── kafka / rabbitmq
├── prometheus
├── grafana
├── loki / elasticsearch
├── tempo / jaeger
└── otel-collector
```

The local environment should support:

- service-to-service communication,
- structured logs,
- metrics scraping,
- distributed tracing,
- broker events,
- Redis cache,
- database persistence,
- controlled failure scenarios.

---

## Technology Stack

Recommended backend stack:

```text
Language: Java 21+
Framework: Spring Boot
Build: Gradle or Maven
Database: PostgreSQL
Cache: Redis
Broker: Kafka, RabbitMQ, or SQS-compatible local alternative
Resilience: Resilience4j
Metrics: Micrometer + Prometheus
Dashboards: Grafana
Tracing: OpenTelemetry + Jaeger/Tempo
Logs: JSON logs + Loki/ELK
Load testing: k6
Containers: Docker + Docker Compose
Cloud: AWS
Deployment target: ECS Fargate
Infrastructure as Code: Terraform or AWS CDK
```

The exact stack can be changed, but the architectural requirements should remain the same.

---

## Repository Structure

Recommended structure:

```text
microservices-ticketing-platform/
├── README.md
├── docker-compose.yml
├── docker-compose.observability.yml
├── .env.example
│
├── api-gateway/
├── catalog-service/
├── reservation-service/
├── order-service/
├── payment-mock-service/
├── notification-service/
│
├── libs/
│   ├── common-observability/
│   ├── common-security/
│   └── common-contracts/
│
├── infra/
│   ├── local/
│   ├── aws/
│   └── terraform/
│
├── dashboards/
│   ├── system-overview.json
│   ├── latency.json
│   ├── errors.json
│   ├── saturation.json
│   ├── redis.json
│   ├── database.json
│   ├── broker.json
│   └── payment-dependency.json
│
├── load-tests/
│   ├── browse-events.js
│   ├── create-reservation.js
│   ├── create-order.js
│   ├── traffic-spike.js
│   ├── payment-failure.js
│   ├── redis-down.md
│   ├── db-slow.md
│   └── broker-lag.md
│
├── docs/
│   ├── architecture.md
│   ├── system-design.md
│   ├── scaling-analysis.md
│   ├── resilience-patterns.md
│   ├── observability.md
│   ├── alerts.md
│   ├── runbooks.md
│   ├── cloud-deployment.md
│   ├── cost-analysis.md
│   ├── trade-offs.md
│   └── adr/
│
└── scripts/
    ├── start-local.sh
    ├── stop-local.sh
    ├── seed-data.sh
    ├── run-load-tests.sh
    └── simulate-failure.sh
```

---

## Microservices

## 1. API Gateway

### Responsibility

The API Gateway is the public entry point to the system.

It is responsible for:

- request routing,
- rate limiting,
- request ID generation,
- correlation ID propagation,
- authentication placeholder,
- basic edge-level request logging,
- rejecting excessive traffic before it reaches downstream services.

### Example Endpoints

```http
GET /api/events
GET /api/events/{eventId}
GET /api/events/{eventId}/availability
POST /api/reservations
GET /api/reservations/{reservationId}
POST /api/orders
GET /api/orders/{orderId}
```

### Required Features

- Per-IP rate limiting.
- Per-API-key rate limiting.
- Token bucket implementation.
- Sliding window implementation.
- `X-Request-ID` support.
- `X-Correlation-ID` support.
- Trace context propagation.
- Metrics for rejected requests.

### Key Metrics

```text
http_server_requests_seconds
api_gateway_rate_limit_rejected_total
api_gateway_downstream_latency_seconds
api_gateway_downstream_errors_total
```

---

## 2. Event Catalog Service

### Responsibility

The Event Catalog Service owns event data and serves read-heavy endpoints.

It is responsible for:

- event listing,
- event details,
- public availability view,
- cache-heavy read path,
- cache invalidation after event changes.

### Example Endpoints

```http
GET /events
GET /events/{eventId}
GET /events/{eventId}/availability
POST /admin/events
PUT /admin/events/{eventId}
```

### Data Owned

```text
events
event_categories
event_metadata
ticket_pools_public_view
```

### Required Features

- Redis cache.
- TTL.
- TTL jitter.
- Cache hit/miss metrics.
- Stampede protection.
- Single-flight for cache rebuild.
- Clear distinction between cached display availability and transactional availability.

### Key Metrics

```text
catalog_cache_hit_total
catalog_cache_miss_total
catalog_cache_rebuild_duration_seconds
catalog_availability_latency_seconds
catalog_db_query_duration_seconds
```

### Important Trade-off

Catalog data can often be stale for a short time. Transactional inventory cannot.

This means:

- event details can be cached aggressively,
- public availability can be cached briefly,
- reservation creation must validate fresh availability.

---

## 3. Reservation Service

### Responsibility

The Reservation Service owns temporary reservations and protects inventory from overselling.

It is responsible for:

- creating temporary reservations,
- expiring reservations,
- confirming reservations after payment,
- releasing inventory after failure or timeout,
- handling concurrent reservation attempts.

### Example Endpoints

```http
POST /reservations
GET /reservations/{reservationId}
DELETE /reservations/{reservationId}
POST /internal/reservations/{reservationId}/confirm
POST /internal/reservations/{reservationId}/release
```

### Data Owned

```text
reservations
reservation_items
inventory_locks
ticket_inventory
```

### Required Features

- Transactional reservation creation.
- No overselling under concurrent load.
- Reservation expiration.
- Idempotent reservation operations where needed.
- Metrics for inventory contention.
- Safe behavior under DB saturation.

### Key Metrics

```text
reservation_created_total
reservation_failed_total
reservation_expired_total
reservation_db_lock_wait_seconds
reservation_inventory_conflict_total
reservation_db_connection_pool_usage
```

### Design Notes

This service is likely to become one of the first bottlenecks under write-heavy load.

Questions to answer:

- Is inventory locked pessimistically or optimistically?
- What happens when 1,000 users try to reserve the last 10 tickets?
- What is the maximum acceptable lock wait time?
- How does the system recover from partially completed reservation workflows?

---

## 4. Order Service

### Responsibility

The Order Service coordinates the order workflow.

It is responsible for:

- creating orders,
- validating reservations,
- calling payment,
- updating order status,
- publishing order events,
- handling idempotent order creation.

### Example Endpoints

```http
POST /orders
GET /orders/{orderId}
GET /orders?userId={userId}
```

### Data Owned

```text
orders
order_items
payment_attempts
idempotency_keys
```

### Required Features

- Idempotency key for `POST /orders`.
- Timeout when calling Payment Service.
- Retry with exponential backoff.
- Circuit breaker for Payment Service.
- Fallback state such as `PAYMENT_PENDING`.
- Event publishing after state changes.

### Order States

```text
PENDING
PAYMENT_PENDING
PAID
FAILED
EXPIRED
CANCELLED
```

### Key Metrics

```text
order_created_total
order_paid_total
order_failed_total
order_payment_latency_seconds
order_payment_retry_total
order_payment_circuit_breaker_state
order_idempotency_replay_total
```

### Important Trade-off

The Order Service should not block forever waiting for payment. A slow payment provider must not exhaust request threads or connection pools.

---

## 5. Payment Mock Service

### Responsibility

The Payment Mock Service simulates an unreliable external payment provider.

It is intentionally not stable. It exists to test resilience and observability.

### Example Endpoints

```http
POST /payments
GET /payments/{paymentId}
POST /admin/failure-mode
```

### Failure Modes

```text
normal
fixed-delay
random-delay
random-5xx
random-timeout
partial-outage
full-outage
```

### Required Features

- Configurable latency.
- Configurable error rate.
- Configurable timeout rate.
- Metrics for payment failures.
- Ability to simulate external dependency degradation.

### Key Metrics

```text
payment_requests_total
payment_errors_total
payment_latency_seconds
payment_timeout_total
payment_failure_mode
```

---

## 6. Notification Service

### Responsibility

The Notification Service consumes order events and sends mock notifications.

It is responsible for:

- consuming events from broker,
- sending mock email/SMS,
- retrying notification delivery,
- measuring consumer lag,
- exposing processing metrics.

### Example Events

```json
{
  "eventType": "OrderPaid",
  "orderId": "ord_123",
  "userId": "usr_456",
  "occurredAt": "2026-01-01T10:00:00Z"
}
```

### Required Features

- Consumer group.
- Retry policy.
- Dead-letter queue or dead-letter topic.
- Broker lag metrics.
- Idempotent event handling.

### Key Metrics

```text
notification_events_consumed_total
notification_events_failed_total
notification_processing_duration_seconds
notification_broker_lag
notification_dlq_messages_total
```

---

## Data Ownership

Each service should own its data.

Recommended model:

```text
catalog-service owns event catalog data
reservation-service owns reservation and inventory data
order-service owns order and payment attempt data
notification-service owns notification delivery data
```

Avoid:

- multiple services writing to the same tables,
- direct joins across service databases,
- using a shared database as an integration layer,
- leaking internal table structure through APIs.

Acceptable local simplification:

- one PostgreSQL instance,
- separate schemas per service.

Better production-like variant:

- separate databases per service,
- independent migrations,
- no cross-service SQL queries.

---

## Synchronous vs Asynchronous Communication

### Synchronous HTTP

Use HTTP when the caller needs an immediate answer.

Examples:

```text
API Gateway -> Catalog Service
API Gateway -> Reservation Service
Order Service -> Reservation Service
Order Service -> Payment Service
```

Requirements:

- explicit timeouts,
- bounded retries,
- circuit breakers for unstable dependencies,
- metrics per downstream call,
- trace propagation.

### Asynchronous Events

Use events when the action can happen after the main user request.

Examples:

```text
Order Service -> OrderPaid event -> Notification Service
Order Service -> OrderFailed event -> Notification Service
Reservation Service -> ReservationExpired event
```

Requirements:

- idempotent consumers,
- retry policy,
- DLQ or failed event handling,
- broker lag metrics,
- event versioning.

---

## Resilience Patterns

## Timeouts

Every outbound call must have a timeout.

Example policy:

```text
Catalog -> Redis: 100 ms
Catalog -> DB: 500 ms
Order -> Reservation: 1 s
Order -> Payment: 2 s
Notification -> Provider: 2 s
```

No service should wait indefinitely for a downstream dependency.

---

## Retries

Retries must be bounded.

Recommended policy:

```text
max attempts: 3
backoff: exponential
jitter: enabled
retry only: timeout, 502, 503, 504
never retry: 400, 401, 403, 404, validation errors
```

Bad retry behavior can amplify an outage.

A retry policy must answer:

- Which errors are retryable?
- How many attempts are allowed?
- What is the maximum retry duration?
- Is the operation idempotent?
- What happens when all retries fail?

---

## Circuit Breaker

Use circuit breakers for unstable downstream dependencies, especially Payment Service.

States:

```text
CLOSED
OPEN
HALF_OPEN
```

Expected behavior:

- When Payment Service fails repeatedly, the circuit opens.
- New calls fail fast.
- After a wait duration, limited trial calls are allowed.
- If trial calls succeed, the circuit closes.

Metrics:

```text
resilience4j_circuitbreaker_state
resilience4j_circuitbreaker_calls
resilience4j_circuitbreaker_failure_rate
```

---

## Graceful Degradation

The system should degrade intentionally.

Examples:

```text
Redis down:
  - serve Catalog from DB with stricter rate limit
  - disable cache-dependent optimizations
  - protect DB from overload

Payment Service slow:
  - mark order as PAYMENT_PENDING
  - avoid blocking request threads
  - process payment status later

Notification Service down:
  - order still succeeds
  - notification event remains in broker
  - alert on consumer lag
```

---

## Bulkheads

Use separate resource pools for different dependency types where possible.

Examples:

```text
Payment HTTP client pool
Reservation HTTP client pool
Catalog DB pool
Order DB pool
Notification worker pool
```

The failure of one dependency should not consume all resources of the service.

---

## Caching Strategy

Caching should be implemented in Event Catalog Service.

### Cacheable Data

```text
GET /events
GET /events/{eventId}
GET /events/{eventId}/availability
```

### Cache Rules

| Data | TTL | Notes |
|---|---:|---|
| Event list | 30-120 s | Highly cacheable |
| Event details | 60-300 s | Cache aggressively |
| Public availability | 2-10 s | Short TTL only |
| Reservation validation | 0 s | Must not rely on stale cache |

### Required Techniques

- TTL.
- TTL jitter.
- Cache hit/miss metrics.
- Cache rebuild timing.
- Single-flight cache rebuild.
- Protection against cache stampede.
- Sensible fallback when Redis is unavailable.

### Cache Stampede Example

Bad behavior:

```text
Popular event cache expires
10,000 users request the same event
All requests hit DB
DB saturates
System latency spikes
```

Expected behavior:

```text
Popular event cache expires
One request rebuilds cache
Other requests wait briefly or get stale value
DB remains stable
```

---

## Rate Limiting Strategy

Rate limiting should happen at the edge, before expensive downstream work.

### Required Limiters

```text
per IP
per API key
token bucket
sliding window
```

### Example Limits

| Client Type | Limit |
|---|---:|
| Anonymous user | 60 requests / minute |
| Logged-in user | 300 requests / minute |
| Partner API key | 3,000 requests / minute |
| Suspicious IP | 10 requests / minute |

### Metrics

```text
rate_limit_allowed_total
rate_limit_rejected_total
rate_limit_remaining_tokens
rate_limit_rejected_by_client_type_total
```

### Expected Behavior

During traffic spikes, the gateway should reject excessive traffic before downstream services become saturated.

---

## Observability

Observability is a core requirement, not an optional addition.

The system must answer these questions:

- Is the system healthy?
- Which endpoint is slow?
- Which service is failing?
- Is the issue caused by latency, errors, saturation, or dependency failure?
- Which user request caused this trace?
- Which downstream dependency introduced latency?
- Did a deployment make things worse?
- Are retries helping or amplifying the problem?

---

## Metrics

Expose metrics from every service using Micrometer and Prometheus.

### Golden Signals

Each service should expose:

```text
traffic
latency
errors
saturation
```

### HTTP Metrics

```text
http_server_requests_seconds_count
http_server_requests_seconds_sum
http_server_requests_seconds_max
http_client_requests_seconds_count
http_client_requests_seconds_sum
http_client_requests_seconds_max
```

### Application Metrics

```text
orders_created_total
orders_paid_total
orders_failed_total
reservations_created_total
reservations_expired_total
cache_hit_total
cache_miss_total
rate_limit_rejected_total
payment_attempts_total
payment_failures_total
```

### Saturation Metrics

```text
jvm_memory_used_bytes
system_cpu_usage
process_cpu_usage
hikaricp_connections_active
hikaricp_connections_pending
redis_command_latency_seconds
broker_consumer_lag
executor_queue_size
```

### Example PromQL Queries

#### Request Rate

```promql
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

#### p95 Latency

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service, uri)
)
```

#### Error Rate

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)
/
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

#### DB Pool Saturation

```promql
hikaricp_connections_active / hikaricp_connections_max
```

#### Cache Hit Ratio

```promql
sum(rate(cache_hit_total[5m]))
/
(
  sum(rate(cache_hit_total[5m])) + sum(rate(cache_miss_total[5m]))
)
```

---

## Logging

Logs must be structured JSON.

### Required Fields

```json
{
  "timestamp": "2026-01-01T10:00:00.000Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "abc123",
  "spanId": "def456",
  "requestId": "req_123",
  "correlationId": "corr_456",
  "userId": "usr_789",
  "orderId": "ord_123",
  "reservationId": "res_456",
  "message": "Order payment completed"
}
```

### Logging Rules

Do:

- log business identifiers,
- log error context,
- log downstream dependency names,
- log retry attempts,
- log circuit breaker transitions,
- log degraded mode activation.

Do not:

- log passwords,
- log tokens,
- log full payment data,
- log personal data unnecessarily,
- log huge request bodies,
- use logs as the only observability mechanism.

---

## Distributed Tracing

Tracing should be implemented with OpenTelemetry.

### Required Trace Flow

```text
API Gateway
  -> Order Service
    -> Reservation Service
      -> PostgreSQL
    -> Payment Mock Service
    -> Broker
      -> Notification Service
```

### Required Span Attributes

```text
service.name
http.method
http.route
http.status_code
db.system
db.operation
messaging.system
messaging.destination
reservation.id
order.id
payment.id
```

### Trace Investigation Example

Problem:

```text
A user reports that POST /orders took 8 seconds.
```

Investigation:

1. Find the request by `correlationId` or `traceId`.
2. Open the distributed trace.
3. Check which span consumed most latency.
4. Confirm with downstream service metrics.
5. Check logs for retries, timeout, or circuit breaker transition.
6. Decide whether this is application latency, dependency latency, or saturation.

---

## Dashboards

Minimum dashboards:

### 1. System Overview

Panels:

```text
RPS by service
p95 latency by service
error rate by service
CPU usage
memory usage
active DB connections
Redis availability
broker lag
open circuit breakers
```

### 2. Service Latency

Panels:

```text
p50 latency
p95 latency
p99 latency
slowest endpoints
latency by downstream dependency
```

### 3. Error Rate

Panels:

```text
5xx by service
4xx by endpoint
exceptions by type
payment failures
reservation conflicts
```

### 4. Saturation

Panels:

```text
CPU
memory
JVM heap
DB connection pool
thread pools
executor queues
broker consumer lag
```

### 5. Redis Cache

Panels:

```text
cache hit ratio
cache misses
Redis latency
Redis errors
cache rebuild duration
hot keys
```

### 6. Database

Panels:

```text
active connections
pending connections
query duration
transaction duration
lock wait time
slow queries
```

### 7. Payment Dependency

Panels:

```text
payment latency
payment error rate
payment timeout rate
retry count
circuit breaker state
fallback count
```

---

## Alerts

Alerts should be based on symptoms that affect users or system stability.

### Required Alerts

| Alert | Condition | Severity |
|---|---|---|
| High Order Latency | p95 latency above SLO | warning/critical |
| High Error Rate | 5xx rate above threshold | critical |
| DB Pool Saturation | active connections near max | critical |
| Payment Circuit Open | circuit breaker open | warning |
| Redis Unavailable | Redis errors or no connection | warning/critical |
| Broker Lag Increasing | lag grows continuously | warning/critical |
| Cache Hit Ratio Dropped | hit ratio below expected | warning |
| Rate Limit Spike | rejected requests spike | info/warning |

### Alerting Principles

Good alerts:

- indicate user impact,
- have a clear owner,
- link to a runbook,
- avoid excessive noise,
- are actionable.

Bad alerts:

- fire on every CPU spike,
- have no runbook,
- duplicate another alert,
- require guessing,
- wake people up for non-user-impacting events.

---

## Runbooks

Runbooks should be stored in `docs/runbooks.md` or `runbooks/`.

## Runbook: Database Down

### Symptoms

```text
High 5xx rate
Order and reservation failures
DB connection errors in logs
DB pool active connections near max
```

### Confirm

- Check database availability.
- Check DB connection pool metrics.
- Check service logs for SQL exceptions.
- Check recent deployments or migrations.

### Immediate Actions

- Stop non-critical traffic if possible.
- Increase rate limiting for write endpoints.
- Disable expensive background jobs.
- Check whether read-only endpoints can be served from cache.

### Do Not

- Increase retries aggressively.
- Restart all services blindly.
- Run heavy analytical queries on production DB.

### Recovery

- Restore DB connectivity.
- Verify migrations.
- Check connection pool recovery.
- Confirm error rate and latency return to normal.

---

## Runbook: Redis Down

### Symptoms

```text
Cache miss spike
Catalog latency increases
Rate limiting may fail or degrade
Redis connection errors
DB load increases
```

### Confirm

- Check Redis health.
- Check cache hit ratio.
- Check DB query rate.
- Check Gateway rate limiting behavior.

### Immediate Actions

- Enable fallback to DB for catalog reads.
- Tighten gateway rate limits to protect DB.
- Serve stale cache if available.
- Disable non-critical cache rebuilds.

### Recovery

- Restore Redis.
- Warm key caches gradually.
- Watch DB load during cache recovery.
- Confirm cache hit ratio improves.

---

## Runbook: Broker Lag

### Symptoms

```text
Notifications delayed
Consumer lag increasing
Message queue depth increasing
Notification processing latency high
```

### Confirm

- Check consumer lag.
- Check Notification Service health.
- Check processing duration.
- Check DLQ count.

### Immediate Actions

- Scale Notification Service consumers.
- Pause non-critical event producers if necessary.
- Inspect poison messages.
- Check downstream notification provider mock.

### Recovery

- Confirm lag is decreasing.
- Reprocess DLQ if safe.
- Verify idempotency before replaying events.

---

## Runbook: Error Rate Spike

### Symptoms

```text
5xx rate increases
User requests fail
Service-specific error panels show spike
```

### Confirm

- Identify affected service.
- Identify affected endpoint.
- Check recent deployments.
- Check downstream dependency metrics.
- Use traces to locate failing span.

### Immediate Actions

- Roll back if caused by deployment.
- Open circuit breaker or enable degraded mode.
- Reduce traffic with rate limiting.
- Disable non-critical features.

### Recovery

- Confirm error rate returns to baseline.
- Check latency and saturation.
- Create postmortem note with timeline and root cause.

---

## Load Testing

Load tests should be written with k6.

### Required Scenarios

```text
browse-events.js
create-reservation.js
create-order.js
traffic-spike.js
payment-failure.js
cache-comparison.js
redis-down.md
db-slow.md
broker-lag.md
```

### Load Levels

Run tests at increasing load:

```text
100 RPS
500 RPS
1000 RPS
3000 RPS
```

Adjust numbers based on local machine capacity, but keep the pattern: baseline, moderate load, high load, overload.

### Test Report Template

Each test should produce a short report:

```markdown
# Test: Create Order Under Payment Latency

## Assumption
Payment Service latency will dominate Order Service p95 latency.

## Setup
- RPS: 500
- Payment latency: random 100 ms - 5 s
- Payment error rate: 20%
- Order Service instances: 2

## Result
- p95 latency: ...
- p99 latency: ...
- error rate: ...
- retry count: ...
- circuit breaker state: ...

## Bottleneck
...

## Evidence
- Metric: ...
- Log query: ...
- Trace example: ...

## Change Applied
...

## Result After Change
...

## Trade-off
...
```

---

## Failure Scenarios

The project should include controlled failure scenarios.

### Payment Service Slow

Expected:

- Order Service latency increases initially.
- Retry count increases.
- Circuit breaker eventually opens.
- Some orders move to `PAYMENT_PENDING`.
- System avoids thread exhaustion.

### Payment Service Down

Expected:

- Circuit breaker opens.
- Calls fail fast.
- Error rate should be controlled.
- Logs show degraded behavior.
- Alert fires.

### Redis Down

Expected:

- Cache misses increase.
- Catalog latency increases.
- DB load increases.
- Gateway may switch to stricter rate limits.
- Alert fires.

### Database Slow

Expected:

- DB query duration increases.
- DB connection pool saturation increases.
- p95/p99 latency increases.
- Reservation and order failures may increase.
- Alert fires before total outage if thresholds are correct.

### Broker Lag

Expected:

- Order creation still works.
- Notifications are delayed.
- Consumer lag increases.
- Alert fires.
- Scaling consumers should reduce lag.

---

## Cloud Deployment

Recommended provider: AWS.

### Target AWS Architecture

```text
AWS Account
└── VPC
    ├── Public Subnets
    │   └── Application Load Balancer
    │
    ├── Private Subnets
    │   ├── ECS Fargate Services
    │   │   ├── api-gateway
    │   │   ├── catalog-service
    │   │   ├── reservation-service
    │   │   ├── order-service
    │   │   ├── payment-mock-service
    │   │   └── notification-service
    │   │
    │   ├── RDS PostgreSQL
    │   ├── ElastiCache Redis
    │   └── Amazon MQ / MSK / SQS
    │
    └── Security Groups
```

### Compute

Use ECS Fargate for stateless services.

Requirements:

- one ECS service per microservice,
- task definitions per service,
- health checks,
- autoscaling based on CPU, memory, or request metrics,
- environment-specific configuration,
- logs shipped to CloudWatch or external stack.

### Storage

Use RDS PostgreSQL.

Requirements:

- private subnet only,
- automated backups,
- restore test,
- security group allowing access only from services,
- optional read replica for read-heavy catalog use case.

### Cache

Use ElastiCache Redis.

Requirements:

- private subnet only,
- no public access,
- metrics enabled,
- failure behavior documented.

### Networking

You should be able to explain:

- why the load balancer is public,
- why services are private,
- why the database is private,
- how security groups restrict traffic,
- how route tables work,
- when NAT Gateway is needed,
- why NAT Gateway can become a cost driver.

---

## Cost Analysis

The project should include `docs/cost-analysis.md`.

### Main Cost Drivers

```text
ECS Fargate CPU and memory
RDS instance size
RDS storage and backups
ElastiCache node size
Load Balancer
NAT Gateway
Data transfer
CloudWatch logs
Metrics ingestion
Tracing ingestion
Broker service
```

### Required Exercise

Prepare two designs:

## Expensive But Technically Correct

Example:

```text
Multi-AZ RDS
Large ElastiCache cluster
MSK Kafka cluster
Multiple ECS tasks per service
High log retention
Full trace sampling
NAT Gateway per AZ
```

Explain why it works, but why it may be too expensive for an early-stage project.

## Cheaper Practical Variant

Example:

```text
Single smaller RDS instance
Small Redis node
SQS instead of MSK
Lower log retention
Sampling for traces
Fewer always-on service replicas
Autoscaling with conservative minimums
```

Explain the trade-offs:

- lower availability,
- lower throughput ceiling,
- slower recovery,
- less operational visibility,
- lower monthly cost.

---

## Security Considerations

Minimum security requirements:

- no public database,
- no public Redis,
- least-privilege security groups,
- secrets outside source code,
- environment variables or secrets manager,
- request validation,
- no sensitive data in logs,
- API keys hashed or securely stored,
- admin endpoints protected,
- dependency updates monitored.

This project is not primarily a security project, but the basic security posture must be defensible.

---

## Development Phases

## Phase 1: Modular Monolith Baseline

Start with a modular monolith.

Modules:

```text
catalog
reservation
order
payment
notification
```

Goal:

- validate domain model,
- implement core flow,
- avoid premature distributed complexity,
- define future service boundaries.

Exit criteria:

- user can browse events,
- user can reserve tickets,
- user can create order,
- payment mock works,
- notification mock works,
- basic tests exist.

---

## Phase 2: Split Into Microservices

Extract modules into separate services.

Goal:

- introduce service-to-service communication,
- add separate data ownership,
- add Docker Compose,
- add request correlation.

Exit criteria:

- each service runs independently,
- services communicate through HTTP and broker,
- each service exposes health endpoint,
- correlation ID is propagated.

---

## Phase 3: Resilience

Add production-like resilience patterns.

Goal:

- protect system from partial failures,
- prevent cascading failures,
- handle slow dependencies.

Exit criteria:

- Redis cache implemented,
- rate limiting implemented,
- Payment Service circuit breaker implemented,
- retries are bounded,
- fallback behavior documented,
- failure modes can be simulated.

---

## Phase 4: Observability

Add evidence-based debugging.

Goal:

- avoid guessing,
- diagnose failures using metrics, logs, and traces.

Exit criteria:

- JSON logs in every service,
- Prometheus metrics in every service,
- Grafana dashboards created,
- OpenTelemetry tracing works,
- alerts created,
- runbooks written.

---

## Phase 5: Load Testing

Test the system under increasing traffic.

Goal:

- identify bottlenecks,
- compare behavior before and after changes,
- document trade-offs.

Exit criteria:

- k6 tests exist,
- baseline results documented,
- overload behavior documented,
- at least three bottlenecks identified,
- improvements measured.

---

## Phase 6: Cloud Deployment

Deploy to AWS.

Goal:

- practice cloud architecture,
- understand compute, storage, networking, and costs.

Exit criteria:

- services deployed to ECS Fargate,
- ALB routes traffic,
- RDS is private,
- Redis is private,
- autoscaling configured,
- backup and restore tested,
- cost analysis written.

---

## Definition of Done

The project is complete only when the following are true.

### Architecture

- [ ] Service boundaries are documented.
- [ ] Data ownership is clear.
- [ ] Sync and async communication are justified.
- [ ] Trade-offs are documented.

### Functionality

- [ ] Users can browse events.
- [ ] Users can reserve tickets.
- [ ] Users can create orders.
- [ ] Payment mock supports failure modes.
- [ ] Notifications are asynchronous.

### Resilience

- [ ] Redis cache exists.
- [ ] Cache stampede protection exists.
- [ ] Rate limiting exists.
- [ ] Circuit breaker exists.
- [ ] Retry and backoff policies are bounded.
- [ ] Graceful degradation is documented.

### Observability

- [ ] Every service exposes Prometheus metrics.
- [ ] Every service produces structured JSON logs.
- [ ] Correlation ID is propagated.
- [ ] Distributed tracing works.
- [ ] Grafana dashboards exist.
- [ ] Alerts exist.
- [ ] Runbooks exist.

### Load Testing

- [ ] k6 tests exist.
- [ ] Baseline performance is documented.
- [ ] Bottlenecks are identified.
- [ ] Improvements are measured.
- [ ] Failure scenarios are tested.

### Cloud

- [ ] System is deployable to AWS.
- [ ] Stateless workloads run on ECS Fargate.
- [ ] RDS is configured with backups.
- [ ] Redis is configured privately.
- [ ] Networking is documented.
- [ ] Cost analysis is documented.

---

## Architecture Decision Records

Use ADRs for important decisions.

Recommended files:

```text
docs/adr/
├── 0001-use-ticketing-domain.md
├── 0002-start-with-modular-monolith.md
├── 0003-use-redis-for-catalog-cache.md
├── 0004-use-circuit-breaker-for-payment.md
├── 0005-use-events-for-notifications.md
├── 0006-use-ecs-fargate-for-cloud-deployment.md
└── 0007-use-sqs-or-kafka-for-events.md
```

ADR template:

```markdown
# ADR-000X: Decision Title

## Status
Accepted / Proposed / Rejected

## Context
What problem are we solving?

## Decision
What did we decide?

## Consequences
What are the trade-offs?

## Alternatives Considered
What else was considered?
```

---

## Interview / Review Questions

You should be able to answer these questions after completing the project.

### System Design

1. Why did you split the system into these services?
2. Which service owns inventory?
3. How do you prevent overselling?
4. Which endpoints are cacheable and why?
5. What happens when cache returns stale availability?
6. What is the first bottleneck at 1,000 RPS?
7. How do you know it is the bottleneck?
8. What is the difference between improving throughput and improving latency?
9. What would you scale first and why?
10. Which part of the system is hardest to scale?

### Resilience

1. What happens when Payment Service is slow?
2. What happens when Payment Service is down?
3. Can retry make the outage worse?
4. Why do you need jitter?
5. What does the circuit breaker protect?
6. What does graceful degradation mean here?
7. What happens when Redis is unavailable?
8. What happens when the broker is lagging?
9. Which operations must be idempotent?
10. Where can data become inconsistent?

### Observability

1. Which metric confirms high latency?
2. Which metric confirms saturation?
3. How do you find the slow downstream dependency?
4. How do you follow a request across services?
5. What should be logged when payment fails?
6. What should not be logged?
7. Which alert fires first during DB saturation?
8. How do you avoid alert fatigue?
9. How do you debug a single failed order?
10. How do you prove that cache improved performance?

### Cloud

1. Why are services deployed in private subnets?
2. Why is the database not public?
3. What is the role of the load balancer?
4. What are the main cost drivers?
5. When is autoscaling better than overprovisioning?
6. What is too expensive in your first cloud design?
7. How would you reduce cost?
8. How do backups and restore work?
9. What would fail during an AZ outage?
10. What would you change for production?

---

## Useful Commands

### Start Local Environment

```bash
./scripts/start-local.sh
```

or:

```bash
docker compose up -d
```

### Stop Local Environment

```bash
./scripts/stop-local.sh
```

or:

```bash
docker compose down
```

### Seed Test Data

```bash
./scripts/seed-data.sh
```

### Run Load Tests

```bash
./scripts/run-load-tests.sh
```

or:

```bash
k6 run load-tests/browse-events.js
k6 run load-tests/create-reservation.js
k6 run load-tests/create-order.js
```

### Simulate Payment Failure

```bash
curl -X POST http://localhost:8085/admin/failure-mode \
  -H "Content-Type: application/json" \
  -d '{"mode":"random-5xx","errorRate":0.3}'
```

### Simulate Payment Latency

```bash
curl -X POST http://localhost:8085/admin/failure-mode \
  -H "Content-Type: application/json" \
  -d '{"mode":"random-delay","minMs":500,"maxMs":5000}'
```

### Check Prometheus Metrics

```bash
curl http://localhost:8081/actuator/prometheus
curl http://localhost:8082/actuator/prometheus
curl http://localhost:8083/actuator/prometheus
```

### Open Local Tools

```text
Grafana:    http://localhost:3000
Prometheus: http://localhost:9090
Jaeger:     http://localhost:16686
```

---

## License

This project is intended for learning and portfolio purposes.

Suggested license: MIT.

---

## Final Note

The goal of this project is not to maximize the number of services. The goal is to build a system that can be reasoned about under load and under failure.

A smaller system with clear service boundaries, strong observability, meaningful load tests, and documented trade-offs is more valuable than a large distributed system that cannot be explained or operated.
