resource "aws_secretsmanager_secret" "database" {
  name                    = "${local.name}/database"
  recovery_window_in_days = var.protect_data ? 30 : 0

  tags = { Name = "${local.name}-database-secret" }
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id
  secret_string = jsonencode({
    username = var.database_username
    password = var.database_password
  })
}

resource "aws_secretsmanager_secret" "rabbitmq" {
  name                    = "${local.name}/rabbitmq"
  recovery_window_in_days = var.protect_data ? 30 : 0

  tags = { Name = "${local.name}-rabbitmq-secret" }
}

resource "aws_secretsmanager_secret_version" "rabbitmq" {
  secret_id = aws_secretsmanager_secret.rabbitmq.id
  secret_string = jsonencode({
    username = var.mq_username
    password = var.mq_password
  })
}
