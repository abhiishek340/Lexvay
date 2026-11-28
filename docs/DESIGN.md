# Lexvay — Design system v3 ("Ledger")

Decided by the CTO on 2026-11-27 after the owner reviewed v2 on the running app and called it AI slop: yellow and
orange accents, icon-heavy cards, a palette and card art that look generated rather than crafted. The owner asked for
a professional, modern, clean interface with human craft in every padding and margin, judged as a senior design
director would judge it. v3 replaces v2 completely. It is the contract for all UI work; refine within it, never
around it.

## What made v2 look generated (do not reintroduce)
1. A stock SaaS rainbow: saturated blue plus amber, emerald and coral, each used as fills and tints.
2. Pills for everything: tags, statuses, counts, filters, tabs, demo notice. Rounded-full tinted shapes everywhere.
3. An icon on every label, info circles on every heading, dashed and question-mark circles as status.
4. Decorative data: progress rings beside every number (mostly empty), rings inside tabs, count-up animations.
5. A 50-tile state map as the hero where five tiles carry meaning.
6. Card soup: every region a rounded white card with a shadow on an off-white page, cards nested in cards.
7. Undisciplined type: a 40 px program name, many sizes and weights, no rhythm; keyboard-hint chips inside buttons.

## Principles
1. **Typography and alignment carry the design.** Hierarchy comes from size, weight, position and whitespace on a
   strict grid, not from colour, boxes or icons. If a screen needs a box to look organised, the layout is wrong.
2. **Ink on paper.** The interface is near-monochrome. Colour appears only where it means something a person must
   act on: evidence found, evidence missing, the primary action, focus and selection. Nothing is coloured for
   decoration.
3. **Structure with rules, not cards.** Sections are separated by 1 px hairlines and space. Cards exist only for
   things that float (menus, popovers, dialogs) or are physically a document (the report sheet).
4. **Honest data.** Show the number. A bar is used only when proportion matters and is drawn to scale. No rings, no
   count-ups, no gauges.
5. **Quiet motion.** Motion confirms an action or orients the eye. It is short (150–200 ms), small (4 px), and never
   performs. Nothing loops, waves, stamps or bounces.
6. **Calm and exact.** The product holds legal evidence. Plain words, people decide, the interface never implies the
   software determined anything.

## Word budget (enforced in review)
- Page title: at most 3 words. A meta line under it in `ink-2`, items separated by " · ".
- Section headings: sentence case, no eyebrow labels, no icons beside them.
- Buttons: at most 3 words, verb first. Table headers: one or two words.
- Counts are numerals before the noun: "33 to review". Empty states: one sentence and one action.
- Explanations: at most one help affordance per screen (see Help), never an info icon per heading.
- Forbidden words stay forbidden: "compliant", "meets requirements" (`ForbiddenWordsTest`).

## Stack
- PrimeNG 22 (styled mode) for behaviour and accessibility, restyled completely by `LexvayPreset` v3. No screen may
  look like stock PrimeNG; no screen may look like v2.
- Fonts, self-hosted: **Inter** variable for the interface (features `cv11`, `ss01`, `tnum` for every number),
  **Source Serif 4** variable for rule text, quoted evidence and the report body only, **IBM Plex Mono** for hashes
  and document offsets only. Requirement refs (AZ-1) are Inter `tnum` 500, not mono.
- Icons: Phosphor **regular** through `@ng-icons`, 16 px (20 px only in the bottom tab bar). No duotone, no fill
  variants except the status marks defined below. Per-chunk registration (PERF-FE-1) unchanged.
- Motion: native CSS and View Transitions only.

## Colour tokens
Defined once in the preset as `light-dark()` pairs, published as `--p-lx-*`. No other colour may appear in any
stylesheet (a unit test scans `frontend/src` for hex, rgb and hsl literals outside the token files).

