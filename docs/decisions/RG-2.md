### RG-2 — Source registry governance (D-566 to D-575)

Context: master prompt M3 step 1 to 2 and §4.4. A regulatory source is a governed object, not a URL someone typed:
fetched politely and only where permitted, with its content validated before it can become a change candidate, and with
its health kept apart from the legal status of any rule. This extends F12, FIX-4 and RG-1; it adds no service. Evidence
class: loopback and fake fetchers only (no network, no paid service, no real website was fetched).

## D-566 Governance fields and the monitor's identity
V027 adds to `rule_source`: `authority_class` (`STATUTE`, `ADMIN_RULE`, `REGISTER`, `BOARD_GUIDANCE`, `BOARD_FORM`,
`OTHER`), `allowed_hosts` (non-empty, CHECK), `allowed_path_prefixes`, `expected_identifiers`, `access_status`
(`UNREVIEWED` default), `access_note`, `owner_user_id` and the validators `last_etag`, `last_modified`. Existing sources
are backfilled with the host of their own URL, `OTHER` and `UNREVIEWED` (the owner is exempt from RLS for the migration
and the update trigger is dropped and recreated, as V016 does). A new source starts the same way; everything else is
edited with `PUT /rule-sources/{id}/governance` (ADMIN), which replaces the whole position, rejects a position that
excludes the source's own host or path, accepts as owner only an active user of the same tenant (a foreign key alone
would accept another tenant's user), lower-cases and de-duplicates hosts, and is audited as
`RULE_SOURCE_GOVERNANCE_CHANGED` with `before` and `after` in the detail. Every request is `LexvayMonitor/<version>
(+<LEXVAY_MONITOR_CONTACT_URL>)` (`MonitorSettings`; the version comes from the build info). The scheduler refuses to
start when monitoring is enabled and the contact URL is unset or not an http(s) address that fits in a header. Reading
the spec literally ("required for the scheduler to run"), a check a person asks for by hand, and the first check a
person asks for by registering a source, still goes out without the contact part when none is configured: it names the
monitor and its version, never pretends to be a browser. Test: `SourceGovernanceApiTest`, `MonitorSettingsTest`.

## D-567 Where a fetch may go, and conditional GET
`FetchScope(allowedHosts, allowedPathPrefixes)` is checked by `JdkHttpFetcher` on the first address and on every
redirect hop, before the SSRF guard's answer is used and before any request is made to that hop; paths are normalised
first, so `/rules/../private` does not walk out of `/rules/`. An address that leaves is a `FETCH_FAILED` check naming the
reason. After a read, requests carry `If-None-Match` and `If-Modified-Since`; a 304 is the outcome `NOT_MODIFIED`: a
success for health, no document, no hash, nothing stored (a 304 to a request that had no baseline is a failure).
The validators of a response are stored only when the baseline moves with them: on `CHANGED`, and on `UNCHANGED` against
the baseline. A change kept behind a waiting draft (`CHANGED_QUEUED`) or recognised as already kept leaves the stored
validators alone: the baseline is still the drafted text (D-555), and a stored validator of the newer text would make
the next check answer 304 and the text behind the draft would never be drafted. Governance edits clear the validators,
so a new expected identifier is checked against a full response. Tests: `JdkHttpFetcherTest`, `ConditionalCheckTest`.

## D-568 Access status gates fetching
The scheduler's predicate is `active AND access_status = 'PERMITTED' AND next_check_at <= now()`. A person can ask for a
check of an `UNREVIEWED` source and it runs; the check row records `access_status` (new column, backfilled `UNREVIEWED`)
and so does the `RULE_SOURCE_CHECKED` audit entry, so the unreviewed state of a check is on the record. `RESTRICTED` and
`BLOCKED` sources are never fetched: `POST .../check` answers 409 and queues nothing, and a job queued before the status
changed fails permanently (`PermanentJobFailure`, audited `JOB_FAILED`) before a single request is made, not even
robots.txt. The first check a new source queues when registered stays: registering a source is a person asking for it,
as "Check now" is, and a new source is always `UNREVIEWED`. Tests: `RuleSourceEnqueueTest`, `ConditionalCheckTest`.

## D-569 Per-host throttle: the job waits in the queue, not in a worker
`source_host_fetch.last_fetch_at` is the last time a host was asked, shared by every instance and tenant.
`SourceHostRepository.reserve` takes the row `FOR UPDATE` in its own committed transaction (the lock is held for three
statements, never across a fetch) and either takes the slot or answers how long to wait: the later of
`last_fetch_at + LEXVAY_SOURCE_MIN_HOST_INTERVAL_SECONDS` (default 30) and the host's `hold_until`. A check that must wait
throws `JobDeferred`; `JobRunner` puts the job back (`QUEUED`, `run_after = now() + wait`, `attempts` decremented so a
wait costs no attempt) and `JobRepository.claim` skips jobs whose `run_after` is in the future. `job.run_after` is the one
change to the job table (V027, `NOT NULL DEFAULT now()`); nothing else about job handling moved. A wait writes no check
and no audit entry. The interval is measured from the start of one request to the start of the next; the robots.txt
request and the page request of one check share the one slot. A 429 or 503 with `Retry-After` (seconds or a date) is a
`FETCH_FAILED` check with the usual FIX-4 backoff and also sets the host's `hold_until` (capped at 7 days), so no other
source on that host is fetched sooner. Tests: `HostThrottleTest` (two jobs, two hosts, eight concurrent instances),
`RobotsCheckTest.aServerThatAsksUsToWaitHoldsEverySourceOnItsHost`.

