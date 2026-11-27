# Offline checks of the stack. Both providers are mocked, so no credentials are used and no resource is created:
# these runs evaluate variable validation and the expressions in the configuration, nothing more.

mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["zone-a", "zone-b", "zone-c"]
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "000000000000"
    }
  }

  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

# CloudFront web ACLs live in us-east-1, so the stack declares a second provider (aws.us_east_1).
mock_provider "aws" {
  alias = "us_east_1"

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

mock_provider "random" {}

# A plan leaves computed values unknown, so two references to the same generated id or ARN cannot be compared.
# These overrides give the ones the assertions below compare a fixed value during the plan.
override_resource {
  target          = aws_wafv2_web_acl.cloudfront
  override_during = plan
  values = {
    arn = "arn:aws:wafv2:us-east-1:000000000000:global/webacl/lexvay-prod/00000000-0000-0000-0000-000000000000"
  }
}

override_resource {
  target          = aws_kms_key.data
  override_during = plan
  values = {
    arn = "arn:aws:kms:us-test-1:000000000000:key/00000000-0000-0000-0000-000000000000"
  }
}

override_resource {
  target          = aws_cloudwatch_log_group.flow
  override_during = plan
  values = {
    arn = "arn:aws:logs:us-test-1:000000000000:log-group:/lexvay/prod/vpc-flow-logs"
  }
}

override_resource {
  target          = aws_network_acl.private
  override_during = plan
  values = {
    id = "acl-private"
  }
}

override_resource {
  target          = aws_subnet.private[0]
  override_during = plan
  values = {
    id = "subnet-private-0"
  }
}

override_resource {
  target          = aws_subnet.private[1]
  override_during = plan
  values = {
    id = "subnet-private-1"
  }
}

variables {
  environment     = "prod"
  region          = "us-test-1"
  api_domain_name = "api.example.org"
  certificate_arn = "arn:aws:acm:us-test-1:000000000000:certificate/00000000-0000-0000-0000-000000000000"
  backend_image   = "registry.example.org/lexvay/backend:1.0.0"
}

run "defaults_are_private_encrypted_and_versioned" {
  command = plan

  assert {
    condition     = length(aws_subnet.private) == 2 && length(aws_subnet.public) == 2
    error_message = "two availability zones should give two public and two private subnets"
  }

  assert {
    condition     = length(aws_nat_gateway.main) == 1
    error_message = "a single NAT gateway is the default"
  }

  assert {
    condition     = aws_db_instance.main.publicly_accessible == false && aws_db_instance.main.storage_encrypted == true
    error_message = "the database must be private and encrypted"
  }

  assert {
    condition     = aws_db_instance.main.engine == "postgres" && startswith(aws_db_instance.main.engine_version, "18")
    error_message = "the database must run PostgreSQL 18"
  }

  assert {
    condition     = aws_s3_bucket_versioning.files.versioning_configuration[0].status == "Enabled" && aws_s3_bucket_versioning.web.versioning_configuration[0].status == "Enabled"
    error_message = "both buckets must be versioned"
  }

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.files.block_public_acls,
      aws_s3_bucket_public_access_block.files.block_public_policy,
      aws_s3_bucket_public_access_block.files.ignore_public_acls,
      aws_s3_bucket_public_access_block.files.restrict_public_buckets,
      aws_s3_bucket_public_access_block.web.block_public_acls,
      aws_s3_bucket_public_access_block.web.block_public_policy,
      aws_s3_bucket_public_access_block.web.ignore_public_acls,
      aws_s3_bucket_public_access_block.web.restrict_public_buckets,
    ])
    error_message = "public access must be blocked on both buckets"
  }

  assert {
    condition     = aws_ecs_service.backend.network_configuration[0].assign_public_ip == false
    error_message = "backend tasks must not get public addresses"
  }
}

run "one_nat_gateway_per_zone_when_asked" {
  command = plan

  variables {
    az_count           = 3
    nat_gateway_per_az = true
  }

  assert {
    condition     = length(aws_nat_gateway.main) == 3 && length(aws_route_table.private) == 3
    error_message = "each zone should have its own NAT gateway and private route table"
  }
}

run "web_acl_guards_the_distribution" {
  command = plan

  assert {
    condition     = aws_cloudfront_distribution.main.web_acl_id == aws_wafv2_web_acl.cloudfront.arn
    error_message = "the distribution must use the web ACL"
  }

  assert {
    condition     = aws_wafv2_web_acl.cloudfront.scope == "CLOUDFRONT" && length(aws_wafv2_web_acl.cloudfront.default_action[0].allow) == 1
    error_message = "the web ACL must be CloudFront-scoped and allow by default"
  }
}

