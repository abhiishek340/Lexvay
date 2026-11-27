# WS-SEC decisions (D-090 to D-099)

Work item WS-SEC in `docs/ROADMAP.md`: OIDC and hardening, backend only. Each entry names what was rejected.
Versions checked on Maven Central metadata (repo1.maven.org) on 2026-11-26.

**D-090 — OIDC through `spring-boot-starter-security-oauth2-client`, only in profile `oidc`.** The starter is managed
by the Boot 4.1.3 BOM (Spring Security 7.1.3, `oauth2-oidc-sdk` 11.38.2, `nimbus-jose-jwt` 10.9.1). It is on the
classpath in every profile but does nothing without `spring.security.oauth2.client.registration.*`, which only
`application.yml`'s `oidc` section defines (`LEXVAY_OIDC_ISSUER_URI`, `LEXVAY_OIDC_CLIENT_ID`,
`LEXVAY_OIDC_CLIENT_SECRET`, scopes `openid,email,profile`, registration id `lexvay`, so browsers start at
`/oauth2/authorization/lexvay`). `OidcLogin` (`@Profile("oidc")`) configures `oauth2Login` on the one security chain;
`SecurityConfig` calls it through an `ObjectProvider`, so the default profile has no OIDC bean at all
(`AuthTest.singleSignOnIsOffUnlessProfileOidcIsActive`). After the provider authenticates, the success handler
replaces the `OAuth2AuthenticationToken` in the session with a `UsernamePasswordAuthenticationToken` whose principal is
the same `LexvayUser` that form login produces, so `CurrentUser`, `TenantContextFilter`, method security and audit
are unchanged, then redirects to `LEXVAY_OIDC_POST_LOGIN_URL` (default `/`) and audits `LOGIN_SUCCEEDED` with
`method: oidc` (form login now audits `method: password`). `/api/**` without a session keeps the 401 problem detail
because the explicit authentication entry point wins over the OAuth2 login redirect. Form login stays enabled in the
`oidc` profile (the spec says it remains the default); an organisation that wants SSO only should disable it in a
follow-up. Rejected: making `LexvayUser` implement `OidcUser` (it would carry token claims in every session and in
every form login); a resource-server (JWT bearer) setup (the browser flow is the requirement); a second
`SecurityFilterChain` for OIDC (two chains would have to repeat CSRF, headers, rate limiting and tenant binding).

**D-091 — Identity to account: verified email, roles from the database.** `LexvayOidcUserService` wraps Spring's
`OidcUserService` (so the user-info call, subject check and ID token validation are Spring's) and looks the `email`
claim up with `AppUserRepository.findForLogin`. It refuses with 403 problem detail "No Lexvay account for this
identity" when the claim is missing or blank, when `email_verified` is present and not `true` (providers send a boolean
or the string "false"; anything else than true fails closed), or when no `app_user` matches. Roles and tenant are
never read from token claims; a token carrying `roles: [ADMIN]` for a reviewer still yields a reviewer
(`OidcLoginTest.theRoleComesFromTheLexvayDatabaseAndNeverFromTheToken`). `findForLogin` returns empty for an email
containing NUL, which PostgreSQL cannot store (it used to raise a database error). Other failures (bad state, nonce,
issuer, signature) are 401 and logged without personal data. Rejected: mapping provider groups to roles (the roles
belong to Lexvay, per the spec); creating accounts on first login (an account carries a tenant and a role, which a
provider cannot decide); matching on `sub` (needs a new column and a linking flow, not in the spec).

