# FIX-5 progress

- Logout: `SecurityConfig` records `LOGOUT` with `recordAs(user.id(), ...)` (D-410). `LogoutAuditTest` (real HTTP, session
  cookie, CSRF header) asserts actor, `entity_type = app_user`, `entity_id` for two users and that an anonymous logout records
  nothing. Run against the unfixed handler, the two tests that expect an actor failed.
- Work queue: `latest` CTE uses `AssessmentRepository.LATEST_FINISHED` (D-411); `WorkQueueSeedParityTest` (D-412).
- Before/after on the seed (one-off scratch test, not kept): the SIGNER, REVIEWER, CONTRIBUTOR and ADMIN views (kind, program,
  jurisdiction, count, id list lengths; 18 lines) are identical with the old and the new statement.
- Frontend: not touched. `frontend/src/app/screens/audit/audit-sentence.ts` is not on `main` yet (D-413).
- Not done: `MappingRepository.latestAssessmentsUsing` still has its own copy of the ordering (D-411).
