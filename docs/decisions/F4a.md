# F4a — Evidence requests API: decisions

Work item `feat-evidence-requests-api`. Spec: `docs/ROADMAP.md`, section "F4a". These entries are merged into
`DECISIONS.md` by the orchestrator.

**D-130 — A response is attached to the mapping; it never edits it.** `evidence_response` is a new append-only
table. The mapping's `finding`, `method`, `rationale` and `evidence_span_id` stay exactly as the system proposed
them (product rules 1 and 5). The response only adds a verified span and a `REOPENED` review event, which makes
the requirement "needs review" again (D-003); the reviewer then decides.
Rejected: replacing the mapping's span with the human-supplied one (rewrites the system's proposal and loses
what the reviewer originally saw); a new `EVIDENCE_PROVIDED` review kind (the schema CHECK and the UI's reading
of review events would both change, and the spec fixes `REOPENED`).

**D-131 — A person's quote passes the model's verifier, through one shared service.** The document, page and
quote checks that `ProgramFactService` already did are extracted into `assessment.ProgramCitationService`, which
both program facts and evidence responses call. It also rejects `RULE_TEXT` documents with their own message
(they have no program, so they previously fell under "different program"). Spans are inserted only after the
verifier accepts, inside the caller's transaction, so a later failure stores nothing.
Rejected: copying the checks into the new service (two places to keep in step for product rule 2); putting the
helper in `document` (it needs `EvidenceSpanRepository`, which lives in `assessment`).

**D-132 — At most one response per request (`UNIQUE (review_event_id)`).** The spec table has no such
constraint; it follows from the loop: a response reopens the mapping, so no open request remains and a second
answer needs a new request. The constraint makes the rule hold under concurrent submissions: the second insert
waits for the first to commit and then fails with 409 ("conflicts with existing data").
Rejected: locking the mapping row (more code, same effect); allowing several responses to one request (the
state model has nothing to show for it).

**D-133 — ANSWERED is derived, not stored.** Rows come from the latest review event of the mapping (D-003):
OPEN when it is `EVIDENCE_REQUESTED`; ANSWERED when it is a `REOPENED` that carries a user and the latest
response answers the latest request. People cannot send `REOPENED` (`ReviewService`) and system reopenings
(`RerunChangeHandler`, course changes) have no user, so a `REOPENED` with a user can only come from a response.
A system reopening after an answer is neither OPEN nor ANSWERED: the requirement is back in normal review.
Rejected: a status column on the request (review events are immutable and a request is an event, so it would
need a second table or an update); marking the reopening event by note text (fragile).

**D-134 — Response count and latest response are per mapping, not per request.** After a second request the
row is OPEN again but still shows the earlier answer, which is what a contributor needs to see before answering
again. The list is the latest finished assessment's current mappings only (D-066, `MappingRepository.CURRENT`
is now public and reused), newest request first, in one query with lateral joins (no N+1).
Rejected: counting only responses to the latest request (always 0 or 1 under D-132, no information).

**D-135 — Audit entity is the mapping.** `EVIDENCE_PROVIDED` is recorded on `mapping` like `REVIEW_RECORDED`, so a
mapping's audit trail reads in one place; the response id is in the detail with requirement ref, jurisdiction,
document id, page and the verified quote. The actor is the contributor (the REOPENED event also carries them as
`user_id`, note `Evidence provided by <display name>`).
Rejected: entity `evidence_response` (splits one requirement's history over two entities).

**D-136 — Checks run in the spec's order and use the mapping's current state.** 404, stale 409, signed 409
(`DeterminationRepository.hasSigned`), no open request 409, then the 400s of the citation. The open request is
read from the newest review event (`ReviewRepository.latest`), the same source as the mapping's decision, and
its id is stored as `review_event_id`.

**D-137 — Roles.** POST is open to all four roles as specified (so only unauthenticated callers are refused);
both GETs follow the other read endpoints (any authenticated user of the tenant). Cross-tenant ids are 404
through row-level security, as everywhere else.

**D-138 — Migration count test.** `MigrationsApplyCleanlyTest` now compares applied migrations with the
`db/migration/V*.sql` files on the classpath instead of a hard-coded number, because V006 belongs to another
work item and the version numbers have a gap. Applied count is 6 here (V001 to V005 and V007); it is 7 once V006
merges, with no test change.
