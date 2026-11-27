# Lexvay — Product rules and how each one is enforced

The nine product rules come from the specification (section 2). Each rule is enforced at the lowest
layer that can enforce it (database first, then service code, then the API, then the UI) and each has
at least one automated test that fails if the rule is broken. Test class names below are the contract:
when a milestone lands, these classes must exist and must be green.

Status column: `planned` until the milestone that builds it is done; then `enforced` with the milestone.

| # | Rule | Enforcement layers | Tests (class names) | Milestone | Status |
|---|---|---|---|---|---|
| 1 | The system never makes a determination. Only three proposed findings; only a human sets a confirmed gap; only a signer signs. | DB: `CHECK (finding IN ('EVIDENCE_FOUND','EVIDENCE_MISSING','AMBIGUOUS'))` on `mapping`. `GAP_CONFIRMED` exists only as a `review_event.kind`. Service: `LlmClient` output schema allows only the three values; anything else is rejected. API: `POST /programs/{id}/determinations` requires `SIGNER`. | `Rule1_FindingValuesTest` (DB rejects a fourth value; model output with `CONFIRMED_GAP` is rejected), `Rule1_OnlyHumansConfirmGapTest`, `Rule1_OnlySignerSignsTest` | 4, 5 | enforced since M5 (DB CHECK, matcher rejection, REOPENED system-only, SIGNER-only signing) |
| 2 | No evidence without a verified quote. | Service: `QuoteVerifier` runs on every `EVIDENCE_FOUND`/`AMBIGUOUS` proposal before persistence; failure → `EVIDENCE_MISSING`, rationale "No verifiable passage found". DB: `CHECK ((finding = 'EVIDENCE_MISSING') OR evidence_span_id IS NOT NULL)` on `mapping` for `method = 'MATCH'`; `evidence_span` has `CHECK (char_end > char_start)`. | `Rule2_QuoteVerifierTest` (exact, whitespace-variant, newline-wrapped, fabricated, empty, cross-page), `Rule2_FabricatedQuoteDiscardedTest` (scripted model returns a quote that is not in the document) | 4 | enforced since M4 |
| 3 | Numbers are checked by code. | Code: `RulesEngine` handles `NUMERIC` and `SUPERVISED_HOURS`; `LlmClient` is never called for those types. Architecture test: package `com.lexvay.assessment.rules` has no dependency on `com.lexvay.llm`. | `Rule3_RulesEngineTest` (operators, missing fact, calculation string), `Rule3_NoModelCallForNumericTest` (FakeLlmClient call counter stays at zero), `Rule3_ArchitectureTest` | 4 | enforced since M4 |
| 4 | When unsure, say ambiguous. | Code: non-required course version → forced `AMBIGUOUS`; quote shorter than the configured minimum → `AMBIGUOUS`; prompt instructs the model to prefer `AMBIGUOUS`. Eval: `make eval` fails if false `EVIDENCE_FOUND` rate exceeds `eval/thresholds.yml`. | `Rule4_ElectiveCourseForcesAmbiguousTest`, `Rule4_ThinQuoteForcesAmbiguousTest`, eval harness threshold check | 4, 9 | code guardrails enforced since M4; eval threshold gate in M9 |
| 5 | Nothing is overwritten. | DB: triggers reject `UPDATE`/`DELETE` on `document`, `document_page`, `rule_version`, `requirement` (after approval), `course_version`, `evidence_span`, `review_event`, `audit_log`, `llm_call`. `determination` allows an update to `status` only. Changes create new rows with `supersedes_id`. Service: `PUT /rule-versions/{id}/requirements` returns `409` once approved. | `Rule5_ImmutabilityTest` (one update and one delete attempt per protected table, each must fail), `Rule5_ApprovedRequirementsRejectEditsTest` (API `409` and trigger) | 1, 3, 5 | enforced since M5 (database triggers and CHECKs from M1, approved lists locked at the API from M3, no review after signing from M5) |
| 6 | A signed determination is locked and stores exact versions. | DB: `determination.rule_version_id`, `determination_course_version`, `versions_hash NOT NULL`; trigger from rule 5. Service: hash computed from sorted ids at sign time; recomputation must match. | `Rule6_DeterminationLockTest`, `Rule6_VersionsHashTest` | 5 | enforced since M5 |
| 7 | Tenants are isolated, in the database. | DB: `tenant_id` on every table except `jurisdiction`; `ENABLE` + `FORCE ROW LEVEL SECURITY`; policy `tenant_id = current_tenant()` for `USING` and `WITH CHECK`; `current_tenant()` returns NULL when the session setting is absent (fail closed); application role has `NOBYPASSRLS` and does not own the tables. Service: tenant set per connection from the authenticated user. | `Rule7_TenantIsolationApiTest` (tenant A cannot read or write B's rows through every GET/POST), `Rule7_TenantIsolationRepositoryTest` (direct repository calls), `Rule7_NoTenantFailsClosedTest`, `Rule7_EveryTableHasPolicyTest` (introspects `pg_policies`) | 1 | enforced since M1 |
| 8 | No student data. | Schema: no table or column whose name contains `student`. Documents are curriculum and rule text only. | `Rule8_NoStudentEntityTest` (introspects `information_schema`) | 1 | enforced since M1 |
| 9 | Every action is audited. | Service: `AuditService.record` inside the same transaction as every mutation; worker actions audited with `user_id` null and job id in `detail`. Guard test: the set of mutating endpoints is compared with the declared audited set so a new endpoint fails the build until covered. | `Rule9_AuditCoverageTest`, per-endpoint audit assertions in each module's integration tests | 1 onward | enforced since M1 (guard test + per-endpoint assertions) |

## UI wording rule (specification section 9)

The words "compliant" and "meets requirements" never appear in the UI, including the signing dialog
(outcome labels are "Determined to meet", "Determined not to meet", "No determination made").

- Test: `ForbiddenWordsTest` scans every `frontend/src/**/*.html` and `*.ts` file, the backend Java
  sources and prompt resources, and the seed content; the frontend unit test `labels.spec.ts` and the
  end-to-end test check the rendered text as well. Enforced since M6.

## Fictional data rule (specification section 1)

Every seed rule set is labeled "Demo rule set" and every seed document carries
"Fictional. Prototype data." in its text. The demo tenant shows a permanent banner.

- Test: `SeedIsLabeledFictionalTest` (every rule version label starts with "Demo rule set", every seed PDF
  page 1 contains "Fictional. Prototype data.", every rule text says it is not the law of any state).
  Enforced since M6.
- E2E assertion: the banner is visible after sign-in (`.banner` contains "Fictional data"). Enforced since M6.

## Security hardening (WS-SEC, decisions D-090 to D-099)

Not product rules, but each has a test that fails when it breaks. Details in `docs/decisions/WS-SEC.md`.

| Control | Enforcement | Tests |
|---|---|---|
| Single sign-on, roles from the Lexvay database, 403 without an account or with an unverified email | Profile `oidc`: `OidcLogin`, `LexvayOidcUserService` | `OidcLoginTest`, `AuthTest.singleSignOnIsOffUnlessProfileOidcIsActive` |
| Rate limits: login 10/min per address, API 600/min and uploads 30/min per user, 429 with `Retry-After`, bounded bucket cache | `RateLimitFilter`, `lexvay.ratelimit.*` | `RateLimitFilterTest`, `RateLimitApiTest`, `ForwardedHeadersTest` |
| Security headers on every response, HSTS on secure requests; session cookie HttpOnly, SameSite=Lax, Secure by configuration | `SecurityHeadersFilter`, `application.yml` | `SecurityHeadersTest`, `SessionSecurityTest`, `SecureCookieTest` |
| Upload size (413), file type, DOCX zip bomb, PDF page cap, file name | Multipart limit, `FileType`, `ContentLimits`, `DocumentService` | `UploadSizeLimitTest`, `DocumentUploadTest`, `ContentLimitsTest` |
| Malformed input is a 4xx problem detail, never a 500, on every endpoint | Bean validation, `ApiExceptionHandler` | `InputValidationTableTest` (fails when an endpoint has no row) |
| The session id changes at login (form and OIDC), login CSRF stays on | `SecurityConfig` | `SessionSecurityTest`, `OidcLoginTest`, `AuthTest` |
