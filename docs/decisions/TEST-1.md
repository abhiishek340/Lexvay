# TEST-1 — Scripted fake model in the end-to-end backend: decisions

**D-280 — `lexvay.llm.fake-scripts` loads fake-llm.yml files at startup.** The property is a list of paths, empty by
default (`LEXVAY_LLM_FAKE_SCRIPTS`, comma-separated, in `.env.example` and `application.yml`). `FakeLlmStartupScripts`
(a component in `com.lexvay.seed`, `@PostConstruct`) reads the files in order through `ScriptedFakeModel`, the loader
`SeedRunner` uses: `ScriptedFakeModel.load` now takes a list of files, the seed's single-directory overload delegates
to it, and `FakeLlmScripts.overriddenBy` merges the files (a key in both takes the later file; a file may hold only
`extraction` or only `match`). There is no second parser. The loader scripts every extraction at once
(`scriptAllExtractions`, which calls `beforeExtraction` per label, so `replaceScript` keeps a repeated load from
queueing an earlier answer). It does not script `match`: a match script names a document by file and needs the
document's id, which exists only after the seed has uploaded it, so matching at startup stays the seed's job. With any
provider other than `fake` the property is ignored and one warning is logged (the model's id is in it); an unknown
provider still fails startup in `LlmConfig`. Nothing sets the property in profile `prod`; it is active there only if an
operator sets it. A missing file fails startup naming the path.

**D-281 — The e2e backend scripts the seed file and a test-only fixture.** `scripts/e2e.sh` exports
`LEXVAY_LLM_FAKE_SCRIPTS=seed/fake-llm.yml,frontend/e2e/fixtures/fake-llm.e2e.yml` (the paths are relative to the
repository root, where the script runs the backend). The seed process inherits the variable too; its own
`beforeExtraction` replaces each script for its labels, so the result is the same. The fixture holds the extraction for
"Demo rule set - Nevada (fictional) 2027": NV-1 total credits >= 60 (numeric), NV-2 ethics, NV-3 human growth, NV-4 a
jurisdiction-specific course (fictional). Its header says it is fictional and test-only.

**D-282 — The rules spec keeps both extraction outcomes.** The contributor test enters the Nevada version and ends with
it at *Needs approval* and its four requirements listed (contributors see no edit or approve buttons). A separate test
enters "Demo rule set - Oregon (fictional) 2027", which has no script, and covers *Extracting* then *Extraction failed*,
the plain-words message and the collapsed technical detail. The reviewer test enters the Oregon list by hand (OR-1,
OR-2) and approves it. Nevada stays at *Needs approval*, so the navigation badge reads "2 rule versions waiting for
approval" after the reviewer saves the Oregon list and "1 rule version" after approving it. The *Extracting* state is
asserted only for Oregon: a scripted extraction can finish within one poll interval, but a failing one retries for
several seconds. Both axe checks stay (extracting, failed) and one is added for the Nevada *Needs approval* state.

**D-283 — Tests.** `FakeLlmStartupScriptsTest` (no database, no network): two files are scripted in order with the later
file winning a shared label; loading one file twice answers with the script once; no or blank paths script nothing; a
missing file fails naming it; an unknown provider fails startup (`ApplicationContextRunner` with `LlmConfig`); a client
that is not the fake one is never called, the properties are ignored and exactly one warning is logged (`anthropic`
provider through the same runner, and a stub client that throws if called); `ScriptedFakeModel.load` refuses a client
that is not the fake one.

**D-284 — Rejected.** Scripting `match` at startup by resolving document names through the database (couples the
startup path to tenant data; the e2e tests that run an assessment use seeded results). A `lexvay.llm.fake-scripts`
profile file `application-e2e.yml` instead of an environment variable (the e2e stack is configured by variables in
`scripts/e2e.sh` everywhere else, and the roadmap's "profile e2e" does not exist in this repository).
