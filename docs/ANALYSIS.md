# Lexvay — Analysis of the source material

Date: 2026-11-26. Status: planning, nothing built yet.

Lexvay is the product name. It replaces both earlier working names: **Licentraz** (the build
specification) and **LicenseGraph** (the architecture design and the clickable prototype).
Everything below uses Lexvay. Environment variables use the prefix `LEXVAY_`, Java packages use
`com.lexvay`.

## 1. The three sources and how they rank

| Source | What it is | Authority |
|---|---|---|
| Build prompt ("Licentraz") | The specification: rules, scope, stack, data model, pipeline, API, UI, eval, seed, milestones | **Binding.** Where it is silent, the simplest option wins and is recorded in `DECISIONS.md`. Where it conflicts with itself, stop and ask. |
| Architecture design ("LicenseGraph", 2026-11-26) | Product thesis, user journey, system shape, security posture, business plan, risks | Context. Explains *why* the spec says what it says. Where it disagrees with the spec, the spec wins. |
| Prototype snippet | One screen of the clickable prototype: signer Dana Whitfield, "State Authorization Director", fictional-data banner | Naming and tone reference for the seed data and the UI banner. |

## 2. What the product is, in one paragraph

A university uploads curriculum documents (syllabi, degree plan, handbook, accreditation letter).
A compliance officer enters a state's licensing rule text as a versioned document. The system splits
the rule into single requirements (a person approves the list), checks numeric requirements with code,
and for subject requirements finds passages in the curriculum and proposes one of three findings with a
verbatim, verified quote. A reviewer accepts, requests evidence, or confirms a gap for each requirement.
A signer signs a per-state determination that is locked to the exact rule and course versions. When a
rule or course changes, affected signed determinations reopen. Every step is audited and exportable.

## 3. Why the product exists (regulatory context, verified 2026-11-26)

- **34 CFR 668.14(b)(32)** (final rule 2023-10-31, applies to students enrolling on or after
  2024-07-01): an institution must certify that each program leading to licensure meets the educational
  requirements of the state where the program is offered and where distance students are located. Still
  in force as of the eCFR revision of July 2026. Sources: eCFR Part 668; NC-SARA Professional Licensure
  FAQ (2025-08-05); WCET "Beyond Disclosures" Q&A (March 2026).
- **34 CFR 668.43(a)(5)(v)**: the public disclosure lists the states where the curriculum **meets**
  and where it **does not meet** the educational requirements. **34 CFR 668.43(c)(1)** covers the case
  where the institution **has not made a determination**, and requires direct notice to students for
  that case and for "does not meet".
- Consequence: a wrong "meets" determination touches an institution's Title IV standing. This is why
  the spec forbids the system from ever determining anything and why a false `EVIDENCE_FOUND` is
  defined as the worst possible error.
- Design consequence adopted by Lexvay: the **three signing outcomes** the spec leaves unnamed map onto
  the regulatory categories above. See `DECISIONS.md` D-004 (confirmed).

## 4. Where the architecture design and the spec disagree (spec wins)

| Topic | Architecture design | Specification | Lexvay does |
|---|---|---|---|
| Candidate passage search | pgvector embeddings | PostgreSQL full-text search behind `PassageSearch`; no vector store | Full-text search. The interface is the seam if embeddings are wanted later. |
| Background jobs | SQS + separate worker process | `job` table polled with `FOR UPDATE SKIP LOCKED`; no broker | Job table. Worker is a thread pool inside the one service. |
| API prefix | `/v1` | `/api/v1` | `/api/v1` |
| Review actions | accept, correct, reject, comment | `ACCEPTED`, `EVIDENCE_REQUESTED`, `GAP_CONFIRMED`, `REOPENED` | Spec kinds. "Correct" and "comment" are not built. Notes exist on every event. |
| Signing endpoint | `POST /v1/assessments/{id}/determinations` | `POST /programs/{id}/determinations` | Spec path. |
| Sign-in | SSO only, no local passwords | Form login with seeded users locally; OIDC added in milestone 8 | Spec. |
| Upload screening for student identifiers | Rejects uploads containing student names | Not mentioned. Rule 8 is a schema rule. | Not built (first principle 2, "delete"). Listed as a candidate for later. |
| Per-tenant encryption keys, SSE job updates, OCR for scans, model-cost meter | Mentioned | Not required by any acceptance check | Not built. Scanned PDFs with no text layer are rejected as empty documents with a clear error. Cost and time per assessment are reported by the eval harness only. |
| Business gates (three paid pilots before building past Phase 0) | Required | Spec says build all milestones | Engineering plan ignores the commercial gate; it is the owner's call, noted here so it is not lost. |

## 5. Ambiguities inside the specification and how Lexvay resolves them

Each item is also a numbered entry in `DECISIONS.md`. Items marked **ASK** were put to the owner and
answered on 2026-11-26; the answers are recorded in `DECISIONS.md`.

1. **Current decision vs `REOPENED` (ASK, D-003).** Section 5 says the current decision is "the latest
   review event that is not `REOPENED`". Read literally, a `REOPENED` event never changes anything,
   which contradicts section 7 ("lose their current decision"). Assumption: the current decision is the
   latest event; if that event is `REOPENED`, the mapping has no current decision.
