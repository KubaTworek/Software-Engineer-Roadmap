# Java classes for the GCP cloud architecture concept

This package contains conceptual Java/Spring Boot classes that represent the cloud architecture concept from the Polish material.

The code focuses on stateless HTTP services, health and readiness checks, Cloud SQL connection pooling, cache-aside with Redis/Memorystore, cache invalidation after writes, asynchronous processing through a Pub/Sub publisher abstraction, idempotency keys for write operations, Redis-based rate limiting, structured JSON logging, explicit timeout thinking for external calls, and separation between controllers, services, repositories and infrastructure configuration.

The `operations` package extends the runtime example with executable architecture decisions: measurable RPO/RTO, backup and restore-drill evidence, regional failover, dependency-loss playbooks, migration-aware rollback, provider-independent desired infrastructure state, drift detection, workload identity and least-privilege IAM validation. These models deliberately do not call GCP APIs; they make operational assumptions testable without requiring a cloud account.

The classes are not meant to be copied as a complete production application without adaptation. They are a clean educational skeleton showing where each cloud-native responsibility should live in a Java backend.
