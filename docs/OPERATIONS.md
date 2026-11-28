# Operations

How to build, run, configure and observe Lexvay in production form. Decisions behind this page are in
`DECISIONS.md (D-140 to D-149)` (D-140 to D-149).

## Build

```
make image          # docker build -t lexvay:local .
```

One image serves the API and the Angular app. The `Dockerfile` has three stages: Node builds the bundle, the JDK
builds the jar with the bundle inside and splits it into layers, the JRE runs it. Nothing from the first two stages
(source, tests, Maven and npm caches) reaches the runtime image; `make smoke` checks that.

The PrimeNG license key is a **build-time** argument: `LEXVAY_PRIMEUI_LICENSE=<key> make image` (or in `.env` for
`docker compose build`). It is passed to `ng build --define` and compiled into the bundle the browser downloads; it is
not read at runtime and not stored in any image layer other than that bundle (the Node stage is discarded). Do not
push an image built with a real key to a public registry. Without it the app builds and runs and shows a license notice.

### Continuous integration

Job `image` in `.github/workflows/ci.yml` runs after `test` succeeds, on every pull request and every push to `main`
(D-240 to D-244). It builds this `Dockerfile` with Buildx and the GitHub Actions cache (`type=gha`, `mode=max`),
loads the result into the runner's Docker as **`lexvay:ci-<full commit sha>`** (never pushed anywhere, no license key),
writes the image size to the job summary, then runs `scripts/smoke.sh lexvay:ci-<sha>` on slot 0. Any failed smoke
check fails the job and the log shows the last 40 lines of the container's output. To repeat it on a machine:
`docker build -t lexvay:ci-local . && scripts/smoke.sh lexvay:ci-local` (`scripts/smoke.sh` alone builds
`lexvay:local`, which is what `make smoke` does). The script uses the Docker daemon the CLI already points at
(`DOCKER_HOST` when set).

### Pinned base images (checked 2026-11-26)

| Stage | Tag | Digest |
|---|---|---|
| web | `node:26.13.1-bookworm-slim` | `sha256:0042e17d232a87a2cd485be1da7900fca4d10a4ad11b29e9285da1d2e54798c9` |
| build | `eclipse-temurin:21.0.13_9-jdk-noble` | `sha256:bc12ebd88ffa6dda0092940e179365b05aef8d813b867f9df5a1f168c9a44feb` |
| runtime | `eclipse-temurin:21.0.13_9-jre-noble` | `sha256:b4f9d2a0bc273417cf5219b7b832bbf0493ed500b7b57d5001d9f83374659060` |

The tags are what the Dockerfile uses; the digests identify the images that were pulled and tested. To pin by
digest, write `FROM image:tag@sha256:...`. Maven comes from the wrapper (3.10.2, D-002); Spring Boot 4.1.3.

### Size and cold start (measured on this machine, Apple silicon, Colima)

| Measure | Value |
|---|---|
| Image, compressed content | 227 MB (`docker image ls`, CONTENT SIZE) |
| Image, unpacked on disk | 718 MB (`docker image ls`, DISK USAGE); the JRE base is 159 MB of JVM plus Ubuntu, the application layer 126 MB of libraries and 0.6 MB of application code |
| `Started LexvayApplication in` | 1.8 s, 3.0 s and 4.1 s in three runs (JVM start to ready, with Flyway validating 7 migrations) |
| `docker compose up --wait app` to healthy | 5 to 6 s (compose polls the health check, so this is an upper bound) |

These were measured with the other agents' builds running on the same machine; treat them as order of magnitude.

## Run

```
cp .env.example .env                       # development defaults; no secrets are committed
docker compose --profile app up --build    # PostgreSQL 18.7 and the app
```

The app is on `http://localhost:8080` (`LEXVAY_APP_PORT`) and its management port on
`http://127.0.0.1:8090` (`LEXVAY_APP_MANAGEMENT_PORT`, bound to loopback). Without `--profile app` compose starts only
the database, as before. To load the fictional demo tenants into that database with the same image:

```
docker compose --profile app run --rm --no-deps -v "$PWD/seed:/seed:ro" \
  -e SPRING_PROFILES_ACTIVE=seed -e LEXVAY_SEED_DIR=/seed app
```

