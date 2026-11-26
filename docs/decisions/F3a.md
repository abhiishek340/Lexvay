# F3a — Rule version diff API: decisions D-120 to D-129

Work item F3a in `docs/ROADMAP.md`. These ids are reserved for this item; the orchestrator folds them into
`DECISIONS.md`.

**D-120 — One definition of "same requirement": `RequirementComparison`.** `com.lexvay.rule.RequirementComparison.changes(a, b)`
returns an `EnumSet` of `TITLE`, `TEXT`, `TYPE`, `NUMERIC`. Text and title are compared after
`QuoteVerifier.normalizeWhitespace` (case stays significant, as before). `NUMERIC` compares field, operator and value, the
value with `BigDecimal.compareTo` (600 equals 600.00; a missing value differs from any present one). Each `Aspect` carries
`affectsEvaluation` (false for `TITLE` only); `sameForEvaluation(a, b)` is true when no changed aspect affects evaluation.
`RerunChangeHandler` calls it directly and its private `unchanged` method is deleted, so carry-over is true exactly when
the set contains none of `TEXT`, `TYPE`, `NUMERIC`, and a title-only change still carries over (identical to the previous
behaviour; `RuleVersionChangeTest` is unmodified and green). The diff service uses the same `changes`.
Rejected: keeping a boolean `unchanged` beside a second set-based comparison for the diff (two definitions would drift);
a `Requirement.equals` override (the record's equality includes ids and must stay structural); comparing title without
whitespace normalization (a re-spaced title is not a change a reviewer cares about; the spec only fixes text, so this is
my reading).

**D-121 — One text diff utility: `TextDiff`, Myers via java-diff-utils.** `com.lexvay.common.TextDiff.words` and `.lines`
return merged segments `{op: EQUAL | DELETE | INSERT, text}`. Tokens are `\w+|\W+` so that joining reproduces the input;
the pattern is compiled with `UNICODE_CHARACTER_CLASS` so accented letters in names and legal text stay inside words
(without it `café` would be split at the `é`). Lines are split after each `\n` and keep their terminator, so the
reconstruction property (EQUAL+DELETE gives `a`, EQUAL+INSERT gives `b`) holds for lines as well, including `\r\n` and a
missing final newline. A replaced block becomes DELETE then INSERT for words and is refined with `words` on the joined
block for lines. One private walker serves both; adjacent segments with the same op (including those produced by refining
neighbouring blocks) are merged and empty segments are dropped. No cap on input size: rule texts are a few pages and the
Myers implementation is O(ND); revisit if a rule document of hundreds of pages ever needs a diff.
Rejected: hand-written diff (forbidden by the spec); character diff (unreadable on legal text); `DiffRowGenerator`
inline mode (HTML-oriented, wraps tags around text, no reconstruction guarantee); `MyersDiffWithLinearSpace` (smaller
memory but not the library default; no need at this size).

**D-122 — Requirement matching: `RequirementMatcher`.** Package-private class used by `RuleVersionDiffService`, split out so it
is unit tested without a database. (a) Pair by identical `ref`. (b) Among the requirements whose ref exists in only one
version, compute the Jaccard similarity of lower-cased `\w+` word sets of the texts for every removed x added pair, keep
pairs with similarity >= 0.80 (the boundary is inclusive and tested at exactly 0.80), sort by similarity descending
(ties by older position, then newer position, so the result is deterministic), and pair greedily so each requirement is
used once; those pairs are `refChanged: true`. (c) The rest is ADDED or REMOVED. Two empty word sets have similarity 0,
never 1, so blank texts do not pair. A pair is `UNCHANGED` only if the ref is the same and the change set is empty, so a
renumbered requirement is always `CHANGED` even when nothing else changed. Output order: newer version's order, then
removed ones in the older version's order. "Order" is the repository order (`created_at, ref`); because the requirements of
a version are inserted in one transaction, this is effectively ref text order (`CO-10` sorts before `CO-2`). That ordering
is pre-existing; the F3 UI may want a natural sort.
Rejected: optimal assignment (Hungarian) instead of greedy (the spec says greedy; at 0.80 conflicts are rare);
similarity on titles; matching on the numeric fields.

**D-123 — Endpoint contract.** `GET /api/v1/rule-versions/{id}/diff?against={otherId}`. `id` is the newer side (`to`),
`against` the older (`from`); when omitted `against` is the version `id` supersedes. 400 problem detail when there is no
superseded version and no `against`, and when jurisdiction or license type differ; 404 when either version is not visible
in the tenant (RLS), including `against`; 401 unauthenticated. Any authenticated role may read it, like the other rule
version reads. Comparing a version with itself is allowed (all UNCHANGED). The lists need not be approved: a reviewer
looks at the diff before approving the new list. Response: `from`/`to` `{id, label, effectiveFrom}`, `summary`
`{added, removed, changed, unchanged}`, `requirements` `[{status, refChanged, before, after, changes, textDiff}]`
(`before`/`after` are the existing `Requirement` JSON, null on the missing side; `textDiff` only for CHANGED rows, else
null; `changes` is an array in the order TITLE, TEXT, TYPE, NUMERIC) and `ruleTextDiff` (`TextDiff.lines` over the two rule
texts). `RuleVersionDiffService.diff` is `@Transactional(readOnly = true)`: two version lookups, two requirement queries and
two rule-text queries in one transaction, no per-requirement queries.
Rejected: `from`/`to` query parameters (the spec fixes the shape); 404 instead of 400 for the mismatch (both versions
exist, the request is wrong); a diff of approved lists only (blocks the review use case).

**D-124 — Tests.** Unit: `TextDiffTest`, `RequirementComparisonTest`, `RequirementMatcherTest`. API:
`RuleVersionDiffSeedTest` (seeded Colorado revision: one CHANGED CO-5 with `TEXT` and `NUMERIC`, eight UNCHANGED;
default `against`, explicit `against`, reverse direction, 400 without a superseded version, 400 across jurisdictions, 404
for another tenant, 401) and `RuleVersionDiffApiTest` (renumbered, edited, added and removed requirements on unapproved
lists, title-only change, 400 across jurisdiction and license type, malformed `against`, tenant isolation including the
`against` parameter, 401). The seed test loads a copy of `seed/manifest.yml` with its own tenant name and email domain
(symlinks to the shared `rules/`, `pdf/` and `fake-llm.yml`), because `SeedLoaderTest` asserts that its load is not
skipped and the two classes share one database container in any order.

**D-125 — Dependency pin.** `io.github.java-diff-utils:java-diff-utils:4.18`, the `latest` and `release` entry in
`https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/maven-metadata.xml` on 2026-11-26 (last updated
2026-10-18). jar sha1 `65e544c565591a97fd6a02150eb294e7e0a426ed`, no transitive dependencies. Declared in
`backend/pom.xml` through the `java-diff-utils.version` property, next to the other pins. API used, read from the jar with
`javap`: `com.github.difflib.DiffUtils.diff(List, List)` (default algorithm `MyersDiff`, verified in the static
initializer), `Patch.getDeltas()`, `AbstractDelta.getType()/getSource()/getTarget()` and `Chunk.getPosition()/getLines()/size()`.
Not used: `DiffRowGenerator`, `UnifiedDiffUtils`, `patch`/`unpatch`. Licence: Apache-2.0 (parent pom); the jar's only
dependencies are test-scoped.
Rejected: Google `diff-match-patch` (character oriented, not the library the spec names), an older java-diff-utils (no
reason to pin behind the latest stable).
