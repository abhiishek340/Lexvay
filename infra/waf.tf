# Web ACL in front of CloudFront. Scope CLOUDFRONT exists only in us-east-1, so every resource here uses the aliased
# provider. The ACL allows by default; each rule below can only add a block. Rules run in priority order.

locals {
  waf_metric_prefix = replace(local.name, "-", "_")

  # The group must be named aws-waf-logs-*: AWS WAF refuses any other log destination.
  waf_log_group_name = "aws-waf-logs-${local.name}"
}

resource "aws_wafv2_web_acl" "cloudfront" {
  provider = aws.us_east_1

  name        = local.name
  description = "${local.name}: managed rule groups and a sign-in rate limit"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "AWSManagedRulesAmazonIpReputationList"
    priority = 0

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesAmazonIpReputationList"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.waf_metric_prefix}_ip_reputation"
    }
  }

  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.waf_metric_prefix}_known_bad_inputs"
    }
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 2

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesCommonRuleSet"

        # Uploads are up to 25 MB and the application enforces that limit; the rule's own body-size cap is lower,
        # so it only counts (and shows in the metrics) instead of blocking a valid upload.
        rule_action_override {
          name = "SizeRestrictions_BODY"

          action_to_use {
            count {}
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.waf_metric_prefix}_common"
    }
  }

  rule {
    name     = "SignInRateLimit"
    priority = 3

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit                 = var.waf_login_rate_limit
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"

        scope_down_statement {
          byte_match_statement {
            search_string         = "/api/v1/auth/"
            positional_constraint = "STARTS_WITH"

            field_to_match {
              uri_path {}
            }

            # Match the path the application sees: %61uth/ and /./ must not slip past the prefix.
            text_transformation {
              priority = 0
              type     = "URL_DECODE"
            }

            text_transformation {
              priority = 1
              type     = "NORMALIZE_PATH"
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      sampled_requests_enabled   = true
      metric_name                = "${local.waf_metric_prefix}_sign_in_rate_limit"
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    sampled_requests_enabled   = true
    metric_name                = "${local.waf_metric_prefix}_web_acl"
  }
}

resource "aws_cloudwatch_log_group" "waf" {
  provider = aws.us_east_1

  name              = local.waf_log_group_name
  retention_in_days = var.log_retention_days
}

# A policy of our own, scoped to this log group, instead of the shared AWSWAF-LOGS policy that AWS WAF would
# otherwise create and grow until it exceeds the size limit.
data "aws_iam_policy_document" "waf_logs" {
  statement {
    sid       = "AllowWafDelivery"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.waf.arn}:*"]

    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:${data.aws_partition.current.partition}:logs:us-east-1:${data.aws_caller_identity.current.account_id}:*"]
    }
  }
}

resource "aws_cloudwatch_log_resource_policy" "waf" {
  provider = aws.us_east_1

  policy_name     = local.waf_log_group_name
  policy_document = data.aws_iam_policy_document.waf_logs.json
}

resource "aws_wafv2_web_acl_logging_configuration" "cloudfront" {
  provider = aws.us_east_1

  resource_arn            = aws_wafv2_web_acl.cloudfront.arn
  log_destination_configs = [aws_cloudwatch_log_group.waf.arn]

  # Credentials never reach the log.
  redacted_fields {
    single_header {
      name = "authorization"
    }
  }

  redacted_fields {
    single_header {
      name = "cookie"
    }
  }

  depends_on = [aws_cloudwatch_log_resource_policy.waf]
}
