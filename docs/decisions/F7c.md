### F7c — Search terms proposed at extraction (D-250 to D-259)

**D-250 — Migration `V014__requirement_search_terms.sql`.** `requirement.search_terms text[] NOT NULL DEFAULT '{}'` with
`CONSTRAINT requirement_search_terms_valid CHECK (search_terms_valid(search_terms))`. `search_terms_valid(text[])` is an `IMMUTABLE`
`PARALLEL SAFE` SQL function: `cardinality(terms) <= 12` and no element that is null or has `length(btrim(t)) NOT BETWEEN 1 AND 40`. The
roadmap's V015 is renumbered V014 because F13 needed no migration. Unlike V012 the migration does not lift `FORCE ROW LEVEL SECURITY` or the
`requirement_locked` trigger: nothing updates an existing row (a constant default is stored without rewriting the table and fires no trigger)
and the CHECK is validated against the stored rows as an internal scan that row-level security does not filter, so approved requirements stay
untouched and locked throughout. The lock trigger is unchanged, so `search_terms` is part of the locked row: `Rule5_ApprovedRequirementsRejectEditsTest`
now also tries `UPDATE requirement SET search_terms`. `RequirementSearchTermsConstraintTest` proves 12 and 13 terms, 40 and 41 characters, empty,
blank and null elements, and the function called directly. Rejected: a `jsonb` column (no constraint without a function either, and `text[]` goes
straight into `to_tsvector(array_to_string(...))`); a child table (a join on the hot path for at most 12 short strings); a `bool_and` over `unnest` in the CHECK
itself (subqueries are not allowed in CHECK constraints, hence the function).

**D-251 — One BM25 statement, three lexeme sources; `Passage.matchedVia` and `matchedTerms`.** The `q` common table expression of D-170 becomes
`SELECT DISTINCT ON (lexeme) lexeme, boost, source, priority` over three selects: the title (boost 2.0, priority 1), the text (1.0, 2) and
`to_tsvector('english', array_to_string(CAST(:searchTerms AS text[]), ' '))` (1.0, 3), ordered by `boost DESC, priority`. A lexeme found in several
sources therefore keeps the highest boost and, on a tie, the requirement's own source (a term that is also in the text counts once, as TEXT).
Nothing else in the statement changes: `lexq`, `wanted`, `hits`, `df`, `bm25` and the excerpt's `ts_headline` already work from `q`, so the
excerpt of a page found through a term shows the term's sentence. The `bm25` expression gains
`(array_agg(q.source ORDER BY q.boost DESC, q.priority))[1] AS matched_via`: the source of the highest-weighted lexeme that matched the page,
`TERMS` only when no word of the title or the text is on the page. `MatchSource` is `TITLE`, `TEXT`, `TERMS`. `Passage` gains `matchedVia` and
`matchedTerms`: for a `TERMS` page, the outer select (which only sees the rows that survived `LIMIT`) lists the terms, in their stored order, whose
lexemes overlap the page's (`tsvector_to_array(to_tsvector('english', term)) && tsvector_to_array(tsv)`); other pages get `{}`. The `ts_rank`
baseline ORs the terms into its tsquery as the spec says, and has no weights, so for it the title and the text are one source: a page it finds is
`TEXT` unless only the terms matched. Rejected: a second statement for terms (the filters, the ordering and the limit would be copied, D-172); naming
the matched terms in Java (it cannot stem); labelling per page in a separate query (a second round trip per requirement).

