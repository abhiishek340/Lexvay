resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/${var.project}/${var.environment}/backend"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.backend_cpu_architecture
  }

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = var.backend_image
      essential = true

      portMappings = [{ containerPort = var.backend_port, protocol = "tcp" }]

      environment = [
        { name = "LEXVAY_PORT", value = tostring(var.backend_port) },
        { name = "LEXVAY_DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${local.db_name}?sslmode=require" },
        { name = "LEXVAY_DB_OWNER_USER", value = local.db_owner_user },
        { name = "LEXVAY_DB_APP_USER", value = local.db_app_user },
        { name = "LEXVAY_FILESTORE", value = "s3" },
        { name = "LEXVAY_S3_BUCKET", value = aws_s3_bucket.files.bucket },
        { name = "LEXVAY_LLM_PROVIDER", value = "anthropic" },
        { name = "LEXVAY_LLM_MODEL", value = var.llm_model },
        { name = "LEXVAY_LLM_EFFORT", value = var.llm_effort },
        { name = "LEXVAY_LLM_PRICE_INPUT_PER_MTOK", value = tostring(var.llm_price_input_per_mtok) },
        { name = "LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK", value = tostring(var.llm_price_output_per_mtok) },
      ]

      secrets = [
        { name = "LEXVAY_DB_OWNER_PASSWORD", valueFrom = "${aws_secretsmanager_secret.database.arn}:owner_password::" },
        { name = "LEXVAY_DB_APP_PASSWORD", valueFrom = "${aws_secretsmanager_secret.database.arn}:app_password::" },
        { name = "ANTHROPIC_API_KEY", valueFrom = aws_secretsmanager_secret.anthropic.arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.backend.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "backend"
        }
      }
    }
  ])

  # The secret values must exist before a task can start.
  depends_on = [aws_secretsmanager_secret_version.database]
}

resource "aws_ecs_service" "backend" {
  name            = "backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count
  launch_type     = "FARGATE"

  propagate_tags = "SERVICE"

  # Flyway migrations run at start-up, so give the first health checks time before they count.
  health_check_grace_period_seconds = 180

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.backend.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = var.backend_port
  }

  depends_on = [aws_lb_listener_rule.from_cloudfront]
}
