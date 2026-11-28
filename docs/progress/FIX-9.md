## FIX-9 — Report-check focus flake and audit-seal perf summary

Branch `fix-focus-perf-summary`, decisions D-648 to D-650 in `docs/decisions/FIX-9.md`. No migration. Branched from
`origin/main` at 743d528; merged `origin/main` twice (design v3 screens, then AC-1), both clean.

### Spec points
1. **The flake** (D-648, D-650). Root cause: PrimeNG's `Dialog.onAfterEnter` focuses the first control of the content
   when the opening animation ends; the dialog focused the result heading as soon as the answer rendered, so an answer
   that came before the animation's end lost focus to "Copy SHA-256". Reproduced on the old code: 2 of 20 runs failed
   `toBeFocused` with ten CPU hogs on a ten-core machine (`Received: inactive`), and a focus probe under CDP CPU
   throttling logged heading focus at 103 ms, dialog `animationend` at 208 ms and focus on the copy button at 208 ms.
   The same assertion failed on an idle CI runner for PR #64 (coordinator note). Fix: the dialog's `onShow` sets an
   `entered` signal and an `afterRenderEffect` focuses the heading when the dialog has entered and the phase is `done`.
   Proof: 20 of 20 passes under the same load after the fix (twice: before and after the first merge of main), and a new
   deterministic e2e test (slowed animation) that fails on the old code and passes now.
2. **The perf summary** (D-649). `AuditSealPerfTest` writes `backend/target/perf/audit-seal.md` (batch seal and verify
   median and p95, run counts, total seal time); the CI `perf` job summary prints it under "Audit trail seal". Ran
   locally: sealing one batch of 500, median 12.87 ms, p95 19.14 ms (41 batches); verifying 20 005 entries, median
   106 ms, p95 113 ms (five runs).
3. **Docs.** `docs/PERF.md` names the new report file; decisions and this file.

### Evidence
`LEXVAY_E2E_SLOT=7 make test`: backend 1316 tests, 0 failures; frontend unit 408 passed; end to end 57 passed, 8 skipped.
Two earlier full runs failed two `rules.spec.ts` tests (the New rule version dialog's character count not found); a fresh
stack run of the whole suite then passed, and the final run passed. Those tests and files are not touched by this item.
