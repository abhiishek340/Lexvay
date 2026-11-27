# Lexvay — Design system v2

Decided by the CTO on 2026-11-26 after the owner asked for a world-class interface: "if someone opens the website,
they should be like wow", very clean, few words, easy to read for everyone, with animation. This replaces v1
completely. It is the contract for all UI work; refine within it.

## Principles
1. **One focal point per screen.** Every screen has a single hero (the state map, the requirement under review, the
   signed seal). Everything else steps back in size, weight and colour.
2. **Show, don't explain.** Numbers become rings and maps; status becomes colour plus icon plus one word. No paragraph
   of explanation appears on a screen. Explanations live behind an info button (`InfoTip`, a PrimeNG Popover).
3. **Motion carries meaning.** Things enter from where they come from, a decision visibly lands, progress fills. Motion
   is short (under half a second for everything but the first view), uses only transform, opacity and SVG stroke, and
   is off under `prefers-reduced-motion`.
4. **Depth, not lines.** Surfaces are separated by elevation and spacing, not hairlines. Hairlines remain only inside
   tables and lists.
5. **Calm and exact.** The product holds legal evidence. No playfulness in copy, no confetti, no sparkle, nothing that
   implies the software decided. People decide; the interface makes their decision feel weighty.

## Word budget (enforced in review)
- Page title: at most 3 words. Subtitle: at most 8 words, or none.
- No uppercase eyebrow labels. Section headings are sentence case, 15 px, 600.
- Buttons: at most 3 words, verb first. Table headers: one or two words.
- A legend appears once per screen, never per row. Counts are written as numerals before the noun: "33 to review".
- Any sentence that explains a concept moves into an `InfoTip`. Empty states are one sentence plus one action.
- Forbidden words stay forbidden: "compliant", "meets requirements" (`ForbiddenWordsTest`).

## Stack
- PrimeNG 22 (styled mode) for behaviour and accessibility, restyled completely by `LexvayPreset` v2 and component
  pass-through. The visual language is ours; no screen should look like stock PrimeNG.
- The PrimeUI license key is required for a clean screen (owner action, ROADMAP "Owner decisions needed"); without it
  every page shows a red license badge, which no design can hide.
- Icons: Phosphor through `@ng-icons`, regular weight at 16 and 20 px, **duotone** at 32 px and larger (empty states,
  map popovers). Icons and component tokens load with the chunk that uses them (PERF-FE-1).
- Fonts, self-hosted (`@fontsource-variable`, versions verified on the registry): **Inter** variable for the interface
  (features `cv11`, `ss01`; tabular figures `tnum` for every number), **Source Serif 4** variable for rule text, quoted
  evidence and the report body, **IBM Plex Mono** for ids, hashes and offsets.
- Motion: native CSS (`animate.enter` / `animate.leave`, keyframes, transitions), the View Transitions API through the
  router (`withViewTransitions`), and the Web Animations API for count-ups and SVG drawing. No animation library and
  no `@angular/animations` (deprecated).

## Tokens
Defined once in the preset as `light-dark()` pairs and published as `--p-lx-*` (v1 mechanism, D-081).

| Token | Light | Dark | Use |
|---|---|---|---|
| ground | #F7F7F4 | #0A0C0F | page background |
| surface | #FFFFFF | #111419 | cards, panels |
| surface-2 | #F1F1ED | #171B21 | insets, inputs, list hover |
| raised | #FFFFFF | #1B2027 | popovers, dialogs, floating bars |
| line | #E6E6E0 | #252B33 | hairlines inside tables and lists only |
| text | #0D1014 | #EEF0F3 | primary text |
| text-2 | #565D67 | #A4ACB7 | secondary text |
| text-3 | #8B919B | #6F7783 | tertiary, placeholders (never for content under 18 px) |
| accent | #2346E0 | #7D9BFF | primary action, focus, selection |
| accent-tint | #EBEFFF | #1A2347 | selected rows, active nav, in-review tiles |
| found | #0B7A52 | #45D19E | evidence found, determined to meet |
| ambiguous | #A35C00 | #F3B54A | ambiguous |
| missing | #C2362B | #FF8B7D | evidence missing, confirmed gap, determined not to meet |
| neutral | #9AA1AB | #59616C | not reviewed, not a target |
| seal | #1C3A8F | #9CB4FF | signed determination seal |