`make smoke` does all of the above in an isolated compose project (`LEXVAY_E2E_SLOT` shifts the ports: database
5433+slot, app 8081+slot, management 9091+slot) and then proves: non-root user, read-only root filesystem, no source
or caches in the image, the app shell on `/` and on a deep link with the right cache headers, sign-in through the
container, an API read, a 404 problem detail with the request id, the usage endpoint, the scrape, ECS logs, and a
graceful stop.

The container runs as uid 10001 with a read-only root filesystem. Everything it writes is under `/var/lexvay`
(uploaded files in `files/`, temporary files in `tmp/`); mount a volume there. In a cluster, run with
`readOnlyRootFilesystem: true` and an `emptyDir` or volume at that path.

## Configure

All configuration is `LEXVAY_*` environment variables; `.env.example` lists every one with its default.

| Variable | Meaning | Default |
|---|---|---|
| `LEXVAY_PORT` | Main port (app, API, `/livez`, `/readyz`) | 8080 |
| `LEXVAY_MANAGEMENT_PORT` | Management port (`/actuator/prometheus`, `/actuator/health`). Keep it off the public network. `-1` turns it off | 8090 |
| `LEXVAY_SHUTDOWN_GRACE` | Time a stop waits for open requests and the running job | 30s |
| `LEXVAY_DB_URL`, `LEXVAY_DB_OWNER_*`, `LEXVAY_DB_APP_*` | PostgreSQL URL and the two roles (D-013, D-034) | see `.env.example` |
| `LEXVAY_LLM_PROVIDER`, `LEXVAY_LLM_MODEL`, `LEXVAY_LLM_EFFORT`, `ANTHROPIC_API_KEY` | Model (D-033). `fake` is for demos only | `fake` |
| `LEXVAY_LLM_PAID_APPROVED`, `LEXVAY_LLM_MONTHLY_BUDGET_USD`, `LEXVAY_LLM_RATES` | The spend gate for a metered provider and the one price table (see "Data mode and the spend gate"). `LEXVAY_LLM_RATES` replaces `LEXVAY_LLM_PRICE_INPUT_PER_MTOK` and `LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK`, which are no longer read | `false`, `0`, empty |
| `LEXVAY_FILESTORE`, `LEXVAY_FILESTORE_PATH`, `LEXVAY_S3_BUCKET` | Where documents are stored | image: `/var/lexvay/files` |
| `SPRING_PROFILES_ACTIVE` (compose: `LEXVAY_PROFILES`) | The image sets `prod` (structured logs); use `prod,oidc` for single sign-on, because setting only `oidc` drops the production log format | `prod` |
| `LEXVAY_OIDC_ISSUER_URI`, `LEXVAY_OIDC_CLIENT_ID`, `LEXVAY_OIDC_CLIENT_SECRET`, `LEXVAY_OIDC_POST_LOGIN_URL` | Single sign-on (WS-SEC, D-090) | empty |
| `LEXVAY_COOKIE_SECURE` | `true` when users reach the service over HTTPS | false |
| `LEXVAY_FORWARD_HEADERS` | `native` behind a reverse proxy or load balancer, so client address and scheme come from the proxy headers; leave `none` when the container is reached directly | none |
| `LEXVAY_RATELIMIT_LOGIN_PER_MINUTE`, `LEXVAY_RATELIMIT_API_PER_MINUTE`, `LEXVAY_RATELIMIT_UPLOAD_PER_MINUTE`, `LEXVAY_UPLOAD_MAX_MB` | Rate limits and upload size (WS-SEC) | 10, 600, 30, 25 |
| `LEXVAY_PACKAGE_MAX_BYTES` | Largest evidence package, in bytes; a larger one fails its job with a message and nothing is stored (F15) | 536870912 (512 MB) |
| `LEXVAY_PASSWORD_HASH_CONCURRENCY` | How many password hashes (sign-in, choosing a password) may run at once, 32 MiB of heap each; a caller that waits 2 seconds gets 503 with `Retry-After: 2` (D-407) | processors, at least 2 |
| `LEXVAY_AUDIT_SEAL_INTERVAL_MS` | How often the sealer chains new audit entries into the tamper-evident trail (SEC-3, D-533); an entry is sealed within about this time of being written | 2000 |

OCR of scanned PDFs (`LEXVAY_OCR`) checkpoints every page in its own committed transaction (`document_ocr_page`, FIX-6), so a retried ingest resumes after the last page that was read instead of recognising the whole document again.

