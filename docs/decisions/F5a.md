# F5a — User administration and audit export API (D-370 to D-379)

#### D-370 Users are never deleted; access is withdrawn with a flag

`app_user` gains `active boolean NOT NULL DEFAULT true` and `security_stamp integer NOT NULL DEFAULT 0` (V015). A user
who leaves is deactivated, not removed: every audit entry, review event and signature names its user (product rule 9),
and a foreign key to a deleted row would either break them or orphan them. There is no `DELETE` endpoint and no
`deleted_at` column. `app_user` keeps ordinary updates, with no immutability trigger: its role, activity, stamp and
password hash are meant to change, and every change is audited as an append-only event instead (rule 5 protects the
history, not the current state of an account).

Rejected: a soft-delete timestamp (a second flag meaning the same as `active = false`); making `app_user` append-only
with a new row per change (every `user_id` reference in the schema would need a "current version" join).

#### D-371 No email delivery: the administrator hands over the code

Creating a user returns a setup code in the response; nothing is sent. Lexvay is deployed inside a university's network
with no mail relay configured, and sending mail needs credentials in the configuration (`LEXVAY_*` secrets), templates,
bounce and retry handling, and a promise about deliverability that the product cannot keep. It would also put the
secret in a mailbox that outlives the 72-hour window. The administrator already knows the colleague and has a channel
(in person, the university's chat, a call). The same endpoint that issues a first code issues a replacement, which is the
password reset. The cost is that the administrator sees the code; the audit entry `USER_SETUP_CODE_ISSUED` records who
issued it and when, and `PASSWORD_SET` records that the person set a password, with the person as actor, so a code used
by somebody else is visible.

Rejected: an SMTP integration behind a `Mailer` seam (a second production path that no test or demo can exercise without
a server, for a feature the first customers do not ask for); emailing a link (same secret, more places it can be read);
letting the administrator choose the first password (the administrator would know it).

#### D-372 Setup codes: 32 random bytes, stored as SHA-256, valid 72 hours, one open per user

`SetupCodes.generate` returns 32 bytes from `SecureRandom` as 43 characters of unpadded base64url. The table
`user_setup_token` stores `token_hash` (lower-case hex SHA-256, unique, checked against `^[0-9a-f]{64}$`), `expires_at`,
`used_at`, `created_by` and the tenant. The code itself exists only in the response that creates it and in the person's
hands: it is never stored, logged or audited (`UserAdminApiTest` searches the whole audit log of the tenant for it).
SHA-256 is enough because the code has 256 bits of entropy, so there is nothing to brute force and no benefit from a slow
hash; it also gives a deterministic value to index and look up, which a salted bcrypt hash would not. Issuing a new code
marks every earlier unused code of the user as used in the same transaction, so at most one code works at a time. Rows
are immutable except `used_at` (`restrict_update_to_columns('used_at')`, and a delete trigger), like every other
history table (rule 5). The table has the tenant policy of V002 and a SELECT policy on the transaction-local
`lexvay.auth_lookup` flag, exactly as `app_user` has (D-034).

Rejected: bcrypt for the code (needs the id to find the row, so the code would have to carry it); a signed token such as
a JWT (cannot be revoked without a table anyway, and adds a key to manage); a six-digit code (guessable inside the
rate limit).

#### D-373 Setting a password: anonymous, one message, shared budget, 72 bytes

`POST /api/v1/auth/setup {code, password}` is open like login, needs the CSRF token like login, and draws on the login
rate-limit budget of the client address (`RateLimitFilter`), because guessing a code is guessing a password.

- The lookup is one `SELECT` joining the code to its user, under `lexvay.auth_lookup` (set in the same short transaction,
  as `findForLogin` does). Unknown, used and expired give the same empty answer and the same 400 "This setup code is not
  valid"; the response cannot tell which. The hash found is compared again with `MessageDigest.isEqual` in
  `SetupCodes.matches`. The database already matched on the hash, and the hash is a one-way image of a 256-bit secret, so
  the timing of an index probe reveals nothing usable; the second comparison exists so that no code path compares a secret
  with `equals`, and `SetupCodesTest` checks that none does.
- Then, under the code's tenant and in one transaction: claim the code (`UPDATE ... WHERE used_at IS NULL AND expires_at >
  now()`, so two requests with one code cannot both win; the loser gets the same 400), hash the password with the
  application's `PasswordEncoder`, set `password_hash`, increment `security_stamp` (every session the user has ends,
  D-374), and audit `PASSWORD_SET` with the user as actor and no detail. The endpoint does not sign anybody in: the client
  goes to the sign-in screen, so sessions are created in exactly one place (login and OIDC).
- A password that breaks a rule does not use up the code. Rules: at least 12 characters, not equal (ignoring case) to the
  email, and **at most 72 bytes in UTF-8**.

**Disagreement with the roadmap:** spec point 3 asks for 12 to 128 characters. The application's encoder is Spring's
delegating encoder with bcrypt as default, and bcrypt refuses more than 72 bytes (`password cannot be more than 72
bytes`, measured with Spring Security 7.1.3: 72 characters pass, 73 throw). A 128-character limit would turn every
password between 73 and 128 characters into a 500. The limit is therefore 72 bytes, and the 400 says so. Raising it
means choosing another default encoder (PBKDF2 or Argon2 behind the same delegating encoder, which would still verify
existing `{bcrypt}` hashes) or pre-hashing, which changes how logins verify; that is an architecture decision for the
orchestrator, not for this item.

Also not done, though point 1 lists it: the `auth_lookup` policy on `user_setup_token` covers SELECT only, not UPDATE.
Nothing needs the update: the claim runs under the tenant, in the transaction that sets the password. An UPDATE policy
open to a flag would be a door with nothing behind it (`Rule7_UserSetupTokenIsolationTest` proves that the flag reads and
cannot write).

Rejected: looking the code up by user and email (needs an identifier the person may not know, and enumerates accounts);
signing the person in at the end (a second place that creates sessions); a lower-case-only or digit-only code (entropy).

#### D-374 Session revocation: a stamp in the session, a 15-second per-instance cache

`LexvayUser` carries the `security_stamp` the user had when the session was issued. `TenantContextFilter` asks
`UserStampCheck` on every authenticated request whether `(active, role, security_stamp)` of the user still match. The
answer comes from a Caffeine cache (per instance, keyed by user id, `expireAfterWrite` 15 seconds, at most 10 000 users);
a miss runs one statement (`AppUserRepository.findAccess`) under the user's tenant. A mismatch, an inactive user or a
user that no longer exists invalidates the HTTP session and answers 401 "Your access changed. Sign in again."

- **Where the stamp moves:** role change, deactivation, reactivation, password set (a new password ends the sessions that
  the old one opened). The role is compared as well, so a role changed by hand in the database ends sessions even without
  a stamp change.
- **This instance:** the service evicts the user after the transaction commits (`evictAfterCommit`; evicting earlier would
  let a request that arrives before the commit cache the old values for a full window), so the next request is refused.
- **Another instance:** it learns after at most **15 seconds**. This is recorded in `docs/OPERATIONS.md`. Fifteen seconds
  because the cost is one statement per active user per instance per window (about four a minute, below any budget in
  `docs/PERF.md`), while the exposure is the time a withdrawn administrator keeps working through an instance that did not
  make the change; shortening it to a few seconds multiplies the lookups for no change in the kind of risk, and
  lengthening it past a minute stops being "within seconds". Not configurable: a setting would be a way to switch revocation
  off by mistake, and nobody has asked to tune it.
- **Reads stay at one connection:** the lookup is outside the request's transaction (a separate checkout) but at most once
  per user per 15 seconds. `ReadPathConnectionsTest` makes a warm-up request of the same user before every measured one and
  still asserts exactly one checkout for the read.
- The check also answers the session endpoint (`GET /api/v1/auth/session`) with the 401 for a stale session, as any
  request carrying one. The session is gone after that answer, so the next call reports `authenticated: false`. F5b should
  treat a 401 from the session call as "signed out".
- Login and logout are not affected: the login filter answers before this filter runs, so a user with a stale cookie can
  sign in again.

Rejected: checking the database on every request (a second connection per read, against D-110); a shared cache or message
channel between instances (new infrastructure for a 15-second gain); invalidating sessions from a session store (Tomcat's
sessions are in memory per instance and cannot be listed or reached from another); short-lived tokens (the session is a
cookie, by design).

#### D-375 Rules for changing a user

`PUT /users/{id}/role` and `PUT /users/{id}/active` (ADMIN only). An administrator cannot change their own role or
deactivate themselves (409 "You cannot change your own role", "You cannot deactivate your own account"). Setting a value a
user already has is not a change: 200 with the user, no audit entry, no stamp increment, so a repeated click ends nobody's
session. Each real change increments the stamp, is audited in the same transaction (`USER_ROLE_CHANGED` with `from` and
`to`; `USER_DEACTIVATED`; `USER_REACTIVATED`; each with the email) and evicts the user from the cache. An unknown id, or an
id of another tenant (row-level security hides it), is 404. A user may be created with any role, administrator included.
`GET /users` keeps today's four fields for every role and adds `active`, `createdAt` and `passwordSet` for administrators
only (`@JsonInclude(NON_NULL)` on one record, filled by two factories); it is ordered by display name, then email and id.
`findForLogin` ignores inactive users, so the login error stays the generic "Email or password is incorrect"; OIDC refuses
them with the existing 403 "No Lexvay account for this identity" (D-091). A user created but without a password has a null
hash and cannot sign in (`DelegatingPasswordEncoder.matches` answers false for it; tested).

A setup code can be issued for a user who already has a password, and for an administrator: that is the reset. It lets
an administrator set another person's password and so act as them. That power already exists in effect (an administrator
can change any role and create users with the ADMIN role), and here it is attributable: `USER_SETUP_CODE_ISSUED` names the
administrator and `PASSWORD_SET` shows the account owner's first sign-in after it.

#### D-376 The last active administrator cannot be removed, even by two at once

A role change away from ADMIN or a deactivation of an administrator is refused with 409 "At least one active
administrator is required" when the target is the only active administrator. Because one administrator can only remove
another (D-375), the rule is reached only when the caller is no longer an administrator (a stale session within the
revocation window) or when two administrators remove each other at the same moment. Each of those two would read "the
other one is still there" and both would commit. So every role or access change first runs `SELECT id FROM app_user
WHERE role = 'ADMIN' AND active ORDER BY id FOR UPDATE`, which holds all active administrators, and then locks the target
row. The second transaction waits, then re-reads under READ COMMITTED and sees the first one's result. Locks are always
taken in that order (administrators by id, then the target), so two changes cannot wait for each other. The statement
costs a few rows per tenant.

`LastAdministratorConcurrencyTest` runs 30 rounds of two threads on a barrier, each removing the other, half by role and
half by deactivation, and asserts after every round that exactly one administrator is left, one request answered 200 and
the other 401 (its session was already stale) or 409. With the lock on the administrators removed, the test fails (checked: PostgreSQL then reports a deadlock between
the two target locks, in the first rounds). `UserAdminApiTest` reaches the 409 deterministically by making the caller's role stale in the
database behind the cache, which is the cross-instance case.

Rejected: serializable isolation for these two statements (retries in the caller for a table that changes a few times a
year); a PostgreSQL advisory lock (a second mechanism where row locks do the job); a constraint trigger counting
administrators (a trigger on `app_user`, which D-370 keeps ordinary; and the application still has to explain the
refusal).

#### D-377 Audit export: streamed through `CsvWriter`, audited after the last row

`GET /api/v1/audit.csv` (ADMIN) takes the filters of the list (`action`, `entityType`, `entityId`, `userId`, `from`,
`to`), now bound once in `AuditFilter` (a record bound from the query string, with the predicates in one place), so the
file and the screen cannot disagree about a filter. `AuditRepository.streamForExport` is one `@Transactional(readOnly =
true)` statement over `audit_log LEFT JOIN app_user` executed with `JdbcClient.withFetchSize(500)` and delivered row by
row to a callback; the PostgreSQL driver then reads through a cursor, 500 rows at a time. `CsvWriter` gained a mode that
writes each record to an `Appendable` as it is added (the in-memory mode of F8 is unchanged), and `AuditExportService`
connects the two: each row is one CSV record written to the response's `OutputStreamWriter`. Nothing holds more than a
fetch window. Columns: time (ISO-8601 UTC, `Instant.toString`), actor (display name, "System" for work no person started),
action, record type, record id, details (the JSON as stored, as text). Newest first, as on the screen. `Content-Type:
text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="lexvay-audit-<yyyy-mm-dd>.csv"` (UTC date, built by
the same `CsvWriter.attachment` as the register's).

- The response is opened when the first record is ready, not before: a request that fails before any row (a filter value
  the database refuses, such as a NUL character, which is the 400 of D-096) is an ordinary problem detail and not an
  error body inside a download called `lexvay-audit-....csv`.
- Audited as `AUDIT_EXPORTED` (entity type `audit_log`, no entity id) with the filters, `rowCount` and `complete`, after
  the stream ends, in its own statement. When the stream fails midway (the client left, the connection dropped), rows have
  already left the system, so the entry is written with `complete: false` and the rows delivered; an export that failed
  before the first row writes nothing. This is the one detail beyond the roadmap's "filters and the row count". It does not
  list itself (it is written after the read).
- Runs on the request thread: `StreamingResponseBody` would run the stream on another thread, where the tenant, held in a
  thread-local, is not set and row-level security would show nothing (or, worse, bind the wrong tenant).
- Heap proof (`AuditExportMemoryTest`): 20 000 entries of 3.3 KB are a 63 MB file (measured: live heap 55 MB before, 56 MB at the highest sample while serving). The test measures live data (a
  `System.gc()` then `MemoryMXBean` used heap) before the request and repeatedly while it is served, and requires the
  largest difference to stay under 32 MB. The roadmap asks for before and after a GC, which would also pass for a writer
  that held everything and let go at the end; the peak catches that.

Known cost: audit rows have no index on `(tenant_id, created_at, id)`, so the database sorts the matching rows before the
first one is sent, as it does for the list. That is memory in PostgreSQL (it spills to disk), not in the application, and
not a hot path; an index is an easy follow-up if a tenant's log reaches millions of rows. Rejected: building the file in
memory (D-273's choice, right for the register, which is bounded by programs times jurisdictions, wrong for a log that only
grows); `COPY ... TO STDOUT` (bypasses row-level security reasoning and `CsvWriter`'s formula protection).

#### D-378 Where things live

`UserAdminService` (create, issue a code, role, active), `PasswordSetupService` (the anonymous setup), `SetupCodes`
(generate, hash, compare), `SetupTokenRepository`, `AppUserRepository` (extended; `findAll` replaced by `listAccounts`,
which has the new columns), `UserView`, `UserController`, `AuthController.setup` in `tenant` and `auth`;
`UserStampCheck`, `AccessState`, `TenantContextFilter` in `auth`; `AuditController` (list and export), `AuditExportService`,
`AuditFilter`, `AuditRepository` in `audit`. `UserSetup` (`{user, setupCode, expiresAt}`) is the response of both create
and issue-code, 201. `RegisterController` now uses `CsvWriter.TEXT_CSV` and `CsvWriter.attachment` instead of its own copies.
All five new mutating endpoints are in `Rule9_AuditCoverageTest`, the export is in `AUDITED_READ_ENDPOINTS`, and all new
endpoints have rows in `InputValidationTableTest` (including a signer refused, an anonymous refused, and every malformed
body of the setup endpoint).

#### D-379 What the tests prove, and what they cannot

Every rule of the roadmap has a test (`docs/progress/F5a.md` maps them). Two limits. (1) The 15-second delay between
instances cannot be waited for in a test run; `UserStampCheckTest` proves it with a fake clock (14 seconds: not seen, 16:
seen) and `UserAdminApiTest` proves the consequence at the service (a change made behind the cache). (2) The constant-time
comparison cannot be observed from a test; `SetupCodesTest` proves the behaviour (any position of a difference fails) and
reads the source to prove that no secret is compared with `equals`, which is a structural guard and nothing more.
Follow-ups outside this item: the screens (F5b); the password length limit of D-373.
