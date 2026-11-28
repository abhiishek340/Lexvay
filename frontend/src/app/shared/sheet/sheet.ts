import { Component, ElementRef, Injector, afterNextRender, inject, input, output, viewChild } from '@angular/core';
import { Drawer } from 'primeng/drawer';

/**
 * A modal overlay sheet at the right of the window (DESIGN.md "Overlays"): PrimeNG Drawer, floating 12 px inside the
 * window with the overlay radius and elevation, fading in and rising 4 px over 150 ms like every overlay (never the
 * drawer's half-second slide), with a Section-type heading that names it and takes focus when it opens, so its focus
 * trap starts inside.
 *   <lx-sheet heading="Provide evidence" [open]="open()" (closed)="close()" size="wide">…</lx-sheet>
 * The backdrop is the sheet's own, not PrimeNG's mask: the Drawer removes its mask on animationend, which never fires
 * when animations are reduced to zero, and a mask left behind blocks the whole page (D-265). Escape closes it only
 * when pressed inside it: PrimeNG listens on the whole document, so Escape in a dialog opened above the sheet (the
 * viewer's range dialog) would close the sheet too. PrimeNG marks the panel "complementary"; pass-through makes it a
 * modal dialog named by the heading (D-087). The chunk that shows a sheet registers the Drawer token set.
 * `size`: `wide` is 70 % of the window (a document beside a form), `narrow` 40 rem; under 1024 px both fill the window.
 */
@Component({
  selector: 'lx-sheet',
  imports: [Drawer],
  template: `
    @if (open()) { <div class="lx-sheet-backdrop" aria-hidden="true" (click)="closed.emit()"></div> }
    <p-drawer [visible]="open()" (visibleChange)="!$event && closed.emit()" (onShow)="shown()" position="right" appendTo="body" [blockScroll]="true"
              [modal]="false" [closeOnEscape]="false" [styleClass]="'lx-sheet lx-sheet-' + size()" [ariaCloseLabel]="closeLabel()"
              [pt]="{ root: { role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': headingId() } }">
      <ng-template #header>
        <h2 [id]="headingId()" class="lx-sheet-heading" tabindex="-1" #title>{{ heading() }}</h2>
      </ng-template>
      <ng-content />
    </p-drawer>
  `,
  styles: `
    /* Under the drawer (PrimeNG places modal layers from z-index 1100) and over the page. */
    .lx-sheet-backdrop { position: fixed; inset: 0; z-index: 1099; background: var(--p-mask-background); animation: lx-sheet-backdrop var(--lx-t) var(--lx-ease) both; }
    @keyframes lx-sheet-backdrop { from { opacity: 0; } }
    /* The drawer is appended to the body, outside :host: its frame is set on PrimeNG's root by class. */
    ::ng-deep .p-drawer.lx-sheet {
      max-width: none; height: calc(100% - 24px); margin: 12px; border: 0; border-radius: var(--lx-r-overlay); box-shadow: var(--p-lx-overlay);
    }
    ::ng-deep .p-drawer.lx-sheet-wide { width: calc(70% - 24px); }
    ::ng-deep .p-drawer.lx-sheet-narrow { width: min(40rem, calc(100% - 24px)); }
    ::ng-deep .p-drawer.lx-sheet.p-drawer-enter-right { animation: lx-overlay-in 150ms var(--lx-ease) both; }
    ::ng-deep .p-drawer.lx-sheet.p-drawer-leave-right { animation: lx-overlay-in 150ms var(--lx-ease) reverse both; }
    @media (max-width: 1023.98px) {
      ::ng-deep .p-drawer.lx-sheet, ::ng-deep .p-drawer.lx-sheet-wide, ::ng-deep .p-drawer.lx-sheet-narrow { width: 100%; height: 100%; margin: 0; border-radius: 0; }
    }
    .lx-sheet-heading { font-size: 15px; line-height: 24px; font-weight: 600; letter-spacing: -0.004em; overflow-wrap: anywhere; }
    .lx-sheet-heading:focus { outline: none; }
    .lx-sheet-heading:focus-visible { outline: 2px solid var(--p-lx-accent); outline-offset: 4px; }
  `,
  host: { '(document:keydown.escape)': 'onEscape($event)' },
})
export class Sheet {
  private readonly injector = inject(Injector);
  readonly heading = input.required<string>();
  /** Unique on the page: the heading's id, which names the dialog. */
  readonly headingId = input.required<string>();
  readonly open = input(false);
  readonly size = input<'wide' | 'narrow'>('wide');
  readonly closeLabel = input('Close');
  readonly closed = output<void>();

  private readonly headingEl = viewChild<ElementRef<HTMLElement>>('title');

  shown(): void {
    afterNextRender(() => this.headingEl()?.nativeElement.focus(), { injector: this.injector });
  }

  onEscape(event: Event): void {
    const panel = this.headingEl()?.nativeElement.closest('.p-drawer');
    if (this.open() && panel && event.target instanceof Node && panel.contains(event.target)) {
      this.closed.emit();
    }
  }
}