run "web_acl_rules_run_in_order_with_the_body_size_override" {
  command = plan

  assert {
    condition = { for r in aws_wafv2_web_acl.cloudfront.rule : tostring(r.priority) => r.name } == {
      "0" = "AWSManagedRulesAmazonIpReputationList"
      "1" = "AWSManagedRulesKnownBadInputsRuleSet"
      "2" = "AWSManagedRulesCommonRuleSet"
      "3" = "SignInRateLimit"
    }
    error_message = "the web ACL must hold the four rules in the specified order"
  }

  assert {
    condition = alltrue([
      for r in aws_wafv2_web_acl.cloudfront.rule :
      r.statement[0].managed_rule_group_statement[0].vendor_name == "AWS" && length(r.override_action[0].none) == 1
      if r.priority < 3
    ])
    error_message = "the three managed rule groups must be AWS's and keep their own actions"
  }

  assert {
    condition = [
      for o in [for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 2][0].statement[0].managed_rule_group_statement[0].rule_action_override :
      o.name if length(o.action_to_use[0].count) == 1
    ] == ["SizeRestrictions_BODY"]
    error_message = "the common rule set must count SizeRestrictions_BODY and override nothing else"
  }

  assert {
    condition = (
      [for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 3][0].statement[0].rate_based_statement[0].limit == 300
      && [for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 3][0].statement[0].rate_based_statement[0].evaluation_window_sec == 300
      && [for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 3][0].statement[0].rate_based_statement[0].aggregate_key_type == "IP"
      && length([for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 3][0].action[0].block) == 1
    )
    error_message = "the sign-in rule must block an IP after 300 requests in 5 minutes"
  }

  assert {
    condition = (
      [for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 3][0].statement[0].rate_based_statement[0].scope_down_statement[0].byte_match_statement[0].search_string == "/api/v1/auth/"
      && [for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 3][0].statement[0].rate_based_statement[0].scope_down_statement[0].byte_match_statement[0].positional_constraint == "STARTS_WITH"
    )
    error_message = "the sign-in rule must match the path /api/v1/auth/ by prefix"
  }
}

run "web_acl_log_keeps_credentials_out" {
  command = plan

  variables {
    log_retention_days = 30
  }

  assert {
    condition     = aws_cloudwatch_log_group.waf.name == "aws-waf-logs-lexvay-prod" && aws_cloudwatch_log_group.waf.retention_in_days == 30
    error_message = "the web ACL log group must carry the aws-waf-logs- prefix and the configured retention"
  }

  assert {
    condition     = toset([for f in aws_wafv2_web_acl_logging_configuration.cloudfront.redacted_fields : f.single_header[0].name]) == toset(["authorization", "cookie"])
    error_message = "the authorization and cookie headers must be redacted"
  }
}

run "rate_limit_follows_the_variable" {
  command = plan

  variables {
    waf_login_rate_limit = 1000
  }

  assert {
    condition     = [for r in aws_wafv2_web_acl.cloudfront.rule : r if r.priority == 3][0].statement[0].rate_based_statement[0].limit == 1000
    error_message = "the sign-in limit must come from waf_login_rate_limit"
  }
}

run "rejects_a_login_rate_limit_below_100" {
  command = plan

  variables {
    waf_login_rate_limit = 99
  }

  expect_failures = [var.waf_login_rate_limit]
}

run "rejects_a_login_rate_limit_above_20000" {
  command = plan

  variables {
    waf_login_rate_limit = 20001
  }

  expect_failures = [var.waf_login_rate_limit]
}

