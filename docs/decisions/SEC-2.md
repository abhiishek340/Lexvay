### SEC-2 — Argon2id passwords and the audit list index (D-400 to D-409)

#### D-400 The application encoder is Argon2id behind the delegating encoder, at 32 MiB, 4 passes, one lane

`PasswordEncoders.application()` (replacing `PasswordEncoderFactories.createDelegatingPasswordEncoder()` in
`SecurityConfig`) is a `DelegatingPasswordEncoder` whose encoding id is `argon2` and whose map holds exactly two
encoders, `argon2` and `bcrypt`. New hashes look like `{argon2}$argon2id$v=19$m=32768,t=4,p=1$...`. The default for
matching a value without a prefix is bcrypt, so a hash written before F5a added the prefix keeps verifying.

**Parameters, measured on this machine** (Apple silicon laptop, JDK 21.0.8, Spring Security 7.1.3 with Bouncy Castle
1.87, mean of 15 verifications after warm-up, other work running):

| Encoder | Verify |
|---|---:|
| bcrypt, cost 10 (what `{bcrypt}` hashes cost today) | 58 to 62 ms |
| `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (16 MiB, 2 passes) | 14 to 17 ms |
| OWASP minimum, 19 MiB, 2 passes | 17 to 20 ms |
| OWASP alternative, 46 MiB, 1 pass | 29 to 44 ms |
| 46 MiB, 3 passes | 83 ms |
| 19 MiB, 7 passes | 70 ms |
| **32 MiB, 4 passes, 1 lane (chosen)** | **73 ms** |
| 64 MiB, 3 passes | 116 to 123 ms |

The Spring defaults are below the 50 ms floor the roadmap sets for this item, so they are not used. 32 MiB with 4 passes
has more memory and more passes than every combination OWASP's Password Storage Cheat Sheet lists (46 MiB with 1 pass,
19 MiB with 2, 12 MiB with 3, 9 MiB with 4, 7 MiB with 5; all with one lane), takes 73 ms, which is about what bcrypt
took, so sign-in latency and the test suite do not change, and needs 32 MiB of heap while it runs. The salt is 16 bytes
and the hash 32. The parameters are written inside every hash, so changing them later makes `upgradeEncoding` true for
older hashes and D-401 replaces them at the next sign-in; no migration is needed.

Only `argon2` and `bcrypt` verify. Spring's `createDelegatingPasswordEncoder()` also accepts `{noop}`, `{sha256}`,
`{MD4}`, `{ldap}` and others, which would make a value such as `{noop}secret` in `app_user.password_hash` a working
password for anyone able to write that column. `PasswordEncodersTest` proves that those, a plain-text value, an empty
value and null do not verify.

**Dependency (D-002 addendum).** `Argon2PasswordEncoder` needs Bouncy Castle's low-level API at run time and Spring Security
declares it optional. `org.bouncycastle:bcprov-jdk18on` is pinned to **1.87** (the newest release on repo1.maven.org,
published 2026-11-11, sixteen days before this work; `maven-metadata.xml` lists 1.81.1 to 1.87), as the property
`bouncycastle.version` in `backend/pom.xml`. No Bouncy Castle artifact was on the classpath before.

**Known limit.** Each concurrent sign-in or password set holds 32 MiB of heap. The login rate limit (10 a minute per
client address and instance, D-092) bounds one source, and the hashing takes the CPU before it takes the heap, but
many addresses at once could add up: Tomcat's 200 request threads would be 6.4 GiB against a default heap of about 1.5 GiB
(2 GiB task, `MaxRAMPercentage=75`), and the JVM exits on out-of-memory (`ExitOnOutOfMemoryError`). Bcrypt used 4 KiB per
hash, so this is new. A small fix is a semaphore around the Argon2 encoder sized to the processors; it is not part of this
item, which asked for the encoder and its upgrade path, and is reported to the orchestrator as a follow-up.

Rejected: Spring's defaults (14 to 17 ms, below the floor); 64 MiB with 3 passes (116 to 123 ms, twice the heap per
sign-in for a cost this application does not need); PBKDF2 (no memory hardness); scrypt (Spring's encoder is slower to
tune and OWASP ranks Argon2id first); keeping `createDelegatingPasswordEncoder()` and only changing its default (it
would keep every legacy verifier accepted).

#### D-401 A hash that is not the current one is replaced at the next successful sign-in, by Spring's own hook

`LexvayUserDetailsService` implements `UserDetailsPasswordService` as well as `UserDetailsService`. Spring Security's
`DaoAuthenticationProvider` calls `updatePassword(user, newEncodedPassword)` only after the password matched and only
when `PasswordEncoder.upgradeEncoding(storedHash)` is true: for a `{bcrypt}` hash, a hash with no prefix, and an `{argon2}`
hash made with other parameters. A hash of the current kind is left alone, so a second sign-in writes nothing.

The hook runs before the authentication filter's success handler and before any tenant is known, so `updatePassword`
sets the user's tenant, then in one transaction runs one statement and one audit entry:

- `UPDATE app_user SET password_hash = :new WHERE id = :id AND password_hash = :old`. The condition on the old hash is the
  guard against a password set between loading the account and now (a setup code used in that moment): the update then
  changes nothing, nothing is audited, and the sign-in carries on with the account as loaded. `security_stamp` does not
  move, because the person has not changed their password and other sessions of theirs should not end.
- `PASSWORD_REHASHED` on `app_user`, actor the user (`recordAs`, as `PASSWORD_SET` does), detail
  `{"from": "bcrypt", "to": "argon2"}`: algorithm names read from the hash prefix (`PasswordEncoders.algorithmOf`, which
  names an unprefixed hash bcrypt), never a hash or a password. `PasswordRehashTest` searches the whole audit log of the
  tenant for the new hash and the password.

A failure of this write fails the sign-in with the generic 401 and does not leave a half-written state; the database being
unavailable would have failed the audit entry of the success handler anyway. Not done: replacing the hash in a background
job (needs the password, which exists only during the sign-in); replacing it in the success handler (a second place that
knows about hashes, and after the session exists).

`PasswordRehashTest` covers: a prefixed bcrypt hash replaced and audited once, an unprefixed one replaced, a wrong
password changing nothing, the stamp not moving, a second sign-in writing nothing, and the concurrent change not being
overwritten.

#### D-402 Passwords are 12 to 128 characters; the 72-byte rule of D-373 is gone

Argon2id has no 72-byte limit, which was the reason D-373 departed from the roadmap. `PasswordSetupService` now requires
at least 12 and at most 128 characters, counted as code points (an emoji is one character, as for the minimum), and the
400 says "Password must have at most 128 characters". The cap is not a technical limit of the encoder: it bounds what one
request can ask the server to hash and keeps the message the same for the person whatever the script they type in.
Tests: 128 characters accepted, 129 refused (ASCII, two-byte and four-byte characters), a 128-character password of 2-,
3- and 4-byte characters (381 bytes) accepted and then signing in only with all of it, and the last character mattering.

The request size limit (`@Size(max = 1000)` UTF-16 units on the setup endpoint) is unchanged: 128 four-byte characters
are 256 units.

#### D-403 No audit index is added: the index the roadmap names has existed since V001

The roadmap asks for `audit_log (tenant_id, created_at DESC)` if the audit list or export sort without it. V001 already
creates `audit_log_newest_idx ON audit_log (tenant_id, created_at DESC)`, the same columns in the same order, so
`audit_log_tenant_created_idx` would be a second copy of it and V017 is not created (the number stays free).

`IndexUsageTest` now adds audit volume to the perf database through `PerfSeed.addAuditVolume` (300 entries for each of
about 200 tenants and 20 000 more for the tenant the statements run as: about 80 000 rows, inserted oldest first as a log
grows) and runs `EXPLAIN (ANALYZE, BUFFERS)` of the real statements (`AuditRepository.listSql` and `exportSql`, made
public for this) as the application role under row-level security. Plans are written to `backend/target/perf/audit-plans.txt`
and summarised in `docs/PERF.md`:

- **List** (limit 100) with no filter, with one action and with a 30-day range: `Index Scan using audit_log_newest_idx` and
  an `Incremental Sort` on `id` over the already ordered rows; 11 to 56 buffers, 0.06 to 0.09 ms. The test fails if a list
  statement scans `audit_log` sequentially or sorts every matching row, and `audit_log_newest_idx` is a candidate in the
  kept-index table.
- **Export** of the 20 000 rows of the tenant (25 percent of the table): `Bitmap Heap Scan` and a full in-memory `Sort`
  (3 MB, 8.6 ms in all); with a 30-day range the planner uses `audit_log_newest_idx` for the bitmap and sorts 3 500
  rows (1.6 ms). The sort is the planner's choice for reading a quarter of the table, not a missing index. A throwaway
  PostgreSQL 18.7 with the same shape of data and an extra `(tenant_id, created_at DESC, id DESC)` index chose the same
  plan, so that index would add write cost to the busiest append-only table for no change in the plan. The export reads
  every matching row by design and the sort costs memory in PostgreSQL (about 150 bytes a row, spilling to disk beyond
  `work_mem`), not in the application; D-377's known cost stands, now with numbers. The test fails if an export scans
  `audit_log` sequentially.

#### D-404 What is documented about administrators

`docs/OPERATIONS.md`, "Users and sessions", says that any administrator can issue a setup code for any user, a signer
included, and could therefore sign in as that person; that both actions are audited; and that production deployments should
use OIDC for SIGNER and ADMIN accounts. Nothing in the code changes: D-375 already records the power and its audit trail.

#### D-405 Where things live

`PasswordEncoders` (`auth`) builds the encoder and names the algorithm of a hash; `LexvayUserDetailsService` (`auth`) holds
the upgrade; `AppUserRepository.rehashPassword` is its statement; `LexvayUser.withPasswordHash` returns the principal the
session keeps. `AuditRepository.listSql` and `exportSql` and `AuditFilter.where` are public so that `IndexUsageTest` runs the
text the application runs. `PerfSeed.addAuditVolume` is test code.

#### D-406 What the tests prove, and what they cannot

`PasswordEncodersTest` (parameters in the hash, every legacy form, no other verifier, long and multi-byte passwords),
`PasswordRehashTest` (D-401), `PasswordSetupApiTest` (D-402 and `{argon2}` after setting a password), `SeedLoaderTest` (the
seed's users have `{argon2}` hashes and the demo password signs in), `IndexUsageTest` (D-403). The hash time is not
asserted: a timing assertion would fail on a loaded machine and say nothing about the cost parameters, which
`PasswordEncodersTest` pins by reading them out of the hash. The measurement in D-400 is a one-off.
