# Phase 6 — Cloud deployment

## Status

Phase 6 adds a pragmatic AWS deployment implementation under:

```text
infra/aws/terraform/
```

This is a real Infrastructure as Code baseline, not only a diagram.

## Implemented scope

- ECS Fargate
- ALB
- RDS PostgreSQL
- ElastiCache Redis
- VPC
- public/private subnets
- security groups
- ECS service discovery
- autoscaling
- CloudWatch logs
- RDS backup retention
- optional Amazon MQ RabbitMQ

## Deployment model

Only API Gateway is public through ALB.
All application services run in private subnets.
RDS, Redis and RabbitMQ are private.

```text
ALB public
  -> api-gateway private ECS task
    -> internal services via Cloud Map
      -> RDS / Redis / MQ
```

## Why AWS and not GCP here

This phase was implemented for AWS because the requested target services were AWS-specific:

- ECS Fargate
- ALB
- RDS
- ElastiCache Redis
- VPC security groups

A GCP version would use different primitives, for example Cloud Run or GKE, Cloud Load Balancing, Cloud SQL, Memorystore, VPC firewall rules and Pub/Sub or RabbitMQ on GKE.

## How to deploy

See:

```text
infra/aws/terraform/README.md
```

## Minimum acceptance criteria

You should be able to answer:

1. Why is only ALB public?
2. Why are ECS tasks in private subnets?
3. What breaks if NAT Gateway is disabled?
4. Why is RDS private?
5. What is the Redis failure mode?
6. How do ECS services discover each other?
7. Which security group allows service-to-service traffic?
8. What does autoscaling use as a signal?
9. How do you restore RDS from backup?
10. Which resource is the biggest avoidable cost driver?

## Deliberate trade-offs

This is not full production-grade infrastructure. The design intentionally keeps complexity manageable.

| Area | Current implementation | Production direction |
|---|---|---|
| TLS | HTTP ALB | HTTPS with ACM and HTTP to HTTPS redirect |
| Secrets | Terraform variables/env vars | AWS Secrets Manager |
| RDS | One shared DB | Separate databases/schemas per service |
| Redis | Single node | Multi-AZ replication group |
| MQ | Single RabbitMQ broker | Multi-AZ broker or SQS/SNS redesign |
| Observability | CloudWatch logs | Managed Prometheus/Grafana/X-Ray/OTel collector |
| NAT | One NAT Gateway | NAT per AZ or VPC endpoints |
