# Java Backend Roadmap — From Mid-Level to Senior (Hands-On Engineering Track)

This repository documents a structured, hands-on progression from **Mid-Level Backend Engineer** to **Senior Engineer** using Java, Spring, Distributed Systems, and Cloud.

It is not a collection of tutorials.  
It is a set of **experiments, failure cases, benchmarks, architectural decisions, and measurable artifacts**.

> No intuition-driven engineering. Only decisions backed by data, trade-offs, and system understanding.

---

# Stage 1 (0–6 Months) — Strong Mid-Level Foundation

## Goals

- Concurrency correctness
- JVM performance awareness
- Understanding Spring internals
- Database performance literacy
- Testability and refactoring discipline

---

## Block A — Java Core & Concurrency

### Core References

- :contentReference[oaicite:0]{index=0}
- :contentReference[oaicite:1]{index=1}

---

### 1. ExecutorService Lab

**Experiments**
- Fixed vs Cached thread pools
- Bounded vs Unbounded queues
- RejectedExecutionHandler strategies
- Thread starvation scenarios

**Exercise**
Simulate 1000 concurrent order requests.

Measure:
- Throughput
- Latency
- Rejected tasks
- Queue growth

**Deliverable**
Short report explaining trade-offs and behavior under load.

---

### 2. Race Condition — Lost Update / Overselling

Implement:
- Broken version (no synchronization)
- `synchronized`
- `ReentrantLock`
- Optimistic locking (version column in DB)

**Deliverable**
Stress test that randomly fails under concurrency when bug is present.

Must explain:
- Why it fails
- Happens-before relationship
- Visibility guarantees

---

### 3. Deadlock Lab

Intentionally introduce:
- Two locks
- Reverse acquisition order

Analyze:
- Thread dump
- Lock ownership
- Deadlock cycle

Fix:
- Global lock ordering

**Deliverable**
Thread dump + explanation of failure.

---

### 4. CompletableFuture Lab

Topics:
- Fan-out / fan-in
- Timeouts
- Fallback strategies
- Exception handling

**Exercise**
Aggregate 3 downstream services with timeout and fallback behavior.

---

## Block B — JVM & Profiling

### Core References

- :contentReference[oaicite:2]{index=2}
- :contentReference[oaicite:3]{index=3}

---

### 1. CPU Hotspot Investigation

- Generate artificial load
- Record JFR profile
- Analyze flame graph
- Identify bottleneck

---

### 2. Allocation Rate & GC Pressure

Compare:
- Streams vs loops
- Immutable vs mutable
- Object churn impact

Measure:
- Allocation rate
- GC pauses
- Throughput

---

### 3. GC Experiments

- G1 as default
- ZGC experiment
- Compare pause times and CPU usage

**Deliverable**
Answer with data:

> Why does the system slow down at 500 RPS?

---

## Block C — Spring Internals

### Reference

- :contentReference[oaicite:4]{index=4}

---

### 1. @Transactional Deep Dive

Cases:
- Self-invocation issue
- Proxy boundary limitations
- Propagation: REQUIRES_NEW vs NESTED

**Deliverable**
Documented case where `@Transactional` does not work + fix.

---

### 2. Security

- JWT access/refresh tokens
- Token rotation
- `@PreAuthorize` with SpEL
- Data-based authorization

Tests:
- Negative cases prioritized

---

### 3. Error Handling

Unified error contract:

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "Order not found",
  "details": "...",
  "traceId": "..."
}