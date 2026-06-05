output "alb_dns_name" {
  description = "Public ALB DNS name for the API Gateway."
  value       = aws_lb.main.dns_name
}

output "api_base_url" {
  description = "HTTP base URL for the deployed API."
  value       = "http://${aws_lb.main.dns_name}"
}

output "ecr_repositories" {
  description = "ECR repository URLs by service."
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "rds_endpoint" {
  value     = aws_db_instance.postgres.address
  sensitive = true
}

output "redis_endpoint" {
  value = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "rabbitmq_endpoint" {
  value     = var.enable_mq ? aws_mq_broker.rabbitmq[0].instances[0].endpoints[0] : "disabled"
  sensitive = true
}
