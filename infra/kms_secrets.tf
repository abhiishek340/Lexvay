# One customer-managed key encrypts the database, its credentials, the document bucket and the flow log group.
resource "aws_kms_key" "data" {
  description             = "${local.name} data at rest: database, secrets, uploaded documents and flow logs"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy                  = data.aws_iam_policy_document.data_key.json

  tags = { Name = "${local.name}-data" }
}

# The first statement is what the default key policy grants: the account's IAM policies decide who may use the key
# (the task roles, RDS, S3 and Secrets Manager rely on it). The second lets CloudWatch Logs encrypt the flow log
# group, and only that one: the encryption context names the log group.
data "aws_iam_policy_document" "data_key" {
  statement {
    sid       = "EnableIamPolicies"
    actions   = ["kms:*"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }

  statement {
    sid       = "CloudWatchLogsEncryptsTheFlowLogGroup"
    actions   = ["kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:Describe*"]
    resources = ["*"]

    principals {
      type        = "Service"
      identifiers = ["logs.${var.region}.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = [local.flow_log_group_arn]
    }
  }
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
