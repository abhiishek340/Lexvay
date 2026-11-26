# WS-EVAL decisions (D-100 to D-109)

Work item WS-EVAL in `docs/ROADMAP.md`: evaluation harness and Terraform. Each entry names what was rejected.
Checked 2026-11-26.

**D-100 — The evaluation is a Spring profile run by `scripts/eval.sh`.** `make eval` and `make eval-live` start an
isolated PostgreSQL through docker compose (project `lexvay-eval-<slot>`, port 5433 + `LEXVAY_E2E_SLOT`), package the
backend, run the jar with `--spring.profiles.active=eval` and tear the database down. The runner (`EvalRunner`) prints
the table and exits 0, 1 (a limit is broken) or 2 (the run itself failed). The scoring is in `EvalService` (no
profile), so the same code runs in `EvalServiceTest` against Testcontainers. Rejected: a JUnit-only harness (no
`make` entry point, no live mode); a separate Maven module (a second build for one runner); a CLI that talks to a
running server over HTTP (it could not substitute the golden list through the service layer, and the job queue would
have to be polled).

**D-101 — Scripting the fake model is a `ModelStrategy`.** `SeedLoader` no longer injects `LlmClient` or
`ObjectMapper` and no longer checks `instanceof FakeLlmClient`. `ModelStrategy` has three hooks:
`beforeExtraction(label)`, `afterExtraction(jurisdiction, ruleVersionId)` (as the reviewer, before approval) and
`beforeMatching(documentByFile)`. `ScriptedFakeModel` (reads `seed/fake-llm.yml`) is the strategy for `make seed`
and tests, `ModelStrategy.NONE` leaves a real model alone, `EvalModel` records extraction and, in live mode,
substitutes the golden list. Creating, extracting and approving a rule version is one private method used for the
initial and the superseding rule sets (it was written out twice). `load(seedDir, password, strategy)` replaces
`load(seedDir, password)`; `loadTenant` and `manifest` are public so the evaluation can seed one tenant. `YamlFile`
(`com.lexvay.common`) is the single YAML reader. `FakeLlmClient.replaceScript` replaces a key's queue: `script` appends,
so a second load of the same data in one JVM was answered with the first load's document ids. Rejected: a subclass of
the loader; a boolean `scriptModel` flag; leaving the `instanceof` check and adding a second loader for the evaluation
(duplicated loading code, which the spec forbids).

