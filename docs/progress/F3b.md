# F3b — Rule workspace review fixes: progress and test evidence

Branch `feat-rule-workspace-fixes`, end-to-end slot 10. Decisions: `docs/decisions/F3b.md`.

## The five review changes and the bundle
1. **Edit grid**: two-line form rows in the same PrimeNG Table body. Line one: ref, title, type, numeric check, and
   the move and delete actions. Line two: the text, with the row's server error under it. Laid out by container
   width. No horizontal scroll at 1024, 1280 and 1440 px (checked in e2e); fields stack under 640 px.
2. **Extraction failure**: "Requirement extraction did not produce a list. A reviewer can enter the list by hand with
   Edit list." The job error is in a collapsed native `details`, "Technical detail", in mono. The list row shows
   only the *Extraction failed* Tag.
3. **Unsaved-changes guard**: a ConfirmDialog, "Discard unsaved changes to this list?", with "Discard changes" and
   "Keep editing" (Keep editing is focused). A CanDeactivate guard covers other versions, other screens and
   signing out; `beforeunload` covers a reload or closing the tab. `leaveNeedsConfirmation` has a unit test. In e2e,
   the reviewer edits a title, chooses another version, keeps editing, and the edit is still there.
4. **Rule text** uses F1's `DocumentViewer` (read-only, no highlight); `rule-text.ts` is deleted.
5. **Screenshots**: every screen regenerated with `LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=10 make e2e` (14 passed),
   including `rules-edit-{light,dark}` with the new layout.
- **Bundle**: the initial bundle is 698.9 kB raw (was 704.6 kB). The Accordion, ConfirmDialog, DatePicker and
  Listbox token sets now load with the Rules chunk (F3b-2).

## Tests
`make frontend-test`:
```
 Test Files  11 passed (11)
      Tests  73 passed (73)
```
`LEXVAY_E2E_SLOT=10 make test`:
```
[INFO] Tests run: 788, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  11 passed (11)
      Tests  73 passed (73)
e2e: starting backend on :8091
  ✓   7 e2e/rules.spec.ts:40:1 › a reviewer reads the seeded Colorado revision: its list, its rule text and what it changed (4.5s)
  ✓   8 e2e/rules.spec.ts:106:1 › a contributor enters a new rule version and watches its extraction (9.4s)
  ✓   9 e2e/rules.spec.ts:163:1 › a reviewer enters the list by hand, corrects it, saves, reloads and approves it (10.0s)
  ✓  10 e2e/rules.spec.ts:258:1 › at 400 px the rule workspace stacks without horizontal page scroll (3.3s)
  -  11 e2e/screens.spec.ts:9:1 › screenshots of every screen in light and dark
  1 skipped
  13 passed (1.0m)
```

## Noticed while testing (not changed)
- `scripts/e2e.sh` sets `LEXVAY_MANAGEMENT_PORT=8091+slot`, and the backend port is `8081+slot`. Slot 10's backend
  (8091) therefore takes the same port as slot 0's management port, and slots 0 to 9 and 10 to 19 overlap in the
  same way. Two such stacks cannot run at once.