| Token | Light | Dark | Use |
|---|---|---|---|
| paper | #FAFAF9 | #0F1012 | page background |
| surface | #FFFFFF | #151618 | panels that must separate from paper (tables, side panes, inputs) |
| sunken | #F3F3F1 | #1B1C1F | hover rows, selected rows (with the rule), code wells |
| line | #E6E5E2 | #26272B | hairlines |
| line-strong | #D2D1CC | #34363B | input and secondary-button borders, column rules |
| line-control | #8C8F95 | #6E7178 | borders of text inputs, select, multiselect, autocomplete and search (3:1, WCAG 1.4.11; replaces line-strong there) |
| ink | #141517 | #ECECEE | primary text, primary button fill (light) |
| ink-2 | #5A5D63 | #A3A6AD | secondary text, meta, table headers, icons |
| ink-3 | #8C8F95 | #6E7178 | decorative only (rules, disabled); never body text |
| accent | #2F4FBF | #93A6F2 | links, focus ring, selection rule, the one active indicator |
| accent-wash | #EEF1FA | #1C2133 | selected row background (with the accent rule), focused input background never |
| found | #2D7A53 | #62B88E | evidence found, determined to meet |
| missing | #B0372E | #E2806F | evidence missing, confirmed gap, determined not to meet, destructive |

There is **no amber, yellow, orange, teal or purple** anywhere, including warnings. "Ambiguous", "outdated", "needs
review", "blocked" and "demo data" are neutral (`ink-2`) and are distinguished by shape and words. Every text and mark
pair passes WCAG AA; `tokens.spec.ts` proves it over this table.

**Radius.** 6 px for controls and labels, 8 px for overlays and the report sheet, 0 for page sections and tables,
full circle only for status dots and avatars.
**Elevation.** Page content has none. Overlays (menu, popover, dialog, toast): `0 12px 32px -8px rgb(20 21 23 /
.14), 0 0 0 1px var(--p-lx-line)`; dark mode uses the 1 px line plus `0 12px 32px -8px rgb(0 0 0 / .5)`.
**Spacing.** A 4 px grid: 4, 8, 12, 16, 24, 32, 40, 56, 80. Page padding 40 px (20 px under 640 px). Content max width
1200 px, left aligned under the title (not centred in a void). Sections are 40 px apart with a hairline between them.

## Typography
| Role | Font | Size / line | Weight | Tracking |
|---|---|---|---|---|
| Title (page) | Inter | 24 / 32 | 600 | -0.012em |
| Section | Inter | 15 / 24 | 600 | -0.004em |
| Body | Inter | 14 / 20 | 400 | 0 |
| UI (buttons, inputs, nav) | Inter | 13 / 20 | 500 | 0 |
| Meta, table header | Inter | 12 / 16 | 500 | 0.01em |
| Figure (key numbers) | Inter `tnum` | 28 / 32 | 500 | -0.02em |
| Document (rule text, quotes, report body) | Source Serif 4 | 16 / 26 | 400 | 0 |
| Mono (hashes, offsets) | IBM Plex Mono | 12 / 16 | 400 | 0 |

No other sizes. No uppercase except state codes. No text larger than 28 px anywhere in the app (sign-in included).

## Status marks (`ui/status-mark.ts`, replaces `StatusIcon`)
Status is always a mark plus a word; the mark alone is never the only carrier.

| Status | Mark (10 px, SVG) | Colour |
|---|---|---|
| Evidence found / accepted | filled circle | found |
| Evidence missing / gap confirmed | filled square, 1 px radius | missing |
| Ambiguous | circle, left half filled | ink-2 |
| Not reviewed | circle outline, 1.5 px | ink-3 stroke, ink-2 word |
| Evidence requested | circle outline with centre dot | ink-2 |
| Outdated / stale | dashed circle outline | ink-2 |
| Blocked | outline square | ink-2 |
| Signed | ink seal (see Seal) | ink |

## Components (shared, `frontend/src/app/ui/`)
- **Meter** (replaces `ProgressRing` and `Stat` rings). A 4 px tall, square-ended horizontal bar on `sunken`, segments
  in the order found, ambiguous (`ink-2` at 55 %), missing, not reviewed (empty). Always accompanied by text
  ("6 of 8 decided"). Drawn to scale; no animation beyond a 150 ms width-free `scaleX` on first render.
- **Figure.** A key number in Figure type over a 12 px meta label. No ring, no icon, no count-up. Figures sit in a row
  separated by 1 px vertical rules.
- **Label** (replaces tags and pills). Only when a compact attribute must stand apart in a dense row ("Required",
  "Elective", a role): 20 px high, 6 px radius, 1 px `line-strong` border, `ink-2` 12/500 text, no fill, no icon.
  Elsewhere attributes are plain text joined with " · ".
