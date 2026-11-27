## INFRA-2 — Remote state, WAF, flow logs and private-range egress

Branch `infra-hardening`, decisions D-463 to D-470 in `docs/decisions/INFRA-2.md`. Terraform only: nothing was planned
against an account or applied.

### Spec points
1. **Remote state** (D-463): `backend "s3" {}` in `versions.tf`, `infra/backend.hcl.example`, `backend.hcl` ignored,
   `init -backend=false` kept; bootstrap and `init -backend-config=backend.hcl` in `docs/OPERATIONS.md`.
2. **WAF** (D-464 to D-466): alias `aws.us_east_1`, `infra/waf.tf` with the four rules in order and the
   `SizeRestrictions_BODY` count override, `web_acl_id` on the distribution, logging to
   `aws-waf-logs-<name>` with its own resource policy, `authorization` and `cookie` redacted, `waf_login_rate_limit`.
3. **Flow logs** (D-467): `infra/flow_logs.tf`, delivery role, key policy statement for CloudWatch Logs limited by
   encryption context.
4. **Egress** (D-468): `infra/nacl.tf`, the specified outbound and inbound rules, link-local caveat in a comment and in
   `docs/OPERATIONS.md`.
5. **Tests** (D-470): eight new runs in `infra/tests/stack.tftest.hcl`.
6. **Docs**: "Deploy on AWS (Terraform)" section in `docs/OPERATIONS.md`; the F12 deployment note and the WS-EVAL
   Terraform sentence removed from the roadmap's follow-ups.

### Checks
- Provider arguments were checked against the schema of the installed `hashicorp/aws` 6.74.0
  (`terraform providers schema -json`) and its registry documentation for that version.
- `make infra-validate`: format check, `init -backend=false`, validate, `terraform test` (16 runs).
- `make backend-test`: see the pull request.

### Deviations from the spec
None. Added beyond it, because the resources need them: a CloudWatch Logs resource policy for the WAF log group
(D-466), text transformations on the rate rule's path match (D-465) and the account-root statement kept in the key
policy (D-467).
