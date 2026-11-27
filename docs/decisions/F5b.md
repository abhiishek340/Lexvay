### F5b — Administration screens (D-426 to D-435)

**D-426 — People: one tenant-wide screen for administrators, filtered in the browser.** `/people` is a tenant-wide
route like the register (`authGuard`, then `adminGuard` on the route in `people.routes.ts`, loaded with `loadChildren`
so `PEOPLE_ICONS` stay in its chunk). `adminGuard` sends anyone else to the overview of the program last worked on, or to
Programs, which opens the only program; the server refuses them in any case. `navLinks(programId, counts, role)` now takes
the role and lists People (`phosphorUsers`, added to `SHELL_ICONS` because the rail draws it) under the register for
administrators only; the command palette reads the same list, so "Go to People" follows. The shell shows the rail on any
tenant-wide link's path instead of naming `/register`. The list is one `GET /users` (the administrator's fields, D-375);
a tenant has tens of people, not thousands, so search (name or email, anywhere, ignoring case), the role chip and the
status chip filter in the browser through the pure `filterPeople` (`people-view.ts`, tested), and the count reads "1 of 6
people". Status: deactivated first, then "Setup pending" while `passwordSet` is false, else Active; each chip a Tag with
icon and word. The chips are the audit trail's pill Selects: their rules moved from `audit.css` to `styles.css`
(`.lx-chip`), unchanged, so both screens share one copy. Rows: avatar initials (the audit trail's Avatar set, moved to
`theme/avatar-tokens.ts`), name with a "You" pill on the administrator's own row, email, role chip, status chip, created
date, and a row menu (PrimeNG Menu, Phosphor icons through its item template). `personActions` decides the menu: Change
role, Deactivate or Reactivate, New setup code; the own row offers New setup code only (the server answers 409 to the
other two, D-375). Under 720 px each row becomes a block (who and the menu, then role, status and date), as the
register's table does. Rejected: server-side filters (an endpoint change for a list this small); hiding the menu on the
own row (the reset of one's own password is legitimate, D-375).

**D-427 — Add person and the one-time link live in one dialog, which alone holds the code.** `PersonDialog` has two
phases: the form (name, email, role; the role Select defaults to Contributor, the least a person can do, with an InfoTip
"About roles" giving one line per role as the server's permissions stand) and the link view. On 201 the dialog turns into
the link view: the person, the whole link `<origin>/setup?code=…` in mono with "Copy link" (focus moves there), "Valid for
72 hours, until 30 Nov 15:04" (`expiryText`, local time, hours rounded) and "Shown once. Copy it now and hand it over
yourself." "New setup code" opens the same view for an existing person, issuing the code from the dialog and adding
"Earlier links stop working." (D-372). The code exists only in the dialog's private `setup` signal: People's list
receives the account without it, nothing is written to storage or the console, and closing the dialog (Done, Escape,
the close button) runs `reset`, which drops it. A 409 ("An account with this email exists") sits under the email field;
other failures above the form. Rejected: showing the code in a toast or on the row (it would outlive the view); an
email field the server already validates, checked again only for shape and presence.

**D-428 — Setup page: the sign-in frame without the story; the code leaves the address bar in the constructor.**
`ui/auth-frame.ts` (`lx-auth-frame`) is what sign-in and setup share: the grid, the form column with the mark and
wordmark, and the form vocabulary (`lx-auth-field`, labels, heading), with encapsulation off because that content is
projected; sign-in projects its map story with `lxAuthStory` and `[story]="true"`, setup shows the form alone. Sign-in
looks as before. `/setup` is anonymous (no guard) and stands alone like sign-in (`beforeSignIn` in `core/navigation.ts`,
used by the shell's top bar and by the access handling of D-430). The component reads `code` from the route snapshot into
a private field and calls `history.replaceState(history.state, '', '/setup')` in its constructor: the router has already
written the URL by then (it does so before activating the route), so the code is gone from the address bar and from
history before the first paint. Without a code (a reload after that) the page says "This setup code is not valid." with
"Ask your administrator for a new link." and "Go to sign in". The POST needs the CSRF cookie, so the session is loaded
first, as sign-in does. Answers: 204 goes to sign-in with the toast "Password set. Sign in."; a 400 whose detail names a
password rule ("Password must ...") stays under the form, since the code still works (D-373); every other 400 is the
invalid-code view (the server's one message for unknown, used and expired codes); a 503 is D-431.

**Deviation: "not the email" is checked as "not an email address".** The page holds only the code; no anonymous endpoint
turns a code into an email, and adding one is a backend change outside this item. So the live rule refuses any password
shaped like an email address (`^[^\s@]+@[^\s@]+\.[^\s@]+$`, after trimming), which includes the account's own: the client
is stricter than the server, never looser, and the server still checks its own rule. Rejected: a rule that shows as met
without knowing (a check the page cannot make would read as a promise); putting the email in the link (more personal
data in an address).

**D-429 — Live rules and a strength meter from length and variety.** `password-rules.ts` (pure, tested): the checklist
"12 to 128 characters" (code points, as the server counts, D-402), "Not an email address", "Both fields match", each
item an icon (empty circle, then a check in the found colour) and its words, with ", met" or ", not met" for screen
readers; both fields name it in `aria-describedby`. Submit stays enabled and says "Meet every rule above." when one is
not met (a disabled button would not say why); it is disabled only while a busy wait counts down. Strength: under 12
characters "Too short"; fewer than six distinct characters "Weak" whatever the length; otherwise one step at 12, two at
16, three at 20, plus one for three kinds of character among lower case, upper case, digits and anything else, capped at
"Strong" (four). Length counts most, so a long passphrase of lower-case words rates "Good". No dictionary library. The
meter is PrimeNG MeterGroup (role `meter`, named "Password strength" with the word as `aria-valuetext`), its colour going
with the word, never alone. The two fields are `pInputPassword` (PrimeNG's masking directive) in an IconField with a
real button in the icon slot ("Show password", `aria-pressed`): PrimeNG Password's own toggle is an SVG with a click
handler that a keyboard cannot reach.

**D-430 — A session the server ended signs out here: one interceptor in `core/access.ts`.** `accessInterceptor`
(registered with `withInterceptors` in `app.config.ts`) watches every answer for a 401 whose problem detail is exactly
"Your access changed. Sign in again." (`ACCESS_CHANGED`; the wrong-password 401 of sign-in has another detail and is left
alone). It calls `SessionService.end(notice)` (the session state is forgotten, so the next guard asks the server again,
and the sentence is kept in `notice`), clears the program context and navigates to sign-in, which shows the sentence in
a warning Message above the form until the next successful sign-in. The error still reaches the caller. It does not
navigate away from sign-in or the setup page. `SessionService.load()` treats a 401 from `GET /auth/session` as D-374
says: that answer ended the stale session, so it asks once more and gets the signed-out view, and the guard routes to
sign-in instead of failing the navigation. Rejected: matching every 401 (a session that timed out is a different story,
with no sentence to show); a polling check of the session (the next request already finds out).

**D-431 — Busy sign-in and setup: the server's Retry-After, counted down on the button.** `busyRetryAfter(e)` in
`core/busy.ts` is the seconds of a 503 that carries a numeric `Retry-After` (SEC-2 sends 2), kept between 1 and 60; a 503
without the header (a proxy without a backend) is not this case and shows the ordinary error. Sign-in and setup then show
"Sign-in is busy. Try again in a moment." (`BUSY`) under the form and disable the submit button for that many seconds
with the label "Retry in 2 s", "Retry in 1 s", counted by `Countdown` (a signal and one interval, stopped with the
component). On setup the code is not used up by a busy refusal (D-407), so the same form is submitted again.

**D-432 — Role change, deactivation and reactivation confirm in a ConfirmDialog that stays open on a refusal.** One
keyed `p-confirmdialog` with message and footer templates: the person, the new role Select for a role change ("Change
role" stays disabled until the role differs, since an unchanged role is no change, D-375), and the consequence in one
sentence with an icon: "Their current session ends." for a role change and a deactivation, "They can sign in again." for
a reactivation. The footer's buttons call the API themselves instead of ConfirmDialog's accept, so a 409 ("At least one
active administrator is required", "You cannot change your own role") appears in the dialog and the dialog stays open;
success closes it, updates the row from the returned account and toasts "Riley Chen is now a reviewer.", "... can no
longer sign in." or "... can sign in again.". Deactivate is the only danger-coloured button on the screen.

**D-433 — Shared changes.** `ui/auth-frame.ts` from sign-in (D-428); `.lx-chip` from `audit.css` to `styles.css` and the
Avatar set from `audit-theme.ts` to `theme/avatar-tokens.ts` (D-426, values unchanged); `@keyframes lx-rise` moved from
`login.css` to `styles.css` for the frame and the sign-in headline; `navLinks` takes the role (D-426); `beforeSignIn`
replaces the shell's `/login` test; `SessionService` gains `notice`, `end` and the 401 retry (D-430); `ApiService` gains
`accounts`, `createUser`, `issueSetupCode`, `changeRole`, `setActive` and `setPassword`; `models.ts` gains `Account`,
`UserSetup` and `NewUser`. No backend change: the F5a actions already have audit sentences (UI2-3, D-341).

**D-434 — What the tests prove.** Vitest: `screens/setup/password-rules.spec.ts` (limits in code points with four-byte
characters, the email shape, the match, every strength step, the distinct-character cap, case in other alphabets),
`screens/people/people-view.spec.ts` (status precedence, search, role and status filters and their combination, the
count, upsert order, the own-row menu, the link, the expiry sentence, the form checks), `core/access.spec.ts` (TestBed
with the real interceptor: sign-out, context cleared, sign-in route, the error still thrown; a wrong-password 401 left
alone; no navigation from setup; a 401 session call read as signed out; the notice cleared by a sign-in),
`core/busy.spec.ts` (header parsing, bounds, the countdown and its stop), `core/navigation.spec.ts` (People for
administrators only, `beforeSignIn`), `commands.spec.ts` and `icons.spec.ts` (two new units). End-to-end
(`e2e/people.spec.ts`): the administrator adds a contributor, copies the link (clipboard read back) and the code is in no
browser storage; signs out; the link leaves the address bar; the password is set and the toast shown; the new person
signs in and has no People; the used link is refused with the invalid-code view. A second context signs in as another new
person; the administrator changes their role and their next request lands on sign-in with "Your access changed. Sign in
again."; they sign in again as Reviewer; deactivation ends their session and sign-in fails with the generic message;
reactivation lets them in; a new setup code shows the view with a different link. The own row offers only New setup code.
Filters and search. A reviewer has no People and `/people` sends them to the overview. axe in both themes at 1440 and
400 px without horizontal scroll on the list, the add dialog, the link view, the role confirmation and the setup page
with rules partly met. Two answers are fulfilled by the test instead of the server, because no honest sequence of
requests produces them on demand: the 409 inside the role confirmation (the last-administrator rule is reachable only
through a stale session or two administrators at once, D-376) and the 503 of the hashing gate (SEC-2's own tests hold
the permits in the backend). Both carry the server's exact problem detail and header.

**D-435 — Bundle, and one disagreement.** Initial bundle in `docs/progress/F5b.md` (the interceptor, `beforeSignIn` and
the People rail icon are the only initial additions; People and setup are lazy chunks). Disagreement with point 4: the
setup link carries the code in the query string (`/setup?code=…`), as specified, and the page removes it at once, but
the request for the page has already gone to the server with it, so any access log in front of the application (a load
balancer or CDN log, if one is ever enabled; none is in `infra/` today, and the application's `ApiAccessLog` logs only
`/api` paths and never a query string) would record live codes. A fragment (`/setup#code=…`) is never sent to any server and would close that gap with the same page logic. It
changes the link format the roadmap names, so it is the orchestrator's call; recorded in `docs/progress/F5b.md` as a
follow-up.