## D-570 `source_host_fetch` is shared, not tenant data
What a host has been asked and its robots.txt are the same for every tenant, and a politeness limit that was per tenant
would not limit anything. So the table has no `tenant_id`. It is not left open either: row-level security is enabled and
forced, and its only policy is the transaction-local worker flag of D-013, which only `SourceHostRepository` sets (inside
its own transactions). "Non-RLS table readable only by the worker flag" in the spec is implemented as this. Consequence:
`Rule7_EveryTableHasPolicyTest` gains one named exception that asserts exactly this shape (no tenant column, forced RLS,
one policy), and `Rule7_SourceHostFetchIsolationTest` shows a tenant session and a session with no tenant read and write
nothing. The hosts are public names and robots.txt is public text; no tenant data is stored in it.

## D-571 robots.txt
Fetched per host at most daily (`robots_fetched_at`), through the same fetcher, scoped to that host. `RobotsRules` reads
only `User-agent`, `Disallow` and `Allow`; `*` matches any run of characters and a final `$` anchors the end; among the
matching rules the longest pattern wins and at equal length Allow wins; no match is allowed. Reading the spec's "a
Disallow matching our agent or `*`" as RFC 9309 does, the groups that name `LexvayMonitor` apply and, only when there is
none, the groups for `*`: a board that wrote an explicit group for us means it. The outcome of a disallow is
`BLOCKED_BY_ROBOTS`, no request to the page, no failure counted, the next check one interval later (the cached verdict
needs no request and no throttle slot). A 4xx robots.txt is cached as "allow all"; 5xx, 429, an unexpected status or an
unreachable server is `FETCH_FAILED` with the reason, is not cached, and nothing is fetched from that host until it can
be read (RFC: unavailable means do not crawl). Stated limit: the robots verdict is for the source's own address;
redirect hops are held to the allowed hosts and paths a person reviewed (D-567), not asked robots.txt again. Tests:
`RobotsRulesTest` (grammar), `RobotsCheckTest` (decisions, cache, daily refetch, shared across sources and tenants).

## D-572 Content validation before a change candidate
After extraction and before the hash is compared, `ContentValidator` can answer `SUSPECT_CONTENT` with a reason; the
check records it, no draft is made, no document is stored, and neither the baseline nor the validators move. It flags: a
content type no reader handles (`FileType.detectFetched` refuses it; this moved from `FETCH_FAILED`); an address that ends
in `.pdf`, `.docx`, `.txt`, `.html` or `.htm` and is answered with another format (a block page in place of a PDF); an
expected identifier missing from the text (case and white space ignored, every missing one named). Only when the source
has no expected identifiers, two more: HTML that contains `<form` (the spec's "a form and no expected identifier", read
strictly, so a source with a search box needs identifiers configured, which is what the field is for), and a text shorter
than 1,500 characters that matches one of the wordings in `BLOCK_PAGE_SHAPES`, the one constant that lists them. A page
that cannot be parsed or is empty stays `FETCH_FAILED`. Nothing tries to get past a challenge. Test: `ContentValidationTest`.

## D-573 Health is computed, never stored
`SourceHealth.of(facts, now)`: `BLOCKED` (access RESTRICTED or BLOCKED, or the latest outcome `BLOCKED_BY_ROBOTS`), else
`UNREVIEWED`, else `SUSPECT` (latest outcome `SUSPECT_CONTENT`), else `FAILING` (three or more consecutive failures; only
`FETCH_FAILED` counts, as FIX-4's counter does), else `STALE` (no `UNCHANGED`, `NOT_MODIFIED`, `CHANGED` or
`CHANGED_QUEUED` within twice the interval; a source never read counts from its creation), else `HEALTHY`. That order is
the severity order and is documented on the enum. `GET /rule-sources` returns `health`, `lastSuccessAt` and the
governance for every row from one statement (two lateral lookups on `rule_source_check_latest_idx`). The overview's
per-state `rules` entry gains `sourceNotice`: "Source stale" when any active source of the program's license type in that
state is not `HEALTHY`. Active only, because a paused source claims no coverage; a state with no source has no notice,
because nothing monitors it, which is not a monitor that fell behind. One more statement for the overview (four, not
three, however many states). Tests: `SourceHealthTest`, `SourceHealthApiTest`.

## D-574 Outcomes and recording
`NOT_MODIFIED`, `BLOCKED_BY_ROBOTS` and `SUSPECT_CONTENT` join the check outcomes (V027 replaces the CHECK; the changed-has-both
constraints of V025 are untouched). `CheckOutcome.SUCCESSES` is the one list of outcomes that read the source. A check
result is a `CheckFinding` value, which the repository records and the handler's baseline logic reads; `markChecked`
takes the baseline hash and the validators together. `UNCHANGED`, `NOT_MODIFIED`, `BLOCKED_BY_ROBOTS` and
`SUSPECT_CONTENT` wait the full interval and clear the failure counter; only `FETCH_FAILED` backs off.

## D-575 Frontend, tests and what is not here
Only `audit-sentence.ts` and its spec change on the frontend (the three outcomes, the access of an unreviewed check, and
the governance sentence, which says "from not reviewed to permitted" when the access changed); the screens for
governance and health are UX-1. Test infrastructure: the shared `FakeHttpFetcher` answers an unscripted `/robots.txt`
with 404 and is reset, with the host rows, before every rule source test; every database test runs with a host interval
of 0 so checks do not wait for each other, and `HostThrottleTest` runs the real 30. Not done, by the spec's boundary: no
UI, no real fetch, no change to the one-draft-per-source promotion rule of D-555.