**D-092 — Rate limiting: one filter, Bucket4j buckets in a bounded Caffeine cache.** `RateLimitFilter` is created
inside `SecurityConfig` (not a bean, so Boot does not register it a second time as a servlet filter) before
`CsrfFilter`, so a login attempt counts whether or not its CSRF token is valid, and the user is already known from the
session. Budgets, all per minute with greedy refill: login 10 per client address (counts `POST /api/v1/auth/login` and
the OIDC callback `GET /login/oauth2/code/*`), authenticated `/api/**` 600 per user, `POST /api/v1/programs/*/documents`
30 per user on top of the API budget. A refusal is a 429 problem detail with `Retry-After` (whole seconds until a token
is back). Settings are `lexvay.ratelimit.*` (`LEXVAY_RATELIMIT_LOGIN_PER_MINUTE`, `_API_PER_MINUTE`,
`_UPLOAD_PER_MINUTE`, `max-keys` 10000, `idle-expiry` 10m), validated positive. The filter takes its clock as a
`LongSupplier`, so refill and idle expiry are tested with a fake clock (`RateLimitFilterTest`, including that the
cache never exceeds `max-keys`). The test base sets the budgets to 100000 and the rate limit tests override them.
`scripts/e2e.sh` sets the login budget to 1000 because every browser test signs in from 127.0.0.1. Budgets are per
instance, so with N instances the effective budget is up to N times higher. Rejected: `bucket4j-spring-boot-starter`
(a third-party auto-configuration for what is one class); a filter before the security chain (the user is unknown
there); Redis or JDBC-backed buckets (the spec says in-memory, per instance); Spring's `HandlerInterceptor`
(does not see login requests, which are handled by a security filter).