2. **Three signing outcomes unnamed (ASK, D-004).** Assumption: `MEETS`, `DOES_NOT_MEET`,
   `NOT_DETERMINED`, matching 34 CFR 668.43. UI labels avoid the forbidden phrases: "Determined to
   meet", "Determined not to meet", "No determination made".
3. **Where `CONFIRMED_GAP` lives (D-005).** `mapping.finding` holds only the three system values. A
   confirmed gap is the review state derived from a `GAP_CONFIRMED` event. The database constrains the
   finding column to three values, so the system cannot write a gap even by mistake.
4. **How `program_fact` rows are created (ASK, D-006).** The spec's API list has no endpoint for them.
   Assumption: `POST /programs/{id}/facts` with key, value, document, page and quote; the quote is
   verified exactly like evidence quotes, producing the `evidence_span_id`. Demo values are seeded.
5. **Target jurisdictions per program (D-007).** No table in the data model. Add `program_jurisdiction`.
6. **Reviewers per program (D-008).** Not built. Any tenant user with `REVIEWER`, `SIGNER` or `ADMIN`
   can review; no acceptance check needs per-program assignment.
7. **Rule text documents have no program (D-009).** `document.program_id` is nullable, used only when
   `kind = RULE_TEXT`. Enforced with a CHECK constraint.
8. **Which rule version an assessment uses (D-010).** The newest approved version for the jurisdiction
   and the program's license type whose `effective_from` is not in the future.
9. **Rules-engine finding semantics (D-011).** Comparison true → `EVIDENCE_FOUND`; comparison false →
   `EVIDENCE_MISSING` with a calculation string such as `INTERNSHIP_HOURS 600 >= 700 is false`; fact
   absent → `EVIDENCE_MISSING`, "No program fact recorded for INTERNSHIP_HOURS". The engine never
   produces `AMBIGUOUS` because there is nothing uncertain about a comparison.
10. **Immutability vs `determination.status` (D-012).** Only `status` may change (SIGNED → REOPENED).
    A database trigger rejects any other update. Review events, documents, rule versions, course
    versions and evidence spans reject all updates and deletes by trigger.
11. **Jobs and row-level security (D-013).** `job` carries `tenant_id` like every other table. The
    poller claims a job under a `lexvay.worker` session flag that only the job table's policy honours,
    then switches to the job's tenant before running the stage.
12. **Raw model input and output storage (D-014).** A `llm_call` table (not in the spec's table list)
    stores model id, prompt version, request and raw response per call. Required by section 6 step 5.
13. **Rule-version change handling (D-015).** See `docs/PLAN.md`, milestone 7. New mappings are created
    against the new requirements; unchanged requirements (same `ref`, same text, same numeric value)
    carry their decision over with a system review event; changed ones start undecided.
14. **Passage = page (D-016).** `document_page` holds the tsvector, so the shortlist unit is a page.
15. **DOCX pagination (D-017).** Word files have no fixed pages. Stored as one page; the rationale shown
    to reviewers says so.
16. **Who is "who" for background actions in the audit log (D-018).** `audit_log.user_id` is null for
    worker actions and `detail` carries the job id and type.

## 6. Risks that shape the plan

| Risk | Why it matters | What the plan does |
|---|---|---|
| False `EVIDENCE_FOUND` | Worst possible error by definition | Quote verification in code, non-required course → `AMBIGUOUS`, eval threshold gate, forbidden-word scan, DB CHECK on finding values |
| Seed data inconsistency | 14 documents, 42 requirements, scripted fake model outputs and a golden set must agree | One seed manifest is the single source for PDFs, requirements, facts and scripted outputs; the golden set is written separately and compared |
| Newest releases are days old (Spring Boot 4.2.0, PostgreSQL 19.0) | Ecosystem and RDS lag | Pin Spring Boot 4.1.3 and PostgreSQL 18.7 (owner decision D-002) |
| Local toolchain gaps | Docker, Maven and Terraform are not installed; Node 22.18 is below Angular 22's minimum | Listed as prerequisites; Maven wrapper removes the Maven dependency |
| RLS correctness | Easy to believe and hard to prove | Separate DB roles, `FORCE ROW LEVEL SECURITY`, fail-closed policy when the tenant setting is absent, tests through the API and through repositories |
| Tenant-aware job worker | Cross-tenant leak through the poller | Worker flag honoured only by the `job` policy; stage code always runs under the job's tenant |
| Regulatory content | Must never be invented | Every rule set labeled "Demo rule set", banner in UI, `fictional = true` in seed metadata |

## 7. What the plan deliberately does not do

- No regulatory database, board monitoring, student data, SIS/CRM, accreditation mapping, billing,
  mobile, marketing site (spec section 3).
- No pgvector, SQS, SSE, OCR, per-tenant KMS keys, upload screening (architecture design items the spec
  dropped).
- No abstraction with a single implementation except the named seams: `LlmClient`, `PassageSearch`,
  `FileStore`.
