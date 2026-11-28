# FIX-9 — Report-check focus flake and audit-seal perf summary (D-648 to D-650)

**D-648 — The result heading takes focus after the dialog has finished opening, not at the moment the answer renders.**
`report-check.spec.ts` failed `toBeFocused` on the result heading intermittently under CPU load (2 of 20 runs with ten
`yes` processes on a ten-core machine; `Received: inactive`). The cause is a race inside the dialog. PrimeNG's
`Dialog.onAfterEnter` calls `focus()` when the opening animation ends, which focuses the first focusable control of the
content (the heading has `tabindex="-1"` and is skipped). `ReportCheckDialog` focused the heading as soon as the answer
rendered. When the answer arrived before the animation ended, which happens whenever the animation is late (load) or the
answer is fast, PrimeNG then moved focus from the heading to "Copy SHA-256", and nothing moved it back. A focus probe
with CPU throttling (CDP, rates 1 to 16) logged the order: heading focused at 103 ms, `animationend` of the dialog at
208 ms, focus on the copy button at 208 ms; only at rate 16, where the animation ended before the answer, did the heading
keep focus. The dialog now has an `entered` signal, set by the dialog's `onShow` (emitted after PrimeNG's own `focus()`)
and cleared when the dialog closes, and an `afterRenderEffect` focuses the heading whenever `entered()` is true and the
phase is `done`. Whichever of the answer and the animation's end comes last, the heading is focused after both and after
the view has rendered, so the result no longer depends on timing. Rejected: a longer assertion timeout or retries (the
focus never returns on its own, so they would only hide the fault); `[focusOnShow]="false"` (the drop zone would lose its
initial focus, a regression for keyboard users); a `setTimeout` after the answer (a guess about the animation's length).
`report-check.spec.ts` gains a deterministic test: it slows the dialog's animation to 1.5 s with a style rule, so the
answer always comes first, and asserts the heading is focused before and after the animation ends. It fails on the old
code on every run and passes with the fix.

**D-649 — `AuditSealPerfTest` writes `target/perf/audit-seal.md`, and the CI summary prints it.** The test printed its
timings but wrote no file, so OPS-3's summary of `target/perf` left the SEC-3 budget out. It now times every sealed batch
and writes a table in the format of `latency.md` (median and p95 of sealing one batch of 500 and of verifying all
entries against the 2 s budget, the run counts and the total seal time) before its assertions, so a failing run still
reports. It reuses `EndpointLatencyTest.percentile`. The `perf` job's summary step names each report file explicitly (it
does not glob, because each section needs its own heading); it gains the "Audit trail seal" section reading
`audit-seal.md`, and the artifact upload already takes all of `target/perf`. With five verify runs the p95 is the slowest
run.

**D-650 — A flake is proved fixed by repetition under load against the real test, and its cause by a test that needs no
load.** The 20 repeated passes (`--repeat-each=20` with ten CPU hogs) show the fix holds under the condition that failed,
but repetition cannot prove a race is gone, so the cause also has its own deterministic test (D-648).
