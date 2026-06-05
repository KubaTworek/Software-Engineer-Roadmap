variable "aws_region" {
  description = "AWS region used for the deployment."
  type        = string
  default     = "eu-central-1"
}

variable "project_name" {
  description = "Name prefix for AWS resources."
  type        = string
  default     = "ticketing-platform"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.30.0.0/16"
}

variable "enable_nat_gateway" {
  description = "If true, creates one NAT Gateway so private ECS tasks can pull images and send logs. This is convenient but not cheap."
  type        = bool
  default     = true
}

variable "container_image_tag" {
  description = "Docker image tag used by ECS task definitions."
  type        = string
  default     = "latest"
}

variable "app_cpu" {
  description = "Default Fargate task CPU units."
  type        = number
  default     = 512
}

variable "app_memory" {
  description = "Default Fargate task memory in MB."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Default desired task count per service."
  type        = number
  default     = 1
}

variable "min_capacity" {
  description = "Minimum ECS service task count for autoscaling."
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum ECS service task count for autoscaling."
  type        = number
  default     = 3
}

variable "autoscaling_cpu_target" {
  description = "Target average CPU utilization for ECS Service Auto Scaling."
  type        = number
  default     = 60
}

variable "database_name" {
  description = "Single shared PostgreSQL database name for this training deployment. Production could split databases per service."
  type        = string
  default     = "ticketing"
}

variable "database_username" {
  description = "RDS master username."
  type        = string
  default     = "app"
}

variable "database_password" {
  description = "RDS master password. Use a tfvars file or environment variable for real deployments."
  type        = string
  sensitive   = true
}

variable "rds_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB."
  type        = number
  default     = 20
}

variable "rds_backup_retention_days" {
  description = "RDS automated backup retention in days."
  type        = number
  default     = 7
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "enable_mq" {
  description = "If true, creates Amazon MQ for RabbitMQ. It is useful for the notification flow but relatively expensive for a training project."
  type        = bool
  default     = true
}

variable "mq_username" {
  description = "Amazon MQ RabbitMQ username."
  type        = string
  default     = "app"
}

variable "mq_password" {
  description = "Amazon MQ RabbitMQ password. Use a tfvars file or environment variable for real deployments."
  type        = string
  sensitive   = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention."
  type        = number
  default     = 14
}
