# AWS architecture decisions

## Decision 1: ECS Fargate instead of Kubernetes

ECS Fargate is enough for this stage because the goal is to understand cloud deployment, networking, autoscaling, observability and cost. Kubernetes would add a second platform to learn and operate.

## Decision 2: only API Gateway is public

The ALB forwards traffic only to API Gateway. Other services use private DNS through Cloud Map. This reduces the public attack surface.

## Decision 3: private RDS and Redis

RDS and Redis are reachable only from ECS security group. They are not public and do not accept internet traffic.

## Decision 4: one shared RDS instance for training

The local project has separate databases per bounded context. The AWS baseline uses one RDS instance and one database to reduce cost. This is acceptable for learning but should be revisited for production isolation.

## Decision 5: NAT Gateway enabled by default

Private Fargate tasks need a way to pull images from ECR and publish logs to CloudWatch. NAT Gateway is the simplest answer but it is a major cost driver. A more mature setup would use VPC endpoints.

## Decision 6: CPU autoscaling first

CPU target tracking is easy to reason about and works as a first autoscaling policy. For real production, add request count, latency, queue depth and custom saturation metrics.

## Decision 7: RDS backup retention

Automated backups are enabled by default. A cloud deployment is not complete unless restore is also practiced.
