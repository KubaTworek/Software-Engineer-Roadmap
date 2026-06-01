# ADR 0007: Docker and Kubernetes runtime

## Context

The application is a modular monolith, but it should be deployable and diagnosable in a production-like environment. Earlier phases focused on domain boundaries, events, outbox, Kafka consumers and observability. This phase adds the runtime packaging and deployment model.

## Decision

We package the application as a Docker image using a multi-stage build and run it as a non-root user. Runtime configuration is passed through environment variables. Local development uses `docker compose` with PostgreSQL and Kafka. Local Kubernetes uses Kustomize manifests with ConfigMap, Secret, Deployments, Services, readiness/liveness/startup probes and rolling update settings.

## Consequences

The same application artifact can run locally and in Kubernetes with different runtime configuration. Operational failures such as broken database credentials, broken hostnames, not-ready pods and rollout problems can be diagnosed through Docker and Kubernetes tools.

## Alternatives considered

- Running everything directly from the IDE. Rejected because it does not exercise runtime configuration and deployment diagnostics.
- Using Helm immediately. Rejected for now because plain manifests are easier to read and better for learning Kubernetes fundamentals.
- Running external managed PostgreSQL/Kafka. Rejected for this phase because the goal is a local, reproducible learning environment.