**D-252 — `SearchTerms` is the one normaliser.** `SearchTerms.normalise(proposed, title, text)`: trim, collapse inner whitespace, lower-case,
drop duplicates (first wins, order kept), drop empty terms, drop a term with a digit, drop a term that names a US state or the District of
Columbia (whole words, case-insensitive: `west virginia board` goes, `oregano` stays), and drop a term whose every word is already a word of
the requirement's title or text (`RequirementMatcher.words`, lower-cased `\w+`; stemming is not available in Java, so `disorder` is kept when the
text says `disorders`). A phrase with at least one new word is kept whole, because the words are searched as a bag anyway and the phrase is what a
reviewer reads. The roadmap says "text"; the title is included because its lexemes are in the search too. Limits are constants beside it:
`MAX_PROPOSED = 8` (what the model is asked for; the extraction schema enforces it), `MAX_STORED = 12` (what a person may keep; the CHECK enforces it),
`MAX_LENGTH = 40`. `RequirementListParser` calls it for both paths. The model's output is schema-validated first (more than 8 or a term over 40
characters is a rejected output and the usual single retry applies). The replace endpoint skips the schema, so `search_terms` is optional there
(absent or null is an empty list) and the parser itself rejects a non-array or a non-string (`requirements[i].search_terms must be an array of
strings`), more than 12 (`requirements[i].search_terms must contain at most 12 terms`) and a term over 40 (`requirements[i].search_terms[j] must be
at most 40 characters`), each a 400 naming the row, with the stored list untouched. Abbreviations of states (`CA`) are not recognised; the prompt
forbids state names and the reviewer reads every term.

**D-253 — Prompt `extract-v2` and the schema.** `llm/extract-v2.system.txt` is v1 plus one rule for `search_terms` (up to 8 single words or two-word
phrases a syllabus or degree plan would use for the subject, synonyms and course-title vocabulary, none already in the requirement text, no state
names, no numbers, an empty list when there is nothing to add). v1 stays on disk. `ExtractRequirementsHandler.PROMPT_VERSION` is `extract-v2`, so
`llm_call` and the `REQUIREMENTS_EXTRACTED` audit entry record it. `requirement-extraction.schema.json` gains `search_terms`: an array of strings,
`maxItems` 8, items `maxLength` 40, listed in `required` for every requirement (the schema is strict, as the other nullable fields are). Every
hand-built extraction in the tests now supplies the member; `JsonSchemaValidatorTest` covers nine terms, a 41-character term, a non-string and a missing member.

**D-254 — `RequirementComparison.Aspect.SEARCH_TERMS`.** `affectsEvaluation` is false, like `TITLE`: a terms-only edit is not a change to the
requirement, so re-run carries decisions over (`sameForEvaluation` is true) and the diff still reports the row. Terms are compared as lists, so
a changed order is reported too. `RuleVersionDiffService` is unchanged: a row whose only change is `SEARCH_TERMS` is `CHANGED` with
`changes: ["SEARCH_TERMS"]` and, as for a title change, a word diff of the (identical) text. `Requirement` and `RequirementDraft` gain `searchTerms`
(last component); `REQUIREMENTS_REPLACED` audits the terms in `before` and `after`. `RuleVersionChangeTest` proves both halves on a real re-run
(all three decisions carried over, no new model call) and the diff.

**D-255 — Binding the terms.** The terms reach SQL as one `text[]` parameter: `.param("searchTerms", String[])` and `CAST(:searchTerms AS text[])`,
which pgjdbc sends as an array (a `List` would have been expanded by Spring into an `IN` list). The statement stays prepared once per mode, no
SQL is built from a term, and quotes and backslashes in a term cannot break the tsquery (`PassageSearchTermsTest`). The three source names are bound
from `MatchSource`, so the SQL holds no copy of the enum. `PassageSearch.search` takes `List<String> searchTerms` between the text and the limit;
`EvidenceMatcher` passes the requirement's own, `RetrievalService` passes them only for the third column. The latency test now runs with six terms per
requirement and its budget (p95 under 30 ms for 500 pages) is unchanged.

**D-256 — The matcher: one sentence, nothing else.** When the cited page was found only through terms, `EvidenceMatcher` appends
` Found through the search terms: <terms that occur on the page>.` to the model's rationale, before any downgrade text. The terms are not shown to the
model, the quote is verified against the stored page exactly as before and a page found by terms is subject to the same downgrade rules, so product rules
1, 2 and 4 are untouched: a term only widens the shortlist. `SearchTermsMatchingTest` covers the sentence, a discarded invented quote on such a page, no
terms (the page is not a candidate at all), and a page that shares a word with the requirement (no sentence).

