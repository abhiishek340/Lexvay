# F5a progress

User administration and audit export API, backend only (V015, D-370 to D-379 in `docs/decisions/F5a.md`).

## Built

- `V015__user_administration.sql`: `app_user.active` and `security_stamp`; `user_setup_token` with the tenant policy, an
  `auth_lookup` SELECT policy, an open-codes index, and immutability except `used_at`.
- `POST /users`, `POST /users/{id}/setup-code`, `PUT /users/{id}/role`, `PUT /users/{id}/active` (ADMIN);
  `GET /users` with `active`, `createdAt`, `passwordSet` for administrators; `POST /auth/setup` (anonymous, login budget).
- Session revocation: stamp in `LexvayUser`, `UserStampCheck` (15 s per-instance cache) in `TenantContextFilter`.
- `GET /audit.csv` (ADMIN), streamed through `CsvWriter`, audited as `AUDIT_EXPORTED`.

## Where each point of the roadmap is tested

| Point | Test |
|---|---|
| 1 schema, RLS, immutability | `Rule7_UserSetupTokenIsolationTest`, `Rule5_UserSetupTokenImmutabilityTest`, `MigrationsApplyCleanlyTest`, `Rule7_EveryTableHasPolicyTest` |
| 2 create, codes, email rules | `UserAdminApiTest` (create, duplicate across tenants, lower case, new code closes old ones, audit without the code), `SetupCodesTest` |
| 3 set password | `PasswordSetupApiTest` (once, same message for unknown, used and expired, rules, concurrent use, CSRF), `RateLimitApiTest.guessingSetupCodesDrawsOnTheLoginBudgetOfTheAddress` |
| 4 role and access | `UserAdminApiTest` (self, last administrator behind the cache, audit, no-op, login of inactive), `LastAdministratorConcurrencyTest` (two threads, 30 rounds) |
| 5 revocation | `SessionRevocationTest` (real HTTP), `UserStampCheckTest` (fake clock: 14 s not seen, 16 s seen), `ReadPathConnectionsTest` (warm-up, one checkout) |
| 6 reads | `UserAdminApiTest.administratorsSeeTheStateOfEveryUser...`, `UserApiTest` |
| 7 export | `AuditExportApiTest`, `AuditExportMemoryTest`, `CsvWriterTest` |
| 8 guards | `InputValidationTableTest` (224 rows, every new endpoint), `Rule9_AuditCoverageTest`, `ForbiddenWordsTest` |

Checked by hand: removing the administrator lock from `lockActiveAdministrators` makes `LastAdministratorConcurrencyTest`
fail (deadlock between the target locks). `AuditExportMemoryTest` on this machine: 20 001 records, 63 MB, live heap 54 MB
before and 57 MB at the highest sample while serving.

## Results

`make backend-test` (after merging origin/main with F7c and FIX-3): `Tests run: 1003, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

`make perf`: `EndpointLatencyTest` and `IndexUsageTest` both `BUILD SUCCESS`; every read endpoint stays at one connection per
call and the slowest p95 is 14.48 ms (program screen, three requests); budget 40 ms.

`LEXVAY_E2E_SLOT=5 make e2e`: `1 skipped`, `18 passed (1.9m)`, exit 0.

## Deviations from the roadmap (see D-373, D-377)

- Password limit is 72 bytes, not 128 characters: bcrypt refuses more.
- The `auth_lookup` policy on `user_setup_token` is SELECT only; nothing needs UPDATE.
- `AUDIT_EXPORTED` also records `complete`.

## Deferred

The screens (F5b). Raising the password limit needs an encoder decision.