- **Seal.** The signed-determination mark, redrawn as line art: a 40 px double ring in `ink`, the signer's initials in
  Inter 11/600, the date beneath the mark in meta type. No outcome colour, no rotation, no stamp animation (it fades in
  over 200 ms).
- **Help.** One per screen at most: a ghost "?" button at the end of the title row opening a popover with the screen's
  definitions as a short list. Terms inside the page may be explained with a dotted underline and a tooltip. Remove
  every per-heading `InfoTip`.
- **CommandPalette** stays (behaviour unchanged), restyled as an overlay with the v3 elevation, 13 px rows, no icons
  except the result type at 16 px in `ink-2`.
- **DecisionBar** becomes a fixed footer of the Review centre column: a hairline above, the three actions left-aligned
  (Accept is the primary button; Request and Gap are secondary, Gap with `missing` text). No blur, no floating, no key
  chips (shortcuts live in the button tooltips and the Help list). The `backdrop-filter` exception is withdrawn.
- **StateMap** is retired from every screen and deleted with its tests; geography adds no information here.
- **Buttons.** 32 px (36 px for the single primary action of a screen), radius 6, 13/500, 12 px horizontal padding.
  Primary: `ink` fill with `paper` text (dark: `ink` fill is light, text dark). Secondary: `surface` with a 1 px
  `line-strong` border. Ghost: text only, `sunken` on hover. Destructive: secondary with `missing` text. Icons in
  buttons only when the button has no text.
- **Inputs.** 32 px, `surface`, 1 px `line-strong`, radius 6, 13 px text; focus is the 2 px accent ring (no glow).
- **Tables.** The default structure for lists. 40 px rows, 1 px `line` between rows, no outer card, header row in meta
  type on `paper` with a `line-strong` rule below. Hover is `sunken`; selection is `accent-wash` with a 2 px accent
  rule on the left. Numbers right aligned in `tnum`.

## Shell
- **Top bar** 48 px on `paper` with a hairline below. Left: the Lexvay mark (monochrome `ink`) and wordmark, then the
  program switcher as a ghost text button with a chevron. Centre-left: search, a bordered 32 px field 280 px wide
  ("Search", key hint in `ink-3` inside the field). Right: the work queue as a ghost icon button with a 6 px accent
  dot when something needs the user (the count is inside its popover), and the account button (24 px avatar on
  `sunken` with `ink` initials). Demo data is shown as plain `ink-2` meta text "Demo data" left of the work queue,
  with its notice in a tooltip; never a coloured pill.
- **Rail** 220 px on `paper`, a hairline on its right. Items are 32 px rows: 16 px icon in `ink-2` and label in UI
  type; the active item has `ink` text, `sunken` background and a 2 px `ink` rule at its left edge. Counts are plain
  `tnum` meta text at the right in `ink-2`; no count pills. Two groups with 12 px meta headings ("Workspace",
  "Program"). Collapsible to 56 px; 56 px under 1200 px; bottom tab bar under 640 px with the More overflow (F5b).
- Skip link; focus moves to the page title after navigation without drawing a ring on it (programmatic focus on a
  heading uses `:focus:not(:focus-visible) { outline: none }`); every interactive element shows the 2 px accent ring
  with 2 px offset on keyboard focus.

## Screens
- **Sign in.** One centred column, 360 px, on `paper`: the mark and wordmark, "Sign in" in Title type, email,
  password, a full-width primary button. For the demo tenant, a quiet list "Demo accounts" of four text buttons
  (role and name). Below the form, one line of meta text: "Fictional demo data." No art, no map, no slogan panel.