The JVM options are in `JAVA_OPTS` (default `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:-UsePerfData
-Djava.io.tmpdir=/var/lexvay/tmp`); override the variable to change them. Password hashing needs heap headroom of
`LEXVAY_PASSWORD_HASH_CONCURRENCY` x 32 MiB on top of the application's own use (64 MiB with the minimum of 2 permits, 256 MiB on
eight processors): if you lower the heap, lower the setting with it, because the JVM exits on out-of-memory.

## Data mode and the spend gate

Demo data stays apart from real data, and no billed model call happens without approval and a cap (MODE-1, D-560 to
D-565).

**Data mode.** Every tenant is `DEMO` or `LIVE` (`tenant.data_mode`). The seed creates its tenants as `DEMO`, and so
do the tests; a tenant created any other way is `LIVE`. The fictional-data banner and the sign-in demo accounts follow
the mode (`GET /api/v1/auth/session` returns `demo: true` for a `DEMO` tenant). A `LIVE` tenant never uses the
scripted model: with `LEXVAY_LLM_PROVIDER=fake` its extraction and assessment jobs fail with "No model is
configured for real data", the seed refuses to write into it, and requirements cannot be approved from a rule version
until its dates basis is recorded and does not say "Fictional demo data" (until RG-1 records dates bases, every approval
in a `LIVE` tenant is refused). Every report, register export and audit export of a `DEMO` tenant carries "Fictional
demo data" in its header.

No API changes the mode. A database administrator does it as the owner role, naming the tenant first because
row-level security applies to the owner too:

```
BEGIN;
SELECT set_config('lexvay.tenant_id', '<tenant id>', true);
UPDATE tenant SET data_mode = 'LIVE' WHERE id = '<tenant id>';
COMMIT;
```

The database writes the audit entry itself (`TENANT_DATA_MODE_CHANGED`, with the old and new mode and the database
role). Do not move a tenant that holds fictional data to `LIVE`: its seeded documents and rules would then be mixed with
real ones.

**Spend gate.** The scripted model costs nothing. The `anthropic` provider is metered, and every call of it is refused
unless all three hold:
1. `LEXVAY_LLM_PAID_APPROVED` is `true` (default `false`).
2. The model has a price in `LEXVAY_LLM_RATES`, written `model=inputPerMtok:outputPerMtok` in USD per million tokens,
   entries separated by commas. There are no built-in prices, so the default table is empty and the cost of any call is
   unknown. Copy the prices from the provider's price list on the day you approve spending; a model that appears in
   this month's recorded calls needs a price too.
3. The cost recorded so far this UTC month, over every tenant, plus the most the call can cost is at most
   `LEXVAY_LLM_MONTHLY_BUDGET_USD` (default `0`). The most a call can cost is its `max-output-tokens` at the output
   price plus one input token per byte of its prompts and schema at the input price.

A refused call sends nothing, fails its job at once (no retries), logs the reason without content and is audited as
`LLM_CALL_REFUSED` with the reason, model and purpose. The cost recorded is the tokens of every stored call
(`llm_call`) at the configured prices: a call that failed at the provider is not stored, and instances running at the
same moment can together pass the budget by the calls in flight. Treat the budget as a guard rail and also set a
spending limit with the provider. At startup one log line states the provider, whether paid calls are approved, the
budget and the priced models. `make eval-live` goes through the same gate.

## Users and sessions

A tenant administrator adds users, changes their role and withdraws their access through the API (F5a, D-370 to D-379);
the first administrator of a tenant still comes from the bootstrap or the seed. There is no outgoing email: creating a
user returns a one-time setup code (valid 72 hours, stored only as a SHA-256) that the administrator hands over, and
`POST /api/v1/users/{id}/setup-code` issues a new one, which is also how a forgotten password is reset (D-371).
Choosing a password counts against the login rate limit of the client address.

