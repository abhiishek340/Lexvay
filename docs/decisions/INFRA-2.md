### INFRA-2 — Remote state, WAF, flow logs and private-range egress (D-463 to D-470)

**D-463 — Remote state is a partial S3 backend with native locking; the bucket is bootstrapped outside the stack.**
`versions.tf` declares `backend "s3" {}` with no values; `infra/backend.hcl.example` carries placeholders for `bucket`,
`key`, `region`, `encrypt = true`, `kms_key_id` and `use_lockfile = true` (the lock is an object beside the state, so
there is no DynamoDB table and no second resource to bootstrap). `backend.hcl` is in `.gitignore`. `make infra-validate`
keeps `init -backend=false`, so validation and the offline tests never need a bucket or credentials.
`docs/OPERATIONS.md` gives the one-time bootstrap (versioning, SSE-KMS with a rotated key, public access block,
TLS-only bucket policy) as AWS CLI commands. Rejected: a Terraform-managed state bucket (the stack would store its own
state in a bucket it can destroy); a DynamoDB lock table (deprecated by the backend's own lock file).

**D-464 — The web ACL is four rules in fixed priority order, with one override.** `waf.tf` creates the ACL with scope
`CLOUDFRONT`, default allow, through the provider alias `aws.us_east_1` (the only region CloudFront ACLs exist in).
Priorities 0 to 3: `AWSManagedRulesAmazonIpReputationList`, `AWSManagedRulesKnownBadInputsRuleSet`,
`AWSManagedRulesCommonRuleSet` (with `rule_action_override` of `SizeRestrictions_BODY` to `count`: uploads are up to
25 MB and the application enforces that limit), then the sign-in rate rule. The managed groups use
`override_action { none {} }`, so each keeps its own actions. The ACL is attached with `web_acl_id` on the
distribution. Every rule and the ACL emit CloudWatch metrics and sampled requests, named from `local.name`.

**D-465 — The sign-in rate rule counts per IP over 5 minutes on a normalised path.** `rate_based_statement` with
`limit = var.waf_login_rate_limit` (default 300, validated 100 to 20000 and whole), `evaluation_window_sec = 300`
written out rather than relying on the provider default, `aggregate_key_type = "IP"` (WAF sees the viewer's address
because it sits on CloudFront), action `block`. The scope-down is a byte match, `STARTS_WITH` `/api/v1/auth/` on the URI
path, with `URL_DECODE` then `NORMALIZE_PATH` applied first: the application decodes and normalises the path before
routing, so `/api/v1/%61uth/` or `/api/v1/./auth/` must not escape a prefix match on the raw string. WAF requires at
least one text transformation, so choosing them is part of the rule, not a change of the spec. The rule complements the
Argon2 gate and does not replace it.

**D-466 — WAF logs go to a log group with a resource policy of our own, and the group is not KMS-encrypted.** The group
is `aws-waf-logs-${local.name}` in us-east-1 (the prefix is mandatory), retention `var.log_retention_days`. Without a
policy of our own AWS WAF writes to a shared `AWSWAF-LOGS` policy that grows until it exceeds the size limit and then
fails every new ACL; `aws_cloudwatch_log_resource_policy` grants `delivery.logs.amazonaws.com` `CreateLogStream` and
`PutLogEvents` on this group only, conditioned on `aws:SourceAccount` and the account's us-east-1 log ARNs. The
`authorization` and `cookie` headers are redacted (lowercase names, as the provider documents). The data key is in the
stack's region and a KMS key cannot protect a log group in another region, so this group keeps CloudWatch's default
encryption. Rejected: a second key in us-east-1 (a second thing to operate for a log that never holds credentials).

**D-467 — Flow logs are encrypted with the data key, and the key policy is written out.** `aws_flow_log` on the VPC,
`traffic_type = "ALL"`, to a CloudWatch log group (`/<project>/<environment>/vpc-flow-logs`) with
`kms_key_id = aws_kms_key.data.arn` and the configured retention. The delivery role trusts only
`vpc-flow-logs.amazonaws.com`, with `aws:SourceAccount`, and may write only to that group. CloudWatch Logs needs the key
policy's permission to use the key, so `aws_kms_key.data` now has an explicit policy: the account-root statement that
the default policy gave (it is what lets the task roles, RDS, S3 and Secrets Manager use the key through IAM, so
dropping it would lock them out), and a statement for `logs.<region>.amazonaws.com` conditioned with `ArnEquals` on
`kms:EncryptionContext:aws:logs:arn` equal to the flow log group's ARN. That ARN is built from the group name in a
local, because reading it from the resource would make the key depend on the group and the group on the key.

**D-468 — The network ACL uses one resource per rule, and link-local stays outside it.** `aws_network_acl.private` is
associated with every private subnet; its rules are `aws_network_acl_rule` resources (an `egress` map keyed by rule
number, plus the two ingress rules), not inline blocks, so a rule change is a one-rule diff and the two styles never
fight. Outbound: 100 allow the VPC CIDR, 110 to 140 deny `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`,
`100.64.0.0/10`, 200 allow `0.0.0.0/0`. Inbound: 100 allow the VPC CIDR, 200 allow TCP 1024 to 65535 from
`0.0.0.0/0` (return traffic; ACLs are stateless). The VPC allow is first, so a VPC that itself sits inside one of the
denied ranges (the default `10.20.0.0/16` does) keeps working. Link-local addresses, the ECS credentials endpoint
included, never pass a network ACL; this is stated in a comment in `nacl.tf` and in `docs/OPERATIONS.md`, and
`SsrfGuard` remains the control for them. Rejected: a deny for `169.254.0.0/16` (it would have no effect and would
suggest a control that does not exist).

**D-469 — Both AWS providers share one `default_tags` local.** The us-east-1 alias must tag its resources like the
primary provider, so the map moved to `local.default_tags` in `versions.tf` and both provider blocks read it.

**D-470 — The offline tests compare overridden values, and the roadmap edit is limited to the Terraform sentence.**
A plan leaves generated ids and ARNs unknown, and the test runner refuses to evaluate a condition on two unknowns, so
`stack.tftest.hcl` gives the web ACL's ARN, the data key's ARN, the flow log group's ARN, the private network ACL's id
and the two private subnet ids a fixed value with `override_resource` and `override_during = plan`. The
aliased provider has its own `mock_provider`. Set-typed blocks (rules, policy statements) are checked with `for`
expressions, since sets cannot be indexed. The new runs cover the ACL attachment, rule order and the body-size
override, the sign-in rule, the log group and redaction, the rate-limit bounds (99 and 20001 rejected), the flow log and
its trust and key policy, and the network ACL rules and association. In `docs/ROADMAP.md` the F12 deployment note was
removed whole; from the WS-EVAL line only the Terraform sentence was removed, because the rest of that bullet (the
unmeasured thresholds, the database role bootstrap) is still open.