run "flow_log_records_all_traffic_into_an_encrypted_log_group" {
  command = plan

  assert {
    condition     = aws_flow_log.vpc.traffic_type == "ALL" && aws_flow_log.vpc.log_destination_type == "cloud-watch-logs"
    error_message = "the VPC flow log must record all traffic to CloudWatch Logs"
  }

  assert {
    condition     = aws_flow_log.vpc.log_destination == aws_cloudwatch_log_group.flow.arn
    error_message = "the flow log must deliver to the flow log group"
  }

  assert {
    condition     = aws_cloudwatch_log_group.flow.kms_key_id == aws_kms_key.data.arn && aws_cloudwatch_log_group.flow.retention_in_days == 90
    error_message = "the flow log group must be encrypted with the data key and keep logs for log_retention_days"
  }

  assert {
    condition = (
      flatten([for s in data.aws_iam_policy_document.flow_logs_assume.statement : [for p in s.principals : tolist(p.identifiers)]]) == ["vpc-flow-logs.amazonaws.com"]
      && [for s in data.aws_iam_policy_document.flow_logs_assume.statement : [for c in s.condition : c.variable]] == [["aws:SourceAccount"]]
    )
    error_message = "only the flow log service of this account may assume the delivery role"
  }

  assert {
    condition = [
      for s in data.aws_iam_policy_document.data_key.statement :
      [for c in s.condition : "${c.test} ${c.variable} ${join(",", c.values)}"] if s.sid == "CloudWatchLogsEncryptsTheFlowLogGroup"
    ] == [["ArnEquals kms:EncryptionContext:aws:logs:arn arn:aws:logs:us-test-1:000000000000:log-group:/lexvay/prod/vpc-flow-logs"]]
    error_message = "CloudWatch Logs may use the data key only for the flow log group"
  }
}

run "private_subnets_cannot_open_connections_to_private_ranges" {
  command = plan

  assert {
    condition = [
      for n in sort(keys(aws_network_acl_rule.egress)) :
      "${n} ${aws_network_acl_rule.egress[n].rule_action} ${aws_network_acl_rule.egress[n].cidr_block}"
      ] == [
      "100 allow 10.20.0.0/16",
      "110 deny 10.0.0.0/8",
      "120 deny 172.16.0.0/12",
      "130 deny 192.168.0.0/16",
      "140 deny 100.64.0.0/10",
      "200 allow 0.0.0.0/0",
    ]
    error_message = "egress must allow the VPC, deny the four private ranges, then allow the rest"
  }

  assert {
    condition     = alltrue([for r in aws_network_acl_rule.egress : r.egress && r.protocol == "-1" && r.network_acl_id == aws_network_acl.private.id])
    error_message = "every egress rule must cover all protocols on the private network ACL"
  }

  assert {
    condition = (
      aws_network_acl_rule.ingress_from_vpc.rule_number == 100 && aws_network_acl_rule.ingress_from_vpc.rule_action == "allow" && aws_network_acl_rule.ingress_from_vpc.cidr_block == "10.20.0.0/16"
      && aws_network_acl_rule.ingress_return_traffic.rule_number == 200 && aws_network_acl_rule.ingress_return_traffic.protocol == "tcp"
      && aws_network_acl_rule.ingress_return_traffic.from_port == 1024 && aws_network_acl_rule.ingress_return_traffic.to_port == 65535
      && aws_network_acl_rule.ingress_return_traffic.cidr_block == "0.0.0.0/0" && !aws_network_acl_rule.ingress_from_vpc.egress && !aws_network_acl_rule.ingress_return_traffic.egress
    )
    error_message = "ingress must allow the VPC, then TCP 1024-65535 from anywhere for return traffic"
  }

  assert {
    condition     = aws_network_acl.private.subnet_ids == toset(["subnet-private-0", "subnet-private-1"])
    error_message = "the network ACL must be associated with every private subnet"
  }
}

run "rejects_unknown_environment" {
  command = plan

  variables {
    environment = "test"
  }

  expect_failures = [var.environment]
}

run "rejects_the_latest_image_tag" {
  command = plan

  variables {
    backend_image = "registry.example.org/lexvay/backend:latest"
  }

  expect_failures = [var.backend_image]
}

run "rejects_a_certificate_that_is_not_acm" {
  command = plan

  variables {
    certificate_arn = "arn:aws:iam::000000000000:server-certificate/example"
  }

  expect_failures = [var.certificate_arn]
}

run "rejects_a_database_that_is_not_postgresql_18" {
  command = plan

  variables {
    db_engine_version = "17.5"
  }

  expect_failures = [var.db_engine_version]
}

run "rejects_a_storage_limit_below_the_initial_size" {
  command = plan

  variables {
    db_allocated_storage     = 100
    db_max_allocated_storage = 50
  }

  expect_failures = [var.db_max_allocated_storage]
}

run "rejects_a_vpc_too_small_to_split" {
  command = plan

  variables {
    vpc_cidr = "10.20.0.0/24"
  }

  expect_failures = [var.vpc_cidr]
}
