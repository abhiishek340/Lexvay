# RG-1 decisions: legal time and status model (D-551 to D-559)

Context: the intake of 2026-11-27 (`docs/INTAKE-2026-11-27.md`) confirmed two defects. The rule-source monitor wrote
the fetch date as a rule's legal effective date, and a change seen while a draft waited was discarded. Unknown stays
unknown, and nothing becomes law because a crawler saw it.

## D-551 The fetch date is an observation, never a legal date
A changed rule source creates a draft with `effective_from NULL`, `legal_status UNKNOWN`, no `dates_basis`, and
`observed_at` equal to the `checked_at` of the check that saw it (one value, passed to both inserts). Its label is
"<source label>, observed <date>"; "fetched" no longer appears as if it were a date in law. `observed_at` is null for an
upload. Test: `CheckRuleSourceTest.aChangedPageCreatesAnUnapprovedDraft...`.

## D-552 Dates and status are never edited in place
`rule_version` keeps only the creation values (V025 adds `effective_to`, `published_at`, `adopted_at`, `legal_status`,
`dates_basis`, `observed_at`; `effective_from` becomes nullable). The V003 trigger already rejects any change but
approval, so the new columns are immutable without further code. A correction is a new row in `rule_version_status`
(full position, not a difference; `recorded_by`, `recorded_at`), append-only through `forbid_change()`, with forced
row-level security and a tenant policy like every table. The view `rule_version_current` (`security_invoker = true`, so
the caller's RLS applies) joins each version to its newest status row, or to its creation values while there is none,
and every reader (`RuleVersionRepository`, the determination register) reads that view. Ties are impossible to confuse:
a correction always outranks the creation row, then newest `recorded_at`, then id.
CHECKs: a date requires `dates_basis` (also checked in `LegalPosition`, so the API answers 400 with words);
`effective_to > effective_from` when both are set. A lone end date is allowed by the database, as the spec's CHECK
reads; selection never uses a version without an effective date.

## D-553 Approval needs a recorded, final status with dates
`POST .../requirements/approve` answers 409 unless the current status is `IN_FORCE` or `FINAL_NOT_EFFECTIVE` and an
effective date is recorded (a date always carries its basis). `PROPOSED`, `WITHDRAWN`, `REPEALED`, `SUPERSEDED` and
`UNKNOWN` cannot be approved. On approval the current position is appended as a status row signed by the approver, even
when an earlier correction already exists: the approval then names the exact dates it approved, and a later correction
is a row after it. Only a person records status, so a monitored draft stays unapprovable until a reviewer does.
MODE-1's fail-closed hook (`RealDataApprovalGuard`, D-561) now reads the current `dates_basis` of the version: in a LIVE
tenant a version is approved only when a basis is recorded and it does not say "Fictional demo data"; a DEMO tenant is
unchanged (`LiveTenantApprovalTest`). The migration is numbered V025 (V022 is MODE-1, V023 SEC-3, V024 AC-1).

## D-554 Backfill
Existing rows get `legal_status = 'IN_FORCE'` when approved, else `'UNKNOWN'`, and `dates_basis = 'Entered at creation
before V025'`; their `effective_from` is kept. The backfill needs to write rows of every tenant and to bypass the
approval-only trigger of V003, so the migration switches `rule_version` (and `assessment`, for D-556) to NO FORCE row
security and disables that one trigger for its own duration and restores both in the same transaction, as V016 does.
`rule_source_check.outcome` historic `DRAFT_EXISTS` rows are immutable and stored nothing, so the value stays valid in
the CHECK and in `CheckOutcome` and is documented as no longer written.

## D-555 Every observed change is kept (A, B, C)
When the text changed and a draft already waits, the handler stores the document and records a `CHANGED_QUEUED` check
whose `rule_version_id` is the waiting draft. `CHANGED` and `CHANGED_QUEUED` both carry a document and a version (the
V025 constraints keep the V011 names). The draft's view lists its `queuedObservations`, oldest first, from one statement
for a whole list (no new index: the planner ignores a partial one at this size, as `IndexUsageTest` showed). Decision on the baseline: it stays the drafted text
(it moves only on `CHANGED`). Moving it on a queued change would leave B and C stored but never drafted once A is
decided, which loses the old behaviour that the next check after approval drafts the page as it then reads. To avoid
recording the same text on every interval, a change equal to the newest text already queued by this source behind the
draft is recorded `UNCHANGED`, not queued again. Consequence, stated plainly: after A is approved the next check drafts
the current page (C); B stays stored and listed behind A, and is not drafted by itself. Promoting each queued
observation to its own draft in order is a decision for the orchestrator (candidate follow-up, with UX-1).
The "review queue" is the rule version view: the work queue kinds are unchanged.

## D-556 Selection by the assessment date
`assessment.assessment_as_of date NOT NULL` (backfilled from `created_at` in UTC; the column default is
`CURRENT_DATE`, the application always sends it). `POST /programs/{id}/assessments` takes an optional body
`{ "asOf": "yyyy-MM-dd" }`; without one, today in UTC. For each target state the version used is the approved one whose
current status is `IN_FORCE` or `FINAL_NOT_EFFECTIVE`, with `effective_from <= as_of` and `effective_to` null or after
`as_of` (the end is exclusive); latest effective date wins, then the newest entry. Interpretation to report: the spec
lists `effective_from <= as_of` only for `FINAL_NOT_EFFECTIVE`; it is applied to `IN_FORCE` as well, because an
`IN_FORCE` version dated in the future is not in force on an earlier date. When no version matches, the start is refused
with 409 "No rule in force on <date> for license type ... in: <states>" and no assessment row is made; the run handler
fails permanently the same way if it ever finds none. It never falls back to the newest version. The overview response
gains `rules`, one entry per target state: `inForce`, `upcoming` (the earliest approved `FINAL_NOT_EFFECTIVE` version
after the date), `noRuleInForce` ("No rule in force on <date>") and `upcomingNotice` ("Upcoming from <date>"), from two
statements for all states. The change re-run (`RERUN_CHANGE`) is unchanged: it names its old and new versions
explicitly.

## D-557 API
`POST /rule-versions/{id}/status` (REVIEWER, ADMIN, as the spec says; a signer or contributor gets 403) takes the whole
position: `legalStatus` (required), `effectiveFrom`, `effectiveTo`, `publishedAt`, `adoptedAt`, `datesBasis`; it appends a
row and is audited `RULE_STATUS_RECORDED` (new position plus `previousLegalStatus`). `POST /rule-versions` takes the same
fields optionally; `effectiveFrom` is no longer required, and no status means `UNKNOWN`. Rule version views expose
`legalStatus`, the dates, `datesBasis`, `observedAt` and `queuedObservations`. The audited creation and approval details
carry the status and dates. Listed in `Rule9_AuditCoverageTest` and `InputValidationTableTest`.

## D-558 Seed
Each fictional demo version carries explicit fictional `adoptedAt`, `publishedAt` and `effectiveFrom` in
`seed/manifest.yml` (`legal:`), status `IN_FORCE`, and `dates_basis` "Fictional demo data" (`DataMode.DEMO_NOTICE`, the notice MODE-1 also prints on demo exports).
Deviation to report: the Colorado revision 2 (effective 2027-01-01, after today) is `FINAL_NOT_EFFECTIVE`, not
`IN_FORCE`. It is the true state of that fictional rule and it shows "Upcoming from 2027-01-01" on the overview.

## D-559 Frontend
The Rules screen shows the new fields as plain text only (status and effective date pills, one muted line with the source
of the dates, the observed date and the count of kept changes); UX-1 designs them. The entry form gains a legal status
and the source of the date, both required with the effective date, so a version entered by hand stays approvable.
Versions without a date list first (a person still has to date them) and read "date not recorded". Program setup
readiness mirrors the server's selection (`assessedVersion`). The audit sentence for `RULE_STATUS_RECORDED` and
`CHANGED_QUEUED` are in `audit-sentence.ts` with their spec. There is no screen yet that records a status for a
monitored draft: it is API-only until UX-1, which means a monitored draft cannot be approved from the interface yet.
