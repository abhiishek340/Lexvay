# Lexvay — Design brief

Decided by the CTO on 2026-11-26. This is the contract for all UI work. Refine within it; do not replace it.

## Who uses this and what matters
A state-authorization director and a program director read a licensing requirement next to the evidence
from their own curriculum and decide. They work for hours in dense material and they are accountable for
the outcome. The interface must feel like a careful legal workbench: calm, exact, fast, and honest about
what the software knows. It must never look like a template or a chatbot.

## Stack
- `primeng@22.1.5` with `@primeuix/themes@3.1.1` (styled mode, CSS variables). One preset, `LexvayPreset`,
  defined with `definePreset` from Aura in `frontend/src/app/theme/lexvay-preset.ts`.
  Dark mode selector: `[data-theme="dark"]`; `system` follows `prefers-color-scheme` by setting the attribute.
- Icons: `@ng-icons/core@36.1.0` with `@ng-icons/phosphor-icons` (regular weight; bold only for the
  finding status icons at small sizes). Register only the icons used.
- Fonts, self-hosted through `@fontsource`: IBM Plex Sans (400, 500, 600), IBM Plex Serif (400, 400
  italic), IBM Plex Mono (400). No requests to external font or icon hosts.
- No Tailwind, no second component library. Layout is plain CSS grid and flex with the tokens below.
- Verify every version against the registry before pinning and record the pins in DECISIONS.md D-002.

## Tokens (light / dark)
| Token | Light | Dark | Use |
|---|---|---|---|
| surface.ground | #F7F7F5 | #0F1113 | page background (warm paper, not pure white) |
| surface.card | #FFFFFF | #16191C | panels, tables |
| surface.raised | #FFFFFF | #1C2024 | dialogs, popovers |
| border.hairline | #DCDCD6 | #2A2F35 | 1 px separators |
| text.primary | #15171A | #ECEDEE | body |
| text.secondary | #555B63 | #A3AAB3 | meta, captions |
| primary (ink blue) | #1B3A6B | #9DB8E8 | actions, selection, focus ring |
| found | #1D6B3A | #7CCF98 | evidence found |
| ambiguous | #8A5A00 | #E8B45A | ambiguous |
| missing | #9B2C1F | #F0958A | evidence missing, confirmed gap |
| notice.bg / notice.fg | #FBF3DC / #5A3E00 | #2E2609 / #F3DFA0 | fictional-data banner |
Radius 4 px (2 px for tags). Spacing scale 4, 8, 12, 16, 24, 32, 48. Elevation only on overlays (one soft
shadow); panels are separated by hairlines, not shadows. Every text/background pair must pass AA.

## Typography
UI 14 px / 20 px IBM Plex Sans; table and list rows 13 px; page title 20 px / 600; section label 11 px
uppercase with 0.06 em tracking in text.secondary. Rule text and quoted evidence are set in IBM Plex Serif
15 px / 24 px: they are the documents under review and must read like documents. Ids, hashes, page and
character offsets in IBM Plex Mono 12 px with tabular figures.

## Shell
- Top bar (48 px): product wordmark "Lexvay" in Plex Sans 600 (no logo mark), tenant name, program switcher
  (PrimeNG Select when more than one program), theme control (segmented: System, Light, Dark), user menu
  (name, role, Sign out).
- Left navigation rail (220 px, collapses to icons under 960 px, becomes a top tab strip under 640 px):
  Program overview, Review, Changes, Evidence report, Audit trail. Each has a Phosphor icon and a text
  label; the active item has a 2 px primary bar and `aria-current="page"`. Show a count badge on Review
  (requirements waiting) and Changes (open events).
- The fictional-data notice is a full-width strip above the top bar for demo tenants, always visible.
- Skip link to main content. Route changes move focus to the page heading.

## Screens
**Program overview.** Header with program name, license type, catalog year. Four figures in one row
separated by hairlines (not cards with shadows): Jurisdictions, Requirements, Waiting for review,
Determinations signed. Jurisdiction table (PrimeNG Table): state name and code, stacked findings bar
(three segments with distinct hatch patterns as well as colour) with an adjacent text legend, review
progress, determination status as a Tag with icon, "Open review" action. Row click opens Review for
that state.

**Review (the screen that gets the most care).** Jurisdiction tabs (PrimeNG Tabs, arrow-key navigation).
A three-pane workspace using PrimeNG Splitter, sizes remembered per user:
1. Requirement list (left, 300 px): ref, title, finding icon and label, decision state; filter chips
   (All, Needs review, Found, Ambiguous, Missing); the current row is clearly selected.
2. Requirement and decision (centre): ref and title, rule text in serif with the rule version label,
   effective date and source link; proposed finding with icon; "How it was checked" in plain words (rules
   engine calculation, or model proposal plus the verification statement); current decision with who and
   when; note field; the three actions: Accept evidence (primary), Request evidence from program director,
   Confirm gap (requires a note; show the requirement inline when missing, do not use a browser alert).
3. Evidence (right): the quoted passage in serif inside a document frame with file name, course code and
   whether the course is required, page number and character range in mono. When no evidence exists, say
   so plainly with the reason.
Keyboard: J / K next and previous requirement, A accept, R request evidence, G focus the note for a gap,
? opens the shortcut list (PrimeNG Dialog). After a decision, advance to the next undecided requirement
and announce the result in a polite live region; also show a PrimeNG Toast. "Sign determination" sits in
a sticky footer bar of the list pane with the progress ("6 of 8 reviewed"); enabled only for a signer when
every requirement has a decision. The signing dialog (PrimeNG Dialog) states what signing means, lists the
three outcomes as radio buttons ("Determined to meet", "Determined not to meet", "No determination made")
and requires an explicit choice.

**Changes.** Table of change events: date, kind with icon, what changed (old → new labels or catalog
years), what it affects (reopened determinations), status Tag, action button with the blocker reason shown
as helper text when the re-run is not yet possible. Progress while the job runs (inline spinner, then
toast).

**Evidence report.** Jurisdiction select; a document-like page (max width 880 px, serif body) with a
stamp block: "Draft. Not a determination." (dashed border) or the signed block (outcome, signer, date,
rule version, versions hash in mono). Requirement table with finding, decision and quoted evidence.
"Generate PDF" then "Download PDF".

**Audit trail.** PrimeNG Table with timestamp (mono), action, record, details, actor name (resolve user
ids to names; "System" for background work); filter by action and record type; newest first.

**Sign in.** Centered single column, wordmark, one sentence on what the product does, email and password
with PrimeNG InputText and Password, inline error. No illustration.

## States and feedback
Loading: PrimeNG Skeleton shaped like the content, never a lone spinner on a blank page. Empty: one
sentence saying what will appear and what action creates it. Errors: inline Message with the server's
problem detail. Long operations show progress and end in a toast. Never block the whole screen.

## What is forbidden
Emoji or Unicode glyphs as icons; gradients; glassmorphism; glowing shadows; oversized rounded cards;
centered hero text; placeholder lorem ipsum; fake data in the UI; the words "compliant" and "meets
requirements"; colour as the only carrier of state; any network request to a third-party host.

## Testing contract
Keep `data-testid="message"` on the status message and stable accessible names for navigation links,
tabs and the three review actions, or update `frontend/e2e` in the same change. Every screen is checked
by axe in the end-to-end suite. Unit-test view-model logic (filters, shortcuts, progress), not markup.
