# FIX-1 — Requirement order column (D-230 to D-239)

**D-230 — The order of a rule's requirements is stored, as `requirement.ordinal`.** The rows of one rule version
are inserted in one transaction and share `created_at`, so the old order `created_at, ref` was the text order of
the ref (`CO-10` before `CO-2`). `ordinal integer NOT NULL` is the position in the list the extraction or the
reviewer produced, counted from 1, with `UNIQUE (rule_version_id, ordinal)`. That constraint's index also serves
`ORDER BY ordinal` within a version, so no extra index is created. The existing `UNIQUE (rule_version_id, ref)`
stays. Rejected: sorting refs naturally in SQL or Java (refs are free text from the rule, and the reviewer may
order them as the rule does); `ORDER BY id` (random uuids).

**D-231 — Migration `V012__requirement_ordinal.sql`.** In the migration: `NO FORCE ROW LEVEL SECURITY` and
`DISABLE TRIGGER requirement_locked` on `requirement`, `ADD COLUMN ordinal integer`, backfill, `SET NOT NULL`,
the unique constraint, then the trigger is enabled and `FORCE ROW LEVEL SECURITY` restored. All of it runs in
Flyway's transaction for the migration, so a failure leaves neither the lock nor the policy weakened. The
exemption is needed because the backfill must touch rows of every tenant and of approved rule versions: the
owner role is otherwise bound by `FORCE`, and the trigger rejects any update of an approved list. Product rule 5
is unaffected: no existing value changes, and the new column is derived from the existing order. V006 stays
unused; V009 to V011 are reserved by branches that merge before this one (D-074).

**D-232 — Backfill expression.** `row_number() OVER (PARTITION BY rule_version_id ORDER BY created_at,
(substring(ref from '(\d+)$'))::int NULLS LAST, ref)`: creation time first, then the number at the end of the
ref compared as a number, then the ref itself; refs with no trailing number sort last. For the rows that exist
today (one transaction per version) this is the numeric order of the refs, which is the order the extraction
returned them in.

**D-233 — The backfill is proven by running the migration's own statement.** Flyway applies all migrations
before the application starts, so a test cannot load rows and then migrate. `RequirementOrdinalBackfillTest`
creates a temporary table named `requirement` (it shadows the real table for that connection only, and is
dropped in a `finally` because the pool reuses connections), reads `V012__requirement_ordinal.sql` from the
classpath, runs its `UPDATE` unchanged and asserts the result: shuffled `CO-1` to `CO-12` with a shared
timestamp, refs without a number last (ordered by ref), an earlier `created_at` first, numbering restarting at 1
per version. Rejected: copying the expression into the test (it would drift from the migration).

**D-234 — Writing the order.** `RuleVersionRepository.insertRequirements(tenantId, ruleVersionId, drafts,
createdBy)` replaces the single-row `insertRequirement`; it is the only writer and assigns `ordinal` = list
position from 1. Both paths use it: `ExtractRequirementsHandler` (model output order) and
`RuleVersionService.replaceRequirements` (submitted order, after the delete of the unapproved list).
`RequirementDraft` stays a plain record: the list carries the order, not each draft.

**D-235 — Reading the order.** `Requirement` gains `ordinal` (last component), so the requirements endpoint and
the diff's `before`/`after` expose it. `RuleVersionRepository.requirements` orders by `ordinal`;
`MappingRepository` orders by `r.ordinal` (`j.code, r.ordinal` for the all-jurisdictions list). Nothing else
orders requirements. The mapping's own `RequirementView` is unchanged: mappings arrive in order and do not need
the number.

**D-236 — The diff follows the rule's own order.** `RequirementMatcher` already emits pairs in "the newer
version's order, removed ones last in the older version's order" (D-122); that order is now the list order of
each version because the repository returns it. No change to the matcher, `RuleVersionDiffService` or the
response shape. The D-122 remark that the order is "effectively ref text order" no longer applies.

**D-237 — Tests.** `RequirementOrderApiTest`: twelve requirements `CO-1` to `CO-12` through the requirements
endpoint (with `ordinal` 1 to 12), the mappings endpoint and the diff (all UNCHANGED, newer order); replace
keeps a submitted order that is neither numeric nor text order (`CO-3`, `CO-1`, `CO-10`), numbers it from 1, and
approval does not reorder. `RequirementOrdinalBackfillTest` as D-233. `MigrationsApplyCleanlyTest` counts files
and needed no change. The `Rule5_*` tests are unchanged; `SchemaFixtures` now supplies `ordinal` in its direct
`INSERT INTO requirement`. `AssessmentFixtures` gains `ruleVersion` (create and extract, unapproved) and
`approvedRuleVersion` calls it, so the creation request exists once. `InputValidationTableTest` is untouched: no
endpoint was added.

**D-238 — Compatibility.** The PUT body and the extraction schema are unchanged: clients send the list in the
order they want and receive `ordinal` in addition. The frontend needs no change; F3 may use `ordinal` instead of
a natural sort of the ref.
