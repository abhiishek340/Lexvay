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

#### D-413 Older `LOGOUT` entries keep no actor; the audit view reads them as before, for `LOGOUT` only

The audit log is append-only (rule 5), so entries recorded before this fix keep `user_id = NULL`; nothing back-fills them.
`audit-view.ts` (UI2-3, D-340 to D-349) attributed an entry without a user to the account it is recorded on for three actions of
a person on their own account. Only `LOGOUT` ever lacked a user (`LOGIN_SUCCEEDED` and `PASSWORD_SET` are recorded with their
actor), so the rule is now narrowed to `LOGOUT` and documented as the reading of entries recorded before FIX-5; an entry that
carries its actor takes the first branch. `audit-view.spec.ts` covers a recorded sign-out, an old one, and that another action
without a user is still the System. `audit-sentence.ts` needed no change: it takes the actor's name as input.

#### D-414 Signing out without a session records nothing

A request to the logout URL without a session has no `Authentication`, no tenant and no user to name; the entry would have no
tenant to be recorded in. The handler still answers 204. `LogoutAuditTest.signingOutWithoutASessionRecordsNothing` asserts
this for a request with no cookie and one with an unknown session id.

#### D-415 Tests drain queued jobs before they clear the fake model

Several test classes began with `fake.reset(); runner.runPending();`. `runPending()` runs every queued job of every tenant,
including jobs other classes left behind, and each of those calls the fake model after the reset; a class that then asserted
a global `fake.callCount(...)` (`RuleVersionApiTest.createStoresRuleTextAndExtractsRequirements`: "expected 1 but was 4" in CI)
failed depending on the order the classes ran in. The two calls are swapped to `runner.runPending(); fake.reset();` in
`AbstractAssessmentTest` (which the other counting tests extend: `AssessmentApiTest`, `AssessmentUsageApiTest`,
`RuleVersionChangeTest`, `Rule1_FindingValuesTest`, `Rule3_NoModelCallForNumericTest`, `SearchTermsMatchingTest`),
`RuleVersionApiTest`, `Rule5_ApprovedRequirementsRejectEditsTest`, `EvalServiceTest`, `EvalModelTest` and the classes that load
the seed (`SeedLoaderTest`, `CoverageSeedTest`, `RegisterSeedTest`, `RuleVersionDiffSeedTest`, `FindingCountsParityTest`,
and this item's `WorkQueueSeedParityTest`). Draining first leaves the fake empty when the test starts, whatever ran before.
