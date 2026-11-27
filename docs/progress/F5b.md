## F5b — Administration screens

Branch `ui2-admin`, decisions D-426 to D-435 in `docs/decisions/F5b.md`. Frontend only; uses F5a's endpoints and
SEC-2's busy gate. `origin/main` (UI2-2) merged before the pull request.

### Spec points
1. **People** (`/people`, D-426): tenant-wide rail entry under Determination register (`phosphorUsers`), administrators
   only; `adminGuard` sends anyone else to the overview. Table of avatar initials, name (with "You" on one's own row),
   email, role chip, status chip (Active, Setup pending, Deactivated; icon and word), created date; search by name or
   email; role and status filter chips; the count as "1 of 6 people". Row menu: Change role, Deactivate or Reactivate,
   New setup code; one's own row offers New setup code only. Rows stack into blocks under 720 px.
2. **Add person** (D-427): Dialog with name, email and role (Select, Contributor by default, "About roles" InfoTip with
   one line per role). On success the dialog becomes the one-time link view: the whole `<origin>/setup#code=…` in mono,
   "Copy link", "Valid for 72 hours, until 30 Nov 15:04", "Shown once. Copy it now and hand it over yourself.", Done.
   "New setup code" shows the same view ("Earlier links stop working."). The code is held only by the dialog and dropped
   when it closes; no storage, no logs.
3. **Change role, deactivate, reactivate** (D-432): ConfirmDialog with the person, the new role Select, and the
   consequence in one sentence ("Their current session ends." / "They can sign in again."); the server's 409 sentences
   appear inside the dialog, which stays open.
4. **Set password** (`/setup#code=…`, D-428, D-429, D-435): the sign-in frame without the story (`ui/auth-frame.ts`, shared with
   sign-in), the code read and removed from the address bar with `history.replaceState` in the constructor, two fields
   with show and hide, the live checklist (12 to 128 characters, not an email address, both fields match) with icons, a
   strength meter from length and character variety, Submit to `POST /auth/setup`; the invalid-code view for the
   server's invalid-code 400; success goes to sign-in with the toast "Password set. Sign in."
5. **Session and busy handling** (`core/access.ts`, `core/busy.ts`, D-430, D-431): the 401 "Your access changed. Sign in
   again." signs out locally, clears the program, routes to sign-in and shows the sentence; a 503 with `Retry-After` on
   sign-in or setup shows "Sign-in is busy. Try again in a moment." and disables the button with "Retry in 2 s".
6. **Tests** (D-434): see below.

### Deviation
"Not the email" is checked as "not an email address": the setup page knows only the code (D-428). The client rule is
stricter than the server's and implies it; the server keeps checking its own.

### Shared changes (called out in the pull request)
- `ui/auth-frame.ts` from the sign-in page (sign-in looks as before); `@keyframes lx-rise` to `styles.css`.
- `.lx-chip` (the audit trail's pill Selects) from `audit.css` to `styles.css`; the Avatar token set from `audit-theme.ts`
  to `theme/avatar-tokens.ts`. Values unchanged.
- `navLinks` takes the role (People for administrators, also in the command palette); `beforeSignIn` replaces the
  shell's `/login` test; `phosphorUsers` in `SHELL_ICONS`.
- `SessionService`: `notice`, `end`, and a 401 from the session call read as signed out (D-374); `ApiService` and
  `models.ts` gain the user administration calls and types.

### Unit tests (`make frontend-test`)
New: `screens/setup/password-rules.spec.ts`, `screens/people/people-view.spec.ts`, `core/access.spec.ts`,
`core/busy.spec.ts`, `core/navigation.spec.ts`; `commands.spec.ts` (People for administrators) and `icons.spec.ts`
(units for People and setup) extended.

### End-to-end (`e2e/people.spec.ts`)
Add a contributor, copy the link (clipboard read back, code in no storage), sign out, set the password (the code gone
from the address bar), toast, sign in as the new person (no People), the used link refused; a second context signs in,
the administrator changes the role and the person's next request lands on sign-in with the access-changed sentence,
they sign in again as Reviewer; deactivate (their session ends, sign-in refused), reactivate (sign-in works), new setup
code (a different link); one's own row offers only New setup code and a 409 stays in the role dialog; search and filter
chips; a reviewer has no People and `/people` sends them to the overview; a busy sign-in counts down; axe in both themes
at 1440 and 400 px without horizontal scroll on People, Add person, the link view, Change role and the setup page with
rules partly met.

### Screenshots (`docs/screens/`, light and dark)
`f5b-people-{1440,400}`, `f5b-add-person-{1440,400}`, `f5b-setup-link-{1440,400}`, `f5b-change-role-{1440,400}`,
`f5b-setup-{1440,400}` (rules partly met), taken by `e2e/people.spec.ts` with `LEXVAY_SCREENSHOTS=1`.

### Bundle
Production build: initial 608.71 kB raw, 156.51 kB estimated transfer (main 598.63 kB, styles 10.08 kB), against
605.33 kB / 155.69 kB on `origin/main` at the merge: +3.38 kB raw (the interceptor, the session notice, the People rail
icon and the chip styles now global). Lazy: `people-routes` 31.89 kB raw (8.61 kB transfer), `setup-routes` 21.50 kB
(6.11 kB), plus the PrimeNG chunks they share with other screens. Budget 680 kB warning.

### Follow-ups outside this item
- An anonymous lookup of a setup code's email would let the page check "not your email" exactly (a backend change).

### Test tails (2026-11-27, after merging `origin/main` at 2bc911c)
`make frontend-test` and `LEXVAY_E2E_SLOT=2 make test`:
```
[INFO] Tests run: 1074, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  34 passed (34)
      Tests  351 passed (351)
  45 passed (4.8m)
```
An earlier `LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=2 make test` run (49 passed, 1 failed) found that `records.spec.ts`'s empty
audit filter ("Access withdrawn" alone) now matches People's deactivation; the test now also filters by Dana, who never
withdraws access. Only the `f5b-*` screenshots from that run are committed.

### Review round 1 (2026-11-27, after merging `origin/main` at da71fbc with F10b and FIX-6)
- **Setup code in the fragment** (D-435): the link is `<origin>/setup#code=…`; the page reads `location.hash` and clears it
  with `history.replaceState`; `?code=` is not read (nothing was ever issued). `people.spec.ts` records every request URL
  while the link is opened and the password set, and requires none to carry `code=` or the code. The backend does not
  build or document the link (it returns the code alone), so no backend or `docs/OPERATIONS.md` change.
- **Register table** (UI2-1's file): `class="lx-table"` instead of the `styleClass` input `p-table` lacks, so the narrow
  rules apply; `ui2-1-register-400-*` retaken.
- **Bottom tab bar** (D-433): with Coverage and People an administrator had ten tabs and the bar scrolled at 400 px. A
  `priority` on every `NavLink`, `tabSlots` and `tabBarOverflow` (pure, unit-tested) put the least used into a final
  "More" tab (PrimeNG Menu in the deferred `shell/more-menu.ts`); at 400 px an administrator's People and Audit trail
  move there, every other role still fits. New `people.spec.ts` test: the bar does not scroll, More opens by keyboard,
  lists People and Audit trail, passes axe, ArrowDown and Enter open People, and the tab then shows it is current.
  `e2e/support.ts` gains `goTo` (link, or More), used by `records.spec.ts` and `screens.spec.ts` for the administrator at
  400 px. Screenshot `f5b-more-menu-400`; the other `f5b-*` retaken.
- Bundle: initial 611.74 kB raw, 157.27 kB transfer (main 601.67 kB, styles 10.08 kB) against 605.94 / 155.81 kB on
  `origin/main` da71fbc (+5.80 kB: the round-1 More tab and People's rail icons on top of the first round). Lazy:
  `people-routes` 32.06 kB, `setup-routes` 21.51 kB, `more-menu` 2.51 kB.

`LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=2 make test`:
```
[INFO] Tests run: 1091, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  35 passed (35)
      Tests  374 passed (374)
  56 passed (7.4m)
```
Only the `f5b-*` and `ui2-1-register-400-*` captures of that run are committed.