**D-102 — Golden file.** `eval/golden.yml` is keyed by `jurisdiction/ref` (a ref may repeat across rule sets) and holds, for
each of the 42 requirements, `title`, `text`, `type`, numeric definition, `finding`, `document`, `quoteContains` and
a human `reason`. Title, text and numeric definition are needed because live mode replaces the model's list with
the golden one through `RuleVersionService.replaceRequirements`. It was written from `seed/rules` and `seed/docs`, not
from `seed/fake-llm.yml`; `GoldenFileTest` checks every cited file exists and every `quoteContains` appears in the
seed text. Fictional, labelled `fictional: true`, and `EvalService` refuses a file that is not. Expected mix: 33
EVIDENCE_FOUND (11 numeric by the rules engine + 22 by the matcher), 6 AMBIGUOUS, 3 EVIDENCE_MISSING. (The roadmap's
"22 evidence found" counts the matcher's findings; the 11 rules-engine ones are separate.) Rejected: generating the
golden set from the fake scripts (it would test nothing); only `ref -> finding` (nothing to score citations with, and no
list to substitute in live mode).

**D-103 — Metrics.** The five metrics are as defined in the roadmap. Details the definition leaves open: proposals with no
golden entry count as "golden differs"; the false rate counts every proposed EVIDENCE_FOUND, rules-engine ones
included (33 in the seed); numeric accuracy requires `method = RULES_ENGINE` and the golden finding; extraction
agreement compares the model's own list (captured before any substitution) by jurisdiction, ref and type; quote
verification runs `QuoteVerifier.find` over every stored `evidence_span` of the tenant (program-fact spans
included) against the stored `document_page`. One metric is added: evidence agreement (golden entries naming a
document whose cited file and quote substring match, out of those entries). Without it `document` and
`quoteContains` in the golden file would not be used and a correct finding citing the wrong passage would pass.
Every disagreement is listed under the table. Rejected: counting only matcher proposals in the false rate (hides
rules-engine errors); a fuzzy quote comparison (rule 2 forbids it).

**D-104 — Thresholds.** `eval/thresholds.yml` has `fake` and `live` sections. Quote verification is not in the file: it
must be 100 % and is fixed in code. `fake` is exact (false rate 0, everything else 100 %), so drift between the fake
scripts, the pipeline and the golden set fails the build. `live`: false EVIDENCE_FOUND 0 and numeric accuracy 100 %,
which need no measurement; recall 0.80, extraction agreement 0.90 and evidence agreement 0.80 are first guesses made
without a live run (no API key was available) and must be tightened after the first real run. An unmeasurable
metric (zero denominator) fails, except the false rate when nothing was proposed. Rejected: one threshold set
(a real model cannot be held to exact reproduction); thresholds as Java constants (the spec names the file).

**D-105 — Tokens, cost and time.** Input and output tokens and call count are summed from `llm_call` for the
evaluation tenant (row-level security scopes the query), extraction included. Cost = tokens times
`lexvay.llm.price.*` (`LEXVAY_LLM_PRICE_INPUT_PER_MTOK`, `LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK`); when neither price is set it
prints "n/a" rather than a free-looking 0. Fake token counts are estimates from text length and the report says so.
Wall time is the assessment's `finished_at - started_at`, the pipeline stage the roadmap asks about, not process
start-up. There is one assessment per run, so the totals are per assessment. Rejected: placeholder default prices in
`application.yml` (invented money); timing the whole process (includes Flyway and JVM start).

**D-106 — Live mode.** `make eval-live` sets `LEXVAY_LLM_PROVIDER=anthropic` only when `ANTHROPIC_API_KEY` is set;
otherwise it prints why and exits 0. `make eval` unsets the key so a scripted run can never reach the network. The
live path (no scripts, golden substitution before approval) is covered by `EvalModelTest`, which drives the same
`EvalModel` with the scripted model; it has not run against the real model. Tests never call a real model.

**D-107 — Isolation.** Each run seeds the first demo tenant under a unique name and unique user emails
(`SeedManifest.Tenant.isolated`) and without the change-tracking demo, so it can run against a database that
already holds the demo seed. The database is a fresh compose project `lexvay-eval-<slot>` removed on exit; the
volume is removed with it. `scripts/eval.sh` repeats the six lines of port and cleanup convention from `scripts/e2e.sh`
instead of both sourcing a shared file, to avoid editing a script other work items touch; fold them together when
convenient. Eval and end-to-end use the same ports for the same slot, so they must not run at the same time in one
checkout.

**D-108 — Terraform design (`infra/`, AWS, no modules).** Plain resources, so every line is reviewable and the only
providers are `aws` and `random`.
- Network: one VPC, a public and a private subnet per zone (2 by default), one NAT gateway by default
  (`nat_gateway_per_az` for one per zone), S3 gateway endpoint, default security group emptied. Backend and database are
  only in private subnets. Rejected: private subnets without NAT (the backend must reach the model API and ECR);
  VPC flow logs and WAF (not in the spec, follow-up for F6).
- Entry: CloudFront serves the Angular build from a private S3 bucket through origin access control (versioned,
  SSE-S3, public access blocked) and sends `/api/*` to a public ALB so the browser has one origin (session cookie and
  CSRF keep working). The ALB serves HTTPS only (ACM certificate variable), accepts only CloudFront's address list,
  and returns 403 unless the request carries a secret header the distribution adds. A CloudFront Function rewrites
  extensionless paths to `/index.html`. Rejected: CloudFront custom error responses for SPA routing (they would also
  rewrite API 403/404 into the page); a CloudFront VPC origin with an internal ALB (newer, needs service-managed
  security groups that cannot be checked without applying); ALB open to the internet; HTTP listener.
- Database: RDS PostgreSQL 18, KMS-encrypted, not publicly accessible, Multi-AZ, 14-day backups, deletion
  protection and final snapshot by default, TLS enforced, master password managed by RDS in Secrets Manager.
  Rejected: a master password variable (secret in a variable file).
- Files bucket: versioned, SSE-KMS with the data key, public access blocked, TLS-only policy, `BucketOwnerEnforced`.
- Secrets Manager: `database` (random passwords of the owner and app roles, in Terraform state) and
  `anthropic-api-key` (entry only, value set with the CLI so the key is never in state). Injected as environment
  variables by the execution role.
- IAM: execution role (managed policy, read two secrets, decrypt the key via Secrets Manager only) and a separate task
  role (get/put objects in the files bucket, list that bucket, use the key via S3 only).
- Variables have descriptions and validation, none hold secrets or account ids (the account id is read from the
  provider where bucket names need it). `backend_image` rejects `:latest`.
- No remote state backend is configured: the choice (bucket, locking, who may read it) belongs to the deployer, and
  the state holds the generated passwords.
Not done and called out: creating the database roles. RDS has no `lexvay_owner` or `lexvay_app` until
`db/initdb/01-roles.sh` (or equivalent) is run with the administrator secret; the passwords it needs are in the
`database` secret. This is a deployment step, not Terraform, and it has not been tried.

**D-109 — Pins and what was verified.** Terraform 1.17.2 (releases.hashicorp.com lists it as newest; installed from the
official darwin_arm64 zip after checking its SHA-256 against the published `SHA256SUMS`, because
`brew install hashicorp/tap/terraform` stopped at "Command Line Tools are too outdated"; the signature could not be
checked, no `gpg`). `hashicorp/aws` 6.74.0 (registry, published 2026-11-23), `hashicorp/random` 3.9.1 (2026-09-11), both
exact in `versions.tf` and locked for linux_amd64, linux_arm64, darwin_arm64 and darwin_amd64 in
`infra/.terraform.lock.hcl`. CI: `hashicorp/setup-terraform@v4` (v4.0.1, 2026-05-12, runs on node24), with
`terraform_version: 1.17.2` and `terraform_wrapper: false`; a separate `infra` job so it runs beside the tests.
`make infra-validate` runs `fmt -check`, `init -backend=false`, `validate` and `terraform test`. The tests
(`infra/tests/stack.tftest.hcl`) use mocked providers, so they need no credentials and create nothing; they check the
variable validations and the private, encrypted, versioned and public-access-blocked properties. `plan` and `apply`
were never run. RDS: PostgreSQL 18 is generally available (18.7 is listed in the RDS release notes); the parameter
group family `postgres18` follows RDS naming and could not be checked without credentials; `db_engine_version`
defaults to the major version `18`. The CloudFront and ALB wiring has been validated for syntax and schema only, not
deployed.
