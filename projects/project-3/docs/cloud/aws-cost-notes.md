# AWS cost notes

This deployment is intentionally practical, not fully production-grade.
The main cost drivers are:

- NAT Gateway: convenient for private ECS tasks, but expensive for a training project.
- ECS Fargate CPU and memory.
- RDS instance class and storage.
- ElastiCache Redis node.
- Amazon MQ RabbitMQ broker.
- ALB hourly cost and LCU usage.
- CloudWatch logs volume and retention.

## Cheaper alternatives

For a cheaper training deployment:

- Set `enable_mq = false` and disable the notification flow.
- Use a single small RDS instance instead of database-per-service.
- Keep `desired_count = 1`.
- Keep `max_capacity = 2` or `3`.
- Destroy the environment after testing.

## What is deliberately not production-grade

- One shared RDS database is used to reduce cost.
- Redis is single-node.
- RabbitMQ is single-instance.
- HTTP is used on ALB instead of HTTPS.
- Secrets are passed as Terraform variables and ECS env vars, not Secrets Manager.

These are acceptable for learning, but you should be able to explain the trade-offs.