Passwords are stored as Argon2id hashes (32 MiB, 4 passes, one lane; about 70 ms per sign-in on a development laptop,
and 32 MiB of heap while it runs) and are 12 to 128 characters long (D-400, D-402). A hash made before SEC-2 is bcrypt;
it keeps working and is replaced at that user's next successful sign-in, with one `PASSWORD_REHASHED` audit entry
(D-401). Any administrator can issue a setup code for any user, a signer or another administrator included, and so
could sign in as that person once the code is used. Both actions are audited (`USER_SETUP_CODE_ISSUED`, `PASSWORD_SET`),
but without email delivery that power is inherent to password accounts. Production deployments should use OIDC for
SIGNER and ADMIN accounts (profile `oidc`, D-090), where the identity provider's own controls decide who can sign in.

A session ends when its user's role, activity or password changes. Each instance keeps the last known access of every
user it served for 15 seconds (at most 10 000 users) and refuses a session issued with other values:
- A change made on this instance takes effect on the next request.
- A change made on another instance takes effect here within **15 seconds**. That is the longest a deactivated user or
  a demoted administrator can keep working through an instance that did not make the change. The delay is fixed in
  code (D-374); it costs one statement per user per instance per 15 seconds.
- A request that is refused gets a 401 "Your access changed. Sign in again." and the session is invalidated.

The audit log can be exported by an administrator as `GET /api/v1/audit.csv` with the filters of the audit list. It is
streamed from the database, so its size does not matter to the heap, but it reads the whole matching log in one
transaction; export a time range when a tenant's log is very large (D-377). Every export is itself an audit entry.

## Audit trail integrity

Triggers stop the application from changing or deleting an audit entry. They do not stop a database superuser, or a
restore from an altered backup. So each tenant's entries are also chained (SEC-3, `docs/decisions/SEC-3.md`): a sealer
(`AuditSealer`, every `LEXVAY_AUDIT_SEAL_INTERVAL_MS`, safe to run on several instances) gives each new entry the next
sequence number and a SHA-256 hash over the entry and the previous entry's hash. Change, remove or reorder anything
sealed and the chain stops matching. An entry written a moment ago is *pending* until the next run; the check below
reports how many.

An administrator checks a tenant's trail with `GET /api/v1/audit/verify`. It recomputes every hash from what is stored
(50 to 220 ms for 20 000 entries on a development laptop) and answers `intact`, how many entries are `sealed` and `pending`, and, when the chain
is broken, `firstBrokenSeq` and `firstBrokenReason`. Each check is an `AUDIT_VERIFIED` audit entry carrying the outcome
and `headHash`, the hash of the newest seal.

What it proves, and what it does not:
- **Proves:** no sealed entry was changed, and no seal was removed from inside the chain.
- **Does not prove:** that seals were not removed from the *end* of the chain, or that the whole chain was not
  rebuilt by someone who can write the database. Keep `headHash` and `sealed` somewhere the database administrator
  cannot write (the `AUDIT_VERIFIED` entries themselves are there, so are exported copies); a later check whose
  `sealed` is smaller than an earlier one, or whose hash for the same number differs, shows the tail was cut or rebuilt.
- Entries are numbered in the order they were sealed. An entry that commits late can have an earlier time than the
  entry before it.

### The canonical form

The hash of an entry is the lower-case hex SHA-256 of the UTF-8 bytes of these ten fields joined with the control
character U+001F (unit separator). A missing value is the empty string.

| # | Field | Form |
|---|---|---|
| 1 | tenant id | UUID, lower case |
| 2 | `seq` | decimal, from 1, no padding |
| 3 | entry id | UUID |
| 4 | actor id | UUID, empty for work no person started |
| 5 | action | as stored |
| 6 | record type | as stored |
| 7 | record id | UUID, empty when the entry has none |
| 8 | details | PostgreSQL `jsonb::text` of the stored details, which is deterministic |
| 9 | time | creation time in UTC, `YYYY-MM-DDTHH:MM:SS.ffffffZ`, always six fractional digits |
| 10 | previous hash | the previous entry's hash; 64 zeros for `seq` 1 |

### Checking an export without the application

The audit export (`GET /api/v1/audit.csv`) has the columns `Seq` and `Row hash` (empty for a pending entry) and the
`Entry ID` and `Actor ID` the form needs. Export without filters, then run, with the tenant id and the file:

```python
import csv, hashlib, sys
from datetime import datetime


def canonical_time(text):  # the export prints 0, 3 or 6 fractional digits; the form always has 6
    text = text.rstrip("Z")
    return datetime.strptime(text if "." in text else text + ".0", "%Y-%m-%dT%H:%M:%S.%f").strftime("%Y-%m-%dT%H:%M:%S.%fZ")


tenant, path = sys.argv[1], sys.argv[2]
with open(path, newline="", encoding="utf-8") as f:
    rows = sorted((r for r in csv.DictReader(f) if r["Seq"]), key=lambda r: int(r["Seq"]))
previous = "0" * 64
for number, row in enumerate(rows, start=1):
    fields = [tenant, row["Seq"], row["Entry ID"], row["Actor ID"], row["Action"], row["Record type"],
              row["Record ID"], row["Details"], canonical_time(row["Time (UTC)"]), previous]
    if int(row["Seq"]) != number or hashlib.sha256("\x1f".join(fields).encode("utf-8")).hexdigest() != row["Row hash"]:
        sys.exit(f"broken at entry {row['Seq']}")
    previous = row["Row hash"]
print(f"{len(rows)} entries, intact, head {previous}")
```

Against a restored database the same recomputation is one statement; it lists the `seq` of every seal that does not
match its entry (no rows: all match). Gaps and links need the application's check or the script above.

```sql
SELECT s.seq FROM audit_seal s JOIN audit_log a ON a.id = s.audit_id
WHERE s.row_hash <> encode(sha256(convert_to(concat_ws(E'\x1f', s.tenant_id, s.seq, a.id, coalesce(a.user_id::text, ''),
      a.action, a.entity_type, coalesce(a.entity_id::text, ''), a.detail::text,
      to_char(a.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), s.prev_hash), 'UTF8')), 'hex')
ORDER BY s.seq;
```

## Evidence packages

`POST /api/v1/determinations/{id}/package` builds one ZIP for a signed determination that has a ready report (F15,
D-540 to D-549): the report PDF, `determination.json`, `requirements.csv`, `quotes.csv`, the original bytes of every
cited document under `documents/`, `audit.csv`, a `README.txt` and a `manifest.json` that lists every other entry with
its size and SHA-256. A job builds it; `GET /api/v1/packages/{id}` shows QUEUED, READY or FAILED with the size and the
SHA-256 of the ZIP, and `GET /api/v1/packages/{id}/file` streams it. Requesting, finishing and downloading are audited
(`PACKAGE_REQUESTED`, `PACKAGE_READY`, `PACKAGE_DOWNLOADED`).

- The build writes the ZIP to the Java temporary directory (`/var/lexvay/tmp` in the image) before storing it, then
  deletes it, so that directory needs as much free space as the largest package expected (at most
  `LEXVAY_PACKAGE_MAX_BYTES`). Nothing is held whole in memory: documents and the audit trail stream through.
- With the S3 store, a ZIP above 16 MB is uploaded in 16 MB parts and a failed upload is aborted, so no partial object
  stays. The task role has `s3:AbortMultipartUpload` for that (`infra/iam.tf`), and the bucket's lifecycle rule
  removes the parts of an upload that was never completed after 7 days.
- A package that passes the size limit, or whose cited document no longer matches the hash recorded at upload, ends
  FAILED. `GET /api/v1/packages/{id}` carries the reason in `error`, and the job shows in the audit trail as
  `JOB_FAILED`. Request a new package after fixing the cause; a finished package is never changed.
- A package is deterministic for the same inputs: entries in a fixed order with the time of generation as their one
  timestamp. Anyone can verify one with `unzip` and `sha256sum` against `manifest.json`.

## Health

| Where | Path | Use |
|---|---|---|
| main port, public | `/livez` | liveness probe |
| main port, public | `/readyz` | readiness probe and the image `HEALTHCHECK`; includes the database |
| management port | `/actuator/health` | full report |

Spring Boot serves every actuator endpoint on the management port when one is set, so `/actuator/health` is not on
the main port; `/livez` and `/readyz` are Boot's additional probe paths for exactly this case.

## Observe

### Request ids

Every response carries `X-Request-Id`. A client may send its own (8 to 64 letters, digits and hyphens); anything
else is replaced by a UUID. Every problem detail has a `requestId` property with the same value. Give that value
to whoever reads the logs.

### Logs

Under profile `prod` (the image default) logs are one JSON object per line in the Elastic Common Schema, written to
standard output. Locally logs stay human readable, with `[requestId]` or `[jobId]` in the pattern. The logging
context carries `requestId`, `tenantId`, `userId` (web requests) and `tenantId`, `jobId` (background jobs). An access
line is written for each `/api/**` request that reached a handler: method, path, status, duration; never the query
string. Document text, quotes, rule text, prompts, model output and email addresses are never logged; a job's failure
message goes to the job row, not the log.