Status tints (backgrounds) are the status colour at 10 % (light) or 16 % (dark) over surface. Every text and
icon pair passes WCAG AA; the agent building the preset proves it with a contrast unit test over the token table.

**Elevation.** e1 `0 1px 2px rgb(13 16 20 / .05), 0 0 0 1px rgb(13 16 20 / .04)`; e2 `0 6px 16px -4px rgb(13 16 20 /
.08), 0 0 0 1px rgb(13 16 20 / .05)`; e3 `0 24px 48px -12px rgb(13 16 20 / .18), 0 0 0 1px rgb(13 16 20 / .06)`. In
dark mode shadows are replaced by a 1 px border in `line` plus an inner top highlight `inset 0 1px 0 rgb(255 255 255
/ .04)`. Cards use e1, hover lifts to e2 with `translateY(-1px)`, overlays use e3.
**Radius.** 8 px controls, 12 px cards, 16 px hero panels, 999 px pills and chips.
**Spacing.** 4, 8, 12, 16, 20, 24, 32, 40, 56, 72. Page gutter 32 px (16 px under 640 px). Content max width 1280 px.

## Typography
| Role | Font | Size / line | Weight | Tracking |
|---|---|---|---|---|
| Display (overview hero, sign-in headline) | Inter | 40 / 44 | 650 | -0.025em |
| Title (page) | Inter | 26 / 32 | 620 | -0.018em |
| Heading (section, card) | Inter | 15 / 22 | 600 | -0.005em |
| Body | Inter | 14 / 22 | 450 | 0 |
| Small | Inter | 13 / 20 | 450 | 0 |
| Number (stat) | Inter `tnum` | 32 / 36 | 600 | -0.02em |
| Document (rule text, quotes) | Source Serif 4 | 17 / 28 | 400 | 0 |
| Mono (ids, hashes, offsets) | IBM Plex Mono | 12 / 16 | 450 | 0 |

## Motion
Tokens (CSS custom properties, defined once in `styles.css`):
`--lx-ease-out: cubic-bezier(.16, 1, .3, 1)`; `--lx-ease-in-out: cubic-bezier(.65, 0, .35, 1)`;
`--lx-spring: linear(0, .009, .035 2.1%, .141 4.4%, .723 12.9%, .938 16.7%, 1.017 20.1%, 1.043 23.4%, 1.035 27.4%,
.999 37.1%, .995 44.2%, 1)`; durations `--lx-t-1: 120ms` (hover, press), `--lx-t-2: 200ms` (toggles, popovers),
`--lx-t-3: 320ms` (panels, list changes, route transitions), `--lx-t-4: 600ms` (count-ups, rings), `--lx-t-hero:
1100ms` (first-view map wave, sign-in story).

| Moment | Motion |
|---|---|
| Route change | View transition: old view fades out 120 ms; new view fades in and rises 8 px over 320 ms `ease-out`. |
| Shared element | A state tile on the overview and the state header in Review share `view-transition-name: state-<code>`; the tile morphs into the header. |
| First view of a list or grid | Items enter with opacity 0 → 1 and `translateY(6px)` → 0, 320 ms, staggered 30 ms, at most 10 staggered (the rest enter with the tenth). `animate.enter` with a `--i` index. |
| Numbers | Count up from 0 on first view of a screen only, 600 ms `ease-out`, tabular figures so width never jumps. |
| Rings and bars | Stroke sweeps from 0 to value, 600 ms `ease-out`, segment by segment. |
| Hover | Cards lift 1 px and gain e2 in 120 ms; buttons darken 4 % in 120 ms; press scales to .98 for 90 ms. |
| Decision in Review | The decided row's status icon draws its stroke (240 ms), the row settles with `--lx-spring`, the next undecided requirement slides in from the right 16 px (320 ms), the progress ring advances. |
| Signing | The seal stamps: scale 1.12 → 1 and rotate -8° → 0 with `--lx-spring` over 420 ms, one ink ring fades out from it over 600 ms. Once, never looped. |
| Overlays | Dialogs and popovers fade and scale from .97, 200 ms; drawers slide 24 px, 320 ms. |
| Loading | Skeletons shaped like the content with a 1.4 s shimmer; never a spinner on an empty page. |

