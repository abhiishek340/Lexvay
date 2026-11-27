import { Component } from '@angular/core';
import { ProgressRing, findingSegments } from '../ui/progress-ring';
import { Seal } from '../ui/seal';
import { Stat } from '../ui/stat';
import { StatusIcon } from '../ui/status-icon';
import { MapState } from '../ui/state-map/state-map-model';
import { StateMap } from '../ui/state-map/state-map';

/**
 * Development builds only (see app.routes.ts): the shared v2 components at every size, so the screens that adopt them
 * (UI2-1 to UI2-4) and the screenshot review can see them before a screen uses them. The figures are a fixed
 * illustration labelled as such, not a tenant's data.
 */
const STATES: MapState[] = [
  { code: 'CA', tile: 'DOES_NOT_MEET', reviewed: 8, total: 8, status: 'Determined not to meet', action: null },
  { code: 'UT', tile: 'MEETS', reviewed: 8, total: 8, status: 'Determined to meet', action: null },
  { code: 'AZ', tile: 'NOT_DETERMINED', reviewed: 7, total: 7, status: 'No determination made', action: null },
  { code: 'CO', tile: 'IN_REVIEW', reviewed: 6, total: 9, status: 'Not signed', action: null },
  { code: 'TX', tile: 'IN_REVIEW', reviewed: 2, total: 8, status: 'Not signed', action: null },
  { code: 'NY', tile: 'IN_REVIEW', reviewed: 0, total: 0, status: 'Not assessed', action: null },
];

@Component({
  selector: 'lx-ui-harness',
  imports: [ProgressRing, Seal, Stat, StatusIcon, StateMap],
  template: `
    <div class="lx-page">
      <header class="lx-page-header"><div><h1>Components</h1><p>Development build only. Illustrative figures.</p></div></header>
      <section class="lx-hero lx-harness-hero" aria-label="Hero map and stats">
        <lx-state-map [states]="states" size="hero" label="Illustrative states" />
        <div class="lx-harness-stats">
          <lx-stat [value]="33" label="To review"><lx-progress-ring [size]="40" [value]="9" [total]="42" label="9 of 42 reviewed" /></lx-stat>
          <lx-stat [value]="72" suffix="%" label="Evidence found"><lx-progress-ring [size]="40" [segments]="segments" [total]="42" label="30 found, 6 ambiguous, 2 missing, 4 not reviewed" /></lx-stat>
          <lx-stat [value]="1" [of]="5" label="Signed" />
        </div>
      </section>
      <div class="lx-harness-row">
        <section class="lx-card lx-harness-card" aria-label="Compact and mini maps">
          <lx-state-map [states]="states" size="compact" label="Illustrative states, compact" />
          <lx-state-map [states]="states" size="mini" [interactive]="false" />
        </section>
        <section class="lx-card lx-harness-card" aria-label="Rings, seal and status icons">
          <div class="lx-harness-inline">
            <lx-progress-ring [size]="20" [value]="3" [total]="8" label="3 of 8 reviewed" />
            <lx-progress-ring [size]="40" [value]="6" [total]="8" label="6 of 8 reviewed">6/8</lx-progress-ring>
            <lx-progress-ring [size]="96" [segments]="segments" [total]="42" label="30 found, 6 ambiguous, 2 missing, 4 not reviewed">38/42</lx-progress-ring>
            <lx-seal outcome="DOES_NOT_MEET" signer="Dana Whitfield" signedAt="2026-11-27T09:00:00Z" />
          </div>
          <ul class="lx-harness-status">
            <li><lx-status-icon finding="EVIDENCE_FOUND" [showLabel]="true" /></li>
            <li><lx-status-icon finding="EVIDENCE_FOUND" decision="ACCEPTED" [showLabel]="true" /></li>
            <li><lx-status-icon finding="AMBIGUOUS" decision="EVIDENCE_REQUESTED" [showLabel]="true" /></li>
            <li><lx-status-icon finding="EVIDENCE_MISSING" decision="GAP_CONFIRMED" [showLabel]="true" /></li>
            <li><lx-status-icon finding="EVIDENCE_FOUND" decision="ACCEPTED" [stale]="true" [showLabel]="true" /></li>
          </ul>
        </section>
      </div>
    </div>
  `,
  styles: `
    .lx-harness-hero { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 40px; align-items: center; padding: 32px; }
    .lx-harness-stats { display: flex; flex-direction: column; gap: 24px; }
    .lx-harness-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr)); gap: 24px; margin-top: 24px; }
    .lx-harness-card { padding: 24px; display: flex; flex-direction: column; gap: 24px; }
    .lx-harness-inline { display: flex; flex-wrap: wrap; align-items: center; gap: 24px; }
    .lx-harness-status { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
    @media (max-width: 900px) { .lx-harness-hero { grid-template-columns: minmax(0, 1fr); padding: 16px; } }
  `,
})
export class UiHarness {
  readonly states = STATES;
  readonly segments = findingSegments({ found: 30, ambiguous: 6, missing: 2, notReviewed: 4 });
}
