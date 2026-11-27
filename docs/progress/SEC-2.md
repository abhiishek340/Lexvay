# SEC-2 progress

Argon2id passwords and the audit list index, backend only (no migration; D-400 to D-407 in `docs/decisions/SEC-2.md`).

## Built

- `PasswordEncoders.application()`: `DelegatingPasswordEncoder`, default `argon2` (Argon2id, 32 MiB, 4 passes, one lane, about
  73 ms per hash here), `bcrypt` verified with or without its prefix, nothing else verifies. `bcprov-jdk18on` 1.87 pinned.
- `LexvayUserDetailsService` is also the `UserDetailsPasswordService`: a successful sign-in with a hash that is not the current
  kind replaces it (one guarded UPDATE, `PASSWORD_REHASHED`, no stamp change).
- Password rule 12 to 128 characters (code points); the 72-byte rule of D-373 is gone.
- `docs/OPERATIONS.md` "Users and sessions": hash and length, and the ADMIN setup-code power with the advice to use OIDC for SIGNER
  and ADMIN accounts.
- Audit plans: `IndexUsageTest` with about 80 000 audit rows. The index the roadmap names exists since V001
  (`audit_log_newest_idx`), so no V017.

## Where each point of the roadmap is tested

| Point | Test |
|---|---|
| 1 encoder, bcrypt still verifies, new hashes `{argon2}` | `PasswordEncodersTest`, `SeedLoaderTest` (hash prefix and sign-in with the demo password), `PasswordSetupApiTest` |
| 2 upgrade on login | `PasswordRehashTest` (prefixed and unprefixed bcrypt, once, audit without secrets, stamp unchanged, wrong password, concurrent change) |
| 3 length | `PasswordSetupApiTest` (128 accepted, 129 refused in ASCII, 2-byte and 4-byte characters, 128 multi-byte characters sign in, last character counts), `PasswordEncodersTest` (73, 128, 2 000 characters) |
| 4 audit index | `IndexUsageTest` (list and export plans, `audit_log_newest_idx` candidate), plans in `docs/PERF.md` and D-403 |
| 5 docs | `docs/OPERATIONS.md` |

Checked by hand: a throwaway PostgreSQL 18.7 with the perf data shape and an added `(tenant_id, created_at DESC, id DESC)` index
chose the same export plan as without it.

## Results

`make backend-test` (after merging origin/main with FIX-4): `Tests run: 1035, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

`make perf`: `EndpointLatencyTest` and `IndexUsageTest` both `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`;
the slowest p95 is 6.63 ms (program overview), budget 40 ms.

`LEXVAY_E2E_SLOT=5 make e2e`: `1 skipped`, `18 passed (1.4m)`.

## Deviations and follow-ups (see D-400, D-403)

- No `V017`: the index named in the roadmap already exists (V001). The export's sort is the planner's choice for reading a quarter
  of a tenant's rows, not a missing index.
- Parameters are explicit (32 MiB, 4 passes) rather than Spring's defaults, which measured 14 to 17 ms, below the 50 ms floor.
- Review change (D-407): every hash goes through a fair gate of `lexvay.security.password-hash-concurrency` permits (default
  `max(2, processors)`); a caller that waits 2 s gets 503 "Sign-in is busy. Try again in a moment." with `Retry-After: 2` and nothing is
  hashed. Tests: `PasswordHashGateTest`, `PasswordHashBusyApiTest`.
