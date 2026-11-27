# Every accepted and rejected connection in the VPC, kept for var.log_retention_days in a log group encrypted with the
# data key. Records carry addresses, ports and byte counts, never payloads.

locals {
  flow_log_group_name = "/${var.project}/${var.environment}/vpc-flow-logs"

  # Spelled out instead of read from the log group, because the key policy needs it and the log group needs the key.
  flow_log_group_arn = "arn:${data.aws_partition.current.partition}:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:${local.flow_log_group_name}"
}

resource "aws_cloudwatch_log_group" "flow" {
  name              = local.flow_log_group_name
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.data.arn
}

data "aws_iam_policy_document" "flow_logs_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "flow_logs" {
  name               = "${local.name}-vpc-flow-logs"
  assume_role_policy = data.aws_iam_policy_document.flow_logs_assume.json
}

data "aws_iam_policy_document" "flow_logs_delivery" {
  statement {
    sid       = "WriteToTheFlowLogGroup"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
    resources = ["${aws_cloudwatch_log_group.flow.arn}:*"]
  }

  statement {
    sid       = "DescribeTheFlowLogGroup"
    actions   = ["logs:DescribeLogGroups"]
    resources = [aws_cloudwatch_log_group.flow.arn]
  }
}

resource "aws_iam_role_policy" "flow_logs" {
  name   = "deliver-flow-logs"
  role   = aws_iam_role.flow_logs.id
  policy = data.aws_iam_policy_document.flow_logs_delivery.json
}

resource "aws_flow_log" "vpc" {
  vpc_id               = aws_vpc.main.id
  traffic_type         = "ALL"
  log_destination_type = "cloud-watch-logs"
  log_destination      = aws_cloudwatch_log_group.flow.arn
  iam_role_arn         = aws_iam_role.flow_logs.arn

  tags = { Name = local.name }

  depends_on = [aws_iam_role_policy.flow_logs]
}