A real line from `make smoke`:

```
{"@timestamp":"2026-11-27T00:48:28.003642343Z","log":{"level":"INFO","logger":"lexvay.access"},"process":{"pid":1,"thread":{"name":"http-nio-8080-exec-3"}},"service":{"name":"lexvay","version":"0.1.0-SNAPSHOT"},"message":"GET /api/v1/unknown 404 0 ms","tenantId":"01502ab8-2abd-4a4c-8f1c-37d18e8a4312","userId":"fc365bb2-6991-4842-9318-9a31cad84322","requestId":"smoke-unknown-0001","ecs":{"version":"8.11"}}
```

### Metrics

`GET http://<host>:8090/actuator/prometheus`, no sign-in. Anything else on that port requires a session, so it serves
nothing. JVM, Tomcat and HikariCP meters come from Spring Boot; Lexvay's are:

| Meter (Prometheus name) | Tags | Meaning |
|---|---|---|
| `lexvay.llm.calls` (`lexvay_llm_calls_total`) | `purpose`, `model`, `outcome` = success, refused, error | model calls |
| `lexvay.llm.tokens` (`lexvay_llm_tokens_total`) | `direction` = input, output | tokens as reported by the provider |
| `lexvay.llm.duration` (`lexvay_llm_duration_seconds_*`) | `purpose` | wall time of a model call |
| `lexvay.job.duration` (`lexvay_job_duration_seconds_*`) | `type`, `outcome` = done, retried, failed | one job attempt |
| `lexvay.job.queue.depth` (`lexvay_job_queue_depth`) | none | queued jobs, all tenants (one indexed count at scrape time) |
| `lexvay.quote.verification` (`lexvay_quote_verification_total`) | `result` = verified, rejected | model quotes checked against page text |
| `lexvay.mapping.finding` (`lexvay_mapping_finding_total`) | `finding`, `method` | mappings stored |

Tag values come from small closed sets; there are no ids, no text. Useful alerts: `lexvay_job_queue_depth` growing
while `lexvay_job_duration_seconds_count{outcome="done"}` is flat (workers stuck), a rising
`lexvay_llm_calls_total{outcome="error"}` or `refused`, and a rising share of `result="rejected"` quotes (the model
is proposing quotes that are not on the page, which the product already discards).

From `make smoke` after one assessment run on the container's unscripted fake model (every call fails, which shows the
error paths):

```
lexvay_job_duration_seconds_count{outcome="failed",type="RUN_ASSESSMENT"} 1
lexvay_job_duration_seconds_count{outcome="retried",type="RUN_ASSESSMENT"} 2
lexvay_job_queue_depth 0.0
lexvay_llm_calls_total{model="fake-llm",outcome="error",purpose="MATCH_EVIDENCE"} 3.0
lexvay_llm_duration_seconds_count{purpose="MATCH_EVIDENCE"} 3
```

### Cost of one assessment