Rules: animate only `transform`, `opacity`, `clip-path` and SVG `stroke-dashoffset`; never width, height, top or left.
No animation loops except the sign-in story, which stops when the user starts typing and never runs under reduced
motion. Under `prefers-reduced-motion: reduce`, every duration becomes 0 except opacity fades of at most 120 ms,
count-ups show the final value, and the map appears at once. Motion must never delay input: a click during an
entrance applies immediately.

## Signature components (shared, `frontend/src/app/ui/`)
- **StateMap.** A tile-grid map of the United States (51 tiles: 50 states and DC in the standard 8 × 12 tile layout,
  positions in one constant) that is Lexvay's visual identity. Tile 44 px, radius 10 px, code in Inter 12/600. Fill by
  state: target with a current signed determination uses the outcome colour (found, missing, or a seal outline for
  "no determination made"); target in review shows `accent-tint` with a bottom-up fill proportional to reviewed /
  total; non-target tiles are `surface-2` with `text-3` codes. Hover or focus opens a popover card (state name,
  ProgressRing, status, one action). Tiles are buttons with full accessible names ("California, 8 of 8 reviewed,
  determined not to meet"); arrow keys move between target tiles (roving tabindex). First view: tiles wave in from
  west to east (`--lx-t-hero`). A legend appears once below the map. Sizes: `hero` (overview), `compact` (register,
  sign-in), `mini` (16 px tiles, popovers).
- **ProgressRing.** SVG ring at 20, 40 or 96 px; either one value or segments by status in the order found, ambiguous,
  missing, not reviewed; centre label optional; accessible name states the numbers.
- **Stat.** A number (count-up), a label of at most three words, an optional ring or delta. No borders: stats sit on
  the surface of a hero panel.
- **CommandPalette.** Ctrl+K / Cmd+K anywhere: go to any screen, jump to a state, jump to a requirement by ref or
  title, run actions the role allows (Run assessment, New rule version, Download register). Fuzzy matching in one pure,
  unit-tested function (subsequence with word-start bonus). Recent items first.
- **DecisionBar.** The floating action bar in Review: raised surface at 88 % opacity with `backdrop-filter:
  blur(12px)` (the only blur in the product), centred at the bottom of the decision pane, three actions with key hints.
- **Seal.** The signed-determination mark: an SVG circle with the outcome, the signer's initials and the date set on
  the ring in Inter caps 10 px, colour `seal`. Used in the report, register and the signing moment.
- **InfoTip.** A 20 px `phosphorInfo` button opening a popover with the explanation that used to be a paragraph.
- **StatusIcon.** One icon that combines finding and decision (for example a filled check for "found, accepted", an
  outlined check for "found, not yet reviewed"), so list rows carry one status, not two labels.

## Shell
- **Top bar** (56 px, surface, e1 when the page has scrolled): the Lexvay mark (an SVG monogram, two strokes forming an
  L and a tick, accent colour) and wordmark; the program switcher as a pill showing the program's short name; centred,
  the command palette trigger styled as a search field ("Search or jump to", key hint); on the right a "Demo data"
  pill for fictional tenants (InfoTip with the full notice), the work queue button with its count (F13 API; popover
  lists what needs the user), and the avatar menu (initials) holding name, role, theme (System, Light, Dark) and Sign
  out. The theme control leaves the bar.
- **Rail** (232 px, collapsible to 64 px with the choice remembered; 64 px under 1200 px; bottom tab bar under 640 px):
  icon and label, the active item an `accent-tint` pill whose position glides between items (320 ms) when the route
  changes. Counts as small pills. Tenant-wide items (Determination register) sit above a divider, program items below.
- Skip link; focus moves to the page title after navigation; visible 2 px accent focus ring with 2 px offset on every
  interactive element.

## Screens
- **Sign in.** Two halves. Left (hidden under 900 px): the StateMap `compact` playing a 6-second story on loop (tiles
  move from neutral to in review to signed), the headline "Every requirement, traced to its evidence." and nothing
  else. Right: the mark, "Sign in", email, password, a full-width accent button; for the demo tenant, a row of four
  account chips that fill the email. Errors appear inline under the field.
- **Program overview.** Title is the program name (Display size) with license and catalog year as two pills. Hero
  panel: StateMap `hero` on the left, three Stats on the right (To review, with ring; Evidence found, as a percentage
  with a segmented ring; Signed, "1 of 5"). Under the hero, "Needs you" (at most three work-queue items as compact rows
  with one action each), then one card per target state: name, segmented ring, status chip, one primary action that
  changes with state (Review, Sign, View report).
- **Review.** Focus mode. Header: state pills with full names and mini rings (the shared-element target), progress
  ring with "6 of 8". Left rail (288 px): requirements as rows of ref, title and one StatusIcon; filter as a segmented
  icon control with counts in tooltips. Centre: the requirement as the hero card: ref chip, title at Title size, rule
  text in Document type inside a quiet inset, finding as a large status chip with the check method in one line and an
  InfoTip for details, current decision. Right: the evidence in `DocumentViewer`. The DecisionBar floats at the bottom
  of the centre pane: Accept (A), Request (R), Gap (G); Gap expands the bar into a note field in place. When every
  requirement is decided, the header shows "Sign California" as the primary action; it pulses once, then stays still.
  The signing dialog is a centred card with the three outcomes as large selectable cards and the Seal preview.
- **Changes.** A vertical timeline of cards: icon, a one-line title ("Colorado rules, revision 2"), old → new as two
  pills with an arrow, status chip, one action. Details (what it affects) collapse under the card.
- **Evidence report.** The document as a sheet (max 880 px, e2, Document type) with the Seal at the top right when
  signed or a dashed "Draft" stamp when not; requirements as a clean list (ref, title, StatusIcon, quote); a floating
  action pill with "Download PDF".
- **Audit trail.** A timeline grouped by day; each entry one sentence ("Dana Whitfield accepted CA-3") with avatar
  initials and time; filters as chips; details expand in place.
- **Rules, Evidence requests, Determination register, Program setup.** Same system: one hero per screen (the version
  list with its selected detail; the inbox; the register's StateMap `compact` above the table; the setup stepper),
  explanations in InfoTips, tables with 52 px rows, hover tint, no zebra striping.

## States and feedback
Loading uses skeletons shaped like the content. Empty states: a 32 px duotone icon in accent, one sentence, one
action. Errors: inline, next to what failed, with the server's message. Long operations show progress and end with a
toast that slides in from the bottom right (the toast never covers the DecisionBar). Never block the whole screen.

## What is forbidden
Emoji or Unicode glyphs as icons; sparkle icons; purple-to-pink or any decorative gradient (the only gradient is the
StateMap's progress fill, which is a hard-edged fill, not a blend); glassmorphism beyond the DecisionBar's single blur;
glowing or coloured shadows; confetti or looping attention animations; centred marketing hero copy inside the app;
placeholder or lorem text; fake data presented as real; the words "compliant" and "meets requirements"; colour as the
only carrier of state; any network request to a third-party host.

## Quality bar and testing contract
- Each screen is reviewed by the orchestrator from screenshots in both themes at 1440 px and 400 px before merge.
- axe in the end-to-end suite on every screen and state: zero serious or critical. Keyboard reaches everything.
- Keep `data-testid="message"` on status messages and stable accessible names for navigation, tabs and the three
  review actions, or update `frontend/e2e` in the same change.
- Unit-test view-model logic (filters, shortcuts, progress, fuzzy matching, map layout), not markup.
- Performance: Largest Contentful Paint under 1.2 s on the seeded tenant in a production build on loopback; no layout
  shift from fonts (`size-adjust` fallbacks) or count-ups; animations never drop below 60 fps on the overview map
  (checked with a Chrome performance trace in the PR).

## Bundle
Icons and component tokens live with the chunk that uses them (PERF-FE-1, D-295 to D-299); the shared preset holds only
the token sets of what the shell and first paint render, and `SHELL_ICONS` only the icons of the shell, sign-in and home.
A routed screen names its other icons in a `<SCREEN>_ICONS` constant next to the screen, provided by the route in
`<screen>.routes.ts` (loaded with `loadChildren`, because route providers are built at start-up), and its other
components' token sets in `<screen>-theme.ts`, registered from its constructor with `registerComponentTokens`; a shared
component does the same through `viewProviders` and its own constructor. `theme/icons.spec.ts` fails on an icon nobody
registers, and the end-to-end suite fails on a "No icon named" warning. The initial bundle budget is in `angular.json`;
a change that crosses the warning moves something out first.
