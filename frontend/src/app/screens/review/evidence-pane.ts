import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, input, linkedSignal } from '@angular/core';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { Mapping } from '../../core/models';
import { DocumentViewer } from '../../shared/document-viewer/document-viewer';
import { EvidenceView } from './review-workspace';

/**
 * The right-hand pane of Review: the system's evidence where it sits on its page (DocumentViewer, the quote alone in a
 * disclosure), then every passage a person provided in answer to an evidence request (F4), newest first. Under
 * 1024 px each page waits behind its own "Show on page" and takes focus when opened.
 */
@Component({
  selector: 'lx-evidence-pane',
  imports: [DatePipe, NgTemplateOutlet, NgIcon, ButtonDirective, ButtonLabel, Message, Skeleton, DocumentViewer],
  templateUrl: './evidence-pane.html',
  styleUrl: './evidence-pane.css',
})
export class EvidencePane {
  readonly mapping = input.required<Mapping>();
  readonly view = input<EvidenceView | null>(null);
  /** The three-pane layout, where the page is always on screen. */
  readonly wide = input(true);

  /** The pages shown on request under 1024 px, by key ('system' or a response id); every requirement starts closed. */
  readonly showOnPage = linkedSignal<string, ReadonlySet<string>>({ source: () => this.mapping().id, computation: () => new Set() });

  toggleOnPage(key: string): void {
    this.showOnPage.update((keys) => {
      const next = new Set(keys);
      if (!next.delete(key)) { next.add(key); }
      return next;
    });
  }
}
