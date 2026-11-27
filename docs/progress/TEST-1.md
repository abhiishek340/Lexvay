# TEST-1 — Scripted fake model in the end-to-end backend: progress and test evidence

Branch `test-scripted-e2e-model`, end-to-end slot 11. Decisions: `docs/decisions/TEST-1.md` (D-280 to D-284).

## Done
- `lexvay.llm.fake-scripts` (`LEXVAY_LLM_FAKE_SCRIPTS`, comma-separated, empty by default): `FakeLlmStartupScripts`
  scripts the fake client from each file in order through `ScriptedFakeModel` (now takes a list of files; the seed's
  overload delegates). Ignored with one warning for the `anthropic` provider.
- `scripts/e2e.sh` sets it to `seed/fake-llm.yml,frontend/e2e/fixtures/fake-llm.e2e.yml`. The fixture holds the Nevada
  extraction (four fictional requirements, one numeric).
- `frontend/e2e/rules.spec.ts`: the contributor's Nevada version reaches *Needs approval* with four requirements
  listed; a separate test covers *Extracting* then *Extraction failed* for an Oregon version with no script; the
  reviewer test enters the Oregon list by hand and approves it (badge counts adjusted: Nevada stays waiting). Axe checks
  kept, one added for the Nevada state.
- `FakeLlmStartupScriptsTest`: two files in order, repeated load, no files, missing file, unknown provider fails
  startup, non-fake client never called with one warning (anthropic provider through `ApplicationContextRunner`),
  `ScriptedFakeModel.load` refuses a non-fake client.

## Test evidence
`make backend-test`:
```
[INFO] Tests run: 796, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
`LEXVAY_E2E_SLOT=11 make test`:
```
[INFO] Tests run: 796, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  11 passed (11)
      Tests  73 passed (73)
  ✓   8 e2e/rules.spec.ts:143:1 › a contributor enters a new rule version and its extraction proposes a list for approval (9.4s)
  ✓   9 e2e/rules.spec.ts:181:1 › a version whose extraction fails says so in words and leaves the list to a reviewer (8.1s)
  ✓  10 e2e/rules.spec.ts:208:1 › a reviewer enters the list by hand, corrects it, saves, reloads and approves it (11.0s)
  14 passed (1.3m)
```

## Noticed (not changed)
- The roadmap bullet says "profile `e2e`"; no such profile exists, the e2e stack is configured by environment
  variables in `scripts/e2e.sh`, so the property is set there (D-284).
- Match scripts are not loaded at startup (they need document ids); only extraction is (D-280).
