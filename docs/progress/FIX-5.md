# FIX-5 progress

- Logout: `SecurityConfig` records `LOGOUT` with `recordAs(user.id(), ...)` (D-410). `LogoutAuditTest` (real HTTP, session
  cookie, CSRF header) asserts actor, `entity_type = app_user`, `entity_id` for two users and that an anonymous logout records
  nothing. Run against the unfixed handler, the two tests that expect an actor failed.
- Work queue: `latest` CTE uses `AssessmentRepository.LATEST_FINISHED` (D-411); `WorkQueueSeedParityTest` (D-412).
- Before/after on the seed (one-off scratch test, not kept): the SIGNER, REVIEWER, CONTRIBUTOR and ADMIN views (kind, program,
  jurisdiction, count, id list lengths; 18 lines) are identical with the old and the new statement.
- Frontend: UI2-3 had merged by the time of the PR; `audit-view.ts` keeps its fallback for `LOGOUT` only, with a spec for recorded and old entries (D-413). `make frontend-test`: 28 files, 282 tests passed.
- Test order: `runPending()` before `fake.reset()` in 11 test classes (D-415).
- Not done: `MappingRepository.latestAssessmentsUsing` still has its own copy of the ordering (D-411).
