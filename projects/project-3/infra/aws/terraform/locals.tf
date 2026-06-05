locals {
  name = "${var.project_name}-${var.environment}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Stage       = "phase-6-cloud"
  }

  namespace_name = "${local.name}.local"
  rabbitmq_host  = var.enable_mq ? trimsuffix(trimprefix(aws_mq_broker.rabbitmq[0].instances[0].endpoints[0], "amqps://"), ":5671") : "disabled"

  service_names = toset([
    "api-gateway",
    "catalog-service",
    "reservation-service",
    "order-service",
    "payment-mock-service",
    "notification-service"
  ])

  services = {
    "catalog-service" = {
      port       = 8081
      dockerfile = "catalog-service/Dockerfile"
      env = {
        SERVER_PORT                              = "8081"
        LOG_LEVEL                                = "INFO"
        SPRING_DATASOURCE_URL                    = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.database_name}"
        SPRING_DATASOURCE_USERNAME               = var.database_username
        SPRING_DATASOURCE_PASSWORD               = var.database_password
        SPRING_DATA_REDIS_HOST                   = aws_elasticache_replication_group.redis.primary_endpoint_address
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY  = "1.0"
      }
    }

    "reservation-service" = {
      port       = 8082
      dockerfile = "reservation-service/Dockerfile"
      env = {
        SERVER_PORT                              = "8082"
        LOG_LEVEL                                = "INFO"
        SPRING_DATASOURCE_URL                    = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.database_name}"
        SPRING_DATASOURCE_USERNAME               = var.database_username
        SPRING_DATASOURCE_PASSWORD               = var.database_password
        CATALOG_SERVICE_URL                      = "http://catalog-service.${local.namespace_name}:8081"
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY  = "1.0"
      }
    }

    "order-service" = {
      port       = 8083
      dockerfile = "order-service/Dockerfile"
      env = {
        SERVER_PORT                              = "8083"
        LOG_LEVEL                                = "INFO"
        SPRING_DATASOURCE_URL                    = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.database_name}"
        SPRING_DATASOURCE_USERNAME               = var.database_username
        SPRING_DATASOURCE_PASSWORD               = var.database_password
        PAYMENT_SERVICE_URL                      = "http://payment-mock-service.${local.namespace_name}:8084"
        RESERVATION_SERVICE_URL                  = "http://reservation-service.${local.namespace_name}:8082"
        SPRING_RABBITMQ_HOST                     = local.rabbitmq_host
        SPRING_RABBITMQ_PORT                     = "5671"
        SPRING_RABBITMQ_SSL_ENABLED              = "true"
        SPRING_RABBITMQ_USERNAME                 = var.mq_username
        SPRING_RABBITMQ_PASSWORD                 = var.mq_password
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY  = "1.0"
      }
    }

    "payment-mock-service" = {
      port       = 8084
      dockerfile = "payment-mock-service/Dockerfile"
      env = {
        SERVER_PORT                              = "8084"
        LOG_LEVEL                                = "INFO"
        PAYMENT_FAILURE_RATE                     = "0.0"
        PAYMENT_MAX_DELAY_MS                     = "300"
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY  = "1.0"
      }
    }

    "notification-service" = {
      port       = 8085
      dockerfile = "notification-service/Dockerfile"
      env = {
        SERVER_PORT                              = "8085"
        LOG_LEVEL                                = "INFO"
        SPRING_RABBITMQ_HOST                     = local.rabbitmq_host
        SPRING_RABBITMQ_PORT                     = "5671"
        SPRING_RABBITMQ_SSL_ENABLED              = "true"
        SPRING_RABBITMQ_USERNAME                 = var.mq_username
        SPRING_RABBITMQ_PASSWORD                 = var.mq_password
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY  = "1.0"
      }
    }

    "api-gateway" = {
      port       = 8080
      dockerfile = "api-gateway/Dockerfile"
      env = {
        SERVER_PORT                              = "8080"
        LOG_LEVEL                                = "INFO"
        CATALOG_SERVICE_URL                      = "http://catalog-service.${local.namespace_name}:8081"
        RESERVATION_SERVICE_URL                  = "http://reservation-service.${local.namespace_name}:8082"
        ORDER_SERVICE_URL                        = "http://order-service.${local.namespace_name}:8083"
        SPRING_DATA_REDIS_HOST                   = aws_elasticache_replication_group.redis.primary_endpoint_address
        RATE_LIMIT_ENABLED                       = "true"
        RATE_LIMIT_ANON_CAPACITY                 = "60"
        RATE_LIMIT_ANON_REFILL_TOKENS            = "60"
        RATE_LIMIT_ANON_REFILL_PERIOD            = "60s"
        RATE_LIMIT_API_KEY_CAPACITY              = "600"
        RATE_LIMIT_API_KEY_REFILL_TOKENS         = "600"
        RATE_LIMIT_API_KEY_REFILL_PERIOD         = "60s"
        MANAGEMENT_TRACING_SAMPLING_PROBABILITY  = "1.0"
      }
    }
  }
}
