# Decisions

## 0001 — Start as a plain layered monolith

The project starts as a regular Spring Boot monolith with controller, service and repository layers.

Reason: the learning goal is to understand concurrency, transactions, JPA, SQL performance, JVM behavior and Spring internals without hiding basic problems behind architectural ceremony.

## 0002 — PostgreSQL is the source of truth

Reservations and capacity are stored in PostgreSQL.

Reason: limited capacity and overselling are transactional problems. The base project should first solve them with relational database mechanisms before introducing cache, NoSQL or asynchronous processing.

## 0003 — Atomic SQL update for reservation capacity

The base reservation flow decrements capacity with a guarded update.

Reason: it is a simple and robust baseline against overselling. Later stages should still implement naive and lock-based variants for educational comparison.
