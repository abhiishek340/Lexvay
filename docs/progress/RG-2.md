## RG-2 — Source registry governance (PR to be numbered)

Branch `rg-source-governance`, merged with `origin/main` before the final run. Evidence class: fixtures and loopback
(fake fetcher, a loopback `HttpServer`, `FakeLlmClient`; no network, no paid service, no real website fetched).
Decisions: `docs/decisions/RG-2.md` (D-566 to D-575).

## Done
- V027: governance fields on `rule_source` (authority class, allowed hosts and path prefixes, expected identifiers,
  access status and note, owner, validators), backfilled; three more check outcomes and the access status each check was
  made under; `source_host_fetch` (per-host last request, hold and cached robots.txt); `job.run_after`.
- `PUT /api/v1/rule-sources/{id}/governance` (ADMIN), audited `RULE_SOURCE_GOVERNANCE_CHANGED` with before and after.
- Only `PERMITTED` sources are queued by the scheduler; `UNREVIEWED` ones can be checked by hand and the check records
  that; `RESTRICTED` and `BLOCKED` ones are never fetched (409 on request, a queued job fails before any request).
- Polite fetching: `LexvayMonitor/<version> (+contact)` User-Agent, contact required for the scheduler; conditional GET
  with `NOT_MODIFIED`; per-host throttle across instances whose waiting jobs return to the queue (`JobDeferred`);
  robots.txt per host per day with longest match; Retry-After holds the host.
- Content validation: expected identifiers, form and challenge-page shapes, format promised by the address; a suspect
  text makes no draft and does not move the baseline.
- Health (`HEALTHY`, `STALE`, `FAILING`, `BLOCKED`, `SUSPECT`, `UNREVIEWED`) computed on read in the list and as
  `sourceNotice` "Source stale" in the overview's per-state `rules`.
- Frontend: only the audit sentences. Docs: OPERATIONS "Monitoring etiquette" and the two new variables, `.env.example`.

## Tests
New: `RobotsRulesTest`, `SourceHealthTest`, `MonitorSettingsTest`, `ContentValidationTest`, `ConditionalCheckTest`,
`RobotsCheckTest`, `HostThrottleTest`, `SourceGovernanceApiTest`, `SourceHealthApiTest`,
`Rule7_SourceHostFetchIsolationTest`; `JdkHttpFetcherTest` grew (User-Agent, conditional headers, Retry-After, scope on
every hop); rows in `InputValidationTableTest` and `Rule9_AuditCoverageTest`; the audit sentence spec.
Existing tests were adapted, none weakened: the fake fetcher takes a request and answers an unscripted `/robots.txt` with
404, scheduler tests permit their sources, and `Rule7_EveryTableHasPolicyTest` names `source_host_fetch` as the one
table that is not tenant data.

## Remaining unknowns
- No screen shows governance or health yet (UX-1); both are API-only.
- No real board's robots.txt, terms or response shapes have been seen, so `BLOCK_PAGE_SHAPES` and the form rule are
  fixtures of what challenge pages usually look like, to be tuned on evidence from the first reviewed sources.
- A redirect hop is held to the reviewed hosts and paths but not asked robots.txt again (D-571).
- Compose does not pass `LEXVAY_RULE_SOURCES_ENABLED` or the two new variables to the app container; an installation
  that enables monitoring sets them in its own environment.
