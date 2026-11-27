# WS-UI decisions (D-080 to D-089)

Choices made while implementing `docs/DESIGN.md` and the WS-UI item in `docs/ROADMAP.md`. Each names the
alternative that was rejected. The orchestrator folds these into `DECISIONS.md`.

## Dependency pins (verified on the npm registry on 2026-11-26)

| Package | Version |
|---|---|
| `primeng` | 22.1.5 |
| `@primeuix/themes` | 3.1.1 |
| `@ng-icons/core` | 36.1.0 |
| `@ng-icons/phosphor-icons` | 36.1.0 |
| `@fontsource/ibm-plex-sans` | 5.3.0 |
| `@fontsource/ibm-plex-serif` | 5.3.0 |
| `@fontsource/ibm-plex-mono` | 5.3.0 |

Transitive packages PrimeNG 22.1.5 brings in (resolved by npm, recorded in `package-lock.json`):
`@primeuix/styled`, `@primeuix/styles`, `@primeuix/utils`, `@primeuix/motion`, `@primeicons/angular`,
`@primeui/license-manager`.

## D-080 PrimeNG 22 requires a PrimeUI license key (needs an owner decision)
PrimeNG 22 is distributed under the PrimeUI License (`node_modules/primeng/LICENSE.md`): a Community License
(free for organisations under USD 1 M revenue, fewer than 5 developers and 10 employees; individuals and
non-commercial open source qualify; renewed yearly) or a paid Commercial License. At start-up
`providePrimeNG` verifies a key offline through `@primeui/license-manager`; without one it logs
"PrimeUI license is not configured" and shows a fixed "Invalid PrimeUI License" notice in the corner of
every page (it is visible in `docs/screens`). The license forbids removing that mechanism, so the notice is
left in place. Every 21.x release has no license check but requires Angular 21, so it cannot be used with
Angular 22.3.
Implemented: the key is read from the build-time constant `LEXVAY_PRIMEUI_LICENSE`
(`ng build --define "LEXVAY_PRIMEUI_LICENSE='<key>'"`, see `src/app/theme/license.ts`) and passed to
`providePrimeNG({ license })`; no key is committed. Rejected: hiding the notice with CSS (breaches the
license); switching libraries or downgrading (contradicts the design brief, and the brief is the CTO's call).
**Action for the owner:** register for the Community License (if eligible) or buy a Commercial License, then
supply the key at build time.

## D-081 Design tokens live once, in the preset, as light-dark() pairs
`@primeuix/themes` 3 expresses Aura's colour scheme with CSS `light-dark()`; PrimeNG emits
`color-scheme: dark` for `[data-theme="dark"]`. `LexvayPreset` writes every DESIGN.md token as a
`light-dark(light, dark)` pair, overrides the Aura semantic and component tokens with them, and publishes
the same values under `extend.lx` so application CSS reads `--p-lx-*`. Rejected: a second copy of the
palette as hand-written CSS variables in `styles.css` (two sources of truth); `colorScheme.light/dark`
blocks (mixed with Aura's `light-dark()` values the precedence is unclear).
`ThemeService` always writes a concrete `data-theme` (`light` or `dark`); "System" resolves it from
`prefers-color-scheme` and follows changes, so one selector drives both PrimeNG and the application CSS.

## D-082 Aura limited to the components Lexvay renders
`definePreset` starts from Aura's base tokens plus the token sets of the 22 components in use (listed in
`lexvay-preset.ts`). The full Aura object carries about 90 component token sets and cost 130 kB of the
initial bundle. Rejected: the full Aura import. Consequence: a component added to a template must be added
to that list, or it renders without its theme.

## D-083 Bundle budget measured on raw size
WS-UI asks for an initial bundle under 600 kB transferred. Angular budgets measure raw (uncompressed)
size, so the budget is set on raw size at 700 kB warning / 800 kB error; the production build measures
672.9 kB raw and 161.8 kB estimated transfer. The program switcher (Select), the account menu and the toast
are `@defer` blocks. Rejected: a 600 kB raw budget (Angular core, router, forms and common alone are about
400 kB raw; meeting it would mean dropping PrimeNG from the shell). The component style budget is raised to
10 kB / 14 kB because the review workspace styles three panes and two dialogs in one component.

Critical-CSS inlining is switched off in the production configuration (`optimization.styles.inlineCritical:
false`): it adds an inline `<script>` that swaps the stylesheet's `media`, which the backend's
Content-Security-Policy (`default-src 'self'`, no inline scripts) blocks, leaving the global styles unapplied.
The built `index.html` now has one external module script and one stylesheet link; fonts load from `./media`.
PrimeNG's runtime `<style>` elements rely on `style-src 'unsafe-inline'`, which the policy allows.

## D-084 Fonts: latin and latin-ext subsets only
The `@fontsource` packages ship cyrillic, greek and vietnamese subsets as well. Only `latin` and
`latin-ext` are imported for Sans 400/500/600, Serif 400 and 400 italic, and Mono 400. Rejected: the
all-subsets entry points (more files in the build for scripts the product does not show).

## D-085 One program context shared by every screen
`ProgramContext` loads the program, its latest finished assessment, determinations, change events,
jurisdictions and rule versions once per program; screens and the navigation badges read it, and mutations
call a targeted refresh. Rejected: each screen fetching its own copy (the previous code issued the same
four requests on every screen change, and the badges would need a third copy).

## D-086 Review view model as a plain signal class
`ReviewWorkspace` (filters, selection, J/K movement, advancing after a decision, progress) and
`shortcutFor`/`decisionBlocker` are plain TypeScript with Angular signals and no dependency injection, unit
tested with Vitest. A decision counts only while it is current: a stale mapping needs review again
(`isDecided`). After a decision the workspace moves to the next requirement in list order that still needs
one and matches the filter, wrapping around. Rejected: an injectable store (needs TestBed to test) and
keeping the logic in the component.

## D-087 Accessibility workarounds for PrimeNG markup
- Tables have no accessible-name input: names are set through pass-through (`[pt]="{ table: { 'aria-label' } }"`).
- `p-progressbar` sets `aria-level` on a `progressbar` role and has no name input, which axe reports;
  progress bars are `aria-hidden` and the progress is always stated in text next to them.
- Splitter gutters get `aria-label="Resize panes"` through pass-through; dialogs get `closeAriaLabel`.
- Navigation badges are `aria-hidden`; the count is a separate `aria-describedby` text so link names stay
  "Review" and "Changes".
- Tag labels use the `value` input so the icon precedes the word.
Rejected: forking or wrapping the PrimeNG components.

## D-088 Focus after navigation, and the live region
Moving to another screen focuses that screen's `h1`; the first page load and query-only navigation (the
jurisdiction tabs, the report's jurisdiction) do not move focus. Review results are announced in one
polite `role="status"` region in the review header (`data-testid="message"`) and also shown as a PrimeNG
Toast, as the brief asks. PrimeNG's toast is `role="alert"`, so a screen reader may hear a decision twice.
Rejected: dropping the toast (the brief asks for both).

## D-089 Evidence report jurisdiction and PDF
The report opens on the jurisdiction in `?jurisdiction=` or the first one, chosen with a PrimeNG Select.
"Generate PDF" is offered only for a signed determination because the existing endpoint renders a signed
determination; a draft explains that the PDF follows signing. Rejected: a client-side PDF of the draft.

## Screens and tests
- Screenshots: `docs/screens/*-light.png` and `*-dark.png`, produced by `e2e/screens.spec.ts`
  (`LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=1 make e2e`).
- Every end-to-end test fails on any request to a host other than localhost (`e2e/support.ts`).
- `expectAccessible` runs axe in the light and the dark theme with transitions disabled.
- Backend: `GET /api/v1/users` (`tenant/UserController`, `UserApiTest`), tenant-scoped by row-level
  security, readable by every role, not in the audited set because it is a GET.
