### FIX-8 — No console errors (D-471 to D-474)

**D-471 — A view transition is never skipped; its animation is switched off instead.** The roadmap's first design
attached handlers to `ready`, `finished` and `updateCallbackDone` and ignored `AbortError`. It cannot work: in
development builds Angular's router already attaches its own `.catch(console.error)` to all three promises
(`createViewTransition`, guarded by `ngDevMode`) before it calls `onViewTransitionCreated`, so a skipped transition is
logged whatever the application does with the promises. The error never reached `provideBrowserGlobalErrorListeners`,
and it is absent from production builds, which is why only a running development app showed it. `app.config.ts` therefore
no longer calls `skipTransition()`. For a change within one screen (a tab, a filter; `sameScreen`) it sets
`data-transition="none"` on `<html>`, and `styles.css` sets the `::view-transition-group/old/new(*)` animations to
`none` under that attribute: the transition runs, finishes and resolves. Every new transition sets or removes the
attribute itself, so a stale value never reaches the next one (no cleanup on `finished`, which would race with a
following transition). Reduced motion needs no script: the media query in `styles.css` already sets the same
animations to `none !important`, so the `MotionPreferences` check in `app.config.ts` was a duplicate and is gone.
Rejected: wrapping `document.startViewTransition` or `console.error`; a global allowlist for the message.

**D-472 — One user action is one router navigation.** A second transition that starts while the first still runs aborts
the first ("New ViewTransition started"), which Angular logs. The end-to-end run showed two chains, both removed
instead of tolerated:
- Signing in, and any visit to `/`, navigated to `/` and then, once the programs arrived, from the `Home` screen to the
  overview of the only program. `landingGuard` (`core/auth.guard.ts`, after `authGuard` on the `''` route) now fetches the
  programs and returns the overview's `UrlTree` for a single program; the redirect resolves inside the same navigation,
  before any transition is created. With several programs, none, or a failed request the guard lets `Home` render, which
  lists them or shows the failure from its own request. `Home` no longer navigates.
- Review and the evidence report wrote their default state into the address (`?jurisdiction=AZ`) right after opening.
  `needsJurisdictionParam` (`core/navigation.ts`) says whether choosing a state must change the URL: an absent
  `?jurisdiction=` already means the program's first state, as Coverage always read it, so the default is applied when
  reading and the URL changes only when the person picks another state.

- Opening Rules without a version in the address (UI2-4) chose the version to open in a component effect and navigated
  again once the program had loaded, which aborted the first transition. `openingVersionGuard`
  (`screens/rules/opening-version.guard.ts`) does the same inside the opening navigation: it awaits the program, then
  redirects the bare address to the version that opens (the query travels along). `ProgramContext.ruleVersionGroups`
  is the one place the versions are grouped, for the guard and the list.

The rule workspace's `?new=1` (the command palette's "new rule version") is also followed by a navigation that removes
the parameter; no test overlaps it, so it is left as it is and noted in the pull request.

**D-473 — `consoleGuard` fails any test that logs a console error.** An automatic fixture in `e2e/support.ts`, beside
`iconGuard`, collects every `console` message of type `error` and every `pageerror` and fails the test if any was
recorded. A test that deliberately causes an error declares it with `allowConsoleErrors(page, /pattern/)`; the pattern is
matched against the message text and the URL it came from, so it names one response by status and URL. There is no
global allowlist.

**D-474 — The seven declared errors.** Chromium logs every 4xx or 5xx response as "Failed to load resource". Seven tests
cause one on purpose:
- `review-and-sign.spec.ts` (sign-in page): the wrong password is answered with 401 by `/api/v1/auth/login`.
- `evidence-requests.spec.ts` (answer drawer): a tampered quote submitted through the screen is answered with 400 by
  `/api/v1/mappings/<id>/evidence-responses`.
- `rules.spec.ts` (hand-entered list): the blank title is answered with 400 by
  `/api/v1/rule-versions/<id>/requirements`.
- `people.spec.ts` (a used setup link): 400 from `/api/v1/auth/setup`.
- `people.spec.ts` (own row, last administrator): the stubbed 409 from `/api/v1/users/<id>/role`.
- `people.spec.ts` (busy sign-in): the stubbed 503 from `/api/v1/auth/login`.
- `setup.spec.ts` (the same syllabus uploaded twice): the 409 from `/api/v1/programs/<id>/documents?kind=SYLLABUS` that refuses the copy.

Every other error the suite found was the view-transition `AbortError`; no screen logged anything else.
