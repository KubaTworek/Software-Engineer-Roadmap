resource "aws_mq_broker" "rabbitmq" {
  count = var.enable_mq ? 1 : 0

  broker_name        = local.name
  engine_type        = "RabbitMQ"
  engine_version     = "3.13"
  host_instance_type = "mq.t3.micro"
  deployment_mode    = "SINGLE_INSTANCE"
  publicly_accessible = false
  subnet_ids          = [aws_subnet.private[0].id]
  security_groups     = [aws_security_group.mq.id]

  user {
    username = var.mq_username
    password = var.mq_password
  }

  logs {
    general = true
  }

  tags = { Name = "${local.name}-rabbitmq" }
}
