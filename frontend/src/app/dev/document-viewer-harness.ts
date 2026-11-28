import { Component, input, numberAttribute, signal } from '@angular/core';
import { DocumentViewer } from '../shared/document-viewer/document-viewer';
import { PassageSelection } from '../shared/document-viewer/page-text';

/**
 * Development builds only (see app.routes.ts): the document viewer with selection switched on, so the
 * end-to-end suite can exercise passage selection before a screen that uses it exists. It shows the last
 * emitted selection and nothing else.
 */
@Component({
  selector: 'lx-document-viewer-harness',
  imports: [DocumentViewer],
  template: `
    <div class="lx-page">
      <header class="lx-page-header"><div><h1>Document viewer harness</h1><p>Development build only.</p></div></header>
      @if (document(); as id) {
        <lx-document-viewer [documentId]="id" [pageNumber]="page()" [selectable]="true" (selection)="chosen.set($event)" />
      }
      <section class="lx-harness-result" aria-labelledby="selection-heading">
        <h2 id="selection-heading" class="lx-meta">Last selection</h2>
        @if (chosen(); as s) {
          <output data-testid="selection" [attr.data-page]="s.pageNumber" [attr.data-start]="s.charStart" [attr.data-end]="s.charEnd">{{ s.quote }}</output>
        } @else {
          <p class="lx-muted">Nothing selected yet.</p>
        }
      </section>
    </div>
  `,
  styles: `
    .lx-page { max-width: 880px; display: flex; flex-direction: column; gap: 16px; }
    .lx-page-header { margin-bottom: 0; }
    output { display: block; white-space: pre-wrap; font-family: var(--lx-font-serif); }
  `,
})
export class DocumentViewerHarness {
  /** Query parameters, bound by the router. */
  readonly document = input<string>();
  readonly page = input(1, { transform: numberAttribute });
  readonly chosen = signal<PassageSelection | null>(null);
}
