# WS-SEC progress: OIDC and hardening (milestone 8 evidence)

Branch `sec-oidc-hardening`, merged with `origin/main` (including PRs 8 to 11) on 2026-11-26. Decisions D-090 to D-099
are in `docs/decisions/WS-SEC.md`.

## What was built

| Spec item | Built | Tests |
|---|---|---|
| 1 OIDC | Profile `oidc`: `OidcLogin`, `LexvayOidcUserService`; role and tenant from `app_user` by verified email; 403 "No Lexvay account for this identity"; `LOGIN_SUCCEEDED` with `method: oidc`; 401 (no redirect) for API calls without a session | `OidcLoginTest` (17, full code flow against a loopback provider), `AuthTest` |
| 2 Rate limiting | `RateLimitFilter`: login 10/min per address, API 600/min and uploads 30/min per user, 429 with `Retry-After`, Caffeine cache of 10 000 keys with 10 minute idle expiry, `lexvay.ratelimit.*` | `RateLimitFilterTest` (11, fake clock), `RateLimitApiTest` (4), `ForwardedHeadersTest` (2) |
| 3 Headers and cookie | `SecurityHeadersFilter` (exact CSP, nosniff, no-referrer, Permissions-Policy, HSTS on secure requests); session cookie HttpOnly, SameSite=Lax, Secure by `LEXVAY_COOKIE_SECURE` | `SecurityHeadersTest` (21), `SessionSecurityTest`, `SecureCookieTest` |
| 4 Upload limits | 413 problem detail over the limit; DOCX zip bomb guard (50 MiB or ratio 100); PDF page cap 500; file name checks | `UploadSizeLimitTest`, `ContentLimitsTest` (10), `DocumentUploadTest` (13) |
| 5 Validation table | 111 requests covering every endpoint, plus a guard that fails when an endpoint has no row | `InputValidationTableTest` |
| 6 Session fixation | `changeSessionId` explicit; id change tested over a real connection for form login and OIDC | `SessionSecurityTest`, `OidcLoginTest` |

`Rule9_AuditCoverageTest` needed no change: no mutating endpoint was added (the OIDC callback and login are
security filters). The merged-in endpoints from main (evidence responses, evidence requests, rule version diff,
search) have rows in the validation table.

## Bugs the validation table found (all fixed, none whitelisted)

1. A NUL character in any stored string (program name, rule text, review note, requirement title) gave 409 or 500:
   `ApiExceptionHandler` maps SQLSTATE class 22 to 400.
2. `numeric_value: 1e400` in the requirements PUT threw `JsonNodeException` (500): parser requires a finite number.
3. Requirement `ref`, `title` and `text` had no size limit (300 000 characters accepted): 50, 200, 20 000.
4. The `jurisdiction` filter of `GET /assessments/{id}/mappings` took any string: `@Pattern`.
5. File names of 5 000 characters or with control characters were accepted (a NUL name reached the database after
   the file was stored): 400.
6. A NUL in the login username or OIDC email logged a database stack trace: `findForLogin` returns empty.

## Real output

`LEXVAY_E2E_SLOT=2 make test` from the repository root (backend `./mvnw verify`, frontend unit tests, end to end):

```
[INFO] Tests run: 445, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  3 passed (3)
      Tests  8 passed (8)
e2e: starting backend on :8083
  ✓  1 e2e/review-and-sign.spec.ts:13:1 › Dana Whitfield reviews every California requirement, signs, and sees the audit entries (4.3s)
  ✓  2 e2e/review-and-sign.spec.ts:86:1 › the review tabs are keyboard operable and the login page is accessible (993ms)
  ✓  3 e2e/review-and-sign.spec.ts:103:1 › the Changes screen lists the seeded changes and re-runs matching for the new course version (2.2s)
  3 passed (11.3s)
```

New or extended test classes (from the same run):

```
Tests run: 11 auth.RateLimitFilterTest      Tests run: 4  auth.RateLimitApiTest
Tests run: 21 auth.SecurityHeadersTest      Tests run: 3  auth.SessionSecurityTest
Tests run: 1  auth.SecureCookieTest         Tests run: 2  auth.ForwardedHeadersTest
Tests run: 17 auth.OidcLoginTest            Tests run: 111 common.InputValidationTableTest
Tests run: 10 document.ContentLimitsTest    Tests run: 13 document.DocumentUploadTest
Tests run: 2  document.UploadSizeLimitTest  Tests run: 10 auth.AuthTest
```

Mutation check: setting `sessionFixation` to `none()` makes `SessionSecurityTest.loginChangesTheSessionId` fail.

## Notes for the orchestrator

- `InputValidationTableTest.everyEndpointHasARow` fails for any branch that adds an endpoint without a table row;
  branches merged after this one must add rows.
- Production behind a load balancer needs `LEXVAY_FORWARD_HEADERS=native` (D-093), otherwise the login budget is one
  shared bucket.
- `scripts/e2e.sh` sets the login budget to 1000 (the browser tests sign in repeatedly from one address).
