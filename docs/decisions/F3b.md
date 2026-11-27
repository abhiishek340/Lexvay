# F3b — Rule workspace review fixes: decisions

F3b has no reserved id range in the D-074 index. These entries amend F3's D-213, D-217 and D-218, and add two
decisions labelled F3b-1 and F3b-2 until the orchestrator assigns them ids when folding this file.

**D-213 (amended) — Edit grid as two-line form rows.** Each requirement is two rows of the same PrimeNG Table body:
line one has ref (mono), title (flexible), type, the numeric check controls when the type is numeric, and the move
and delete icon buttons; line two has the text Textarea across the full width, with the row's server error directly
under it. Each control has a visible label and keeps its `aria-label` naming the ref and the column. A container query
on the form lays line one out by the pane's own width. At 740 px or more it is one line, with a fixed check column
so the fields of every row line up. From 480 to 739 px it is two lines: ref, title and actions, then type and check.
Under 480 px each field stacks, which covers every window under 640 px. The table has no `min-width` and no scroll
container, and the e2e test checks it at 1024, 1280 and 1440 px. In a 1440 px window the pane is about 760 px wide,
so the title field is the narrowest part (about 7 rem) of a numeric row. Rejected: a horizontally scrolling grid (the
first review); one cell per column (does not fit the 676 px pane at 1280 px).

**D-217 (amended) — Rule text through DocumentViewer.** The Rule text tab renders `lx-document-viewer` (F1)
read-only with no highlight, on page 1 of the version's document. F3's own `rule-text.ts` is deleted, so there is
one way to render a page. Pages come through `DocumentPageCache`.

**D-218 (amended) — Extraction failure in plain words.** The detail header says "Requirement extraction did not
produce a list. A reviewer can enter the list by hand with Edit list." The stored job error sits in mono inside a
native `details` element, "Technical detail", collapsed by default. The version list row shows only the
*Extraction failed* Tag. The option's accessible name never contained the error.

**F3b-1 — Unsaved-changes guard.** The Rules screen provides a screen-scoped `UnsavedChanges` signal, which the
Requirements tab sets while it is editing and the list is dirty. The route's `canDeactivate` (`confirmLeave` in
`app.routes.ts`, typed by a `ConfirmsLeave` interface so the routes do not import the lazy screen) asks the screen.
`leaveNeedsConfirmation(dirty, currentUrl, nextUrl)` is pure and unit-tested. It asks only when edits are unsaved and
the path changes. The screen's route serves every version, so choosing another version also goes through the guard.
The question is a PrimeNG ConfirmDialog: "Discard unsaved changes to this list?", with "Discard changes" (danger,
outlined) and "Keep editing" (primary, focused by default). ConfirmDialog does not pass `closeAriaLabel` to its
dialog, so the close button is named "Close and keep editing" through pass-through. When the person keeps editing,
the Listbox selection goes back to the current version through a `linkedSignal`. `beforeunload` calls
`preventDefault()` while edits are unsaved, so a reload or tab close gets the browser's own question.

**F3b-2 — Tokens of Rules-only components load with the Rules chunk.** Accordion, ConfirmDialog, DatePicker and
Listbox are rendered only by the Rules screen. Their Aura token sets move out of `LexvayPreset` and are merged into
the active preset with `updatePreset` from `@primeuix/themes` when the Rules component is first constructed
(`rules-theme.ts`), before any of those components exists. The initial bundle goes from 704.6 kB to 698.9 kB raw,
under the 700 kB warning (D-083). InputNumber stays in the preset because F1's DocumentViewer uses it on Review too.
Rejected: `@defer` around the edit grid's dialogs and the new-version dialog. They are already in the lazy `rules`
chunk, so deferring them does not change the initial bundle; the initial bundle carries the preset, not the dialogs.
