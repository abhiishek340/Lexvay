import { Component } from '@angular/core';
import { Figure } from '../ui/figure';
import { Help, HelpTerm } from '../ui/help';
import { Label } from '../ui/label';
import { Meter, MeterSegment } from '../ui/meter';
import { Seal } from '../ui/seal';
import { STATUS_MARK, Status, StatusMark } from '../ui/status-mark';

/**
 * Development builds only (see app.routes.ts): the shared v3 components, so the screens that adopt them (UI3-1 to UI3-3)
 * and the screenshot review can see them before a screen uses them. The figures are a fixed illustration labelled as
 * such, not a tenant's data.
 */
const SEGMENTS: readonly MeterSegment[] = [{ tone: 'found', value: 5 }, { tone: 'ambiguous', value: 1 }, { tone: 'missing', value: 1 }];
const HELP: readonly HelpTerm[] = [
  { term: 'Status mark', definition: 'A 10 px shape and a word; the shape alone never carries the state.' },
  { term: 'Meter', definition: 'A bar drawn to scale, always beside the words it shows.' },
];

@Component({
  selector: 'lx-ui-harness',
  imports: [Figure, Help, Label, Meter, Seal, StatusMark],
  template: `
    <div class="lx-page">
      <header class="lx-page-header">
        <div><h1>Components</h1><p class="lx-meta">Development build only · Illustrative figures</p></div>
        <lx-help [terms]="help" />
      </header>
      <div class="lx-harness-figures" role="group" aria-label="Illustrative figures">
        <lx-figure [value]="33" label="To review" />
        <lx-figure value="72%" label="Evidence found" />
        <lx-figure [value]="1" [of]="5" label="Signed" />
      </div>
      <section class="lx-section" aria-labelledby="marks-heading">
        <h2 id="marks-heading">Status marks</h2>
        <ul class="lx-harness-list">
          @for (s of statuses; track s) { <li><lx-status-mark [status]="s" /></li> }
        </ul>
      </section>
      <section class="lx-section" aria-labelledby="meters-heading">
        <h2 id="meters-heading">Meters</h2>
        <ul class="lx-harness-list">
          <li><lx-meter [segments]="segments" [total]="8" text="7 of 8 decided" /></li>
          <li><lx-meter [segments]="[{ tone: 'decided', value: 3 }]" [total]="9" text="3 of 9 decided" /></li>
          <li><lx-meter [segments]="[]" [total]="8" text="0 of 8 decided" /></li>
        </ul>
      </section>
      <section class="lx-section" aria-labelledby="labels-heading">
        <h2 id="labels-heading">Labels and seals</h2>
        <div class="lx-harness-inline">
          <lx-label>Required</lx-label><lx-label>Elective</lx-label><lx-label>Signer</lx-label>
          <lx-seal outcome="DOES_NOT_MEET" signer="Dana Whitfield" signedAt="2026-11-27T09:00:00Z" />
          <lx-seal outcome="MEETS" signer="Dana Whitfield" signedAt="2026-11-27T09:00:00Z" [compact]="true" />
        </div>
      </section>
    </div>
  `,
  styles: `
    .lx-harness-figures { display: flex; gap: 32px; }
    .lx-harness-list { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 12px; }
    .lx-harness-inline { display: flex; flex-wrap: wrap; align-items: center; gap: 24px; }
  `,
})
export class UiHarness {
  readonly statuses = Object.keys(STATUS_MARK) as Status[];
  readonly segments = SEGMENTS;
  readonly help = HELP;
}
