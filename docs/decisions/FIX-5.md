# FIX-5 — Logout audit actor and the work queue's latest-finished predicate

#### D-410 `LOGOUT` takes its actor from the `Authentication` Spring hands the success handler

`LogoutFilter` reads the authentication once, runs its handlers (`SecurityContextLogoutHandler` clears the security context
and the session), and then calls the success handler with the authentication it read first. The handler already used that
argument to find the user and the tenant, but wrote the entry through `AuditService.record`, which asks `CurrentUser` for the
actor, and by then the context is empty. The entry therefore had `user_id = NULL` (rule 9: "who" was missing). The handler now
calls `AuditService.recordAs(user.id(), "LOGOUT", "app_user", user.id(), Map.of())` inside `TenantContext.runAs(user.tenantId())`.
The actor and the record are the same person, as for `LOGIN_SUCCEEDED`.

Rejected: a `LogoutHandler` placed before the context-clearing one that stashes the user in a request attribute (a second place
that has to run in the right order, to hand over what the success handler already receives); reading `SecurityContextHolder`
in a handler that Spring does not guarantee to run first.

#### D-411 The work queue's `latest` CTE uses `AssessmentRepository.LATEST_FINISHED`

The CTE kept its own copy of "the program's latest finished assessment" (status FINISHED, newest `finished_at`, then newest
`created_at`). It now takes the predicate from `AssessmentRepository.LATEST_FINISHED` (`%7$s` in `SHARED`), as the coverage
matrix and the signing gate already do, so a change to the ordering reaches the queue with them. The lateral subquery keeps
the alias `a` that the constant expects. The statement the database runs is the same text as before, so `IndexUsageTest` and
the latency budget are unchanged. Not touched: `MappingRepository.latestAssessmentsUsing`, which has the same ordering as a
correlated subquery over `a2`; the constant carries `LIMIT 1` and the alias `a`, so it does not fit there, and it is outside
this item.

#### D-412 Parity test: the queue reads the same assessment as `latestFinished`

`WorkQueueSeedParityTest` loads the seeded Northfield tenant from a copy of the manifest and, for the SIGNER, REVIEWER and
ADMIN, asserts that every queue item naming an assessment names the one `AssessmentRepository.latestFinished` returns for its
program, that the badge total equals the sum of the list's counts, and that the check was not vacuous. The queue before and
after the switch was also compared on the same seed (item kinds, programs, jurisdictions, counts and id list lengths for
four roles: 18 lines, identical; see `docs/progress/FIX-5.md`). The test compares against the repository method, not a
frozen copy of the old ordering, so it stays valid when the ordering is changed on purpose. The READY_TO_SIGN parity test
(`WorkQueueApiTest.readyToSignAgreesWithTheSigningEndpointForEveryProgramAndJurisdiction`) is unchanged and green.

#### D-413 Older `LOGOUT` entries keep no actor

The audit log is append-only (rule 5), so entries recorded before this fix keep `user_id = NULL`; nothing back-fills them.
The audit sentence in the screen (UI2-3's `audit-sentence.ts`, not on `main` when this item was written) still has to name the
account for those entries, and may drop the workaround for entries that have an actor. That frontend change belongs to
whichever item brings `audit-sentence.ts` in.

#### D-414 Signing out without a session records nothing

A request to the logout URL without a session has no `Authentication`, no tenant and no user to name; the entry would have no
tenant to be recorded in. The handler still answers 204. `LogoutAuditTest.signingOutWithoutASessionRecordsNothing` asserts
this for a request with no cookie and one with an unknown session id.