`GET /api/v1/assessments/{id}/usage` (any signed-in user of the tenant; another tenant's id is a 404). With prices
set (`fake-llm=2.00:10.00` here), for the seeded Northfield assessment:

```
{"assessmentId":"9a3e5aef-0a0f-4c15-a8b0-b32b14703993","model":"fake-llm","calls":31,"inputTokens":49362,"outputTokens":2618,"modelTimeMs":0,"wallTimeMs":150,"estimatedCostUsd":0.124904,"prices":{"inputPerMtok":2.00,"outputPerMtok":10.00},"byPurpose":[{"purpose":"MATCH_EVIDENCE","calls":31,"inputTokens":49362,"outputTokens":2618,"durationMs":0}]}
```

`estimatedCostUsd` is the tokens times the price of the assessment's model in `LEXVAY_LLM_RATES` (for this example `LEXVAY_LLM_RATES=fake-llm=2.00:10.00`), rounded up to a millionth of a dollar; it and `prices` are `null` when the model has no price. The evaluation report's cost line uses the same table. Requirement
extraction calls belong to a rule version, not an assessment, and are not included.

## Deploy on AWS (Terraform)

`infra/` holds the whole stack. `make infra-validate` formats, validates and tests it offline; nothing in the
repository plans or applies it, and the applying credentials never live here.

### Terraform state

State holds the generated database passwords and the origin-verify secret, so it lives in an S3 bucket that this
stack does not manage (the stack cannot create the place that stores its own state). Create it once per account, before
the first `init`, with the AWS CLI (placeholders in angle brackets):

```
aws kms create-key --description "lexvay terraform state"          # note the KeyId, then:
aws kms enable-key-rotation --key-id <state-key-id>
aws s3api create-bucket --bucket <state-bucket> --region <region> \
  --create-bucket-configuration LocationConstraint=<region>        # omit the last line in us-east-1
aws s3api put-bucket-versioning --bucket <state-bucket> --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket <state-bucket> --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"aws:kms","KMSMasterKeyID":"<state-key-arn>"},"BucketKeyEnabled":true}]}'
aws s3api put-public-access-block --bucket <state-bucket> --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-policy --bucket <state-bucket> --policy file://state-bucket-policy.json
```

`state-bucket-policy.json` denies every request that does not use TLS:

```
{"Version":"2012-10-17","Statement":[{"Sid":"TlsOnly","Effect":"Deny","Principal":"*","Action":"s3:*",
 "Resource":["arn:aws:s3:::<state-bucket>","arn:aws:s3:::<state-bucket>/*"],
 "Condition":{"Bool":{"aws:SecureTransport":"false"}}}]}
```

Then give the people and the pipeline that run Terraform read and write on the bucket's `*.tfstate` and `*.tflock`
objects and use of the key, and nothing else. Locking is the backend's own lock file (`use_lockfile = true`, an object
next to the state), so there is no DynamoDB table. Copy `infra/backend.hcl.example` to `infra/backend.hcl` (ignored by
git), replace every value, and run `terraform -chdir=infra init -backend-config=backend.hcl`. `make infra-validate`
keeps `init -backend=false`, so it needs no bucket.

### Web ACL

CloudFront is protected by a web ACL (`infra/waf.tf`) that exists in us-east-1, whatever region the rest of the stack
uses, so the stack declares a second AWS provider for it. Rules run in this order: the AWS IP reputation list, the
known-bad-inputs set, the common rule set (its body-size rule only counts, because uploads are up to 25 MB and the
application enforces that limit), and a rate limit that blocks an address after `waf_login_rate_limit` (default 300)
requests in 5 minutes to `/api/v1/auth/`. The limit complements the password-hashing gate in the application and does
not replace it. Blocked and counted requests are in the CloudWatch metrics of the ACL; every request is logged to the
us-east-1 log group `aws-waf-logs-<project>-<environment>` for `log_retention_days`, with the `authorization` and
`cookie` headers redacted. That log group uses CloudWatch's default encryption, because a key in another region cannot
protect it.

### Network logs and egress

Every connection in the VPC is recorded by a flow log (accepted and rejected, `ALL`) into a log group encrypted with the
data key; the key policy lets CloudWatch Logs use the key for that log group only.

The private subnets, which hold the backend and the database, have a network ACL. Outbound: the VPC's own range is
allowed first, then `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` and `100.64.0.0/10` are denied, then everything
else is allowed. Inbound: the VPC range, and TCP 1024 to 65535 from anywhere for replies to the model API and registry
calls (a network ACL is stateless). This closes the DNS-rebinding window left by the SSRF guard on source fetches:
the guard resolves a host before connecting and the HTTP client resolves it again, so a short-lived answer could point
a fetch at an internal address, and the network now refuses it. Link-local addresses (`169.254.0.0/16`, including the
metadata service and the ECS credentials endpoint at `169.254.170.2` that the task needs) never pass through a network
ACL, so this control cannot block them; the application's `SsrfGuard` remains the control for those.

## Stop

`docker stop` (SIGTERM) starts a graceful shutdown: the server stops accepting connections and lets open requests
finish; the job poller claims no further job and the running one completes; then the scheduler and the database pool
close, all within `LEXVAY_SHUTDOWN_GRACE`. Give the container a stop timeout longer than that (compose uses 40 s). A job
that is still running when the grace period ends is claimed again by another instance after ten minutes
(`JobRepository.claim`); each requirement is committed on its own (D-052), so the run continues where it stopped.