- **Program overview.** Title row: program name (Title), meta line (license · catalog year · N target states), the
  primary action on the right when one exists (Run assessment, or Sign when everything is decided). Then a Figure
  row: To review, Evidence found (%), Signed (n of m). Then **States**, a table: State (full name), Rules (version
  label), Progress (Meter plus "6 of 8 decided"), Evidence found (%), Determination (Seal plus "Signed by Dana
  Whitfield, 27 Nov" or "Not signed"), and a right-aligned text action (Review / Sign / Report). Then **Needs you**
  (work queue, at most five rows) as a plain list. No map.
- **Review.** Three columns divided by vertical hairlines, no cards: requirement list (280 px), requirement (flex),
  evidence (420 px). The header is the title "Review", a state switcher as an underlined tab row (state names in UI
  type, the active one `ink` with a 2 px `ink` underline; progress as "3 of 8" meta text after each name, no rings),
  and on the right "3 of 8 decided" with a Meter 120 px wide. The list filter is a text tab row (All, To review,
  Found, Ambiguous, Missing) with counts, not icons. The requirement column: ref and title (Title type), the rule
  text in Document type with a 2 px `line-strong` rule on its left (no box), source line in meta, then "Finding" as a
  section: StatusMark and word, the check method as one plain line; then the decision footer.
- **Coverage.** The matrix as a table on `surface`; cells use the status marks (filled circle system quote, outline
  circle person's, both = ring with dot, dashed = outdated); flags are plain text with a mark, filter as a text tab
  row. Column headers are course codes in `tnum` meta type.
- **Changes, Audit trail.** Plain lists grouped by day under meta headings; each row one sentence in Body, the time in
  meta at the right, detail expands below the row. No timeline dots or cards.
- **Evidence report.** The only card in the app: a sheet 816 px wide (US Letter proportion) on `surface` with 8 px
  radius and the overlay shadow, Document type body, the Seal at the top right when signed or the meta word "Draft".
  Actions (Download PDF, Check a file) in the title row, not floating.
- **Rules, Requests, Register, Setup, People.** Same system: title row, meta line, tables. Program setup's steps are
  a numbered text list on the left (number in `tnum`, the current step `ink` with the left rule), not a stepper of
  circles.

## Motion
Tokens: `--lx-ease: cubic-bezier(.2, 0, 0, 1)`; `--lx-t-fast: 120ms` (hover, press), `--lx-t: 180ms` (everything
else). Route change: the new view fades in from 0 and rises 4 px over 180 ms; nothing staggers. Overlays fade and
rise 4 px over 150 ms. A decision in Review updates the row's mark with a 120 ms fade and moves the selection to the
next requirement without sliding. No count-ups, no stroke sweeps, no stamps, no waves, no springs. Under
`prefers-reduced-motion: reduce` all of it is off.

## States and feedback
Loading: skeleton rows the height of real rows in `sunken`, no shimmer. Empty states: one sentence in `ink-2` and one
action, no illustration or icon. Errors: inline next to what failed, `missing` text, the server's message. Toasts:
bottom right, overlay style, text only, 4 s.

## What is forbidden
Amber, yellow, orange, teal, purple or any colour outside the token table; gradients of any kind; coloured, glowing or
blurred shadows; backdrop blur; pills and rounded-full chips; rings, gauges and count-ups; duotone or filled icons
(except status marks); an icon beside a heading; more than one help affordance per screen; cards for page sections or
cards inside cards; text above 28 px; centred marketing copy; emoji or Unicode glyphs as icons; sparkle icons;
confetti or looping animation; keyboard-hint chips inside buttons; placeholder or lorem text; colour as the only
carrier of state; network requests to third-party hosts; the words "compliant" and "meets requirements".

## Quality bar and testing contract
- The orchestrator reviews every screen from screenshots in both themes at 1440 px and 400 px against this document
  before merge, looking specifically for the seven v2 failures listed at the top.
- axe on every screen and state: zero serious or critical. Keyboard reaches everything.
- `tokens.spec.ts` checks AA contrast over the table; a source scan fails on colour literals outside the token files
  and on the forbidden component names (`ProgressRing`, `StateMap`, `p-tag`, `p-chip`, `rounded-full`).
- Keep `data-testid="message"` on status messages and stable accessible names for navigation, tabs and the three
  review actions, or update `frontend/e2e` in the same change.
- Performance: LCP under 1.0 s on the seeded tenant in a production build on loopback; no layout shift.

## Bundle
Unchanged from v2 (PERF-FE-1): icons and component tokens live with the chunk that uses them, the shared preset holds
only what the shell and first paint render, and the initial bundle stays under the `angular.json` warning. v3 should
shrink it: the map, rings, count-ups and duotone icons leave.
