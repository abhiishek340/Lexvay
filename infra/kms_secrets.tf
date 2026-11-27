# One customer-managed key encrypts the database, its credentials and the document bucket.
resource "aws_kms_key" "data" {
  description             = "${local.name} data at rest: database, secrets and uploaded documents"
  enable_key_rotation     = true
  deletion_window_in_days = 30

  tags = { Name = "${local.name}-data" }
}

resource "aws_kms_alias" "data" {
  name          = "alias/${local.name}-data"
  target_key_id = aws_kms_key.data.key_id
}

# Passwords of the two database roles. They live in Terraform state, so the state must be encrypted and
# readable only by the people who operate this stack. Rotating them means changing the role in PostgreSQL too.
resource "random_password" "db_owner" {
  length  = 40
  special = false
}

resource "random_password" "db_app" {
  length  = 40
  special = false
}

resource "aws_secretsmanager_secret" "database" {
  name        = "${local.name}/database"
  description = "Passwords of the ${local.db_owner_user} (migrations) and ${local.db_app_user} (service) database roles"
  kms_key_id  = aws_kms_key.data.arn

  recovery_window_in_days = var.deletion_protection ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id

  secret_string = jsonencode({
    owner_password = random_password.db_owner.result
    app_password   = random_password.db_app.result
  })
}

# The model API key is never in Terraform: create the entry here, then store the key with
#   aws secretsmanager put-secret-value --secret-id <anthropic_secret_arn> --secret-string <key>
resource "aws_secretsmanager_secret" "anthropic" {
  name        = "${local.name}/anthropic-api-key"
  description = "Anthropic API key (ANTHROPIC_API_KEY) for the backend. The value is set outside Terraform."
  kms_key_id  = aws_kms_key.data.arn

  recovery_window_in_days = var.deletion_protection ? 30 : 0
}

# Shared secret between CloudFront and the load balancer, so only this distribution can use the load balancer.
resource "random_password" "origin_verify" {
  length  = 40
  special = false
}