**D-093 — Response headers, session cookie, forwarded headers.** `SecurityHeadersFilter` sets, on every response,
the exact `Content-Security-Policy` from the spec, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
`Permissions-Policy: camera=(), microphone=(), geolocation=()`, and `Strict-Transport-Security: max-age=31536000 ;
includeSubDomains` when `request.isSecure()`. It is a servlet filter at highest precedence rather than the Spring
Security headers DSL because the request firewall rejects some URLs (for example `;` in the path) before the security
chain starts, and those 400 responses must carry the headers too (`SecurityHeadersTest` covers 200, 400, 401, 403,
404, 405, 415, the firewall's 400 and 429). The DSL keeps `X-Frame-Options: DENY` and `Cache-Control: no-store`; its nosniff and
HSTS writers are disabled to avoid duplicates. Session cookie: `server.servlet.session.cookie` `http-only: true`,
`same-site: lax` (Lax, not Strict, because the OIDC callback is a cross-site top-level navigation), `secure` from
`LEXVAY_COOKIE_SECURE`. `server.forward-headers-strategy` comes from `LEXVAY_FORWARD_HEADERS` (default `none`).
Behind a load balancer it must be `native`: otherwise every client shares the balancer's address, one login budget
for everybody, and the scheme is always http (no HSTS). `native` uses Tomcat's `RemoteIpValve`, which trusts the
header only from private-range peers (`ForwardedHeadersTest`). `framework` was rejected: `ForwardedHeaderFilter` does
not change the remote address. Rejected: Spring Security's HSTS writer alone (misses firewall rejections); a CSP with
nonces (the API serves JSON, the Angular build is served elsewhere).

**D-094 — Session fixation and login CSRF stay on, with tests.** `sessionFixation().changeSessionId()` is now explicit
in `SecurityConfig` so it cannot be lost by accident, and CSRF is untouched. `SessionSecurityTest` uses a real
connection (MockMvc has no container session): it plants a session with an unauthenticated request, logs in with it,
and asserts the id changed, the new id is authenticated and the planted one is not. Changing the setting to `none()`
makes the test fail (checked). `OidcLoginTest.theSessionIdChangesAtOidcLoginOverARealConnection` proves the same for
the OIDC callback. Login without the CSRF header is 403 (`SessionSecurityTest`, `AuthTest`). Rejected: asserting the id
change with MockMvc (`MockHttpSession` does not follow the container's behaviour).

**D-095 — Upload limits.** (1) Size: Spring's multipart limit (`LEXVAY_UPLOAD_MAX_MB`, default 25) answers 413 problem
detail through `ResponseEntityExceptionHandler` (`UploadSizeLimitTest`, real connection, limit 1 MB for speed). (2) Type:
`FileType.detect` (extension and leading bytes) as before; tests cover every mismatch with a problem detail. (3) DOCX
zip bomb: `ContentLimits.requireSafeDocx` inflates the archive counting the bytes that really come out (declared sizes
can lie) and stops at the first byte beyond `min(50 MiB, 100 x file size)`; it runs at upload (400 problem detail,
nothing stored) and again in `TextExtractor` (any caller). An unreadable archive is left to the parser, so a corrupt
DOCX is still accepted and fails ingest as before. (4) PDF page cap 500: checked in `TextExtractor` after the PDF is
opened, so an over-long PDF is accepted with 202 and its ingest job fails with "PDF has 501 pages; the limit is 500",
like the other content failures; loading every PDF at upload just to count pages was rejected. Also: file names longer
than 255 characters or with control characters are refused with 400 (a NUL used to reach PostgreSQL after the file was
already stored). 50 MB is read as 50 MiB. Rejected: POI's own `ZipSecureFile` limits as the only guard (no total size
limit); `ZipFile` on a temporary file (disk writes for an in-memory check).

**D-096 — Input validation table test and the fixes it forced.** `InputValidationTableTest` lists 111 requests, at
least one per endpoint (bad UUID, missing field, oversized string, unknown enum, NUL character, malformed or deeply
nested JSON, wrong media type, non-JSON login) and asserts the expected 4xx with a problem detail. A second test fails
when `RequestMappingHandlerMapping` contains an endpoint without a row, so new endpoints cannot skip it (a work item
that adds an endpoint must add a row, like `Rule9_AuditCoverageTest`). It runs against records that exist (program,
document, unapproved rule version, mapping with an open evidence request) so validation below the controller is
reached too. Bugs found and fixed: (a) any string containing NUL (program name, rule text, review note, requirement
title) made PostgreSQL fail with SQLSTATE 22021 and the response was 409 or 500: `ApiExceptionHandler` now answers 400
for every SQLSTATE class 22 (data exception) and keeps 409 for class 23; (b) `numeric_value: 1e400` in the
requirements PUT threw `JsonNodeException` (500): `RequirementListParser` requires a finite positive number; (c) a
300 000-character requirement text, ref or title was accepted: limits 50, 200 and 20 000 characters in the parser (the
LLM output schema is not changed, structured-output schemas do not accept `maxLength`); (d) the mappings
`jurisdiction` filter accepted any string: `@Pattern` like every other jurisdiction input; (e) file names (D-095); (f) a
NUL in the login username or the OIDC email logged a database stack trace: `findForLogin` returns empty. Rejected:
whitelisting any 500; a global filter stripping NUL (it would silently change user data).

**D-097 — Dependency pins.** `com.bucket4j:bucket4j_jdk17-core:8.22.0` (published 2026-11-03). 8.23.0 is the latest but was
published on 2026-11-22, four days before this check; the release before it was chosen to let it settle. `com.github.ben-manes.caffeine:caffeine` 3.2.4 and `spring-boot-starter-security-oauth2-client` 4.1.3 come
from the Boot BOM, unpinned in the POM. `bucket4j_jdk17-core` has no transitive dependencies. Test-only code uses
Nimbus (already transitive of the OAuth2 client) and the JDK HTTP server.

**D-098 — OIDC tests use a loopback provider, not explicit endpoints.** The spec asks for explicit endpoints so tests
need no network. Boot always performs discovery when `issuer-uri` is set, and an empty issuer fails startup
("issuer cannot be empty"), so the explicit-endpoint form cannot coexist with the shipped `application.yml`. The test
instead starts `FakeIdentityProvider` (JDK `HttpServer` on 127.0.0.1, random port) serving discovery, a JWK set and a
token endpoint, and sets `LEXVAY_OIDC_ISSUER_URI` to it. Nothing leaves the machine, and the shipped configuration,
issuer validation, signature check, state and nonce are all exercised (an ID token with another issuer, nonce or state
is refused). Rejected: `MockMvc` with `oidcLogin()` post-processors (they skip the code exchange and the account
lookup, which is the part that matters); mocking `ClientRegistrationRepository`.

**D-099 — Test isolation.** The shared test base sets the three rate limit budgets to 100000 through
`@TestPropertySource`; tests that need tight budgets, a Secure cookie, forwarded headers, a 1 MB upload limit or the
`oidc` profile declare their own and get their own cached Spring context (five extra contexts, about 8 seconds each).
`spring-security-test`'s `csrf()` is still not used; `TestCsrf.csrf()` stays.
