# RG-1 progress: legal time and status model

Branch `rg-legal-time`, merged with `origin/main` (MODE-1 included) before the final run. Evidence
class: fixtures (fictional data, `FakeLlmClient`, no network). Decisions: `docs/decisions/RG-1.md` (D-551 to D-559).

## Done
- V025: legal status, dates and `dates_basis` on `rule_version`; append-only `rule_version_status` with forced RLS;
  view `rule_version_current`; `CHANGED_QUEUED` checks; `assessment.assessment_as_of`.
- Monitor: a changed source makes a draft with no effective date, status `UNKNOWN` and `observed_at`; every change seen
  while a draft waits is stored and listed behind it, oldest first.
- Approval needs a recorded final status and effective date (409 otherwise) and pins it as a status row.
- `POST /rule-versions/{id}/status` (REVIEWER, ADMIN), audited `RULE_STATUS_RECORDED`.
- Assessments select the rule in force on `assessment_as_of` (settable per request); "No rule in force on <date>" stops
  the start; the overview API reports the rule in force and any upcoming one per target state.
- Seed: fictional dates, status and basis. Frontend: plain-text status, dates and kept changes; entry form fields;
  readiness mirrors the server; audit sentences.

## Tests
New: `LegalTimeApiTest` (16), `Rule5_RuleVersionStatusImmutabilityTest`, `Rule7_RuleVersionStatusIsolationTest`, the
A, B, C case in `CheckRuleSourceTest`, rows in `InputValidationTableTest`, `Rule9_AuditCoverageTest`, frontend specs.
Existing tests were updated for the new request fields and outcome names; none was weakened.

`LiveTenantApprovalTest`: the MODE-1 guard now refuses a LIVE approval whose dates basis is fictional demo data.

## Remaining unknowns
- No screen records a status for a monitored draft, so such a draft cannot be approved from the interface until UX-1;
  the API does it.
- After a waiting draft is decided, the queued observations behind it are not drafted one by one (D-555).
- No qualified licensure expert has reviewed any status or date; seed data is fictional.
