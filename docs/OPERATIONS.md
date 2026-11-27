# Operations

How to build, run, configure and observe Lexvay in production form. Decisions behind this page are in
`docs/decisions/F6.md` (D-140 to D-149).

## Build

```
make image          # docker build -t lexvay:local .
```

One image serves the API and the Angular app. The `Dockerfile` has three stages: Node builds the bundle, the JDK
builds the jar with the bundle inside and splits it into layers, the JRE runs it. Nothing from the first two stages
(source, tests, Maven and npm caches) reaches the runtime image; `make smoke` checks that.

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
| Image, compressed content | 223 MB (`docker image ls`, CONTENT SIZE) |
| Image, unpacked on disk | 710 MB (`docker image ls`, DISK USAGE); the JRE base is 159 MB of JVM plus Ubuntu, the application layer 126 MB of libraries and 0.6 MB of application code |
| `Started LexvayApplication in` | 1.8 s and 3.0 s in two runs (JVM start to ready, with Flyway validating 7 migrations) |
| `docker compose up --wait app` to healthy | 6 s (compose polls the health check, so this is an upper bound) |

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
5433+slot, app 8081+slot, management 8091+slot) and then proves: non-root user, read-only root filesystem, no source
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
| `LEXVAY_LLM_PRICE_INPUT_PER_MTOK`, `LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK` | USD per million tokens, for the cost estimate; 0 means not set | 0 |
| `LEXVAY_FILESTORE`, `LEXVAY_FILESTORE_PATH`, `LEXVAY_S3_BUCKET` | Where documents are stored | image: `/var/lexvay/files` |
| `SPRING_PROFILES_ACTIVE` | The image sets `prod` (structured logs) | `prod` |

The JVM options are in `JAVA_OPTS` (default `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:-UsePerfData
-Djava.io.tmpdir=/var/lexvay/tmp`); override the variable to change them.

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
| `lexvay.llm.duration` (`lexvay_llm_duration_seconds_*`) | none | wall time of a model call |
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
lexvay_llm_duration_seconds_count 3
```

### Cost of one assessment

`GET /api/v1/assessments/{id}/usage` (any signed-in user of the tenant; another tenant's id is a 404). With prices
set (`2.00` and `10.00` here), for the seeded Northfield assessment:

```
{"assessmentId":"9a3e5aef-0a0f-4c15-a8b0-b32b14703993","model":"fake-llm","calls":31,"inputTokens":49362,"outputTokens":2618,"modelTimeMs":0,"wallTimeMs":150,"estimatedCostUsd":0.124904,"prices":{"inputPerMtok":2.00,"outputPerMtok":10.00},"byPurpose":[{"purpose":"MATCH_EVIDENCE","calls":31,"inputTokens":49362,"outputTokens":2618,"durationMs":0}]}
```

`estimatedCostUsd` is tokens times the configured prices and is `null` when no price is configured. Requirement
extraction calls belong to a rule version, not an assessment, and are not included.

## Stop

`docker stop` (SIGTERM) starts a graceful shutdown: the server stops accepting connections and lets open requests
finish; the job poller claims no further job and the running one completes; then the scheduler and the database pool
close, all within `LEXVAY_SHUTDOWN_GRACE`. Give the container a stop timeout longer than that (compose uses 40 s). A job
that is still running when the grace period ends is claimed again by another instance after ten minutes
(`JobRepository.claim`); each requirement is committed on its own (D-052), so the run continues where it stopped.
