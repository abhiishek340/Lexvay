resource "aws_db_subnet_group" "main" {
  name        = local.name
  description = "Private subnets for the ${local.name} database"
  subnet_ids  = aws_subnet.private[*].id
}

resource "aws_db_parameter_group" "main" {
  name        = "${local.name}-postgres18"
  family      = "postgres18"
  description = "${local.name}: TLS required, statements slower than one second logged"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }
}

resource "aws_db_instance" "main" {
  identifier = local.name

  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  storage_type          = "gp3"
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.data.arn

  # The administrator password is created and rotated by RDS in Secrets Manager. The application roles in
  # local.db_owner_user and local.db_app_user are created from db/initdb/01-roles.sh, using that administrator.
  username                      = "lexvay_admin"
  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.data.arn

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  parameter_group_name   = aws_db_parameter_group.main.name
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period         = var.db_backup_retention_days
  copy_tags_to_snapshot           = true
  deletion_protection             = var.deletion_protection
  skip_final_snapshot             = !var.deletion_protection
  final_snapshot_identifier       = var.deletion_protection ? "${local.name}-final" : null
  auto_minor_version_upgrade      = true
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  apply_immediately               = false
}
