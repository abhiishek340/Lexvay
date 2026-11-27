# PERF-FE-1 — Icons and theme tokens per chunk (D-295 to D-299)

**D-295 — `SHELL_ICONS` is the only icon registry in `app.config.ts`.** It holds the 17 icons of the shell, the
sign-in page and the home screen (`theme/icons.ts`); `LEXVAY_ICONS` is deleted. `IconName` is now
`` `phosphor${string}` ``: it used to be `keyof typeof LEXVAY_ICONS`, which no longer exists as one registry, and the
tables that carry icon names (`FINDING_ICON`, `RULE_STATUS`, ...) are checked by `icons.spec.ts` instead of by the type.
Rejected: a union of every screen's constant (the label tables would import types from the screens they serve).

**D-296 — Each routed screen provides its own icons through its route; shared components use `viewProviders`.**
A route's `providers` are built when the app starts, so naming a screen's `<SCREEN>_ICONS` in `app.routes.ts` would
import the screen into the initial bundle. Each screen with icons beyond `SHELL_ICONS` (Overview, Review, Requests,
Changes, Rules, Report, Register) therefore has a `<screen>.routes.ts` (one route, `path: ''`, the screen as `component`, its
`providers: [provideIcons(<SCREEN>_ICONS)]`, its title and guards) that `app.routes.ts` loads with `loadChildren`; the
screen and its routes are one chunk, so there is no second request. `provideIcons` merges with the icons of the parent
injector, so a screen names only what `SHELL_ICONS` does not. `DocumentViewer`, `FindingLabel` and `DecisionState`
provide theirs in `viewProviders` (`DOCUMENT_VIEWER_ICONS`, `FINDING_LABEL_ICONS`, `DECISION_STATE_ICONS`). Login, Home
and Audit show nothing beyond the shell's icons and keep `loadComponent`. `confirmLeave` moved from `app.routes.ts` to
`core/confirm-leave.ts` so `rules.routes.ts` does not import the root route table. The label tables that held icon names
moved next to the one screen that shows them (`CHANGE_KIND` to `screens/changes/change-kind.ts`, `DIFF_STATUS` to
`screens/rules/diff-status.ts`, `RULE_STATUS` to `screens/rules/rule-status.ts`, `OUTCOME_ICON` and `STATUS_ICON` into
`register.ts`, `REQUEST_STATE` into `requests.ts`, `FINDING_ICON` and `DECISION_ICON` into `ui/finding.ts`): `core/labels.ts` and `core/rules.ts` are
imported by the shell, so icon names left there would belong to every chunk.
Rejected: component-level `providers` on each screen (works, but the spec says the route's).

**D-297 — The registration guard reads sources; the end-to-end suite watches the console.** `theme/icons.spec.ts`
collects every quoted `phosphor...` name in the files a screen reaches through relative imports and `templateUrl`
(stopping at a shared component that registers its own), and requires the registered set to equal the names shown
minus `SHELL_ICONS`, in both directions (so a stale entry fails too). It also fails if any source file that names an
icon is reached by no unit, so a new screen cannot forget to register. The spec asked for rendering each screen's
template; a rendered DOM covers only the states a fixture reaches (the loading skeleton shows no icon), while the source
covers every branch. The runtime half is `iconGuard` in `e2e/support.ts`: every end-to-end test fails on a
"No icon named" console warning. The spec reads files, so `@types/node` 26.6.11 (registry latest, checked 2026-11-27,
matching Node 26.13.1) is a dev dependency and `tsconfig.spec.json` lists `node` in `types`.

**D-298 — `registerComponentTokens(sets)` and what moved.** One helper in `theme/component-tokens.ts` replaces
`registerRulesTheme` and `registerRegisterTheme`: it calls `updatePreset` for the components not yet registered, so two
chunks naming the same component (Dialog, InputNumber, Textarea, ProgressBar) are safe. Each screen exports its sets as
`<SCREEN>_TOKENS` in `<screen>-theme.ts` and registers them from its constructor; `DocumentViewer` registers Dialog and
InputNumber itself. Moved out of the shared preset: Dialog, InputNumber, ProgressBar, ProgressSpinner, RadioButton,
Splitter, Textarea. F4's Drawer set and F8's ToggleSwitch set (with its Lexvay colours, now `theme/toggleswitch-tokens.ts`,
shared by Register and Requests) follow the same pattern; `evidence-drawer-theme.ts` is gone and the Requests screen
registers the drawer's sets. The Lexvay overrides of ProgressBar and Splitter moved with their sets with the same values
(`theme/progressbar-tokens.ts` is shared by Overview and Review); the Dialog override restated Aura's own title values
(1.125rem, 600), so it was dropped with no change of value. Disagreement with the spec's list: SelectButton and
ToggleButton stay in the preset because the shell's theme switcher renders them on every screen, and IconField stays
with Select (Select's filter uses it; 61 bytes).

**D-299 — The budget stays at 700 kB / 800 kB; the initial bundle is 695.0 kB, not under 640 kB.** See
`docs/PERF.md`, "Frontend bundle". The icon cut does not work as the spec assumes: `@ng-icons/phosphor-icons/regular`
is one module, and the bundler places a module in the chunk every importer shares, with the union of the exports used
anywhere. Because the shell imports it for `SHELL_ICONS`, the icons of every screen still ship in the initial chunk
(26.9 kB for 52 icons; the 17 shell icons are 7.5 kB). Proposed next cut, measured on a scratch build: give the shell
its own copy of its 17 icons (a generated `theme/shell-icons.ts` of SVG strings, checked against the package by a
test), which takes the initial bundle to 676.5 kB. Still short of 640 kB; the rest is in the PERF.md breakdown.
