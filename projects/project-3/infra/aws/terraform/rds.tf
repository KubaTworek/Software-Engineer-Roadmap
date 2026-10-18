resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db-subnets"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${local.name}-db-subnets" }
}

resource "aws_db_instance" "postgres" {
  identifier                = local.name
  engine                    = "postgres"
  engine_version            = "16"
  instance_class            = var.rds_instance_class
  allocated_storage         = var.rds_allocated_storage
  storage_type              = "gp3"
  db_name                   = var.database_name
  username                  = var.database_username
  password                  = var.database_password
  db_subnet_group_name      = aws_db_subnet_group.main.name
  vpc_security_group_ids    = [aws_security_group.rds.id]
  publicly_accessible       = false
  multi_az                  = var.enable_high_availability
  backup_retention_period   = var.rds_backup_retention_days
  backup_window             = "02:00-03:00"
  maintenance_window        = "sun:03:00-sun:04:00"
  deletion_protection       = var.protect_data
  skip_final_snapshot       = !var.protect_data
  final_snapshot_identifier = var.protect_data ? "${local.name}-final-snapshot" : null

  performance_insights_enabled = var.enable_high_availability

  tags = { Name = "${local.name}-postgres" }
}