**D-257 — Seed and fixtures.** Every requirement scripted in `seed/fake-llm.yml` (55 across six rule sets, Colorado twice) and in
`frontend/e2e/fixtures/fake-llm.e2e.yml` (4) carries 3 to 5 hand-written fictional terms by subject (credits: `program of study`, `degree plan`;
ethics: `code of ethics`, `confidentiality`; and so on). CO-5 and every other Colorado requirement have identical terms in both rule sets, so the
diff between them still shows exactly one `CHANGED` row (`RuleVersionDiffSeedTest` unchanged and green). A term the production normaliser would drop
(all its words in the title or text) was removed from the file rather than left to be dropped silently: `lifespan development` for the human growth
requirements, `appraisal` for CA-8, `practice sessions` and `accredited program` in the retrieval queries. `SeedSearchTermsTest` runs both files through the
real parser and asserts none is dropped, 3 to 8 each, the schema accepts them, and the Colorado terms match.

**D-258 — Evaluation.** `eval/retrieval/queries.yml` gains `searchTerms` (3 to 8, lower-case, normaliser-stable: `RetrievalFileTest` runs them through
`SearchTerms.normalise`) for all 35 queries. The file header says they were written for the requirement alone, as the extraction prompt asks, and are never to be
edited to raise a number. Honest limit of that claim: the expected passages' `contains` strings sit in the same file and were visible when the terms
were written; the terms use the vocabulary any counseling syllabus uses (`psychopathology`, `couples therapy`, `videoconferencing`, `progress notes`), but
the lexical-gap result below is the result of one author's one pass, not of a model's proposals, and `make eval-live` does not measure it (its live path
substitutes the golden requirement list, which has no terms). `RetrievalService` runs ts_rank and BM25 without terms and BM25 with them; `RetrievalReport`
shows three columns in the console and in `docs/RETRIEVAL.md` (and each query's terms). `Thresholds.Retrieval` gains `minGapRecallAtDeep: 0.66` (4 of 6; 4 / 6 passes and
3 / 6 does not) and `Thresholds.termsBreaches` checks BM25 with terms against the existing recall and coverage floors, against BM25's recall@8 and recall@3,
and the gap floor. Result (slot 3 run): counted recall@8 28 / 29 for all three columns, recall@3 25, 26 and 27 of 29, mean reciprocal rank 0.796, 0.766 and 0.839, excerpt
coverage 28 / 28; lexical gaps recall@8 2, 2 and 6 of 6, recall@3 1, 1 and 6 of 6. R13 stays outside the top eight in all three. The pipeline
on the seed is unchanged: 0 false `EVIDENCE_FOUND`, 33 / 33 recall, 42 / 42 extraction agreement, 39 / 39 evidence agreement, 36 model calls.
`RetrievalServiceTest` asserts the gap floor and that without the terms the same queries fail it.

**D-259 — Consequences and what was left alone.** (1) The current requirement grid (F3) sends no `search_terms`, so until UI2-4 adds the cell, saving
the grid replaces the stored terms with none; the API and every other client keep them. (2) The live evaluation substitutes the golden requirement list
through the replace path, which has no terms, so live retrieval is measured without them. (3) `matchedTerms` covers terms that share a lexeme with the page; a
term can name a page that the other terms also matched. (4) `scripts/eval.sh` runs the eval profile with the default management port 8090, which fails to
start when a development backend holds it; the run here set `LEXVAY_MANAGEMENT_PORT=9093`. The profile should set `management.server.port: -1` like `seed`;
it is not part of this item. (5) The reviewer search, the `RULE_TEXT` exclusion and the tenant scoping (row-level security) are untouched; no endpoint was added,
so `Rule9_AuditCoverageTest` needs no entry, and the replace endpoint was already audited.
