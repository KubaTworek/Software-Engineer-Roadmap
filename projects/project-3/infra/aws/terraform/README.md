# Phase 6 — AWS cloud deployment

This Terraform stack deploys the ticketing platform to AWS using a pragmatic ECS Fargate architecture.
It is not meant to be a perfect production setup. It is meant to force senior-level cloud decisions around networking, scaling, cost, backup, and operational trade-offs.

## Architecture

```text
Internet
  -> Application Load Balancer, public subnets
    -> API Gateway, ECS Fargate, private subnets
      -> Catalog Service, ECS Fargate, private subnets
      -> Reservation Service, ECS Fargate, private subnets
      -> Order Service, ECS Fargate, private subnets
      -> Payment Mock Service, ECS Fargate, private subnets
      -> Notification Service, ECS Fargate, private subnets
        -> RDS PostgreSQL, private subnets
        -> ElastiCache Redis, private subnets
        -> Amazon MQ RabbitMQ, private subnet, optional
```

## What is created

- VPC with two public and two private subnets.
- Internet Gateway.
- Optional NAT Gateway for private ECS tasks.
- ALB in public subnets.
- ECS cluster with Fargate services.
- AWS Cloud Map private service discovery.
- ECR repositories for all application services.
- RDS PostgreSQL.
- ElastiCache Redis.
- Optional Amazon MQ RabbitMQ.
- Security groups for ALB, ECS, RDS, Redis, and MQ.
- CloudWatch log groups.
- ECS Service Auto Scaling based on CPU.
- RDS automated backups.

## Prerequisites

Install:

- AWS CLI
- Terraform >= 1.6
- Docker
- PowerShell

Authenticate to AWS:

```powershell
aws configure
aws sts get-caller-identity
```

## First deployment

From repository root:

```powershell
Copy-Item infra/aws/terraform/terraform.tfvars.example infra/aws/terraform/terraform.tfvars
```

Edit:

```text
infra/aws/terraform/terraform.tfvars
```

Set strong values for:

```hcl
database_password = "..."
mq_password       = "..."
```

Initialize and create ECR repositories first:

```powershell
cd infra/aws/terraform
terraform init
terraform apply -target=aws_ecr_repository.service -target=aws_ecr_lifecycle_policy.service -var-file=terraform.tfvars
cd ../../..
```

Build and push images:

```powershell
./scripts/aws-build-and-push-images.ps1 -Region eu-central-1 -ProjectName ticketing-platform -Environment dev -Tag latest
```

Deploy full infrastructure:

```powershell
./scripts/aws-deploy.ps1
```

Print outputs:

```powershell
./scripts/aws-print-outputs.ps1
```

Call the API:

```powershell
$baseUrl = terraform -chdir=infra/aws/terraform output -raw api_base_url
curl.exe "$baseUrl/events"
```

## Destroy

Destroy the stack when you are done. This environment creates paid resources.

```powershell
./scripts/aws-destroy.ps1
```

## Why this design is defensible

### ECS Fargate

Fargate reduces operational burden. You do not manage EC2 worker nodes. This is appropriate for a learning project focused on service architecture, not Kubernetes operations.

### ALB

Only the gateway is public. Internal services are private and discovered through Cloud Map.

### Private subnets

Application tasks, RDS, Redis, and RabbitMQ are not directly exposed to the internet.

### RDS

A managed database gives backups, patching, monitoring, and restore workflows. This setup uses one shared DB for cost reasons. A stricter production setup could use separate RDS instances or schemas per service.

### Redis

Redis supports catalog caching and gateway rate limiting. This stack uses a small single-node Redis for cost reasons.

### Autoscaling

All ECS services get CPU target tracking autoscaling. It is simple and sufficient for the first cloud deployment. A stronger setup could add request count per target, queue depth, latency, or custom metrics.

### Backup/restore

RDS automated backups are enabled. A restore runbook is included in `docs/cloud/rds-backup-restore.md`.

## Known limitations

- HTTP only. Add HTTPS with ACM before exposing anything serious.
- Secrets are plain Terraform variables and ECS environment variables. Use Secrets Manager for a more mature setup.
- One shared RDS database is used to reduce cost.
- Redis and RabbitMQ are single-node/single-instance.
- Observability is mostly CloudWatch in this stack. The local Grafana/Tempo/Loki stack is not deployed to AWS here.
- NAT Gateway is convenient but costly. For lower cost, replace it with VPC endpoints for ECR, CloudWatch Logs, and S3, or temporarily use public IPs for ECS tasks in a non-production sandbox.
