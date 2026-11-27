## FIX-8 — No console errors

Branch `fix-console-errors`, decisions D-471 to D-474 in `docs/decisions/FIX-8.md`. Frontend only.

### Spec points
1. **View-transition errors** (D-471): the roadmap's `ignoreSkippedTransition` handlers cannot silence errors that
   Angular's router logs itself in development, so transitions are no longer skipped (orchestrator decision). A
   same-screen change sets `data-transition="none"` and `styles.css` turns the animations off; reduced motion stays in CSS.
2. **`consoleGuard`** (D-473): automatic fixture in `e2e/support.ts` with `allowConsoleErrors(page, pattern)`.
3. **One navigation per user action** (D-472): `landingGuard` for sign-in and `/`; `openingVersionGuard` for Rules; Review and the report no longer write
   the default state into the URL. Unit tests: `core/auth.guard.spec.ts`, `core/navigation.spec.ts`.
4. **Whole end-to-end suite** run with the guard: the only application console errors were the view-transition
   `AbortError`s; seven tests declare an expected 4xx or 5xx (D-474).
