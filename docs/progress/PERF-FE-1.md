# PERF-FE-1 — Icons and theme tokens per chunk

Branch `perf-frontend-chunks`, built on `origin/main` at 91f6602 (F8 merged, design v2 docs). Structural only: no token
value, font or visual changed. Decisions D-295 to D-299 in `docs/decisions/PERF-FE-1.md`.

## Status

Built and tested. The budget step is not done: the initial bundle is 695.0 kB raw, not under 640 kB, so
`angular.json` keeps 700 kB / 800 kB. The breakdown and the next cuts are in `docs/PERF.md`, "Frontend bundle".

## What changed

- `SHELL_ICONS` (17 icons) is the only registry in `app.config.ts`; `LEXVAY_ICONS` is gone.
- Routed screens with their own icons (Overview, Review, Changes, Rules, Report, Register) have a
  `<screen>.routes.ts` that provides `<SCREEN>_ICONS`; `app.routes.ts` loads them with `loadChildren`.
  `DocumentViewer`, `FindingLabel` and `DecisionState` provide theirs with `viewProviders`.
- `registerComponentTokens(sets)` (`theme/component-tokens.ts`) replaces `registerRulesTheme` and
  `registerRegisterTheme`; `<screen>-theme.ts` files export `<SCREEN>_TOKENS`. Dialog, InputNumber, ProgressBar,
  ProgressSpinner, RadioButton, Splitter and Textarea left the shared preset.
- Icon label tables moved next to their one screen; `confirmLeave` moved to `core/confirm-leave.ts`.
- Tests: `theme/icons.spec.ts` (every name shown is registered by its chunk, and nothing extra),
  `theme/component-tokens.spec.ts`, and an `iconGuard` fixture that fails any end-to-end test on a "No icon named"
  warning. `@types/node` 26.6.11 added as a dev dependency for the spec.
- `docs/DESIGN.md` "Bundle" says how a new screen follows the pattern.

## Bundle (production build, raw / estimated transfer)

| | before (91f6602) | after |
|---|---:|---:|
| Initial total | 703.12 kB / 169.43 kB | 695.03 kB / 168.22 kB |
| main.js | 695.67 kB / 167.84 kB | 687.58 kB / 166.62 kB |

Lazy chunks per screen are in `docs/PERF.md`. Why the icon cut does not shrink the initial chunk: the package's
`regular` entry is one module, so the bundler keeps every icon any chunk uses in the chunk the shell shares
(52 icons, 26.9 kB, in `main`). A scratch build in which the shell holds its own copy of its 17 icons measures
676.5 kB.

## Test evidence

```
make test (LEXVAY_E2E_SLOT=6)
  backend:   Tests run: 821, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS
  frontend:  Test Files 15 passed (15) / Tests 92 passed (92)
  e2e:       1 skipped (the screenshot test) / 16 passed (1.2m)
LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=6 make e2e
  17 passed (1.7m)
```

Axe (zero serious or critical, light and dark) runs inside those end-to-end tests and passed.

## Screenshots

The committed `docs/screens` are unchanged (no visual change to record). To check, every screenshot was regenerated
with this branch and again with `91f6602` on the same stack and slot, and the two sets were compared pixel by pixel:
25 of the 38 are identical. The others differ only where a time or a generated record id is printed (audit trail times and ids, the register's
"Generated" line, the evidence report's signature time: 111 to 9455 px in those boxes) or by 2 to 34 pixels of
capture noise (a dialog's scroll position in rules-new-version-light, one-pixel edges in review-400px-light and
rules-approve-light). No icon, colour or layout moved.
